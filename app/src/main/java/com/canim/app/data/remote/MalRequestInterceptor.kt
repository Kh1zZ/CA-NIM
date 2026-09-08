package com.canim.app.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp [Interceptor] that enforces the MAL per-host request policy:
 *
 * - Blocks the request thread while a 429 cooldown is active (runBlocking delay).
 * - On 429: arms cooldown from `Retry-After` header, then returns the 429 response
 *   without consuming the body so callers can still inspect it.
 * - On 5xx or timeout on **idempotent** (GET) requests: retries up to 2 times with
 *   exponential backoff + jitter via [RequestPolicy].
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

        // If in cooldown, wait until it clears (block OkHttp dispatcher thread briefly).
        val remaining = policy.remainingCooldownMs()
        if (remaining > 0L) {
            runBlocking { kotlinx.coroutines.delay(remaining) }
        }

        var attempt = 0
        var lastResponse: Response? = null

        while (true) {
            val cooldown = policy.remainingCooldownMs()
            if (cooldown > 0L) {
                runBlocking { kotlinx.coroutines.delay(cooldown) }
            }

            val response = try {
                chain.proceed(request)
            } catch (e: java.net.SocketTimeoutException) {
                if (!isIdempotent || attempt >= 2) throw e
                attempt++
                runBlocking {
                    val backoff = minOf(1_000L * (1L shl (attempt - 1)), 8_000L)
                    val jitter = kotlin.random.Random.nextLong(-300L, 301L)
                    kotlinx.coroutines.delay(maxOf(0L, backoff + jitter))
                }
                continue
            } catch (e: java.io.IOException) {
                if (!isIdempotent || attempt >= 2) throw e
                attempt++
                runBlocking {
                    val backoff = minOf(1_000L * (1L shl (attempt - 1)), 8_000L)
                    val jitter = kotlin.random.Random.nextLong(-300L, 301L)
                    kotlinx.coroutines.delay(maxOf(0L, backoff + jitter))
                }
                continue
            }

            lastResponse = response

            when {
                response.code == 429 -> {
                    val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
                    policy.armCooldown(retryAfterMs)
                    return response // return 429 to caller; do NOT retry mutations or reads
                }
                response.code in 500..599 && isIdempotent && attempt < 2 -> {
                    response.close()
                    attempt++
                    runBlocking {
                        val backoff = minOf(1_000L * (1L shl (attempt - 1)), 8_000L)
                        val jitter = kotlin.random.Random.nextLong(-300L, 301L)
                        kotlinx.coroutines.delay(maxOf(0L, backoff + jitter))
                    }
                    // Note: chain.proceed cannot be called twice on the same chain;
                    // we reconstruct a new call via proceed on the same request object.
                    continue
                }
                else -> return response
            }
        }

        @Suppress("UNREACHABLE_CODE")
        return lastResponse!!
    }
}
