package com.canim.app.ui.viewmodel

import android.content.Context
import android.util.Log
import com.canim.app.util.LogRedactor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.canim.app.data.model.*
import com.canim.app.domain.repository.CanimRepositoryContract
import com.canim.app.data.repository.CacheRefreshType
import com.canim.app.data.cache.CacheManager
import com.canim.app.CanimApplication
import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.repository.StudioBioRegistry
import com.canim.app.ui.navigation.ScreenRoute
import com.canim.app.BuildConfig
import com.canim.app.data.remote.UpdateChecker
import com.canim.app.data.remote.UpdateInfo
import androidx.compose.runtime.Immutable
import com.canim.app.ui.viewmodel.detail.DetailEvent
import com.canim.app.ui.viewmodel.detail.DetailUiState
import com.canim.app.ui.viewmodel.discover.DiscoverEvent
import com.canim.app.ui.viewmodel.discover.DiscoverUiState
import com.canim.app.ui.viewmodel.gacha.GachaEvent
import com.canim.app.ui.viewmodel.gacha.GachaUiState
import com.canim.app.ui.viewmodel.global.GlobalEvent
import com.canim.app.ui.viewmodel.global.GlobalUiState
import com.canim.app.ui.viewmodel.library.LibraryEvent
import com.canim.app.ui.viewmodel.library.LibraryUiState
import com.canim.app.ui.viewmodel.search.SearchEvent
import com.canim.app.ui.viewmodel.search.SearchUiState
import com.canim.app.ui.viewmodel.studio.StudioEvent
import com.canim.app.ui.viewmodel.studio.StudioUiState
import com.canim.app.ui.viewmodel.update.UpdateEvent
import com.canim.app.ui.viewmodel.update.UpdateUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CanimViewModel @Inject constructor(
    private val repository: CanimRepositoryContract,
    private val gachaCreditManager: GachaCreditManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CanimUiState(
            malUser = repository.getMalUser(),
            appMode = if (repository.getMalUser().isLoggedIn) "online_sync" else "offline"
        )
    )
    val uiState: StateFlow<CanimUiState> = _uiState.asStateFlow()

    // Isolated Search Feature State
    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    // Isolated Discover Feature State
    private val _discoverState = MutableStateFlow(DiscoverUiState())
    val discoverState: StateFlow<DiscoverUiState> = _discoverState.asStateFlow()

    // Isolated Detail Feature State
    private val _detailState = MutableStateFlow(DetailUiState())
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    // Isolated Library Feature State
    private val _libraryState = MutableStateFlow(LibraryUiState())
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

    // Isolated Gacha Feature State
    private val _gachaState = MutableStateFlow(GachaUiState())
    val gachaState: StateFlow<GachaUiState> = _gachaState.asStateFlow()

    // Isolated Studio Feature State
    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    // Isolated AppUpdate Feature State
    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    // Isolated Global App State (Auth, Health, Navigation)
    private val _globalState = MutableStateFlow(
        GlobalUiState(
            malUser = repository.getMalUser(),
            appMode = if (repository.getMalUser().isLoggedIn) "online_sync" else "offline"
        )
    )
    val globalState: StateFlow<GlobalUiState> = _globalState.asStateFlow()

    // Centralized Navigation Back Stack (ScreenRoute)
    private val _screenStack = MutableStateFlow<List<ScreenRoute>>(emptyList())
    val screenStack: StateFlow<List<ScreenRoute>> = _screenStack.asStateFlow()

    // ── Rate-limiter transient notifications ────────────────────────────────────
    // Collects LimiterEvent from both AniList and MAL limiters and surfaces them as
    // short Snackbar messages. These are informational — NOT errors.
    init {
        viewModelScope.launch {
            merge(
                com.canim.app.data.remote.ApiClient.aniListLimiter.eventFlow,
                com.canim.app.data.remote.ApiClient.malLimiter.eventFlow
            ).collect { event ->
                val displayHost = when (event) {
                    is com.canim.app.data.remote.LimiterEvent.Throttled ->
                        if (event.host == "anilist") "AniList" else "MyAnimeList"
                    is com.canim.app.data.remote.LimiterEvent.CooldownStarted ->
                        if (event.host == "anilist") "AniList" else "MyAnimeList"
                    is com.canim.app.data.remote.LimiterEvent.Retrying ->
                        if (event.host == "anilist") "AniList" else "MyAnimeList"
                    is com.canim.app.data.remote.LimiterEvent.Recovered ->
                        if (event.host == "anilist") "AniList" else "MyAnimeList"
                }
                val msg = when (event) {
                    is com.canim.app.data.remote.LimiterEvent.Throttled ->
                        "⏳ Memperlambat permintaan ke $displayHost..."
                    is com.canim.app.data.remote.LimiterEvent.CooldownStarted ->
                        "⏳ Menunggu API $displayHost siap..."
                    is com.canim.app.data.remote.LimiterEvent.Retrying ->
                        "🔄 Mencoba ulang ke $displayHost..."
                    is com.canim.app.data.remote.LimiterEvent.Recovered ->
                        "✅ Koneksi ke $displayHost pulih."
                }
                showSnackbar(msg)
            }
        }
    }
    // ────────────────────────────────────────────────────────────────────────────

    // Reactive search flow with unique trigger token to avoid StateFlow conflation on filter changes
    data class SearchTrigger(
        val query: String,
        val type: MediaType,
        val token: Long = System.nanoTime(),
        val forceRefresh: Boolean = false
    )
    private val _searchQueryFlow = MutableStateFlow(SearchTrigger("", MediaType.ANIME))

    private var discoverJob: Job? = null
    private var discoverRequestToken = 0L
    private var detailJob: Job? = null
    private var detailRequestToken = 0L
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
        val currentCredits = gachaCreditManager.getCredits()
        _gachaState.update { it.copy(credits = currentCredits) }
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
                        val searchState = _searchState.value
                        val hasFilters = searchState.genres.isNotEmpty() || searchState.year != null || searchState.format != null
                        if (trimmed.length < 2 && !hasFilters) {
                            emit(emptyList<MediaItem>())
                        } else {
                            _searchState.update { it.copy(isSearching = true) }
                            _uiState.update { it.copy(isSearching = true) }
                            val results = if (type == MediaType.ANIME) {
                                repository.searchAnime(trimmed, searchState.genres, searchState.year, searchState.format, forceRefresh = trigger.forceRefresh)
                            } else {
                                repository.searchManga(trimmed, searchState.genres, searchState.year, searchState.format, forceRefresh = trigger.forceRefresh)
                            }
                            emit(results)
                        }
                    }
                }
                .flowOn(Dispatchers.IO)
                .collect { results ->
                    _searchState.update { it.copy(results = results, isSearching = false) }
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

        // Observe background SWR cache refresh events and update active UI
        viewModelScope.launch {
            repository.cacheRefreshEvents.collect { event ->
                when (event.type) {
                    CacheRefreshType.SEARCH -> {
                        val searchState = _searchState.value
                        val trimmed = searchState.query.trim()
                        val type = searchState.type.name
                        val genres = searchState.genres
                        val year = searchState.year
                        val format = searchState.format
                        val filterKey = repository.searchFilterKey(trimmed, genres, year, format)
                        val searchKey = CacheManager.searchKey(filterKey, type)
                        if (event.key == searchKey || event.key == filterKey) {
                            val fresh = CacheManager.getSearch(filterKey, type)
                            if (fresh != null) {
                                _searchState.update { it.copy(results = fresh) }
                                _uiState.update { it.copy(searchResults = fresh) }
                            }
                        }
                    }
                    CacheRefreshType.DISCOVER -> {
                        val token = discoverRequestToken
                        val discoverState = _discoverState.value
                        val categoryKey = repository.discoverFilterKey(discoverState.selectedCategory, discoverState.filter, page = 1)
                        val discoverKey = CacheManager.discoverKey(categoryKey)
                        if (event.key == discoverKey || event.key == categoryKey) {
                            val fresh = CacheManager.getDiscover(categoryKey)
                            if (fresh != null && token == discoverRequestToken) {
                                _discoverState.update {
                                    it.copy(
                                        items = fresh,
                                        canLoadMore = fresh.size >= 20
                                    )
                                }
                                _uiState.update {
                                    it.copy(
                                        discoverItems = fresh,
                                        canLoadMoreDiscover = fresh.size >= 20
                                    )
                                }
                            }
                        }
                    }
                    CacheRefreshType.DETAIL -> {
                        val token = detailRequestToken
                        val state = _uiState.value
                        if (state.isDetailOpen && token == detailRequestToken) {
                            val selected = state.selectedDetailItem
                            val ext = state.extendedDetail
                            val aniId = ext?.anilistId ?: (selected as? MediaItem)?.anilistId ?: (selected as? UserMediaItem)?.anilistId
                            val malId = ext?.malId ?: (selected as? MediaItem)?.malId ?: (selected as? UserMediaItem)?.malId
                            val matchesDetail = (aniId != null && event.key == CacheManager.detailKey(aniId, malId))
                                || (aniId != null && event.key == CacheManager.detailKey(aniId, null))
                                || (malId != null && event.key == CacheManager.detailKey(null, malId))
                                || (aniId != null && event.key.contains("ani_$aniId"))
                                || (malId != null && event.key.contains("mal_$malId"))
                            if (matchesDetail) {
                                val fresh = CacheManager.getDetail(event.key)
                                if (fresh != null && token == detailRequestToken) {
                                    selected?.let { cacheDetail(it, fresh) }
                                    _detailState.update {
                                        it.copy(
                                            extendedDetail = fresh,
                                            isLoadingExtendedDetail = false
                                        )
                                    }
                                    _uiState.update {
                                        it.copy(
                                            extendedDetail = fresh,
                                            isLoadingExtendedDetail = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun checkApiHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            val aniDown = repository.isAniListUnavailable()
            val malDown = repository.isMalUnavailable()
            _uiState.update { it.copy(isAniListDown = aniDown, isMalDown = malDown) }
            _globalState.update { it.copy(isAniListDown = aniDown, isMalDown = malDown) }
        }
    }

    /**
     * Loads the authoritative library from MAL in parallel (or demo dataset if not logged in).
     */
    fun loadUserLibrary(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val user = repository.getMalUser()
            _libraryState.update { it.copy(isLoading = true) }
            _uiState.update { it.copy(isLoadingLibrary = true) }
            if (user.isLoggedIn) {
                // SWR: If cached items exist and synced within 30 minutes, skip network on startup unless forced
                val hasCachedData = _uiState.value.animeList.isNotEmpty() || _uiState.value.mangaList.isNotEmpty()
                val lastSynced = repository.getLastSyncedTime()
                val isCacheFresh = (System.currentTimeMillis() - lastSynced) < 30 * 60 * 1000L
                if (!forceRefresh && hasCachedData && isCacheFresh) {
                    _libraryState.update { it.copy(isLoading = false) }
                    _uiState.update { it.copy(isLoadingLibrary = false, syncStatus = SyncStatus.IDLE) }
                    _globalState.update { it.copy(syncStatus = SyncStatus.IDLE) }
                    return@launch
                }

                _uiState.update { it.copy(isLoadingLibrary = true, syncStatus = SyncStatus.SYNCING) }
                _globalState.update { it.copy(syncStatus = SyncStatus.SYNCING) }
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
                animes.forEach { gachaCreditManager.initBaselineProgress(it.id, it.progress) }
                mangas.forEach { gachaCreditManager.initBaselineProgress(it.id, it.progress) }

                updateLibraryData(animes, mangas)
                _libraryState.update { it.copy(isLoading = false) }
                _uiState.update { it.copy(isLoadingLibrary = false, syncStatus = finalSyncStatus) }
                _globalState.update { it.copy(syncStatus = finalSyncStatus) }

                if (finalSyncStatus == SyncStatus.SUCCESS) {
                    launch {
                        delay(3000L)
                        _uiState.update { if (it.syncStatus == SyncStatus.SUCCESS) it.copy(syncStatus = SyncStatus.IDLE) else it }
                        _globalState.update { if (it.syncStatus == SyncStatus.SUCCESS) it.copy(syncStatus = SyncStatus.IDLE) else it }
                    }
                }
            } else {
                // In-memory demo data for unauthenticated mode
                if (_uiState.value.animeList.isEmpty() && _uiState.value.mangaList.isEmpty()) {
                    updateLibraryData(repository.getDemoAnime(), repository.getDemoManga())
                }
                _libraryState.update { it.copy(isLoading = false) }
                _uiState.update { it.copy(isLoadingLibrary = false, syncStatus = SyncStatus.IDLE) }
                _globalState.update { it.copy(syncStatus = SyncStatus.IDLE) }
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
                _libraryState.update { current ->
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

    // --- Library Feature Events & State Operations ---
    fun onLibraryEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SetFilterType -> {
                _libraryState.update { it.copy(filterType = event.type) }
                _uiState.update { it.copy(libraryFilterType = event.type) }
            }
            is LibraryEvent.SetStatusFilter -> {
                _libraryState.update { it.copy(statusFilter = event.status) }
                _uiState.update { it.copy(libraryStatusFilter = event.status) }
            }
            is LibraryEvent.SetSearchQuery -> {
                _libraryState.update { it.copy(searchQuery = event.query) }
                _uiState.update { it.copy(librarySearchQuery = event.query) }
            }
            is LibraryEvent.SetSortBy -> {
                _libraryState.update { it.copy(sortBy = event.sort) }
                _uiState.update { it.copy(librarySortBy = event.sort) }
            }
            is LibraryEvent.SaveAnime -> {
                saveAnime(event.item)
            }
            is LibraryEvent.SaveManga -> {
                saveManga(event.item)
            }
            is LibraryEvent.DeleteAnime -> {
                deleteAnime(event.animeId)
            }
            is LibraryEvent.DeleteManga -> {
                deleteManga(event.mangaId)
            }
            is LibraryEvent.IncrementProgress -> {
                quickIncrementAnime(event.identifier)
            }
            is LibraryEvent.ReloadLibrary -> {
                loadUserLibrary(forceRefresh = true)
            }
        }
    }

    // --- Navigation & Filter Controls ---
    fun setTab(tab: String) {
        clearScreenStack()
        _uiState.update { it.copy(activeTab = tab) }
        _globalState.update { it.copy(activeTab = tab) }
        if (tab == "discover" && _uiState.value.discoverItems.isEmpty() && !_uiState.value.isDiscoverLoading) {
            loadDiscoverCategory(_uiState.value.selectedDiscoverCategory, _uiState.value.discoverFilter)
        }
    }

    fun setLibraryFilterType(type: MediaType) {
        onLibraryEvent(LibraryEvent.SetFilterType(type))
    }

    fun setLibraryStatusFilter(status: String?) {
        onLibraryEvent(LibraryEvent.SetStatusFilter(status))
    }

    fun setLibrarySearch(query: String) {
        onLibraryEvent(LibraryEvent.SetSearchQuery(query))
    }

    fun setLibrarySort(sort: String) {
        onLibraryEvent(LibraryEvent.SetSortBy(sort))
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
        val awarded = gachaCreditManager.recordProgressAndAwardCredits(item.id, updatedItem.tracking.progress)
        if (awarded > 0) {
            val newBal = gachaCreditManager.getCredits()
            _gachaState.update { it.copy(credits = newBal) }
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
        val awarded = gachaCreditManager.recordProgressAndAwardCredits(item.id, updatedItem.tracking.progress)
        if (awarded > 0) {
            val newBal = gachaCreditManager.getCredits()
            _gachaState.update { it.copy(credits = newBal) }
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
    fun onUpdateEvent(event: UpdateEvent) {
        when (event) {
            is UpdateEvent.CheckForUpdates -> checkForUpdates(manual = event.manual)
            is UpdateEvent.SetAutoUpdateCheck -> setAutoUpdateCheck(enabled = event.enabled)
            is UpdateEvent.DismissDialog -> dismissUpdateDialog()
            is UpdateEvent.StartDownload -> startDownloadUpdate(context = event.context)
            is UpdateEvent.InstallUpdate -> installDownloadedUpdate(context = event.context)
        }
    }

    private fun initUpdateChecker() {
        val prefs = try {
            CanimApplication.instance.getSharedPreferences("canim_update_prefs", Context.MODE_PRIVATE)
        } catch (_: Exception) { null }
        val isAutoEnabled = prefs?.getBoolean("auto_check_updates", true) ?: true
        _uiState.update { it.copy(isAutoUpdateCheckEnabled = isAutoEnabled) }
        _updateState.update { it.copy(isAutoCheckEnabled = isAutoEnabled) }

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
        _updateState.update { it.copy(isAutoCheckEnabled = enabled) }
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (_uiState.value.isCheckingUpdate || _updateState.value.isChecking) return
        _uiState.update { it.copy(isCheckingUpdate = true) }
        _updateState.update { it.copy(isChecking = true) }
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
                    _updateState.update { it.copy(updateInfo = info, isChecking = false) }
                    if (manual) {
                        showSnackbar("Pembaruan tersedia: ${info.latestVersion}!")
                    }
                } else {
                    _uiState.update { it.copy(isCheckingUpdate = false) }
                    _updateState.update { it.copy(isChecking = false) }
                    if (manual) {
                        showSnackbar("CA\'NIM sudah versi terbaru (${BuildConfig.VERSION_NAME})")
                    }
                }
            } else {
                _uiState.update { it.copy(isCheckingUpdate = false) }
                _updateState.update { it.copy(isChecking = false) }
                if (manual) {
                    val rawMsg = result.exceptionOrNull()?.message ?: "Jaringan bermasalah"
                    val userMsg = if (rawMsg.contains("403") || rawMsg.contains("rate limit", ignoreCase = true)) {
                        "Batas permintaan GitHub terlampaui. Coba beberapa saat lagi."
                    } else {
                        rawMsg
                    }
                    showSnackbar("Gagal memeriksa pembaruan: $userMsg")
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(updateInfo = null, isDownloadingUpdate = false, downloadedApkFile = null) }
        _updateState.update { it.copy(updateInfo = null, isDownloading = false, downloadedApkFile = null) }
    }

    fun startDownloadUpdate(context: Context) {
        val info = _updateState.value.updateInfo ?: _uiState.value.updateInfo ?: return
        val downloadUrl = info.apkDownloadUrl ?: info.htmlUrl
        val apkName = info.apkName ?: "canim-release-${info.latestVersion}.apk"

        if (_uiState.value.isDownloadingUpdate || _updateState.value.isDownloading) return

        _uiState.update {
            it.copy(
                isDownloadingUpdate = true,
                updateDownloadProgress = 0f,
                downloadedApkFile = null
            )
        }
        _updateState.update {
            it.copy(
                isDownloading = true,
                downloadProgress = 0f,
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
                    _updateState.update { it.copy(downloadProgress = progress) }
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
                    _updateState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
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
                    _updateState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 0f
                        )
                    }
                    showSnackbar("Gagal mengunduh update: ${error.message}")
                }
            )
        }
    }

    fun installDownloadedUpdate(context: Context) {
        val file = _updateState.value.downloadedApkFile ?: _uiState.value.downloadedApkFile ?: return
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
        val awarded = gachaCreditManager.recordProgressAndAwardCredits(item.id, item.tracking.progress)
        if (awarded > 0) {
            val newBal = gachaCreditManager.getCredits()
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
        val awarded = gachaCreditManager.recordProgressAndAwardCredits(item.id, item.tracking.progress)
        if (awarded > 0) {
            val newBal = gachaCreditManager.getCredits()
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

    // --- Search Feature Events & State Operations ---
    fun onSearchEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> {
                val typeChanged = _searchState.value.type != event.type
                _searchState.update { current ->
                    if (typeChanged) {
                        current.copy(
                            query = event.query,
                            type = event.type,
                            genres = emptyList(),
                            year = null,
                            format = null
                        )
                    } else {
                        current.copy(query = event.query, type = event.type)
                    }
                }
                _uiState.update { current ->
                    if (typeChanged) {
                        current.copy(
                            searchQuery = event.query,
                            searchType = event.type,
                            searchGenres = emptyList(),
                            searchYear = null,
                            searchFormat = null
                        )
                    } else {
                        current.copy(searchQuery = event.query, searchType = event.type)
                    }
                }
                _searchQueryFlow.value = SearchTrigger(event.query, event.type, forceRefresh = event.forceRefresh)
            }
            is SearchEvent.TypeChanged -> {
                if (_searchState.value.type != event.type) {
                    _searchState.update {
                        it.copy(
                            type = event.type,
                            genres = emptyList(),
                            year = null,
                            format = null
                        )
                    }
                    _uiState.update {
                        it.copy(
                            searchType = event.type,
                            searchGenres = emptyList(),
                            searchYear = null,
                            searchFormat = null
                        )
                    }
                    _searchQueryFlow.value = SearchTrigger(_searchState.value.query, event.type)
                }
            }
            is SearchEvent.FilterApplied -> {
                _searchState.update {
                    it.copy(
                        genres = event.genres,
                        year = event.year,
                        format = event.format
                    )
                }
                _uiState.update {
                    it.copy(
                        searchGenres = event.genres,
                        searchYear = event.year,
                        searchFormat = event.format
                    )
                }
                _searchQueryFlow.value = SearchTrigger(_searchState.value.query, _searchState.value.type)
            }
            is SearchEvent.FilterReset -> {
                _searchState.update {
                    it.copy(
                        genres = emptyList(),
                        year = null,
                        format = null
                    )
                }
                _uiState.update {
                    it.copy(
                        searchGenres = emptyList(),
                        searchYear = null,
                        searchFormat = null
                    )
                }
                _searchQueryFlow.value = SearchTrigger(_searchState.value.query, _searchState.value.type)
            }
            is SearchEvent.Refresh -> {
                val s = _searchState.value
                _searchQueryFlow.value = SearchTrigger(s.query, s.type, forceRefresh = true)
            }
        }
    }

    fun onSearchQueryChange(query: String, type: MediaType, forceRefresh: Boolean = false) {
        onSearchEvent(SearchEvent.QueryChanged(query, type, forceRefresh))
    }

    fun refreshSearch() {
        onSearchEvent(SearchEvent.Refresh)
    }

    fun setSearchType(type: MediaType) {
        onSearchEvent(SearchEvent.TypeChanged(type))
    }

    fun search(query: String, type: MediaType) {
        onSearchQueryChange(query, type)
    }

    fun applySearchFilters(genres: List<String>, year: Int?, format: String?) {
        onSearchEvent(SearchEvent.FilterApplied(genres, year, format))
    }

    fun resetSearchFilters() {
        onSearchEvent(SearchEvent.FilterReset)
    }

    // --- Discover Feature Events & State Operations ---
    fun onDiscoverEvent(event: DiscoverEvent) {
        when (event) {
            is DiscoverEvent.CategorySelected -> {
                discoverJob?.cancel()
                val token = ++discoverRequestToken
                val category = event.category
                val filter = event.filter ?: _discoverState.value.filter

                _discoverState.update {
                    it.copy(
                        selectedCategory = category,
                        filter = filter,
                        isLoading = true,
                        page = 1,
                        canLoadMore = true
                    )
                }
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
                    val items = repository.getDiscoverMedia(category, filter, page = 1, forceRefresh = event.forceRefresh)
                    if (token == discoverRequestToken) {
                        _discoverState.update {
                            it.copy(
                                items = items,
                                isLoading = false,
                                canLoadMore = items.size >= 20
                            )
                        }
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
            is DiscoverEvent.FilterUpdated -> {
                onDiscoverEvent(
                    DiscoverEvent.CategorySelected(
                        category = _discoverState.value.selectedCategory,
                        filter = event.filter,
                        forceRefresh = false
                    )
                )
            }
            is DiscoverEvent.LoadMore -> {
                val current = _discoverState.value
                if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return
                val nextPage = current.page + 1
                val token = discoverRequestToken

                _discoverState.update { it.copy(isLoadingMore = true) }
                _uiState.update { it.copy(isDiscoverLoadingMore = true) }

                viewModelScope.launch(Dispatchers.IO) {
                    val nextItems = repository.getDiscoverMedia(
                        current.selectedCategory,
                        current.filter,
                        page = nextPage
                    )

                    if (token == discoverRequestToken) {
                        val existingIds = current.items.map { it.malId ?: it.anilistId }.toSet()
                        val newFiltered = nextItems.filter { !existingIds.contains(it.malId ?: it.anilistId) }

                        _discoverState.update {
                            it.copy(
                                items = it.items + newFiltered,
                                page = nextPage,
                                canLoadMore = nextItems.isNotEmpty(),
                                isLoadingMore = false
                            )
                        }
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
            is DiscoverEvent.Refresh -> {
                val current = _discoverState.value
                onDiscoverEvent(
                    DiscoverEvent.CategorySelected(
                        category = current.selectedCategory,
                        filter = current.filter,
                        forceRefresh = true
                    )
                )
            }
        }
    }

    fun loadDiscoverCategory(
        category: DiscoverCategory,
        filter: DiscoverFilter = _discoverState.value.filter,
        forceRefresh: Boolean = false
    ) {
        onDiscoverEvent(DiscoverEvent.CategorySelected(category, filter, forceRefresh))
    }

    fun loadMoreDiscover() {
        onDiscoverEvent(DiscoverEvent.LoadMore)
    }

    // --- Gacha Feature Events & State Operations ---
    fun onGachaEvent(event: GachaEvent) {
        when (event) {
            is GachaEvent.OpenGacha -> {
                pushScreen(ScreenRoute.Flashcard)
                if (_gachaState.value.deck.isEmpty() && _gachaState.value.credits > 0) {
                    onGachaEvent(GachaEvent.LoadDeck)
                }
            }
            is GachaEvent.ConsumeCredit -> {
                consumeGachaCredit()
            }
            is GachaEvent.SwipeDismiss -> {
                _gachaState.update {
                    val updatedDeck = it.deck.filter { card -> card.id != event.item.id }
                    it.copy(deck = updatedDeck)
                }
                _uiState.update {
                    val updatedDeck = it.flashcardDeck.filter { card -> card.id != event.item.id }
                    it.copy(flashcardDeck = updatedDeck)
                }
                if (_gachaState.value.deck.isEmpty() && _gachaState.value.credits > 0) {
                    onGachaEvent(GachaEvent.LoadDeck)
                }
            }
            is GachaEvent.LoadDeck -> {
                viewModelScope.launch(Dispatchers.IO) {
                    _gachaState.update { it.copy(isLoading = true) }
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

                    _gachaState.update {
                        it.copy(
                            deck = pool,
                            isLoading = false
                        )
                    }
                    _uiState.update {
                        it.copy(
                            flashcardDeck = pool,
                            isFlashcardLoading = false
                        )
                    }
                }
            }
            is GachaEvent.UpdateCredits -> {
                gachaCreditManager.setCredits(event.newCredits)
                _gachaState.update { it.copy(credits = event.newCredits) }
                _uiState.update { it.copy(gachaCredits = event.newCredits) }
            }
        }
    }

    // --- Flashcard Gacha System (v5.0.0) ---
    fun openFlashcard() {
        onGachaEvent(GachaEvent.OpenGacha)
    }

    fun consumeGachaCredit(): Boolean {
        val success = gachaCreditManager.consumeCredit()
        if (success) {
            val updated = gachaCreditManager.getCredits()
            _gachaState.update { it.copy(credits = updated) }
            _uiState.update { it.copy(gachaCredits = updated) }
        }
        return success
    }

    fun swipeDismissFlashcard(item: MediaItem) {
        onGachaEvent(GachaEvent.SwipeDismiss(item))
    }

    fun loadFlashcardDeck() {
        onGachaEvent(GachaEvent.LoadDeck)
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

                _detailState.update {
                    it.copy(
                        selectedItem = resolvedItem,
                        mediaType = type,
                        isOpen = true,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = false,
                        extendedDetail = initialDetail,
                        isLoadingExtendedDetail = cachedDetail == null
                    )
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
                val token = ++detailRequestToken
                detailJob = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val initialMalId = malId ?: (anilistId?.let { CacheManager.getMalIdForAniListId(it, type) })
                        var effectiveMalId = initialMalId

                        // 1. Concurrently launch MAL fetch if MAL ID is known
                        val malDeferred = if (initialMalId != null) {
                            async {
                                repository.getMalExtendedDetailFallback(initialMalId, type)
                            }
                        } else null

                        // 2. Concurrently launch AniList fetch
                        val aniDeferred = async {
                            com.canim.app.data.remote.AniListClient.getExtendedDetails(anilistId, initialMalId, type)
                        }

                        // Worker A: As soon as MAL responds, update UI with MAL metrics and detail immediately!
                        if (malDeferred != null) {
                            launch {
                                try {
                                    val malDetail = malDeferred.await()
                                    if (malDetail != null && token == detailRequestToken) {
                                        _detailState.update { current ->
                                            val currentExt = current.extendedDetail
                                            val merged = (currentExt ?: malDetail).copy(
                                                coverImage = malDetail.coverImage?.takeIf { it.isNotBlank() } ?: currentExt?.coverImage ?: "",
                                                malScore = malDetail.malScore ?: currentExt?.malScore,
                                                malRank = malDetail.malRank ?: currentExt?.malRank,
                                                malPopularity = malDetail.malPopularity ?: currentExt?.malPopularity,
                                                malMembers = malDetail.malMembers ?: currentExt?.malMembers,
                                                synopsis = malDetail.synopsis?.takeIf { it.isNotBlank() } ?: currentExt?.synopsis ?: "",
                                                airingStatus = malDetail.airingStatus ?: currentExt?.airingStatus,
                                                isFromFallback = currentExt == null || currentExt.isFromFallback
                                            )
                                            cacheDetail(resolvedItem, merged)
                                            cacheDetail(item, merged)
                                            current.copy(
                                                extendedDetail = merged,
                                                isLoadingExtendedDetail = false
                                            )
                                        }
                                        _uiState.update { current ->
                                            val currentExt = current.extendedDetail
                                            val merged = (currentExt ?: malDetail).copy(
                                                coverImage = malDetail.coverImage?.takeIf { it.isNotBlank() } ?: currentExt?.coverImage ?: "",
                                                malScore = malDetail.malScore ?: currentExt?.malScore,
                                                malRank = malDetail.malRank ?: currentExt?.malRank,
                                                malPopularity = malDetail.malPopularity ?: currentExt?.malPopularity,
                                                malMembers = malDetail.malMembers ?: currentExt?.malMembers,
                                                synopsis = malDetail.synopsis?.takeIf { it.isNotBlank() } ?: currentExt?.synopsis ?: "",
                                                airingStatus = malDetail.airingStatus ?: currentExt?.airingStatus,
                                                isFromFallback = currentExt == null || currentExt.isFromFallback
                                            )
                                            current.copy(
                                                extendedDetail = merged,
                                                isLoadingExtendedDetail = false
                                            )
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        // Worker B: Wait for AniList and merge rich media
                        val aniDetail = try {
                            aniDeferred.await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("CanimViewModel", "AniList detail fetch failed: ${LogRedactor.redact(e.message ?: "")}")
                            null
                        }

                        if (aniDetail != null && token == detailRequestToken) {
                            if (effectiveMalId == null && aniDetail.malId != null) {
                                effectiveMalId = aniDetail.malId
                                launch {
                                    try {
                                        val malDetail = repository.getMalExtendedDetailFallback(aniDetail.malId, type)
                                        if (malDetail != null && token == detailRequestToken) {
                                            _detailState.update { current ->
                                                val currentExt = current.extendedDetail ?: aniDetail
                                                val merged = currentExt.copy(
                                                    coverImage = malDetail.coverImage?.takeIf { it.isNotBlank() } ?: currentExt.coverImage,
                                                    malScore = malDetail.malScore ?: currentExt.malScore,
                                                    malRank = malDetail.malRank ?: currentExt.malRank,
                                                    malPopularity = malDetail.malPopularity ?: currentExt.malPopularity,
                                                    malMembers = malDetail.malMembers ?: currentExt.malMembers,
                                                    synopsis = malDetail.synopsis?.takeIf { it.isNotBlank() } ?: currentExt.synopsis,
                                                    airingStatus = malDetail.airingStatus ?: currentExt.airingStatus
                                                )
                                                cacheDetail(resolvedItem, merged)
                                                cacheDetail(item, merged)
                                                current.copy(extendedDetail = merged)
                                            }
                                            _uiState.update { current ->
                                                val currentExt = current.extendedDetail ?: aniDetail
                                                val merged = currentExt.copy(
                                                    coverImage = malDetail.coverImage?.takeIf { it.isNotBlank() } ?: currentExt.coverImage,
                                                    malScore = malDetail.malScore ?: currentExt.malScore,
                                                    malRank = malDetail.malRank ?: currentExt.malRank,
                                                    malPopularity = malDetail.malPopularity ?: currentExt.malPopularity,
                                                    malMembers = malDetail.malMembers ?: currentExt.malMembers,
                                                    synopsis = malDetail.synopsis?.takeIf { it.isNotBlank() } ?: currentExt.synopsis,
                                                    airingStatus = malDetail.airingStatus ?: currentExt.airingStatus
                                                )
                                                current.copy(extendedDetail = merged)
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }

                            _detailState.update { current ->
                                val currentExt = current.extendedDetail
                                val merged = aniDetail.copy(
                                    coverImage = currentExt?.coverImage?.takeIf { it.isNotBlank() } ?: aniDetail.coverImage,
                                    malScore = currentExt?.malScore ?: aniDetail.malScore,
                                    malRank = currentExt?.malRank ?: aniDetail.rank,
                                    malPopularity = currentExt?.malPopularity ?: aniDetail.popularity,
                                    malMembers = currentExt?.malMembers ?: aniDetail.watchers,
                                    synopsis = currentExt?.synopsis?.takeIf { it.isNotBlank() } ?: aniDetail.synopsis,
                                    airingStatus = currentExt?.airingStatus ?: aniDetail.airingStatus
                                )
                                cacheDetail(resolvedItem, merged)
                                cacheDetail(item, merged)
                                current.copy(
                                    extendedDetail = merged,
                                    isLoadingExtendedDetail = false
                                )
                            }
                            _uiState.update { current ->
                                val currentExt = current.extendedDetail
                                val merged = aniDetail.copy(
                                    coverImage = currentExt?.coverImage?.takeIf { it.isNotBlank() } ?: aniDetail.coverImage,
                                    malScore = currentExt?.malScore ?: aniDetail.malScore,
                                    malRank = currentExt?.malRank ?: aniDetail.rank,
                                    malPopularity = currentExt?.malPopularity ?: aniDetail.popularity,
                                    malMembers = currentExt?.malMembers ?: aniDetail.watchers,
                                    synopsis = currentExt?.synopsis?.takeIf { it.isNotBlank() } ?: aniDetail.synopsis,
                                    airingStatus = currentExt?.airingStatus ?: aniDetail.airingStatus
                                )
                                current.copy(
                                    extendedDetail = merged,
                                    isLoadingExtendedDetail = false
                                )
                            }
                        } else if (aniDetail == null && effectiveMalId != null && malDeferred != null) {
                            // AniList failed or timed out: wait for MAL fallback to finish
                            val malDetail = malDeferred.await()
                            if (malDetail != null && token == detailRequestToken) {
                                cacheDetail(resolvedItem, malDetail)
                                cacheDetail(item, malDetail)
                                _detailState.update { it.copy(extendedDetail = malDetail, isLoadingExtendedDetail = false) }
                                _uiState.update { it.copy(extendedDetail = malDetail, isLoadingExtendedDetail = false) }
                            }
                        }

                        // Unified tracking resolution: if not in local library, fetch live MAL tracking asynchronously
                        if (resolvedItem !is UserMediaItem && effectiveMalId != null && _uiState.value.malUser.isLoggedIn) {
                            try {
                                val tracking = repository.getMalTrackingStatus(effectiveMalId, type)
                                if (tracking != null) {
                                    val currentExt = _detailState.value.extendedDetail
                                    val media = resolvedItem as? MediaItem
                                    val itemTitle = media?.title ?: currentExt?.title ?: ""
                                    val itemImageUrl = currentExt?.coverImage?.takeIf { it.isNotBlank() } ?: media?.imageUrl ?: ""
                                    val metadata = MediaMetadata(
                                        title = itemTitle,
                                        titleEnglish = media?.titleEnglish ?: currentExt?.titleEnglish,
                                        titleNative = currentExt?.nativeTitle,
                                        imageUrl = itemImageUrl,
                                        type = type,
                                        score = currentExt?.malScore ?: media?.score,
                                        synopsis = media?.synopsis ?: currentExt?.synopsis,
                                        totalEpisodes = media?.episodes,
                                        totalChapters = media?.chapters,
                                        status = media?.status ?: currentExt?.airingStatus,
                                        year = media?.year ?: currentExt?.startDate?.take(4)?.toIntOrNull(),
                                        season = media?.season,
                                        genres = if (media?.genres?.isNotEmpty() == true) media.genres else (currentExt?.genres ?: emptyList()),
                                        format = media?.format ?: currentExt?.source,
                                        studio = media?.studio ?: currentExt?.studio
                                    )
                                    val newUserItem = UserMediaItem(
                                        identity = MediaRef(
                                            anilistId = anilistId ?: currentExt?.anilistId,
                                            malId = effectiveMalId
                                        ),
                                        metadata = metadata,
                                        tracking = tracking
                                    )
                                    resolvedItem = newUserItem
                                    _detailState.update {
                                        it.copy(selectedItem = newUserItem)
                                    }
                                    _uiState.update {
                                        it.copy(selectedDetailItem = newUserItem)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("CanimViewModel", "Detail fetch failed: ${LogRedactor.redact(e.message ?: "")}")
                    } finally {
                        if (token == detailRequestToken) {
                            _detailState.update { it.copy(isLoadingExtendedDetail = false) }
                            _uiState.update { it.copy(isLoadingExtendedDetail = false) }
                            if (_detailState.value.extendedDetail == null) {
                                showSnackbar("Gagal memuat detail media. Periksa koneksi internet Anda.")
                            }
                        }
                    }
                }
            }
            is ScreenRoute.CastCrew -> {
                _detailState.update {
                    it.copy(
                        selectedItem = null,
                        isOpen = false,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = true
                    )
                }
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
                    _detailState.update {
                        it.copy(
                            selectedCastCrewProfile = profile,
                            isLoadingCastCrewProfile = false
                        )
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
                _detailState.update {
                    it.copy(isOpen = false)
                }
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
            is ScreenRoute.Diagnostics -> {
                // Diagnostics screen state is handled independently via AppMetrics
            }
            null -> {
                detailJob?.cancel()
                studioJob?.cancel()
                _detailState.update {
                    it.copy(
                        selectedItem = null,
                        isOpen = false,
                        extendedDetail = null,
                        isLoadingExtendedDetail = false,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = false
                    )
                }
                _studioState.update {
                    it.copy(
                        studioId = null,
                        studioName = "",
                        bio = null,
                        items = emptyList(),
                        totalEntries = 0,
                        isLoading = false,
                        isLoadingMore = false,
                        page = 1,
                        canLoadMore = true
                    )
                }
                _uiState.update {
                    it.copy(
                        selectedDetailItem = null,
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
        onDetailEvent(DetailEvent.OpenCastCrewProfile(id, isStaff))
    }

    fun closeCastCrewProfile() {
        onDetailEvent(DetailEvent.CloseCastCrewProfile)
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

    // --- Studio Feature Events & State Operations ---
    fun onStudioEvent(event: StudioEvent) {
        when (event) {
            is StudioEvent.OpenStudio -> {
                val bio = try { StudioBioRegistry.getStudioInfo(event.studioId, event.studioName) } catch (_: Exception) { null }
                _studioState.update {
                    it.copy(
                        studioId = event.studioId,
                        studioName = event.studioName,
                        bio = bio,
                        sort = StudioFilmographySort.YEAR_DESC
                    )
                }
                _uiState.update {
                    it.copy(
                        studioFilmographyBio = bio,
                        studioFilmographySort = StudioFilmographySort.YEAR_DESC
                    )
                }
                pushScreen(ScreenRoute.StudioFilmography(event.studioId, event.studioName))
            }
            is StudioEvent.CloseStudio -> {
                studioJob?.cancel()
                if (_screenStack.value.lastOrNull() is ScreenRoute.StudioFilmography) {
                    popScreen()
                } else {
                    _studioState.update {
                        it.copy(
                            studioId = null,
                            studioName = "",
                            bio = null,
                            items = emptyList(),
                            totalEntries = 0,
                            isLoading = false,
                            isLoadingMore = false,
                            page = 1,
                            canLoadMore = true
                        )
                    }
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
            is StudioEvent.SetSort -> {
                _studioState.update { it.copy(sort = event.sort) }
                _uiState.update { it.copy(studioFilmographySort = event.sort) }
                val sId = _studioState.value.studioId ?: _uiState.value.studioFilmographyStudioId
                val sName = _studioState.value.studioName.ifEmpty { _uiState.value.studioFilmographyStudioName }
                if (sId != null) {
                    loadStudioFilmography(sId, sName, page = 1)
                }
            }
            is StudioEvent.LoadFilmography -> {
                val page = event.page
                val studioId = event.studioId
                val studioName = event.studioName
                if (page == 1) {
                    studioJob?.cancel()
                    val bio = _studioState.value.bio
                        ?: _uiState.value.studioFilmographyBio
                        ?: try { StudioBioRegistry.getStudioInfo(studioId, studioName) } catch (_: Exception) { null }
                    _studioState.update {
                        it.copy(
                            studioId = studioId,
                            studioName = studioName,
                            bio = bio,
                            isLoading = true,
                            items = emptyList(),
                            totalEntries = 0,
                            page = 1,
                            canLoadMore = true
                        )
                    }
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
                    _studioState.update { it.copy(isLoadingMore = true) }
                    _uiState.update { it.copy(isStudioFilmographyLoadingMore = true) }
                }

                studioJob = viewModelScope.launch(Dispatchers.IO) {
                    val sort = _studioState.value.sort
                    val pageResult = repository.getStudioFilmography(studioId = studioId, page = page, sort = sort)
                    val currentStudio = _studioState.value
                    val newItems = if (page == 1) {
                        pageResult?.items ?: emptyList()
                    } else {
                        val existingIds = currentStudio.items.map { it.id }.toSet()
                        val added = (pageResult?.items ?: emptyList()).filter { it.id !in existingIds }
                        currentStudio.items + added
                    }
                    val totalEntries = if (page == 1) (pageResult?.total ?: 0) else currentStudio.totalEntries
                    val updatedBio = currentStudio.bio?.let { currBio ->
                        currBio.copy(
                            totalAnime = if (totalEntries > 0) totalEntries else currBio.totalAnime,
                            officialSite = pageResult?.siteUrl ?: currBio.officialSite,
                            favourites = pageResult?.favourites ?: currBio.favourites
                        )
                    }

                    _studioState.update {
                        it.copy(
                            bio = updatedBio ?: it.bio,
                            items = newItems,
                            totalEntries = if (totalEntries > 0) totalEntries else newItems.size,
                            isLoading = false,
                            isLoadingMore = false,
                            page = page,
                            canLoadMore = pageResult?.hasNextPage ?: false
                        )
                    }
                    _uiState.update {
                        it.copy(
                            studioFilmographyBio = updatedBio ?: it.studioFilmographyBio,
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
            is StudioEvent.LoadMore -> {
                val s = _studioState.value
                if (s.isLoading || s.isLoadingMore || !s.canLoadMore) return
                val studioId = s.studioId ?: _uiState.value.studioFilmographyStudioId ?: return
                val studioName = s.studioName.ifEmpty { _uiState.value.studioFilmographyStudioName }
                onStudioEvent(StudioEvent.LoadFilmography(studioId, studioName, s.page + 1))
            }
            is StudioEvent.SearchStudios -> {
                studioSearchJob?.cancel()
                val trimmed = event.query.trim()
                if (trimmed.isBlank()) {
                    _studioState.update {
                        it.copy(
                            searchResults = emptyList(),
                            isSearchingStudios = false
                        )
                    }
                    _uiState.update {
                        it.copy(
                            studioSearchResults = emptyList(),
                            isSearchingStudios = false
                        )
                    }
                    return
                }

                val localMatches = try {
                    StudioBioRegistry.searchCuratedStudios(trimmed)
                } catch (_: Exception) {
                    emptyList()
                }
                _studioState.update {
                    it.copy(
                        searchResults = localMatches,
                        isSearchingStudios = true
                    )
                }
                _uiState.update {
                    it.copy(
                        studioSearchResults = localMatches,
                        isSearchingStudios = true
                    )
                }

                studioSearchJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(250L)
                    val remoteResults = try {
                        repository.searchStudios(trimmed)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val merged = (localMatches + remoteResults).distinctBy { it.studioId }

                    _studioState.update {
                        it.copy(
                            searchResults = merged,
                            isSearchingStudios = false
                        )
                    }
                    _uiState.update {
                        it.copy(
                            studioSearchResults = merged,
                            isSearchingStudios = false
                        )
                    }
                }
            }
            is StudioEvent.ClearStudioSearch -> {
                studioSearchJob?.cancel()
                _studioState.update {
                    it.copy(
                        searchResults = emptyList(),
                        isSearchingStudios = false
                    )
                }
                _uiState.update {
                    it.copy(
                        studioSearchResults = emptyList(),
                        isSearchingStudios = false
                    )
                }
            }
        }
    }

    // --- Studio Filmography (v5.0.0) ---
    fun openStudio(studioId: Int, studioName: String) {
        onStudioEvent(StudioEvent.OpenStudio(studioId, studioName))
    }

    fun setStudioFilmographySort(sort: StudioFilmographySort) {
        onStudioEvent(StudioEvent.SetSort(sort))
    }

    // --- Studio Live Search (v5.1.1) ---
    fun searchStudios(query: String) {
        onStudioEvent(StudioEvent.SearchStudios(query))
    }

    fun clearStudioSearch() {
        onStudioEvent(StudioEvent.ClearStudioSearch)
    }

    fun closeStudio() {
        onStudioEvent(StudioEvent.CloseStudio)
    }

    fun loadStudioFilmography(studioId: Int, studioName: String, page: Int = 1) {
        onStudioEvent(StudioEvent.LoadFilmography(studioId, studioName, page))
    }

    fun loadMoreStudioFilmography() {
        onStudioEvent(StudioEvent.LoadMore)
    }

    // --- Detail Feature Events & State Operations ---
    fun onDetailEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.OpenDetail -> {
                pushScreen(ScreenRoute.Detail(event.item, event.type))
            }
            is DetailEvent.CloseDetail -> {
                if (_screenStack.value.lastOrNull() is ScreenRoute.Detail) {
                    popScreen()
                } else {
                    detailJob?.cancel()
                    _detailState.update {
                        it.copy(
                            selectedItem = null,
                            isOpen = false,
                            extendedDetail = null,
                            isLoadingExtendedDetail = false
                        )
                    }
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
            is DetailEvent.OpenCastCrewProfile -> {
                pushScreen(ScreenRoute.CastCrew(event.id, event.isStaff))
            }
            is DetailEvent.CloseCastCrewProfile -> {
                if (_screenStack.value.lastOrNull() is ScreenRoute.CastCrew) {
                    popScreen()
                } else {
                    _detailState.update {
                        it.copy(
                            selectedCastCrewProfile = null,
                            isLoadingCastCrewProfile = false
                        )
                    }
                    _uiState.update {
                        it.copy(
                            selectedCastCrewProfile = null,
                            isLoadingCastCrewProfile = false
                        )
                    }
                }
            }
            is DetailEvent.ItemUpdated -> {
                _detailState.update { it.copy(selectedItem = event.updatedItem) }
                _uiState.update { it.copy(selectedDetailItem = event.updatedItem) }
            }
        }
    }

    // --- Details ---
    fun openDetail(item: Any, type: MediaType) {
        onDetailEvent(DetailEvent.OpenDetail(item, type))
    }

    fun closeDetail() {
        onDetailEvent(DetailEvent.CloseDetail)
    }

    // --- Global Feature Events & State Operations ---
    fun onGlobalEvent(event: GlobalEvent) {
        when (event) {
            is GlobalEvent.SetActiveTab -> setTab(event.tab)
            is GlobalEvent.SetAppMode -> setAppMode(event.mode)
            is GlobalEvent.ShowSnackbar -> showSnackbar(event.message)
            is GlobalEvent.DismissSnackbar -> dismissSnackbar()
            is GlobalEvent.RefreshHealth -> checkApiHealth()
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
            _globalState.update { it.copy(isExchangingToken = true) }
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
                _globalState.update {
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
                _globalState.update { it.copy(isExchangingToken = false) }
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
        _globalState.update {
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
            _globalState.update { it.copy(isSyncingMal = true) }
            val result = repository.syncWithMal()
            _uiState.update { it.copy(isSyncingMal = false) }
            _globalState.update { it.copy(isSyncingMal = false) }
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
        _globalState.update { it.copy(appMode = mode) }
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
        _globalState.update { it.copy(snackbarMessage = message) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
        _globalState.update { it.copy(snackbarMessage = null) }
    }
}
