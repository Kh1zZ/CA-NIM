package com.canim.app.data.remote

import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.model.CastCrewProfile
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.StudioBioInfo
import com.canim.app.data.model.StudioFilmographySort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import com.canim.app.data.metrics.AppMetrics

data class AniListErrorDetail(
    val message: String,
    val status: Int? = null
)

sealed class AniListResult<out T> {
    data class Success<out T>(val data: T) : AniListResult<T>()
    object NotFound : AniListResult<Nothing>()
    data class RateLimited(val retryAfterSeconds: Long?) : AniListResult<Nothing>()
    data class HttpError(val code: Int, val message: String?, val errors: List<AniListErrorDetail>? = null) : AniListResult<Nothing>()
    data class GraphQLError(val errors: List<AniListErrorDetail>) : AniListResult<Nothing>()
    data class NetworkError(val throwable: Throwable) : AniListResult<Nothing>()
    data class Timeout(val isReadTimeout: Boolean) : AniListResult<Nothing>()
    data class ParseError(val throwable: Throwable) : AniListResult<Nothing>()

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
}

object AniListMetrics {
    private val _requestCount = AtomicLong(0)
    private val _duplicateInFlightCount = AtomicLong(0)
    private val _cacheHitCount = AtomicLong(0)
    private val _cacheMissCount = AtomicLong(0)
    private val _timeoutCount = AtomicLong(0)
    private val _rateLimitCount = AtomicLong(0)
    private val _http5xxCount = AtomicLong(0)
    private val _graphQLErrorCount = AtomicLong(0)
    private val _parseErrorCount = AtomicLong(0)
    private val _retryCount = AtomicLong(0)

    val requestCount: Long get() = _requestCount.get()
    val duplicateInFlightCount: Long get() = _duplicateInFlightCount.get()
    val cacheHitCount: Long get() = _cacheHitCount.get()
    val cacheMissCount: Long get() = _cacheMissCount.get()
    val timeoutCount: Long get() = _timeoutCount.get()
    val rateLimitCount: Long get() = _rateLimitCount.get()
    val http5xxCount: Long get() = _http5xxCount.get()
    val graphQLErrorCount: Long get() = _graphQLErrorCount.get()
    val parseErrorCount: Long get() = _parseErrorCount.get()
    val retryCount: Long get() = _retryCount.get()

    fun recordRequest() {
        _requestCount.incrementAndGet()
        AppMetrics.recordRequest("anilist", "graphql")
    }

    fun recordDuplicateInFlight() {
        _duplicateInFlightCount.incrementAndGet()
        AppMetrics.recordDeduplication("anilist", "graphql")
    }

    fun recordCacheHit() {
        _cacheHitCount.incrementAndGet()
        AppMetrics.recordCacheHit("anilist")
    }

    fun recordCacheMiss() {
        _cacheMissCount.incrementAndGet()
        AppMetrics.recordCacheMiss("anilist")
    }

    fun recordTimeout() {
        _timeoutCount.incrementAndGet()
        AppMetrics.recordTimeout("anilist", "graphql")
    }

    fun recordRateLimit() {
        _rateLimitCount.incrementAndGet()
        AppMetrics.recordRateLimit("anilist", "graphql")
    }

    fun recordHttp5xx() {
        _http5xxCount.incrementAndGet()
        AppMetrics.recordHttp5xx("anilist", "graphql")
    }

    fun recordGraphQLError() = _graphQLErrorCount.incrementAndGet()
    fun recordParseError() = _parseErrorCount.incrementAndGet()

    fun recordRetry() {
        _retryCount.incrementAndGet()
        AppMetrics.recordRetry("anilist", "graphql")
    }

    fun reset() {
        _requestCount.set(0)
        _duplicateInFlightCount.set(0)
        _cacheHitCount.set(0)
        _cacheMissCount.set(0)
        _timeoutCount.set(0)
        _rateLimitCount.set(0)
        _http5xxCount.set(0)
        _graphQLErrorCount.set(0)
        _parseErrorCount.set(0)
        _retryCount.set(0)
        AppMetrics.reset()
    }
}

object AniListClient {
    private val inFlightRequests = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Any?>>()

    @Volatile
    private var testHttpClient: OkHttpClient? = null

    val httpClient: OkHttpClient
        get() = testHttpClient ?: ApiClient.aniListOkHttpClient

    fun setClientForTesting(client: OkHttpClient?) {
        testHttpClient = client
        com.canim.app.data.remote.anilist.AniListApolloClient.setOkHttpClientForTesting(client)
    }

