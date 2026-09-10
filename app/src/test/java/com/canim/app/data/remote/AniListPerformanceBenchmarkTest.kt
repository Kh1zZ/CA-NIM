package com.canim.app.data.remote

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.*
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.anilist.AniListApolloClient
import com.canim.app.data.resolver.MediaResolver
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
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
class AniListPerformanceBenchmarkTest {

    private class BenchmarkInterceptor : Interceptor {
        var handler: ((Request, Int) -> Response)? = null
        val callCount = AtomicInteger(0)
        val capturedRequests = CopyOnWriteArrayList<Request>()

        fun reset() {
            callCount.set(0)
            capturedRequests.clear()
            handler = null
        }

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
                .body("""{"data": {"Media": {"id": 16498, "idMal": 52991}}}""".toResponseBody(jsonMediaType))
                .build()
        }
    }

    private lateinit var mockInterceptor: BenchmarkInterceptor
    private lateinit var testHttpClient: OkHttpClient

    // Hardened Legacy Implementation simulation
    private object LegacyClient {
        private const val GRAPHQL_ENDPOINT = "https://graphql.anilist.co"

        suspend fun executeQuery(
            client: OkHttpClient,
            query: String,
            variables: JSONObject
        ): String? = withContext(Dispatchers.IO) {
            val requestBodyJson = JSONObject().apply {
                put("query", query)
                put("variables", variables)
            }

            val request = Request.Builder()
                .url(GRAPHQL_ENDPOINT)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "CanimApp/2.0")
                .header("Accept", "application/json")
                .build()

            var attempts = 0
            val maxAttempts = 2

            while (attempts < maxAttempts) {
                attempts++
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        return@withContext response.body?.string()
                    }
                    if (response.code == 429 && attempts < maxAttempts) {
                        response.close()
                        delay(50L)
                        continue
                    }
                    response.close()
                    return@withContext null
                } catch (_: SocketTimeoutException) {
                    return@withContext null
                } catch (_: Exception) {
                    if (attempts >= maxAttempts) return@withContext null
                    delay(50L)
                }
            }
            null
        }

        suspend fun resolveIdMal(client: OkHttpClient, malId: Int, type: MediaType): Int? = withContext(Dispatchers.IO) {
            val query = "query ($malId: Int, $type: MediaType) { Media(idMal: $malId, type: $type) { id idMal } }"
            val vars = JSONObject().apply {
                put("malId", malId)
                put("type", type.name)
            }
            val res = executeQuery(client, query, vars) ?: return@withContext null
            try {
                val root = JSONObject(res)
                val data = root.optJSONObject("data")
                val media = data?.optJSONObject("Media")
                val id = media?.optInt("id", 0)
                if (id != null && id > 0) id else null
            } catch (_: Exception) {
                null
            }
        }

        suspend fun legacyMediaResolver(client: OkHttpClient, malId: Int, type: MediaType): Int? = withContext(Dispatchers.IO) {
            val cached = CacheManager.getAniListIdForMalId(malId)
            if (cached != null) return@withContext cached

            val negKey = "neg_mal_$malId"
            if (CacheManager.isNegativeCached(negKey)) return@withContext null

            val resolved = resolveIdMal(client, malId, type)
            if (resolved != null) {
                CacheManager.putIdMapping(malId = malId, aniListId = resolved)
                resolved
            } else {
                // LEGACY BEHAVIOR: Always cached negative on null, even on timeout, 429, or 500!
                CacheManager.putNegativeCache(negKey)
                null
            }
        }
    }

    @Before
    fun setUp() {
        mockInterceptor = BenchmarkInterceptor()
        testHttpClient = OkHttpClient.Builder()
            .addInterceptor(mockInterceptor)
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .writeTimeout(1, TimeUnit.SECONDS)
            .build()

        ApiClient.aniListLimiter.configureForTesting(burstCapacity = 200, refillIntervalMs = 1L)
        AniListApolloClient.setOkHttpClientForTesting(testHttpClient)
        AniListClient.setClientForTesting(testHttpClient)
        AniListMetrics.reset()
        CacheManager.clearIdMappings()
        CacheManager.clearMetadataCache()
        CacheManager.clearNegativeCache()
    }

    @After
    fun tearDown() {
        ApiClient.aniListLimiter.configureForTesting(burstCapacity = 3, refillIntervalMs = 2_000L)
        AniListApolloClient.setOkHttpClientForTesting(null)
        AniListClient.setClientForTesting(null)
        AniListMetrics.reset()
        CacheManager.clearIdMappings()
        CacheManager.clearMetadataCache()
        CacheManager.clearNegativeCache()
    }

    private fun percentile(sorted: List<Long>, pct: Double): Long {
        if (sorted.isEmpty()) return 0L
        val index = (Math.ceil(pct * sorted.size) - 1).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    private fun idResolutionJson(id: Int = 16498, malId: Int = 52991) =
        """{"data": {"Media": {"id": $id, "idMal": $malId}}}"""

    private fun searchMediaJson(id: Int = 16498) = """
    {
      "data": {
        "Page": {
          "pageInfo": {"total": 1, "currentPage": 1, "lastPage": 1, "hasNextPage": false, "perPage": 20},
          "media": [
            {
              "id": $id,
              "idMal": 52991,
              "title": {"romaji": "Attack on Titan", "english": "Attack on Titan", "native": "進撃の巨人"},
              "coverImage": {"large": "https://img.anilist.co/cover.jpg", "extraLarge": "https://img.anilist.co/cover_hd.jpg"},
              "averageScore": 85,
              "description": "Centuries ago, mankind was slaughtered by titans...",
              "episodes": 25,
              "chapters": null,
              "status": "FINISHED",
              "format": "TV",
              "genres": ["Action", "Drama"],
              "startDate": {"year": 2013, "month": 4, "day": 7},
              "popularity": 450000,
              "type": "ANIME"
            }
          ]
        }
      }
    }
    """.trimIndent()

    private fun extendedMediaJson(id: Int = 16498, malId: Int = 52991) = """
    {
      "data": {
        "Media": {
          "__typename": "Media",
          "id": $id,
          "idMal": $malId,
          "title": {"romaji": "Attack on Titan", "english": "Attack on Titan", "native": "進撃の巨人"},
          "coverImage": {"large": "https://img.anilist.co/cover.jpg", "extraLarge": "https://img.anilist.co/cover_hd.jpg"},
          "averageScore": 85,
          "description": "Centuries ago, mankind was slaughtered by titans...",
          "episodes": 25,
          "chapters": null,
          "volumes": null,
          "status": "FINISHED",
          "seasonYear": 2013,
          "season": "SPRING",
          "format": "TV",
          "genres": ["Action", "Drama"],
          "startDate": {"year": 2013, "month": 4, "day": 7},
          "endDate": {"year": 2013, "month": 9, "day": 28},
          "duration": 24,
          "source": "MANGA",
          "popularity": 450000,
          "type": "ANIME",
          "rankings": [{"rank": 1, "type": "POPULAR", "allTime": true}],
          "recommendations": {"nodes": []},
          "studios": {"edges": [{"isMain": true, "node": {"id": 858, "name": "Wit Studio"}}]},
          "characters": {
            "edges": [
              {
                "node": {"id": 40882, "name": {"full": "Eren Yeager"}, "image": {"large": "https://img.anilist.co/eren.jpg"}},
                "voiceActors": [{"id": 95299, "name": {"full": "Yuki Kaji"}, "image": {"large": "https://img.anilist.co/kaji.jpg"}}]
              }
            ]
          },
          "staff": {
            "edges": [
              {
                "role": "Director",
                "node": {"id": 95100, "name": {"full": "Tetsuro Araki"}, "image": {"large": "https://img.anilist.co/araki.jpg"}}
              }
            ]
          },
          "relations": {"edges": []}
        }
      }
    }
    """.trimIndent()

    private fun characterProfileJson(id: Int = 40882) = """
    {
      "data": {
        "Character": {
          "id": $id,
          "name": {"full": "Eren Yeager", "native": "エレン・イェーガー"},
          "image": {"large": "https://img.anilist.co/eren.jpg"},
          "description": "Protagonist of Attack on Titan.",
          "gender": "Male",
          "dateOfBirth": {"year": 835, "month": 3, "day": 30},
          "age": "15-19",
          "bloodType": "B",
          "favourites": 85000,
          "media": {
            "pageInfo": {"hasNextPage": false},
            "edges": [
              {
                "characterRole": "MAIN",
                "voiceActors": [{"id": 95299, "name": {"full": "Yuki Kaji"}, "image": {"large": "https://img.anilist.co/kaji.jpg"}, "language": "JAPANESE"}],
                "node": {
                  "id": 16498,
                  "idMal": 52991,
                  "title": {"romaji": "Attack on Titan", "english": "Attack on Titan"},
                  "coverImage": {"large": "https://img.anilist.co/cover.jpg"},
                  "type": "ANIME",
                  "format": "TV",
                  "averageScore": 85,
                  "startDate": {"year": 2013}
                }
              }
            ]
          }
        }
      }
    }
    """.trimIndent()

    private fun studioFilmographyJson(studioId: Int = 858) = """
    {
      "data": {
        "Studio": {
          "id": $studioId,
          "name": "Wit Studio",
          "isAnimationStudio": true,
          "favourites": 12000,
          "siteUrl": "http://witstudio.co.jp/",
          "media": {
            "pageInfo": {"total": 25, "currentPage": 1, "hasNextPage": true},
            "nodes": [
              {
                "id": 16498,
                "idMal": 52991,
                "title": {"romaji": "Attack on Titan", "english": "Attack on Titan"},
                "coverImage": {"large": "https://img.anilist.co/cover.jpg", "extraLarge": "https://img.anilist.co/cover_hd.jpg"},
                "format": "TV",
                "type": "ANIME",
                "status": "FINISHED",
                "episodes": 25,
                "averageScore": 85,
                "popularity": 450000,
                "genres": ["Action", "Drama"],
                "startDate": {"year": 2013}
              }
            ]
          }
        }
      }
    }
    """.trimIndent()

    @Test
    fun runComprehensivePerformanceBenchmark() = runBlocking {
        println("=================================================================")
        println("PHASE 7 — COMPREHENSIVE ANILIST PERFORMANCE BENCHMARK (EMPIRICAL)")
        println("=================================================================")

        mockInterceptor.handler = { req, _ ->
            val bodyStr = req.body?.let {
                val buf = okio.Buffer()
                it.writeTo(buf)
                buf.readUtf8()
            } ?: ""

            when {
                bodyStr.contains("Character") -> Response.Builder()
                    .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(characterProfileJson().toResponseBody(jsonMediaType)).build()
                bodyStr.contains("Studio") -> Response.Builder()
                    .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(studioFilmographyJson().toResponseBody(jsonMediaType)).build()
                bodyStr.contains("Page") && bodyStr.contains("search") -> Response.Builder()
                    .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(searchMediaJson().toResponseBody(jsonMediaType)).build()
                bodyStr.contains("characters") || bodyStr.contains("extendedMediaDetailFields") -> Response.Builder()
                    .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(extendedMediaJson().toResponseBody(jsonMediaType)).build()
                else -> Response.Builder()
                    .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(idResolutionJson().toResponseBody(jsonMediaType)).build()
            }
        }

        val iterations = 50

        // -------------------------------------------------------------
        // 1. Latency & Network: ID Resolution (Cache Miss)
        // -------------------------------------------------------------
        val legacyIdMissLatencies = mutableListOf<Long>()
        val apolloIdMissLatencies = mutableListOf<Long>()

        for (i in 1..iterations) {
            CacheManager.clearIdMappings()
            val startLegacy = System.nanoTime()
            val resL = LegacyClient.resolveIdMal(testHttpClient, 50000 + i, MediaType.ANIME)
            legacyIdMissLatencies.add(System.nanoTime() - startLegacy)
            assertEquals(16498, resL)

            CacheManager.clearIdMappings()
            val startApollo = System.nanoTime()
            val resA = AniListApolloClient.executeResolveMalId(50000 + i, MediaType.ANIME)
            apolloIdMissLatencies.add(System.nanoTime() - startApollo)
            assertTrue(resA is AniListResult.Success)
        }
        legacyIdMissLatencies.sort()
        apolloIdMissLatencies.sort()

        // -------------------------------------------------------------
        // 2. Latency & Network: ID Resolution (Cache Hit)
        // -------------------------------------------------------------
        val legacyIdHitLatencies = mutableListOf<Long>()
        val apolloIdHitLatencies = mutableListOf<Long>()
        CacheManager.putIdMapping(52991, 16498)

        for (i in 1..iterations) {
            val startLegacy = System.nanoTime()
            val resL = LegacyClient.legacyMediaResolver(testHttpClient, 52991, MediaType.ANIME)
            legacyIdHitLatencies.add(System.nanoTime() - startLegacy)
            assertEquals(16498, resL)

            val startApollo = System.nanoTime()
            val resA = MediaResolver.resolveAniListIdForMalId(52991, MediaType.ANIME)
            apolloIdHitLatencies.add(System.nanoTime() - startApollo)
            assertEquals(16498, resA)
        }
        legacyIdHitLatencies.sort()
        apolloIdHitLatencies.sort()

        // -------------------------------------------------------------
        // 3. Latency: Search Media
        // -------------------------------------------------------------
        val apolloSearchMissLatencies = mutableListOf<Long>()
        val apolloSearchHitLatencies = mutableListOf<Long>()

        for (i in 1..iterations) {
            CacheManager.clearMetadataCache()
            val t0 = System.nanoTime()
            val res = AniListApolloClient.searchMedia("Titan $i", MediaType.ANIME)
            apolloSearchMissLatencies.add(System.nanoTime() - t0)
            assertEquals(1, res.size)

            val t1 = System.nanoTime()
            val resHit = AniListApolloClient.searchMedia("Titan $i", MediaType.ANIME)
            apolloSearchHitLatencies.add(System.nanoTime() - t1)
            assertEquals(1, resHit.size)
        }
        apolloSearchMissLatencies.sort()
        apolloSearchHitLatencies.sort()

        // -------------------------------------------------------------
        // 4. Latency: Extended Media Detail
        // -------------------------------------------------------------
        val apolloDetailMissLatencies = mutableListOf<Long>()
        val apolloDetailHitLatencies = mutableListOf<Long>()

        for (i in 1..iterations) {
            CacheManager.clearMetadataCache()
            val t0 = System.nanoTime()
            val res = AniListApolloClient.getExtendedDetails(16498 + i, null, MediaType.ANIME)
            apolloDetailMissLatencies.add(System.nanoTime() - t0)
            assertNotNull(res)

            val t1 = System.nanoTime()
            val resHit = AniListApolloClient.getExtendedDetails(16498 + i, null, MediaType.ANIME)
            apolloDetailHitLatencies.add(System.nanoTime() - t1)
            assertNotNull(resHit)
        }
        apolloDetailMissLatencies.sort()
        apolloDetailHitLatencies.sort()

        // -------------------------------------------------------------
        // 5. Latency: Character Profile
        // -------------------------------------------------------------
        val apolloCharMissLatencies = mutableListOf<Long>()
        val apolloCharHitLatencies = mutableListOf<Long>()

        for (i in 1..iterations) {
            CacheManager.clearMetadataCache()
            val t0 = System.nanoTime()
            val res = AniListApolloClient.getCharacterProfile(40882 + i)
            apolloCharMissLatencies.add(System.nanoTime() - t0)
            assertNotNull(res)

            val t1 = System.nanoTime()
            val resHit = AniListApolloClient.getCharacterProfile(40882 + i)
            apolloCharHitLatencies.add(System.nanoTime() - t1)
            assertNotNull(resHit)
        }
        apolloCharMissLatencies.sort()
        apolloCharHitLatencies.sort()

        // -------------------------------------------------------------
        // 6. Latency: Studio Filmography
        // -------------------------------------------------------------
        val apolloStudioMissLatencies = mutableListOf<Long>()
        val apolloStudioHitLatencies = mutableListOf<Long>()

        for (i in 1..iterations) {
            CacheManager.clearMetadataCache()
            val t0 = System.nanoTime()
            val res = AniListApolloClient.fetchStudioFilmography(858 + i)
            apolloStudioMissLatencies.add(System.nanoTime() - t0)
            assertNotNull(res)

            val t1 = System.nanoTime()
            val resHit = AniListApolloClient.fetchStudioFilmography(858 + i)
            apolloStudioHitLatencies.add(System.nanoTime() - t1)
            assertNotNull(resHit)
        }
        apolloStudioMissLatencies.sort()
        apolloStudioHitLatencies.sort()

        // -------------------------------------------------------------
        // 7. Concurrency & Deduplication Test (50 Concurrent Callers)
        // -------------------------------------------------------------
        mockInterceptor.reset()
        mockInterceptor.handler = { req, _ ->
            Thread.sleep(20)
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(idResolutionJson().toResponseBody(jsonMediaType)).build()
        }
        CacheManager.clearIdMappings()
        val legacyConcurrencyCalls = 50
        val legacyStart = System.currentTimeMillis()
        val legacyResults = coroutineScope {
            (1..legacyConcurrencyCalls).map {
                async(Dispatchers.IO) {
                    LegacyClient.legacyMediaResolver(testHttpClient, 99999, MediaType.ANIME)
                }
            }.awaitAll()
        }
        val legacyConcurrencyDuration = System.currentTimeMillis() - legacyStart
        val legacyHttpRequests = mockInterceptor.callCount.get()

        mockInterceptor.reset()
        AniListMetrics.reset()
        CacheManager.clearIdMappings()
        mockInterceptor.handler = { req, _ ->
            Thread.sleep(10)
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(idResolutionJson().toResponseBody(jsonMediaType)).build()
        }
        val apolloConcurrencyCalls = 50
        val apolloStart = System.currentTimeMillis()
        val apolloResults = coroutineScope {
            (1..apolloConcurrencyCalls).map {
                async(Dispatchers.IO) {
                    MediaResolver.resolveAniListIdForMalId(99999, MediaType.ANIME)
                }
            }.awaitAll()
        }
        val apolloConcurrencyDuration = System.currentTimeMillis() - apolloStart
        val apolloHttpRequests = mockInterceptor.callCount.get()
        val apolloDeduped = AniListMetrics.duplicateInFlightCount

        // -------------------------------------------------------------
        // 8. Negative Caching & Error Handling Comparison
        // -------------------------------------------------------------
        mockInterceptor.reset()
        mockInterceptor.handler = { req, _ ->
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data": {"Media": null}}""".toResponseBody(jsonMediaType)).build()
        }
        CacheManager.clearNegativeCache()
        val apollo404Res = MediaResolver.resolveAniListIdForMalId(11111, MediaType.ANIME)
        assertNull(apollo404Res)
        val apollo404Cached = CacheManager.isNegativeCached("neg_mal_11111")
        assertTrue("Apollo MUST negative-cache verified 404", apollo404Cached)

        mockInterceptor.reset()
        mockInterceptor.handler = { _, _ -> throw SocketTimeoutException("Read timed out") }
        CacheManager.clearNegativeCache()
        val legacyTimeoutRes = LegacyClient.legacyMediaResolver(testHttpClient, 22222, MediaType.ANIME)
        val legacyTimeoutCached = CacheManager.isNegativeCached("neg_mal_22222")
        CacheManager.clearNegativeCache()
        val apolloTimeoutRes = MediaResolver.resolveAniListIdForMalId(22222, MediaType.ANIME)
        val apolloTimeoutCached = CacheManager.isNegativeCached("neg_mal_22222")

        mockInterceptor.reset()
        mockInterceptor.handler = { req, _ ->
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(429).message("Too Many Requests")
                .body("rate limit".toResponseBody("text/plain".toMediaType())).build()
        }
        CacheManager.clearNegativeCache()
        val legacy429Res = LegacyClient.legacyMediaResolver(testHttpClient, 33333, MediaType.ANIME)
        val legacy429Cached = CacheManager.isNegativeCached("neg_mal_33333")
        CacheManager.clearNegativeCache()
        val apollo429Res = MediaResolver.resolveAniListIdForMalId(33333, MediaType.ANIME)
        val apollo429Cached = CacheManager.isNegativeCached("neg_mal_33333")

        mockInterceptor.reset()
        mockInterceptor.handler = { req, _ ->
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                .body("down".toResponseBody("text/plain".toMediaType())).build()
        }
        CacheManager.clearNegativeCache()
        val legacy503Res = LegacyClient.legacyMediaResolver(testHttpClient, 44444, MediaType.ANIME)
        val legacy503Cached = CacheManager.isNegativeCached("neg_mal_44444")
        CacheManager.clearNegativeCache()
        val apollo503Res = MediaResolver.resolveAniListIdForMalId(44444, MediaType.ANIME)
        val apollo503Cached = CacheManager.isNegativeCached("neg_mal_44444")

        // -------------------------------------------------------------
        // Print Formatted Report Summary
        // -------------------------------------------------------------
        fun nsToMs(ns: Long): Double = Math.round(ns / 10_000.0) / 100.0

        println("\n>>> [1] ID RESOLUTION LATENCY (MISS) <<<")
        println("Legacy: Median=" + nsToMs(percentile(legacyIdMissLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(legacyIdMissLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(legacyIdMissLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(legacyIdMissLatencies, 0.99)) + "ms")
        println("Apollo: Median=" + nsToMs(percentile(apolloIdMissLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(apolloIdMissLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(apolloIdMissLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(apolloIdMissLatencies, 0.99)) + "ms")

        println("\n>>> [2] ID RESOLUTION LATENCY (HIT) <<<")
        println("Legacy: Median=" + nsToMs(percentile(legacyIdHitLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(legacyIdHitLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(legacyIdHitLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(legacyIdHitLatencies, 0.99)) + "ms")
        println("Apollo: Median=" + nsToMs(percentile(apolloIdHitLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(apolloIdHitLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(apolloIdHitLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(apolloIdHitLatencies, 0.99)) + "ms")

        println("\n>>> [3] SEARCH LATENCY (APOLLO) <<<")
        println("Miss: Median=" + nsToMs(percentile(apolloSearchMissLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(apolloSearchMissLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(apolloSearchMissLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(apolloSearchMissLatencies, 0.99)) + "ms")
        println("Hit:  Median=" + nsToMs(percentile(apolloSearchHitLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(apolloSearchHitLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(apolloSearchHitLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(apolloSearchHitLatencies, 0.99)) + "ms")

        println("\n>>> [4] MEDIA DETAIL LATENCY (APOLLO) <<<")
        println("Miss: Median=" + nsToMs(percentile(apolloDetailMissLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(apolloDetailMissLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(apolloDetailMissLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(apolloDetailMissLatencies, 0.99)) + "ms")
        println("Hit:  Median=" + nsToMs(percentile(apolloDetailHitLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(apolloDetailHitLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(apolloDetailHitLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(apolloDetailHitLatencies, 0.99)) + "ms")

        println("\n>>> [5] CHARACTER PROFILE LATENCY (APOLLO) <<<")
        println("Miss: Median=" + nsToMs(percentile(apolloCharMissLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(apolloCharMissLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(apolloCharMissLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(apolloCharMissLatencies, 0.99)) + "ms")
        println("Hit:  Median=" + nsToMs(percentile(apolloCharHitLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(apolloCharHitLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(apolloCharHitLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(apolloCharHitLatencies, 0.99)) + "ms")

        println("\n>>> [6] STUDIO FILMOGRAPHY LATENCY (APOLLO) <<<")
        println("Miss: Median=" + nsToMs(percentile(apolloStudioMissLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(apolloStudioMissLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(apolloStudioMissLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(apolloStudioMissLatencies, 0.99)) + "ms")
        println("Hit:  Median=" + nsToMs(percentile(apolloStudioHitLatencies, 0.5)) + "ms, p90=" + nsToMs(percentile(apolloStudioHitLatencies, 0.90)) + "ms, p95=" + nsToMs(percentile(apolloStudioHitLatencies, 0.95)) + "ms, p99=" + nsToMs(percentile(apolloStudioHitLatencies, 0.99)) + "ms")

        println("\n>>> [7] CONCURRENCY & DEDUPLICATION (50 Callers) <<<")
        println("Legacy: Callers=" + legacyConcurrencyCalls + " -> HttpRequests=" + legacyHttpRequests + ", Time=" + legacyConcurrencyDuration + "ms")
        println("Apollo: Callers=" + apolloConcurrencyCalls + " -> HttpRequests=" + apolloHttpRequests + ", DedupedInFlight=" + apolloDeduped + ", Time=" + apolloConcurrencyDuration + "ms")

        println("\n>>> [8] NEGATIVE CACHE INCORRECTNESS COMPARISON <<<")
        println("Legacy Timeout Negative-Cached: " + legacyTimeoutCached + " (Expected true = INCORRECT REGRESSION IN LEGACY)")
        println("Apollo Timeout Negative-Cached: " + apolloTimeoutCached + " (Expected false = CORRECT)")
        println("Legacy 429 Negative-Cached: " + legacy429Cached + " (Expected true = INCORRECT REGRESSION IN LEGACY)")
        println("Apollo 429 Negative-Cached: " + apollo429Cached + " (Expected false = CORRECT)")
        println("Legacy 503 Negative-Cached: " + legacy503Cached + " (Expected true = INCORRECT REGRESSION IN LEGACY)")
        println("Apollo 503 Negative-Cached: " + apollo503Cached + " (Expected false = CORRECT)")

        assertTrue("Legacy must exhibit duplicate HTTP requests due to lack of deduplication", legacyHttpRequests > 1)
        assertEquals(1, apolloHttpRequests)
        assertEquals(49L, apolloDeduped)
        assertFalse(apolloTimeoutCached)
        assertFalse(apollo429Cached)
        assertFalse(apollo503Cached)
        assertTrue(legacyTimeoutCached)
        assertTrue(legacy429Cached)
        assertTrue(legacy503Cached)
    }
}
