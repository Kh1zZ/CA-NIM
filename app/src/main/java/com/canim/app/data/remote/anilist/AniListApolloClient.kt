package com.canim.app.data.remote.anilist

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.okHttpClient
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.CastCrewProfile
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.FilmographyItem
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.ApiClient
import com.canim.app.data.remote.AniListClient
import com.canim.app.data.remote.AniListErrorDetail
import com.canim.app.data.remote.AniListMetrics
import com.canim.app.data.remote.AniListResult
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.StudioBioInfo
import com.canim.app.data.model.StudioFilmographySort
import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.remote.anilist.graphql.GetCharacterProfileQuery
import com.canim.app.data.remote.anilist.graphql.GetDiscoverMediaQuery
import com.canim.app.data.remote.anilist.graphql.GetExtendedDetailsByIdQuery
import com.canim.app.data.remote.anilist.graphql.GetExtendedDetailsByMalIdQuery
import com.canim.app.data.remote.anilist.graphql.GetMediaBatchByMalIdsQuery
import com.canim.app.data.remote.anilist.graphql.GetStaffProfileQuery
import com.canim.app.data.remote.anilist.graphql.GetStudioFilmographyQuery
import com.canim.app.data.remote.anilist.graphql.HealthPingQuery
import com.canim.app.data.remote.anilist.graphql.ResolveAniListIdQuery
import com.canim.app.data.remote.anilist.graphql.ResolveMalIdQuery
import com.canim.app.data.remote.anilist.graphql.SearchMediaQuery
import com.canim.app.data.remote.anilist.graphql.SearchStudiosQuery
import com.canim.app.data.remote.anilist.graphql.fragment.ExtendedMediaDetailFields
import com.canim.app.data.remote.anilist.graphql.type.MediaFormat
import com.canim.app.data.remote.anilist.graphql.type.MediaSeason
import com.canim.app.data.remote.anilist.graphql.type.MediaSort
import com.canim.app.data.remote.anilist.graphql.type.MediaStatus
import com.canim.app.data.remote.anilist.graphql.type.MediaType as ApolloMediaType
import java.util.Calendar
import com.canim.app.util.TextSanitizer
import com.canim.app.data.remote.PolicyResult
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.SocketTimeoutException

import com.canim.app.data.metrics.AppMetrics

/**
 * Dedicated ApolloClient for AniList GraphQL operations.
 *
 * Fully reuses CA'NIM's dedicated `ApiClient.aniListOkHttpClient` to preserve:
 * - Tuned timeouts (10s connect, 15s read, 10s write)
 * - Isolated connection pool (5 connections, 5 minutes)
 * - Logging and connection retry configuration
 * - HTTP request cancellation and resource management
 */
object AniListApolloClient {
    private const val ANILIST_GRAPHQL_ENDPOINT = "https://graphql.anilist.co"

    @Volatile
    private var testApolloClient: ApolloClient? = null

    val client: ApolloClient
        get() = testApolloClient ?: defaultClient

    private val defaultClient: ApolloClient by lazy {
        ApolloClient.Builder()
            .serverUrl(ANILIST_GRAPHQL_ENDPOINT)
            .okHttpClient(ApiClient.aniListOkHttpClient)
            .addHttpHeader("User-Agent", "CanimApp/2.1 (Android; GraphQL Engine)")
            .build()
    }

    fun setClientForTesting(apolloClient: ApolloClient?) {
        testApolloClient = apolloClient
        // Reset policy state so 429 cooldowns from previous tests don't bleed through.
        ApiClient.aniListPolicy.resetForTesting()
        ApiClient.malPolicy.resetForTesting()
    }

    fun setOkHttpClientForTesting(okHttpClient: OkHttpClient?) {
        testApolloClient = okHttpClient?.let {
            ApolloClient.Builder()
                .serverUrl(ANILIST_GRAPHQL_ENDPOINT)
                .okHttpClient(it)
                .build()
        }
        // Reset policy state so 429 cooldowns from previous tests don't bleed through.
        ApiClient.aniListPolicy.resetForTesting()
        ApiClient.malPolicy.resetForTesting()
    }

