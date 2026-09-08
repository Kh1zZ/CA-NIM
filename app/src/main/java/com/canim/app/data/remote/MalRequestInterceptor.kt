package com.canim.app.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.min
import kotlin.random.Random

/**
 * OkHttp [Interceptor] that enforces the MAL per-host request policy:
 *
 * - Non-blocking 429 cooldown: if active, immediately returns HTTP 429 with `Retry-After`
 *   header without blocking the OkHttp dispatcher thread.
 * - On 429 response from server: arms cooldown from `Retry-After` header, returns 429 response
 *   immediately without consuming the body so callers can still inspect it.
 * - On 5xx or timeout on **idempotent** (GET) requests: retries up to 2 times with
 *   exponential backoff + jitter via [RequestPolicy] parameters.
 * - PUT/DELETE mutations are **never** retried.
 * - Sensitive headers (Authorization, X-MAL-CLIENT-ID) are NOT logged here.
 *
 * The interceptor shares the same [RequestPolicy] instance held by [ApiClient] so that
 * all callers see the same cooldown and concurrency state.
 */
internal class MalRequestInterceptor(
    private val policy: RequestPolicy
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isIdempotent = request.method == "GET"

        // Non-blocking 429 cooldown check before touching network
        val remaining = policy.remainingCooldownMs()
        if (remaining > 0L) {
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

        var attempt = 0
        var lastResponse: Response? = null

        while (true) {
            // Check cooldown again before each attempt
            val cooldown = policy.remainingCooldownMs()
            if (cooldown > 0L) {
                lastResponse?.close()
                val retryAfterSeconds = maxOf(1L, (cooldown + 999) / 1000)
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests (cooldown active)")
                    .header("Retry-After", retryAfterSeconds.toString())
                    .body("{}".toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .build()
            }

            val response = try {
                chain.proceed(request)
            } catch (e: SocketTimeoutException) {
                if (!isIdempotent || attempt >= 2) throw e
                attempt++
                sleepBackoff(attempt)
                continue
            } catch (e: IOException) {
                if (!isIdempotent || attempt >= 2) throw e
                attempt++
                sleepBackoff(attempt)
                continue
            }

            lastResponse = response

            when {
                response.code == 429 -> {
                    val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
                    policy.armCooldown(retryAfterMs)
                    return response // return 429 to caller immediately; do NOT retry mutations or reads
                }
                response.code in 500..599 && isIdempotent && attempt < 2 -> {
                    response.close()
                    attempt++
                    sleepBackoff(attempt)
                    continue
                }
                else -> return response
            }
        }

        @Suppress("UNREACHABLE_CODE")
        return lastResponse!!
    }

    private fun sleepBackoff(attempt: Int) {
        if (policy.baseBackoffMs <= 0L) return
        val backoff = min(policy.baseBackoffMs * (1L shl (attempt - 1)), policy.maxBackoffMs)
        val jitter = if (policy.jitterMs > 0L) Random.nextLong(-policy.jitterMs, policy.jitterMs + 1) else 0L
        val delayMs = maxOf(0L, backoff + jitter)
        if (delayMs > 0L) {
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}
