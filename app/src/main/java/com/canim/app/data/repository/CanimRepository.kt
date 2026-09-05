package com.canim.app.data.repository

import android.content.Context
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.*
import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.remote.AniListClient
import kotlinx.coroutines.Dispatchers
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

    // --- Search with AniList as Primary & Offline Fallback ---
    suspend fun searchAnime(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val cached = CacheManager.getSearch(trimmed, "ANIME")
        if (cached != null) return@withContext cached

        var result = AniListClient.searchMedia(trimmed, MediaType.ANIME)

        if (result.isEmpty()) {
            val localMatches = fallbackAnime().filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                (it.titleEnglish?.contains(trimmed, ignoreCase = true) == true)
            }
            if (localMatches.isNotEmpty()) {
                result = localMatches
            }
        }

        if (result.isNotEmpty()) {
            CacheManager.putSearch(trimmed, "ANIME", result)
        }
        result
    }

    suspend fun searchManga(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val cached = CacheManager.getSearch(trimmed, "MANGA")
        if (cached != null) return@withContext cached

        var result = AniListClient.searchMedia(trimmed, MediaType.MANGA)

        if (result.isEmpty()) {
            val localMatches = fallbackManga().filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                (it.titleEnglish?.contains(trimmed, ignoreCase = true) == true)
            }
            if (localMatches.isNotEmpty()) {
                result = localMatches
            }
        }

        if (result.isNotEmpty()) {
            CacheManager.putSearch(trimmed, "MANGA", result)
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

        var results = AniListClient.getDiscoverMedia(
            category = category,
            filter = filter,
            page = page,
            randomSort = randomSort,
            forceRefresh = forceRefresh
        )

        // Offline fallback if network fails
        if (results.isEmpty() && page == 1) {
            results = if (filter.format == "MANGA") fallbackManga() else fallbackAnime()
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
        // 1. Primary: AniList
        var detail = AniListClient.getExtendedDetails(aniListId, malId, type, forceRefresh)

        val effectiveMalId = detail?.malId ?: malId
        if (effectiveMalId != null) {
            val malExt = malAuthManager.getExtendedDetailFallback(effectiveMalId, type)
            if (malExt != null) {
                detail = if (detail != null) {
                    detail.copy(
                        // Metrics: Prioritize MAL, fallback to AniList so nothing is empty
                        malScore = malExt.malScore ?: detail.averageScore,
                        malRank = malExt.malRank ?: detail.rank,
                        malPopularity = malExt.malPopularity ?: detail.popularity,
                        malMembers = malExt.malMembers ?: detail.watchers,
                        // Visual / rich media: Prioritize AniList, fallback to MAL
                        studio = detail.studio ?: malExt.studio,
                        studioId = detail.studioId ?: malExt.studioId,
                        publisher = detail.publisher ?: malExt.publisher,
                        airingStatus = detail.airingStatus ?: malExt.airingStatus,
                        startDate = detail.startDate ?: malExt.startDate,
                        endDate = detail.endDate ?: malExt.endDate,
                        genres = if (detail.genres.isNotEmpty()) detail.genres else malExt.genres,
                        source = detail.source ?: malExt.source
                    )
                } else {
                    malExt
                }
            }
        }

        detail
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
        forceRefresh: Boolean = false
    ): StudioFilmographyPage? {
        return AniListClient.getStudioFilmography(studioId, search, page, forceRefresh = forceRefresh)
    }
}
