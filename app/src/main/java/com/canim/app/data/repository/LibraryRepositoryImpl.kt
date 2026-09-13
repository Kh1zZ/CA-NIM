package com.canim.app.data.repository

import android.content.Context
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibraryEntry
import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.local.PendingMutation
import com.canim.app.data.local.PendingMutationDao
import com.canim.app.data.model.*
import com.canim.app.data.remote.AniListClient
import com.canim.app.domain.repository.LibraryRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl(
    private val malAuthManager: MalAuthManager,
    private val libraryDao: LibraryDao? = null,
    private val pendingMutationDao: PendingMutationDao? = null,
    private val syncEngine: LibrarySyncEngine? = null,
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : LibraryRepository {

    @Inject
    constructor(
        malAuthManager: MalAuthManager,
        libraryDao: LibraryDao,
        pendingMutationDao: PendingMutationDao,
        syncEngine: LibrarySyncEngine,
        @ApplicationContext context: Context
    ) : this(
        malAuthManager = malAuthManager,
        libraryDao = libraryDao,
        pendingMutationDao = pendingMutationDao,
        syncEngine = syncEngine,
        context = context,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private val gson = Gson()

    override suspend fun retryFailedMutations(mediaType: String?): Int =
        syncEngine?.retryFailedPermanently(mediaType) ?: 0

    override fun getLastSyncedTime(): Long = malAuthManager.getLastSynced()

    override fun getCachedTracking(type: String): List<UserMediaItem>? {
        val memory = CacheManager.getTracking(type)
        if (memory != null) return memory
        val appContext = context
        if (appContext != null) {
            val disk = CacheManager.loadTrackingFromDisk(appContext, type)
            if (disk != null) {
                CacheManager.putTracking(type, disk)
                return disk
            }
        }
        return null
    }

    override suspend fun getUserAnimeList(forceRefresh: Boolean): MalFetchResult<List<UserMediaItem>> = withContext(Dispatchers.IO) {
        val dao = libraryDao
        if (dao != null && !forceRefresh) {
            val localEntries = dao.getAllEntries("ANIME")
            if (localEntries.isNotEmpty()) {
                val items = localEntries.map { it.toUserMediaItem { json -> parseJsonList(json) } }
                CacheManager.putTracking("ANIME", items)
                return@withContext MalFetchResult.Success(items, items.size)
            }
        }

        if (dao != null && forceRefresh) {
            return@withContext safeReconcileAnime()
        }

        val result = malAuthManager.fetchUserAnimeList(forceRefresh = forceRefresh)
        when (result) {
            is MalFetchResult.Failure -> result
            is MalFetchResult.Success -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.ANIME)
                CacheManager.putTracking("ANIME", enriched)
                dao?.let { upsertLibraryEntries(it, enriched, "ANIME") }
                context?.let {
                    CacheManager.saveTrackingToDisk(it, "ANIME", enriched)
                }
                MalFetchResult.Success(enriched, result.totalItems)
            }
            is MalFetchResult.Partial -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.ANIME)
                CacheManager.putTracking("ANIME", enriched)
                dao?.let { upsertLibraryEntries(it, enriched, "ANIME") }
                context?.let {
                    CacheManager.saveTrackingToDisk(it, "ANIME", enriched)
                }
                MalFetchResult.Partial(enriched, result.fetchedItems, result.error)
            }
        }
    }

    private suspend fun safeReconcileAnime(): MalFetchResult<List<UserMediaItem>> {
        val dao = libraryDao ?: return MalFetchResult.Failure(Exception("LibraryDao not wired"))
        val mutDao = pendingMutationDao

        try {
            mutDao?.resetFailedPermanentlyToPending("ANIME")
            syncEngine?.drainQueue()
        } catch (_: Exception) {}

        val result = malAuthManager.fetchUserAnimeList(forceRefresh = true)
        if (result is MalFetchResult.Failure) return result

        val serverItems = when (result) {
            is MalFetchResult.Success -> result.data
            is MalFetchResult.Partial -> result.data
            else -> emptyList()
        }

        val activePendingMalIds = mutDao?.getActiveMalIds("ANIME") ?: emptySet()
        val enrichedServer = enrichWithAniListMetadata(serverItems, MediaType.ANIME)

        enrichedServer.forEach { serverItem ->
            val malId = serverItem.malId ?: return@forEach
            if (malId !in activePendingMalIds) {
                dao.upsertEntry(serverItem.toLibraryEntry("ANIME"))
            }
        }

        val serverMalIds = enrichedServer.mapNotNull { it.malId }.toSet()
        val localMalIds = dao.getAllMalIds("ANIME")
        (localMalIds - serverMalIds).forEach { orphanId ->
            if (orphanId !in activePendingMalIds) {
                dao.deleteEntry(orphanId, "ANIME")
            }
        }

        val finalItems = dao.getAllEntries("ANIME").map { it.toUserMediaItem { json -> parseJsonList(json) } }
        CacheManager.putTracking("ANIME", finalItems)
        context?.let {
            CacheManager.saveTrackingToDisk(it, "ANIME", finalItems)
        }
        mutDao?.clearSucceeded()

        return when (result) {
            is MalFetchResult.Success -> MalFetchResult.Success(finalItems, finalItems.size)
            is MalFetchResult.Partial -> MalFetchResult.Partial(finalItems, finalItems.size, result.error)
            else -> MalFetchResult.Success(finalItems, finalItems.size)
        }
    }

    override suspend fun getUserMangaList(forceRefresh: Boolean): MalFetchResult<List<UserMediaItem>> = withContext(Dispatchers.IO) {
        val dao = libraryDao
        if (dao != null && !forceRefresh) {
            val localEntries = dao.getAllEntries("MANGA")
            if (localEntries.isNotEmpty()) {
                val items = localEntries.map { it.toUserMediaItem { json -> parseJsonList(json) } }
                CacheManager.putTracking("MANGA", items)
                return@withContext MalFetchResult.Success(items, items.size)
            }
        }

        if (dao != null && forceRefresh) {
            return@withContext safeReconcileManga()
        }

        val result = malAuthManager.fetchUserMangaList(forceRefresh = forceRefresh)
        when (result) {
            is MalFetchResult.Failure -> result
            is MalFetchResult.Success -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.MANGA)
                CacheManager.putTracking("MANGA", enriched)
                dao?.let { upsertLibraryEntries(it, enriched, "MANGA") }
                context?.let {
                    CacheManager.saveTrackingToDisk(it, "MANGA", enriched)
                }
                MalFetchResult.Success(enriched, result.totalItems)
            }
            is MalFetchResult.Partial -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.MANGA)
                CacheManager.putTracking("MANGA", enriched)
                dao?.let { upsertLibraryEntries(it, enriched, "MANGA") }
                context?.let {
                    CacheManager.saveTrackingToDisk(it, "MANGA", enriched)
                }
                MalFetchResult.Partial(enriched, result.fetchedItems, result.error)
            }
        }
    }

    private suspend fun safeReconcileManga(): MalFetchResult<List<UserMediaItem>> {
        val dao = libraryDao ?: return MalFetchResult.Failure(Exception("LibraryDao not wired"))
        val mutDao = pendingMutationDao

        try {
            mutDao?.resetFailedPermanentlyToPending("MANGA")
            syncEngine?.drainQueue()
        } catch (_: Exception) {}

        val result = malAuthManager.fetchUserMangaList(forceRefresh = true)
        if (result is MalFetchResult.Failure) return result

        val serverItems = when (result) {
            is MalFetchResult.Success -> result.data
            is MalFetchResult.Partial -> result.data
            else -> emptyList()
        }

        val activePendingMalIds = mutDao?.getActiveMalIds("MANGA") ?: emptySet()
        val enrichedServer = enrichWithAniListMetadata(serverItems, MediaType.MANGA)

        enrichedServer.forEach { serverItem ->
            val malId = serverItem.malId ?: return@forEach
            if (malId !in activePendingMalIds) {
                dao.upsertEntry(serverItem.toLibraryEntry("MANGA"))
            }
        }
        val serverMalIds = enrichedServer.mapNotNull { it.malId }.toSet()
        val localMalIds = dao.getAllMalIds("MANGA")
        (localMalIds - serverMalIds).forEach { orphanId ->
            if (orphanId !in activePendingMalIds) {
                dao.deleteEntry(orphanId, "MANGA")
            }
        }

        val finalItems = dao.getAllEntries("MANGA").map { it.toUserMediaItem { json -> parseJsonList(json) } }
        CacheManager.putTracking("MANGA", finalItems)
        context?.let {
            CacheManager.saveTrackingToDisk(it, "MANGA", finalItems)
        }
        mutDao?.clearSucceeded()

        return when (result) {
            is MalFetchResult.Success -> MalFetchResult.Success(finalItems, finalItems.size)
            is MalFetchResult.Partial -> MalFetchResult.Partial(finalItems, finalItems.size, result.error)
            else -> MalFetchResult.Success(finalItems, finalItems.size)
        }
    }

    private suspend fun enrichWithAniListMetadata(
        items: List<UserMediaItem>,
        type: MediaType
    ): List<UserMediaItem> = withContext(Dispatchers.IO) {
        val malIds = items.mapNotNull { it.malId }.distinct()
        if (malIds.isEmpty()) return@withContext items

        val dao = libraryDao

        // Populate from local DB / Cache without bursting AniList network API during sync
        items.map { item ->
            val mId = item.malId ?: return@map item
            val cachedMeta = CacheManager.getMetadata(mId, type)
            if (cachedMeta != null) {
                val updatedMetadata = item.metadata.copy(
                    titleEnglish = cachedMeta.titleEnglish ?: item.metadata.titleEnglish,
                    imageUrl = item.metadata.imageUrl.ifBlank { cachedMeta.imageUrl },
                    totalEpisodes = cachedMeta.episodes ?: item.metadata.totalEpisodes,
                    totalChapters = cachedMeta.chapters ?: item.metadata.totalChapters,
                    totalVolumes = cachedMeta.volumes ?: item.metadata.totalVolumes,
                    genres = if (cachedMeta.genres.isNotEmpty()) cachedMeta.genres else item.metadata.genres,
                    studio = cachedMeta.studio ?: item.metadata.studio,
                    format = cachedMeta.format ?: item.metadata.format,
                    year = cachedMeta.year ?: item.metadata.year,
                    season = cachedMeta.season ?: item.metadata.season
                )
                item.copy(
                    identity = MediaRef(anilistId = cachedMeta.anilistId ?: CacheManager.getAniListIdForMalId(mId, type), malId = mId),
                    metadata = updatedMetadata
                )
            } else {
                    val localEntry = dao?.getEntry(mId, type.name)
                    if (localEntry?.anilistId != null) {
                        val localGenres = if (item.metadata.genres.isEmpty()) parseJsonList(localEntry.genresJson) else item.metadata.genres
                        val updatedMetadata = item.metadata.copy(
                            titleEnglish = localEntry.titleEnglish ?: item.metadata.titleEnglish,
                            imageUrl = item.metadata.imageUrl.ifBlank { localEntry.imageUrl },
                            totalEpisodes = (if (type == MediaType.ANIME && localEntry.totalEpisodes > 0) localEntry.totalEpisodes else null) ?: item.metadata.totalEpisodes,
                            totalChapters = (if (type == MediaType.MANGA && localEntry.totalChapters > 0) localEntry.totalChapters else null) ?: item.metadata.totalChapters,
                            totalVolumes = (if (type == MediaType.MANGA && localEntry.totalVolumes > 0) localEntry.totalVolumes else null) ?: item.metadata.totalVolumes,
                            genres = localGenres,
                            studio = localEntry.studio ?: item.metadata.studio,
                            format = localEntry.format ?: item.metadata.format,
                            year = (if (localEntry.year > 0) localEntry.year else null) ?: item.metadata.year,
                            season = localEntry.season ?: item.metadata.season
                        )
                        item.copy(
                            identity = MediaRef(anilistId = localEntry.anilistId, malId = mId),
                            metadata = updatedMetadata
                        )
                    } else {
                        item
                    }
                }
            }
        }

    override suspend fun updateAnimeTracking(malId: Int, tracking: MalTracking): Result<Unit> = withContext(Dispatchers.IO) {
        val dao = libraryDao
        val mutDao = pendingMutationDao
        if (dao != null && mutDao != null) {
            return@withContext localFirstUpdate(malId, "ANIME", tracking, dao, mutDao)
        }
        malAuthManager.updateAnimeTracking(malId, tracking)
    }

    override suspend fun updateMangaTracking(malId: Int, tracking: MalTracking): Result<Unit> = withContext(Dispatchers.IO) {
        val dao = libraryDao
        val mutDao = pendingMutationDao
        if (dao != null && mutDao != null) {
            return@withContext localFirstUpdate(malId, "MANGA", tracking, dao, mutDao)
        }
        malAuthManager.updateMangaTracking(malId, tracking)
    }

    override suspend fun deleteAnimeTracking(malId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val dao = libraryDao
        val mutDao = pendingMutationDao
        if (dao != null && mutDao != null) {
            return@withContext localFirstDelete(malId, "ANIME", dao, mutDao)
        }
        malAuthManager.deleteAnimeTracking(malId)
    }

    override suspend fun deleteMangaTracking(malId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val dao = libraryDao
        val mutDao = pendingMutationDao
        if (dao != null && mutDao != null) {
            return@withContext localFirstDelete(malId, "MANGA", dao, mutDao)
        }
        malAuthManager.deleteMangaTracking(malId)
    }

    override suspend fun saveUserMediaItem(item: UserMediaItem): Result<Unit> = withContext(Dispatchers.IO) {
        val dao = libraryDao
        val mutDao = pendingMutationDao
        val mediaType = if (item.isAnime) "ANIME" else "MANGA"
        val malId = item.malId ?: 0
        if (dao != null && mutDao != null && malId > 0) {
            return@withContext try {
                val now = System.currentTimeMillis()
                val existing = dao.getEntry(malId, mediaType)
                val entryToSave = if (existing != null) {
                    existing.copy(
                        status = item.tracking.status,
                        score = item.tracking.score,
                        progress = item.tracking.progress,
                        progressVolumes = item.tracking.progressVolumes,
                        isRepeating = if (item.tracking.isRepeating) 1 else 0,
                        numTimesRewatched = item.tracking.numTimesRewatched,
                        rewatchValue = item.tracking.rewatchValue,
                        priority = item.tracking.priority,
                        tagsJson = gson.toJson(item.tracking.tags ?: emptyList<String>()),
                        comments = item.tracking.comments,
                        startDate = item.tracking.startDate,
                        finishDate = item.tracking.finishDate,
                        title = item.metadata.title.takeIf { it.isNotBlank() } ?: existing.title,
                        titleEnglish = item.metadata.titleEnglish ?: existing.titleEnglish,
                        imageUrl = item.metadata.imageUrl.takeIf { it.isNotBlank() } ?: existing.imageUrl,
                        totalEpisodes = item.metadata.totalEpisodes?.takeIf { it > 0 } ?: existing.totalEpisodes,
                        totalChapters = item.metadata.totalChapters?.takeIf { it > 0 } ?: existing.totalChapters,
                        totalVolumes = item.metadata.totalVolumes?.takeIf { it > 0 } ?: existing.totalVolumes,
                        airingStatus = item.metadata.status ?: existing.airingStatus,
                        year = item.metadata.year?.takeIf { it > 0 } ?: existing.year,
                        season = item.metadata.season ?: existing.season,
                        genresJson = if (item.metadata.genres.isNotEmpty()) gson.toJson(item.metadata.genres) else existing.genresJson,
                        format = item.metadata.format ?: existing.format,
                        studio = item.metadata.studio ?: existing.studio,
                        anilistId = item.anilistId ?: existing.anilistId,
                        localUpdatedAt = now
                    )
                } else {
                    item.toLibraryEntry(mediaType).copy(localUpdatedAt = now)
                }

                val mutation = PendingMutation(
                    malId = malId,
                    mediaType = mediaType,
                    mutationType = PendingMutation.TYPE_UPDATE,
                    payloadJson = gson.toJson(item.tracking),
                    localUpdatedAt = now,
                    createdAt = now
                )

                dao.upsertWithMutation(entryToSave, mutation, mutDao)
                scope.launch { syncEngine?.trySendImmediate(malId, mediaType) }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        if (item.isAnime) {
            malAuthManager.updateAnimeTracking(malId, item.tracking)
        } else {
            malAuthManager.updateMangaTracking(malId, item.tracking)
        }
    }

    private suspend fun localFirstUpdate(
        malId: Int,
        mediaType: String,
        tracking: MalTracking,
        dao: LibraryDao,
        mutDao: PendingMutationDao
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val existing = dao.getEntry(malId, mediaType)
            val baseEntry = existing ?: run {
                val cachedDetail = CacheManager.getDetail(CacheManager.detailKey(null, malId))
                LibraryEntry(
                    malId = malId,
                    mediaType = mediaType,
                    title = cachedDetail?.title ?: "",
                    titleEnglish = cachedDetail?.titleEnglish,
                    imageUrl = cachedDetail?.coverImage ?: "",
                    totalEpisodes = 0,
                    airingStatus = cachedDetail?.airingStatus,
                    genresJson = gson.toJson(cachedDetail?.genres ?: emptyList<String>()),
                    studio = cachedDetail?.studio,
                    anilistId = cachedDetail?.anilistId
                )
            }
            val updated = baseEntry.copy(
                status = tracking.status,
                score = tracking.score,
                progress = tracking.progress,
                progressVolumes = tracking.progressVolumes,
                isRepeating = if (tracking.isRepeating) 1 else 0,
                numTimesRewatched = tracking.numTimesRewatched,
                rewatchValue = tracking.rewatchValue,
                priority = tracking.priority,
                tagsJson = gson.toJson(tracking.tags ?: emptyList<String>()),
                comments = tracking.comments,
                startDate = tracking.startDate,
                finishDate = tracking.finishDate,
                localUpdatedAt = now
            )
            val mutation = PendingMutation(
                malId = malId,
                mediaType = mediaType,
                mutationType = PendingMutation.TYPE_UPDATE,
                payloadJson = gson.toJson(tracking),
                localUpdatedAt = now,
                createdAt = now
            )

            dao.upsertWithMutation(updated, mutation, mutDao)
            scope.launch { syncEngine?.trySendImmediate(malId, mediaType) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun localFirstDelete(
        malId: Int,
        mediaType: String,
        dao: LibraryDao,
        mutDao: PendingMutationDao
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val mutation = PendingMutation(
                malId = malId,
                mediaType = mediaType,
                mutationType = PendingMutation.TYPE_DELETE,
                payloadJson = "",
                localUpdatedAt = now,
                createdAt = now
            )

            dao.deleteWithMutation(malId, mediaType, mutation, mutDao)
            scope.launch { syncEngine?.trySendImmediate(malId, mediaType) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getDemoAnime(): List<UserMediaItem> = listOf(
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

    override fun getDemoManga(): List<UserMediaItem> = listOf(
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

    private fun upsertLibraryEntries(dao: LibraryDao, items: List<UserMediaItem>, mediaType: String) {
        items.forEach { item ->
            try {
                dao.upsertEntry(item.toLibraryEntry(mediaType))
            } catch (_: Exception) { }
        }
    }

    private fun UserMediaItem.toLibraryEntry(mediaType: String): LibraryEntry {
        val t = tracking
        val m = metadata
        return LibraryEntry(
            malId = malId ?: 0,
            mediaType = mediaType,
            status = t.status,
            score = t.score,
            progress = t.progress,
            progressVolumes = t.progressVolumes,
            isRepeating = if (t.isRepeating) 1 else 0,
            numTimesRewatched = t.numTimesRewatched,
            rewatchValue = t.rewatchValue,
            priority = t.priority,
            tagsJson = gson.toJson(t.tags ?: emptyList<String>()),
            comments = t.comments,
            startDate = t.startDate,
            finishDate = t.finishDate,
            title = m.title,
            titleEnglish = m.titleEnglish,
            imageUrl = m.imageUrl,
            totalEpisodes = m.totalEpisodes ?: 0,
            totalChapters = m.totalChapters ?: 0,
            totalVolumes = m.totalVolumes ?: 0,
            airingStatus = m.status,
            year = m.year ?: 0,
            season = m.season,
            genresJson = gson.toJson(m.genres),
            format = m.format,
            studio = m.studio,
            anilistId = anilistId,
            localUpdatedAt = t.updatedAt,
            syncedAt = 0L
        )
    }

    private fun parseJsonList(json: String): List<String> {
        return try {
            if (json.isBlank() || json == "[]") emptyList()
            else gson.fromJson(json, Array<String>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
