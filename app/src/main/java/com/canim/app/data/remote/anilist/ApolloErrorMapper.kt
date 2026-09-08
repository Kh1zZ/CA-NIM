package com.canim.app.data.remote.anilist

import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.canim.app.data.remote.AniListErrorDetail
import com.canim.app.data.remote.AniListMetrics
import com.canim.app.data.remote.AniListResult
import kotlinx.coroutines.CancellationException
import java.net.SocketTimeoutException

/**
 * Centralized error mapper and execution helper for Apollo GraphQL calls.
 * Maps Apollo-specific and network exceptions into CA'NIM's [AniListResult] semantics,
 * tracks appropriate performance/failure metrics, and preserves coroutine cancellation.
 */
object ApolloErrorMapper {

    /**
     * Converts any caught [Throwable] into an appropriate [AniListResult] failure state.
     * Re-throws [CancellationException] to preserve coroutine structured concurrency.
     */
    fun toAniListResult(throwable: Throwable): AniListResult<Nothing> {
        if (throwable is CancellationException) throw throwable

        return when (throwable) {
            is ApolloHttpException -> {
                when (throwable.statusCode) {
                    429 -> {
                        AniListMetrics.recordRateLimit()
                        val retryAfterSec = throwable.headers
                            .firstOrNull { it.name.equals("Retry-After", ignoreCase = true) }
                            ?.value?.trim()?.toLongOrNull() ?: 60L
                        AniListResult.RateLimited(retryAfterSec)
                    }
                    404 -> AniListResult.NotFound
                    in 500..599 -> {
                        AniListMetrics.recordHttp5xx()
                        AniListResult.HttpError(
                            throwable.statusCode,
                            throwable.message ?: "HTTP Error ${throwable.statusCode}",
                            emptyList()
                        )
                    }
                    else -> AniListResult.HttpError(
                        throwable.statusCode,
                        throwable.message ?: "HTTP Error ${throwable.statusCode}",
                        emptyList()
                    )
                }
            }
            is ApolloNetworkException -> {
                val cause = throwable.cause
                if (cause is SocketTimeoutException) {
                    AniListMetrics.recordTimeout()
                    AniListResult.Timeout(isReadTimeout = true)
                } else {
                    AniListResult.NetworkError(cause ?: throwable)
                }
            }
            is ApolloException -> {
                val cause = throwable.cause
                if (cause is SocketTimeoutException) {
                    AniListMetrics.recordTimeout()
                    AniListResult.Timeout(isReadTimeout = true)
                } else {
                    AniListResult.NetworkError(cause ?: throwable)
                }
            }
            is SocketTimeoutException -> {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            }
            else -> {
                val cause = throwable.cause
                if (cause is SocketTimeoutException) {
                    AniListMetrics.recordTimeout()
                    AniListResult.Timeout(isReadTimeout = true)
                } else {
                    AniListResult.NetworkError(throwable)
                }
            }
        }
    }

    /**
     * Inspects Apollo GraphQL errors and maps to [AniListResult.NotFound] or [AniListResult.GraphQLError].
     * Returns null if there are no errors.
     */
    fun handleGraphQLErrors(errors: List<Error>?): AniListResult<Nothing>? {
        if (errors.isNullOrEmpty()) return null
        if (errors.any { it.message.trim().trimEnd('.').equals("Not Found", ignoreCase = true) }) {
            return AniListResult.NotFound
        }
        AniListMetrics.recordGraphQLError()
        val errorDetails = errors.map { AniListErrorDetail(it.message, null) }
        return AniListResult.GraphQLError(errorDetails)
    }

    /**
     * Wraps an Apollo query execution and maps any thrown exceptions cleanly into [AniListResult].
     */
    inline fun <T> safeApolloCall(block: () -> AniListResult<T>): AniListResult<T> {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            toAniListResult(e)
        }
    }

    /**
     * Extracts the `Retry-After` header value (in **milliseconds**) from an [ApolloHttpException].
     * Returns 0 if the header is absent or the exception is not HTTP-based.
     */
    fun parseRetryAfterMs(exception: Throwable): Long {
        if (exception !is ApolloHttpException) return 0L
        val headerSeconds = exception.headers
            .firstOrNull { it.name.equals("Retry-After", ignoreCase = true) }
            ?.value?.trim()?.toLongOrNull() ?: return 0L
        return headerSeconds * 1000L
    }
}

