package com.canim.app.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.min
import kotlin.random.Random

/**
 * Per-host request policy: concurrency limit, 429 cooldown, and idempotent retry.
 *
 * Rules enforced here:
 * - Max [maxConcurrent] simultaneous in-flight requests (semaphore).
 * - After an HTTP 429, all subsequent requests are blocked for [cooldownMs] (or the
 *   value from Retry-After, whichever is greater) before being allowed to proceed.
 * - Idempotent read requests are retried up to [maxRetries] times on Timeout or 5xx,
 *   with exponential backoff (base 1 s, cap 8 s) plus ±[jitterMs] random jitter.
 * - Mutations (non-idempotent) are NOT retried — callers pass `retryable = false`.
 * - CancellationException is always re-thrown to preserve structured concurrency.
 * - Token, Authorization, PKCE, and other sensitive headers are never logged here.
 */
class RequestPolicy(
    internal val maxConcurrent: Int = 4,
    internal val maxRetries: Int = 2,
    internal var baseBackoffMs: Long = 1_000L,
    internal var maxBackoffMs: Long = 8_000L,
    internal var jitterMs: Long = 300L,
    /** Clock abstraction — override in tests to avoid real wall-clock delays. */
    clock: () -> Long = { System.currentTimeMillis() },
    val limiter: AdaptiveRateLimiter = AdaptiveRateLimiter(
        host = "general",
        burstCapacity = 10,
        refillIntervalMs = 700L,
        baseCooldownMs = 5_000L,
        maxCooldownMs = 60_000L,
        clock = clock
    )
) {
    internal var clock: () -> Long = clock
        set(value) {
            field = value
            limiter.clock = value
        }

    internal val semaphore = Semaphore(maxConcurrent)

    /** Expose for testing. */
    fun getCooldownUntilMs(): Long = clock() + limiter.remainingCooldownMs()

    /**
     * Arms an adaptive cooldown that will block new requests.
     * Respects [retryAfterMs] if > 0, otherwise uses progressive backoff via [AdaptiveRateLimiter].
     */
    fun armCooldown(retryAfterMs: Long, minCooldownMs: Long = 5_000L) {
        val duration = if (retryAfterMs > 0) retryAfterMs else minCooldownMs
        limiter.onRateLimitedBlocking(duration)
    }

    suspend fun armCooldownAsync(retryAfterMs: Long = 0L) {
        limiter.onRateLimited(retryAfterMs)
    }

    suspend fun onSuccess() {
        limiter.onSuccess()
    }

    fun onSuccessBlocking() {
        limiter.onSuccessBlocking()
    }

    /**
     * Resets all policy state (cooldown, backoff, limiter) for use in unit tests.
     * Call this in @Before / @After to prevent cross-test contamination via shared singletons.
     */
    fun resetForTesting() {
        baseBackoffMs = 0L
        maxBackoffMs = 0L
        jitterMs = 0L
        limiter.resetForTestingBlocking()
    }

    /** Returns remaining cooldown in ms, or 0 if not in cooldown. */
    fun remainingCooldownMs(): Long = limiter.remainingCooldownMs()

    /**
     * Executes [block] under this policy:
     *
     * 1. If in 429 cooldown, immediately returns [onCooldown] result (non-blocking).
     * 2. Acquires rate limit permit via [AdaptiveRateLimiter.acquire] (non-blocking smoothing).
     * 3. Acquires the concurrency semaphore.
     * 4. Executes [block], applying retry logic if [retryable] is true.
     *
     * [block] must return a [PolicyResult] so the policy can inspect HTTP status codes.
     *
     * @param retryable Set to false for mutations (PUT/DELETE) — they are never retried.
     * @param onCooldown Factory for the result to return when the cooldown is still active.
     */
    suspend fun <T> withPolicy(
        retryable: Boolean = true,
        onCooldown: () -> T,
        block: suspend () -> PolicyResult<T>
    ): T {
        if (remainingCooldownMs() > 0L) return onCooldown()

        var attempt = 0
        var lastResult: PolicyResult<T>? = null

        while (true) {
            if (remainingCooldownMs() > 0L) return onCooldown()

            // Smooth burst traffic through adaptive rate limiter
            limiter.acquire()
            if (remainingCooldownMs() > 0L) return onCooldown()

            val result = try {
                semaphore.withPermit {
                    if (remainingCooldownMs() > 0L) return@withPermit null
                    block()
                } ?: return onCooldown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                PolicyResult.Failure(e)
            }

            lastResult = result

            when (result) {
                is PolicyResult.Success -> {
                    limiter.onSuccess()
                    return result.value
                }

                is PolicyResult.RateLimited -> {
                    limiter.onRateLimited(result.retryAfterMs)
                    return onCooldown()
                }

                is PolicyResult.Retryable -> {
                    if (!retryable || attempt >= maxRetries) break
                    attempt++
                    // Permit is released before delay so backoff does not starve other callers
                    if (baseBackoffMs > 0L) {
                        val backoff = min(baseBackoffMs * (1L shl (attempt - 1)), maxBackoffMs)
                        val jitter = if (jitterMs > 0L) Random.nextLong(-jitterMs, jitterMs + 1) else 0L
                        val delayMs = maxOf(0L, backoff + jitter)
                        if (delayMs > 0L) delay(delayMs)
                    }
                }

                is PolicyResult.Failure -> break
                is PolicyResult.NonRetryable -> {
                    limiter.onSuccess()
                    break
                }
            }
        }

        // Exhausted retries or non-retryable failure — extract value
        return when (val r = lastResult) {
            is PolicyResult.Failure -> throw r.throwable
            is PolicyResult.Retryable -> r.fallback
            is PolicyResult.NonRetryable -> r.value
            else -> onCooldown() // safety
        }
    }
}

/**
 * Sealed result type that [RequestPolicy] uses to decide retry/cooldown behaviour.
 *
 * Callers wrap their remote call outcome in one of these before returning from
 * the [RequestPolicy.withPolicy] block.
 */
sealed class PolicyResult<out T> {
    /** Request succeeded. */
    data class Success<T>(val value: T) : PolicyResult<T>()

    /**
     * HTTP 429 received. [retryAfterMs] is parsed from the `Retry-After` response
     * header (converted to milliseconds); use 0 if the header is absent.
     */
    data class RateLimited<T>(val retryAfterMs: Long) : PolicyResult<T>()

    /**
     * Transient failure (Timeout or HTTP 5xx) that is safe to retry.
     * [fallback] is returned when all retry attempts are exhausted.
     */
    data class Retryable<T>(val fallback: T, val cause: Throwable? = null) : PolicyResult<T>()

    /** Non-transient failure that must NOT be retried. Returns [value] to the caller. */
    data class NonRetryable<T>(val value: T) : PolicyResult<T>()

    /** Unexpected exception during the block — re-thrown after retries are exhausted. */
    data class Failure<T>(val throwable: Throwable) : PolicyResult<T>()
}

/**
 * Parses the `Retry-After` header value (seconds) and returns the equivalent milliseconds.
 * Returns 0 if the header is absent or unparseable.
 */
fun parseRetryAfterMs(headerValue: String?): Long {
    if (headerValue.isNullOrBlank()) return 0L
    return (headerValue.trim().toLongOrNull()?.let { it * 1000L }) ?: 0L
}
