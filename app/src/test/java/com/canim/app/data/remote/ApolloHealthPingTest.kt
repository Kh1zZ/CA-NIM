package com.canim.app.data.remote

import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.remote.anilist.AniListApolloClient
import com.canim.app.data.repository.CanimRepository
import com.canim.app.data.repository.MalAuthManager
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
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ApolloHealthPingTest {

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
                .body("""{"data": {"Media": {"id": 1}}}""".toResponseBody(jsonMediaType))
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
    }

    @After
    fun tearDown() {
        AniListApolloClient.setOkHttpClientForTesting(null)
        AniListClient.setClientForTesting(null)
    }

    // 1. Successful health response
    @Test
    fun test1_SuccessfulHealthResponse() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data": {"Media": {"id": 1}}}""".toResponseBody(jsonMediaType))
                .build()
        }

        val isHealthy = AniListApolloClient.pingHealth()
        assertTrue("Apollo pingHealth must return true when Media(id: 1) is returned", isHealthy)

        // Verify request payload was constructed properly by Apollo
        assertEquals(1, mockInterceptor.capturedBodies.size)
        val body = mockInterceptor.capturedBodies[0]
        assertTrue("Payload should contain operationName HealthPing", body.contains("HealthPing"))
    }

    // 2. Transient failure (1st fails with 503, retry succeeds with 200)
    @Test
    fun test2_TransientFailureAndRecovery() = runBlocking {
        mockInterceptor.handler = { req, count ->
            if (count == 1) {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("Transient drop".toResponseBody("text/plain".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"data": {"Media": {"id": 1}}}""".toResponseBody(jsonMediaType))
                    .build()
            }
        }

        val app = RuntimeEnvironment.getApplication()
        val storage = MalSecureStorage(app)
        val malAuthManager = MalAuthManager(storage)
        val repository = CanimRepository(malAuthManager)

        // CanimRepository should tolerate transient drop and recover on retry
        val isUnavailable = repository.isAniListUnavailable()
        assertFalse("AniList should be declared available after surviving transient drop", isUnavailable)
        assertEquals(2, mockInterceptor.callCount.get())
    }

    // 3. Persistent failure (all calls fail with 503)
    @Test
    fun test3_PersistentFailure() = runBlocking {
        mockInterceptor.handler = { req, _ ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Service Unavailable")
                .body("Down".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val directHealth = AniListApolloClient.pingHealth()
        assertFalse("Direct pingHealth must return false during 503 outage", directHealth)

        val app = RuntimeEnvironment.getApplication()
        val storage = MalSecureStorage(app)
        val malAuthManager = MalAuthManager(storage)
        val repository = CanimRepository(malAuthManager)

        val isUnavailable = repository.isAniListUnavailable()
        assertTrue("Repository must report unavailable during persistent outage", isUnavailable)
    }

    // 4. Timeout handling
    @Test
    fun test4_TimeoutHandling() = runBlocking {
        mockInterceptor.handler = { _, _ ->
            throw SocketTimeoutException("Read timed out")
        }

        val isHealthy = AniListApolloClient.pingHealth()
        assertFalse("pingHealth must return false on SocketTimeoutException without throwing", isHealthy)
    }

    // 5. Network error handling
    @Test
    fun test5_NetworkErrorHandling() = runBlocking {
        mockInterceptor.handler = { _, _ ->
            throw IOException("Connection refused by peer")
        }

        val isHealthy = AniListApolloClient.pingHealth()
        assertFalse("pingHealth must return false on IOException without throwing", isHealthy)
    }

    // 6. Cancellation propagation
    @Test
    fun test6_CancellationPropagation() = runBlocking {
        val job = launch(Dispatchers.Default) {
            AniListApolloClient.pingHealth()
        }
        job.cancelAndJoin()
        assertTrue("Job must be cancelled cleanly", job.isCancelled)

        // Cancellation within an active scope must throw CancellationException
        var cancellationCaught = false
        try {
            coroutineScope {
                cancel()
                AniListApolloClient.pingHealth()
            }
        } catch (e: CancellationException) {
            cancellationCaught = true
        }
        assertTrue("pingHealth must propagate CancellationException", cancellationCaught)
    }
}
