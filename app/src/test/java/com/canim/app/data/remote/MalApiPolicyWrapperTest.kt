package com.canim.app.data.remote

import com.canim.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [MalApiPolicyWrapper] verifying:
 * 1. Semaphore concurrency limit (maxConcurrent = 3) is enforced at the coroutine layer.
 * 2. Permits are released before backoff delay so retrying requests do not starve concurrent slots.
 * 3. Idempotent GET requests retry on 5xx and timeout up to 2 times via coroutines without Thread.sleep.
 * 4. Mutations (PUT/DELETE/POST) are never retried on 5xx or timeout.
 * 5. Full-stack integration through real Retrofit + OkHttp + MalRequestInterceptor + MalApiPolicyWrapper.
 */
private val jsonMedia = "application/json; charset=utf-8".toMediaType()

@OptIn(ExperimentalCoroutinesApi::class)
class MalApiPolicyWrapperTest {

    private val testDispatcher = StandardTestDispatcher()

    /** Stub implementation of [MalApiService] with configurable handlers for testing. */
    private open class FakeMalApiService : MalApiService {
        var rankingHandler: (suspend (Int) -> Response<MalAnimeListResponse>)? = null
        var updateStatusHandler: (suspend (Int) -> Response<ResponseBody>)? = null
        var userProfileHandler: (suspend (Int) -> MalUserProfile)? = null

        val rankingCallCount = AtomicInteger(0)
        val updateStatusCallCount = AtomicInteger(0)
        val userProfileCallCount = AtomicInteger(0)

        override suspend fun exchangeToken(clientId: String, code: String, codeVerifier: String, grantType: String, redirectUri: String): MalTokenResponse = error("stub")
        override suspend fun refreshToken(clientId: String, refreshToken: String, grantType: String): MalTokenResponse = error("stub")

        override suspend fun getUserProfile(authHeader: String, fields: String): MalUserProfile {
            val count = userProfileCallCount.incrementAndGet()
            val handler = userProfileHandler ?: return MalUserProfile(1, "test", null, null, null)
            return handler(count)
        }

        override suspend fun getUserAnimeList(authHeader: String, limit: Int, offset: Int, fields: String, nsfw: Boolean): MalAnimeListResponse = error("stub")
        override suspend fun getUserMangaList(authHeader: String, limit: Int, offset: Int, fields: String, nsfw: Boolean): MalMangaListResponse = error("stub")

        override suspend fun updateAnimeStatus(
            authHeader: String, animeId: Int, status: String?, score: Int?,
            numEpisodesWatched: Int?, isRewatching: Boolean?, numTimesRewatched: Int?,
            priority: Int?, comments: String?, tags: String?, startDate: String?, finishDate: String?
        ): Response<ResponseBody> {
            val count = updateStatusCallCount.incrementAndGet()
            val handler = updateStatusHandler ?: return Response.success("{}".toResponseBody(jsonMedia))
            return handler(count)
        }

        override suspend fun deleteAnimeFromList(authHeader: String, animeId: Int): Response<ResponseBody> = error("stub")
        override suspend fun updateMangaStatus(authHeader: String, mangaId: Int, status: String?, score: Int?, numChaptersRead: Int?, numVolumesRead: Int?, isRereading: Boolean?, numTimesReread: Int?, priority: Int?, comments: String?, tags: String?, startDate: String?, finishDate: String?): Response<ResponseBody> = error("stub")
        override suspend fun deleteMangaFromList(authHeader: String, mangaId: Int): Response<ResponseBody> = error("stub")
        override suspend fun getAnimeDetailFallback(clientId: String, animeId: Int, fields: String): Response<MalAnimeNode> = error("stub")
        override suspend fun getMangaDetailFallback(clientId: String, mangaId: Int, fields: String): Response<MalMangaNode> = error("stub")
        override suspend fun getAnimeDetailAuth(authHeader: String, animeId: Int, fields: String): Response<MalAnimeNode> = error("stub")
        override suspend fun getMangaDetailAuth(authHeader: String, mangaId: Int, fields: String): Response<MalMangaNode> = error("stub")

        override suspend fun getAnimeRanking(clientId: String, rankingType: String, limit: Int, offset: Int, fields: String): Response<MalAnimeListResponse> {
            val count = rankingCallCount.incrementAndGet()
            val handler = rankingHandler ?: return Response.success(MalAnimeListResponse(emptyList(), null))
            return handler(count)
        }

        override suspend fun getMangaRanking(clientId: String, rankingType: String, limit: Int, offset: Int, fields: String): Response<MalMangaListResponse> = error("stub")
        override suspend fun searchAnime(clientId: String, query: String, limit: Int, offset: Int, fields: String): Response<MalAnimeListResponse> = error("stub")
        override suspend fun searchManga(clientId: String, query: String, limit: Int, offset: Int, fields: String): Response<MalMangaListResponse> = error("stub")
        override suspend fun getSeasonalAnime(clientId: String, year: Int, season: String, limit: Int, offset: Int, fields: String): Response<MalAnimeListResponse> = error("stub")
    }

