package com.canim.app.data.remote.anilist

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.okHttpClient
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.CastCrewProfile
import com.canim.app.data.model.CharacterCastItem
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.FilmographyItem
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaRelationItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.StaffMemberItem
import com.canim.app.data.remote.ApiClient
import com.canim.app.data.remote.AniListClient
import com.canim.app.data.remote.AniListErrorDetail
import com.canim.app.data.remote.AniListMetrics
import com.canim.app.data.remote.AniListResult
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.StudioBioInfo
import com.canim.app.data.model.StudioFilmographySort
import com.canim.app.data.repository.StudioBioRegistry
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.SocketTimeoutException

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
    }

    fun setOkHttpClientForTesting(okHttpClient: OkHttpClient?) {
        testApolloClient = okHttpClient?.let {
            ApolloClient.Builder()
                .serverUrl(ANILIST_GRAPHQL_ENDPOINT)
                .okHttpClient(it)
                .build()
        }
    }

    /**
     * Executes the Apollo HealthPing operation and maps the response into
     * CA'NIM's application-level `AniListResult` semantics.
     */
    suspend fun executeHealthPing(): AniListResult<Boolean> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        try {
            val response = client.query(HealthPingQuery()).execute()
            response.exception?.let { throw it }
            if (response.hasErrors()) {
                AniListMetrics.recordGraphQLError()
                val errorDetails = response.errors?.map { AniListErrorDetail(it.message, null) } ?: emptyList()
                return@withContext AniListResult.GraphQLError(errorDetails)
            }
            val media = response.data?.Media
            if (media == null) {
                return@withContext AniListResult.NotFound
            }
            AniListResult.Success(media.id == 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApolloHttpException) {
            when (e.statusCode) {
                429 -> {
                    AniListMetrics.recordRateLimit()
                    AniListResult.RateLimited(60)
                }
                404 -> AniListResult.NotFound
                in 500..599 -> {
                    AniListMetrics.recordHttp5xx()
                    AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
                }
                else -> AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
            }
        } catch (e: ApolloNetworkException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: ApolloException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: Exception) {
            AniListResult.NetworkError(e)
        }
    }

    /**
     * Fast health check returning true if AniList responds successfully with Media id 1.
     * Preserves cancellation and maps failures/outages to false without throwing.
     */
    suspend fun pingHealth(): Boolean = when (val res = executeHealthPing()) {
        is AniListResult.Success -> res.data
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
        try {
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
                return@withContext AniListResult.Success(media.id)
            }

            if (response.hasErrors()) {
                val errors = response.errors ?: emptyList()
                if (errors.any { it.message.contains("Not Found", ignoreCase = true) }) {
                    return@withContext AniListResult.NotFound
                }
                AniListMetrics.recordGraphQLError()
                val errorDetails = errors.map { AniListErrorDetail(it.message, null) }
                return@withContext AniListResult.GraphQLError(errorDetails)
            }

            AniListResult.NotFound
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApolloHttpException) {
            when (e.statusCode) {
                429 -> {
                    AniListMetrics.recordRateLimit()
                    AniListResult.RateLimited(60)
                }
                404 -> AniListResult.NotFound
                in 500..599 -> {
                    AniListMetrics.recordHttp5xx()
                    AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
                }
                else -> AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
            }
        } catch (e: ApolloNetworkException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: ApolloException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: Exception) {
            AniListResult.NetworkError(e)
        }
    }

    /**
     * Resolves a MAL ID to AniList ID with cache hit/miss tracking and in-flight deduplication.
     */
    suspend fun resolveIdMal(malId: Int, type: MediaType): AniListResult<Int> = withContext(Dispatchers.IO) {
        val cached = CacheManager.getAniListIdForMalId(malId)
        if (cached != null) {
            AniListMetrics.recordCacheHit()
            return@withContext AniListResult.Success(cached)
        }
        AniListMetrics.recordCacheMiss()

        AniListClient.deduplicateInFlight("resolve_mal_${malId}_${type.name}") {
            val result = executeResolveMalId(malId, type)
            if (result is AniListResult.Success) {
                CacheManager.putIdMapping(malId = malId, aniListId = result.data)
            }
            result
        }
    }

    /**
     * Executes the Apollo ResolveAniListId query.
     */
    suspend fun executeResolveAniListId(aniListId: Int): AniListResult<Int> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        try {
            val response = client.query(
                ResolveAniListIdQuery(
                    id = Optional.present(aniListId)
                )
            ).execute()
            response.exception?.let { throw it }

            val media = response.data?.Media
            if (media?.idMal != null) {
                return@withContext AniListResult.Success(media.idMal)
            }

            if (response.hasErrors()) {
                val errors = response.errors ?: emptyList()
                if (errors.any { it.message.contains("Not Found", ignoreCase = true) }) {
                    return@withContext AniListResult.NotFound
                }
                AniListMetrics.recordGraphQLError()
                val errorDetails = errors.map { AniListErrorDetail(it.message, null) }
                return@withContext AniListResult.GraphQLError(errorDetails)
            }

            AniListResult.NotFound
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApolloHttpException) {
            when (e.statusCode) {
                429 -> {
                    AniListMetrics.recordRateLimit()
                    AniListResult.RateLimited(60)
                }
                404 -> AniListResult.NotFound
                in 500..599 -> {
                    AniListMetrics.recordHttp5xx()
                    AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
                }
                else -> AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
            }
        } catch (e: ApolloNetworkException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: ApolloException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: Exception) {
            AniListResult.NetworkError(e)
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
        AniListMetrics.recordCacheMiss()

        AniListClient.deduplicateInFlight("resolve_ani_${aniListId}") {
            val result = executeResolveAniListId(aniListId)
            if (result is AniListResult.Success) {
                CacheManager.putIdMapping(malId = result.data, aniListId = aniListId)
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
        try {
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
                AniListMetrics.recordGraphQLError()
                val errorDetails = response.errors?.map { AniListErrorDetail(it.message, null) } ?: emptyList()
                return@withContext AniListResult.GraphQLError(errorDetails)
            }

            val mediaList = response.data?.Page?.media
            if (mediaList == null) {
                return@withContext AniListResult.Success(emptyList())
            }

            val items = mediaList.filterNotNull().map { mapApolloMediaToItem(it, type) }
            AniListResult.Success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApolloHttpException) {
            when (e.statusCode) {
                429 -> {
                    AniListMetrics.recordRateLimit()
                    AniListResult.RateLimited(60)
                }
                404 -> AniListResult.NotFound
                in 500..599 -> {
                    AniListMetrics.recordHttp5xx()
                    AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
                }
                else -> AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
            }
        } catch (e: ApolloNetworkException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: ApolloException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: Exception) {
            AniListResult.NetworkError(e)
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
        format: String? = null
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val cacheKey = "${query}_${type.name}_${genres?.joinToString(",")}_${year}_${format}"
        val cached = CacheManager.getSearch(cacheKey, type.name)
        if (cached != null) {
            AniListMetrics.recordCacheHit()
            return@withContext cached
        }
        AniListMetrics.recordCacheMiss()

        AniListClient.deduplicateInFlight("search_${cacheKey}") {
            val res = executeSearchMedia(query, type, genres, year, format)
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

    private fun mapApolloMediaToItem(
        m: SearchMediaQuery.Medium,
        fallbackType: MediaType
    ): MediaItem {
        val primaryTitle = m.title?.romaji ?: m.title?.english ?: "Unknown Title"
        val englishTitle = m.title?.english
        val img = m.coverImage?.large ?: m.coverImage?.extraLarge ?: m.coverImage?.medium ?: ""
        val imgHd = m.coverImage?.extraLarge ?: m.coverImage?.large ?: m.coverImage?.medium
        val score = if (m.averageScore != null && m.averageScore > 0) m.averageScore / 10.0 else null
        val cleanDescription = m.description
            ?.replace(Regex("<[^>]*>"), "")
            ?.replace("&quot;", "\"")
            ?.replace("&#039;", "'")
            ?.replace("&amp;", "&")

        val statusStr = when (m.status) {
            MediaStatus.RELEASING -> if (fallbackType == MediaType.ANIME) "AIRING" else "PUBLISHING"
            MediaStatus.FINISHED -> if (fallbackType == MediaType.ANIME) "AIRED" else "FINISHED"
            MediaStatus.NOT_YET_RELEASED -> "NOT YET AIRED"
            MediaStatus.CANCELLED -> "CANCELLED"
            else -> m.status?.rawValue ?: "AIRED"
        }

        val studioName = m.studios?.nodes?.firstOrNull()?.name

        // Cache ID mapping
        if (m.idMal != null) {
            CacheManager.putIdMapping(malId = m.idMal, aniListId = m.id)
        }

        return MediaItem(
            malId = m.idMal,
            anilistId = m.id,
            title = primaryTitle,
            titleEnglish = englishTitle,
            imageUrl = img,
            type = fallbackType,
            score = score,
            synopsis = cleanDescription,
            episodes = m.episodes,
            chapters = m.chapters,
            volumes = m.volumes,
            status = statusStr,
            year = m.seasonYear,
            season = m.season?.rawValue,
            genres = m.genres?.filterNotNull() ?: emptyList(),
            format = m.format?.rawValue,
            studio = studioName,
            imageUrlHd = imgHd
        )
    }

    /**
     * Executes GetExtendedDetailsByIdQuery and returns AniListResult<ExtendedMediaDetail>.
     */
    suspend fun executeGetExtendedDetailsById(aniListId: Int): AniListResult<ExtendedMediaDetail> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        try {
            val response = client.query(GetExtendedDetailsByIdQuery(Optional.present(aniListId))).execute()
            response.exception?.let { throw it }
            if (response.hasErrors()) {
                AniListMetrics.recordGraphQLError()
                val errorDetails = response.errors?.map { AniListErrorDetail(it.message, null) } ?: emptyList()
                val is404 = errorDetails.any { it.message.trim().trimEnd('.').equals("Not Found", ignoreCase = true) }
                if (is404) return@withContext AniListResult.NotFound
                return@withContext AniListResult.GraphQLError(errorDetails)
            }
            val media = response.data?.Media
            if (media == null) {
                return@withContext AniListResult.NotFound
            }
            val detail = mapApolloExtendedDetailsToDomain(media.extendedMediaDetailFields, null)
            AniListResult.Success(detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApolloHttpException) {
            when (e.statusCode) {
                429 -> {
                    AniListMetrics.recordRateLimit()
                    AniListResult.RateLimited(60)
                }
                404 -> AniListResult.NotFound
                in 500..599 -> {
                    AniListMetrics.recordHttp5xx()
                    AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
                }
                else -> AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
            }
        } catch (e: ApolloNetworkException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: ApolloException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: Exception) {
            AniListResult.NetworkError(e)
        }
    }

    /**
     * Executes GetExtendedDetailsByMalIdQuery and returns AniListResult<ExtendedMediaDetail>.
     */
    suspend fun executeGetExtendedDetailsByMalId(malId: Int, type: MediaType): AniListResult<ExtendedMediaDetail> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        try {
            val apolloType = if (type == MediaType.ANIME) ApolloMediaType.ANIME else ApolloMediaType.MANGA
            val response = client.query(
                GetExtendedDetailsByMalIdQuery(
                    idMal = Optional.present(malId),
                    type = Optional.present(apolloType)
                )
            ).execute()
            response.exception?.let { throw it }
            if (response.hasErrors()) {
                AniListMetrics.recordGraphQLError()
                val errorDetails = response.errors?.map { AniListErrorDetail(it.message, null) } ?: emptyList()
                val is404 = errorDetails.any { it.message.trim().trimEnd('.').equals("Not Found", ignoreCase = true) }
                if (is404) return@withContext AniListResult.NotFound
                return@withContext AniListResult.GraphQLError(errorDetails)
            }
            val media = response.data?.Media
            if (media == null) {
                return@withContext AniListResult.NotFound
            }
            val detail = mapApolloExtendedDetailsToDomain(media.extendedMediaDetailFields, malId)
            AniListResult.Success(detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApolloHttpException) {
            when (e.statusCode) {
                429 -> {
                    AniListMetrics.recordRateLimit()
                    AniListResult.RateLimited(60)
                }
                404 -> AniListResult.NotFound
                in 500..599 -> {
                    AniListMetrics.recordHttp5xx()
                    AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
                }
                else -> AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
            }
        } catch (e: ApolloNetworkException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: ApolloException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: Exception) {
            AniListResult.NetworkError(e)
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
        val resolvedId = aniListId ?: (malId?.let { CacheManager.getAniListIdForMalId(it) })
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

    private fun formatFuzzyDate(year: Int?, month: Int?, day: Int?): String? {
        if (year == null) return null
        val monthNames = listOf("", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agt", "Sep", "Okt", "Nov", "Des")
        val mStr = if (month != null && month in 1..12) monthNames[month] else null
        return if (day != null && mStr != null) {
            "$day $mStr $year"
        } else if (mStr != null) {
            "$mStr $year"
        } else {
            "$year"
        }
    }

    private fun mapApolloExtendedDetailsToDomain(
        fields: ExtendedMediaDetailFields,
        fallbackMalId: Int?
    ): ExtendedMediaDetail {
        if (fields.idMal != null) {
            CacheManager.putIdMapping(malId = fields.idMal, aniListId = fields.id)
        }

        val castList = fields.characters?.edges?.mapNotNull { edge ->
            val charNode = edge?.node ?: return@mapNotNull null
            val va = edge.voiceActors?.firstOrNull()
            CharacterCastItem(
                characterId = charNode.id,
                characterName = charNode.name?.full ?: "Karakter",
                characterImage = charNode.image?.large ?: charNode.image?.medium,
                actorId = va?.id,
                actorName = va?.name?.full,
                actorImage = va?.image?.large ?: va?.image?.medium,
                role = edge.role?.rawValue ?: "Supporting"
            )
        } ?: emptyList()

        val staffList = fields.staff?.edges?.mapNotNull { edge ->
            val staffNode = edge?.node ?: return@mapNotNull null
            StaffMemberItem(
                staffId = staffNode.id,
                name = staffNode.name?.full ?: "Staff",
                role = edge.role ?: "Crew",
                image = staffNode.image?.large ?: staffNode.image?.medium
            )
        } ?: emptyList()

        val studioNode = fields.studios?.nodes?.firstOrNull()
        val studioName = studioNode?.name
        val studioId = studioNode?.id

        val recList = fields.recommendations?.nodes?.mapNotNull { node ->
            val rec = node?.mediaRecommendation ?: return@mapNotNull null
            val recType = if (rec.type == ApolloMediaType.MANGA || rec.format?.rawValue == "MANGA") MediaType.MANGA else MediaType.ANIME
            MediaItem(
                malId = rec.idMal,
                anilistId = rec.id,
                title = rec.title?.romaji ?: rec.title?.english ?: "Unknown Title",
                titleEnglish = rec.title?.english,
                imageUrl = rec.coverImage?.large ?: rec.coverImage?.medium ?: "",
                score = if (rec.averageScore != null && rec.averageScore > 0) rec.averageScore / 10.0 else null,
                type = recType,
                synopsis = null,
                episodes = null,
                chapters = null,
                volumes = null,
                status = null,
                year = null,
                season = null,
                genres = emptyList(),
                format = rec.format?.rawValue
            )
        } ?: emptyList()

        val relationsList = fields.relations?.edges?.mapNotNull { edge ->
            val node = edge?.node ?: return@mapNotNull null
            val relType = edge.relationType?.rawValue ?: "RELATED"
            MediaRelationItem(
                id = node.id,
                malId = node.idMal,
                title = node.title?.romaji ?: node.title?.english ?: "Unknown",
                titleEnglish = node.title?.english,
                imageUrl = node.coverImage?.large ?: node.coverImage?.medium,
                relationType = relType,
                type = if (node.type == ApolloMediaType.MANGA) MediaType.MANGA else MediaType.ANIME,
                format = node.format?.rawValue,
                status = node.status?.rawValue
            )
        } ?: emptyList()

        val avgScore = if (fields.averageScore != null && fields.averageScore > 0) fields.averageScore / 10.0 else null
        val rankValue = fields.rankings?.firstOrNull { it?.allTime == true }?.rank ?: fields.rankings?.firstOrNull()?.rank

        return ExtendedMediaDetail(
            anilistId = fields.id,
            malId = fields.idMal ?: fallbackMalId,
            title = fields.title?.romaji ?: fields.title?.english ?: "",
            titleEnglish = fields.title?.english,
            nativeTitle = fields.title?.native,
            studio = studioName,
            studioId = studioId,
            source = fields.source?.rawValue,
            airingStatus = fields.status?.rawValue,
            startDate = formatFuzzyDate(fields.startDate?.year, fields.startDate?.month, fields.startDate?.day),
            endDate = formatFuzzyDate(fields.endDate?.year, fields.endDate?.month, fields.endDate?.day),
            genres = fields.genres?.filterNotNull() ?: emptyList(),
            durationMinutes = fields.duration,
            cast = castList,
            crew = staffList,
            relations = relationsList,
            averageScore = avgScore,
            popularity = fields.popularity,
            rank = rankValue,
            watchers = fields.popularity,
            recommendations = recList,
            isFromFallback = false
        )
    }

    /**
     * Executes GetCharacterProfileQuery and returns AniListResult<CastCrewProfile>.
     */
    suspend fun executeGetCharacterProfile(id: Int): AniListResult<CastCrewProfile> = withContext(Dispatchers.IO) {
        AniListMetrics.recordRequest()
        try {
            val response = client.query(GetCharacterProfileQuery(Optional.present(id))).execute()
            response.exception?.let { throw it }
            if (response.hasErrors()) {
                AniListMetrics.recordGraphQLError()
                val errorDetails = response.errors?.map { AniListErrorDetail(it.message, null) } ?: emptyList()
                val is404 = errorDetails.any { it.message.trim().trimEnd('.').equals("Not Found", ignoreCase = true) }
                if (is404) return@withContext AniListResult.NotFound
                return@withContext AniListResult.GraphQLError(errorDetails)
            }
            val char = response.data?.Character
            if (char == null) {
                return@withContext AniListResult.NotFound
            }

            val fullName = char.name?.full?.takeIf { it.isNotBlank() } ?: "Karakter"
            val nativeName = char.name?.native?.takeIf { it.isNotBlank() }
            val firstName = char.name?.first?.takeIf { it.isNotBlank() }
            val lastName = char.name?.last?.takeIf { it.isNotBlank() }

            val imageUrl = char.image?.large?.takeIf { it.isNotBlank() }
                ?: char.image?.medium?.takeIf { it.isNotBlank() }

            val cleanDesc = char.description?.let { TextSanitizer.sanitize(it) }?.takeIf { it.isNotBlank() }

            val filmography = char.media?.edges?.mapNotNull { edge ->
                val role = edge?.characterRole?.rawValue ?: "Character"
                val node = edge?.node ?: return@mapNotNull null
                val mId = node.id
                val malId = node.idMal
                val tRomaji = node.title?.romaji
                val tEng = node.title?.english
                val cImg = node.coverImage?.large ?: node.coverImage?.medium
                val sYear = node.startDate?.year
                val fmt = node.format?.rawValue
                val mType = if (node.type == ApolloMediaType.MANGA) MediaType.MANGA else MediaType.ANIME

                FilmographyItem(
                    id = mId,
                    malId = malId,
                    title = tRomaji ?: tEng ?: "Judul",
                    titleEnglish = tEng,
                    imageUrl = cImg,
                    year = sYear,
                    format = fmt,
                    type = mType,
                    role = role,
                    characterName = fullName,
                    characterImage = imageUrl
                )
            } ?: emptyList()

            val profile = CastCrewProfile(
                id = id,
                isStaff = false,
                name = fullName,
                nativeName = nativeName,
                firstName = firstName,
                lastName = lastName,
                image = imageUrl,
                biography = cleanDesc,
                nationality = null,
                birthday = null,
                age = null,
                gender = null,
                filmography = filmography
            )
            AniListResult.Success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApolloHttpException) {
            when (e.statusCode) {
                429 -> {
                    AniListMetrics.recordRateLimit()
                    AniListResult.RateLimited(60)
                }
                404 -> AniListResult.NotFound
                in 500..599 -> {
                    AniListMetrics.recordHttp5xx()
                    AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
                }
                else -> AniListResult.HttpError(e.statusCode, e.message ?: "HTTP Error ${e.statusCode}", emptyList())
            }
        } catch (e: ApolloNetworkException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: ApolloException) {
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: Exception) {
            AniListResult.NetworkError(e)
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

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Step 5.3 â€” GetStaffProfile
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private suspend fun executeGetStaffProfile(id: Int): AniListResult<CastCrewProfile> {
        AniListMetrics.recordRequest()
        return try {
            val query = GetStaffProfileQuery(id = Optional.present(id))
            val response = client.query(query).execute()
            val staff = response.data?.Staff
            if (staff == null) {
                AniListMetrics.recordHttp5xx()
                return AniListResult.NetworkError(Exception("Staff not found for id=$id"))
            }

            val name = staff.name
            val fullName = name?.full?.takeIf { it.isNotBlank() } ?: "Staff"
            val nativeName = name?.native?.takeIf { it.isNotBlank() }
            val firstName = name?.first?.takeIf { it.isNotBlank() }
            val lastName = name?.last?.takeIf { it.isNotBlank() }

            val imageUrl = staff.image?.large?.takeIf { it.isNotBlank() }
                ?: staff.image?.medium?.takeIf { it.isNotBlank() }

            val cleanDesc = staff.description
                ?.let { TextSanitizer.sanitize(it) }
                ?.takeIf { it.isNotBlank() }

            // Build filmography from character voice roles (characters connection)
            val filmography = mutableListOf<FilmographyItem>()
            val charEdges = staff.characters?.edges
            if (charEdges != null) {
                for (edge in charEdges) {
                    val charNode = edge?.node ?: continue
                    val charName = charNode.name?.full
                    val charImg = charNode.image?.large?.takeIf { it.isNotBlank() }
                        ?: charNode.image?.medium?.takeIf { it.isNotBlank() }
                    val roleStr = edge.role?.rawValue ?: "MAIN"

                    // Each character edge has media: [Media] â€” take first
                    val mediaList = edge.media ?: emptyList()
                    for (m in mediaList) {
                        if (m == null) continue
                        val mId = m.id
                        val malId = m.idMal
                        val tRomaji = m.title?.romaji?.takeIf { it.isNotBlank() }
                        val tEng = m.title?.english?.takeIf { it.isNotBlank() }
                        val cImg = m.coverImage?.large?.takeIf { it.isNotBlank() }
                            ?: m.coverImage?.medium?.takeIf { it.isNotBlank() }
                        val sYear = m.startDate?.year?.takeIf { it > 0 }
                        val fmt = m.format?.rawValue
                        val mType = if (m.type?.rawValue == "MANGA") MediaType.MANGA else MediaType.ANIME

                        filmography.add(
                            FilmographyItem(
                                id = mId,
                                malId = malId,
                                title = tRomaji ?: tEng ?: "Judul",
                                titleEnglish = tEng,
                                imageUrl = cImg,
                                year = sYear,
                                format = fmt,
                                type = mType,
                                role = roleStr,
                                characterName = charName,
                                characterImage = charImg
                            )
                        )
                    }
                }
            }

            // Build filmography from production staff roles (staffMedia connection)
            val staffEdges = staff.staffMedia?.edges
            if (staffEdges != null) {
                for (edge in staffEdges) {
                    val node = edge?.node ?: continue
                    val mId = node.id
                    val malId = node.idMal
                    val tRomaji = node.title?.romaji?.takeIf { it.isNotBlank() }
                    val tEng = node.title?.english?.takeIf { it.isNotBlank() }
                    val cImg = node.coverImage?.large?.takeIf { it.isNotBlank() }
                        ?: node.coverImage?.medium?.takeIf { it.isNotBlank() }
                    val sYear = node.startDate?.year?.takeIf { it > 0 }
                    val fmt = node.format?.rawValue
                    val mType = if (node.type?.rawValue == "MANGA") MediaType.MANGA else MediaType.ANIME
                    val role = edge.staffRole?.takeIf { it.isNotBlank() } ?: "Staff"

                    filmography.add(
                        FilmographyItem(
                            id = mId,
                            malId = malId,
                            title = tRomaji ?: tEng ?: "Judul",
                            titleEnglish = tEng,
                            imageUrl = cImg,
                            year = sYear,
                            format = fmt,
                            type = mType,
                            role = role,
                            characterName = null,
                            characterImage = null
                        )
                    )
                }
            }

            val profile = CastCrewProfile(
                id = id,
                isStaff = true,
                name = fullName,
                nativeName = nativeName,
                firstName = firstName,
                lastName = lastName,
                image = imageUrl,
                biography = cleanDesc,
                nationality = null,   // not in this schema version
                birthday = null,      // not in this schema version
                age = null,           // not in this schema version
                gender = null,        // not in this schema version
                filmography = filmography.distinctBy { it.id }
            )
                        AniListResult.Success(profile)
        } catch (e: ApolloHttpException) {
            AniListMetrics.recordHttp5xx()
            AniListResult.HttpError(e.statusCode, e.message)
        } catch (e: ApolloNetworkException) {
            AniListMetrics.recordTimeout()
            val cause = e.cause
            if (cause is SocketTimeoutException) {
                AniListMetrics.recordTimeout()
                AniListResult.Timeout(isReadTimeout = true)
            } else {
                AniListResult.NetworkError(cause ?: e)
            }
        } catch (e: Exception) {
            AniListResult.NetworkError(e)
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

    private fun mapApolloMediaNodeToItem(
        node: GetStudioFilmographyQuery.Node,
        studioName: String
    ): MediaItem? {
        val mId = node.id.takeIf { it > 0 } ?: return null
        val malId = node.idMal
        val tRomaji = node.title?.romaji?.takeIf { it.isNotBlank() }
        val tEng = node.title?.english?.takeIf { it.isNotBlank() }
        val cover = node.coverImage?.large?.takeIf { it.isNotBlank() }
            ?: node.coverImage?.medium?.takeIf { it.isNotBlank() }
            ?: node.coverImage?.extraLarge?.takeIf { it.isNotBlank() } ?: ""
        val coverHd = node.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
            ?: node.coverImage?.large?.takeIf { it.isNotBlank() }
        val fmt = node.format?.rawValue
        val mType = if (node.type?.rawValue == "MANGA") MediaType.MANGA else MediaType.ANIME
        val status = node.status?.rawValue
        val episodes = node.episodes?.takeIf { it > 0 }
        val chapters = node.chapters?.takeIf { it > 0 }
        val avgScore = node.averageScore ?: 0
        val score = if (avgScore > 0) avgScore / 10.0 else null
        val popularity = node.popularity?.takeIf { it > 0 }
        val genresList = node.genres?.filterNotNull() ?: emptyList()
        val year = node.startDate?.year?.takeIf { it > 0 }

        CacheManager.putIdMapping(mId, mId) // ensure anilist id is registered
        malId?.let { CacheManager.putIdMapping(it, mId) }

        return MediaItem(
            malId = malId,
            anilistId = mId,
            title = tRomaji ?: tEng ?: "Judul",
            titleEnglish = tEng,
            imageUrl = cover,
            type = mType,
            score = score,
            format = fmt,
            status = status,
            episodes = episodes,
            chapters = chapters,
            genres = genresList,
            year = year,
            studio = studioName,
            popularity = popularity,
            imageUrlHd = coverHd
        )
    }

    suspend fun fetchStudioFilmography(
        studioId: Int?,
        search: String? = null,
        page: Int = 1,
        perPage: Int = 24,
        sort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC,
        forceRefresh: Boolean = false
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

        val dedupeKey = "studio_${studioId}_${search}_${page}_${sort.name}"
        AniListClient.deduplicateInFlight(dedupeKey) {
            AniListMetrics.recordRequest()
            try {
                val query = GetStudioFilmographyQuery(
                    id = if (studioId != null) Optional.present(studioId) else Optional.absent(),
                    search = if (!search.isNullOrBlank()) Optional.present(search) else Optional.absent(),
                    page = Optional.present(page),
                    perPage = Optional.present(perPage),
                    sort = Optional.present(apolloSort)
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
                        else mapApolloMediaNodeToItem(node, resolvedName)
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
            } catch (e: ApolloHttpException) {
                AniListMetrics.recordHttp5xx()
                null
            } catch (e: ApolloNetworkException) {
                AniListMetrics.recordTimeout()
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Step 5.5 â€” SearchStudios
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

                    val info = StudioBioRegistry.getStudioInfo(sId, sName).let { base ->
                        base.copy(
                            studioId = sId,
                            name = sName,
                            coverUrl = base.coverUrl ?: topCover,
                            favourites = base.favourites ?: favs,
                            officialSite = base.officialSite ?: siteUrl
                        )
                    }
                    StudioBioRegistry.saveToPersistentCache(info)
                    results.add(info)
                }
                                results
            } catch (e: ApolloHttpException) {
                AniListMetrics.recordHttp5xx()
                emptyList()
            } catch (e: ApolloNetworkException) {
                AniListMetrics.recordTimeout()
                emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Step 5.6 â€” GetDiscoverMedia
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun mapApolloDiscoverMediaToItem(
        m: GetDiscoverMediaQuery.Medium,
        fallbackType: MediaType
    ): MediaItem {
        val primaryTitle = m.title?.romaji?.takeIf { it.isNotBlank() }
            ?: m.title?.english?.takeIf { it.isNotBlank() }
            ?: "Unknown Title"
        val englishTitle = m.title?.english
        val img = m.coverImage?.large?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.medium?.takeIf { it.isNotBlank() }
            ?: ""
        val imgHd = m.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.large?.takeIf { it.isNotBlank() }
        val score = if ((m.averageScore ?: 0) > 0) m.averageScore!! / 10.0 else null
        val cleanDesc = m.description?.let { TextSanitizer.sanitize(it) }

        val statusStr = when (m.status?.rawValue) {
            "RELEASING" -> if (fallbackType == MediaType.ANIME) "AIRING" else "PUBLISHING"
            "FINISHED"  -> if (fallbackType == MediaType.ANIME) "AIRED" else "FINISHED"
            "NOT_YET_RELEASED" -> "NOT YET AIRED"
            "CANCELLED" -> "CANCELLED"
            "HIATUS"    -> "ON HIATUS"
            else        -> m.status?.rawValue?.uppercase() ?: "AIRED"
        }

        val studioName = m.studios?.nodes?.firstOrNull()?.name
        if (m.idMal != null) CacheManager.putIdMapping(m.idMal, m.id)

        return MediaItem(
            malId = m.idMal,
            anilistId = m.id,
            title = primaryTitle,
            titleEnglish = englishTitle,
            imageUrl = img,
            type = fallbackType,
            score = score,
            synopsis = cleanDesc,
            episodes = m.episodes?.takeIf { it > 0 },
            chapters = m.chapters?.takeIf { it > 0 },
            volumes = m.volumes?.takeIf { it > 0 },
            status = statusStr,
            year = m.seasonYear,
            season = m.season?.rawValue,
            genres = m.genres?.filterNotNull() ?: emptyList(),
            format = m.format?.rawValue,
            studio = studioName,
            imageUrlHd = imgHd
        )
    }

    suspend fun getDiscoverMedia(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        perPage: Int = 25,
        randomSort: String? = null,
        forceRefresh: Boolean = false
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

        val cacheKey = "${category.key}_${filter.genre}_${filter.format}_${filter.year}_${filter.season}_${filter.minScore}_${randomSort}_p$page"
        if (!forceRefresh) {
            val cached = CacheManager.getDiscover(cacheKey)
            if (cached != null) {
                AniListMetrics.recordCacheHit()
                return@withContext cached
            }
        }
        AniListMetrics.recordCacheMiss()

        val isManga = filter.format == "MANGA"
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
                apolloSort = Optional.present(listOf(MediaSort.END_DATE_DESC))
            }
            DiscoverCategory.NEWLY_ADDED_MANGA -> {
                apolloSort = Optional.present(listOf(MediaSort.ID_DESC))
            }
        }

        // Apply filter overrides
        filter.genre?.let { apolloGenre = Optional.present(it) }
        filter.year?.let { apolloSeasonYear = Optional.present(it) }
        filter.season?.let { apolloSeason = Optional.present(MediaSeason.safeValueOf(it)) }
        filter.minScore?.let { apolloMinScore = Optional.present(it) }
        filter.format?.takeIf { it != "MANGA" }?.let {
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
                val items = mediaList.filterNotNull().map { mapApolloDiscoverMediaToItem(it, fallbackType) }
                                if (items.isNotEmpty()) CacheManager.putDiscover(cacheKey, items)
                items
            } catch (e: ApolloHttpException) {
                AniListMetrics.recordHttp5xx()
                emptyList()
            } catch (e: ApolloNetworkException) {
                AniListMetrics.recordTimeout()
                emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Step 5.7 â€” GetMediaBatchByMalIds
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun mapApolloBatchMediaToItem(
        m: GetMediaBatchByMalIdsQuery.Medium,
        fallbackType: MediaType
    ): MediaItem {
        val primaryTitle = m.title?.romaji?.takeIf { it.isNotBlank() }
            ?: m.title?.english?.takeIf { it.isNotBlank() }
            ?: "Unknown Title"
        val englishTitle = m.title?.english
        val img = m.coverImage?.large?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.medium?.takeIf { it.isNotBlank() }
            ?: ""
        val imgHd = m.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.large?.takeIf { it.isNotBlank() }
        val score = if ((m.averageScore ?: 0) > 0) m.averageScore!! / 10.0 else null
        val cleanDesc = m.description?.let { TextSanitizer.sanitize(it) }

        val statusStr = when (m.status?.rawValue) {
            "RELEASING" -> if (fallbackType == MediaType.ANIME) "AIRING" else "PUBLISHING"
            "FINISHED"  -> if (fallbackType == MediaType.ANIME) "AIRED" else "FINISHED"
            "NOT_YET_RELEASED" -> "NOT YET AIRED"
            "CANCELLED" -> "CANCELLED"
            "HIATUS"    -> "ON HIATUS"
            else        -> m.status?.rawValue?.uppercase() ?: "AIRED"
        }

        val studioName = m.studios?.nodes?.firstOrNull()?.name
        if (m.idMal != null) CacheManager.putIdMapping(m.idMal, m.id)

        return MediaItem(
            malId = m.idMal,
            anilistId = m.id,
            title = primaryTitle,
            titleEnglish = englishTitle,
            imageUrl = img,
            type = fallbackType,
            score = score,
            synopsis = cleanDesc,
            episodes = m.episodes?.takeIf { it > 0 },
            chapters = m.chapters?.takeIf { it > 0 },
            volumes = m.volumes?.takeIf { it > 0 },
            status = statusStr,
            year = m.seasonYear,
            season = m.season?.rawValue,
            genres = m.genres?.filterNotNull() ?: emptyList(),
            format = m.format?.rawValue,
            studio = studioName,
            imageUrlHd = imgHd
        )
    }

    suspend fun getMediaBatchByMalIds(
        malIds: List<Int>,
        type: MediaType
    ): Map<Int, MediaItem> = withContext(Dispatchers.IO) {
        if (malIds.isEmpty()) return@withContext emptyMap()
        val resultMap = mutableMapOf<Int, MediaItem>()
        val distinctIds = malIds.distinct().filter { it > 0 }
        val apolloType: ApolloMediaType = if (type == MediaType.ANIME) ApolloMediaType.ANIME else ApolloMediaType.MANGA

        for (chunk in distinctIds.chunked(50)) {
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
                    for (m in mediaList.filterNotNull()) {
                        val item = mapApolloBatchMediaToItem(m, type)
                        m.idMal?.let { malId -> chunkResult[malId] = item }
                    }
                                        chunkResult
                } catch (e: ApolloHttpException) {
                    AniListMetrics.recordHttp5xx()
                    mutableMapOf()
                } catch (e: ApolloNetworkException) {
                    AniListMetrics.recordTimeout()
                    mutableMapOf()
                } catch (e: Exception) {
                    mutableMapOf()
                }
            }
            resultMap.putAll(chunkMap)
        }
        resultMap
    }
}




