package com.canim.app.data.remote

import com.canim.app.data.metrics.AppMetrics
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * Transient UI notification events emitted by [AdaptiveRateLimiter].
 * These events are informational — they are NOT errors and MUST NOT be displayed as errors.
 *
 * Anti-spam: each event type is debounced to max once per [NOTIFICATION_DEBOUNCE_MS] per host.
 */
sealed interface LimiterEvent {
    val host: String

    /** Emitted when token bucket is exhausted and acquire() must wait. */
    data class Throttled(override val host: String, val waitMs: Long) : LimiterEvent

    /** Emitted when a 429 response arms a cooldown / backoff window. */
    data class CooldownStarted(override val host: String, val durationMs: Long) : LimiterEvent

    /** Emitted when a retryable error triggers a retry attempt (timeout / 5xx). */
    data class Retrying(override val host: String) : LimiterEvent

    /** Emitted when consecutive429Count drops back to 0 (sustained successes). */
    data class Recovered(override val host: String) : LimiterEvent
}

private const val NOTIFICATION_DEBOUNCE_MS = 30_000L

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
    var burstCapacity: Int = 10,
    var refillIntervalMs: Long = 700L,
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

    /**
     * Hot stream of transient [LimiterEvent] notifications for UI consumption.
     * Collect in ViewModel; do NOT show as error dialogs.
     */
    private val _eventFlow = MutableSharedFlow<LimiterEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val eventFlow: SharedFlow<LimiterEvent> = _eventFlow.asSharedFlow()

    /** Tracks last emit timestamp per event-type key for anti-spam debounce. */
    private val lastNotifiedMs = mutableMapOf<String, Long>()

    /** Emits [event] only if the debounce window for this event type has elapsed. */
    private fun tryEmitDebounced(event: LimiterEvent) {
        val key = when (event) {
            is LimiterEvent.Throttled      -> "throttled"
            is LimiterEvent.CooldownStarted -> "cooldown"
            is LimiterEvent.Retrying        -> "retrying"
            is LimiterEvent.Recovered       -> "recovered"
        }
        val now = clock()
        val last = lastNotifiedMs[key] ?: 0L
        if (event is LimiterEvent.CooldownStarted || event is LimiterEvent.Recovered || now - last >= NOTIFICATION_DEBOUNCE_MS) {
            lastNotifiedMs[key] = now
            _eventFlow.tryEmit(event)
        }
    }

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
            tryEmitDebounced(LimiterEvent.Throttled(host, waitMs))
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
     * Updates limiter internal state and pacing dynamically based on response headers
     * (e.g. AniList GraphQL `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After`).
     *
     * Pacing strategy:
     * - If [remaining] <= 3 (critical window), drain available tokens and activate proactive pacing
     *   for [resetTimestampSeconds] or up to 5s to prevent hitting HTTP 429.
     * - If [remaining] <= 10 (warning window), clamp tokens to smoothly pace before starvation.
     * - If [retryAfterMs] > 0, arms backoff cooldown.
     */
    suspend fun updateFromHeaders(
        remaining: Int? = null,
        resetTimestampSeconds: Long? = null,
        retryAfterMs: Long? = null
    ) {
        val cooldownToRecord = mutex.withLock {
            if (retryAfterMs != null && retryAfterMs > 0L) {
                consecutive429Count++
                consecutiveSuccessCount = 0
                availableTokens = 0.0
                cooldownUntilMs = clock() + retryAfterMs
                retryAfterMs
            } else if (remaining != null) {
                if (remaining <= 3) {
                    availableTokens = 0.0
                    val now = clock()
                    val paceDuration = if (resetTimestampSeconds != null) {
                        val resetMs = resetTimestampSeconds * 1000L
                        (resetMs - now).coerceIn(1_000L, 5_000L)
                    } else {
                        2_000L
                    }
                    if (cooldownUntilMs < now + paceDuration) {
                        cooldownUntilMs = now + paceDuration
                    }
                    AppMetrics.recordLimiterThrottled(host)
                    paceDuration
                } else if (remaining <= 10) {
                    if (availableTokens > 2.0) {
                        availableTokens = 2.0
                    }
                    0L
                } else {
                    0L
                }
            } else {
                0L
            }
        }
        if (cooldownToRecord > 0L) {
            AppMetrics.recordCooldownEvent(host, cooldownToRecord)
        }
    }

    /**
     * Synchronous variant of updateFromHeaders for non-suspending contexts (e.g. OkHttp interceptors).
     */
    fun updateFromHeadersBlocking(
        remaining: Int? = null,
        resetTimestampSeconds: Long? = null,
        retryAfterMs: Long? = null
    ) {
        val cooldownToRecord: Long
        synchronized(this) {
            if (retryAfterMs != null && retryAfterMs > 0L) {
                consecutive429Count++
                consecutiveSuccessCount = 0
                availableTokens = 0.0
                cooldownUntilMs = clock() + retryAfterMs
                cooldownToRecord = retryAfterMs
            } else if (remaining != null) {
                if (remaining <= 3) {
                    availableTokens = 0.0
                    val now = clock()
                    val paceDuration = if (resetTimestampSeconds != null) {
                        val resetMs = resetTimestampSeconds * 1000L
                        (resetMs - now).coerceIn(1_000L, 5_000L)
                    } else {
                        2_000L
                    }
                    if (cooldownUntilMs < now + paceDuration) {
                        cooldownUntilMs = now + paceDuration
                    }
                    AppMetrics.recordLimiterThrottled(host)
                    cooldownToRecord = paceDuration
                } else if (remaining <= 10) {
                    if (availableTokens > 2.0) {
                        availableTokens = 2.0
                    }
                    cooldownToRecord = 0L
                } else {
                    cooldownToRecord = 0L
                }
            } else {
                cooldownToRecord = 0L
            }
        }
        if (cooldownToRecord > 0L) {
            AppMetrics.recordCooldownEvent(host, cooldownToRecord)
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
        tryEmitDebounced(LimiterEvent.CooldownStarted(host, duration))
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
        // tryEmitDebounced is safe to call from blocking context (no suspend needed internally)
        tryEmitDebounced(LimiterEvent.CooldownStarted(host, duration))
    }

    /**
     * Called upon successful request completion (2xx).
     * Sustained successes gradually reset backoff multiplier to baseline.
     * Emits [LimiterEvent.Recovered] when the host is fully recovered (consecutive429Count → 0).
     */
    suspend fun onSuccess() {
        val recovered = mutex.withLock {
            if (consecutive429Count > 0) {
                consecutiveSuccessCount++
                if (consecutiveSuccessCount >= recoverySuccessThreshold) {
                    consecutive429Count = maxOf(0, consecutive429Count - 1)
                    consecutiveSuccessCount = 0
                    consecutive429Count == 0 // true = fully recovered
                } else false
            } else false
        }
        if (recovered) tryEmitDebounced(LimiterEvent.Recovered(host))
    }

    /**
     * Synchronous variant of onSuccess for OkHttp interceptors.
     */
    fun onSuccessBlocking() {
        val recovered: Boolean
        synchronized(this) {
            recovered = if (consecutive429Count > 0) {
                consecutiveSuccessCount++
                if (consecutiveSuccessCount >= recoverySuccessThreshold) {
                    consecutive429Count = maxOf(0, consecutive429Count - 1)
                    consecutiveSuccessCount = 0
                    consecutive429Count == 0
                } else false
            } else false
        }
        if (recovered) tryEmitDebounced(LimiterEvent.Recovered(host))
    }

    /**
     * Called by sibling networking classes (e.g. [MalApiPolicyWrapper], [AniListApolloClient])
     * to emit a transient [LimiterEvent.Retrying] notification.
     * Internal to the `data.remote` package.
     */
    internal fun tryEmitRetrying() {
        tryEmitDebounced(LimiterEvent.Retrying(host))
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
        lastNotifiedMs.clear()
    }

    fun resetForTestingBlocking() {
        synchronized(this) {
            availableTokens = burstCapacity.toDouble()
            lastRefillTimeMs = clock()
            cooldownUntilMs = 0L
            consecutive429Count = 0
            consecutiveSuccessCount = 0
            lastNotifiedMs.clear()
        }
    }

    fun configureForTesting(burstCapacity: Int? = null, refillIntervalMs: Long? = null) {
        synchronized(this) {
            burstCapacity?.let { this.burstCapacity = it }
            refillIntervalMs?.let { this.refillIntervalMs = it }
            resetForTestingBlocking()
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
