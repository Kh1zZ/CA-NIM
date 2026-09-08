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
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ApolloCharacterProfileTest {

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
                .body("""{"data": {"Character": null}}""".toResponseBody(jsonMediaType))
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

    private fun sampleCharacterJson(id: Int = 40882): String = """
    {
      "data": {
        "Character": {
          "id": $id,
          "name": {
            "first": "Eren",
            "last": "Yeager",
            "full": "Eren Yeager",
            "native": "エレン・イェーガー"
          },
          "image": {
            "large": "https://example.com/eren_large.jpg",
            "medium": "https://example.com/eren_medium.jpg"
          },
          "description": "A member of the Survey Corps. ~!He holds the Attack Titan.!~",
          "media": {
            "edges": [
              {
                "characterRole": "MAIN",
                "node": {
                  "id": 16498,
                  "idMal": 16498,
                  "title": {
                    "romaji": "Shingeki no Kyojin",
                    "english": "Attack on Titan"
                  },
                  "coverImage": {
                    "large": "https://example.com/aot.jpg",
                    "medium": "https://example.com/aot_m.jpg"
                  },
                  "startDate": {
                    "year": 2013
                  },
                  "format": "TV",
                  "type": "ANIME"
                }
              }
            ]
          }
        }
      }
    }
    """.trimIndent()

    // 1. Successful character profile fetch & mapping
    @Test
    fun test1_SuccessfulCharacterProfile() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleCharacterJson(40882).toResponseBody(jsonMediaType))
                .build()
        }

        val profile = AniListApolloClient.getCharacterProfile(40882)
        assertNotNull(profile)
        assertEquals(40882, profile!!.id)
        assertFalse(profile.isStaff)
        assertEquals("Eren Yeager", profile.name)
        assertEquals("エレン・イェーガー", profile.nativeName)
        assertEquals("Eren", profile.firstName)
        assertEquals("Yeager", profile.lastName)
        assertEquals("https://example.com/eren_large.jpg", profile.image)

        // Spoilers should be sanitized
        assertNotNull(profile.biography)
        assertTrue(profile.biography!!.contains("A member of the Survey Corps"))
        assertFalse(profile.biography!!.contains("~!"))

        // Filmography
        assertEquals(1, profile.filmography.size)
        val film = profile.filmography[0]
        assertEquals(16498, film.id)
        assertEquals(16498, film.malId)
        assertEquals("Shingeki no Kyojin", film.title)
        assertEquals("Attack on Titan", film.titleEnglish)
        assertEquals(2013, film.year)
        assertEquals("TV", film.format)
        assertEquals(MediaType.ANIME, film.type)
        assertEquals("MAIN", film.role)
        assertEquals("Eren Yeager", film.characterName)
    }

    // 2. Cache Hit on secondary lookup
    @Test
    fun test2_CacheHitBehavior() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleCharacterJson(40882).toResponseBody(jsonMediaType))
                .build()
        }

        // First call: fetches from network
        val p1 = AniListApolloClient.getCharacterProfile(40882)
        assertNotNull(p1)
        assertEquals(1, mockInterceptor.callCount.get())

        // Second call without forceRefresh: should hit CacheManager
        val p2 = AniListApolloClient.getCharacterProfile(40882, forceRefresh = false)
        assertNotNull(p2)
        assertEquals(1, mockInterceptor.callCount.get())
        assertEquals(1L, AniListMetrics.cacheHitCount)

        // Third call with forceRefresh: should bypass cache
        val p3 = AniListApolloClient.getCharacterProfile(40882, forceRefresh = true)
        assertNotNull(p3)
        assertEquals(2, mockInterceptor.callCount.get())
    }

    // 3. In-Flight Request Deduplication
    @Test
    fun test3_InFlightDeduplication() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Thread.sleep(100)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleCharacterJson(40882).toResponseBody(jsonMediaType))
                .build()
        }

        val d1 = async(Dispatchers.IO) { AniListApolloClient.getCharacterProfile(40882) }
        val d2 = async(Dispatchers.IO) { AniListApolloClient.getCharacterProfile(40882) }

        val r1 = d1.await()
        val r2 = d2.await()

        assertNotNull(r1)
        assertNotNull(r2)
        assertEquals(1, mockInterceptor.callCount.get())
        assertTrue(AniListMetrics.duplicateInFlightCount >= 1L)
    }

    // 4. NotFound / Null Handling
    @Test
    fun test4_NotFoundHandling() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Character": null}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val res = AniListApolloClient.getCharacterProfile(999999)
        assertNull(res)
        assertEquals(1, mockInterceptor.callCount.get())
    }

    // 5. Rate Limit (429) Handling
    @Test
    fun test5_RateLimitHandling() = runBlocking {
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

        val result = AniListApolloClient.executeGetCharacterProfile(40882)
        assertTrue(result is AniListResult.RateLimited)
        assertEquals(60L, (result as AniListResult.RateLimited).retryAfterSeconds)
        assertEquals(1L, AniListMetrics.rateLimitCount)
    }

    // 6. Server Error (500) Handling
    @Test
    fun test6_Http5xxHandling() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body("{}".toResponseBody(jsonMediaType))
                .build()
        }

        val result = AniListApolloClient.executeGetCharacterProfile(40882)
        assertTrue(result is AniListResult.HttpError)
        assertEquals(500, (result as AniListResult.HttpError).code)
        assertEquals(1L, AniListMetrics.http5xxCount)
    }

    // 7. Timeout Handling
    @Test
    fun test7_TimeoutHandling() = runBlocking {
        mockInterceptor.handler = { _, _ ->
            throw SocketTimeoutException("Read timed out")
        }

        val result = AniListApolloClient.executeGetCharacterProfile(40882)
        assertTrue(result is AniListResult.Timeout)
        assertEquals(1L, AniListMetrics.timeoutCount)
    }

    // 8. GraphQL Errors Handling
    @Test
    fun test8_GraphQLErrorHandling() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"errors": [{"message": "Character not found"}]}""".toResponseBody(jsonMediaType))
                .build()
        }

        val result = AniListApolloClient.executeGetCharacterProfile(40882)
        assertTrue(result is AniListResult.GraphQLError)
        assertEquals(1, (result as AniListResult.GraphQLError).errors.size)
        assertEquals("Character not found", result.errors[0].message)
        assertEquals(1L, AniListMetrics.graphQLErrorCount)
    }

    // 9. Cancellation Handling
    @Test
    fun test9_CancellationHandling() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Thread.sleep(500)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sampleCharacterJson(40882).toResponseBody(jsonMediaType))
                .build()
        }

        val job = launch(Dispatchers.IO) {
            AniListApolloClient.getCharacterProfile(40882)
        }
        delay(50)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
