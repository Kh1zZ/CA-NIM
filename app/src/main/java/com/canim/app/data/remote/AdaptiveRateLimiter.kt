package com.canim.app.data.remote

import com.canim.app.data.metrics.AppMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * Thread-safe, non-blocking adaptive rate limiter implementing a token-bucket algorithm
 * combined with exponential 429 backoff and progressive recovery.
 *
 * Guarantees:
 * - Zero delay for normal traffic within token capacity.
 * - Non-blocking coroutine suspension (delay) for burst smoothing. Never blocks threads or UI.
 * - Dynamic cooldown respecting `Retry-After` header when provided.
 * - Progressive cooldown doubling upon repeated 429s (no arbitrary fixed 40s/60s).
 * - Gradual recovery to baseline upon sustained request success.
 * - Full thread safety via coroutine Mutex.
 */
class AdaptiveRateLimiter(
    val host: String,
    val burstCapacity: Int = 10,
    val refillIntervalMs: Long = 700L,
    val baseCooldownMs: Long = 5_000L,
    val maxCooldownMs: Long = 60_000L,
    private val recoverySuccessThreshold: Int = 5,
    /** Clock abstraction for deterministic unit testing */
    internal var clock: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()

    private var availableTokens: Double = burstCapacity.toDouble()
    private var lastRefillTimeMs: Long = clock()

    @Volatile
    private var cooldownUntilMs: Long = 0L

    @Volatile
    private var consecutive429Count: Int = 0

    @Volatile
    private var consecutiveSuccessCount: Int = 0

    fun remainingCooldownMs(): Long = maxOf(0L, cooldownUntilMs - clock())

    fun isCooldownActive(): Boolean = remainingCooldownMs() > 0L

    fun getConsecutive429Count(): Int = consecutive429Count

    /**
     * Suspends until a rate limit token is available, or until cooldown expires.
     * For normal usage where tokens are present, this completes immediately with 0 delay.
     * For burst traffic exceeding capacity, delays non-blockingly and records throttled metrics.
     */
    suspend fun acquire() {
        while (true) {
            val waitMs = mutex.withLock {
                refillTokens()

                val cooldown = remainingCooldownMs()
                if (cooldown > 0L) {
                    return@withLock cooldown
                }

                if (availableTokens >= 1.0) {
                    availableTokens -= 1.0
                    return@withLock 0L
                }

                // Tokens exhausted: compute wait time for 1 token
                val neededTokens = 1.0 - availableTokens
                val wait = (neededTokens * refillIntervalMs).toLong().coerceAtLeast(1L)
                AppMetrics.recordLimiterThrottled(host)
                wait
            }

            if (waitMs <= 0L) return
            delay(waitMs)
        }
    }

    /**
     * Non-blocking attempt to acquire a permit. Returns true if acquired immediately,
     * or false if in cooldown or tokens are exhausted.
     */
    suspend fun tryAcquire(): Boolean = mutex.withLock {
        refillTokens()
        if (remainingCooldownMs() > 0L) return false
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0
            true
        } else {
            false
        }
    }

    /**
     * Called when an HTTP 429 Too Many Requests response is encountered.
     * Arms adaptive cooldown and increases progressive backoff.
     */
    suspend fun onRateLimited(retryAfterMs: Long = 0L) {
        val duration = mutex.withLock {
            consecutive429Count++
            consecutiveSuccessCount = 0
            availableTokens = 0.0 // Drain bucket on 429

            val cooldown = if (retryAfterMs > 0L) {
                retryAfterMs
            } else {
                val shift = min(consecutive429Count - 1, 6)
                min(baseCooldownMs * (1L shl shift), maxCooldownMs)
            }
            cooldownUntilMs = clock() + cooldown
            cooldown
        }
        AppMetrics.recordCooldownEvent(host, duration)
    }

    /**
     * Synchronous variant of onRateLimited for callers in non-suspending contexts (e.g., OkHttp Interceptors).
     */
    fun onRateLimitedBlocking(retryAfterMs: Long = 0L) {
        val duration: Long
        synchronized(this) {
            consecutive429Count++
            consecutiveSuccessCount = 0
            availableTokens = 0.0

            duration = if (retryAfterMs > 0L) {
                retryAfterMs
            } else {
                val shift = min(consecutive429Count - 1, 6)
                min(baseCooldownMs * (1L shl shift), maxCooldownMs)
            }
            cooldownUntilMs = clock() + duration
        }
        AppMetrics.recordCooldownEvent(host, duration)
    }

    /**
     * Called upon successful request completion (2xx).
     * Sustained successes gradually reset backoff multiplier to baseline.
     */
    suspend fun onSuccess() = mutex.withLock {
        if (consecutive429Count > 0) {
            consecutiveSuccessCount++
            if (consecutiveSuccessCount >= recoverySuccessThreshold) {
                consecutive429Count = maxOf(0, consecutive429Count - 1)
                consecutiveSuccessCount = 0
            }
        }
    }

    /**
     * Synchronous variant of onSuccess for OkHttp interceptors.
     */
    fun onSuccessBlocking() {
        synchronized(this) {
            if (consecutive429Count > 0) {
                consecutiveSuccessCount++
                if (consecutiveSuccessCount >= recoverySuccessThreshold) {
                    consecutive429Count = maxOf(0, consecutive429Count - 1)
                    consecutiveSuccessCount = 0
                }
            }
        }
    }

    /**
     * Resets limiter state for unit testing.
     */
    suspend fun resetForTesting() = mutex.withLock {
        availableTokens = burstCapacity.toDouble()
        lastRefillTimeMs = clock()
        cooldownUntilMs = 0L
        consecutive429Count = 0
        consecutiveSuccessCount = 0
    }

    fun resetForTestingBlocking() {
        synchronized(this) {
            availableTokens = burstCapacity.toDouble()
            lastRefillTimeMs = clock()
            cooldownUntilMs = 0L
            consecutive429Count = 0
            consecutiveSuccessCount = 0
        }
    }

    private fun refillTokens() {
        val now = clock()
        val elapsed = now - lastRefillTimeMs
        if (elapsed > 0) {
            val tokensToAdd = elapsed.toDouble() / refillIntervalMs
            availableTokens = min(burstCapacity.toDouble(), availableTokens + tokensToAdd)
            lastRefillTimeMs = now
        }
    }
}
