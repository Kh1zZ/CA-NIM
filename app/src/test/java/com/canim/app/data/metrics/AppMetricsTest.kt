package com.canim.app.data.metrics

import com.canim.app.data.remote.AniListMetrics
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppMetricsTest {

    @Before
    fun setUp() {
        AppMetrics.reset()
    }

    @Test
    fun testInitialMetricsAreZero() {
        val snapshot = AppMetrics.getSnapshot()
        assertEquals(0L, snapshot.totalRequests)
        assertEquals(0L, snapshot.cacheHits)
        assertEquals(0L, snapshot.cacheMisses)
        assertEquals(0.0, snapshot.cacheHitRate, 0.001)
        assertEquals(0L, snapshot.deduplicationCount)
        assertTrue(snapshot.requestsByHost.isEmpty())
        assertTrue(snapshot.requestsByOperation.isEmpty())
        assertTrue(snapshot.retriesByHost.isEmpty())
        assertTrue(snapshot.rateLimitsByHost.isEmpty())
        assertTrue(snapshot.timeoutsByHost.isEmpty())
        assertTrue(snapshot.http5xxByHost.isEmpty())
    }

    @Test
    fun testRecordRequestAndOperations() {
        AppMetrics.recordRequest("anilist", "SearchMedia")
        AppMetrics.recordRequest("anilist", "SearchMedia")
        AppMetrics.recordRequest("anilist", "GetMediaDetail")
        AppMetrics.recordRequest("myanimelist", "getUserProfile")
        AppMetrics.recordRequest("github", "checkLatestRelease")

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(5L, snapshot.totalRequests)
        assertEquals(3L, snapshot.requestsByHost["anilist"])
        assertEquals(1L, snapshot.requestsByHost["myanimelist"])
        assertEquals(1L, snapshot.requestsByHost["github"])

        assertEquals(2L, snapshot.requestsByOperation["SearchMedia"])
        assertEquals(1L, snapshot.requestsByOperation["GetMediaDetail"])
        assertEquals(1L, snapshot.requestsByOperation["getUserProfile"])
        assertEquals(1L, snapshot.requestsByOperation["checkLatestRelease"])
    }

    @Test
    fun testCacheHitMissAndHitRate() {
        AppMetrics.recordCacheHit("metadata")
        AppMetrics.recordCacheHit("metadata")
        AppMetrics.recordCacheHit("metadata")
        AppMetrics.recordCacheMiss("metadata")

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(3L, snapshot.cacheHits)
        assertEquals(1L, snapshot.cacheMisses)
        assertEquals(0.75, snapshot.cacheHitRate, 0.001)
    }

    @Test
    fun testDeduplicationAndRetries() {
        AppMetrics.recordDeduplication("anilist", "SearchMedia")
        AppMetrics.recordDeduplication("anilist", "SearchMedia")
        AppMetrics.recordRetry("anilist", "SearchMedia")
        AppMetrics.recordRetry("myanimelist", "updateAnimeStatus")
        AppMetrics.recordRetry("local_sync", "UPDATE")

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(2L, snapshot.deduplicationCount)
        assertEquals(1L, snapshot.retriesByHost["anilist"])
        assertEquals(1L, snapshot.retriesByHost["myanimelist"])
        assertEquals(1L, snapshot.retriesByHost["local_sync"])
    }

    @Test
    fun testErrorsAndRateLimits() {
        AppMetrics.recordRateLimit("myanimelist", "http_429")
        AppMetrics.recordRateLimit("anilist", "graphql")
        AppMetrics.recordTimeout("anilist", "SearchMedia")
        AppMetrics.recordHttp5xx("myanimelist", "getUserAnimeList", 503)

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.rateLimitsByHost["myanimelist"])
        assertEquals(1L, snapshot.rateLimitsByHost["anilist"])
        assertEquals(1L, snapshot.timeoutsByHost["anilist"])
        assertEquals(1L, snapshot.http5xxByHost["myanimelist"])
    }

    @Test
    fun testLatencyStatistics() {
        AppMetrics.recordLatency("anilist", "SearchMedia", 100L)
        AppMetrics.recordLatency("anilist", "SearchMedia", 200L)
        AppMetrics.recordLatency("anilist", "SearchMedia", 300L)

        val snapshot = AppMetrics.getSnapshot()
        val hostStats = snapshot.latencyByHost["anilist"]
        assertNotNull(hostStats)
        assertEquals(3L, hostStats!!.count)
        assertEquals(600L, hostStats.totalMs)
        assertEquals(100L, hostStats.minMs)
        assertEquals(300L, hostStats.maxMs)
        assertEquals(200.0, hostStats.avgMs, 0.001)

        val opStats = snapshot.latencyByOperation["SearchMedia"]
        assertNotNull(opStats)
        assertEquals(3L, opStats!!.count)
        assertEquals(200.0, opStats.avgMs, 0.001)
    }

    @Test
    fun testSanitizeLabelStripsQueriesAndPII() {
        assertEquals("graphql.anilist.co", AppMetrics.sanitizeLabel("https://graphql.anilist.co?query=secret"))
        assertEquals("userProfile", AppMetrics.sanitizeLabel("userProfile?token=xyz123"))
        assertEquals("safe_operation-1", AppMetrics.sanitizeLabel("safe_operation-1#hash"))
        assertEquals("unknown", AppMetrics.sanitizeLabel(""))
    }

    @Test
    fun testFormattedSummaryContainsSanitizedData() {
        AppMetrics.recordRequest("anilist", "SearchMedia")
        AppMetrics.recordCacheHit("metadata")
        AppMetrics.recordLatency("anilist", "SearchMedia", 150L)

        val summary = AppMetrics.getDebugSummary()
        assertTrue(summary.contains("CA'NIM System Observability Snapshot"))
        assertTrue(summary.contains("Total HTTP/GraphQL Requests: 1"))
        assertTrue(summary.contains("anilist=1"))
        assertTrue(summary.contains("Hits=1"))
        assertTrue(summary.contains("150"))
    }

    @Test
    fun testAniListMetricsSynchronization() {
        AniListMetrics.recordRequest()
        AniListMetrics.recordDuplicateInFlight()
        AniListMetrics.recordCacheHit()
        AniListMetrics.recordCacheMiss()
        AniListMetrics.recordTimeout()
        AniListMetrics.recordRateLimit()
        AniListMetrics.recordHttp5xx()
        AniListMetrics.recordRetry()

        assertEquals(1L, AniListMetrics.requestCount)
        assertEquals(1L, AniListMetrics.duplicateInFlightCount)
        assertEquals(1L, AniListMetrics.cacheHitCount)
        assertEquals(1L, AniListMetrics.cacheMissCount)
        assertEquals(1L, AniListMetrics.timeoutCount)
        assertEquals(1L, AniListMetrics.rateLimitCount)
        assertEquals(1L, AniListMetrics.http5xxCount)
        assertEquals(1L, AniListMetrics.retryCount)

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.totalRequests)
        assertEquals(1L, snapshot.deduplicationCount)
        assertEquals(1L, snapshot.cacheHits)
        assertEquals(1L, snapshot.cacheMisses)
        assertEquals(1L, snapshot.timeoutsByHost["anilist"])
        assertEquals(1L, snapshot.rateLimitsByHost["anilist"])
        assertEquals(1L, snapshot.http5xxByHost["anilist"])
        assertEquals(1L, snapshot.retriesByHost["anilist"])

        AniListMetrics.reset()
        assertEquals(0L, AniListMetrics.requestCount)
        assertEquals(0L, AppMetrics.getSnapshot().totalRequests)
    }

    @Test
    fun testConcurrentRecordingThreadSafety() {
        val threadCount = 8
        val iterationsPerThread = 500
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    for (j in 0 until iterationsPerThread) {
                        AppMetrics.recordRequest("concurrent_host", "testOp")
                        AppMetrics.recordCacheHit("test")
                        AppMetrics.recordLatency("concurrent_host", "testOp", (j % 50).toLong())
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        val snapshot = AppMetrics.getSnapshot()
        val expectedTotal = (threadCount * iterationsPerThread).toLong()
        assertEquals(expectedTotal, snapshot.totalRequests)
        assertEquals(expectedTotal, snapshot.cacheHits)
        val stats = snapshot.latencyByHost["concurrent_host"]
        assertNotNull(stats)
        assertEquals(expectedTotal, stats!!.count)
        assertEquals(0L, stats.minMs)
        assertEquals(49L, stats.maxMs)
    }
}
