package com.canim.app.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [MalRequestInterceptor] verifying HTTP-layer cooldown policy:
 * 1. Non-blocking 429 cooldown: returns HTTP 429 immediately without calling backend.
 * 2. 429 from server arms cooldown and returns 429 response immediately.
 * 3. Normal request passes through without interference.
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
    fun `server 429 — arms cooldown and returns 429 immediately`() {
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

    @Test
    fun `normal request — passes through without interference`() {
        val (client, callCount) = createClient { req, _ ->
            Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"status\":\"ok\"}".toResponseBody(jsonMedia)).build()
        }

        val request = Request.Builder().url("https://api.myanimelist.net/v2/anime/1").get().build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(1, callCount.get())
    }
}
