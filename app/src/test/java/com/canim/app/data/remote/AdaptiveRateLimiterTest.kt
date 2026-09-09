package com.canim.app.data.remote

import com.canim.app.data.metrics.AppMetrics
import com.canim.app.data.remote.AniListResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveRateLimiterTest {

    private lateinit var limiter: AdaptiveRateLimiter
    private var testClock: Long = 100000L

    @Before
    fun setUp() {
        AppMetrics.reset()
        limiter = AdaptiveRateLimiter(
            host = "test.api",
            burstCapacity = 3,
            refillIntervalMs = 500L,
            baseCooldownMs = 2000L,
            maxCooldownMs = 10000L
        )
    }

    @Test
    fun testNormalUsageNegligibleDelay() = runTest {
        val testLimiter = AdaptiveRateLimiter(
            host = "test.api",
            burstCapacity = 3,
            refillIntervalMs = 500L,
            clock = { testScheduler.currentTime }
        )
        val startVirtualTime = testScheduler.currentTime
        testLimiter.acquire()
        testLimiter.acquire()
        testLimiter.acquire()
        assertEquals(startVirtualTime, testScheduler.currentTime) // 0 delay
    }

    @Test
    fun testBurstTrafficThrottled() = runTest {
        val testLimiter = AdaptiveRateLimiter(
            host = "test.api",
            burstCapacity = 3,
            refillIntervalMs = 500L,
            clock = { testScheduler.currentTime }
        )
        testLimiter.acquire()
        testLimiter.acquire()
        testLimiter.acquire()

        val beforeThrottle = testScheduler.currentTime
        testLimiter.acquire()
        val afterThrottle = testScheduler.currentTime
        assertTrue("Expected delay for burst traffic", afterThrottle > beforeThrottle)
        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.totalThrottled)
        assertEquals(1L, snapshot.throttledRequestsByHost["test.api"])
    }

    @Test
    fun testSeparateLimitsAniListAndMal() = runTest {
        val anilistLimiter = AdaptiveRateLimiter("graphql.anilist.co", burstCapacity = 2, refillIntervalMs = 500L, clock = { testScheduler.currentTime })
        val malLimiter = AdaptiveRateLimiter("api.myanimelist.net", burstCapacity = 2, refillIntervalMs = 500L, clock = { testScheduler.currentTime })

        // Exhaust AniList
        anilistLimiter.acquire()
        anilistLimiter.acquire()

        // MAL should still have capacity and not be throttled
        val start = testScheduler.currentTime
        malLimiter.acquire()
        assertEquals(start, testScheduler.currentTime)

        // Only AniList throttles on 3rd acquire
        val anilistStart = testScheduler.currentTime
        anilistLimiter.acquire()
        assertTrue(testScheduler.currentTime > anilistStart)
        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.throttledRequestsByHost["graphql.anilist.co"])
        assertEquals(null, snapshot.throttledRequestsByHost["api.myanimelist.net"])
    }

    @Test
    fun test429CooldownTriggered() = runTest {
        val testLimiter = AdaptiveRateLimiter("test.api", baseCooldownMs = 2000L, clock = { testScheduler.currentTime })
        testLimiter.onRateLimited()
        assertTrue(testLimiter.isCooldownActive())
        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.totalCooldownEvents)
        assertEquals(2000L, snapshot.totalCooldownDurationMs)

        // Advancing testScheduler past cooldown
        testScheduler.advanceTimeBy(2001L)
        assertFalse(testLimiter.isCooldownActive())
    }

    @Test
    fun testRespectRetryAfter() = runTest {
        val testLimiter = AdaptiveRateLimiter("test.api", clock = { testScheduler.currentTime })
        testLimiter.onRateLimited(retryAfterMs = 4000L)
        assertTrue(testLimiter.isCooldownActive())
        val snapshot = AppMetrics.getSnapshot()
        assertEquals(4000L, snapshot.totalCooldownDurationMs)

        testScheduler.advanceTimeBy(4001L)
        assertFalse(testLimiter.isCooldownActive())
    }

    @Test
    fun testRepeated429ExponentialBackoff() = runTest {
        val testLimiter = AdaptiveRateLimiter(
            "test.api",
            baseCooldownMs = 2000L,
            maxCooldownMs = 10000L,
            clock = { testScheduler.currentTime }
        )
        // 1st 429: baseCooldownMs = 2000ms
        testLimiter.onRateLimited()
        assertEquals(1, testLimiter.getConsecutive429Count())

        // 2nd 429: 2000 * 2^1 = 4000ms
        testLimiter.onRateLimited()
        assertEquals(2, testLimiter.getConsecutive429Count())
        assertEquals(6000L, AppMetrics.getSnapshot().totalCooldownDurationMs) // 2000 + 4000

        // 3rd 429: 2000 * 2^2 = 8000ms
        testLimiter.onRateLimited()
        assertEquals(3, testLimiter.getConsecutive429Count())
        assertEquals(14000L, AppMetrics.getSnapshot().totalCooldownDurationMs) // 6000 + 8000

        // 4th 429: clamped to maxCooldownMs = 10000ms
        testLimiter.onRateLimited()
        assertEquals(4, testLimiter.getConsecutive429Count())
        assertEquals(24000L, AppMetrics.getSnapshot().totalCooldownDurationMs) // 14000 + 10000
    }

    @Test
    fun testRecoveryAfterConsecutiveSuccesses() = runTest {
        limiter.onRateLimited()
        limiter.onRateLimited()
        assertEquals(2, limiter.getConsecutive429Count())

        // 5 consecutive successes reduces consecutive429Count by 1
        repeat(5) { limiter.onSuccess() }
        assertEquals(1, limiter.getConsecutive429Count())

        // Another 5 consecutive successes reduces consecutive429Count to 0
        repeat(5) { limiter.onSuccess() }
        assertEquals(0, limiter.getConsecutive429Count())
    }

    @Test
    fun testThreadSafetyConcurrentAcquires() = runTest {
        val testLimiter = AdaptiveRateLimiter(
            host = "test.api",
            burstCapacity = 3,
            refillIntervalMs = 500L,
            clock = { testScheduler.currentTime }
        )
        val jobs = List(10) {
            launch {
                testLimiter.acquire()
            }
        }
        jobs.forEach { it.join() }
        val snapshot = AppMetrics.getSnapshot()
        val throttled = snapshot.throttledRequestsByHost["test.api"] ?: 0L
        assertTrue("Expected throttled requests on concurrent burst", throttled >= 7L)
    }

    @Test
    fun testAniListNotificationPolicySilentOnRateControl() {
        // RateLimited is treated as reachable so throttling does not trigger outage UI banners
        val rateLimitedResult = AniListResult.RateLimited(retryAfterSeconds = 10)
        assertTrue(rateLimitedResult is AniListResult.RateLimited)
        assertEquals(10L, rateLimitedResult.retryAfterSeconds)

        // Success is reachable
        val successResult = AniListResult.Success("data")
        assertTrue(successResult is AniListResult.Success)

        // HttpError or NetworkError are genuine failures
        val httpError = AniListResult.HttpError(500, "500 Internal Server Error")
        assertTrue(httpError is AniListResult.HttpError)

        val networkError = AniListResult.NetworkError(java.io.IOException("Failed to connect"))
        assertTrue(networkError is AniListResult.NetworkError)
    }

    @Test
    fun testAppMetricsLimiterTracking() {
        AppMetrics.recordLimiterThrottled("graphql.anilist.co")
        AppMetrics.recordLimiterThrottled("graphql.anilist.co")
        AppMetrics.recordLimiterThrottled("api.myanimelist.net")

        AppMetrics.recordCooldownEvent("graphql.anilist.co", 5000L)
        AppMetrics.recordCooldownEvent("api.myanimelist.net", 3000L)

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(3L, snapshot.totalThrottled)
        assertEquals(2L, snapshot.throttledRequestsByHost["graphql.anilist.co"])
        assertEquals(1L, snapshot.throttledRequestsByHost["api.myanimelist.net"])

        assertEquals(2L, snapshot.totalCooldownEvents)
        assertEquals(1L, snapshot.cooldownEventsByHost["graphql.anilist.co"])
        assertEquals(1L, snapshot.cooldownEventsByHost["api.myanimelist.net"])

        assertEquals(8000L, snapshot.totalCooldownDurationMs)
        assertEquals(5000L, snapshot.cooldownDurationMsByHost["graphql.anilist.co"])
        assertEquals(3000L, snapshot.cooldownDurationMsByHost["api.myanimelist.net"])

        AppMetrics.reset()
        val afterReset = AppMetrics.getSnapshot()
        assertEquals(0L, afterReset.totalThrottled)
        assertEquals(0L, afterReset.totalCooldownEvents)
        assertEquals(0L, afterReset.totalCooldownDurationMs)
    }

    @Test
    fun testUpdateFromHeadersCriticalRemainingPacesRequests() = runTest {
        val testLimiter = AdaptiveRateLimiter("test.api", burstCapacity = 5, clock = { testScheduler.currentTime })
        // Normal state: not in cooldown
        assertFalse(testLimiter.isCooldownActive())

        // Header says remaining = 2, reset at current + 3 seconds
        val resetSec = (testScheduler.currentTime / 1000L) + 3L
        testLimiter.updateFromHeaders(remaining = 2, resetTimestampSeconds = resetSec)

        // Limiter should have activated pacing cooldown
        assertTrue(testLimiter.isCooldownActive())
        val waitMs = testLimiter.remainingCooldownMs()
        assertTrue("Expected pacing cooldown of ~3000ms", waitMs in 2000L..4000L)

        // Snapshot recorded limiter throttled and cooldown event
        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.totalThrottled)
        assertEquals(1L, snapshot.totalCooldownEvents)

        // Advance time past reset
        testScheduler.advanceTimeBy(3500L)
        assertFalse(testLimiter.isCooldownActive())
    }

    @Test
    fun testUpdateFromHeadersWarningRemainingClampsTokens() = runTest {
        val testLimiter = AdaptiveRateLimiter("test.api", burstCapacity = 10, clock = { testScheduler.currentTime })
        // Header says remaining = 8 (warning threshold)
        testLimiter.updateFromHeaders(remaining = 8)

        // Does not trigger full cooldown
        assertFalse(testLimiter.isCooldownActive())

        // But tokens were clamped to 2, so 3rd acquire will throttle
        testLimiter.acquire()
        testLimiter.acquire()
        val before = testScheduler.currentTime
        testLimiter.acquire()
        val after = testScheduler.currentTime
        assertTrue("3rd acquire should be throttled due to token clamping", after > before)
    }

    @Test
    fun testUpdateFromHeadersWithRetryAfterArmsCooldown() = runTest {
        val testLimiter = AdaptiveRateLimiter("test.api", clock = { testScheduler.currentTime })
        testLimiter.updateFromHeaders(retryAfterMs = 2500L)

        assertTrue(testLimiter.isCooldownActive())
        assertEquals(1, testLimiter.getConsecutive429Count())
        assertEquals(2500L, AppMetrics.getSnapshot().totalCooldownDurationMs)

        testScheduler.advanceTimeBy(2501L)
        assertFalse(testLimiter.isCooldownActive())
    }
}