    private lateinit var fakeService: FakeMalApiService
    private lateinit var policy: RequestPolicy

    @Before
    fun setUp() {
        fakeService = FakeMalApiService()
        policy = RequestPolicy(
            maxConcurrent = 3,
            maxRetries = 2,
            baseBackoffMs = 0L,
            maxBackoffMs = 0L,
            jitterMs = 0L
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Concurrency limit (maxConcurrent = 3)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `concurrency limit — strictly enforces max 3 concurrent executions`() = runTest(testDispatcher) {
        val activeCount = AtomicInteger(0)
        val peakConcurrent = AtomicInteger(0)

        fakeService.rankingHandler = {
            val current = activeCount.incrementAndGet()
            peakConcurrent.updateAndGet { peak -> maxOf(peak, current) }
            delay(50L)
            activeCount.decrementAndGet()
            Response.success(MalAnimeListResponse(emptyList(), null))
        }

        val wrapper = MalApiPolicyWrapper(fakeService, policy)

        // Launch 6 concurrent requests
        val deferreds = (1..6).map {
            async {
                wrapper.getAnimeRanking("clientId")
            }
        }
        deferreds.awaitAll()

        assertEquals("Total calls executed", 6, fakeService.rankingCallCount.get())
        assertTrue("Peak concurrent must not exceed 3 (was ${peakConcurrent.get()})", peakConcurrent.get() <= 3)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Permit release before backoff delay
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `permit is released before backoff delay — sleeping retry does not block other requests`() = runTest(testDispatcher) {
        val policyWithBackoff = RequestPolicy(
            maxConcurrent = 1, // Only 1 permit available!
            maxRetries = 2,
            baseBackoffMs = 500L,
            maxBackoffMs = 500L,
            jitterMs = 0L
        )

        var call2CompletedWhileCall1Delayed = false

        fakeService.rankingHandler = { count ->
            if (count == 1) {
                // Call 1 fails on attempt 1, will backoff for 500ms
                Response.error(503, "Unavailable".toResponseBody(jsonMedia))
            } else {
                Response.success(MalAnimeListResponse(emptyList(), null))
            }
        }

        fakeService.userProfileHandler = {
            // Call 2 executes while Call 1 is in backoff
            call2CompletedWhileCall1Delayed = true
            MalUserProfile(1, "test", null, null, null)
        }

        val wrapper = MalApiPolicyWrapper(fakeService, policyWithBackoff)

        // Launch Call 1 (which will fail and delay 500ms before retry)
        val job1 = launch {
            wrapper.getAnimeRanking("clientId")
        }

        // Advance slightly so Call 1 attempts and enters backoff
        testScheduler.advanceTimeBy(10L)

        // Now launch Call 2 — if permit was held during Call 1's backoff, Call 2 would be blocked!
        val job2 = launch {
            wrapper.getUserProfile("Bearer token")
        }

        testScheduler.advanceTimeBy(10L)

        assertTrue(
            "Call 2 must acquire permit and complete while Call 1 is in backoff delay",
            call2CompletedWhileCall1Delayed
        )

        job1.cancel()
        job2.cancel()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Idempotent GET retry (5xx & Timeout) via Coroutines
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `idempotent GET — retries 5xx up to 2 times (total 3 attempts) then returns 5xx`() = runTest(testDispatcher) {
        fakeService.rankingHandler = {
            Response.error(503, "Unavailable".toResponseBody(jsonMedia))
        }

        val wrapper = MalApiPolicyWrapper(fakeService, policy)
        val response = wrapper.getAnimeRanking("clientId")

        assertEquals(503, response.code())
        assertEquals("1 initial + 2 retries = 3 attempts", 3, fakeService.rankingCallCount.get())
    }

    @Test
    fun `idempotent GET — succeeds on retry attempt 2`() = runTest(testDispatcher) {
        fakeService.rankingHandler = { count ->
            if (count == 1) {
                Response.error(500, "Error".toResponseBody(jsonMedia))
            } else {
                Response.success(MalAnimeListResponse(emptyList(), null))
            }
        }

        val wrapper = MalApiPolicyWrapper(fakeService, policy)
        val response = wrapper.getAnimeRanking("clientId")

        assertTrue(response.isSuccessful)
        assertEquals("Succeeded on attempt 2", 2, fakeService.rankingCallCount.get())
    }

    @Test
    fun `idempotent GET — retries timeout up to 2 times then throws`() = runTest(testDispatcher) {
        fakeService.rankingHandler = {
            throw SocketTimeoutException("Read timed out")
        }

        val wrapper = MalApiPolicyWrapper(fakeService, policy)
        try {
            wrapper.getAnimeRanking("clientId")
            fail("Expected SocketTimeoutException")
        } catch (e: SocketTimeoutException) {
            assertEquals("3 attempts made before throwing", 3, fakeService.rankingCallCount.get())
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Mutations (PUT/DELETE/POST) are NEVER retried
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `PUT mutation — never retried on 5xx`() = runTest(testDispatcher) {
        fakeService.updateStatusHandler = {
            Response.error(500, "Server error".toResponseBody(jsonMedia))
        }

        val wrapper = MalApiPolicyWrapper(fakeService, policy)
        val response = wrapper.updateAnimeStatus("Bearer token", 1, status = "completed")

        assertEquals(500, response.code())
        assertEquals("Mutation must be attempted only once", 1, fakeService.updateStatusCallCount.get())
    }

    @Test
    fun `PUT mutation — never retried on timeout`() = runTest(testDispatcher) {
        fakeService.updateStatusHandler = {
            throw SocketTimeoutException("Read timed out")
        }

        val wrapper = MalApiPolicyWrapper(fakeService, policy)
        try {
            wrapper.updateAnimeStatus("Bearer token", 1, status = "completed")
            fail("Expected SocketTimeoutException")
        } catch (e: SocketTimeoutException) {
            assertEquals("Mutation must be attempted only once", 1, fakeService.updateStatusCallCount.get())
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Integration: Wrapper + Interceptor + Real Retrofit / OkHttp Stack
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `integration — idempotent GET through wrapper and interceptor executes exactly 3 attempts on 500`() = runTest(testDispatcher) {
        val actualNetworkCalls = AtomicInteger(0)
        val mockBackend = Interceptor { chain ->
            actualNetworkCalls.incrementAndGet()
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body("{\"error\":\"internal_server_error\"}".toResponseBody(jsonMedia))
                .build()
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(MalRequestInterceptor(policy))
            .addInterceptor(mockBackend)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.myanimelist.net/v2/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val realRetrofitService = retrofit.create(MalApiService::class.java)
        val wrapper = MalApiPolicyWrapper(realRetrofitService, policy)

        val response = wrapper.getAnimeRanking("test_client_id")

        assertEquals(500, response.code())
        assertEquals("GET request through wrapper and interceptor must execute exactly 1 initial + 2 retries = 3 attempts total", 3, actualNetworkCalls.get())
    }

    @Test
    fun `integration — direct model GET through wrapper and interceptor throws HttpException after exactly 3 attempts`() = runTest(testDispatcher) {
        val actualNetworkCalls = AtomicInteger(0)
        val mockBackend = Interceptor { chain ->
            actualNetworkCalls.incrementAndGet()
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Service Unavailable")
                .body("{\"error\":\"service_unavailable\"}".toResponseBody(jsonMedia))
                .build()
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(MalRequestInterceptor(policy))
            .addInterceptor(mockBackend)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.myanimelist.net/v2/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val realRetrofitService = retrofit.create(MalApiService::class.java)
        val wrapper = MalApiPolicyWrapper(realRetrofitService, policy)

        try {
            wrapper.getUserProfile("Bearer test_token")
            fail("Expected HttpException after retries exhausted")
        } catch (e: HttpException) {
            assertEquals(503, e.code())
            assertEquals("Direct model GET must throw HttpException after exactly 3 attempts", 3, actualNetworkCalls.get())
        }
    }

    @Test
    fun `integration — mutation through wrapper and interceptor never retries and executes exactly 1 attempt`() = runTest(testDispatcher) {
        val actualNetworkCalls = AtomicInteger(0)
        val mockBackend = Interceptor { chain ->
            actualNetworkCalls.incrementAndGet()
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Server Error")
                .body("{}".toResponseBody(jsonMedia))
                .build()
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(MalRequestInterceptor(policy))
            .addInterceptor(mockBackend)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.myanimelist.net/v2/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val realRetrofitService = retrofit.create(MalApiService::class.java)
        val wrapper = MalApiPolicyWrapper(realRetrofitService, policy)

        val response = wrapper.updateAnimeStatus("Bearer test_token", 1, status = "completed")

        assertEquals(500, response.code())
        assertEquals("Mutation must NEVER be retried; exactly 1 attempt", 1, actualNetworkCalls.get())
    }

    @Test
    fun `integration — cooldown active short-circuits network calls to 0 attempts`() = runTest(testDispatcher) {
        policy.armCooldown(30_000L) // 30 seconds cooldown armed

        val actualNetworkCalls = AtomicInteger(0)
        val mockBackend = Interceptor { chain ->
            actualNetworkCalls.incrementAndGet()
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody(jsonMedia))
                .build()
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(MalRequestInterceptor(policy))
            .addInterceptor(mockBackend)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.myanimelist.net/v2/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val realRetrofitService = retrofit.create(MalApiService::class.java)
        val wrapper = MalApiPolicyWrapper(realRetrofitService, policy)

        try {
            wrapper.getUserProfile("Bearer test_token")
            fail("Expected 429 HttpException during cooldown")
        } catch (e: HttpException) {
            assertEquals(429, e.code())
            assertEquals("Network backend must not be touched during active cooldown", 0, actualNetworkCalls.get())
        }
    }
}
