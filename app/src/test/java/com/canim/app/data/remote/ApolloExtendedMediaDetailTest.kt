package com.canim.app.data.remote

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.anilist.AniListApolloClient
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
class ApolloExtendedMediaDetailTest {

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
                .body("""{"data": {"Media": null}}""".toResponseBody(jsonMediaType))
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
        CacheManager.clearMetadataCache()
    }

    @After
    fun tearDown() {
        AniListApolloClient.setOkHttpClientForTesting(null)
        AniListClient.setClientForTesting(null)
        AniListMetrics.reset()
        CacheManager.clearIdMappings()
        CacheManager.clearMetadataCache()
    }

    private fun sampleMediaJson(id: Int = 16498, malId: Int = 5114): String = """
    {
      "data": {
        "Media": {
          "__typename": "Media",
          "id": $id,
          "idMal": $malId,
          "title": {
            "romaji": "Shingeki no Kyojin",
            "english": "Attack on Titan",
            "native": "進撃の巨人"
          },
          "duration": 24,
          "source": "MANGA",
          "status": "FINISHED",
          "genres": ["Action", "Drama", "Fantasy", "Mystery"],
          "averageScore": 85,
          "popularity": 500000,
          "rankings": [
            { "rank": 1, "allTime": true },
            { "rank": 5, "allTime": false }
          ],
          "recommendations": {
            "nodes": [
              {
                "mediaRecommendation": {
                  "id": 11061,
                  "idMal": 11061,
                  "title": {
                    "romaji": "Hunter x Hunter (2011)",
                    "english": "Hunter x Hunter"
                  },
                  "coverImage": {
                    "large": "https://example.com/hxh.jpg",
                    "medium": "https://example.com/hxh_m.jpg"
                  },
                  "type": "ANIME",
                  "averageScore": 90,
                  "format": "TV"
                }
              }
            ]
          },
          "startDate": { "year": 2013, "month": 4, "day": 7 },
          "endDate": { "year": 2013, "month": 9, "day": 29 },
          "studios": {
            "nodes": [
              { "id": 858, "name": "Wit Studio" }
            ]
          },
          "characters": {
            "edges": [
              {
                "role": "MAIN",
                "node": {
                  "id": 40882,
                  "name": { "full": "Eren Yeager", "native": "エレン・イェーガー" },
                  "image": { "medium": "https://example.com/eren_m.jpg", "large": "https://example.com/eren_l.jpg" }
                },
                "voiceActors": [
                  {
                    "id": 95084,
                    "name": { "full": "Yuki Kaji", "native": "梶裕貴" },
                    "image": { "medium": "https://example.com/kaji_m.jpg", "large": "https://example.com/kaji_l.jpg" }
                  }
                ]
              }
            ]
          },
          "staff": {
            "edges": [
              {
                "role": "Director",
                "node": {
                  "id": 95200,
                  "name": { "full": "Tetsuro Araki", "native": "荒木哲郎" },
                  "image": { "medium": "https://example.com/araki_m.jpg", "large": "https://example.com/araki_l.jpg" }
                }
              }
            ]
          },
          "relations": {
            "edges": [
              {
                "relationType": "ADAPTATION",
                "node": {
                  "id": 53390,
                  "idMal": 23390,
                  "title": { "romaji": "Shingeki no Kyojin", "english": "Attack on Titan" },
                  "coverImage": { "large": "https://example.com/manga_l.jpg", "medium": "https://example.com/manga_m.jpg" },
                  "type": "MANGA",
                  "format": "MANGA",
                  "status": "FINISHED"
                }
              }
            ]
          }
        }
      }
    }
    """.trimIndent()

    // 1. Successful fetch by AniList ID
    @Test
    fun test1_SuccessfulGetExtendedDetailsByAniListId() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleMediaJson(16498, 5114).toResponseBody(jsonMediaType))
                .build()
        }

        val detail = AniListApolloClient.getExtendedDetails(aniListId = 16498, malId = null, type = MediaType.ANIME)
        assertNotNull(detail)
        assertEquals(16498, detail!!.anilistId)
        assertEquals(5114, detail.malId)
        assertEquals("Shingeki no Kyojin", detail.title)
        assertEquals("Attack on Titan", detail.titleEnglish)
        assertEquals("進撃の巨人", detail.nativeTitle)
        assertEquals(24, detail.durationMinutes)
        assertEquals("Wit Studio", detail.studio)
        assertEquals(858, detail.studioId)
        assertEquals(8.5, detail.averageScore!!, 0.001)
        assertEquals(1, detail.rank)
        assertEquals("7 Apr 2013", detail.startDate)
        assertEquals("29 Sep 2013", detail.endDate)

        // Cast & Crew
        assertEquals(1, detail.cast.size)
        assertEquals("Eren Yeager", detail.cast[0].characterName)
        assertEquals("Yuki Kaji", detail.cast[0].actorName)
        assertEquals(1, detail.crew.size)
        assertEquals("Tetsuro Araki", detail.crew[0].name)
        assertEquals("Director", detail.crew[0].role)

        // Recommendations & Relations
        assertEquals(1, detail.recommendations.size)
        assertEquals(11061, detail.recommendations[0].malId)
        assertEquals(1, detail.relations.size)
        assertEquals("ADAPTATION", detail.relations[0].relationType)
        assertEquals(MediaType.MANGA, detail.relations[0].type)

        // ID Mapping verified
        assertEquals(16498, CacheManager.getAniListIdForMalId(5114))
    }

    // 2. Successful fetch by MAL ID
    @Test
    fun test2_SuccessfulGetExtendedDetailsByMalId() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleMediaJson(16498, 5114).toResponseBody(jsonMediaType))
                .build()
        }

        val detail = AniListClient.getExtendedDetails(aniListId = null, malId = 5114, type = MediaType.ANIME)
        assertNotNull(detail)
        assertEquals(16498, detail!!.anilistId)
        assertEquals(5114, detail.malId)
        assertEquals("Attack on Titan", detail.titleEnglish)
        assertEquals(16498, CacheManager.getAniListIdForMalId(5114))
    }

    // 3. Cache Hit on secondary lookup
    @Test
    fun test3_CacheHitBehavior() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleMediaJson(16498, 5114).toResponseBody(jsonMediaType))
                .build()
        }

        // First call: network fetch
        val detail1 = AniListApolloClient.getExtendedDetails(aniListId = 16498, malId = 5114, type = MediaType.ANIME)
        assertNotNull(detail1)
        assertEquals(1, mockInterceptor.callCount.get())

        // Second call without forceRefresh: should hit CacheManager
        val detail2 = AniListApolloClient.getExtendedDetails(aniListId = 16498, malId = 5114, type = MediaType.ANIME, forceRefresh = false)
        assertNotNull(detail2)
        assertEquals(1, mockInterceptor.callCount.get()) // No new HTTP request
        assertEquals(1L, AniListMetrics.cacheHitCount)

        // Third call with forceRefresh = true: should issue network request
        val detail3 = AniListApolloClient.getExtendedDetails(aniListId = 16498, malId = 5114, type = MediaType.ANIME, forceRefresh = true)
        assertNotNull(detail3)
        assertEquals(2, mockInterceptor.callCount.get())
    }

    // 4. In-Flight Request Deduplication
    @Test
    fun test4_InFlightDeduplication() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Thread.sleep(100)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleMediaJson(16498, 5114).toResponseBody(jsonMediaType))
                .build()
        }

        val d1 = async(Dispatchers.IO) { AniListApolloClient.getExtendedDetails(16498, null, MediaType.ANIME) }
        val d2 = async(Dispatchers.IO) { AniListApolloClient.getExtendedDetails(16498, null, MediaType.ANIME) }

        val r1 = d1.await()
        val r2 = d2.await()

        assertNotNull(r1)
        assertNotNull(r2)
        assertEquals(1, mockInterceptor.callCount.get())
        assertTrue(AniListMetrics.duplicateInFlightCount >= 1L)
    }

    // 5. Negative Caching on NotFound
    @Test
    fun test5_NegativeCachingOnNotFound() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": null}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val res = AniListApolloClient.getExtendedDetails(999999, null, MediaType.ANIME)
        assertNull(res)
        assertEquals(1, mockInterceptor.callCount.get())

        // Next call should be negative cached
        val res2 = AniListApolloClient.getExtendedDetails(999999, null, MediaType.ANIME)
        assertNull(res2)
        assertEquals(1, mockInterceptor.callCount.get()) // No new HTTP call
    }

    // 6. Rate Limit (429) Handling
    @Test
    fun test6_RateLimitHandling() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .header("Retry-After", "60")
                .body("{}".toResponseBody(jsonMediaType))
                .build()
        }

        val result = AniListApolloClient.executeGetExtendedDetailsById(16498)
        assertTrue(result is AniListResult.RateLimited)
        assertEquals(60L, (result as AniListResult.RateLimited).retryAfterSeconds)
        assertEquals(1L, AniListMetrics.rateLimitCount)

        // 429 should NOT be negative cached
        assertFalse(CacheManager.isNegativeCached(CacheManager.detailKey(16498, null)))
    }

    // 7. Server Error (500) Handling
    @Test
    fun test7_Http5xxHandling() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body("{}".toResponseBody(jsonMediaType))
                .build()
        }

        val result = AniListApolloClient.executeGetExtendedDetailsById(16498)
        assertTrue(result is AniListResult.HttpError)
        assertEquals(500, (result as AniListResult.HttpError).code)
        assertEquals(1L, AniListMetrics.http5xxCount)
    }

    // 8. Timeout Handling
    @Test
    fun test8_TimeoutHandling() = runBlocking {
        mockInterceptor.handler = { _, _ ->
            throw SocketTimeoutException("Read timed out")
        }

        val result = AniListApolloClient.executeGetExtendedDetailsById(16498)
        assertTrue(result is AniListResult.Timeout)
        assertEquals(1L, AniListMetrics.timeoutCount)
    }

    // 9. GraphQL Errors Handling
    @Test
    fun test9_GraphQLErrorHandling() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"errors": [{"message": "Validation error"}]}""".toResponseBody(jsonMediaType))
                .build()
        }

        val result = AniListApolloClient.executeGetExtendedDetailsById(16498)
        assertTrue(result is AniListResult.GraphQLError)
        assertEquals(1, (result as AniListResult.GraphQLError).errors.size)
        assertEquals("Validation error", result.errors[0].message)
        assertEquals(1L, AniListMetrics.graphQLErrorCount)
    }

    // 10. Cancellation Handling
    @Test
    fun test10_CancellationHandling() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Thread.sleep(500)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleMediaJson(16498, 5114).toResponseBody(jsonMediaType))
                .build()
        }

        val job = launch(Dispatchers.IO) {
            AniListApolloClient.getExtendedDetails(16498, null, MediaType.ANIME)
        }
        delay(50)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
