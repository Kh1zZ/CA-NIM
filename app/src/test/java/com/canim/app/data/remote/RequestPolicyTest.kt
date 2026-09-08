package com.canim.app.data.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic unit tests for [RequestPolicy].
 *
 * Tests cover:
 *  1. Concurrency limit — semaphore blocks beyond maxConcurrent simultaneous calls.
 *  2. 429 cooldown — armCooldown() blocks subsequent requests; clears after cooldown expires.
 *  3. Retry on Retryable — retries up to maxRetries with exponential backoff, returns fallback
 *     when exhausted.
 *  4. No retry on NonRetryable — single attempt only.
 *  5. CancellationException propagation — cancel is re-thrown through withPolicy.
 *  6. parseRetryAfterMs — correct parsing of header values.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestPolicyTest {

    private val testDispatcher = StandardTestDispatcher()

    /** Fake wall clock; starts at 0 and is advanced manually. */
    private var fakeNow = 0L

    private fun makePolicy(
        maxConcurrent: Int = 4,
        maxRetries: Int = 2,
        baseBackoffMs: Long = 0L,   // zero backoff so tests are fast
        maxBackoffMs: Long = 0L,
        jitterMs: Long = 0L
    ) = RequestPolicy(
        maxConcurrent = maxConcurrent,
        maxRetries = maxRetries,
        baseBackoffMs = baseBackoffMs,
        maxBackoffMs = maxBackoffMs,
        jitterMs = jitterMs,
        clock = { fakeNow }
    )

    @Before
    fun setUp() {
        fakeNow = 0L
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Concurrency limit
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `concurrency limit — semaphore allows maxConcurrent parallel executions`() = runTest(testDispatcher) {
        val policy = makePolicy(maxConcurrent = 3)
        val running = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)

        val jobs = (1..6).map {
            launch {
                policy.withPolicy(retryable = false, onCooldown = { PolicyResult.NonRetryable(Unit) }) {
                    val now = running.incrementAndGet()
                    maxObserved.updateAndGet { cur -> maxOf(cur, now) }
                    // Yield so other coroutines can interleave
                    kotlinx.coroutines.yield()
                    running.decrementAndGet()
                    PolicyResult.Success(Unit)
                }
            }
        }
        jobs.forEach { it.join() }

        // Semaphore limits concurrency; observed peak must be <= maxConcurrent
        assertTrue(
            "Peak concurrency ${maxObserved.get()} exceeded limit 3",
            maxObserved.get() <= 3
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. 429 cooldown
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `429 cooldown — armCooldown blocks immediately after arming`() = runTest(testDispatcher) {
        val policy = makePolicy()
        policy.armCooldown(retryAfterMs = 30_000L) // 30 s cooldown

        var cooldownHit = false
        policy.withPolicy(
            retryable = false,
            onCooldown = {
                cooldownHit = true
                PolicyResult.NonRetryable(Unit)
            }
        ) {
            // Should never reach here while cooldown is active
            PolicyResult.Success(Unit)
        }

        assertTrue("onCooldown should be invoked when cooldown is active", cooldownHit)
    }

    @Test
    fun `429 cooldown — clears after time advances past deadline`() = runTest(testDispatcher) {
        val policy = makePolicy()
        policy.armCooldown(retryAfterMs = 10_000L)

        // Advance clock past cooldown
        fakeNow = 10_001L

        var blockExecuted = false
        policy.withPolicy(retryable = false, onCooldown = { PolicyResult.NonRetryable(false) }) {
            blockExecuted = true
            PolicyResult.Success(true)
        }

        assertTrue("Block should execute after cooldown expires", blockExecuted)
    }

    @Test
    fun `429 cooldown — RateLimited result arms cooldown and returns onCooldown`() = runTest(testDispatcher) {
        val policy = makePolicy()

        var onCooldownCalled: Boolean
        policy.withPolicy(
            retryable = true,
            onCooldown = {
                onCooldownCalled = true
                PolicyResult.RateLimited<Unit>(0L)
            }
        ) {
            PolicyResult.RateLimited(5_000L) // simulate 429 with 5 s Retry-After
        }

        assertTrue("Cooldown should be armed after RateLimited result", policy.remainingCooldownMs() > 0)
        // Next call should hit cooldown
        onCooldownCalled = false
        policy.withPolicy(retryable = false, onCooldown = { onCooldownCalled = true; PolicyResult.NonRetryable(Unit) }) {
            PolicyResult.Success(Unit)
        }
        assertTrue("Second call should immediately hit cooldown", onCooldownCalled)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Retry on Retryable
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `retry — retries exactly maxRetries times then returns fallback`() = runTest(testDispatcher) {
        val policy = makePolicy(maxRetries = 2)
        val callCount = AtomicInteger(0)

        val result = policy.withPolicy(
            retryable = true,
            onCooldown = { PolicyResult.NonRetryable("cooldown") }
        ) {
            callCount.incrementAndGet()
            PolicyResult.Retryable(fallback = "fallback")
        }

        // 1 initial + 2 retries = 3 total calls
        assertEquals("Expected 3 total calls (1 + 2 retries)", 3, callCount.get())
        assertEquals("Expected fallback after exhausting retries", "fallback", result)
    }

    @Test
    fun `retry — succeeds on second attempt`() = runTest(testDispatcher) {
        val policy = makePolicy(maxRetries = 2)
        val callCount = AtomicInteger(0)

        val result = policy.withPolicy(
            retryable = true,
            onCooldown = { PolicyResult.NonRetryable("cooldown") }
        ) {
            val attempt = callCount.incrementAndGet()
            if (attempt == 1) {
                PolicyResult.Retryable(fallback = "fallback")
            } else {
                PolicyResult.Success("ok")
            }
        }

        assertEquals("Expected 2 total calls", 2, callCount.get())
        assertEquals("Expected success on retry", "ok", result)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. No retry on NonRetryable
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `no retry — NonRetryable returns immediately after single attempt`() = runTest(testDispatcher) {
        val policy = makePolicy(maxRetries = 2)
        val callCount = AtomicInteger(0)

        policy.withPolicy(retryable = true, onCooldown = { PolicyResult.NonRetryable(Unit) }) {
            callCount.incrementAndGet()
            PolicyResult.NonRetryable(Unit)
        }

        assertEquals("NonRetryable must not be retried", 1, callCount.get())
    }

    @Test
    fun `no retry — retryable=false skips retry even on Retryable result`() = runTest(testDispatcher) {
        val policy = makePolicy(maxRetries = 2)
        val callCount = AtomicInteger(0)

        policy.withPolicy(retryable = false, onCooldown = { PolicyResult.NonRetryable(Unit) }) {
            callCount.incrementAndGet()
            PolicyResult.Retryable(fallback = Unit)
        }

        assertEquals("retryable=false must not retry mutations", 1, callCount.get())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. CancellationException propagation
    // ──────────────────────────────────────────────────────────────────────────

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun `cancellation — CancellationException is re-thrown`() = runTest(testDispatcher) {
        val policy = makePolicy()

        policy.withPolicy(retryable = false, onCooldown = { PolicyResult.NonRetryable(Unit) }) {
            throw kotlinx.coroutines.CancellationException("test cancel")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. parseRetryAfterMs helper
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseRetryAfterMs — parses seconds correctly`() {
        assertEquals(30_000L, parseRetryAfterMs("30"))
        assertEquals(1_000L, parseRetryAfterMs("1"))
        assertEquals(120_000L, parseRetryAfterMs("120"))
    }

    @Test
    fun `parseRetryAfterMs — returns 0 for null or blank`() {
        assertEquals(0L, parseRetryAfterMs(null))
        assertEquals(0L, parseRetryAfterMs(""))
        assertEquals(0L, parseRetryAfterMs("  "))
    }

    @Test
    fun `parseRetryAfterMs — returns 0 for non-numeric`() {
        assertEquals(0L, parseRetryAfterMs("abc"))
        assertEquals(0L, parseRetryAfterMs("1.5"))
    }

    @Test
    fun `armCooldown uses minCooldownMs when retryAfterMs is 0`() = runTest(testDispatcher) {
        val policy = makePolicy()
        policy.armCooldown(retryAfterMs = 0L, minCooldownMs = 5_000L)

        assertTrue("Should be in cooldown when retryAfterMs=0 and minCooldownMs=5000", policy.remainingCooldownMs() > 0)
    }
}
