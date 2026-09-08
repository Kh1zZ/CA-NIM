package com.canim.app.data.remote

import com.canim.app.data.metrics.AppMetrics
import com.canim.app.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withPermit
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.min
import kotlin.random.Random

/**
 * Decorator around [MalApiService] that enforces the centralized MAL [RequestPolicy] at the coroutine layer:
 *
 * 1. **Concurrency limiting**: Enforces [policy.semaphore] (maxConcurrent = 3) on every network attempt.
 * 2. **Short-lived permit holding**: The semaphore permit is acquired ONLY for the duration of the
 *    network call and is released immediately before any backoff delay.
 * 3. **Non-blocking coroutine retry**: Transient failures (SocketTimeoutException, 5xx) on idempotent
 *    GET requests are retried up to 2 times with exponential backoff + jitter using coroutine [delay],
 *    without blocking OkHttp dispatcher threads with Thread.sleep.
 * 4. **Mutation safety**: Mutations (PUT, DELETE, POST) pass `retryable = false` and are NEVER retried.
 */
internal class MalApiPolicyWrapper(
    private val delegate: MalApiService,
    private val policy: RequestPolicy
) : MalApiService {

    private suspend fun <T> executeWithPolicy(
        retryable: Boolean = true,
        operation: String = "api",
        call: suspend () -> T
    ): T {
        AppMetrics.recordRequest("myanimelist", operation)
        val startNs = System.nanoTime()
        var attempt = 0
        try {
            while (true) {
                val result = try {
                    policy.semaphore.withPermit {
                        call()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SocketTimeoutException) {
                    AppMetrics.recordTimeout("myanimelist", operation)
                    if (!retryable || attempt >= policy.maxRetries) throw e
                    attempt++
                    AppMetrics.recordRetry("myanimelist", operation)
                    val delayMs = calculateBackoff(attempt)
                    if (delayMs > 0L) delay(delayMs)
                    continue
                } catch (e: IOException) {
                    if (!retryable || attempt >= policy.maxRetries) throw e
                    attempt++
                    AppMetrics.recordRetry("myanimelist", operation)
                    val delayMs = calculateBackoff(attempt)
                    if (delayMs > 0L) delay(delayMs)
                    continue
                } catch (e: HttpException) {
                    if (e.code() in 500..599) {
                        AppMetrics.recordHttp5xx("myanimelist", operation, e.code())
                    }
                    if (!retryable || e.code() !in 500..599 || attempt >= policy.maxRetries) throw e
                    attempt++
                    AppMetrics.recordRetry("myanimelist", operation)
                    val delayMs = calculateBackoff(attempt)
                    if (delayMs > 0L) delay(delayMs)
                    continue
                }

                // If result is Retrofit Response<*>, retry on HTTP 5xx
                if (result is Response<*> && result.code() in 500..599) {
                    AppMetrics.recordHttp5xx("myanimelist", operation, result.code())
                    if (retryable && attempt < policy.maxRetries) {
                        attempt++
                        AppMetrics.recordRetry("myanimelist", operation)
                        val delayMs = calculateBackoff(attempt)
                        if (delayMs > 0L) delay(delayMs)
                        continue
                    }
                }

                return result
            }
        } finally {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000L
            AppMetrics.recordLatency("myanimelist", operation, durationMs)
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        if (policy.baseBackoffMs <= 0L) return 0L
        val backoff = min(policy.baseBackoffMs * (1L shl (attempt - 1)), policy.maxBackoffMs)
        val jitter = if (policy.jitterMs > 0L) Random.nextLong(-policy.jitterMs, policy.jitterMs + 1) else 0L
        return maxOf(0L, backoff + jitter)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auth & Token (Mutations — NEVER retried)
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun exchangeToken(
        clientId: String,
        code: String,
        codeVerifier: String,
        grantType: String,
        redirectUri: String
    ): MalTokenResponse = executeWithPolicy(retryable = false, operation = "exchangeToken") {
        delegate.exchangeToken(clientId, code, codeVerifier, grantType, redirectUri)
    }

    override suspend fun refreshToken(
        clientId: String,
        refreshToken: String,
        grantType: String
    ): MalTokenResponse = executeWithPolicy(retryable = false, operation = "refreshToken") {
        delegate.refreshToken(clientId, refreshToken, grantType)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // User Profile & Library (Idempotent GET — Retryable)
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun getUserProfile(
        authHeader: String,
        fields: String
    ): MalUserProfile = executeWithPolicy(retryable = true, operation = "getUserProfile") {
        delegate.getUserProfile(authHeader, fields)
    }

    override suspend fun getUserAnimeList(
        authHeader: String,
        limit: Int,
        offset: Int,
        fields: String,
        nsfw: Boolean
    ): MalAnimeListResponse = executeWithPolicy(retryable = true, operation = "getUserAnimeList") {
        delegate.getUserAnimeList(authHeader, limit, offset, fields, nsfw)
    }

    override suspend fun getUserMangaList(
        authHeader: String,
        limit: Int,
        offset: Int,
        fields: String,
        nsfw: Boolean
    ): MalMangaListResponse = executeWithPolicy(retryable = true, operation = "getUserMangaList") {
        delegate.getUserMangaList(authHeader, limit, offset, fields, nsfw)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tracking Mutations (PUT / DELETE — NEVER retried)
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun updateAnimeStatus(
        authHeader: String,
        animeId: Int,
        status: String?,
        score: Int?,
        numEpisodesWatched: Int?,
        isRewatching: Boolean?,
        numTimesRewatched: Int?,
        priority: Int?,
        comments: String?,
        tags: String?,
        startDate: String?,
        finishDate: String?
    ): Response<ResponseBody> = executeWithPolicy(retryable = false) {
        delegate.updateAnimeStatus(
            authHeader, animeId, status, score, numEpisodesWatched,
            isRewatching, numTimesRewatched, priority, comments, tags, startDate, finishDate
        )
    }

    override suspend fun deleteAnimeFromList(
        authHeader: String,
        animeId: Int
    ): Response<ResponseBody> = executeWithPolicy(retryable = false) {
        delegate.deleteAnimeFromList(authHeader, animeId)
    }

    override suspend fun updateMangaStatus(
        authHeader: String,
        mangaId: Int,
        status: String?,
        score: Int?,
        numChaptersRead: Int?,
        numVolumesRead: Int?,
        isRereading: Boolean?,
        numTimesReread: Int?,
        priority: Int?,
        comments: String?,
        tags: String?,
        startDate: String?,
        finishDate: String?
    ): Response<ResponseBody> = executeWithPolicy(retryable = false) {
        delegate.updateMangaStatus(
            authHeader, mangaId, status, score, numChaptersRead,
            numVolumesRead, isRereading, numTimesReread, priority, comments, tags, startDate, finishDate
        )
    }

    override suspend fun deleteMangaFromList(
        authHeader: String,
        mangaId: Int
    ): Response<ResponseBody> = executeWithPolicy(retryable = false) {
        delegate.deleteMangaFromList(authHeader, mangaId)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Details & Rankings (Idempotent GET — Retryable)
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun getAnimeDetailFallback(
        clientId: String,
        animeId: Int,
        fields: String
    ): Response<MalAnimeNode> = executeWithPolicy(retryable = true) {
        delegate.getAnimeDetailFallback(clientId, animeId, fields)
    }

    override suspend fun getMangaDetailFallback(
        clientId: String,
        mangaId: Int,
        fields: String
    ): Response<MalMangaNode> = executeWithPolicy(retryable = true) {
        delegate.getMangaDetailFallback(clientId, mangaId, fields)
    }

    override suspend fun getAnimeDetailAuth(
        authHeader: String,
        animeId: Int,
        fields: String
    ): Response<MalAnimeNode> = executeWithPolicy(retryable = true) {
        delegate.getAnimeDetailAuth(authHeader, animeId, fields)
    }

    override suspend fun getMangaDetailAuth(
        authHeader: String,
        mangaId: Int,
        fields: String
    ): Response<MalMangaNode> = executeWithPolicy(retryable = true) {
        delegate.getMangaDetailAuth(authHeader, mangaId, fields)
    }

    override suspend fun getAnimeRanking(
        clientId: String,
        rankingType: String,
        limit: Int,
        offset: Int,
        fields: String
    ): Response<MalAnimeListResponse> = executeWithPolicy(retryable = true) {
        delegate.getAnimeRanking(clientId, rankingType, limit, offset, fields)
    }

    override suspend fun getMangaRanking(
        clientId: String,
        rankingType: String,
        limit: Int,
        offset: Int,
        fields: String
    ): Response<MalMangaListResponse> = executeWithPolicy(retryable = true) {
        delegate.getMangaRanking(clientId, rankingType, limit, offset, fields)
    }

    override suspend fun searchAnime(
        clientId: String,
        query: String,
        limit: Int,
        offset: Int,
        fields: String
    ): Response<MalAnimeListResponse> = executeWithPolicy(retryable = true) {
        delegate.searchAnime(clientId, query, limit, offset, fields)
    }

    override suspend fun searchManga(
        clientId: String,
        query: String,
        limit: Int,
        offset: Int,
        fields: String
    ): Response<MalMangaListResponse> = executeWithPolicy(retryable = true) {
        delegate.searchManga(clientId, query, limit, offset, fields)
    }

    override suspend fun getSeasonalAnime(
        clientId: String,
        year: Int,
        season: String,
        limit: Int,
        offset: Int,
        fields: String
    ): Response<MalAnimeListResponse> = executeWithPolicy(retryable = true) {
        delegate.getSeasonalAnime(clientId, year, season, limit, offset, fields)
    }
}