    /**
     * Executes a read-only AniList call under the shared [ApiClient.aniListPolicy]:
     * - Concurrency limiting via Semaphore (max 4 in-flight).
     * - Non-blocking 429 cooldown check: immediately returns [AniListResult.RateLimited] if in cooldown.
     * - Arms cooldown on 429 response.
     * - Retries transient failures (Timeout, HTTP 5xx) up to 2 times with exponential backoff + jitter.
     * - Mutations are never retried (retryable = false).
     * - Error metric counters are incremented only on final failure to reflect true operation status.
     * - Preserves coroutine cancellation.
     */
    private suspend fun <T> withAniListPolicy(
        retryable: Boolean = true,
        operationName: String = "graphql",
        block: suspend (isFinalAttempt: Boolean) -> AniListResult<T>
    ): AniListResult<T> {
        val policy = ApiClient.aniListPolicy
        val onCooldown: () -> AniListResult<T> = {
            AniListMetrics.recordRateLimit()
            AppMetrics.recordRateLimit("anilist", operationName)
            AniListResult.RateLimited(policy.remainingCooldownMs() / 1000L)
        }

        if (policy.remainingCooldownMs() > 0L) return onCooldown()

        val startNs = System.nanoTime()
        var attempt = 0
        try {
            while (true) {
                if (policy.remainingCooldownMs() > 0L) return onCooldown()

                // Smooth burst traffic through adaptive rate limiter
                policy.limiter.acquire()
                if (policy.remainingCooldownMs() > 0L) return onCooldown()

                val isFinalAttempt = !retryable || attempt >= 2
                val result = try {
                    policy.semaphore.withPermit {
                        if (policy.remainingCooldownMs() > 0L) {
                            return@withPermit null
                        }
                        block(isFinalAttempt)
                    } ?: return onCooldown()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    ApolloErrorMapper.toAniListResult(e, recordMetrics = isFinalAttempt)
                }

                when {
                    result is AniListResult.RateLimited -> {
                        val retryMs = (result.retryAfterSeconds ?: 0L) * 1000L
                        policy.armCooldown(retryMs)
                        AppMetrics.recordRateLimit("anilist", operationName)
                        return result
                    }
                    result is AniListResult.Success -> {
                        policy.onSuccess()
                        return result
                    }
                    result is AniListResult.NotFound -> {
                        policy.onSuccess()
                        return result
                    }
                    (result is AniListResult.Timeout || (result is AniListResult.HttpError && result.code in 500..599))
                        && retryable && attempt < 2 -> {
                        attempt++
                        AniListMetrics.recordRetry()
                        AppMetrics.recordRetry("anilist", operationName)
                        ApiClient.aniListLimiter.tryEmitRetrying()
                        // Permit has been released! Delay does not starve other concurrent requests.
                        if (policy.baseBackoffMs > 0L) {
                            val backoff = minOf(policy.baseBackoffMs * (1L shl (attempt - 1)), policy.maxBackoffMs)
                            val jitter = if (policy.jitterMs > 0L) kotlin.random.Random.nextLong(-policy.jitterMs, policy.jitterMs + 1) else 0L
                            val delayMs = maxOf(0L, backoff + jitter)
                            if (delayMs > 0L) kotlinx.coroutines.delay(delayMs)
                        }
                    }
                    else -> return result
                }
            }
        } finally {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000L
            AppMetrics.recordLatency("anilist", operationName, durationMs)
        }
    }

