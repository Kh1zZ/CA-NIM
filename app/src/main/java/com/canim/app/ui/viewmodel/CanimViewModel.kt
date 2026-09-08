package com.canim.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.canim.app.data.model.*
import com.canim.app.data.repository.CanimRepository
import com.canim.app.CanimApplication
import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.repository.StudioBioRegistry
import com.canim.app.ui.navigation.ScreenRoute
import com.canim.app.BuildConfig
import com.canim.app.data.remote.UpdateChecker
import com.canim.app.data.remote.UpdateInfo
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Immutable
data class CanimUiState(
    val animeList: List<UserMediaItem> = emptyList(),
    val mangaList: List<UserMediaItem> = emptyList(),
    // Off-main-thread filtered lists for fast UI rendering
    val watchingAnime: List<UserMediaItem> = emptyList(),
    val readingManga: List<UserMediaItem> = emptyList(),
    val completedAnimeMalIds: Set<Int> = emptySet(),
    val completedMangaMalIds: Set<Int> = emptySet(),

    val stats: TrackerStats = TrackerStats(),
    val activeTab: String = "dashboard",
    val libraryFilterType: MediaType = MediaType.ANIME,
    val libraryStatusFilter: String? = null,
    val librarySearchQuery: String = "",
    val librarySortBy: String = "updated",

    // Discover state
    val selectedDiscoverCategory: DiscoverCategory = DiscoverCategory.CURRENT_SEASON,
    val discoverFilter: DiscoverFilter = DiscoverFilter(),
    val discoverItems: List<MediaItem> = emptyList(),
    val isDiscoverLoading: Boolean = false,
    val discoverPage: Int = 1,
    val isDiscoverLoadingMore: Boolean = false,
    val canLoadMoreDiscover: Boolean = true,

    // Reactive search state
    val searchQuery: String = "",
    val searchType: MediaType = MediaType.ANIME,
    val searchResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchGenres: List<String> = emptyList(),
    val searchYear: Int? = null,
    val searchFormat: String? = null,

    // Detail & Extended details state
    val selectedDetailItem: Any? = null,
    val detailMediaType: MediaType = MediaType.ANIME,
    val isDetailOpen: Boolean = false,
    val extendedDetail: ExtendedMediaDetail? = null,
    val isLoadingExtendedDetail: Boolean = false,

    // Cast & Crew Profile state
    val selectedCastCrewProfile: CastCrewProfile? = null,
    val isLoadingCastCrewProfile: Boolean = false,

    // Stats Fullscreen state
    val isStatsOpen: Boolean = false,
    val isAddTitleSheetOpen: Boolean = false,

    // Studio Filmography state
    val studioFilmographyStudioId: Int? = null,
    val studioFilmographyStudioName: String = "",
    val studioFilmographyBio: StudioBioInfo? = null,
    val studioFilmographySort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC,
    val studioFilmographyItems: List<MediaItem> = emptyList(),
    val isStudioFilmographyLoading: Boolean = false,
    val isStudioFilmographyLoadingMore: Boolean = false,
    val studioFilmographyPage: Int = 1,
    val studioFilmographyTotalEntries: Int = 0,
    val canLoadMoreStudioFilmography: Boolean = true,
    val studioSearchResults: List<StudioBioInfo> = emptyList(),
    val isSearchingStudios: Boolean = false,

    // Gacha Flashcard state
    val gachaCredits: Int = 5,
    val flashcardDeck: List<MediaItem> = emptyList(),
    val isFlashcardLoading: Boolean = false,

    // App Update state
    val isCheckingUpdate: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val isAutoUpdateCheckEnabled: Boolean = true,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float = 0f,
    val downloadedApkFile: java.io.File? = null,

    // App & Auth state
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val snackbarMessage: String? = null,
    val appMode: String = "online_sync", // "offline" or "online_sync"
    val malUser: MalUser = MalUser(),
    val isSyncingMal: Boolean = false,
    val isExchangingToken: Boolean = false,
    val isLoadingLibrary: Boolean = false,
    val isAniListDown: Boolean = false,
    val isMalDown: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CanimViewModel(
    private val repository: CanimRepository,
    private val gachaCreditManager: GachaCreditManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CanimUiState(
            malUser = repository.getMalUser(),
            appMode = if (repository.getMalUser().isLoggedIn) "online_sync" else "offline"
        )
    )
    val uiState: StateFlow<CanimUiState> = _uiState.asStateFlow()

    // Centralized Navigation Back Stack (ScreenRoute)
    private val _screenStack = MutableStateFlow<List<ScreenRoute>>(emptyList())
    val screenStack: StateFlow<List<ScreenRoute>> = _screenStack.asStateFlow()

    // Reactive search flow with unique trigger token to avoid StateFlow conflation on filter changes
    data class SearchTrigger(
        val query: String,
        val type: MediaType,
        val token: Long = System.nanoTime()
    )
    private val _searchQueryFlow = MutableStateFlow(SearchTrigger("", MediaType.ANIME))

    private var discoverJob: Job? = null
    private var discoverRequestToken = 0L
    private var detailJob: Job? = null
    private var studioJob: Job? = null
    private var studioSearchJob: Job? = null

    // Scroll Position Registry & Detail Cache for Seamless Detail-to-Detail Navigation
    private val detailScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    private val detailCache = mutableMapOf<String, ExtendedMediaDetail>()

    // Stats Screen Scroll Position Preservation
    private var statsScrollIndex: Int = 0
    private var statsScrollOffset: Int = 0

    fun saveStatsScrollPosition(index: Int, offset: Int) {
        statsScrollIndex = index
        statsScrollOffset = offset
    }

    fun getStatsScrollPosition(): Pair<Int, Int> = Pair(statsScrollIndex, statsScrollOffset)

    fun resetStatsScrollPosition() {
        statsScrollIndex = 0
        statsScrollOffset = 0
    }

    fun cacheDetail(item: Any, detail: ExtendedMediaDetail) {
        val key = getMediaKey(item)
        detailCache[key] = detail
        detail.anilistId?.let { detailCache[it.toString()] = detail }
        detail.malId?.let { detailCache[it.toString()] = detail }
        (item as? MediaItem)?.malId?.let { detailCache[it.toString()] = detail }
        (item as? MediaItem)?.anilistId?.let { detailCache[it.toString()] = detail }
        (item as? UserMediaItem)?.malId?.let { detailCache[it.toString()] = detail }
        (item as? UserMediaItem)?.anilistId?.let { detailCache[it.toString()] = detail }
    }

    fun getCachedDetail(item: Any): ExtendedMediaDetail? {
        val key = getMediaKey(item)
        val direct = detailCache[key]
        if (direct != null) return direct

        val aniId = when (item) {
            is UserMediaItem -> item.anilistId
            is MediaItem -> item.anilistId
            else -> null
        }
        val malId = when (item) {
            is UserMediaItem -> item.malId
            is MediaItem -> item.malId
            else -> null
        }
        val type = when (item) {
            is UserMediaItem -> item.metadata.type
            is MediaItem -> item.type
            else -> null
        }

        return (aniId?.let { detailCache[it.toString()] })
            ?: (malId?.let { detailCache[it.toString()] })
            ?: repository.getCachedExtendedDetail(aniId, malId, type)
    }

    fun saveDetailScrollPosition(key: String, index: Int, offset: Int) {
        detailScrollPositions[key] = Pair(index, offset)
    }

    fun getDetailScrollPosition(key: String): Pair<Int, Int> {
        return detailScrollPositions[key] ?: Pair(0, 0)
    }

    fun getMediaKey(item: Any): String {
        return when (item) {
            is UserMediaItem -> item.anilistId?.toString() ?: item.malId?.toString() ?: item.title
            is MediaItem -> item.anilistId?.toString() ?: item.malId?.toString() ?: item.title
            else -> item.toString()
        }
    }

    init {
        // Cold-start instant cache-first load from disk/memory
        val cachedAnime = repository.getCachedTracking("ANIME")
        val cachedManga = repository.getCachedTracking("MANGA")
        if (!cachedAnime.isNullOrEmpty() || !cachedManga.isNullOrEmpty()) {
            updateLibraryData(cachedAnime ?: emptyList(), cachedManga ?: emptyList())
        }

        // Initialize Gacha Credits
        val gachaMgr = gachaCreditManager ?: try { GachaCreditManager.getInstance(CanimApplication.instance) } catch (_: Exception) { null }
        val currentCredits = gachaMgr?.getCredits() ?: 5
        _uiState.update { it.copy(gachaCredits = currentCredits) }

        // Initialize Update Preferences & Auto-check
        initUpdateChecker()

        // Prefetch discovery lazily after initial UI is ready (or on-demand when switching to discover tab)
        viewModelScope.launch {
            delay(2500L)
            if (_uiState.value.discoverItems.isEmpty()) {
                loadDiscoverCategory(_uiState.value.selectedDiscoverCategory, _uiState.value.discoverFilter)
            }
        }

        // Silent background sync / load user library
        loadUserLibrary()

        // Reactive Debounced Search (300ms)
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300L)
                .flatMapLatest { trigger ->
                    flow {
                        val trimmed = trigger.query.trim()
                        val type = trigger.type
                        val state = _uiState.value
                        val hasFilters = state.searchGenres.isNotEmpty() || state.searchYear != null || state.searchFormat != null
                        if (trimmed.length < 2 && !hasFilters) {
                            emit(emptyList<MediaItem>())
                        } else {
                            _uiState.update { it.copy(isSearching = true) }
                            val results = if (type == MediaType.ANIME) {
                                repository.searchAnime(trimmed, state.searchGenres, state.searchYear, state.searchFormat)
                            } else {
                                repository.searchManga(trimmed, state.searchGenres, state.searchYear, state.searchFormat)
                            }
                            emit(results)
                        }
                    }
                }
                .flowOn(Dispatchers.IO)
                .collect { results ->
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
        }

        // Memory Cache Auto-Pruning every 15 minutes
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15 * 60 * 1000L)
                repository.pruneCache()
            }
        }

        // Real-time API outage check (AniList & MAL)
        checkApiHealth()
    }

    fun checkApiHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            val aniDown = repository.isAniListUnavailable()
            val malDown = repository.isMalUnavailable()
            _uiState.update { it.copy(isAniListDown = aniDown, isMalDown = malDown) }
        }
    }

    /**
     * Loads the authoritative library from MAL in parallel (or demo dataset if not logged in).
     */
    fun loadUserLibrary(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val user = repository.getMalUser()
            if (user.isLoggedIn) {
                // SWR: If cached items exist and synced within 30 minutes, skip network on startup unless forced
                val hasCachedData = _uiState.value.animeList.isNotEmpty() || _uiState.value.mangaList.isNotEmpty()
                val lastSynced = repository.getLastSyncedTime()
                val isCacheFresh = (System.currentTimeMillis() - lastSynced) < 30 * 60 * 1000L
                if (!forceRefresh && hasCachedData && isCacheFresh) {
                    _uiState.update { it.copy(isLoadingLibrary = false, syncStatus = SyncStatus.IDLE) }
                    return@launch
                }

                _uiState.update { it.copy(isLoadingLibrary = true, syncStatus = SyncStatus.SYNCING) }
                val animeDeferred = async(Dispatchers.IO) { repository.getUserAnimeList(forceRefresh) }
                val mangaDeferred = async(Dispatchers.IO) { repository.getUserMangaList(forceRefresh) }

                val animeResult = animeDeferred.await()
                val mangaResult = mangaDeferred.await()

                val animes = when (animeResult) {
                    is MalFetchResult.Success -> animeResult.data
                    is MalFetchResult.Partial -> {
                        showSnackbar("Peringatan: Sebagian anime gagal dimuat (${animeResult.error.message})")
                        animeResult.data
                    }
                    is MalFetchResult.Failure -> {
                        showSnackbar("Gagal memuat anime MAL: ${animeResult.error.message}")
                        _uiState.value.animeList
                    }
                }

                val mangas = when (mangaResult) {
                    is MalFetchResult.Success -> mangaResult.data
                    is MalFetchResult.Partial -> {
                        showSnackbar("Peringatan: Sebagian manga gagal dimuat (${mangaResult.error.message})")
                        mangaResult.data
                    }
                    is MalFetchResult.Failure -> {
                        showSnackbar("Gagal memuat manga MAL: ${mangaResult.error.message}")
                        _uiState.value.mangaList
                    }
                }

                val hasFailure = animeResult is MalFetchResult.Failure && mangaResult is MalFetchResult.Failure
                val finalSyncStatus = if (hasFailure) SyncStatus.FAILED else SyncStatus.SUCCESS

                val gachaMgr = gachaCreditManager ?: try { GachaCreditManager.getInstance(CanimApplication.instance) } catch (_: Exception) { null }
                animes.forEach { gachaMgr?.initBaselineProgress(it.id, it.progress) }
                mangas.forEach { gachaMgr?.initBaselineProgress(it.id, it.progress) }

                updateLibraryData(animes, mangas)
                _uiState.update { it.copy(isLoadingLibrary = false, syncStatus = finalSyncStatus) }

                if (finalSyncStatus == SyncStatus.SUCCESS) {
                    launch {
                        delay(3000L)
                        _uiState.update { if (it.syncStatus == SyncStatus.SUCCESS) it.copy(syncStatus = SyncStatus.IDLE) else it }
                    }
                }
            } else {
                // In-memory demo data for unauthenticated mode
                if (_uiState.value.animeList.isEmpty() && _uiState.value.mangaList.isEmpty()) {
                    updateLibraryData(repository.getDemoAnime(), repository.getDemoManga())
                }
                _uiState.update { it.copy(isLoadingLibrary = false, syncStatus = SyncStatus.IDLE) }
            }
        }
    }

    private fun updateLibraryData(animes: List<UserMediaItem>, mangas: List<UserMediaItem>) {
        viewModelScope.launch(Dispatchers.Default) {
            val watching = animes.filter { it.status == "watching" }
            val reading = mangas.filter { it.status == "reading" }
            val completedAnimeIds = animes.filter { it.status == "completed" }.mapNotNull { it.malId }.toSet()
            val completedMangaIds = mangas.filter { it.status == "completed" }.mapNotNull { it.malId }.toSet()

            val totalEp = animes.sumOf { it.progress }
            val totalCh = mangas.sumOf { it.progressChapters }
            val totalVol = mangas.sumOf { it.progressVolumes }
            val completedAnimeCount = animes.count { it.status == "completed" }
            val completedMangaCount = mangas.count { it.status == "completed" }
            val totalRated = animes.count { it.score > 0 } + mangas.count { it.score > 0 }
            val totalScore = animes.sumOf { it.score } + mangas.sumOf { it.score }
            val mean = if (totalRated > 0) totalScore.toDouble() / totalRated else 0.0
            val daysWatched = (totalEp * 24.0) / (60.0 * 24.0)

            val stats = TrackerStats(
                totalAnime = animes.size,
                totalManga = mangas.size,
                episodesWatched = totalEp,
                chaptersRead = totalCh,
                volumesRead = totalVol,
                completedCount = completedAnimeCount + completedMangaCount,
                meanScore = (mean * 10).toInt() / 10.0,
                daysWatched = (daysWatched * 10).toInt() / 10.0,
                animeWatching = animes.count { it.status == "watching" },
                animeCompleted = completedAnimeCount,
                animeOnHold = animes.count { it.status == "on_hold" },
                animeDropped = animes.count { it.status == "dropped" },
                animePlanToWatch = animes.count { it.status == "plan_to_watch" },
                mangaReading = mangas.count { it.status == "reading" },
                mangaCompleted = completedMangaCount,
                mangaOnHold = mangas.count { it.status == "on_hold" },
                mangaDropped = mangas.count { it.status == "dropped" },
                mangaPlanToRead = mangas.count { it.status == "plan_to_read" }
            )

            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    current.copy(
                        animeList = animes,
                        mangaList = mangas,
                        watchingAnime = watching,
                        readingManga = reading,
                        completedAnimeMalIds = completedAnimeIds,
                        completedMangaMalIds = completedMangaIds,
                        stats = stats
                    )
                }
            }
        }
    }

    // --- Navigation & Filter Controls ---
    fun setTab(tab: String) {
        clearScreenStack()
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == "discover" && _uiState.value.discoverItems.isEmpty() && !_uiState.value.isDiscoverLoading) {
            loadDiscoverCategory(_uiState.value.selectedDiscoverCategory, _uiState.value.discoverFilter)
        }
    }

    fun setLibraryFilterType(type: MediaType) {
        _uiState.update { it.copy(libraryFilterType = type) }
    }

    fun setLibraryStatusFilter(status: String?) {
        _uiState.update { it.copy(libraryStatusFilter = status) }
    }

    fun setLibrarySearch(query: String) {
        _uiState.update { it.copy(librarySearchQuery = query) }
    }

    fun setLibrarySort(sort: String) {
        _uiState.update { it.copy(librarySortBy = sort) }
    }

    // --- Bidirectional Optimistic Tracking Actions ---
    fun quickIncrementAnime(identifier: Any) {
        val currentList = _uiState.value.animeList
        val item = findAnimeItem(identifier) ?: return
        // Guard: Prevent increment & exploit if anime is completed or already at max episodes
        val isCompleted = item.tracking.status.equals("completed", ignoreCase = true)
        val totalEp = item.metadata.totalEpisodes ?: 0
        val isMaxProgress = totalEp > 0 && item.tracking.progress >= totalEp
        if (isCompleted || isMaxProgress) return

        val updatedItem = item.copy(
            tracking = item.tracking.copy(
                progress = item.tracking.progress + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        val gachaMgr = gachaCreditManager ?: try { GachaCreditManager.getInstance(CanimApplication.instance) } catch (_: Exception) { null }
        val awarded = gachaMgr?.recordProgressAndAwardCredits(item.id, updatedItem.tracking.progress) ?: 0
        if (awarded > 0) {
            val newBal = gachaMgr?.getCredits() ?: (_uiState.value.gachaCredits + awarded)
            _uiState.update { it.copy(gachaCredits = newBal) }
            showSnackbar("+1 Episode ditambahkan! (+$awarded Tiket Gacha)")
        } else {
            showSnackbar("+1 Episode ditambahkan!")
        }

        // 1. Optimistic UI update
        val optimisticList = currentList.map { if (it.id == item.id) updatedItem else it }
        updateLibraryData(optimisticList, _uiState.value.mangaList)

        // 2. Dispatch to MAL API if logged in
        if (item.malId != null && _uiState.value.malUser.isLoggedIn) {
            viewModelScope.launch {
                val result = repository.updateAnimeTracking(item.malId!!, updatedItem.tracking)
                if (result.isFailure) {
                    // Revert optimistic update
                    updateLibraryData(currentList, _uiState.value.mangaList)
                    showSnackbar("Gagal update MAL: ${result.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
                }
            }
        }
    }

    fun quickDecrementAnime(identifier: Any) {
        val currentList = _uiState.value.animeList
        val item = findAnimeItem(identifier) ?: return
        if (item.tracking.progress <= 0) return
        val updatedItem = item.copy(
            tracking = item.tracking.copy(
                progress = item.tracking.progress - 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        val optimisticList = currentList.map { if (it.id == item.id) updatedItem else it }
        updateLibraryData(optimisticList, _uiState.value.mangaList)
        showSnackbar("-1 Episode dikurangkan!")

        if (item.malId != null && _uiState.value.malUser.isLoggedIn) {
            viewModelScope.launch {
                val result = repository.updateAnimeTracking(item.malId!!, updatedItem.tracking)
                if (result.isFailure) {
                    updateLibraryData(currentList, _uiState.value.mangaList)
                    showSnackbar("Gagal update MAL: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun quickIncrementManga(identifier: Any) {
        val currentList = _uiState.value.mangaList
        val item = findMangaItem(identifier) ?: return
        // Guard: Prevent increment if manga is completed or already at max chapters
        val isCompleted = item.tracking.status.equals("completed", ignoreCase = true)
        val totalCh = item.metadata.totalChapters ?: 0
        val isMaxProgress = totalCh > 0 && item.tracking.progress >= totalCh
        if (isCompleted || isMaxProgress) return

        val updatedItem = item.copy(
            tracking = item.tracking.copy(
                progress = item.tracking.progress + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        val gachaMgr = gachaCreditManager ?: try { GachaCreditManager.getInstance(CanimApplication.instance) } catch (_: Exception) { null }
        val awarded = gachaMgr?.recordProgressAndAwardCredits(item.id, updatedItem.tracking.progress) ?: 0
        if (awarded > 0) {
            val newBal = gachaMgr?.getCredits() ?: (_uiState.value.gachaCredits + awarded)
            _uiState.update { it.copy(gachaCredits = newBal) }
            showSnackbar("+1 Chapter ditambahkan! (+$awarded Tiket Gacha)")
        } else {
            showSnackbar("+1 Chapter ditambahkan!")
        }

        val optimisticList = currentList.map { if (it.id == item.id) updatedItem else it }
        updateLibraryData(_uiState.value.animeList, optimisticList)

        if (item.malId != null && _uiState.value.malUser.isLoggedIn) {
            viewModelScope.launch {
                val result = repository.updateMangaTracking(item.malId!!, updatedItem.tracking)
                if (result.isFailure) {
                    updateLibraryData(_uiState.value.animeList, currentList)
                    showSnackbar("Gagal update MAL: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun quickDecrementManga(identifier: Any) {
        val currentList = _uiState.value.mangaList
        val item = findMangaItem(identifier) ?: return
        if (item.tracking.progress <= 0) return
        val updatedItem = item.copy(
            tracking = item.tracking.copy(
                progress = item.tracking.progress - 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        val optimisticList = currentList.map { if (it.id == item.id) updatedItem else it }
        updateLibraryData(_uiState.value.animeList, optimisticList)
        showSnackbar("-1 Chapter dikurangkan!")

        if (item.malId != null && _uiState.value.malUser.isLoggedIn) {
            viewModelScope.launch {
                val result = repository.updateMangaTracking(item.malId!!, updatedItem.tracking)
                if (result.isFailure) {
                    updateLibraryData(_uiState.value.animeList, currentList)
                    showSnackbar("Gagal update MAL: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }


    // --- Flashcard Plan to Watch Save Flow ---
    fun saveFlashcardPlanToWatch(item: MediaItem, onResult: (Boolean) -> Unit) {
        val currentList = _uiState.value.animeList
        val isAlreadyInList = currentList.any { it.id == item.id }
        if (isAlreadyInList) {
            showSnackbar("\"${item.title}\" sudah ada di Library!")
            onResult(true)
            return
        }

        val identity = MediaRef(
            anilistId = item.anilistId,
            malId = item.malId
        )
        val metadata = MediaMetadata(
            title = item.title,
            titleEnglish = item.titleEnglish,
            titleNative = null,
            imageUrl = item.imageUrl,
            type = item.type,
            score = item.score,
            synopsis = item.synopsis,
            totalEpisodes = item.episodes ?: 0,
            totalChapters = item.chapters ?: 0,
            totalVolumes = item.volumes ?: 0,
            status = item.status ?: "Finished",
            year = item.year,
            season = item.season,
            genres = item.genres,
            format = item.format,
            studio = item.studio
        )
        val tracking = MalTracking(
            status = "plan_to_watch",
            score = 0,
            progress = 0,
            comments = "",
            updatedAt = System.currentTimeMillis()
        )
        val userItem = UserMediaItem(identity = identity, metadata = metadata, tracking = tracking)

        val optimisticList = currentList + userItem
        updateLibraryData(optimisticList, _uiState.value.mangaList)
        showSnackbar("Ditambahkan ke Rencana Ditonton")

        if (item.malId != null && _uiState.value.malUser.isLoggedIn) {
            viewModelScope.launch {
                val result = repository.updateAnimeTracking(item.malId, tracking)
                if (result.isFailure) {
                    updateLibraryData(currentList, _uiState.value.mangaList)
                    showSnackbar("Gagal menyimpan ke MAL: ${result.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
                    onResult(false)
                } else {
                    onResult(true)
                }
            }
        } else {
            onResult(true)
        }
    }

    // --- In-App Update Checker Methods ---
    private fun initUpdateChecker() {
        val prefs = try {
            CanimApplication.instance.getSharedPreferences("canim_update_prefs", Context.MODE_PRIVATE)
        } catch (_: Exception) { null }
        val isAutoEnabled = prefs?.getBoolean("auto_check_updates", true) ?: true
        _uiState.update { it.copy(isAutoUpdateCheckEnabled = isAutoEnabled) }

        if (isAutoEnabled) {
            val lastCheck = prefs?.getLong("last_update_check_time", 0L) ?: 0L
            val oneDayMs = 24L * 60L * 60L * 1000L
            if (System.currentTimeMillis() - lastCheck > oneDayMs) {
                checkForUpdates(manual = false)
            }
        }
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        try {
            val prefs = CanimApplication.instance.getSharedPreferences("canim_update_prefs", Context.MODE_PRIVATE)
            prefs?.edit()?.putBoolean("auto_check_updates", enabled)?.apply()
        } catch (_: Exception) {}
        _uiState.update { it.copy(isAutoUpdateCheckEnabled = enabled) }
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (_uiState.value.isCheckingUpdate) return
        _uiState.update { it.copy(isCheckingUpdate = true) }
        viewModelScope.launch {
            val result = UpdateChecker.checkLatestRelease(BuildConfig.VERSION_NAME)
            val info = result.getOrNull()
            if (info != null) {
                try {
                    val prefs = CanimApplication.instance.getSharedPreferences("canim_update_prefs", Context.MODE_PRIVATE)
                    prefs?.edit()?.putLong("last_update_check_time", System.currentTimeMillis())?.apply()
                } catch (_: Exception) {}

                if (info.isUpdateAvailable) {
                    _uiState.update { it.copy(updateInfo = info, isCheckingUpdate = false) }
                    if (manual) {
                        showSnackbar("Pembaruan tersedia: ${info.latestVersion}!")
                    }
                } else {
                    _uiState.update { it.copy(isCheckingUpdate = false) }
                    if (manual) {
                        showSnackbar("CA\'NIM sudah versi terbaru (${BuildConfig.VERSION_NAME})")
                    }
                }
            } else {
                _uiState.update { it.copy(isCheckingUpdate = false) }
                if (manual) {
                    showSnackbar("Gagal memeriksa pembaruan: ${result.exceptionOrNull()?.message ?: "Jaringan bermasalah"}")
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(updateInfo = null, isDownloadingUpdate = false, downloadedApkFile = null) }
    }

    fun startDownloadUpdate(context: Context) {
        val info = _uiState.value.updateInfo ?: return
        val downloadUrl = info.apkDownloadUrl ?: info.htmlUrl
        val apkName = info.apkName ?: "canim-release-${info.latestVersion}.apk"

        if (_uiState.value.isDownloadingUpdate) return

        _uiState.update {
            it.copy(
                isDownloadingUpdate = true,
                updateDownloadProgress = 0f,
                downloadedApkFile = null
            )
        }

        viewModelScope.launch {
            val result = UpdateChecker.downloadApk(
                context = context,
                downloadUrl = downloadUrl,
                fileName = apkName,
                onProgress = { progress ->
                    _uiState.update { it.copy(updateDownloadProgress = progress) }
                }
            )

            result.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadProgress = 1f,
                            downloadedApkFile = file
                        )
                    }
                    showSnackbar("Update berhasil diunduh! Membuka installer...")
                    installDownloadedUpdate(context)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadProgress = 0f
                        )
                    }
                    showSnackbar("Gagal mengunduh update: ${error.message}")
                }
            )
        }
    }

    fun installDownloadedUpdate(context: Context) {
        val file = _uiState.value.downloadedApkFile ?: return
        val result = UpdateChecker.installApk(context, file)
        if (result.isFailure) {
            showSnackbar("Gagal membuka installer APK: ${result.exceptionOrNull()?.message}")
        }
    }

    fun saveAnime(item: UserMediaItem) {
        val currentList = _uiState.value.animeList
        val exists = currentList.any { it.id == item.id }
        val optimisticList = if (exists) {
            currentList.map { if (it.id == item.id) item else it }
        } else {
            currentList + item
        }
        val gachaMgr = gachaCreditManager ?: try { GachaCreditManager.getInstance(CanimApplication.instance) } catch (_: Exception) { null }
        val awarded = gachaMgr?.recordProgressAndAwardCredits(item.id, item.tracking.progress) ?: 0
        if (awarded > 0) {
            val newBal = gachaMgr?.getCredits() ?: _uiState.value.gachaCredits
            _uiState.update { it.copy(gachaCredits = newBal) }
        }
        updateLibraryData(optimisticList, _uiState.value.mangaList)
        closeDetail()
        showSnackbar("Perubahan \"${item.title}\" disimpan!")

        if (item.malId != null && _uiState.value.malUser.isLoggedIn) {
            viewModelScope.launch {
                val result = repository.updateAnimeTracking(item.malId!!, item.tracking)
                if (result.isFailure) {
                    updateLibraryData(currentList, _uiState.value.mangaList)
                    showSnackbar("Gagal menyimpan ke MAL: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun saveManga(item: UserMediaItem) {
        val currentList = _uiState.value.mangaList
        val exists = currentList.any { it.id == item.id }
        val optimisticList = if (exists) {
            currentList.map { if (it.id == item.id) item else it }
        } else {
            currentList + item
        }
        val gachaMgr = gachaCreditManager ?: try { GachaCreditManager.getInstance(CanimApplication.instance) } catch (_: Exception) { null }
        val awarded = gachaMgr?.recordProgressAndAwardCredits(item.id, item.tracking.progress) ?: 0
        if (awarded > 0) {
            val newBal = gachaMgr?.getCredits() ?: _uiState.value.gachaCredits
            _uiState.update { it.copy(gachaCredits = newBal) }
        }
        updateLibraryData(_uiState.value.animeList, optimisticList)
        closeDetail()
        showSnackbar("Perubahan \"${item.title}\" disimpan!")

        if (item.malId != null && _uiState.value.malUser.isLoggedIn) {
            viewModelScope.launch {
                val result = repository.updateMangaTracking(item.malId!!, item.tracking)
                if (result.isFailure) {
                    updateLibraryData(_uiState.value.animeList, currentList)
                    showSnackbar("Gagal menyimpan ke MAL: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun deleteAnime(idOrItem: Any) {
        val currentList = _uiState.value.animeList
        val item = findAnimeItem(idOrItem) ?: return
        val optimisticList = currentList.filter { it.id != item.id }
        updateLibraryData(optimisticList, _uiState.value.mangaList)
        closeDetail()
        showSnackbar("\"${item.title}\" dihapus dari Library")

        if (item.malId != null && _uiState.value.malUser.isLoggedIn) {
            viewModelScope.launch {
                val result = repository.deleteAnimeTracking(item.malId!!)
                if (result.isFailure) {
                    updateLibraryData(currentList, _uiState.value.mangaList)
                    showSnackbar("Gagal menghapus dari MAL: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun deleteManga(idOrItem: Any) {
        val currentList = _uiState.value.mangaList
        val item = findMangaItem(idOrItem) ?: return
        val optimisticList = currentList.filter { it.id != item.id }
        updateLibraryData(_uiState.value.animeList, optimisticList)
        closeDetail()
        showSnackbar("\"${item.title}\" dihapus dari Library")

        if (item.malId != null && _uiState.value.malUser.isLoggedIn) {
            viewModelScope.launch {
                val result = repository.deleteMangaTracking(item.malId!!)
                if (result.isFailure) {
                    updateLibraryData(_uiState.value.animeList, currentList)
                    showSnackbar("Gagal menghapus dari MAL: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun addFromCatalog(item: MediaItem, status: MediaStatus) {
        val tracking = MalTracking(
            status = status.apiValue,
            score = 0,
            progress = if (status == MediaStatus.COMPLETED) (item.episodes ?: item.chapters ?: 0) else 0
        )
        val metadata = MediaMetadata(
            title = item.title,
            titleEnglish = item.titleEnglish,
            imageUrl = item.imageUrl,
            type = item.type,
            totalEpisodes = item.episodes,
            totalChapters = item.chapters,
            totalVolumes = item.volumes,
            status = item.status,
            genres = item.genres,
            format = item.format,
            studio = item.studio,
            year = item.year,
            season = item.season,
            synopsis = item.synopsis
        )
        val userItem = UserMediaItem(
            identity = item.identity,
            metadata = metadata,
            tracking = tracking
        )

        if (item.type == MediaType.ANIME) {
            saveAnime(userItem)
        } else {
            saveManga(userItem)
        }
    }

    private fun findAnimeItem(identifier: Any): UserMediaItem? {
        val list = _uiState.value.animeList
        return when (identifier) {
            is UserMediaItem -> identifier
            is MediaItem -> list.firstOrNull {
                (identifier.malId != null && it.malId == identifier.malId) ||
                (identifier.anilistId != null && it.anilistId == identifier.anilistId) ||
                it.title.equals(identifier.title, ignoreCase = true)
            }
            is String -> list.firstOrNull { it.id == identifier || it.malId?.toString() == identifier }
            is Int -> list.firstOrNull { it.malId == identifier || it.anilistId == identifier }
            else -> null
        }
    }

    private fun findMangaItem(identifier: Any): UserMediaItem? {
        val list = _uiState.value.mangaList
        return when (identifier) {
            is UserMediaItem -> identifier
            is MediaItem -> list.firstOrNull {
                (identifier.malId != null && it.malId == identifier.malId) ||
                (identifier.anilistId != null && it.anilistId == identifier.anilistId) ||
                it.title.equals(identifier.title, ignoreCase = true)
            }
            is String -> list.firstOrNull { it.id == identifier || it.malId?.toString() == identifier }
            is Int -> list.firstOrNull { it.malId == identifier || it.anilistId == identifier }
            else -> null
        }
    }

    // --- Search ---
    fun onSearchQueryChange(query: String, type: MediaType) {
        val typeChanged = _uiState.value.searchType != type
        _uiState.update {
            if (typeChanged) {
                it.copy(
                    searchQuery = query,
                    searchType = type,
                    searchGenres = emptyList(),
                    searchYear = null,
                    searchFormat = null
                )
            } else {
                it.copy(searchQuery = query, searchType = type)
            }
        }
        _searchQueryFlow.value = SearchTrigger(query, type)
    }

    fun setSearchType(type: MediaType) {
        if (_uiState.value.searchType != type) {
            _uiState.update {
                it.copy(
                    searchType = type,
                    searchGenres = emptyList(),
                    searchYear = null,
                    searchFormat = null
                )
            }
            _searchQueryFlow.value = SearchTrigger(_uiState.value.searchQuery, type)
        }
    }

    fun search(query: String, type: MediaType) {
        onSearchQueryChange(query, type)
    }

    fun applySearchFilters(genres: List<String>, year: Int?, format: String?) {
        _uiState.update {
            it.copy(
                searchGenres = genres,
                searchYear = year,
                searchFormat = format
            )
        }
        _searchQueryFlow.value = SearchTrigger(_uiState.value.searchQuery, _uiState.value.searchType)
    }

    fun resetSearchFilters() {
        _uiState.update {
            it.copy(
                searchGenres = emptyList(),
                searchYear = null,
                searchFormat = null
            )
        }
        _searchQueryFlow.value = SearchTrigger(_uiState.value.searchQuery, _uiState.value.searchType)
    }

    // --- Discover & Fixed Race-Safe Randomizer ---
    fun loadDiscoverCategory(
        category: DiscoverCategory,
        filter: DiscoverFilter = _uiState.value.discoverFilter,
        forceRefresh: Boolean = false
    ) {
        discoverJob?.cancel()
        val token = ++discoverRequestToken

        _uiState.update {
            it.copy(
                selectedDiscoverCategory = category,
                discoverFilter = filter,
                isDiscoverLoading = true,
                discoverPage = 1,
                canLoadMoreDiscover = true
            )
        }

        discoverJob = viewModelScope.launch(Dispatchers.IO) {
            val items = repository.getDiscoverMedia(category, filter, page = 1, forceRefresh = forceRefresh)
            if (token == discoverRequestToken) {
                _uiState.update {
                    it.copy(
                        discoverItems = items,
                        isDiscoverLoading = false,
                        canLoadMoreDiscover = items.size >= 20
                    )
                }
            }
        }
    }

    fun loadMoreDiscover() {
        val current = _uiState.value
        if (current.isDiscoverLoading || current.isDiscoverLoadingMore || !current.canLoadMoreDiscover) return
        val nextPage = current.discoverPage + 1
        val token = discoverRequestToken

        _uiState.update { it.copy(isDiscoverLoadingMore = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val nextItems = repository.getDiscoverMedia(
                current.selectedDiscoverCategory,
                current.discoverFilter,
                page = nextPage
            )

            if (token == discoverRequestToken) {
                val existingIds = current.discoverItems.map { it.malId ?: it.anilistId }.toSet()
                val newFiltered = nextItems.filter { !existingIds.contains(it.malId ?: it.anilistId) }

                _uiState.update {
                    it.copy(
                        discoverItems = it.discoverItems + newFiltered,
                        discoverPage = nextPage,
                        canLoadMoreDiscover = nextItems.isNotEmpty(),
                        isDiscoverLoadingMore = false
                    )
                }
            }
        }
    }

        // --- Flashcard Gacha System (v5.0.0) ---
    fun openFlashcard() {
        pushScreen(ScreenRoute.Flashcard)
        if (_uiState.value.flashcardDeck.isEmpty() && _uiState.value.gachaCredits > 0) {
            loadFlashcardDeck()
        }
    }

    fun consumeGachaCredit(): Boolean {
        val gachaMgr = gachaCreditManager ?: try { GachaCreditManager.getInstance(CanimApplication.instance) } catch (_: Exception) { null }
        val success = gachaMgr?.consumeCredit() ?: (_uiState.value.gachaCredits > 0)
        if (success) {
            val updated = gachaMgr?.getCredits() ?: (_uiState.value.gachaCredits - 1).coerceAtLeast(0)
            _uiState.update { it.copy(gachaCredits = updated) }
        }
        return success
    }

    fun swipeDismissFlashcard(item: MediaItem) {
        _uiState.update {
            val updatedDeck = it.flashcardDeck.filter { card -> card.id != item.id }
            it.copy(flashcardDeck = updatedDeck)
        }
        if (_uiState.value.flashcardDeck.isEmpty() && _uiState.value.gachaCredits > 0) {
            loadFlashcardDeck()
        }
    }

    fun loadFlashcardDeck() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isFlashcardLoading = true) }
            val currentSeason = repository.getDiscoverMedia(DiscoverCategory.CURRENT_SEASON, DiscoverFilter(), page = 1)
            val upcoming = repository.getDiscoverMedia(DiscoverCategory.UPCOMING, DiscoverFilter(), page = 1)
            val completedIds = _uiState.value.completedAnimeMalIds
            val libraryIds = _uiState.value.animeList.mapNotNull { it.malId }.toSet()
            var rawPool = (currentSeason + upcoming)
                .filter { item ->
                    val mId = item.malId
                    mId == null || (!completedIds.contains(mId) && !libraryIds.contains(mId))
                }
                .distinctBy { it.malId ?: it.anilistId }

            if (rawPool.isEmpty()) {
                val trending = repository.getDiscoverMedia(DiscoverCategory.TRENDING_NOW, DiscoverFilter(), page = 1)
                val topAnime = repository.getDiscoverMedia(DiscoverCategory.TOP_ANIME, DiscoverFilter(), page = 1)
                rawPool = (trending + topAnime)
                    .filter { item ->
                        val mId = item.malId
                        mId == null || (!completedIds.contains(mId) && !libraryIds.contains(mId))
                    }
                    .distinctBy { it.malId ?: it.anilistId }
            }

            if (rawPool.isEmpty()) {
                rawPool = repository.getDemoAnime().map { demo ->
                    MediaItem(
                        malId = demo.malId,
                        anilistId = demo.anilistId,
                        title = demo.title,
                        titleEnglish = demo.metadata.titleEnglish,
                        imageUrl = demo.imageUrl,
                        type = MediaType.ANIME,
                        score = demo.metadata.score,
                        synopsis = demo.synopsis,
                        episodes = demo.totalEpisodes,
                        status = demo.status,
                        year = demo.metadata.year,
                        genres = demo.metadata.genres,
                        studio = demo.studio
                    )
                }
            }

            val pool = rawPool.shuffled().take(15)

            _uiState.update {
                it.copy(
                    flashcardDeck = pool,
                    isFlashcardLoading = false
                )
            }
        }
    }

    // --- Centralized Screen Stack Navigation (v4.2.0) ---
    fun pushScreen(route: ScreenRoute) {
        _screenStack.update { it + route }
        syncStateWithRoute(route)
    }

    fun popScreen() {
        _screenStack.update { stack ->
            if (stack.isNotEmpty()) stack.dropLast(1) else stack
        }
        syncStateWithRoute(_screenStack.value.lastOrNull())
    }

    fun clearScreenStack() {
        _screenStack.value = emptyList()
        syncStateWithRoute(null)
    }

    private fun syncStateWithRoute(route: ScreenRoute?) {
        when (route) {
            is ScreenRoute.Detail -> {
                val item = route.item
                val type = route.type
                val localItem = if (type == MediaType.ANIME) findAnimeItem(item) else findMangaItem(item)
                var resolvedItem: Any = localItem ?: item

                val anilistId = when (resolvedItem) {
                    is UserMediaItem -> resolvedItem.anilistId
                    is MediaItem -> resolvedItem.anilistId
                    else -> null
                }
                val malId = when (resolvedItem) {
                    is UserMediaItem -> resolvedItem.malId
                    is MediaItem -> resolvedItem.malId
                    else -> null
                }

                val mediaKey = getMediaKey(resolvedItem)
                val inMemoryDetail = detailCache[mediaKey]
                val cachedDetail = inMemoryDetail
                    ?: repository.getCachedExtendedDetail(anilistId, malId, type)

                // Instant baseline synthesis (0ms): If cachedDetail is null, populate known fields immediately
                val initialDetail = cachedDetail ?: when (resolvedItem) {
                    is UserMediaItem -> ExtendedMediaDetail(
                        anilistId = resolvedItem.anilistId,
                        malId = resolvedItem.malId,
                        title = resolvedItem.title,
                        titleEnglish = resolvedItem.metadata.titleEnglish,
                        coverImage = resolvedItem.imageUrl,
                        synopsis = resolvedItem.synopsis,
                        studio = resolvedItem.metadata.studio,
                        source = resolvedItem.metadata.format,
                        airingStatus = resolvedItem.metadata.status,
                        genres = resolvedItem.metadata.genres,
                        malScore = if (resolvedItem.score > 0) resolvedItem.score.toDouble() else resolvedItem.metadata.score
                    )
                    is MediaItem -> ExtendedMediaDetail(
                        anilistId = resolvedItem.anilistId,
                        malId = resolvedItem.malId,
                        title = resolvedItem.title,
                        titleEnglish = resolvedItem.titleEnglish,
                        coverImage = resolvedItem.imageUrl,
                        synopsis = resolvedItem.synopsis,
                        studio = resolvedItem.studio,
                        source = resolvedItem.format,
                        airingStatus = resolvedItem.status,
                        genres = resolvedItem.genres,
                        malScore = null,
                        averageScore = resolvedItem.score?.toDouble()
                    )
                    else -> null
                }

                if (initialDetail != null) {
                    cacheDetail(resolvedItem, initialDetail)
                    cacheDetail(item, initialDetail)
                }

                _uiState.update {
                    it.copy(
                        selectedDetailItem = resolvedItem,
                        detailMediaType = type,
                        isDetailOpen = true,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = false,
                        isStatsOpen = false,
                        isAddTitleSheetOpen = false,
                        extendedDetail = initialDetail,
                        isLoadingExtendedDetail = cachedDetail == null
                    )
                }

                detailJob?.cancel()
                detailJob = viewModelScope.launch(Dispatchers.IO) {
                    // FAST PATH (Phase 1): Fetch AniList details immediately (cast, crew, rankings, recommendations)
                    val aniDetail = com.canim.app.data.remote.AniListClient.getExtendedDetails(anilistId, malId, type)
                    if (aniDetail != null) {
                        _uiState.update { current ->
                            val currentExt = current.extendedDetail
                            val mergedFast = aniDetail.copy(
                                malScore = currentExt?.malScore ?: aniDetail.malScore,
                                malRank = currentExt?.malRank ?: aniDetail.rank
                            )
                            cacheDetail(resolvedItem, mergedFast)
                            cacheDetail(item, mergedFast)
                            current.copy(
                                extendedDetail = mergedFast,
                                isLoadingExtendedDetail = false
                            )
                        }
                    }

                    // SECONDARY PATH (Phase 2): Asynchronously enrich with authoritative MAL details (score, rank, members)
                    val effectiveMalId = aniDetail?.malId ?: malId
                    val detail = repository.getExtendedDetails(anilistId, effectiveMalId, type)

                    // Final merge with authoritative MAL metrics
                    if (detail != null) {
                        cacheDetail(resolvedItem, detail)
                        cacheDetail(item, detail)
                        _uiState.update {
                            it.copy(
                                extendedDetail = detail,
                                isLoadingExtendedDetail = false
                            )
                        }
                    }

                    // Unified tracking resolution: if not in local library, fetch live MAL tracking asynchronously
                    if (resolvedItem !is UserMediaItem && effectiveMalId != null && _uiState.value.malUser.isLoggedIn) {
                        try {
                            val tracking = repository.getMalTrackingStatus(effectiveMalId, type)
                            if (tracking != null) {
                                val media = resolvedItem as? MediaItem
                                val itemTitle = media?.title ?: detail?.title ?: ""
                                val itemImageUrl = media?.imageUrl ?: detail?.coverImage ?: ""
                                val metadata = MediaMetadata(
                                    title = itemTitle,
                                    titleEnglish = media?.titleEnglish ?: detail?.titleEnglish,
                                    titleNative = detail?.nativeTitle,
                                    imageUrl = itemImageUrl,
                                    type = type,
                                    score = detail?.malScore ?: media?.score,
                                    synopsis = media?.synopsis ?: detail?.synopsis,
                                    totalEpisodes = media?.episodes,
                                    totalChapters = media?.chapters,
                                    status = media?.status ?: detail?.airingStatus,
                                    year = media?.year ?: detail?.startDate?.take(4)?.toIntOrNull(),
                                    season = media?.season,
                                    genres = if (media?.genres?.isNotEmpty() == true) media.genres else (detail?.genres ?: emptyList()),
                                    format = media?.format ?: detail?.source,
                                    studio = media?.studio ?: detail?.studio
                                )
                                val newUserItem = UserMediaItem(
                                    identity = MediaRef(
                                        anilistId = anilistId ?: detail?.anilistId,
                                        malId = effectiveMalId
                                    ),
                                    metadata = metadata,
                                    tracking = tracking
                                )
                                resolvedItem = newUserItem
                                _uiState.update {
                                    it.copy(selectedDetailItem = newUserItem)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            is ScreenRoute.CastCrew -> {
                _uiState.update {
                    it.copy(
                        selectedDetailItem = null,
                        isDetailOpen = false,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = true,
                        isStatsOpen = false,
                        isAddTitleSheetOpen = false
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val profile = if (route.isStaff) {
                        repository.getStaffProfile(route.id)
                    } else {
                        repository.getCharacterProfile(route.id)
                    }
                    _uiState.update {
                        it.copy(
                            selectedCastCrewProfile = profile,
                            isLoadingCastCrewProfile = false
                        )
                    }
                }
            }
            is ScreenRoute.FullCastList -> {
                _uiState.update {
                    it.copy(
                        isStatsOpen = false,
                        isAddTitleSheetOpen = false
                    )
                }
            }
            is ScreenRoute.Stats -> {
                _uiState.update {
                    it.copy(
                        isStatsOpen = true,
                        isDetailOpen = false,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = false,
                        isAddTitleSheetOpen = false
                    )
                }
            }
            is ScreenRoute.AddTitleSheet -> {
                _uiState.update {
                    it.copy(
                        isAddTitleSheetOpen = true,
                        isStatsOpen = false,
                        isDetailOpen = false,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = false
                    )
                }
            }
            is ScreenRoute.Flashcard -> {
                _uiState.update {
                    it.copy(
                        isStatsOpen = false,
                        isAddTitleSheetOpen = false,
                        isDetailOpen = false
                    )
                }
                if (_uiState.value.flashcardDeck.isEmpty() && _uiState.value.gachaCredits > 0) {
                    loadFlashcardDeck()
                }
            }
            is ScreenRoute.StudioFilmography -> {
                loadStudioFilmography(route.studioId, route.studioName, 1)
            }
            null -> {
                detailJob?.cancel()
                studioJob?.cancel()
                _uiState.update {
                    it.copy(
                        isDetailOpen = false,
                        isLoadingExtendedDetail = false,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = false,
                        isStatsOpen = false,
                        isAddTitleSheetOpen = false,
                        studioFilmographyStudioId = null,
                        studioFilmographyStudioName = "",
                        studioFilmographyBio = null,
                        studioFilmographyItems = emptyList(),
                        studioFilmographyTotalEntries = 0,
                        isStudioFilmographyLoading = false,
                        isStudioFilmographyLoadingMore = false,
                        studioFilmographyPage = 1,
                        canLoadMoreStudioFilmography = true
                    )
                }
            }
        }
    }

    // --- Cast & Crew Bio Navigation ---
    fun openCastCrewProfile(id: Int, isStaff: Boolean) {
        pushScreen(ScreenRoute.CastCrew(id, isStaff))
    }

    fun closeCastCrewProfile() {
        if (_screenStack.value.lastOrNull() is ScreenRoute.CastCrew) {
            popScreen()
        } else {
            _uiState.update {
                it.copy(
                    selectedCastCrewProfile = null,
                    isLoadingCastCrewProfile = false
                )
            }
        }
    }

    // --- Stats Screen Navigation ---
    fun openStats() {
        resetStatsScrollPosition()
        pushScreen(ScreenRoute.Stats)
    }

    fun closeStats() {
        if (_screenStack.value.lastOrNull() is ScreenRoute.Stats) {
            popScreen()
        } else {
            _uiState.update { it.copy(isStatsOpen = false) }
        }
    }

    // --- Add Title Modal Sheet ---
    fun openAddTitleSheet() {
        pushScreen(ScreenRoute.AddTitleSheet)
    }

    fun closeAddTitleSheet() {
        if (_screenStack.value.lastOrNull() is ScreenRoute.AddTitleSheet) {
            popScreen()
        } else {
            _uiState.update { it.copy(isAddTitleSheetOpen = false) }
        }
    }

    // --- Full Cast & Crew List Navigation ---
    fun openFullCastList(
        mediaTitle: String,
        castList: List<CharacterCastItem>,
        staffList: List<StaffMemberItem>,
        isCrewInitial: Boolean = false
    ) {
        pushScreen(ScreenRoute.FullCastList(mediaTitle, castList, staffList, isCrewInitial))
    }

    // --- Studio Filmography (v5.0.0) ---
    fun openStudio(studioId: Int, studioName: String) {
        val bio = try { StudioBioRegistry.getStudioInfo(studioId, studioName) } catch (_: Exception) { null }
        _uiState.update {
            it.copy(
                studioFilmographyBio = bio,
                studioFilmographySort = StudioFilmographySort.YEAR_DESC
            )
        }
        pushScreen(ScreenRoute.StudioFilmography(studioId, studioName))
    }

    fun setStudioFilmographySort(sort: StudioFilmographySort) {
        _uiState.update { it.copy(studioFilmographySort = sort) }
        val sId = _uiState.value.studioFilmographyStudioId
        val sName = _uiState.value.studioFilmographyStudioName
        if (sId != null) {
            loadStudioFilmography(sId, sName, page = 1)
        }
    }

    // --- Studio Live Search (v5.1.1) ---
    fun searchStudios(query: String) {
        studioSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    studioSearchResults = emptyList(),
                    isSearchingStudios = false
                )
            }
            return
        }

        // 1. Instant 0ms local match from curated registry
        val localMatches = try {
            StudioBioRegistry.searchCuratedStudios(trimmed)
        } catch (_: Exception) {
            emptyList()
        }
        _uiState.update {
            it.copy(
                studioSearchResults = localMatches,
                isSearchingStudios = true
            )
        }

        // 2. Query global AniList database in background with light debounce
        studioSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250L)
            val remoteResults = try {
                repository.searchStudios(trimmed)
            } catch (_: Exception) {
                emptyList()
            }

            // Merge local and remote, deduplicated by studioId
            val merged = (localMatches + remoteResults).distinctBy { it.studioId }

            _uiState.update {
                it.copy(
                    studioSearchResults = merged,
                    isSearchingStudios = false
                )
            }
        }
    }

    fun clearStudioSearch() {
        studioSearchJob?.cancel()
        _uiState.update {
            it.copy(
                studioSearchResults = emptyList(),
                isSearchingStudios = false
            )
        }
    }

    fun closeStudio() {
        studioJob?.cancel()
        if (_screenStack.value.lastOrNull() is ScreenRoute.StudioFilmography) {
            popScreen()
        } else {
            _uiState.update {
                it.copy(
                    studioFilmographyStudioId = null,
                    studioFilmographyStudioName = "",
                    studioFilmographyBio = null,
                    studioFilmographyItems = emptyList(),
                    studioFilmographyTotalEntries = 0,
                    isStudioFilmographyLoading = false,
                    isStudioFilmographyLoadingMore = false,
                    studioFilmographyPage = 1,
                    canLoadMoreStudioFilmography = true
                )
            }
        }
    }

    fun loadStudioFilmography(studioId: Int, studioName: String, page: Int = 1) {
        if (page == 1) {
            studioJob?.cancel()
            val bio = _uiState.value.studioFilmographyBio
                ?: try { StudioBioRegistry.getStudioInfo(studioId, studioName) } catch (_: Exception) { null }
            _uiState.update {
                it.copy(
                    studioFilmographyStudioId = studioId,
                    studioFilmographyStudioName = studioName,
                    studioFilmographyBio = bio,
                    isStudioFilmographyLoading = true,
                    studioFilmographyItems = emptyList(),
                    studioFilmographyTotalEntries = 0,
                    studioFilmographyPage = 1,
                    canLoadMoreStudioFilmography = true
                )
            }
        } else {
            _uiState.update { it.copy(isStudioFilmographyLoadingMore = true) }
        }

        studioJob = viewModelScope.launch(Dispatchers.IO) {
            val sort = _uiState.value.studioFilmographySort
            val pageResult = repository.getStudioFilmography(studioId = studioId, page = page, sort = sort)
            _uiState.update { currentState ->
                val newItems = if (page == 1) {
                    pageResult?.items ?: emptyList()
                } else {
                    val existingIds = currentState.studioFilmographyItems.map { item -> item.id }.toSet()
                    val added = (pageResult?.items ?: emptyList()).filter { item -> item.id !in existingIds }
                    currentState.studioFilmographyItems + added
                }
                val totalEntries = if (page == 1) (pageResult?.total ?: 0) else currentState.studioFilmographyTotalEntries
                val updatedBio = currentState.studioFilmographyBio?.let { currBio ->
                    currBio.copy(
                        totalAnime = if (totalEntries > 0) totalEntries else currBio.totalAnime,
                        officialSite = pageResult?.siteUrl ?: currBio.officialSite,
                        favourites = pageResult?.favourites ?: currBio.favourites
                    )
                }
                currentState.copy(
                    studioFilmographyBio = updatedBio ?: currentState.studioFilmographyBio,
                    studioFilmographyItems = newItems,
                    studioFilmographyTotalEntries = if (totalEntries > 0) totalEntries else newItems.size,
                    isStudioFilmographyLoading = false,
                    isStudioFilmographyLoadingMore = false,
                    studioFilmographyPage = page,
                    canLoadMoreStudioFilmography = pageResult?.hasNextPage ?: false
                )
            }
        }
    }

    fun loadMoreStudioFilmography() {
        val s = _uiState.value
        if (s.isStudioFilmographyLoading || s.isStudioFilmographyLoadingMore || !s.canLoadMoreStudioFilmography) return
        val studioId = s.studioFilmographyStudioId ?: return
        loadStudioFilmography(studioId, s.studioFilmographyStudioName, s.studioFilmographyPage + 1)
    }

    // --- Details ---
    fun openDetail(item: Any, type: MediaType) {
        pushScreen(ScreenRoute.Detail(item, type))
    }

    fun closeDetail() {
        if (_screenStack.value.lastOrNull() is ScreenRoute.Detail) {
            popScreen()
        } else {
            detailJob?.cancel()
            _uiState.update {
                it.copy(
                    selectedDetailItem = null,
                    isDetailOpen = false,
                    extendedDetail = null,
                    isLoadingExtendedDetail = false
                )
            }
        }
    }

    // --- MAL OAuth & Sync ---
    fun loginWithMal(context: Context) {
        try {
            val url = repository.buildMalAuthorizeUrl()
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            showSnackbar("Gagal membuka browser untuk login: ${e.message}")
        }
    }

    fun handleOAuthCallback(code: String, state: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExchangingToken = true) }
            val result = repository.handleMalOAuthCallback(code, state)
            if (result.isSuccess) {
                val user = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        malUser = user,
                        appMode = "online_sync",
                        isExchangingToken = false
                    )
                }
                showSnackbar("Login MAL berhasil! Memuat library...")
                loadUserLibrary(forceRefresh = true)
            } else {
                _uiState.update { it.copy(isExchangingToken = false) }
                showSnackbar("Gagal login MyAnimeList: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun logoutMal() {
        repository.logoutMal()
        _uiState.update {
            it.copy(
                malUser = MalUser(),
                appMode = "offline"
            )
        }
        showSnackbar("Akun MyAnimeList telah logout.")
        // Revert to demo data
        updateLibraryData(repository.getDemoAnime(), repository.getDemoManga())
    }

    fun syncWithMal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingMal = true) }
            val result = repository.syncWithMal()
            _uiState.update { it.copy(isSyncingMal = false) }
            if (result.isSuccess) {
                showSnackbar("Sync MAL selesai: ${result.animeSynced} anime & ${result.mangaSynced} manga")
                loadUserLibrary(forceRefresh = true)
            } else {
                showSnackbar("Sync gagal: ${result.errorMessage}")
            }
        }
    }

    fun setAppMode(mode: String) {
        _uiState.update { it.copy(appMode = mode) }
    }

    fun loadDemoData() {
        updateLibraryData(repository.getDemoAnime(), repository.getDemoManga())
        showSnackbar("Dataset demo dimuat!")
    }

    fun clearAllData() {
        updateLibraryData(emptyList(), emptyList())
        showSnackbar("Daftar tampilan telah dibersihkan.")
    }

    fun clearImageCache(context: Context) {
        viewModelScope.launch {
            repository.clearImageCache(context)
            showSnackbar("Cache gambar telah dibersihkan.")
        }
    }

    fun clearMetadataCache() {
        repository.clearMetadataCache()
        showSnackbar("Cache query & metadata telah dibersihkan.")
    }

    fun clearAllCache(context: Context) {
        viewModelScope.launch {
            repository.clearAllCache(context)
            showSnackbar("Semua cache berhasil dibersihkan.")
        }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

class CanimViewModelFactory(
    private val repository: CanimRepository,
    private val gachaCreditManager: GachaCreditManager? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CanimViewModel::class.java)) {
            return CanimViewModel(repository, gachaCreditManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
