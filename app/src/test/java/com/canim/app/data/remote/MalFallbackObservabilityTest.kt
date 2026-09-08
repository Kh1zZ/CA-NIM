package com.canim.app.data.remote

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.metrics.AppMetrics
import com.canim.app.data.model.*
import com.canim.app.data.repository.MalAuthManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MalFallbackObservabilityTest {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private class MockMalApiService : MalApiService {
        var animeDetailHandler: (suspend (Int) -> Response<MalAnimeNode>)? = null
        val animeDetailCallCount = AtomicInteger(0)

        override suspend fun exchangeToken(clientId: String, code: String, codeVerifier: String, grantType: String, redirectUri: String): MalTokenResponse = error("stub")
        override suspend fun refreshToken(clientId: String, refreshToken: String, grantType: String): MalTokenResponse = error("stub")
        override suspend fun getUserProfile(authHeader: String, fields: String): MalUserProfile = error("stub")
        override suspend fun getUserAnimeList(authHeader: String, limit: Int, offset: Int, fields: String, nsfw: Boolean): MalAnimeListResponse = error("stub")
        override suspend fun getUserMangaList(authHeader: String, limit: Int, offset: Int, fields: String, nsfw: Boolean): MalMangaListResponse = error("stub")
        override suspend fun updateAnimeStatus(authHeader: String, animeId: Int, status: String?, score: Int?, numEpisodesWatched: Int?, isRewatching: Boolean?, numTimesRewatched: Int?, priority: Int?, comments: String?, tags: String?, startDate: String?, finishDate: String?): Response<ResponseBody> = error("stub")
        override suspend fun deleteAnimeFromList(authHeader: String, animeId: Int): Response<ResponseBody> = error("stub")
        override suspend fun updateMangaStatus(authHeader: String, mangaId: Int, status: String?, score: Int?, numChaptersRead: Int?, numVolumesRead: Int?, isRereading: Boolean?, numTimesReread: Int?, priority: Int?, comments: String?, tags: String?, startDate: String?, finishDate: String?): Response<ResponseBody> = error("stub")
        override suspend fun deleteMangaFromList(authHeader: String, mangaId: Int): Response<ResponseBody> = error("stub")

        override suspend fun getAnimeDetailFallback(clientId: String, animeId: Int, fields: String): Response<MalAnimeNode> {
            val count = animeDetailCallCount.incrementAndGet()
            val handler = animeDetailHandler ?: return Response.success(
                MalAnimeNode(
                    id = animeId,
                    title = "Test Anime",
                    mainPicture = null,
                    numEpisodes = null,
                    status = null,
                    genres = null,
                    synopsis = null
                )
            )
            return handler(count)
        }

        override suspend fun getMangaDetailFallback(clientId: String, mangaId: Int, fields: String): Response<MalMangaNode> = error("stub")
        override suspend fun getAnimeDetailAuth(authHeader: String, animeId: Int, fields: String): Response<MalAnimeNode> = error("stub")
        override suspend fun getMangaDetailAuth(authHeader: String, mangaId: Int, fields: String): Response<MalMangaNode> = error("stub")
        override suspend fun getAnimeRanking(clientId: String, rankingType: String, limit: Int, offset: Int, fields: String): Response<MalAnimeListResponse> = error("stub")
        override suspend fun getMangaRanking(clientId: String, rankingType: String, limit: Int, offset: Int, fields: String): Response<MalMangaListResponse> = error("stub")
        override suspend fun searchAnime(clientId: String, query: String, limit: Int, offset: Int, fields: String): Response<MalAnimeListResponse> = error("stub")
        override suspend fun searchManga(clientId: String, query: String, limit: Int, offset: Int, fields: String): Response<MalMangaListResponse> = error("stub")
        override suspend fun getSeasonalAnime(clientId: String, year: Int, season: String, limit: Int, offset: Int, fields: String): Response<MalAnimeListResponse> = error("stub")
    }

    private fun createAnimeNode(
        id: Int,
        title: String = "Test Anime",
        mean: Double? = null,
        synopsis: String? = null
    ) = MalAnimeNode(
        id = id,
        title = title,
        mainPicture = null,
        numEpisodes = null,
        status = null,
        genres = null,
        synopsis = synopsis,
        mean = mean
    )

    private lateinit var mockService: MockMalApiService
    private lateinit var policy: RequestPolicy
    private lateinit var wrapper: MalApiPolicyWrapper

    @Before
    fun setUp() {
        AppMetrics.reset()
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.invalidateTracking()
        mockService = MockMalApiService()
        policy = RequestPolicy(
            maxConcurrent = 3,
            maxRetries = 2,
            baseBackoffMs = 0L,
            maxBackoffMs = 0L,
            jitterMs = 0L
        )
        wrapper = MalApiPolicyWrapper(mockService, policy)
        ApiClient.setMalApiForTesting(wrapper)
    }

    @After
    fun tearDown() {
        ApiClient.setMalApiForTesting(null)
        AppMetrics.reset()
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.invalidateTracking()
    }

    // 1. MAL success
    @Test
    fun test1_malSuccess() = runTest {
        val expectedNode = createAnimeNode(id = 12345, title = "Frieren", mean = 9.14)
        mockService.animeDetailHandler = { Response.success(expectedNode) }

        val response = wrapper.getAnimeDetailFallback("client_id", 12345)
        assertTrue(response.isSuccessful)
        assertEquals("Frieren", response.body()?.title)
        assertEquals(9.14, response.body()?.mean ?: 0.0, 0.001)
    }

    // 2. MAL timeout
    @Test
    fun test2_malTimeout() = runTest {
        mockService.animeDetailHandler = { throw SocketTimeoutException("Read timed out") }

        try {
            wrapper.getAnimeDetailFallback("client_id", 12345)
            fail("Expected SocketTimeoutException")
        } catch (e: SocketTimeoutException) {
            assertEquals("Read timed out", e.message)
        }

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.requestsByHost["myanimelist"])
        assertEquals(1L, snapshot.requestsByOperation["getAnimeDetailFallback"])
        assertTrue((snapshot.timeoutsByHost["myanimelist"] ?: 0L) >= 1L)
    }

    // 3. MAL network failure
    @Test
    fun test3_malNetworkFailure() = runTest {
        mockService.animeDetailHandler = { throw IOException("Connection reset by peer") }

        try {
            wrapper.getAnimeDetailFallback("client_id", 12345)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Connection reset by peer", e.message)
        }

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.requestsByHost["myanimelist"])
        assertEquals(2L, snapshot.retriesByHost["myanimelist"])
    }

    // 4. MAL 429
    @Test
    fun test4_mal429RateLimit() = runTest {
        mockService.animeDetailHandler = {
            Response.error(429, "{\"message\":\"Too Many Requests\"}".toResponseBody(jsonMedia))
        }

        val response = wrapper.getAnimeDetailFallback("client_id", 12345)
        assertEquals(429, response.code())

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.rateLimitsByHost["myanimelist"])
    }

    // 5. MAL 5xx retry
    @Test
    fun test5_mal5xxRetrySuccess() = runTest {
        mockService.animeDetailHandler = { attempt ->
            if (attempt == 1) {
                Response.error(503, "Service Unavailable".toResponseBody(jsonMedia))
            } else {
                Response.success(createAnimeNode(id = 12345, title = "Recovered Anime"))
            }
        }

        val response = wrapper.getAnimeDetailFallback("client_id", 12345)
        assertTrue(response.isSuccessful)
        assertEquals("Recovered Anime", response.body()?.title)
        assertEquals(2, mockService.animeDetailCallCount.get())

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.http5xxByHost["myanimelist"])
        assertEquals(1L, snapshot.retriesByHost["myanimelist"])
    }

    // 6. AniList failure -> MAL fallback success
    @Test
    fun test6_aniListFailureMalFallbackSuccess() = runTest {
        val malNode = createAnimeNode(
            id = 50265,
            title = "Spy x Family",
            mean = 8.5,
            synopsis = "A spy on an undercover mission..."
        )
        mockService.animeDetailHandler = { Response.success(malNode) }

        val app = RuntimeEnvironment.getApplication()
        val storage = MalSecureStorage(app)
        val authManager = MalAuthManager(storage)

        val fallbackDetail = authManager.getExtendedDetailFallback(50265, MediaType.ANIME)
        assertNotNull(fallbackDetail)
        assertEquals(50265, fallbackDetail?.malId)
        assertEquals("Spy x Family", fallbackDetail?.title)
        assertEquals(8.5, fallbackDetail?.malScore ?: 0.0, 0.001)
        assertTrue(fallbackDetail?.isFromFallback == true)

        val cached = CacheManager.getDetail(CacheManager.detailKey(null, 50265))
        assertNotNull(cached)
        assertEquals("Spy x Family", cached?.title)
    }

    // 7. AniList failure -> MAL fallback timeout/failure
    @Test
    fun test7_aniListFailureMalFallbackTimeout() = runTest {
        mockService.animeDetailHandler = { throw SocketTimeoutException("Read timed out") }

        val app = RuntimeEnvironment.getApplication()
        val storage = MalSecureStorage(app)
        val authManager = MalAuthManager(storage)

        val fallbackDetail = authManager.getExtendedDetailFallback(99999, MediaType.ANIME)
        assertNull(fallbackDetail)

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.requestsByHost["myanimelist"])
        assertEquals(1L, snapshot.requestsByOperation["getAnimeDetailFallback"])
        assertTrue((snapshot.timeoutsByHost["myanimelist"] ?: 0L) >= 1L)
    }

    // 8. MAL request telemetry on success
    @Test
    fun test8_malRequestTelemetryOnSuccess() = runTest {
        mockService.animeDetailHandler = {
            Response.success(createAnimeNode(id = 100, title = "Telemetry Test"))
        }

        wrapper.getAnimeDetailFallback("client_id", 100)

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(1L, snapshot.totalRequests)
        assertEquals(1L, snapshot.requestsByHost["myanimelist"])
        assertEquals(1L, snapshot.requestsByOperation["getAnimeDetailFallback"])

        val latencyStats = snapshot.latencyByHost["myanimelist"]
        assertNotNull(latencyStats)
        assertEquals(1L, latencyStats?.count)
        assertTrue((latencyStats?.maxMs ?: 0L) >= 0L)
    }

    // 9. MAL timeout telemetry
    @Test
    fun test9_malTimeoutTelemetry() = runTest {
        mockService.animeDetailHandler = { throw SocketTimeoutException("Connection timed out") }

        runCatching { wrapper.getAnimeDetailFallback("client_id", 200) }

        val snapshot = AppMetrics.getSnapshot()
        assertTrue((snapshot.timeoutsByHost["myanimelist"] ?: 0L) >= 1L)

        val latencyStats = snapshot.latencyByOperation["getAnimeDetailFallback"]
        assertNotNull(latencyStats)
        assertEquals(1L, latencyStats?.count)
    }

    // 10. Existing retry telemetry remains correct
    @Test
    fun test10_existingRetryTelemetryRemainsCorrect() = runTest {
        mockService.animeDetailHandler = { attempt ->
            if (attempt <= 2) {
                Response.error(500, "Internal Server Error".toResponseBody(jsonMedia))
            } else {
                Response.success(createAnimeNode(id = 300, title = "Retry Pass"))
            }
        }

        val result = wrapper.getAnimeDetailFallback("client_id", 300)
        assertTrue(result.isSuccessful)
        assertEquals(3, mockService.animeDetailCallCount.get())

        val snapshot = AppMetrics.getSnapshot()
        assertEquals(2L, snapshot.retriesByHost["myanimelist"])
        assertEquals(2L, snapshot.http5xxByHost["myanimelist"])
    }
}
