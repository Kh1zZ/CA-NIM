package com.canim.app.data.remote

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.MediaItem
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
class ApolloSearchMediaTest {

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
                .body("""{"data": {"Page": {"media": []}}}""".toResponseBody(jsonMediaType))
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

    // 1. Successful search with full domain mapping & ID caching
    @Test
    fun test1_SuccessfulSearch() = runBlocking {
        val jsonPayload = """
        {
          "data": {
            "Page": {
              "media": [
                {
                  "id": 16498,
                  "idMal": 52991,
                  "title": {
                    "romaji": "Sousou no Frieren",
                    "english": "Frieren: Beyond Journey's End"
                  },
                  "coverImage": {
                    "medium": "https://example.com/m.jpg",
                    "large": "https://example.com/l.jpg",
                    "extraLarge": "https://example.com/xl.jpg"
                  },
                  "averageScore": 88,
                  "description": "An elf &quot;mage&quot; &amp; her friends <br>went on a journey.",
                  "episodes": 28,
                  "chapters": null,
                  "volumes": null,
                  "status": "RELEASING",
                  "seasonYear": 2023,
                  "season": "FALL",
                  "format": "TV",
                  "genres": ["Adventure", "Drama", "Fantasy"],
                  "studios": {
                    "nodes": [
                      { "name": "Madhouse" }
                    ]
                  }
                }
              ]
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
                .body(jsonPayload.toResponseBody(jsonMediaType))
                .build()
        }

        val items = AniListClient.searchMedia(
            query = "Frieren",
            type = MediaType.ANIME,
            genres = listOf("Adventure"),
            year = 2023,
            format = "TV"
        )

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(52991, item.malId)
        assertEquals(16498, item.anilistId)
        assertNotEquals(item.malId, item.anilistId) // Strict ID separation
        assertEquals("Sousou no Frieren", item.title)
        assertEquals("Frieren: Beyond Journey's End", item.titleEnglish)
        assertEquals("https://example.com/l.jpg", item.imageUrl)
        assertEquals("https://example.com/xl.jpg", item.imageUrlHd)
        assertEquals(8.8, item.score!!, 0.01)
        assertEquals("AIRING", item.status)
        assertEquals(2023, item.year)
        assertEquals("FALL", item.season)
        assertEquals("TV", item.format)
        assertEquals("Madhouse", item.studio)
        assertTrue(item.genres.contains("Adventure"))
        assertEquals("An elf \"mage\" & her friends went on a journey.", item.synopsis)

        // Verify ID mapping is populated in CacheManager
        assertEquals(16498, CacheManager.getAniListIdForMalId(52991))
        assertEquals(52991, CacheManager.getMalIdForAniListId(16498))

        // Verify GraphQL request parameters
        assertEquals(1, mockInterceptor.capturedBodies.size)
        val requestBody = mockInterceptor.capturedBodies[0]
        assertTrue(requestBody.contains("SearchMedia"))
        assertTrue(requestBody.contains("Frieren"))
    }

    // 2. Empty search (zero results)
    @Test
    fun test2_EmptySearch() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Page": {"media": []}}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val items = AniListClient.searchMedia("NonExistentXYZ", MediaType.ANIME)
        assertTrue(items.isEmpty())
        assertEquals(1, mockInterceptor.callCount.get())
    }

    // 3. Malformed or null response returns empty list safely
    @Test
    fun test3_MalformedOrNullResponse() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Page": null}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val items = AniListClient.searchMedia("NullPageQuery", MediaType.ANIME)
        assertTrue(items.isEmpty())

        val directResult = AniListApolloClient.executeSearchMedia("NullPageQuery2", MediaType.ANIME)
        assertTrue(directResult is AniListResult.Success)
        assertTrue((directResult as AniListResult.Success).data.isEmpty())
    }

    // 4. GraphQL error mapping
    @Test
    fun test4_GraphQLError() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"errors": [{"message": "Field 'media' error occurred"}]}""".toResponseBody(jsonMediaType))
                .build()
        }

        val directResult = AniListApolloClient.executeSearchMedia("ErrQuery", MediaType.ANIME)
        assertTrue(directResult is AniListResult.GraphQLError)
        assertEquals(1L, AniListMetrics.graphQLErrorCount)

        val items = AniListClient.searchMedia("ErrQuery", MediaType.ANIME)
        assertTrue(items.isEmpty())
    }

    // 5. Timeout mapping
    @Test
    fun test5_Timeout() = runBlocking {
        mockInterceptor.handler = { _, _ ->
            throw SocketTimeoutException("Read timed out")
        }

        val directResult = AniListApolloClient.executeSearchMedia("TimeoutQuery", MediaType.ANIME)
        assertTrue(directResult is AniListResult.Timeout)
        assertEquals(1L, AniListMetrics.timeoutCount)

        val items = AniListClient.searchMedia("TimeoutQuery", MediaType.ANIME)
        assertTrue(items.isEmpty())
    }

    // 6. Network failure mapping
    @Test
    fun test6_NetworkFailure() = runBlocking {
        mockInterceptor.handler = { _, _ ->
            throw IOException("Connection dropped")
        }

        val directResult = AniListApolloClient.executeSearchMedia("NetFailQuery", MediaType.ANIME)
        assertTrue(directResult is AniListResult.NetworkError)

        val items = AniListClient.searchMedia("NetFailQuery", MediaType.ANIME)
        assertTrue(items.isEmpty())
    }

    // 7. Rate limit (HTTP 429) mapping
    @Test
    fun test7_RateLimit() = runBlocking {
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

        val directResult = AniListApolloClient.executeSearchMedia("RateLimitedQuery", MediaType.ANIME)
        assertTrue(directResult is AniListResult.RateLimited)
        assertEquals(1L, AniListMetrics.rateLimitCount)

        val items = AniListClient.searchMedia("RateLimitedQuery", MediaType.ANIME)
        assertTrue(items.isEmpty())
    }

    // 8. Pagination parameters passed correctly
    @Test
    fun test8_Pagination() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Page": {"media": []}}}""".toResponseBody(jsonMediaType))
                .build()
        }

        AniListApolloClient.executeSearchMedia(
            query = "Bleach",
            type = MediaType.ANIME,
            page = 3,
            perPage = 15
        )

        assertEquals(1, mockInterceptor.capturedBodies.size)
        val body = mockInterceptor.capturedBodies[0]
        assertTrue(body.contains("\"page\":3"))
        assertTrue(body.contains("\"perPage\":15"))
    }

    // 9. Cache behavior: hit returns cached items without network call
    @Test
    fun test9_CacheBehavior() = runBlocking {
        val jsonPayload = """
        {
          "data": {
            "Page": {
              "media": [
                {
                  "id": 100,
                  "idMal": 200,
                  "title": { "romaji": "Cached Anime" },
                  "status": "FINISHED"
                }
              ]
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
                .body(jsonPayload.toResponseBody(jsonMediaType))
                .build()
        }

        // First call: cache miss, network request executed
        val firstResults = AniListClient.searchMedia("CacheTest", MediaType.ANIME)
        assertEquals(1, firstResults.size)
        assertEquals(1, mockInterceptor.callCount.get())
        assertEquals(1L, AniListMetrics.cacheMissCount)
        assertEquals(0L, AniListMetrics.cacheHitCount)

        // Second call: cache hit, no network request
        val secondResults = AniListClient.searchMedia("CacheTest", MediaType.ANIME)
        assertEquals(1, secondResults.size)
        assertEquals("Network call must not repeat on cache hit", 1, mockInterceptor.callCount.get())
        assertEquals(1L, AniListMetrics.cacheHitCount)
        assertEquals(firstResults[0].anilistId, secondResults[0].anilistId)
    }

    // 10. Concurrent duplicate searches trigger exactly 1 network call
    @Test
    fun test10_ConcurrentDuplicateRequests() = runBlocking {
        val jsonPayload = """
        {
          "data": {
            "Page": {
              "media": [
                {
                  "id": 101,
                  "idMal": 202,
                  "title": { "romaji": "Concurrent Anime" },
                  "status": "FINISHED"
                }
              ]
            }
          }
        }
        """.trimIndent()

        mockInterceptor.handler = { req, _ ->
            Thread.sleep(100)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(jsonPayload.toResponseBody(jsonMediaType))
                .build()
        }

        val results = coroutineScope {
            (1..8).map {
                async(Dispatchers.IO) {
                    AniListClient.searchMedia("DedupeTest", MediaType.ANIME)
                }
            }.awaitAll()
        }

        assertEquals(8, results.size)
        assertTrue(results.all { it.size == 1 && it[0].anilistId == 101 })
        assertEquals("Concurrent callers for same query must execute exactly 1 network request", 1, mockInterceptor.callCount.get())
        assertEquals(7L, AniListMetrics.duplicateInFlightCount)
    }
}
