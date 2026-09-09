package com.canim.app.data.repository

import android.content.Context
import android.util.Log
import com.canim.app.data.cache.CacheManager
import com.canim.app.util.LogRedactor
import kotlinx.coroutines.CancellationException
import com.canim.app.data.model.*
import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibraryEntry
import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.local.PendingMutation
import com.canim.app.data.local.PendingMutationDao
import com.canim.app.data.remote.ApiClient
import com.canim.app.data.remote.AniListClient
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.canim.app.domain.repository.CanimRepositoryContract
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class CacheRefreshType {
    SEARCH,
    DISCOVER,
    DETAIL
}

data class CacheRefreshEvent(
    val key: String,
    val type: CacheRefreshType
)

/**
 * Single source of coordination for CA'NIM.
 * Principle:
 * - MAL is authoritative for user tracking data.
 * - AniList is the primary provider for rich metadata.
 * - CA'NIM acts as a client/UI layer.
 */
@Singleton
class CanimRepository(
    val malAuthManager: MalAuthManager,
    private val swrScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    // Phase 4 Local-First: nullable so existing tests remain unaffected
    private val libraryDao: LibraryDao? = null,
    private val pendingMutationDao: PendingMutationDao? = null,
    private val syncEngine: LibrarySyncEngine? = null
) : CanimRepositoryContract {

    @Inject
    constructor(
        malAuthManager: MalAuthManager,
        libraryDao: LibraryDao,
        pendingMutationDao: PendingMutationDao,
        syncEngine: LibrarySyncEngine
    ) : this(
        malAuthManager = malAuthManager,
        swrScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        libraryDao = libraryDao,
        pendingMutationDao = pendingMutationDao,
        syncEngine = syncEngine
    )
    private val gson = Gson()
    /**
     * SharedFlow for SWR cache refresh events. Buffer size 64 with DROP_OLDEST policy.
     * Emitted after background SWR refresh writes fresh data to CacheManager.
     */
    private val _cacheRefreshEvents = kotlinx.coroutines.flow.MutableSharedFlow<CacheRefreshEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val cacheRefreshEvents: kotlinx.coroutines.flow.SharedFlow<CacheRefreshEvent> = _cacheRefreshEvents.asSharedFlow()

    /**
     * Active in-flight SWR background refresh jobs keyed by canonical cache key.
     * Prevents overlapping refreshes on the same key by cancelling previous in-flight jobs.
     */
    internal val swrJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    internal fun launchSwrJob(key: String, block: suspend CoroutineScope.() -> Unit): Job {
        swrJobs[key]?.cancel()
        val job = swrScope.launch {
            try {
                block()
            } finally {
                swrJobs.remove(key, coroutineContext[Job])
            }
        }
        swrJobs[key] = job
        return job
    }

    internal suspend fun emitCacheRefreshEvent(event: CacheRefreshEvent) {
        _cacheRefreshEvents.emit(event)
    }

    fun getActiveSwrJob(key: String): Job? = swrJobs[key]

    suspend fun <T> deduplicateInFlight(key: String, block: suspend () -> T): T =
        AniListClient.deduplicateInFlight(key, block)

    override fun buildMalAuthorizeUrl(): String = malAuthManager.buildAuthorizeUrl()

    override suspend fun handleMalOAuthCallback(code: String, state: String?): Result<MalUser> =
        malAuthManager.handleOAuthCallback(code, state)

    override fun getMalUser(): MalUser = malAuthManager.getCurrentUser()

    override fun logoutMal() {
        // Phase 4: notify SyncEngine first so it can cancel in-flight job
        // and clear local DB before auth credentials are wiped.
        syncEngine?.onLogout()
        malAuthManager.logout()
    }

    override suspend fun syncWithMal(): MalSyncResult = malAuthManager.syncWithMal()

    /**
     * Explicitly requeues FAILED_PERMANENTLY mutations back to PENDING (resetting attempts to 0)
     * and triggers a drain if online. Only called by explicit user action.
     */
    override suspend fun retryFailedMutations(mediaType: String?): Int =
        syncEngine?.retryFailedPermanently(mediaType) ?: 0

    override fun getLastSyncedTime(): Long = malAuthManager.getLastSynced()

    override fun getCachedTracking(type: String): List<UserMediaItem>? {
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
    /**
     * Loads the user's anime list.
     *
     * Phase 4 local-first behavior:
     * - [forceRefresh] = false: reads from local DB first if available; falls back to MAL fetch.
     * - [forceRefresh] = true: uses safe reconcile flow — drain pending queue first (best-effort),
     *   then fetch MAL, then reconcile (entries with active pending mutations are NOT overwritten).
     *   If the fetch fails, local state is preserved unchanged.
     */
    override suspend fun getUserAnimeList(forceRefresh: Boolean): MalFetchResult<List<UserMediaItem>> = withContext(Dispatchers.IO) {
        val dao = libraryDao
        if (dao != null && !forceRefresh) {
            // Phase 4: serve from local DB if we have data
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

        // Fallback: original behavior (no DAO wired, or first-run before DB is populated)
        val result = malAuthManager.fetchUserAnimeList(forceRefresh = forceRefresh)
        when (result) {
            is MalFetchResult.Failure -> result
            is MalFetchResult.Success -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.ANIME)
                CacheManager.putTracking("ANIME", enriched)
                dao?.let { upsertLibraryEntries(it, enriched, "ANIME") }
                runCatching { com.canim.app.CanimApplication.instance }.getOrNull()?.let {
                    CacheManager.saveTrackingToDisk(it, "ANIME", enriched)
                }
                MalFetchResult.Success(enriched, result.totalItems)
            }
            is MalFetchResult.Partial -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.ANIME)
                CacheManager.putTracking("ANIME", enriched)
                dao?.let { upsertLibraryEntries(it, enriched, "ANIME") }
                runCatching { com.canim.app.CanimApplication.instance }.getOrNull()?.let {
                    CacheManager.saveTrackingToDisk(it, "ANIME", enriched)
                }
                MalFetchResult.Partial(enriched, result.fetchedItems, result.error)
            }
        }
    }

    /**
     * Safe force-refresh reconciliation for Anime:
     * 1. Drain pending queue best-effort (failures are preserved, not discarded).
     * 2. Fetch MAL — if fetch fails, return failure without touching local state.
     * 3. Reconcile: entries with active pending mutations are NOT overwritten by server data.
     * 4. Update DB + CacheManager + disk only on confirmed success.
     */
    private suspend fun safeReconcileAnime(): MalFetchResult<List<UserMediaItem>> {
        val dao = libraryDao ?: return MalFetchResult.Failure(Exception("LibraryDao not wired"))
        val mutDao = pendingMutationDao

        // Step 1: on explicit force-refresh, requeue FAILED_PERMANENTLY mutations for ANIME
        // then drain pending best-effort (failures are preserved, not discarded).
        try {
            mutDao?.resetFailedPermanentlyToPending("ANIME")
            syncEngine?.drainQueue()
        } catch (_: Exception) {}

        // Step 2: fetch from MAL
        val result = malAuthManager.fetchUserAnimeList(forceRefresh = true)
        if (result is MalFetchResult.Failure) return result  // local state untouched

        val serverItems = when (result) {
            is MalFetchResult.Success -> result.data
            is MalFetchResult.Partial -> result.data
            else -> emptyList()
        }

        // Step 3: reconcile — entries with active pending mutations keep local data
        val activePendingMalIds = mutDao?.getActiveMalIds("ANIME") ?: emptySet()
        val enrichedServer = enrichWithAniListMetadata(serverItems, MediaType.ANIME)

        // Overwrite entries WITHOUT active pending; preserve entries WITH active pending
        enrichedServer.forEach { serverItem ->
            val malId = serverItem.malId ?: return@forEach
            if (malId !in activePendingMalIds) {
                dao.upsertEntry(serverItem.toLibraryEntry("ANIME"))
            }
        }
        // Remove entries from DB that are no longer in MAL list (and have no pending)
        val serverMalIds = enrichedServer.mapNotNull { it.malId }.toSet()
        val localMalIds = dao.getAllMalIds("ANIME")
        (localMalIds - serverMalIds).forEach { orphanId ->
            if (orphanId !in activePendingMalIds) {
                dao.deleteEntry(orphanId, "ANIME")
            }
        }

        // Step 4: read final state from DB (blend of server + preserved local)
        val finalItems = dao.getAllEntries("ANIME").map { it.toUserMediaItem { json -> parseJsonList(json) } }
        CacheManager.putTracking("ANIME", finalItems)
        runCatching { com.canim.app.CanimApplication.instance }.getOrNull()?.let {
            CacheManager.saveTrackingToDisk(it, "ANIME", finalItems)
        }
        mutDao?.clearSucceeded()

        return when (result) {
            is MalFetchResult.Success -> MalFetchResult.Success(finalItems, finalItems.size)
            is MalFetchResult.Partial -> MalFetchResult.Partial(finalItems, finalItems.size, result.error)
            else -> MalFetchResult.Success(finalItems, finalItems.size)
        }
    }

    /**
     * Loads the user's manga list from MAL as source of truth, enriched with AniList metadata.
     */
    /**
     * Loads the user's manga list.
     * Phase 4: same local-first + safe reconcile logic as [getUserAnimeList].
     */
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
                runCatching { com.canim.app.CanimApplication.instance }.getOrNull()?.let {
                    CacheManager.saveTrackingToDisk(it, "MANGA", enriched)
                }
                MalFetchResult.Success(enriched, result.totalItems)
            }
            is MalFetchResult.Partial -> {
                val enriched = enrichWithAniListMetadata(result.data, MediaType.MANGA)
                CacheManager.putTracking("MANGA", enriched)
                dao?.let { upsertLibraryEntries(it, enriched, "MANGA") }
                runCatching { com.canim.app.CanimApplication.instance }.getOrNull()?.let {
                    CacheManager.saveTrackingToDisk(it, "MANGA", enriched)
                }
                MalFetchResult.Partial(enriched, result.fetchedItems, result.error)
            }
        }
    }

    private suspend fun safeReconcileManga(): MalFetchResult<List<UserMediaItem>> {
        val dao = libraryDao ?: return MalFetchResult.Failure(Exception("LibraryDao not wired"))
        val mutDao = pendingMutationDao

        // Step 1: on explicit force-refresh, requeue FAILED_PERMANENTLY mutations for MANGA
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
        runCatching { com.canim.app.CanimApplication.instance }.getOrNull()?.let {
            CacheManager.saveTrackingToDisk(it, "MANGA", finalItems)
        }
        mutDao?.clearSucceeded()

        return when (result) {
            is MalFetchResult.Success -> MalFetchResult.Success(finalItems, finalItems.size)
            is MalFetchResult.Partial -> MalFetchResult.Partial(finalItems, finalItems.size, result.error)
            else -> MalFetchResult.Success(finalItems, finalItems.size)
        }
    }

    override suspend fun getCharacterProfile(characterId: Int, forceRefresh: Boolean): CastCrewProfile? = withContext(Dispatchers.IO) {
        AniListClient.getCharacterProfile(characterId, forceRefresh)
    }

    override suspend fun getStaffProfile(staffId: Int, forceRefresh: Boolean): CastCrewProfile? = withContext(Dispatchers.IO) {
        AniListClient.getStaffProfile(staffId, forceRefresh)
    }

    private suspend fun enrichWithAniListMetadata(
        items: List<UserMediaItem>,
        type: MediaType
    ): List<UserMediaItem> = withContext(Dispatchers.IO) {
        val malIds = items.mapNotNull { it.malId }.distinct()
        if (malIds.isEmpty()) return@withContext items

        val dao = libraryDao
        val unEnrichedMalIds = mutableListOf<Int>()

        for (mId in malIds) {
            val localEntry = dao?.getEntry(mId, type.name)
            if (localEntry?.anilistId != null) {
                CacheManager.putIdMapping(mId, localEntry.anilistId, type)
                continue
            }
            if (CacheManager.getMetadata(mId, type) != null) {
                continue
            }
            val aniId = CacheManager.getAniListIdForMalId(mId, type)
            if (aniId != null && CacheManager.getDetail(CacheManager.detailKey(aniId, mId)) != null) {
                continue
            }
            if (CacheManager.isNegativeCached("resolve_mal_${mId}_${type.name}")) {
                continue
            }
            unEnrichedMalIds.add(mId)
        }

        val aniListMap = if (unEnrichedMalIds.isNotEmpty()) {
            try {
                AniListClient.getMediaBatchByMalIds(unEnrichedMalIds, type)
            } catch (_: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        items.map { item ->
            val mId = item.malId ?: return@map item
            val aniItem = aniListMap[mId]
            if (aniItem != null) {
                val updatedMetadata = item.metadata.copy(
                    titleEnglish = aniItem.titleEnglish ?: item.metadata.titleEnglish,
                    imageUrl = item.metadata.imageUrl.ifBlank { aniItem.imageUrl },
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
    }

    // --- Tracking Mutations (Phase 4: Local-First → Queue → Best-Effort Immediate Send) ---

    /**
     * Updates anime tracking locally first, then enqueues for sync to MAL.
     * Returns [Result.success] as soon as the local write is confirmed.
     * The actual HTTP call is handled by [LibrarySyncEngine] in the background.
     *
     * Falls back to direct MAL call if local DB is not wired (test / no-DAO mode).
     */
    override suspend fun updateAnimeTracking(malId: Int, tracking: MalTracking): Result<Unit> = withContext(Dispatchers.IO) {
        val dao = libraryDao
        val mutDao = pendingMutationDao
        if (dao != null && mutDao != null) {
            // Phase 4 path: write locally, enqueue mutation
            return@withContext localFirstUpdate(malId, "ANIME", tracking, dao, mutDao)
        }
        // Fallback: direct MAL call (original behavior)
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

    private suspend fun localFirstUpdate(
        malId: Int,
        mediaType: String,
        tracking: MalTracking,
        dao: LibraryDao,
        mutDao: PendingMutationDao
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            // Update tracking fields on existing DB entry (preserves metadata)
            val existing = dao.getEntry(malId, mediaType)
            val updated = (existing ?: LibraryEntry(malId = malId, mediaType = mediaType)).copy(
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

            // Atomic: upsert local entry AND enqueue pending mutation in single SQLite transaction
            dao.upsertWithMutation(updated, mutation, mutDao)

            // Best-effort immediate send if online
            swrScope.launch { syncEngine?.trySendImmediate(malId, mediaType) }

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

            // Atomic: delete local entry AND enqueue delete mutation in single SQLite transaction
            dao.deleteWithMutation(malId, mediaType, mutation, mutDao)

            swrScope.launch { syncEngine?.trySendImmediate(malId, mediaType) }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Helpers for MyAnimeList Node Mapping ---
    private fun mapMalAnimeNodeToMediaItem(node: MalAnimeNode): MediaItem {
        return MediaItem(
            malId = node.id,
            anilistId = CacheManager.getAniListIdForMalId(node.id),
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
            anilistId = CacheManager.getAniListIdForMalId(node.id),
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

    override fun searchFilterKey(query: String, genres: List<String>?, year: Int?, format: String?): String {
        val trimmed = query.trim()
        val genresKey = if (genres.isNullOrEmpty()) "" else genres.sorted().joinToString(",")
        return "${trimmed}_${genresKey}_${year}_${format}"
    }

    override fun discoverFilterKey(
        category: DiscoverCategory,
        filter: DiscoverFilter,
        page: Int,
        randomSort: String?,
        mediaType: MediaType?
    ): String {
        val resolvedType = mediaType ?: if (
            filter.format == "MANGA" ||
            category == DiscoverCategory.TOP_MANGA ||
            category == DiscoverCategory.RECENTLY_DONE_MANGA ||
            category == DiscoverCategory.NEWLY_ADDED_MANGA
        ) MediaType.MANGA else MediaType.ANIME
        return "${resolvedType.name.lowercase()}_${category.key}_${filter.genre}_${filter.format}_${filter.year}_${filter.season}_${filter.minScore}_${randomSort}_p$page"
    }

    override suspend fun searchAnime(
        query: String,
        genres: List<String>?,
        year: Int?,
        format: String?,
        forceRefresh: Boolean
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val filterKey = searchFilterKey(trimmed, genres, year, format)
        if (trimmed.isEmpty() && genres.isNullOrEmpty() && year == null && format == null) return@withContext emptyList()

        // SWR: serve cached data immediately; trigger background refresh only if stale
        if (!forceRefresh) {
            val swrHit = CacheManager.getSearchSwr(filterKey, "ANIME")
            if (swrHit != null) {
                if (swrHit.isStale) {
                    val cacheKey = CacheManager.searchKey(filterKey, "ANIME")
                    launchSwrJob(cacheKey) {
                        runCatching {
                            val fresh = AniListClient.searchMedia(trimmed, MediaType.ANIME, genres, year, format, forceRefresh = true)
                            if (fresh.isNotEmpty()) {
                                CacheManager.putSearch(filterKey, "ANIME", fresh)
                                if (fresh != swrHit.data) {
                                    _cacheRefreshEvents.emit(CacheRefreshEvent(cacheKey, CacheRefreshType.SEARCH))
                                }
                            }
                        }
                    }
                }
                return@withContext swrHit.data
            }
        }

        deduplicateInFlight("search_anime_$filterKey") {
            var result = runCatching {
                AniListClient.searchMedia(trimmed, MediaType.ANIME, genres, year, format, forceRefresh = forceRefresh)
            }.getOrDefault(emptyList())

            val hasExplicitFilters = !genres.isNullOrEmpty() || year != null || !format.isNullOrBlank()

            // Fallback to MyAnimeList Search API v2 if AniList is down / empty
            if (result.isEmpty()) {
                try {
                    val malItems = if (trimmed.isNotBlank()) {
                        val malResp = ApiClient.malApi.searchAnime(MalAuthManager.CLIENT_ID, trimmed, limit = 50)
                        if (malResp.isSuccessful && malResp.body()?.data?.isNotEmpty() == true) {
                            malResp.body()!!.data.map { mapMalAnimeNodeToMediaItem(it.node) }
                        } else emptyList()
                    } else if (hasExplicitFilters) {
                        val malResp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "bypopularity", limit = 100)
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
                        result = if (hasExplicitFilters) filtered else malItems.take(30)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("CanimRepository", "MAL search anime fallback failed: ${LogRedactor.redact(e.message ?: "")}")
                }
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
                if (localMatches.isNotEmpty() && (!hasExplicitFilters || localMatches != baseList)) {
                    result = localMatches
                }
            }

            if (result.isNotEmpty()) {
                CacheManager.putSearch(filterKey, "ANIME", result)
            }
            result
        }
    }

    override suspend fun searchManga(
        query: String,
        genres: List<String>?,
        year: Int?,
        format: String?,
        forceRefresh: Boolean
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val filterKey = searchFilterKey(trimmed, genres, year, format)
        if (trimmed.isEmpty() && genres.isNullOrEmpty() && year == null && format == null) return@withContext emptyList()

        // SWR: serve cached data immediately; trigger background refresh only if stale
        if (!forceRefresh) {
            val swrHit = CacheManager.getSearchSwr(filterKey, "MANGA")
            if (swrHit != null) {
                if (swrHit.isStale) {
                    val cacheKey = CacheManager.searchKey(filterKey, "MANGA")
                    launchSwrJob(cacheKey) {
                        runCatching {
                            val fresh = AniListClient.searchMedia(trimmed, MediaType.MANGA, genres, year, format, forceRefresh = true)
                            if (fresh.isNotEmpty()) {
                                CacheManager.putSearch(filterKey, "MANGA", fresh)
                                if (fresh != swrHit.data) {
                                    _cacheRefreshEvents.emit(CacheRefreshEvent(cacheKey, CacheRefreshType.SEARCH))
                                }
                            }
                        }
                    }
                }
                return@withContext swrHit.data
            }
        }

        deduplicateInFlight("search_manga_$filterKey") {
            var result = runCatching {
                AniListClient.searchMedia(trimmed, MediaType.MANGA, genres, year, format, forceRefresh = forceRefresh)
            }.getOrDefault(emptyList())

            val hasExplicitFilters = !genres.isNullOrEmpty() || year != null || !format.isNullOrBlank()

            // Fallback to MyAnimeList Search API v2 if AniList is down / empty
            if (result.isEmpty()) {
                try {
                    val malItems = if (trimmed.isNotBlank()) {
                        val malResp = ApiClient.malApi.searchManga(MalAuthManager.CLIENT_ID, trimmed, limit = 50)
                        if (malResp.isSuccessful && malResp.body()?.data?.isNotEmpty() == true) {
                            malResp.body()!!.data.map { mapMalMangaNodeToMediaItem(it.node) }
                        } else emptyList()
                    } else if (hasExplicitFilters) {
                        val malResp = ApiClient.malApi.getMangaRanking(MalAuthManager.CLIENT_ID, "bypopularity", limit = 100)
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
                        result = if (hasExplicitFilters) filtered else malItems.take(30)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("CanimRepository", "MAL search manga fallback failed: ${LogRedactor.redact(e.message ?: "")}")
                }
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
                if (localMatches.isNotEmpty() && (!hasExplicitFilters || localMatches != baseList)) {
                    result = localMatches
                }
            }

            if (result.isNotEmpty()) {
                CacheManager.putSearch(filterKey, "MANGA", result)
            }
            result
        }
    }

    // --- Discover with On-Demand Loading & forceRefresh Propagation ---
    override suspend fun getDiscoverMedia(
        category: DiscoverCategory,
        filter: DiscoverFilter,
        page: Int,
        forceRefresh: Boolean,
        randomSort: String?,
        mediaType: MediaType?
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val cacheKey = discoverFilterKey(category, filter, page, randomSort, mediaType)
        if (!forceRefresh) {
            val swrHit = CacheManager.getDiscoverSwr(cacheKey)
            if (swrHit != null) {
                if (swrHit.isStale) {
                    // Capture variables for background lambda
                    val capCategory = category
                    val capFilter = filter
                    val capPage = page
                    val capRandomSort = randomSort
                    val capMediaType = mediaType
                    val capKey = cacheKey
                    val capStale = swrHit.data
                    val canonicalDiscoverKey = CacheManager.discoverKey(cacheKey)
                    launchSwrJob(canonicalDiscoverKey) {
                        runCatching {
                            val fresh = fetchDiscoverInternal(
                                category = capCategory,
                                filter = capFilter,
                                page = capPage,
                                randomSort = capRandomSort,
                                forceRefresh = true,
                                mediaType = capMediaType
                            )
                            if (fresh.isNotEmpty()) {
                                CacheManager.putDiscover(capKey, fresh)
                                if (fresh != capStale) {
                                    _cacheRefreshEvents.emit(CacheRefreshEvent(canonicalDiscoverKey, CacheRefreshType.DISCOVER))
                                }
                            }
                        }
                    }
                }
                return@withContext swrHit.data
            }
        }

        fetchDiscoverInternal(category, filter, page, randomSort, forceRefresh, mediaType)
    }

    private suspend fun fetchDiscoverInternal(
        category: DiscoverCategory,
        filter: DiscoverFilter,
        page: Int,
        randomSort: String?,
        forceRefresh: Boolean,
        mediaType: MediaType? = null
    ): List<MediaItem> {
        val cacheKey = discoverFilterKey(category, filter, page, randomSort, mediaType)
        val limit = 25
        val offset = (page - 1) * limit

        // B.4: Top Anime exclusively based on MAL API ranking
        if (category == DiscoverCategory.TOP_ANIME) {
            try {
                val resp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "all", limit, offset)
                if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                    val malNodes = resp.body()!!.data.map { it.node }
                    val malIds = malNodes.map { it.id }.distinct()
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
                    return items
                }
            } catch (_: Exception) {}
        } else if (category == DiscoverCategory.TOP_MANGA) {
            try {
                val resp = ApiClient.malApi.getMangaRanking(MalAuthManager.CLIENT_ID, "all", limit, offset)
                if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                    val malNodes = resp.body()!!.data.map { it.node }
                    val malIds = malNodes.map { it.id }.distinct()
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
                    return items
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
                forceRefresh = forceRefresh,
                mediaType = mediaType
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
                    DiscoverCategory.TRENDING_NOW,
                    DiscoverCategory.RECENTLY_DONE_MANGA,
                    DiscoverCategory.NEWLY_ADDED_MANGA -> {
                        // MAL does not provide trending, recently finished, or newly added manga endpoints (only top manga rankings).
                        // These categories exclusively require AniList engine and remain empty when AniList is down.
                    }
                    else -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CanimRepository", "MAL discover fallback failed: ${LogRedactor.redact(e.message ?: "")}")
            }
        }

        // Offline fallback if network fails completely (except AniList-exclusive categories)
        if (category != DiscoverCategory.TRENDING_NOW &&
            category != DiscoverCategory.RECENTLY_DONE_MANGA &&
            category != DiscoverCategory.NEWLY_ADDED_MANGA &&
            results.isEmpty() && page == 1) {
            results = if (filter.format == "MANGA") fallbackManga() else fallbackAnime()
        }

        if (results.isNotEmpty()) {
            CacheManager.putDiscover(cacheKey, results)
        }

        return results
    }

    /**
     * Synchronously inspects persistent / memory cache for extended media details.
     * Enforces strict MediaType namespace isolation when resolving IDs.
     */
    override fun getCachedExtendedDetail(
        aniListId: Int?,
        malId: Int?,
        type: MediaType?
    ): ExtendedMediaDetail? {
        val resolvedAniListId = aniListId ?: (malId?.let { CacheManager.getAniListIdForMalId(it, type) })
        val resolvedMalId = malId ?: (resolvedAniListId?.let { CacheManager.getMalIdForAniListId(it, type) })
        val primaryCacheKey = CacheManager.detailKey(resolvedAniListId, resolvedMalId)

        return CacheManager.getDetail(primaryCacheKey)
            ?: (resolvedAniListId?.let { CacheManager.getDetail(CacheManager.detailKey(it, null)) })
            ?: (resolvedMalId?.let { CacheManager.getDetail(CacheManager.detailKey(null, it)) })
    }

    override suspend fun getMalExtendedDetailFallback(malId: Int, type: MediaType): ExtendedMediaDetail? =
        malAuthManager.getExtendedDetailFallback(malId, type)

    // --- Extended Details: Primary AniList, Fallback to MAL ---
    suspend fun getExtendedDetails(
        aniListId: Int?,
        malId: Int?,
        type: MediaType,
        forceRefresh: Boolean = false
    ): ExtendedMediaDetail? = withContext(Dispatchers.IO) {
        val resolvedAniListId = aniListId ?: (malId?.let { CacheManager.getAniListIdForMalId(it, type) })
        val resolvedMalId = malId ?: (resolvedAniListId?.let { CacheManager.getMalIdForAniListId(it, type) })
        val primaryCacheKey = CacheManager.detailKey(resolvedAniListId, resolvedMalId)

        if (!forceRefresh) {
            // SWR: resolve from multiple cache keys, prefer most specific
            val swrHit = CacheManager.getDetailSwr(primaryCacheKey)
                ?: (resolvedAniListId?.let { CacheManager.getDetailSwr(CacheManager.detailKey(it, null)) })
                ?: (resolvedMalId?.let { CacheManager.getDetailSwr(CacheManager.detailKey(null, it)) })
            if (swrHit != null && (swrHit.data.malScore != null || resolvedMalId == null)) {
                if (swrHit.isStale) {
                    val capAniId = resolvedAniListId
                    val capMalId = resolvedMalId
                    val capType = type
                    val capPrimaryKey = primaryCacheKey
                    launchSwrJob(capPrimaryKey) {
                        runCatching {
                            val fresh = AniListClient.getExtendedDetails(capAniId, capMalId, capType, forceRefresh = true)
                            if (fresh != null) {
                                CacheManager.putDetail(capPrimaryKey, fresh)
                                capAniId?.let { CacheManager.putDetail(CacheManager.detailKey(it, null), fresh) }
                                capMalId?.let { CacheManager.putDetail(CacheManager.detailKey(null, it), fresh) }
                                if (fresh != swrHit.data) {
                                    _cacheRefreshEvents.emit(CacheRefreshEvent(capPrimaryKey, CacheRefreshType.DETAIL))
                                }
                            }
                        }
                    }
                }
                return@withContext swrHit.data
            }
        }

        deduplicateInFlight("detail_${resolvedAniListId}_${resolvedMalId}_${type.name}") {
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

                val aniDetail = try { aniDeferred.await() } catch (_: Exception) { null }
                var malExt = try { malDeferred.await() } catch (_: Exception) { null }

                // If MAL ID wasn't known beforehand, but AniList returned it, fetch MAL fallback
                val effectiveMalId = aniDetail?.malId ?: resolvedMalId
                if (malExt == null && effectiveMalId != null && effectiveMalId != resolvedMalId) {
                    malExt = malAuthManager.getExtendedDetailFallback(effectiveMalId, type)
                }

                val merged = if (aniDetail != null && malExt != null) {
                    aniDetail.copy(
                        // Cover: MAL fully authoritative, AniList is fallback
                        coverImage = malExt.coverImage?.takeIf { it.isNotBlank() } ?: aniDetail.coverImage,
                        // Metrics: MAL is authoritative for Rating MAL
                        malScore = malExt.malScore ?: aniDetail.malScore,
                        malRank = malExt.malRank ?: aniDetail.rank,
                        malPopularity = malExt.malPopularity ?: aniDetail.popularity,
                        malMembers = malExt.malMembers ?: aniDetail.watchers,
                        // Basic metadata provided by MAL
                        synopsis = malExt.synopsis?.takeIf { it.isNotBlank() } ?: aniDetail.synopsis,
                        airingStatus = malExt.airingStatus ?: aniDetail.airingStatus,
                        startDate = malExt.startDate ?: aniDetail.startDate,
                        endDate = malExt.endDate ?: aniDetail.endDate,
                        genres = if (malExt.genres.isNotEmpty()) malExt.genres else aniDetail.genres,
                        source = malExt.source ?: aniDetail.source,
                        // Visual / rich media not provided by MAL: AniList
                        bannerImage = aniDetail.bannerImage ?: malExt.bannerImage,
                        studio = aniDetail.studio ?: malExt.studio,
                        studioId = aniDetail.studioId ?: malExt.studioId,
                        publisher = aniDetail.publisher ?: malExt.publisher
                    )
                } else {
                    aniDetail ?: malExt
                }

                if (merged != null) {
                    val effectiveAni = merged.anilistId
                    val effectiveMal = merged.malId
                    if (effectiveAni != null && effectiveMal != null) {
                        CacheManager.putIdMapping(effectiveMal, effectiveAni, type)
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
    }

    override suspend fun isAniListUnavailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (AniListClient.pingHealth()) {
                return@withContext false
            }
            // Tolerant retry: wait 1.5s before concluding outage
            kotlinx.coroutines.delay(1500L)
            !AniListClient.pingHealth()
        } catch (_: Exception) {
            true
        }
    }

    override suspend fun isMalUnavailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "all", limit = 1)
            !resp.isSuccessful
        } catch (_: Exception) {
            true
        }
    }

    // --- Cache Management Actions ---
    override suspend fun pruneCache() {
        CacheManager.pruneExpired()
    }

    override fun clearMetadataCache() {
        CacheManager.clearMetadataCache()
    }

    override suspend fun clearImageCache(context: Context) {
        CacheManager.clearImageCache(context)
    }

    override suspend fun clearAllCache(context: Context) {
        CacheManager.clearAllCache(context)
    }

    // --- In-Memory Demo Dataset for Unauthenticated Mode ---
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

    override suspend fun getMalTrackingStatus(malId: Int, type: MediaType): MalTracking? {
        return malAuthManager.getMalUserTracking(malId, type)
    }

    override suspend fun getStudioFilmography(
        studioId: Int?,
        search: String?,
        page: Int,
        forceRefresh: Boolean,
        sort: StudioFilmographySort,
        isMain: Boolean
    ): StudioFilmographyPage? {
        return AniListClient.getStudioFilmography(studioId, search, page, forceRefresh = forceRefresh, sort = sort, isMain = isMain)
    }

    override suspend fun searchStudios(query: String, page: Int, perPage: Int): List<StudioBioInfo> {
        return AniListClient.searchStudios(query, page, perPage)
    }

    // ── Phase 4 local DB helper functions ─────────────────────────────────────

    /**
     * Bulk-upserts a list of [UserMediaItem] into [LibraryDao].
     * Called after a successful MAL fetch to populate/update the local DB.
     */
    private fun upsertLibraryEntries(dao: LibraryDao, items: List<UserMediaItem>, mediaType: String) {
        items.forEach { item ->
            try {
                dao.upsertEntry(item.toLibraryEntry(mediaType))
            } catch (_: Exception) { /* best-effort — do not fail the whole fetch */ }
        }
    }

    /**
     * Converts a [UserMediaItem] to a [LibraryEntry] for local storage.
     * [mediaType] must be "ANIME" or "MANGA".
     */
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

    /**
     * Parses a JSON array string (e.g. `["Action","Fantasy"]`) into a [List<String>].
     * Returns an empty list on parse failure.
     */
    private fun parseJsonList(json: String): List<String> {
        return try {
            if (json.isBlank() || json == "[]") emptyList()
            else gson.fromJson(json, Array<String>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