    fun getInFlightCount(): Int = inFlightRequests.size

    @Suppress("UNCHECKED_CAST", "UNREACHABLE_CODE")
    suspend fun <T> deduplicateInFlight(key: String, block: suspend () -> T): T = coroutineScope {
        while (true) {
            val existing = inFlightRequests[key]
            if (existing != null) {
                AniListMetrics.recordDuplicateInFlight()
                try {
                    return@coroutineScope existing.await() as T
                } catch (e: CancellationException) {
                    continue
                }
            }

            val deferred = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                block()
            }
            val prev = inFlightRequests.putIfAbsent(key, deferred)
            if (prev != null) {
                deferred.cancel()
                AniListMetrics.recordDuplicateInFlight()
                try {
                    return@coroutineScope prev.await() as T
                } catch (e: CancellationException) {
                    continue
                }
            }

            try {
                return@coroutineScope deferred.await()
            } finally {
                inFlightRequests.remove(key, deferred)
            }
        }
        error("Unreachable")
    }

    suspend fun pingHealth(): Boolean =
        com.canim.app.data.remote.anilist.AniListApolloClient.pingHealth()

    suspend fun resolveIdMal(malId: Int, type: MediaType): AniListResult<Int> =
        com.canim.app.data.remote.anilist.AniListApolloClient.resolveIdMal(malId, type)

    suspend fun resolveAniListId(aniListId: Int): AniListResult<Int> =
        com.canim.app.data.remote.anilist.AniListApolloClient.resolveAniListId(aniListId)

    suspend fun getMediaBatchByMalIds(malIds: List<Int>, type: MediaType): Map<Int, MediaItem> =
        com.canim.app.data.remote.anilist.AniListApolloClient.getMediaBatchByMalIds(malIds, type)

    /**
     * Search Anime or Manga using AniList GraphQL via Apollo.
     */
    suspend fun searchMedia(
        query: String,
        type: MediaType,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null,
        forceRefresh: Boolean = false
    ): List<MediaItem> =
        com.canim.app.data.remote.anilist.AniListApolloClient.searchMedia(query, type, genres, year, format, forceRefresh)

    /**
     * On-demand Discover fetching by dynamic category & filters using server-side AniList parameters via Apollo.
     */
    suspend fun getDiscoverMedia(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        perPage: Int = 25,
        randomSort: String? = null,
        forceRefresh: Boolean = false,
        mediaType: MediaType? = null
    ): List<MediaItem> =
        com.canim.app.data.remote.anilist.AniListApolloClient.getDiscoverMedia(
            category, filter, page, perPage, randomSort, forceRefresh, mediaType
        )

    /**
     * Fetch extended details (Cast, Staff/Crew, Studio, Duration) on demand via Apollo.
     */
    suspend fun getExtendedDetails(
        aniListId: Int?,
        malId: Int?,
        type: MediaType,
        forceRefresh: Boolean = false
    ): ExtendedMediaDetail? =
        com.canim.app.data.remote.anilist.AniListApolloClient.getExtendedDetails(aniListId, malId, type, forceRefresh)

    suspend fun getCharacterProfile(id: Int, forceRefresh: Boolean = false): CastCrewProfile? =
        com.canim.app.data.remote.anilist.AniListApolloClient.getCharacterProfile(id, forceRefresh)

    suspend fun getStaffProfile(id: Int, forceRefresh: Boolean = false): CastCrewProfile? =
        com.canim.app.data.remote.anilist.AniListApolloClient.getStaffProfile(id, forceRefresh)

    suspend fun getStudioFilmography(
        studioId: Int?,
        search: String? = null,
        page: Int = 1,
        perPage: Int = 24,
        sort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC,
        forceRefresh: Boolean = false,
        isMain: Boolean = true
    ): StudioFilmographyPage? =
        com.canim.app.data.remote.anilist.AniListApolloClient.fetchStudioFilmography(
            studioId, search, page, perPage, sort, forceRefresh, isMain
        )

    /**
     * Searches studios dynamically across AniList's global database via Apollo.
     * Enriches results with curated metadata if available, extracts top anime cover,
     * and saves to persistent cache.
     */
    suspend fun searchStudios(
        query: String,
        page: Int = 1,
        perPage: Int = 20
    ): List<StudioBioInfo> =
        com.canim.app.data.remote.anilist.AniListApolloClient.searchStudios(query, page, perPage)
}