    /**
     * Executes the Apollo HealthPing operation and maps the response into
     * CA'NIM's application-level `AniListResult` semantics.
     * Health check does not auto-retry internally to avoid duplicating retry/delay in SystemRepository.
     */
    suspend fun executeHealthPing(): AniListResult<Boolean> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        withAniListPolicy(retryable = false) { isFinalAttempt ->
            ApolloErrorMapper.safeApolloCall(recordMetrics = isFinalAttempt) {
                val response = client.query(HealthPingQuery()).execute()
                response.exception?.let { throw it }
                if (response.hasErrors()) {
                    val errorResult = ApolloErrorMapper.handleGraphQLErrors(response.errors)
                    if (errorResult != null) return@safeApolloCall errorResult
                }
                val media = response.data?.Media ?: return@safeApolloCall AniListResult.NotFound
                AniListResult.Success(media.id == 1)
            }
        }
    }

    /**
     * Fast health check returning true if AniList responds successfully with Media id 1.
     * Preserves cancellation and maps failures/outages to false without throwing.
     */
    suspend fun pingHealth(): Boolean = when (val res = executeHealthPing()) {
        is AniListResult.Success -> res.data
        // Normal rate control / throttling handled internally; server is alive and reachable
        is AniListResult.RateLimited -> true
        else -> false
    }

    /**
     * Executes the Apollo ResolveMalId query.
     *
     * CRITICAL INVARIANT: media.id is the verified AniList ID returned from AniList.
     * media.idMal is the MyAnimeList ID. A MAL ID is NEVER copied into an AniList ID field.
     */
    suspend fun executeResolveMalId(malId: Int, type: MediaType): AniListResult<Int> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        withAniListPolicy { isFinalAttempt ->
            ApolloErrorMapper.safeApolloCall(recordMetrics = isFinalAttempt) {
                val apolloType = if (type == MediaType.ANIME) {
                    ApolloMediaType.ANIME
                } else {
                    ApolloMediaType.MANGA
                }
                val response = client.query(
                    ResolveMalIdQuery(
                        idMal = Optional.present(malId),
                        type = Optional.present(apolloType)
                    )
                ).execute()
                response.exception?.let { throw it }

                val media = response.data?.Media
                if (media != null) {
                    return@safeApolloCall AniListResult.Success(media.id)
                }

                if (response.hasErrors()) {
                    val errorResult = ApolloErrorMapper.handleGraphQLErrors(response.errors)
                    if (errorResult != null) return@safeApolloCall errorResult
                }

                AniListResult.NotFound
            }
        }
    }

    /**
     * Resolves a MAL ID to AniList ID with cache hit/miss tracking and in-flight deduplication.
     */
    suspend fun resolveIdMal(malId: Int, type: MediaType): AniListResult<Int> = withContext(Dispatchers.IO) {
        val cached = CacheManager.getAniListIdForMalId(malId, type)
        if (cached != null) {
            AniListMetrics.recordCacheHit()
            return@withContext AniListResult.Success(cached)
        }
        val negKey = "resolve_mal_${malId}_${type.name}"
        if (CacheManager.isNegativeCached(negKey)) {
            AniListMetrics.recordCacheHit()
            return@withContext AniListResult.NotFound
        }
        AniListMetrics.recordCacheMiss()

        AniListClient.deduplicateInFlight(negKey) {
            val result = executeResolveMalId(malId, type)
            if (result is AniListResult.Success) {
                CacheManager.putIdMapping(malId = malId, aniListId = result.data, type = type)
            } else if (result is AniListResult.NotFound) {
                CacheManager.putNegativeCache(negKey)
            }
            result
        }
    }

    /**
     * Executes the Apollo ResolveAniListId query.
     */
    suspend fun executeResolveAniListId(aniListId: Int): AniListResult<Int> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        withAniListPolicy { isFinalAttempt ->
            ApolloErrorMapper.safeApolloCall(recordMetrics = isFinalAttempt) {
                val response = client.query(
                    ResolveAniListIdQuery(
                        id = Optional.present(aniListId)
                    )
                ).execute()
                response.exception?.let { throw it }

                val media = response.data?.Media
                if (media?.idMal != null) {
                    return@safeApolloCall AniListResult.Success(media.idMal)
                }

                if (response.hasErrors()) {
                    val errorResult = ApolloErrorMapper.handleGraphQLErrors(response.errors)
                    if (errorResult != null) return@safeApolloCall errorResult
                }

                AniListResult.NotFound
            }
        }
    }

    /**
     * Resolves an AniList ID to MAL ID with cache hit/miss tracking and in-flight deduplication.
     */
    suspend fun resolveAniListId(aniListId: Int): AniListResult<Int> = withContext(Dispatchers.IO) {
        val cached = CacheManager.getMalIdForAniListId(aniListId)
        if (cached != null) {
            AniListMetrics.recordCacheHit()
            return@withContext AniListResult.Success(cached)
        }
        val negKey = "resolve_ani_${aniListId}"
        if (CacheManager.isNegativeCached(negKey)) {
            AniListMetrics.recordCacheHit()
            return@withContext AniListResult.NotFound
        }
        AniListMetrics.recordCacheMiss()

        AniListClient.deduplicateInFlight(negKey) {
            val result = executeResolveAniListId(aniListId)
            if (result is AniListResult.Success) {
                CacheManager.putIdMapping(malId = result.data, aniListId = aniListId)
            } else if (result is AniListResult.NotFound) {
                CacheManager.putNegativeCache(negKey)
            }
            result
        }
    }

    /**
     * Executes the Apollo SearchMedia query and maps the response into
     * CA'NIM's application-level `AniListResult<List<MediaItem>>`.
     */
    suspend fun executeSearchMedia(
        query: String,
        type: MediaType,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null,
        page: Int = 1,
        perPage: Int = 30
    ): AniListResult<List<MediaItem>> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        withAniListPolicy { isFinalAttempt ->
            ApolloErrorMapper.safeApolloCall(recordMetrics = isFinalAttempt) {
                val apolloType = if (type == MediaType.ANIME) ApolloMediaType.ANIME else ApolloMediaType.MANGA

                val (validGenres, validTags) = if (!genres.isNullOrEmpty()) {
                    val officialGenres = setOf(
                        "Action", "Adventure", "Comedy", "Drama", "Ecchi",
                        "Fantasy", "Hentai", "Horror", "Mahou Shoujo", "Mecha",
                        "Music", "Mystery", "Psychological", "Romance", "Sci-Fi",
                        "Slice of Life", "Sports", "Supernatural", "Thriller"
                    )
                    val vg = genres.filter { it in officialGenres }
                    val vt = genres.filter { it !in officialGenres && !it.equals("Award Winning", ignoreCase = true) }
                    Pair(
                        if (vg.isNotEmpty()) vg else null,
                        if (vt.isNotEmpty()) vt else null
                    )
                } else {
                    Pair(null, null)
                }

                val (seasonYear, startDateGreater, startDateLesser) = if (year != null) {
                    if (type == MediaType.ANIME && year >= 1917) {
                        Triple(year, null, null)
                    } else if (type == MediaType.MANGA && year >= 1874) {
                        Triple(null, year * 10000, (year + 1) * 10000)
                    } else {
                        Triple(null, null, null)
                    }
                } else {
                    Triple(null, null, null)
                }

                val apolloFormat = if (!format.isNullOrBlank()) {
                    MediaFormat.knownEntries.find { it.rawValue.equals(format.trim(), ignoreCase = true) }
                } else {
                    null
                }

                val searchOpt = if (query.isNotBlank()) Optional.present(query.trim()) else Optional.Absent
                val genresOpt = if (validGenres != null) Optional.present(validGenres) else Optional.Absent
                val tagsOpt = if (validTags != null) Optional.present(validTags) else Optional.Absent
                val seasonYearOpt = if (seasonYear != null) Optional.present(seasonYear) else Optional.Absent
                val startDateGreaterOpt = if (startDateGreater != null) Optional.present(startDateGreater) else Optional.Absent
                val startDateLesserOpt = if (startDateLesser != null) Optional.present(startDateLesser) else Optional.Absent
                val formatOpt = if (apolloFormat != null) Optional.present(apolloFormat) else Optional.Absent

                val response = client.query(
                    SearchMediaQuery(
                        page = Optional.present(page),
                        perPage = Optional.present(perPage),
                        type = Optional.present(apolloType),
                        search = searchOpt,
                        genres = genresOpt,
                        tags = tagsOpt,
                        seasonYear = seasonYearOpt,
                        startDateGreater = startDateGreaterOpt,
                        startDateLesser = startDateLesserOpt,
                        format = formatOpt
                    )
                ).execute()

                response.exception?.let { throw it }

                if (response.hasErrors()) {
                    val errorResult = ApolloErrorMapper.handleGraphQLErrors(response.errors)
                    if (errorResult != null) return@safeApolloCall errorResult
                }

                val mediaList = response.data?.Page?.media
                if (mediaList == null) {
                    return@safeApolloCall AniListResult.Success(emptyList())
                }

                val items = mediaList.filterNotNull().map { m ->
                    if (m.idMal != null) {
                        CacheManager.putIdMapping(malId = m.idMal, aniListId = m.id, type = type)
                    }
                    val item = AniListApolloMapper.toMediaItem(m, type)
                    if (m.idMal != null) {
                        CacheManager.putMetadata(m.idMal, type, item)
                    }
                    item
                }
                AniListResult.Success(items)
            }
        }
    }

    /**
     * Searches Media (Anime/Manga) with caching and in-flight deduplication.
     */
    suspend fun searchMedia(
        query: String,
        type: MediaType,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null,
        page: Int = 1,
        perPage: Int = 30,
        forceRefresh: Boolean = false
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val cacheKey = "${query}_${type.name}_${genres?.joinToString(",")}_${year}_${format}_p${page}"
        if (!forceRefresh) {
            val cached = CacheManager.getSearch(cacheKey, type.name)
            if (cached != null) {
                AniListMetrics.recordCacheHit()
                return@withContext cached
            }
        }
        AniListMetrics.recordCacheMiss()

        AniListClient.deduplicateInFlight("search_${cacheKey}") {
            val res = executeSearchMedia(query, type, genres, year, format, page, perPage)
            if (res is AniListResult.Success) {
                val items = res.data
                if (items.isNotEmpty()) {
                    CacheManager.putSearch(cacheKey, type.name, items)
                }
                items
            } else {
                emptyList()
            }
        }
    }

    /**
     * Executes GetExtendedDetailsByIdQuery and returns AniListResult<ExtendedMediaDetail>.
     */
    suspend fun executeGetExtendedDetailsById(aniListId: Int): AniListResult<ExtendedMediaDetail> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        withAniListPolicy { isFinalAttempt ->
            ApolloErrorMapper.safeApolloCall(recordMetrics = isFinalAttempt) {
                val response = client.query(GetExtendedDetailsByIdQuery(Optional.present(aniListId))).execute()
                response.exception?.let { throw it }
                if (response.hasErrors()) {
                    val errorResult = ApolloErrorMapper.handleGraphQLErrors(response.errors)
                    if (errorResult != null) return@safeApolloCall errorResult
                }
                val media = response.data?.Media ?: return@safeApolloCall AniListResult.NotFound
                val fields = media.extendedMediaDetailFields
                if (fields.idMal != null) {
                    CacheManager.putIdMapping(malId = fields.idMal, aniListId = fields.id)
                }
                val detail = AniListApolloMapper.toExtendedMediaDetail(fields, null)
                AniListResult.Success(detail)
            }
        }
    }

    /**
     * Executes GetExtendedDetailsByMalIdQuery and returns AniListResult<ExtendedMediaDetail>.
     */
    suspend fun executeGetExtendedDetailsByMalId(malId: Int, type: MediaType): AniListResult<ExtendedMediaDetail> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        withAniListPolicy { isFinalAttempt ->
            ApolloErrorMapper.safeApolloCall(recordMetrics = isFinalAttempt) {
                val apolloType = if (type == MediaType.ANIME) ApolloMediaType.ANIME else ApolloMediaType.MANGA
                val response = client.query(
                    GetExtendedDetailsByMalIdQuery(
                        idMal = Optional.present(malId),
                        type = Optional.present(apolloType)
                    )
                ).execute()
                response.exception?.let { throw it }
                if (response.hasErrors()) {
                    val errorResult = ApolloErrorMapper.handleGraphQLErrors(response.errors)
                    if (errorResult != null) return@safeApolloCall errorResult
                }
                val media = response.data?.Media ?: return@safeApolloCall AniListResult.NotFound
                val fields = media.extendedMediaDetailFields
                if (fields.idMal != null) {
                    CacheManager.putIdMapping(malId = fields.idMal, aniListId = fields.id, type = type)
                }
                val detail = AniListApolloMapper.toExtendedMediaDetail(fields, malId)
                AniListResult.Success(detail)
            }
        }
    }

    /**
     * Fetch extended details (Cast, Staff/Crew, Studio, Duration) with caching and deduplication.
     */
    suspend fun getExtendedDetails(
        aniListId: Int?,
        malId: Int?,
        type: MediaType,
        forceRefresh: Boolean = false
    ): ExtendedMediaDetail? = withContext(Dispatchers.IO) {
        val resolvedId = aniListId ?: (malId?.let { CacheManager.getAniListIdForMalId(it, type) })
        if (resolvedId == null && malId == null) return@withContext null
        val cacheKey = CacheManager.detailKey(resolvedId, malId)

        if (!forceRefresh) {
            val cached = CacheManager.getDetail(cacheKey)
            if (cached != null) {
                AniListMetrics.recordCacheHit()
                return@withContext cached
            }
            if (CacheManager.isNegativeCached(cacheKey)) {
                AniListMetrics.recordCacheHit()
                return@withContext null
            }
        }
        AniListMetrics.recordCacheMiss()

        val dedupeKey = "detail_${resolvedId}_${malId}_${type.name}"
        AniListClient.deduplicateInFlight(dedupeKey) {
            val res = if (resolvedId != null) {
                executeGetExtendedDetailsById(resolvedId)
            } else if (malId != null) {
                executeGetExtendedDetailsByMalId(malId, type)
            } else {
                return@deduplicateInFlight null
            }

            when (res) {
                is AniListResult.Success -> {
                    val detail = res.data
                    CacheManager.putDetail(cacheKey, detail)
                    detail
                }
                is AniListResult.NotFound -> {
                    CacheManager.putNegativeCache(cacheKey)
                    null
                }
                else -> {
                    null
                }
            }
        }
    }

    /**
     * Executes GetCharacterProfileQuery and returns AniListResult<CastCrewProfile>.
     */
    suspend fun executeGetCharacterProfile(id: Int): AniListResult<CastCrewProfile> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        withAniListPolicy { isFinalAttempt ->
            ApolloErrorMapper.safeApolloCall(recordMetrics = isFinalAttempt) {
                val response = client.query(GetCharacterProfileQuery(Optional.present(id))).execute()
                response.exception?.let { throw it }
                if (response.hasErrors()) {
                    val errorResult = ApolloErrorMapper.handleGraphQLErrors(response.errors)
                    if (errorResult != null) return@safeApolloCall errorResult
                }
                val char = response.data?.Character ?: return@safeApolloCall AniListResult.NotFound
                val profile = AniListApolloMapper.toCharacterProfile(id, char)
                AniListResult.Success(profile)
            }
        }
    }

    /**
     * Fetch character profile with caching and in-flight deduplication.
     */
    suspend fun getCharacterProfile(id: Int, forceRefresh: Boolean = false): CastCrewProfile? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = CacheManager.getCastCrewProfile(id, isStaff = false)
            if (cached != null) {
                AniListMetrics.recordCacheHit()
                return@withContext cached
            }
        }
        AniListMetrics.recordCacheMiss()

        AniListClient.deduplicateInFlight("char_$id") {
            val res = executeGetCharacterProfile(id)
            if (res is AniListResult.Success) {
                val profile = res.data
                CacheManager.putCastCrewProfile(id, isStaff = false, profile)
                profile
            } else {
                null
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Step 5.3 — GetStaffProfile
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun executeGetStaffProfile(id: Int): AniListResult<CastCrewProfile> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        withAniListPolicy { isFinalAttempt ->
            ApolloErrorMapper.safeApolloCall(recordMetrics = isFinalAttempt) {
                val query = GetStaffProfileQuery(id = Optional.present(id))
                val response = client.query(query).execute()
                response.exception?.let { throw it }
                if (response.hasErrors()) {
                    val errorResult = ApolloErrorMapper.handleGraphQLErrors(response.errors)
                    if (errorResult != null) return@safeApolloCall errorResult
                }
                val staff = response.data?.Staff ?: return@safeApolloCall AniListResult.NotFound
                val profile = AniListApolloMapper.toStaffProfile(id, staff)
                AniListResult.Success(profile)
            }
        }
    }

    /**
     * Fetch staff/VA profile with caching and in-flight deduplication.
     */
    suspend fun getStaffProfile(id: Int, forceRefresh: Boolean = false): CastCrewProfile? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = CacheManager.getCastCrewProfile(id, isStaff = true)
            if (cached != null) {
                AniListMetrics.recordCacheHit()
                return@withContext cached
            }
        }
        AniListMetrics.recordCacheMiss()

        AniListClient.deduplicateInFlight("staff_$id") {
            val res = executeGetStaffProfile(id)
            if (res is AniListResult.Success) {
                val profile = res.data
                CacheManager.putCastCrewProfile(id, isStaff = true, profile)
                profile
            } else {
                null
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Step 5.4 â€” GetStudioFilmography
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun fetchStudioFilmography(
        studioId: Int?,
        search: String? = null,
        page: Int = 1,
        perPage: Int = 24,
        sort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC,
        forceRefresh: Boolean = false,
        isMain: Boolean = true
    ): StudioFilmographyPage? = withContext(Dispatchers.IO) {
        if (studioId == null && search.isNullOrBlank()) return@withContext null
        if (!forceRefresh && studioId != null) {
            val cached = CacheManager.getStudioFilmography(studioId, page)
            if (cached != null) {
                AniListMetrics.recordCacheHit()
                return@withContext cached
            }
        }
        AniListMetrics.recordCacheMiss()

        val apolloSort: List<MediaSort> = when (sort) {
            StudioFilmographySort.YEAR_DESC -> listOf(MediaSort.START_DATE_DESC, MediaSort.POPULARITY_DESC)
            StudioFilmographySort.YEAR_ASC  -> listOf(MediaSort.START_DATE, MediaSort.POPULARITY_DESC)
            StudioFilmographySort.SCORE_DESC -> listOf(MediaSort.SCORE_DESC, MediaSort.POPULARITY_DESC)
            StudioFilmographySort.POPULARITY_DESC -> listOf(MediaSort.POPULARITY_DESC)
        }

        val dedupeKey = "studio_${studioId}_${search}_${page}_${sort.name}_main_$isMain"
        AniListClient.deduplicateInFlight(dedupeKey) {
            AniListMetrics.recordRequest()
            try {
                val query = GetStudioFilmographyQuery(
                    id = if (studioId != null) Optional.present(studioId) else Optional.absent(),
                    search = if (!search.isNullOrBlank()) Optional.present(search) else Optional.absent(),
                    page = Optional.present(page),
                    perPage = Optional.present(perPage),
                    sort = Optional.present(apolloSort),
                    isMain = Optional.present(isMain)
                )
                val response = client.query(query).execute()
                val studioObj = response.data?.Studio ?: return@deduplicateInFlight null

                val resolvedId = studioObj.id
                val resolvedName = studioObj.name
                val isAnimationStudio = studioObj.isAnimationStudio
                val siteUrl = studioObj.siteUrl?.takeIf { it.isNotBlank() }
                val favourites = studioObj.favourites?.takeIf { it > 0 }

                val pageInfo = studioObj.media?.pageInfo
                val hasNextPage = pageInfo?.hasNextPage ?: false
                val currentPage = pageInfo?.currentPage ?: page
                val total = pageInfo?.total ?: 0

                val seenIds = mutableSetOf<Int>()
                val items = (studioObj.media?.nodes ?: emptyList())
                    .filterNotNull()
                    .mapNotNull { node ->
                        if (!seenIds.add(node.id)) null
                        else {
                            if (node.idMal != null && node.idMal > 0) {
                                val mType = if (node.type?.rawValue == "MANGA") MediaType.MANGA else MediaType.ANIME
                                CacheManager.putIdMapping(malId = node.idMal, aniListId = node.id, type = mType)
                            }
                            AniListApolloMapper.toStudioFilmographyItem(node, resolvedName)
                        }
                    }

                val result = StudioFilmographyPage(
                    studioId = resolvedId,
                    studioName = resolvedName,
                    items = items,
                    hasNextPage = hasNextPage,
                    currentPage = currentPage,
                    total = total,
                    siteUrl = siteUrl,
                    favourites = favourites,
                    isAnimationStudio = isAnimationStudio
                )
                CacheManager.putStudioFilmography(resolvedId, page, result)
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ApolloErrorMapper.toAniListResult(e)
                null
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Step 5.5 — SearchStudios
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun searchStudios(
        query: String,
        page: Int = 1,
        perPage: Int = 20
    ): List<StudioBioInfo> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val dedupeKey = "search_studio_${query.trim().lowercase()}_${page}"
        AniListClient.deduplicateInFlight(dedupeKey) {
            AniListMetrics.recordRequest()
            try {
                val q = SearchStudiosQuery(
                    search = Optional.present(query.trim()),
                    page = Optional.present(page),
                    perPage = Optional.present(perPage)
                )
                val response = client.query(q).execute()
                val studios = response.data?.Page?.studios ?: return@deduplicateInFlight emptyList()

                val results = mutableListOf<StudioBioInfo>()
                for (s in studios) {
                    if (s == null) continue
                    val sId = s.id.takeIf { it > 0 } ?: continue
                    val sName = s.name.takeIf { it.isNotBlank() } ?: continue
                    val favs = s.favourites?.takeIf { it > 0 }
                    val siteUrl = s.siteUrl?.takeIf { it.isNotBlank() }

                    // Top popular anime cover image
                    val topCover = s.media?.nodes
                        ?.firstOrNull()
                        ?.coverImage
                        ?.let { img ->
                            img.large?.takeIf { it.isNotBlank() }
                                ?: img.extraLarge?.takeIf { it.isNotBlank() }
                        }

                    val info = StudioBioInfo(
                        studioId = sId,
                        name = sName,
                        coverUrl = topCover,
                        favourites = favs,
                        officialSite = siteUrl
                    )
                    results.add(info)
                }
                results
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ApolloErrorMapper.toAniListResult(e)
                emptyList()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Step 5.6 — GetDiscoverMedia
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun getDiscoverMedia(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        perPage: Int = 25,
        randomSort: String? = null,
        forceRefresh: Boolean = false,
        mediaType: MediaType? = null
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) // 0-11

        val (currentSeason, nextSeason, nextSeasonYear) = when (currentMonth) {
            in 0..2  -> Triple("WINTER", "SPRING", currentYear)
            in 3..5  -> Triple("SPRING", "SUMMER", currentYear)
            in 6..8  -> Triple("SUMMER", "FALL", currentYear)
            else     -> Triple("FALL", "WINTER", currentYear + 1)
        }

        val isManga = mediaType == MediaType.MANGA ||
            filter.format == "MANGA" ||
            category == DiscoverCategory.TOP_MANGA ||
            category == DiscoverCategory.RECENTLY_DONE_MANGA ||
            category == DiscoverCategory.NEWLY_ADDED_MANGA
        val typePrefix = if (isManga) "manga" else "anime"
        val cacheKey = "${typePrefix}_${category.key}_${filter.genre}_${filter.format}_${filter.year}_${filter.season}_${filter.minScore}_${randomSort}_p$page"
        if (!forceRefresh) {
            val cached = CacheManager.getDiscover(cacheKey)
            if (cached != null) {
                AniListMetrics.recordCacheHit()
                return@withContext cached
            }
        }
        AniListMetrics.recordCacheMiss()
        val apolloType: ApolloMediaType = if (isManga) ApolloMediaType.MANGA else ApolloMediaType.ANIME
        val fallbackType = if (isManga) MediaType.MANGA else MediaType.ANIME

        // Build optional variables based on category
        var apolloStatus: Optional<MediaStatus> = Optional.absent()
        var apolloSeasonYear: Optional<Int> = Optional.absent()
        var apolloSeason: Optional<MediaSeason> = Optional.absent()
        var apolloSort: Optional<List<MediaSort>> = Optional.absent()
        var apolloGenre: Optional<String> = Optional.absent()
        var apolloFormat: Optional<MediaFormat> = Optional.absent()
        var apolloMinScore: Optional<Int> = Optional.absent()

        when (category) {
            DiscoverCategory.CURRENT_SEASON -> {
                apolloSeason = Optional.present(MediaSeason.safeValueOf(currentSeason))
                apolloSeasonYear = Optional.present(currentYear)
                apolloSort = Optional.present(listOf(MediaSort.POPULARITY_DESC))
            }
            DiscoverCategory.NEXT_SEASON -> {
                apolloSeason = Optional.present(MediaSeason.safeValueOf(nextSeason))
                apolloSeasonYear = Optional.present(nextSeasonYear)
                apolloSort = Optional.present(listOf(MediaSort.POPULARITY_DESC))
            }
            DiscoverCategory.UPCOMING -> {
                apolloStatus = Optional.present(MediaStatus.NOT_YET_RELEASED)
                apolloSort = Optional.present(listOf(MediaSort.POPULARITY_DESC))
            }
            DiscoverCategory.TBA -> {
                apolloStatus = Optional.present(MediaStatus.NOT_YET_RELEASED)
                apolloSort = Optional.present(listOf(MediaSort.ID_DESC))
            }
            DiscoverCategory.STUDIO -> {
                apolloSort = Optional.present(listOf(MediaSort.POPULARITY_DESC))
            }
            DiscoverCategory.TOP_ANIME -> {
                apolloSort = Optional.present(listOf(MediaSort.SCORE_DESC))
            }
            DiscoverCategory.TRENDING_NOW -> {
                apolloSort = Optional.present(listOf(MediaSort.TRENDING_DESC))
            }
            DiscoverCategory.TOP_MANGA -> {
                apolloSort = Optional.present(listOf(MediaSort.SCORE_DESC))
            }
            DiscoverCategory.RECENTLY_DONE_MANGA -> {
                apolloStatus = Optional.present(MediaStatus.FINISHED)
                apolloSort = Optional.present(listOf(MediaSort.END_DATE_DESC, MediaSort.POPULARITY_DESC))
            }
            DiscoverCategory.NEWLY_ADDED_MANGA -> {
                apolloSort = Optional.present(listOf(MediaSort.START_DATE_DESC, MediaSort.POPULARITY_DESC))
            }
        }

        if (randomSort != null && category != DiscoverCategory.TOP_ANIME && category != DiscoverCategory.TOP_MANGA) {
            when (randomSort) {
                "POPULARITY_DESC" -> apolloSort = Optional.present(listOf(MediaSort.POPULARITY_DESC))
                "SCORE_DESC" -> apolloSort = Optional.present(listOf(MediaSort.SCORE_DESC))
                "FAVOURITES_DESC" -> apolloSort = Optional.present(listOf(MediaSort.FAVOURITES_DESC))
                "TRENDING_DESC" -> apolloSort = Optional.present(listOf(MediaSort.TRENDING_DESC))
                "START_DATE_DESC" -> apolloSort = Optional.present(listOf(MediaSort.START_DATE_DESC))
                "TITLE_ROMAJI" -> apolloSort = Optional.present(listOf(MediaSort.TITLE_ROMAJI))
            }
        }

        filter.genre?.let {
            apolloGenre = Optional.present(it)
        }
        filter.minScore?.let {
            apolloMinScore = Optional.present(it * 10)
        }
        filter.season?.let {
            apolloSeason = Optional.present(MediaSeason.safeValueOf(it.uppercase()))
        }
        filter.year?.let {
            apolloSeasonYear = Optional.present(it)
        }
        filter.format?.let {
            apolloFormat = Optional.present(MediaFormat.safeValueOf(it))
        }

        val dedupeKey = "discover_${cacheKey}"
        AniListClient.deduplicateInFlight(dedupeKey) {
            AniListMetrics.recordRequest()
            try {
                val q = GetDiscoverMediaQuery(
                    page = Optional.present(page),
                    perPage = Optional.present(perPage),
                    type = Optional.present(apolloType),
                    status = apolloStatus,
                    seasonYear = apolloSeasonYear,
                    season = apolloSeason,
                    genre = apolloGenre,
                    format = apolloFormat,
                    averageScore_greater = apolloMinScore,
                    sort = apolloSort
                )
                val response = client.query(q).execute()
                val mediaList = response.data?.Page?.media ?: emptyList()
                val items = mediaList.filterNotNull().map { m ->
                    if (m.idMal != null) {
                        CacheManager.putIdMapping(malId = m.idMal, aniListId = m.id, type = fallbackType)
                    }
                    val item = AniListApolloMapper.toDiscoverMediaItem(m, fallbackType)
                    if (m.idMal != null) {
                        CacheManager.putMetadata(m.idMal, fallbackType, item)
                    }
                    item
                }
                if (items.isNotEmpty()) CacheManager.putDiscover(cacheKey, items)
                items
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ApolloErrorMapper.toAniListResult(e)
                emptyList()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Step 5.7 — GetMediaBatchByMalIds
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun getMediaBatchByMalIds(
        malIds: List<Int>,
        type: MediaType
    ): Map<Int, MediaItem> = withContext(Dispatchers.IO) {
        if (malIds.isEmpty()) return@withContext emptyMap()
        val distinctIds = malIds.distinct().filter { it > 0 }
        if (distinctIds.isEmpty()) return@withContext emptyMap()

        val resultMap = mutableMapOf<Int, MediaItem>()
        val missingIds = mutableListOf<Int>()

        for (id in distinctIds) {
            val cached = CacheManager.getMetadata(id, type)
            if (cached != null) {
                AniListMetrics.recordCacheHit()
                resultMap[id] = cached
            } else if (CacheManager.isNegativeCached("resolve_mal_${id}_${type.name}")) {
                AniListMetrics.recordCacheHit()
            } else {
                AniListMetrics.recordCacheMiss()
                missingIds.add(id)
            }
        }

        if (missingIds.isEmpty()) {
            return@withContext resultMap
        }

        val apolloType: ApolloMediaType = if (type == MediaType.ANIME) ApolloMediaType.ANIME else ApolloMediaType.MANGA

        for (chunk in missingIds.chunked(50)) {
            // Apply rate limiter backpressure so multi-chunk batch queries don't burst AniList
            ApiClient.aniListLimiter.acquire()

            val dedupeKey = "batch_${chunk.sorted().hashCode()}_${type.name}"
            val chunkMap = AniListClient.deduplicateInFlight(dedupeKey) {
                AniListMetrics.recordRequest()
                try {
                    val q = GetMediaBatchByMalIdsQuery(
                        idMal_in = Optional.present(chunk),
                        type = Optional.present(apolloType)
                    )
                    val response = client.query(q).execute()
                    val mediaList = response.data?.Page?.media ?: emptyList()
                    val chunkResult = mutableMapOf<Int, MediaItem>()
                    val foundMalIds = mutableSetOf<Int>()
                    for (m in mediaList.filterNotNull()) {
                        if (m.idMal != null) {
                            foundMalIds.add(m.idMal)
                            CacheManager.putIdMapping(malId = m.idMal, aniListId = m.id, type = type)
                        }
                        val item = AniListApolloMapper.toBatchMediaItem(m, type)
                        m.idMal?.let { malId ->
                            chunkResult[malId] = item
                            CacheManager.putMetadata(malId, type, item)
                        }
                    }
                    val missingInBatch = chunk.toSet() - foundMalIds
                    for (missingId in missingInBatch) {
                        CacheManager.putNegativeCache("resolve_mal_${missingId}_${type.name}")
                    }
                    chunkResult
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    ApolloErrorMapper.toAniListResult(e)
                    mutableMapOf()
                }
            }
            resultMap.putAll(chunkMap)
        }
        resultMap
    }
}




