package com.canim.app.data.remote

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.anilist.AniListApolloClient
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
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ApolloIdResolutionTest {

    private class MockInterceptor : Interceptor {
        var handler: ((Request, Int) -> Response)? = null
        val callCount = AtomicInteger(0)
        val capturedRequests = CopyOnWriteArrayList<Request>()
        val capturedBodies = CopyOnWriteArrayList<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val currentCount = callCount.incrementAndGet()
            capturedRequests.add(req)

            val body = req.body
            if (body != null) {
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                capturedBodies.add(buffer.readUtf8())
            } else {
                capturedBodies.add("")
            }

            val currentHandler = handler
            if (currentHandler != null) {
                return currentHandler(req, currentCount)
            }

            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": {"id": 16498, "idMal": 52991}}}""".toResponseBody(jsonMediaType))
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

        AniListApolloClient.setOkHttpClientForTesting(testHttpClient)
        AniListClient.setClientForTesting(testHttpClient)
        AniListMetrics.reset()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @After
    fun tearDown() {
        AniListApolloClient.setOkHttpClientForTesting(null)
        AniListClient.setClientForTesting(null)
        AniListMetrics.reset()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    // 1. MAL ID resolves to AniList ID
    @Test
    fun test1_MalIdResolvesToAniListId() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": {"id": 16498, "idMal": 52991}}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val anilistId = MediaResolver.resolveAniListIdForMalId(52991, MediaType.ANIME)
        assertEquals(16498, anilistId)
        assertEquals(16498, CacheManager.getAniListIdForMalId(52991))
        assertEquals(52991, CacheManager.getMalIdForAniListId(16498))

        // Verify request sent through Apollo with operationName ResolveMalId
        assertEquals(1, mockInterceptor.capturedBodies.size)
        assertTrue(mockInterceptor.capturedBodies[0].contains("ResolveMalId"))
    }

    // 2. AniList ID resolves to MAL ID
    @Test
    fun test2_AniListIdResolvesToMalId() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": {"id": 16498, "idMal": 52991}}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val malId = MediaResolver.resolveMalIdForAniListId(16498)
        assertEquals(52991, malId)
        assertEquals(52991, CacheManager.getMalIdForAniListId(16498))
        assertEquals(16498, CacheManager.getAniListIdForMalId(52991))

        assertEquals(1, mockInterceptor.capturedBodies.size)
        assertTrue(mockInterceptor.capturedBodies[0].contains("ResolveAniListId"))
    }

    // 3. Unresolved mapping
    @Test
    fun test3_UnresolvedMapping() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": null}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val anilistId = MediaResolver.resolveAniListIdForMalId(999999, MediaType.ANIME)
        assertNull(anilistId)
        assertNull(CacheManager.getAniListIdForMalId(999999))
    }

    // 4. MAL ID never enters AniList ID field (CRITICAL INVARIANT)
    @Test
    fun test4_MalIdNeverEntersAniListIdField() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": null}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val malId = 52991
        val resolvedAniListId = MediaResolver.resolveAniListIdForMalId(malId, MediaType.ANIME)
        assertNull(resolvedAniListId)

        // Ensure domain models enforce null anilistId
        val mediaItem = MediaItem(
            malId = malId,
            anilistId = resolvedAniListId,
            title = "Sousou no Frieren",
            imageUrl = "https://example.com/cover.jpg",
            type = MediaType.ANIME
        )
        assertNull("anilistId must remain null when unresolved", mediaItem.anilistId)
        assertNotEquals(mediaItem.malId, mediaItem.anilistId)

        val extendedDetail = ExtendedMediaDetail(
            malId = malId,
            anilistId = resolvedAniListId,
            title = "Sousou no Frieren"
        )
        assertNull("extendedDetail.anilistId must remain null", extendedDetail.anilistId)
        assertNotEquals(extendedDetail.malId, extendedDetail.anilistId)
    }

    // 5. Cache hit
    @Test
    fun test5_CacheHit() = runBlocking {
        CacheManager.putIdMapping(malId = 52991, aniListId = 16498)

        val directResult = AniListApolloClient.resolveIdMal(52991, MediaType.ANIME)
        assertTrue(directResult is AniListResult.Success)
        assertEquals(16498, (directResult as AniListResult.Success).data)
        assertEquals("Cache hit must increment cacheHitCount", 1L, AniListMetrics.cacheHitCount)
        assertEquals("Cache hit must not produce network requests", 0, mockInterceptor.callCount.get())

        val resolvedId = MediaResolver.resolveAniListIdForMalId(52991, MediaType.ANIME)
        assertEquals(16498, resolvedId)
    }

    // 6. Cache miss
    @Test
    fun test6_CacheMiss() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": {"id": 16498, "idMal": 52991}}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val anilistId = MediaResolver.resolveAniListIdForMalId(52991, MediaType.ANIME)
        assertEquals(16498, anilistId)
        assertEquals(1, mockInterceptor.callCount.get())
        assertEquals(1L, AniListMetrics.cacheMissCount)
    }

    // 7. Negative cache on verified NotFound
    @Test
    fun test7_NegativeCacheOnVerifiedNotFound() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": null}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val res1 = MediaResolver.resolveAniListIdForMalId(88888, MediaType.ANIME)
        assertNull(res1)
        assertTrue("Negative cache must be populated on verified NotFound", CacheManager.isNegativeCached("neg_mal_88888"))

        // Subsequent call must hit negative cache and not make any network request
        val res2 = MediaResolver.resolveAniListIdForMalId(88888, MediaType.ANIME)
        assertNull(res2)
        assertEquals("Network call must not repeat for negative-cached key", 1, mockInterceptor.callCount.get())
    }

    // 8. No negative cache on timeout
    @Test
    fun test8_NoNegativeCacheOnTimeout() = runBlocking {
        mockInterceptor.handler = { _, _ ->
            throw SocketTimeoutException("Read timed out")
        }

        val res = MediaResolver.resolveAniListIdForMalId(77777, MediaType.ANIME)
        assertNull(res)
        assertFalse("Negative cache MUST NOT be populated on timeout", CacheManager.isNegativeCached("neg_mal_77777"))
    }

    // 9. No negative cache on 429
    @Test
    fun test9_NoNegativeCacheOn429() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .header("Retry-After", "60")
                .message("Too Many Requests")
                .body("Rate limit exceeded".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val res = MediaResolver.resolveAniListIdForMalId(66666, MediaType.ANIME)
        assertNull(res)
        assertFalse("Negative cache MUST NOT be populated on 429", CacheManager.isNegativeCached("neg_mal_66666"))
    }

    // 10. No negative cache on 5xx
    @Test
    fun test10_NoNegativeCacheOn5xx() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Service Unavailable")
                .body("Down".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val res = MediaResolver.resolveAniListIdForMalId(55555, MediaType.ANIME)
        assertNull(res)
        assertFalse("Negative cache MUST NOT be populated on 5xx", CacheManager.isNegativeCached("neg_mal_55555"))
    }

    // 11. Concurrent duplicate resolution
    @Test
    fun test11_ConcurrentDuplicateResolution() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Thread.sleep(100)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": {"id": 16498, "idMal": 52991}}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val results = coroutineScope {
            (1..10).map {
                async(Dispatchers.IO) {
                    MediaResolver.resolveAniListIdForMalId(52991, MediaType.ANIME)
                }
            }.awaitAll()
        }

        assertEquals(10, results.size)
        assertTrue(results.all { it == 16498 })
        assertEquals("Concurrent callers for same key must execute exactly 1 network request", 1, mockInterceptor.callCount.get())
        assertEquals(9L, AniListMetrics.duplicateInFlightCount)
    }

    // 12. Cancellation cleanup
    @Test
    fun test12_CancellationCleanup() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Thread.sleep(10000)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": {"id": 9999, "idMal": 11111}}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val job = launch(Dispatchers.Default) {
            AniListApolloClient.resolveIdMal(11111, MediaType.ANIME)
        }
        delay(50L)
        job.cancelAndJoin()

        assertEquals("In-flight map must be cleaned up on cancellation", 0, AniListClient.getInFlightCount())

        // Subsequent call must execute cleanly
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": {"id": 9999, "idMal": 11111}}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val followUp = AniListApolloClient.resolveIdMal(11111, MediaType.ANIME)
        assertTrue(followUp is AniListResult.Success)
        assertEquals(9999, (followUp as AniListResult.Success).data)
    }

    // 13. Studio filmography does not self-map AniList ID as MAL ID (Regression P0)
    @Test
    fun test13_StudioFilmographyDoesNotSelfMapAniListIdAsMalId() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""
                    {
                      "data": {
                        "Studio": {
                          "id": 569,
                          "name": "MAPPA",
                          "isAnimationStudio": true,
                          "siteUrl": "http://www.mappa.co.jp/",
                          "favourites": 12000,
                          "media": {
                            "pageInfo": {
                              "hasNextPage": false,
                              "currentPage": 1,
                              "total": 1
                            },
                            "nodes": [
                              {
                                "id": 16498,
                                "idMal": 52991,
                                "title": { "romaji": "Chainsaw Man", "english": "Chainsaw Man" },
                                "coverImage": { "large": "https://img.jpg" },
                                "format": "TV",
                                "type": "ANIME",
                                "status": "FINISHED",
                                "episodes": 12,
                                "chapters": null,
                                "averageScore": 86,
                                "popularity": 300000,
                                "genres": ["Action"],
                                "startDate": { "year": 2022 }
                              }
                            ]
                          }
                        }
                      }
                    }
                """.trimIndent().toResponseBody(jsonMediaType))
                .build()
        }

        val page = AniListApolloClient.fetchStudioFilmography(studioId = 569, page = 1)
        assertNotNull(page)
        assertEquals(1, page!!.items.size)

        // MAL ID 52991 -> AniList ID 16498 must be mapped correctly
        assertEquals(16498, CacheManager.getAniListIdForMalId(52991))
        assertEquals(52991, CacheManager.getMalIdForAniListId(16498))

        // P0 check: AniList ID 16498 must NEVER be mapped as MAL ID 16498 (no self-mapping)
        assertNull("AniList ID must not be registered as its own MAL ID", CacheManager.getAniListIdForMalId(16498))
    }

    // 14. Enforce correct direction: MAL ID -> AniList ID
    @Test
    fun test14_EnforceCorrectDirectionMalIdToAniListId() {
        CacheManager.putIdMapping(malId = 52991, aniListId = 16498)

        // Correct direction
        assertEquals(16498, CacheManager.getAniListIdForMalId(52991))
        assertEquals(52991, CacheManager.getMalIdForAniListId(16498))

        // Reverse queries should NOT cross-pollinate
        assertNull(CacheManager.getAniListIdForMalId(16498))
        assertNull(CacheManager.getMalIdForAniListId(52991))
    }

    // 15. MediaType namespace isolation (Anime vs Manga ID isolation)
    @Test
    fun test15_MediaTypeNamespaceIsolation() {
        // Suppose MAL Anime 1 is Cowboy Bebop (AniList 1), but MAL Manga 1 is Monster (AniList 30001)
        CacheManager.putIdMapping(malId = 1, aniListId = 1, type = MediaType.ANIME)
        CacheManager.putIdMapping(malId = 1, aniListId = 30001, type = MediaType.MANGA)

        assertEquals(1, CacheManager.getAniListIdForMalId(1, MediaType.ANIME))
        assertEquals(30001, CacheManager.getAniListIdForMalId(1, MediaType.MANGA))

        assertEquals(1, CacheManager.getMalIdForAniListId(1, MediaType.ANIME))
        assertEquals(1, CacheManager.getMalIdForAniListId(30001, MediaType.MANGA))
    }
}
