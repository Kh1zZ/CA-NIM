package com.canim.app.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [MalRequestInterceptor] verifying:
 * 1. Non-blocking 429 cooldown: returns HTTP 429 immediately without blocking.
 * 2. 429 from server arms cooldown and returns 429 response immediately.
 * 3. Idempotent (GET) requests retry up to 2 times on 5xx or timeout.
 * 4. Mutations (PUT/DELETE/POST) are never retried on 5xx or timeout.
 */
class MalRequestInterceptorTest {

    private var fakeNow = 0L
    private lateinit var policy: RequestPolicy
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Before
    fun setUp() {
        fakeNow = 0L
        policy = RequestPolicy(
            maxConcurrent = 3,
            maxRetries = 2,
            baseBackoffMs = 0L,
            maxBackoffMs = 0L,
            jitterMs = 0L,
            clock = { fakeNow }
        )
    }

    private fun createClient(handler: (Request, Int) -> Response): Pair<OkHttpClient, AtomicInteger> {
        val callCount = AtomicInteger(0)
        val mockBackend = Interceptor { chain ->
            val req = chain.request()
            val count = callCount.incrementAndGet()
            handler(req, count)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(MalRequestInterceptor(policy))
            .addInterceptor(mockBackend)
            .build()

        return Pair(client, callCount)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Non-blocking 429 cooldown
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `cooldown active — returns 429 immediately without calling backend`() {
        policy.armCooldown(30_000L) // 30s cooldown

        val (client, callCount) = createClient { req, _ ->
            Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody(jsonMedia)).build()
        }

        val request = Request.Builder().url("https://api.myanimelist.net/v2/anime/1").get().build()
        val response = client.newCall(request).execute()

        assertEquals("Must return 429", 429, response.code)
        assertEquals("Backend must not be touched during cooldown", 0, callCount.get())
        assertEquals("30", response.header("Retry-After"))
    }

    @Test
    fun `server 429 — arms cooldown and returns 429 without retry`() {
        val (client, callCount) = createClient { req, _ ->
            Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(429).message("Too Many Requests")
                .header("Retry-After", "15")
                .body("{}".toResponseBody(jsonMedia)).build()
        }

        val request = Request.Builder().url("https://api.myanimelist.net/v2/anime/1").get().build()
        val response = client.newCall(request).execute()

        assertEquals(429, response.code)
        assertEquals("Backend called exactly once", 1, callCount.get())
        assertTrue("Cooldown must be armed", policy.remainingCooldownMs() > 0L)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Idempotent GET retry on 5xx & Timeout
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `GET request — retries 5xx up to 2 times (total 3 attempts) then returns 5xx`() {
        val (client, callCount) = createClient { req, _ ->
            Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(503).message("Service Unavailable")
                .body("{}".toResponseBody(jsonMedia)).build()
        }

        val request = Request.Builder().url("https://api.myanimelist.net/v2/anime/1").get().build()
        val response = client.newCall(request).execute()

        assertEquals(503, response.code)
        assertEquals("1 initial + 2 retries = 3 attempts", 3, callCount.get())
    }

    @Test
    fun `GET request — succeeds on retry attempt 2`() {
        val (client, callCount) = createClient { req, count ->
            if (count < 2) {
                Response.Builder()
                    .request(req).protocol(Protocol.HTTP_1_1).code(500).message("Internal Server Error")
                    .body("{}".toResponseBody(jsonMedia)).build()
            } else {
                Response.Builder()
                    .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"status\":\"ok\"}".toResponseBody(jsonMedia)).build()
            }
        }

        val request = Request.Builder().url("https://api.myanimelist.net/v2/anime/1").get().build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("Succeeded on attempt 2", 2, callCount.get())
    }

    @Test
    fun `GET request — retries timeout up to 2 times then throws`() {
        val (client, callCount) = createClient { _, _ ->
            throw SocketTimeoutException("Read timed out")
        }

        val request = Request.Builder().url("https://api.myanimelist.net/v2/anime/1").get().build()
        try {
            client.newCall(request).execute()
            fail("Expected SocketTimeoutException")
        } catch (e: SocketTimeoutException) {
            assertEquals("3 attempts made before throwing", 3, callCount.get())
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Mutations (PUT/DELETE/POST) are NEVER retried
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `PUT mutation — never retried on 5xx`() {
        val (client, callCount) = createClient { req, _ ->
            Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(500).message("Server Error")
                .body("{}".toResponseBody(jsonMedia)).build()
        }

        val request = Request.Builder()
            .url("https://api.myanimelist.net/v2/anime/1/my_list_status")
            .put("status=completed".toRequestBody(jsonMedia))
            .build()
        val response = client.newCall(request).execute()

        assertEquals(500, response.code)
        assertEquals("Mutation must be attempted only once", 1, callCount.get())
    }

    @Test
    fun `DELETE mutation — never retried on timeout`() {
        val (client, callCount) = createClient { _, _ ->
            throw SocketTimeoutException("Read timed out")
        }

        val request = Request.Builder()
            .url("https://api.myanimelist.net/v2/anime/1/my_list_status")
            .delete()
            .build()
        try {
            client.newCall(request).execute()
            fail("Expected SocketTimeoutException")
        } catch (e: SocketTimeoutException) {
            assertEquals("Mutation must be attempted only once", 1, callCount.get())
        }
    }
}
