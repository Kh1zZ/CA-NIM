package com.canim.app.data.remote

import com.canim.app.data.metrics.AppMetrics
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * OkHttp [Interceptor] that enforces the MAL per-host cooldown policy at the HTTP layer:
 *
 * - Non-blocking 429 cooldown: if active, immediately returns HTTP 429 with `Retry-After`
 *   header without touching the network or blocking OkHttp dispatcher threads.
 * - On 429 response from server: arms cooldown on [policy] from `Retry-After` header and
 *   returns the 429 response immediately without consuming the body.
 * - Concurrency limiting (maxConcurrent = 3) and async idempotent retries are managed at the
 *   coroutine layer ([MalApiPolicyWrapper]) without holding or blocking dispatcher threads.
 * - Sensitive headers (Authorization, X-MAL-CLIENT-ID) are NOT logged here.
 */
internal class MalRequestInterceptor(
    private val policy: RequestPolicy
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Non-blocking 429 cooldown check before touching network
        val remaining = policy.remainingCooldownMs()
        if (remaining > 0L) {
            AppMetrics.recordRateLimit("myanimelist", "cooldown_intercepted")
            val retryAfterSeconds = maxOf(1L, (remaining + 999) / 1000)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests (cooldown active)")
                .header("Retry-After", retryAfterSeconds.toString())
                .body("{}".toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()
        }

        val response = chain.proceed(request)

        if (response.code == 429) {
            AppMetrics.recordRateLimit("myanimelist", "http_429")
            val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
            policy.armCooldown(retryAfterMs)
        }

        return response
    }
}
