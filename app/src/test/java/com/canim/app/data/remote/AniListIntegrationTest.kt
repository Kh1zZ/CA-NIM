package com.canim.app.data.remote

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaRef
import com.canim.app.data.model.MediaRelationItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.CanimRepository
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.data.resolver.MediaResolver
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val anilistJsonMediaType: okhttp3.MediaType = "application/json; charset=utf-8".toMediaType()

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AniListIntegrationTest {

    private class MockInterceptor : Interceptor {
        var handler: ((Request, Int) -> Response)? = null
        val callCount = AtomicInteger(0)
        val capturedRequests = CopyOnWriteArrayList<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val currentCount = callCount.incrementAndGet()
            capturedRequests.add(req)

            val currentHandler = handler
            if (currentHandler != null) {
                return currentHandler(req, currentCount)
            }

            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody(anilistJsonMediaType))
                .build()
        }
    }

    private lateinit var mockInterceptor: MockInterceptor
    private lateinit var testHttpClient: OkHttpClient

    @Before
    fun setUp() {
        mockInterceptor = MockInterceptor()
        testHttpClient = OkHttpClient.Builder()
            .addInterceptor(mockInterceptor)
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .writeTimeout(1, TimeUnit.SECONDS)
            .build()

        AniListClient.setClientForTesting(testHttpClient)
        AniListMetrics.reset()
        CacheManager.clearIdMappings()
        CacheManager.clearMetadataCache()
        CacheManager.clearNegativeCache()
    }

    @After
    fun tearDown() {
        AniListClient.setClientForTesting(null)
        AniListMetrics.reset()
        CacheManager.clearIdMappings()
        CacheManager.clearMetadataCache()
        CacheManager.clearNegativeCache()
    }

    // --- 1. Negative-cache correctness ---
    @Test
    fun test1_NegativeCacheCorrectness() = runBlocking {
        // Case A: 404 / NotFound -> Must write negative cache
        val notFoundJson = """
            {
              "data": {
                "Media": null
              },
              "errors": [
                {"message": "Not Found.", "status": 404}
              ]
            }
        """.trimIndent()

        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(notFoundJson.toResponseBody(anilistJsonMediaType))
                .build()
        }

        val resolvedMissing = MediaResolver.resolveAniListIdForMalId(999999, MediaType.ANIME)
        assertNull(resolvedMissing)
        assertTrue("Verified NotFound MUST be placed in negative cache", CacheManager.isNegativeCached("neg_mal_999999"))

        // Case B: Network failure / Timeout -> MUST NEVER write negative cache
        mockInterceptor.handler = { _, _ ->
            throw SocketTimeoutException("Read timed out")
        }

        val resolvedTimeout = MediaResolver.resolveAniListIdForMalId(888888, MediaType.ANIME)
        assertNull(resolvedTimeout)
        assertFalse("Timeout MUST NOT be placed in negative cache", CacheManager.isNegativeCached("neg_mal_888888"))

        // Case C: Rate limit 429 -> MUST NEVER write negative cache
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .header("Retry-After", "1")
                .body("{}".toResponseBody(anilistJsonMediaType))
                .build()
        }

        val resolvedRateLimit = MediaResolver.resolveAniListIdForMalId(777777, MediaType.ANIME)
        assertNull(resolvedRateLimit)
        assertFalse("RateLimited 429 MUST NOT be placed in negative cache", CacheManager.isNegativeCached("neg_mal_777777"))
    }

    // --- 2. MAL ID vs AniList ID namespace separation ---
    @Test
    fun test2_MalIdVsAniListIdNamespaceSeparation() {
        val mediaRef = MediaRef(malId = 52991, anilistId = 16498)
        assertEquals(52991, mediaRef.malId)
        assertEquals(16498, mediaRef.anilistId)
        assertNotEquals(mediaRef.malId, mediaRef.anilistId)

        // When only MAL ID exists, anilistId MUST NOT be defaulted to malId
        val malOnlyRef = MediaRef(malId = 52991, anilistId = null)
        assertNull(malOnlyRef.anilistId)
        assertNotEquals(52991, malOnlyRef.anilistId)

        // MediaItem with only MAL ID
        val item = MediaItem(
            malId = 52991,
            anilistId = null,
            title = "Frieren",
            imageUrl = "https://example.com/frieren.jpg",
            type = MediaType.ANIME
        )
        assertNull(item.anilistId)
        assertEquals(52991, item.malId)

        // MediaRelationItem MAL separation
        val relationItem = MediaRelationItem(
            id = 52991,
            malId = 52991,
            title = "Frieren Prequel",
            imageUrl = null,
            relationType = "PREQUEL",
            type = MediaType.ANIME
        )
        assertEquals(52991, relationItem.malId)
    }

    // --- 3. In-flight request deduplication ---
    @Test
    fun test3_InFlightRequestDeduplication() = runBlocking {
        val networkCallCount = AtomicInteger(0)

        val results = coroutineScope {
            (1..10).map {
                async(Dispatchers.IO) {
                    AniListClient.deduplicateInFlight("test_dedup_key") {
                        networkCallCount.incrementAndGet()
                        delay(150L)
                        "result_payload"
                    }
                }
            }.awaitAll()
        }

        assertEquals(10, results.size)
        assertTrue(results.all { it == "result_payload" })
        assertEquals("Network block must be executed exactly once across concurrent callers", 1, networkCallCount.get())
        assertEquals(9L, AniListMetrics.duplicateInFlightCount)
    }

    // --- 4. In-flight request cleanup after failure ---
    @Test
    fun test4_InFlightRequestCleanupAfterFailure() = runBlocking {
        var failureOccurred = false
        try {
            AniListClient.deduplicateInFlight<String>("failing_key") {
                throw IllegalStateException("Transient network drop")
            }
        } catch (_: Exception) {
            failureOccurred = true
        }

        assertTrue(failureOccurred)
        assertEquals("In-flight table must be cleaned up on failure", 0, AniListClient.getInFlightCount())

        // Ensure new request with same key executes immediately without being blocked
        val result = AniListClient.deduplicateInFlight("failing_key") {
            "recovered_value"
        }
        assertEquals("recovered_value", result)
    }

    // --- 5. In-flight request cleanup after cancellation ---
    @Test
    fun test5_InFlightRequestCleanupAfterCancellation() = runBlocking {
        val job = launch(Dispatchers.Default) {
            AniListClient.deduplicateInFlight("cancel_key") {
                delay(10000L)
            }
        }

        delay(30L)
        job.cancelAndJoin()

        assertEquals("In-flight table must be cleaned up on cancellation", 0, AniListClient.getInFlightCount())

        // Ensure key is immediately reusable
        val followUp = AniListClient.deduplicateInFlight("cancel_key") {
            "immediate_success"
        }
        assertEquals("immediate_success", followUp)
    }

    // --- 6. Cache hit behavior ---
    @Test
    fun test6_CacheHitBehavior() = runBlocking {
        CacheManager.putIdMapping(malId = 52991, aniListId = 16498)

        val result = AniListClient.resolveIdMal(52991, MediaType.ANIME)

        assertTrue(result is AniListResult.Success)
        assertEquals(16498, (result as AniListResult.Success).data)
        assertEquals("Cache hit must increment cacheHitCount", 1L, AniListMetrics.cacheHitCount)
        assertEquals("No network calls should occur on cache hit", 0, mockInterceptor.callCount.get())
    }

    // --- 7. Cache miss behavior ---
    @Test
    fun test7_CacheMissBehavior() = runBlocking {
        val resolveSuccessJson = """
            {
              "data": {
                "Media": {
                  "id": 16498,
                  "idMal": 52991
                }
              }
            }
        """.trimIndent()

        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(resolveSuccessJson.toResponseBody(anilistJsonMediaType))
                .build()
        }

        val result = AniListClient.resolveIdMal(52991, MediaType.ANIME)

        assertTrue(result is AniListResult.Success)
        assertEquals(16498, (result as AniListResult.Success).data)
        assertEquals("Cache miss must increment cacheMissCount", 1L, AniListMetrics.cacheMissCount)
        assertEquals("Network call must be triggered on cache miss", 1, mockInterceptor.callCount.get())
        // And now mapping is cached
        assertEquals(16498, CacheManager.getAniListIdForMalId(52991))
    }

    // --- 8. Health check transient failure handling ---
    @Test
    fun test8_HealthCheckTransientFailureHandling() = runBlocking {
        // Transient failure: 1st ping fails (503), 2nd ping succeeds (200)
        mockInterceptor.handler = { req, count ->
            if (count == 1) {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Temporary Gateway Error")
                    .body("Gateway timeout".toResponseBody("text/plain".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"data": {"Media": {"id": 1}}}""".toResponseBody(anilistJsonMediaType))
                    .build()
            }
        }

        val app = RuntimeEnvironment.getApplication()
        val storage = MalSecureStorage(app)
        val malAuthManager = MalAuthManager(storage)
        val repository = CanimRepository(malAuthManager)

        // Repository should retry and tolerate the 1st transient drop
        val isUnavailable = repository.isAniListUnavailable()
        assertFalse("AniList should be available after surviving transient drop", isUnavailable)

        // Persistent outage: All pings fail
        mockInterceptor.callCount.set(0)
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Service Unavailable")
                .body("Down".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val persistentOutage = repository.isAniListUnavailable()
        assertTrue("AniList should be declared unavailable during persistent outage", persistentOutage)
    }
}
