package com.canim.app.data.repository

import android.content.Context
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.*
import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.remote.ApiClient
import com.canim.app.data.remote.AniListClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Single source of coordination for CA'NIM.
 * Principle:
 * - MAL is authoritative for user tracking data.
 * - AniList is the primary provider for rich metadata.
 * - CA'NIM acts as a client/UI layer.
 */
class CanimRepository(
    val malAuthManager: MalAuthManager
) {
    fun buildMalAuthorizeUrl(): String = malAuthManager.buildAuthorizeUrl()

    suspend fun handleMalOAuthCallback(code: String, state: String?): Result<MalUser> =
        malAuthManager.handleOAuthCallback(code, state)

    fun getMalUser(): MalUser = malAuthManager.getCurrentUser()

    fun logoutMal() {
        malAuthManager.logout()
    }

    suspend fun syncWithMal(): MalSyncResult = malAuthManager.syncWithMal()

    fun getCachedTracking(type: String): List<UserMediaItem>? {
        val memory = CacheManager.getTracking(type)
        if (memory != null) return memory
        val appContext = runCatching { com.canim.app.CanimApplication.instance }.getOrNull()
        if (appContext != null) {
            val disk = CacheManager.loadTrackingFromDisk(appContext, type)
            if (disk != null) {
                CacheManager.putTracking(type, disk)
                return disk
            }
        }
        return null
    }

    /**
     * Loads the user's anime list from MAL as source of truth, enriched with AniList metadata.
     * Batches metadata requests via AniList GraphQL (50 per batch) to avoid API request storms.
     */
    suspend fun getUserAnimeList(forceRefresh: Boolean = false): MalFetchResult<List<UserMediaItem>> = withContext(Dispatchers.IO) {
        val result = malAuthManager.fetchUserAnimeList(forceRefresh = forceRefresh)
        when (result) {
            is MalFetchResult.Failure -> result
            is MalFetchResult.Success -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.ANIME)
                CacheManager.putTracking("ANIME", enriched)
                runCatching { com.canim.app.CanimApplication.instance }.getOrNull()?.let {
                    CacheManager.saveTrackingToDisk(it, "ANIME", enriched)
                }
                MalFetchResult.Success(enriched, result.totalItems)
            }
            is MalFetchResult.Partial -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.ANIME)
                CacheManager.putTracking("ANIME", enriched)
                runCatching { com.canim.app.CanimApplication.instance }.getOrNull()?.let {
                    CacheManager.saveTrackingToDisk(it, "ANIME", enriched)
                }
                MalFetchResult.Partial(enriched, result.fetchedItems, result.error)
            }
        }
    }

    /**
     * Loads the user's manga list from MAL as source of truth, enriched with AniList metadata.
     */
    suspend fun getUserMangaList(forceRefresh: Boolean = false): MalFetchResult<List<UserMediaItem>> = withContext(Dispatchers.IO) {
        val result = malAuthManager.fetchUserMangaList(forceRefresh = forceRefresh)
        when (result) {
            is MalFetchResult.Failure -> result
            is MalFetchResult.Success -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.MANGA)
                CacheManager.putTracking("MANGA", enriched)
                runCatching { com.canim.app.CanimApplication.instance }.getOrNull()?.let {
                    CacheManager.saveTrackingToDisk(it, "MANGA", enriched)
                }
                MalFetchResult.Success(enriched, result.totalItems)
            }
            is MalFetchResult.Partial -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.MANGA)
                CacheManager.putTracking("MANGA", enriched)
                runCatching { com.canim.app.CanimApplication.instance }.getOrNull()?.let {
                    CacheManager.saveTrackingToDisk(it, "MANGA", enriched)
                }
                MalFetchResult.Partial(enriched, result.fetchedItems, result.error)
            }
        }
    }

    suspend fun getCharacterProfile(characterId: Int, forceRefresh: Boolean = false): CastCrewProfile? = withContext(Dispatchers.IO) {
        AniListClient.getCharacterProfile(characterId, forceRefresh)
    }

    suspend fun getStaffProfile(staffId: Int, forceRefresh: Boolean = false): CastCrewProfile? = withContext(Dispatchers.IO) {
        AniListClient.getStaffProfile(staffId, forceRefresh)
    }

    private suspend fun enrichWithAniListMetadata(
        items: List<UserMediaItem>,
        type: MediaType
    ): List<UserMediaItem> = withContext(Dispatchers.IO) {
        val malIds = items.mapNotNull { it.malId }
        val aniListMap = try {
            AniListClient.getMediaBatchByMalIds(malIds, type)
        } catch (_: Exception) {
            emptyMap()
        }

        items.map { item ->
            val mId = item.malId
            val aniItem = mId?.let { aniListMap[it] }
            if (aniItem != null) {
                val updatedMetadata = item.metadata.copy(
                    titleEnglish = aniItem.titleEnglish ?: item.metadata.titleEnglish,
                    imageUrl = aniItem.imageUrl.ifBlank { item.metadata.imageUrl },
                    totalEpisodes = aniItem.episodes ?: item.metadata.totalEpisodes,
                    totalChapters = aniItem.chapters ?: item.metadata.totalChapters,
                    totalVolumes = aniItem.volumes ?: item.metadata.totalVolumes,
                    genres = if (aniItem.genres.isNotEmpty()) aniItem.genres else item.metadata.genres,
                    studio = aniItem.studio ?: item.metadata.studio,
                    format = aniItem.format ?: item.metadata.format,
                    year = aniItem.year ?: item.metadata.year,
                    season = aniItem.season ?: item.metadata.season
                )
                item.copy(
                    identity = MediaRef(anilistId = aniItem.anilistId, malId = mId),
                    metadata = updatedMetadata
                )
            } else {
                item
            }
        }
    }

    // --- Tracking Mutations (Bidirectional MAL Operations) ---
    suspend fun updateAnimeTracking(malId: Int, tracking: MalTracking): Result<Unit> =
        malAuthManager.updateAnimeTracking(malId, tracking)

    suspend fun updateMangaTracking(malId: Int, tracking: MalTracking): Result<Unit> =
        malAuthManager.updateMangaTracking(malId, tracking)

    suspend fun deleteAnimeTracking(malId: Int): Result<Unit> =
        malAuthManager.deleteAnimeTracking(malId)

    suspend fun deleteMangaTracking(malId: Int): Result<Unit> =
        malAuthManager.deleteMangaTracking(malId)

    // --- Helpers for MyAnimeList Node Mapping ---
    private fun mapMalAnimeNodeToMediaItem(node: MalAnimeNode): MediaItem {
        return MediaItem(
            malId = node.id,
            anilistId = CacheManager.getAniListIdForMalId(node.id) ?: node.id,
            title = node.title,
            titleEnglish = node.alternativeTitles?.en ?: node.title,
            imageUrl = node.mainPicture?.large ?: node.mainPicture?.medium ?: "",
            type = MediaType.ANIME,
            score = node.mean,
            synopsis = node.synopsis ?: "",
            episodes = node.numEpisodes,
            chapters = null,
            volumes = null,
            status = when (node.status?.lowercase()) {
                "currently_airing" -> "AIRING"
                "finished_airing" -> "AIRED"
                "not_yet_aired" -> "NOT YET AIRED"
                else -> node.status?.uppercase() ?: "AIRED"
            },
            year = node.startDate?.take(4)?.toIntOrNull(),
            season = null,
            genres = node.genres?.map { it.name } ?: emptyList(),
            format = "TV",
            studio = node.studios?.firstOrNull()?.name
        )
    }

    private fun mapMalMangaNodeToMediaItem(node: MalMangaNode): MediaItem {
        return MediaItem(
            malId = node.id,
            anilistId = CacheManager.getAniListIdForMalId(node.id) ?: node.id,
            title = node.title,
            titleEnglish = node.alternativeTitles?.en ?: node.title,
            imageUrl = node.mainPicture?.large ?: node.mainPicture?.medium ?: "",
            type = MediaType.MANGA,
            score = node.mean,
            synopsis = node.synopsis ?: "",
            episodes = null,
            chapters = node.numChapters,
            volumes = node.numVolumes,
            status = when (node.status?.lowercase()) {
                "currently_publishing" -> "PUBLISHING"
                "finished" -> "FINISHED"
                "on_hiatus" -> "ON HIATUS"
                "discontinued" -> "CANCELLED"
                else -> node.status?.uppercase() ?: "FINISHED"
            },
            year = node.startDate?.take(4)?.toIntOrNull(),
            season = null,
            genres = node.genres?.map { it.name } ?: emptyList(),
            format = "MANGA",
            studio = node.authors?.firstOrNull()?.name
        )
    }

    suspend fun searchAnime(
        query: String,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val filterKey = "${trimmed}_${genres?.sorted()?.joinToString(",")}_${year}_${format}"
        if (trimmed.isEmpty() && genres.isNullOrEmpty() && year == null && format == null) return@withContext emptyList()

        val cached = CacheManager.getSearch(filterKey, "ANIME")
        if (cached != null) return@withContext cached

        var result = runCatching {
            AniListClient.searchMedia(trimmed, MediaType.ANIME, genres, year, format)
        }.getOrDefault(emptyList())

        // Fallback to MyAnimeList Search API v2 if AniList is down / empty
        if (result.isEmpty()) {
            try {
                val malItems = if (trimmed.isNotBlank()) {
                    val malResp = ApiClient.malApi.searchAnime(MalAuthManager.CLIENT_ID, trimmed, limit = 50)
                    if (malResp.isSuccessful && malResp.body()?.data?.isNotEmpty() == true) {
                        malResp.body()!!.data.map { mapMalAnimeNodeToMediaItem(it.node) }
                    } else emptyList()
                } else if (!genres.isNullOrEmpty() || year != null || !format.isNullOrBlank()) {
                    val malResp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "all", limit = 50)
                    if (malResp.isSuccessful && malResp.body()?.data?.isNotEmpty() == true) {
                        malResp.body()!!.data.map { mapMalAnimeNodeToMediaItem(it.node) }
                    } else emptyList()
                } else emptyList()

                if (malItems.isNotEmpty()) {
                    var filtered = malItems
                    if (!genres.isNullOrEmpty()) {
                        filtered = filtered.filter { item ->
                            item.genres.any { g -> genres.any { sel -> g.contains(sel, ignoreCase = true) } }
                        }
                    }
                    if (year != null) {
                        filtered = filtered.filter { item -> item.year == year }
                    }
                    if (!format.isNullOrBlank()) {
                        filtered = filtered.filter { item -> item.format.equals(format, ignoreCase = true) }
                    }
                    if (filtered.isNotEmpty()) {
                        result = filtered
                    }
                }
            } catch (_: Exception) {}
        }

        if (result.isEmpty()) {
            val baseList = fallbackAnime()
            var localMatches = baseList
            if (trimmed.isNotBlank()) {
                localMatches = localMatches.filter {
                    it.title.contains(trimmed, ignoreCase = true) ||
                    (it.titleEnglish?.contains(trimmed, ignoreCase = true) == true)
                }
            }
            if (!genres.isNullOrEmpty()) {
                localMatches = localMatches.filter { item ->
                    item.genres.any { g -> genres.any { sel -> g.contains(sel, ignoreCase = true) } }
                }
            }
            if (year != null) {
                localMatches = localMatches.filter { item -> item.year == year }
            }
            if (!format.isNullOrBlank()) {
                localMatches = localMatches.filter { item -> item.format.equals(format, ignoreCase = true) }
            }
            if (localMatches.isNotEmpty()) {
                result = localMatches
            }
        }

        if (result.isNotEmpty()) {
            CacheManager.putSearch(filterKey, "ANIME", result)
        }
        result
    }

    suspend fun searchManga(
        query: String,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val filterKey = "${trimmed}_${genres?.sorted()?.joinToString(",")}_${year}_${format}"
        if (trimmed.isEmpty() && genres.isNullOrEmpty() && year == null && format == null) return@withContext emptyList()

        val cached = CacheManager.getSearch(filterKey, "MANGA")
        if (cached != null) return@withContext cached

        var result = runCatching {
            AniListClient.searchMedia(trimmed, MediaType.MANGA, genres, year, format)
        }.getOrDefault(emptyList())

        // Fallback to MyAnimeList Search API v2 if AniList is down / empty
        if (result.isEmpty()) {
            try {
                val malItems = if (trimmed.isNotBlank()) {
                    val malResp = ApiClient.malApi.searchManga(MalAuthManager.CLIENT_ID, trimmed, limit = 50)
                    if (malResp.isSuccessful && malResp.body()?.data?.isNotEmpty() == true) {
                        malResp.body()!!.data.map { mapMalMangaNodeToMediaItem(it.node) }
                    } else emptyList()
                } else if (!genres.isNullOrEmpty() || year != null || !format.isNullOrBlank()) {
                    val malResp = ApiClient.malApi.getMangaRanking(MalAuthManager.CLIENT_ID, "all", limit = 50)
                    if (malResp.isSuccessful && malResp.body()?.data?.isNotEmpty() == true) {
                        malResp.body()!!.data.map { mapMalMangaNodeToMediaItem(it.node) }
                    } else emptyList()
                } else emptyList()

                if (malItems.isNotEmpty()) {
                    var filtered = malItems
                    if (!genres.isNullOrEmpty()) {
                        filtered = filtered.filter { item ->
                            item.genres.any { g -> genres.any { sel -> g.contains(sel, ignoreCase = true) } }
                        }
                    }
                    if (year != null) {
                        filtered = filtered.filter { item -> item.year == year }
                    }
                    if (!format.isNullOrBlank()) {
                        filtered = filtered.filter { item -> item.format.equals(format, ignoreCase = true) }
                    }
                    if (filtered.isNotEmpty()) {
                        result = filtered
                    }
                }
            } catch (_: Exception) {}
        }

        if (result.isEmpty()) {
            val baseList = fallbackManga()
            var localMatches = baseList
            if (trimmed.isNotBlank()) {
                localMatches = localMatches.filter {
                    it.title.contains(trimmed, ignoreCase = true) ||
                    (it.titleEnglish?.contains(trimmed, ignoreCase = true) == true)
                }
            }
            if (!genres.isNullOrEmpty()) {
                localMatches = localMatches.filter { item ->
                    item.genres.any { g -> genres.any { sel -> g.contains(sel, ignoreCase = true) } }
                }
            }
            if (year != null) {
                localMatches = localMatches.filter { item -> item.year == year }
            }
            if (!format.isNullOrBlank()) {
                localMatches = localMatches.filter { item -> item.format.equals(format, ignoreCase = true) }
            }
            if (localMatches.isNotEmpty()) {
                result = localMatches
            }
        }

        if (result.isNotEmpty()) {
            CacheManager.putSearch(filterKey, "MANGA", result)
        }
        result
    }

    // --- Discover with On-Demand Loading & forceRefresh Propagation ---
    suspend fun getDiscoverMedia(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        forceRefresh: Boolean = false,
        randomSort: String? = null
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val cacheKey = "${category.key}_${filter.genre}_${filter.format}_${filter.year}_${filter.season}_${filter.minScore}_${randomSort}_p$page"
        if (!forceRefresh) {
            val cached = CacheManager.getDiscover(cacheKey)
            if (cached != null) return@withContext cached
        }

        val limit = 25
        val offset = (page - 1) * limit

        // B.4: Top Anime exclusively based on MAL API ranking
        if (category == DiscoverCategory.TOP_ANIME) {
            try {
                val resp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "all", limit, offset)
                if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                    val malNodes = resp.body()!!.data.map { it.node }
                    val malIds = malNodes.map { it.id }
                    val aniMap = runCatching { AniListClient.getMediaBatchByMalIds(malIds, MediaType.ANIME) }.getOrDefault(emptyMap())
                    val items = malNodes.map { node ->
                        val ani = aniMap[node.id]
                        if (ani != null) {
                            ani.copy(
                                score = node.mean ?: ani.score,
                                imageUrl = ani.imageUrl.ifBlank { node.mainPicture?.large ?: node.mainPicture?.medium ?: "" },
                                synopsis = ani.synopsis?.ifBlank { node.synopsis ?: "" } ?: (node.synopsis ?: "")
                            )
                        } else {
                            mapMalAnimeNodeToMediaItem(node)
                        }
                    }
                    CacheManager.putDiscover(cacheKey, items)
                    return@withContext items
                }
            } catch (_: Exception) {}
        } else if (category == DiscoverCategory.TOP_MANGA) {
            try {
                val resp = ApiClient.malApi.getMangaRanking(MalAuthManager.CLIENT_ID, "all", limit, offset)
                if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                    val malNodes = resp.body()!!.data.map { it.node }
                    val malIds = malNodes.map { it.id }
                    val aniMap = runCatching { AniListClient.getMediaBatchByMalIds(malIds, MediaType.MANGA) }.getOrDefault(emptyMap())
                    val items = malNodes.map { node ->
                        val ani = aniMap[node.id]
                        if (ani != null) {
                            ani.copy(
                                score = node.mean ?: ani.score,
                                imageUrl = ani.imageUrl.ifBlank { node.mainPicture?.large ?: node.mainPicture?.medium ?: "" },
                                synopsis = ani.synopsis?.ifBlank { node.synopsis ?: "" } ?: (node.synopsis ?: "")
                            )
                        } else {
                            mapMalMangaNodeToMediaItem(node)
                        }
                    }
                    CacheManager.putDiscover(cacheKey, items)
                    return@withContext items
                }
            } catch (_: Exception) {}
        }

        // Try AniList first if not top ranking
        var results = runCatching {
            AniListClient.getDiscoverMedia(
                category = category,
                filter = filter,
                page = page,
                randomSort = randomSort,
                forceRefresh = forceRefresh
            )
        }.getOrDefault(emptyList())

        // Resilient Dual-Engine: If AniList fails/is down, fallback to MyAnimeList API
        if (results.isEmpty()) {
            try {
                when (category) {
                    DiscoverCategory.CURRENT_SEASON -> {
                        val resp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "airing", limit, offset)
                        if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                            results = resp.body()!!.data.map { mapMalAnimeNodeToMediaItem(it.node) }
                        }
                    }
                    DiscoverCategory.NEXT_SEASON, DiscoverCategory.UPCOMING, DiscoverCategory.TBA -> {
                        val resp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "upcoming", limit, offset)
                        if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                            results = resp.body()!!.data.map { mapMalAnimeNodeToMediaItem(it.node) }
                        }
                    }
                    DiscoverCategory.TRENDING_NOW -> {
                        if (filter.format == "MANGA") {
                            val resp = ApiClient.malApi.getMangaRanking(MalAuthManager.CLIENT_ID, "bypopularity", limit, offset)
                            if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                                results = resp.body()!!.data.map { mapMalMangaNodeToMediaItem(it.node) }
                            }
                        } else {
                            val resp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "bypopularity", limit, offset)
                            if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                                results = resp.body()!!.data.map { mapMalAnimeNodeToMediaItem(it.node) }
                            }
                        }
                    }
                    DiscoverCategory.RECENTLY_DONE_MANGA -> {
                        val resp = ApiClient.malApi.getMangaRanking(MalAuthManager.CLIENT_ID, "manga", limit, offset)
                        if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                            results = resp.body()!!.data.map { mapMalMangaNodeToMediaItem(it.node) }
                        }
                    }
                    DiscoverCategory.NEWLY_ADDED_MANGA -> {
                        val resp = ApiClient.malApi.getMangaRanking(MalAuthManager.CLIENT_ID, "favorite", limit, offset)
                        if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                            results = resp.body()!!.data.map { mapMalMangaNodeToMediaItem(it.node) }
                        }
                    }
                    else -> {}
                }
            } catch (_: Exception) {}
        }

        // Offline fallback if network fails completely
        if (results.isEmpty() && page == 1) {
            results = if (filter.format == "MANGA") fallbackManga() else fallbackAnime()
        }

        if (results.isNotEmpty()) {
            CacheManager.putDiscover(cacheKey, results)
        }

        results
    }

    // --- Extended Details: Primary AniList, Fallback to MAL ---
    suspend fun getExtendedDetails(
        aniListId: Int?,
        malId: Int?,
        type: MediaType,
        forceRefresh: Boolean = false
    ): ExtendedMediaDetail? = withContext(Dispatchers.IO) {
        val resolvedAniListId = aniListId ?: (malId?.let { CacheManager.getAniListIdForMalId(it) })
        val resolvedMalId = malId ?: (resolvedAniListId?.let { CacheManager.getMalIdForAniListId(it) })
        val primaryCacheKey = CacheManager.detailKey(resolvedAniListId, resolvedMalId)

        if (!forceRefresh) {
            val cached = CacheManager.getDetail(primaryCacheKey)
                ?: (resolvedAniListId?.let { CacheManager.getDetail(CacheManager.detailKey(it, null)) })
                ?: (resolvedMalId?.let { CacheManager.getDetail(CacheManager.detailKey(null, it)) })
            if (cached != null && (cached.malScore != null || resolvedMalId == null)) {
                return@withContext cached
            }
        }

        coroutineScope {
            // Concurrent parallel fetching over HTTP/2
            val aniDeferred = async {
                AniListClient.getExtendedDetails(resolvedAniListId, resolvedMalId, type, forceRefresh)
            }
            val malDeferred = async {
                if (resolvedMalId != null) {
                    malAuthManager.getExtendedDetailFallback(resolvedMalId, type)
                } else null
            }

            val aniDetail = aniDeferred.await()
            var malExt = malDeferred.await()

            // If MAL ID wasn't known beforehand, but AniList returned it, fetch MAL fallback
            val effectiveMalId = aniDetail?.malId ?: resolvedMalId
            if (malExt == null && effectiveMalId != null) {
                malExt = malAuthManager.getExtendedDetailFallback(effectiveMalId, type)
            }

            val merged = if (aniDetail != null && malExt != null) {
                aniDetail.copy(
                    // Metrics: MAL is authoritative for Rating MAL
                    malScore = malExt.malScore,
                    malRank = malExt.malRank ?: aniDetail.rank,
                    malPopularity = malExt.malPopularity ?: aniDetail.popularity,
                    malMembers = malExt.malMembers ?: aniDetail.watchers,
                    // Visual / rich media: Prioritize AniList, fallback to MAL
                    studio = aniDetail.studio ?: malExt.studio,
                    studioId = aniDetail.studioId ?: malExt.studioId,
                    publisher = aniDetail.publisher ?: malExt.publisher,
                    airingStatus = aniDetail.airingStatus ?: malExt.airingStatus,
                    startDate = aniDetail.startDate ?: malExt.startDate,
                    endDate = aniDetail.endDate ?: malExt.endDate,
                    genres = if (aniDetail.genres.isNotEmpty()) aniDetail.genres else malExt.genres,
                    source = aniDetail.source ?: malExt.source
                )
            } else {
                aniDetail ?: malExt
            }

            if (merged != null) {
                val effectiveAni = merged.anilistId
                val effectiveMal = merged.malId
                if (effectiveAni != null && effectiveMal != null) {
                    CacheManager.putIdMapping(effectiveMal, effectiveAni)
                }
                CacheManager.putDetail(CacheManager.detailKey(effectiveAni, effectiveMal), merged)
                if (effectiveAni != null) {
                    CacheManager.putDetail(CacheManager.detailKey(effectiveAni, null), merged)
                }
                if (effectiveMal != null) {
                    CacheManager.putDetail(CacheManager.detailKey(null, effectiveMal), merged)
                }
            }

            merged
        }
    }

    // --- Cache Management Actions ---
    fun clearMetadataCache() {
        CacheManager.clearMetadataCache()
    }

    suspend fun clearImageCache(context: Context) {
        CacheManager.clearImageCache(context)
    }

    suspend fun clearAllCache(context: Context) {
        CacheManager.clearAllCache(context)
    }

    // --- In-Memory Demo Dataset for Unauthenticated Mode ---
    fun getDemoAnime(): List<UserMediaItem> = listOf(
        UserMediaItem(
            identity = MediaRef(anilistId = 154587, malId = 52991),
            metadata = MediaMetadata(
                title = "Sousou no Frieren",
                titleEnglish = "Frieren: Beyond Journey's End",
                imageUrl = "https://cdn.myanimelist.net/images/anime/1015/138075l.jpg",
                type = MediaType.ANIME,
                totalEpisodes = 28,
                status = "Finished Airing",
                genres = listOf("Adventure", "Drama", "Fantasy"),
                synopsis = "During their decade-long quest to defeat the Demon King, the members of the hero's party formed deep bonds...",
                year = 2023,
                season = "Fall",
                studio = "Madhouse"
            ),
            tracking = MalTracking(
                status = "watching",
                score = 10,
                progress = 24,
                comments = "Mahakarya sinematografi dan pacing emosional terbaik."
            )
        ),
        UserMediaItem(
            identity = MediaRef(anilistId = 16498, malId = 16498),
            metadata = MediaMetadata(
                title = "Shingeki no Kyojin",
                titleEnglish = "Attack on Titan",
                imageUrl = "https://cdn.myanimelist.net/images/anime/10/47347l.jpg",
                type = MediaType.ANIME,
                totalEpisodes = 25,
                status = "Finished Airing",
                genres = listOf("Action", "Drama", "Suspense"),
                synopsis = "Centuries ago, mankind was slaughtered to near extinction by monstrous humanoid creatures called Titans...",
                year = 2013,
                season = "Spring",
                studio = "Wit Studio"
            ),
            tracking = MalTracking(
                status = "completed",
                score = 9,
                progress = 25,
                comments = "Soundtrack Hiroyuki Sawano luar biasa."
            )
        ),
        UserMediaItem(
            identity = MediaRef(anilistId = 113415, malId = 40748),
            metadata = MediaMetadata(
                title = "Jujutsu Kaisen",
                titleEnglish = "Jujutsu Kaisen",
                imageUrl = "https://cdn.myanimelist.net/images/anime/1171/109222l.jpg",
                type = MediaType.ANIME,
                totalEpisodes = 24,
                status = "Finished Airing",
                genres = listOf("Action", "Fantasy"),
                synopsis = "Idly indulging in paranormal activities with the Occult Club, high schooler Yuuji Itadori spends his days...",
                year = 2020,
                season = "Fall",
                studio = "MAPPA"
            ),
            tracking = MalTracking(
                status = "watching",
                score = 8,
                progress = 18,
                comments = "Pertarungan MAPPA sangat mulus."
            )
        )
    )

    fun getDemoManga(): List<UserMediaItem> = listOf(
        UserMediaItem(
            identity = MediaRef(anilistId = 30013, malId = 13),
            metadata = MediaMetadata(
                title = "One Piece",
                titleEnglish = "One Piece",
                imageUrl = "https://cdn.myanimelist.net/images/manga/2/253146l.jpg",
                type = MediaType.MANGA,
                totalChapters = 0,
                status = "Publishing",
                genres = listOf("Action", "Adventure", "Fantasy"),
                synopsis = "Gol D. Roger, a man referred to as the 'King of the Pirates,' is poised for execution..."
            ),
            tracking = MalTracking(
                status = "reading",
                score = 10,
                progress = 1110,
                comments = "Arc Egghead penuh kejutan dunia lore."
            )
        ),
        UserMediaItem(
            identity = MediaRef(anilistId = 30002, malId = 2),
            metadata = MediaMetadata(
                title = "Berserk",
                titleEnglish = "Berserk",
                imageUrl = "https://cdn.myanimelist.net/images/manga/1/157897l.jpg",
                type = MediaType.MANGA,
                totalChapters = 0,
                status = "Publishing",
                genres = listOf("Action", "Adventure", "Drama", "Dark Fantasy"),
                synopsis = "Guts, a former mercenary now known as the 'Black Swordsman,' is out for revenge..."
            ),
            tracking = MalTracking(
                status = "reading",
                score = 10,
                progress = 375,
                comments = "Karya seni visual terbaik sepanjang masa."
            )
        ),
        UserMediaItem(
            identity = MediaRef(anilistId = 105398, malId = 121496),
            metadata = MediaMetadata(
                title = "Solo Leveling",
                titleEnglish = "Solo Leveling",
                imageUrl = "https://cdn.myanimelist.net/images/manga/3/222295l.jpg",
                type = MediaType.MANGA,
                totalChapters = 179,
                status = "Finished",
                genres = listOf("Action", "Adventure", "Fantasy"),
                synopsis = "Ten years ago, 'the Gate' appeared and connected the real world with the realm of magic and monsters..."
            ),
            tracking = MalTracking(
                status = "completed",
                score = 9,
                progress = 179,
                comments = "Sung Jin-woo sang Shadow Monarch!"
            )
        )
    )

    private fun fallbackAnime(): List<MediaItem> = listOf(
        MediaItem(52991, 154587, "Sousou no Frieren", "Frieren: Beyond Journey's End", "https://cdn.myanimelist.net/images/anime/1015/138075l.jpg", MediaType.ANIME, 9.35, "During their decade-long quest to defeat the Demon King...", 28, null, null, "Finished Airing", 2023, "Fall", listOf("Adventure", "Fantasy"), "TV", "Madhouse"),
        MediaItem(16498, 16498, "Shingeki no Kyojin", "Attack on Titan", "https://cdn.myanimelist.net/images/anime/10/47347l.jpg", MediaType.ANIME, 8.55, "Centuries ago, mankind was slaughtered...", 25, null, null, "Finished Airing", 2013, "Spring", listOf("Action", "Drama"), "TV", "Wit Studio"),
        MediaItem(5114, 5114, "Fullmetal Alchemist: Brotherhood", "Fullmetal Alchemist: Brotherhood", "https://cdn.myanimelist.net/images/anime/1223/96541l.jpg", MediaType.ANIME, 9.10, "After a horrific alchemy experiment goes wrong...", 64, null, null, "Finished Airing", 2009, "Spring", listOf("Action", "Adventure"), "TV", "Bones"),
        MediaItem(40748, 113415, "Jujutsu Kaisen", "Jujutsu Kaisen", "https://cdn.myanimelist.net/images/anime/1171/109222l.jpg", MediaType.ANIME, 8.61, "Idly indulging in paranormal activities with the Occult Club...", 24, null, null, "Finished Airing", 2020, "Fall", listOf("Action", "Fantasy"), "TV", "MAPPA"),
        MediaItem(38000, 101922, "Kimetsu no Yaiba", "Demon Slayer", "https://cdn.myanimelist.net/images/anime/1286/99889l.jpg", MediaType.ANIME, 8.48, "Ever since the death of his father...", 26, null, null, "Finished Airing", 2019, "Spring", listOf("Action", "Fantasy"), "TV", "ufotable")
    )

    private fun fallbackManga(): List<MediaItem> = listOf(
        MediaItem(2, 30002, "Berserk", "Berserk", "https://cdn.myanimelist.net/images/manga/1/157897l.jpg", MediaType.MANGA, 9.47, "Guts, a former mercenary now known as the 'Black Swordsman'...", null, null, null, "Publishing", null, null, listOf("Action", "Dark Fantasy"), "MANGA", null),
        MediaItem(13, 30013, "One Piece", "One Piece", "https://cdn.myanimelist.net/images/manga/2/253146l.jpg", MediaType.MANGA, 9.22, "Gol D. Roger was known as the 'Pirate King'...", null, null, null, "Publishing", null, null, listOf("Action", "Adventure"), "MANGA", null),
        MediaItem(656, 30656, "Vagabond", "Vagabond", "https://cdn.myanimelist.net/images/manga/1/259070l.jpg", MediaType.MANGA, 9.25, "Growing up in 16th century Sengoku era Japan...", null, 327, 37, "On Hiatus", null, null, listOf("Action", "Historical"), "MANGA", null),
        MediaItem(121496, 105398, "Solo Leveling", "Solo Leveling", "https://cdn.myanimelist.net/images/manga/3/222295l.jpg", MediaType.MANGA, 8.68, "Ten years ago, 'the Gate' appeared...", null, 179, null, "Finished", null, null, listOf("Action", "Fantasy"), "MANGA", null)
    )

    suspend fun getMalTrackingStatus(malId: Int, type: MediaType): MalTracking? {
        return malAuthManager.getMalUserTracking(malId, type)
    }

    suspend fun getStudioFilmography(
        studioId: Int?,
        search: String? = null,
        page: Int = 1,
        forceRefresh: Boolean = false,
        sort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC
    ): StudioFilmographyPage? {
        return AniListClient.getStudioFilmography(studioId, search, page, forceRefresh = forceRefresh, sort = sort)
    }

    suspend fun searchStudios(query: String, page: Int = 1, perPage: Int = 20): List<StudioBioInfo> {
        return AniListClient.searchStudios(query, page, perPage)
    }
}
