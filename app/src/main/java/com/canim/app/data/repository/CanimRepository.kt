package com.canim.app.data.repository

import android.content.Context
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.AnimeDao
import com.canim.app.data.local.AnimeEntity
import com.canim.app.data.local.MangaDao
import com.canim.app.data.local.MangaEntity
import com.canim.app.data.model.*
import com.canim.app.data.remote.AniListClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CanimRepository(
    private val animeDao: AnimeDao,
    private val mangaDao: MangaDao,
    val malAuthManager: MalAuthManager? = null
) {
    val allAnime: Flow<List<AnimeEntity>> = animeDao.getAllAnime()
    val allManga: Flow<List<MangaEntity>> = mangaDao.getAllManga()

    fun buildMalAuthorizeUrl(): String? = malAuthManager?.buildAuthorizeUrl()

    suspend fun handleMalOAuthCallback(code: String, state: String?): Result<MalUser> =
        malAuthManager?.handleOAuthCallback(code, state)
            ?: Result.failure(IllegalStateException("MalAuthManager tidak diinisialisasi"))

    fun getMalUser(): MalUser = malAuthManager?.getCurrentUser() ?: MalUser()

    fun logoutMal() {
        malAuthManager?.logout()
    }

    suspend fun syncWithMal(): MalSyncResult =
        malAuthManager?.syncWithMal() ?: MalSyncResult(isSuccess = false, errorMessage = "MalAuthManager null")

    suspend fun upsertAnime(anime: AnimeEntity) = withContext(Dispatchers.IO) {
        animeDao.insert(anime)
        CacheManager.invalidateMedia(anime.malId, anime.anilistId)
    }

    suspend fun upsertManga(manga: MangaEntity) = withContext(Dispatchers.IO) {
        mangaDao.insert(manga)
        CacheManager.invalidateMedia(manga.malId, manga.anilistId)
    }

    suspend fun deleteAnime(id: String) = withContext(Dispatchers.IO) {
        animeDao.deleteById(id)
    }

    suspend fun deleteManga(id: String) = withContext(Dispatchers.IO) {
        mangaDao.deleteById(id)
    }

    suspend fun incrementAnime(id: String, amount: Int = 1) = withContext(Dispatchers.IO) {
        animeDao.incrementProgress(id, amount, System.currentTimeMillis())
    }

    suspend fun incrementManga(id: String, amount: Int = 1) = withContext(Dispatchers.IO) {
        mangaDao.incrementProgress(id, amount, System.currentTimeMillis())
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        animeDao.clearAll()
        mangaDao.clearAll()
    }

    // --- Search with AniList as Primary, Cache-First & Offline Fallback ---
    suspend fun searchAnime(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 1. Check cache first
        val cached = CacheManager.getSearch(trimmed, "ANIME")
        if (cached != null) return@withContext cached

        // 2. Query AniList
        var result = AniListClient.searchMedia(trimmed, MediaType.ANIME)

        // 3. Fallback to offline catalog if device is offline or query failed
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

        // 1. Check cache first
        val cached = CacheManager.getSearch(trimmed, "MANGA")
        if (cached != null) return@withContext cached

        // 2. Query AniList
        var result = AniListClient.searchMedia(trimmed, MediaType.MANGA)

        // 3. Fallback to offline catalog if offline
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

    // --- Discover with On-Demand Loading & Server-Side Filtering ---
    suspend fun getDiscoverMedia(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        forceRefresh: Boolean = false,
        randomSort: String? = null
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val cacheKey = "discover_${category.key}_${filter.genre}_${filter.format}_${filter.year}_${filter.season}_${filter.minScore}_${randomSort}_p$page"
        if (!forceRefresh) {
            val cached = CacheManager.getDiscover(cacheKey)
            if (cached != null) return@withContext cached
        }

        var results = AniListClient.getDiscoverMedia(category, filter, page = page, randomSort = randomSort)

        // Offline fallback if network fails
        if (results.isEmpty() && page == 1) {
            results = if (filter.format == "MANGA") fallbackManga() else fallbackAnime()
        }

        results
    }

    // --- Extended Details: Crew, Cast, Studio, Duration ---
    suspend fun getExtendedDetails(
        aniListId: Int?,
        malId: Int?,
        type: MediaType
    ): ExtendedMediaDetail? = withContext(Dispatchers.IO) {
        // 1. Primary: AniList
        var detail = AniListClient.getExtendedDetails(aniListId, malId, type)

        // 2. If metadata is missing or incomplete, enrich from MyAnimeList Fallback Provider (Request 11)
        if ((detail == null || detail.studio.isNullOrBlank() || detail.startDate.isNullOrBlank() || detail.genres.isEmpty()) && malId != null && malAuthManager != null) {
            val malExt = malAuthManager.getExtendedDetailFallback(malId, type)
            if (malExt != null) {
                detail = if (detail != null) {
                    detail.copy(
                        studio = detail.studio ?: malExt.studio,
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

    // --- Cache Management Actions (Safe: Preserves User Data) ---
    fun clearMetadataCache() {
        CacheManager.clearMetadataCache()
    }

    suspend fun clearImageCache(context: Context) {
        CacheManager.clearImageCache(context)
    }

    suspend fun clearAllCache(context: Context) {
        CacheManager.clearAllCache(context)
    }

    suspend fun loadDemoData() = withContext(Dispatchers.IO) {
        val sampleAnime = listOf(
            AnimeEntity(
                id = "anime_52991",
                malId = 52991,
                anilistId = 154587,
                title = "Sousou no Frieren",
                titleEnglish = "Frieren: Beyond Journey's End",
                imageUrl = "https://cdn.myanimelist.net/images/anime/1015/138075l.jpg",
                status = "watching",
                score = 10,
                progress = 24,
                totalEpisodes = 28,
                airingStatus = "Finished Airing",
                genres = "Adventure, Drama, Fantasy",
                synopsis = "During their decade-long quest to defeat the Demon King, the members of the hero's party formed deep bonds...",
                year = 2023,
                season = "Fall",
                notes = "Mahakarya sinematografi dan pacing emosional terbaik.",
                studio = "Madhouse"
            ),
            AnimeEntity(
                id = "anime_16498",
                malId = 16498,
                anilistId = 16498,
                title = "Shingeki no Kyojin",
                titleEnglish = "Attack on Titan",
                imageUrl = "https://cdn.myanimelist.net/images/anime/10/47347l.jpg",
                status = "completed",
                score = 9,
                progress = 25,
                totalEpisodes = 25,
                airingStatus = "Finished Airing",
                genres = "Action, Drama, Suspense",
                synopsis = "Centuries ago, mankind was slaughtered to near extinction by monstrous humanoid creatures called Titans...",
                year = 2013,
                season = "Spring",
                notes = "Soundtrack Hiroyuki Sawano luar biasa.",
                studio = "Wit Studio"
            ),
            AnimeEntity(
                id = "anime_40748",
                malId = 40748,
                anilistId = 113415,
                title = "Jujutsu Kaisen",
                titleEnglish = "Jujutsu Kaisen",
                imageUrl = "https://cdn.myanimelist.net/images/anime/1171/109222l.jpg",
                status = "watching",
                score = 8,
                progress = 18,
                totalEpisodes = 24,
                airingStatus = "Finished Airing",
                genres = "Action, Fantasy",
                synopsis = "Idly indulging in paranormal activities with the Occult Club, high schooler Yuuji Itadori spends his days...",
                year = 2020,
                season = "Fall",
                notes = "Pertarungan MAPPA sangat mulus.",
                studio = "MAPPA"
            )
        )

        val sampleManga = listOf(
            MangaEntity(
                id = "manga_13",
                malId = 13,
                anilistId = 30013,
                title = "One Piece",
                titleEnglish = "One Piece",
                imageUrl = "https://cdn.myanimelist.net/images/manga/2/253146l.jpg",
                status = "reading",
                score = 10,
                progressChapters = 1110,
                totalChapters = 0,
                publishingStatus = "Publishing",
                genres = "Action, Adventure, Fantasy",
                synopsis = "Gol D. Roger, a man referred to as the 'King of the Pirates,' is poised for execution...",
                author = "Eiichiro Oda",
                notes = "Arc Egghead penuh kejutan dunia lore."
            ),
            MangaEntity(
                id = "manga_2",
                malId = 2,
                anilistId = 30002,
                title = "Berserk",
                titleEnglish = "Berserk",
                imageUrl = "https://cdn.myanimelist.net/images/manga/1/157897l.jpg",
                status = "reading",
                score = 10,
                progressChapters = 375,
                totalChapters = 0,
                publishingStatus = "Publishing",
                genres = "Action, Adventure, Drama, Dark Fantasy",
                synopsis = "Guts, a former mercenary now known as the 'Black Swordsman,' is out for revenge...",
                author = "Kentaro Miura",
                notes = "Karya seni visual terbaik sepanjang masa."
            ),
            MangaEntity(
                id = "manga_121496",
                malId = 121496,
                anilistId = 105398,
                title = "Solo Leveling",
                titleEnglish = "Solo Leveling",
                imageUrl = "https://cdn.myanimelist.net/images/manga/3/222295l.jpg",
                status = "completed",
                score = 9,
                progressChapters = 179,
                totalChapters = 179,
                publishingStatus = "Finished",
                genres = "Action, Adventure, Fantasy",
                synopsis = "Ten years ago, 'the Gate' appeared and connected the real world with the realm of magic and monsters...",
                author = "Chugong & DUBU",
                notes = "Sung Jin-woo sang Shadow Monarch!"
            )
        )

        animeDao.insertAll(sampleAnime)
        mangaDao.insertAll(sampleManga)
    }

    private fun fallbackAnime(): List<MediaItem> = listOf(
        MediaItem(52991, 154587, "Sousou no Frieren", "Frieren: Beyond Journey's End", "https://cdn.myanimelist.net/images/anime/1015/138075l.jpg", MediaType.ANIME, 9.35, "During their decade-long quest to defeat the Demon King...", 28, null, null, "Finished Airing", 2023, "Fall", listOf("Adventure", "Fantasy"), "TV", "Madhouse"),
        MediaItem(16498, 16498, "Shingeki no Kyojin", "Attack on Titan", "https://cdn.myanimelist.net/images/anime/10/47347l.jpg", MediaType.ANIME, 8.55, "Centuries ago, mankind was slaughtered...", 25, null, null, "Finished Airing", 2013, "Spring", listOf("Action", "Drama"), "TV", "Wit Studio"),
        MediaItem(5114, 5114, "Fullmetal Alchemist: Brotherhood", "Fullmetal Alchemist: Brotherhood", "https://cdn.myanimelist.net/images/anime/1223/96541l.jpg", MediaType.ANIME, 9.10, "After a horrific alchemy experiment goes wrong...", 64, null, null, "Finished Airing", 2009, "Spring", listOf("Action", "Adventure"), "TV", "Bones"),
        MediaItem(40748, 113415, "Jujutsu Kaisen", "Jujutsu Kaisen", "https://cdn.myanimelist.net/images/anime/1171/109222l.jpg", MediaType.ANIME, 8.61, "Idly indulging in paranormal activities with the Occult Club...", 24, null, null, "Finished Airing", 2020, "Fall", listOf("Action", "Fantasy"), "TV", "MAPPA"),
        MediaItem(38000, 101922, "Kimetsu no Yaiba", "Demon Slayer", "https://cdn.myanimelist.net/images/anime/1286/99889l.jpg", MediaType.ANIME, 8.48, "Ever since the death of his father, the burden of supporting...", 26, null, null, "Finished Airing", 2019, "Spring", listOf("Action", "Fantasy"), "TV", "ufotable")
    )

    private fun fallbackManga(): List<MediaItem> = listOf(
        MediaItem(2, 30002, "Berserk", "Berserk", "https://cdn.myanimelist.net/images/manga/1/157897l.jpg", MediaType.MANGA, 9.47, "Guts, a former mercenary now known as the 'Black Swordsman'...", null, null, null, "Publishing", null, null, listOf("Action", "Dark Fantasy"), "MANGA", null),
        MediaItem(13, 30013, "One Piece", "One Piece", "https://cdn.myanimelist.net/images/manga/2/253146l.jpg", MediaType.MANGA, 9.22, "Gol D. Roger was known as the 'Pirate King'...", null, null, null, "Publishing", null, null, listOf("Action", "Adventure"), "MANGA", null),
        MediaItem(656, 30656, "Vagabond", "Vagabond", "https://cdn.myanimelist.net/images/manga/1/259070l.jpg", MediaType.MANGA, 9.25, "Growing up in 16th century Sengoku era Japan...", null, 327, 37, "On Hiatus", null, null, listOf("Action", "Historical"), "MANGA", null),
        MediaItem(121496, 105398, "Solo Leveling", "Solo Leveling", "https://cdn.myanimelist.net/images/manga/3/222295l.jpg", MediaType.MANGA, 8.68, "Ten years ago, 'the Gate' appeared...", null, 179, null, "Finished", null, null, listOf("Action", "Fantasy"), "MANGA", null)
    )
}
