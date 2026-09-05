package com.canim.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.*
import com.canim.app.data.repository.CanimRepository
import com.canim.app.ui.navigation.ScreenRoute
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
    val randomSort: String? = null,
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
    val studioFilmographyItems: List<MediaItem> = emptyList(),
    val isStudioFilmographyLoading: Boolean = false,
    val isStudioFilmographyLoadingMore: Boolean = false,
    val studioFilmographyPage: Int = 1,
    val studioFilmographyTotalEntries: Int = 0,
    val canLoadMoreStudioFilmography: Boolean = true,

    // App & Auth state
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val snackbarMessage: String? = null,
    val appMode: String = "online_sync", // "offline" or "online_sync"
    val malUser: MalUser = MalUser(),
    val isSyncingMal: Boolean = false,
    val isExchangingToken: Boolean = false,
    val isLoadingLibrary: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CanimViewModel(
    private val repository: CanimRepository
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

    // Reactive search flow
    private val _searchQueryFlow = MutableStateFlow(Pair("", MediaType.ANIME))

    private var discoverJob: Job? = null
    private var discoverRequestToken = 0L
    private var detailJob: Job? = null
    private var studioJob: Job? = null

    init {
        // Cold-start instant cache-first load from disk/memory
        val cachedAnime = repository.getCachedTracking("ANIME")
        val cachedManga = repository.getCachedTracking("MANGA")
        if (!cachedAnime.isNullOrEmpty() || !cachedManga.isNullOrEmpty()) {
            updateLibraryData(cachedAnime ?: emptyList(), cachedManga ?: emptyList())
        }

        // Load discovery category
        loadDiscoverCategory(_uiState.value.selectedDiscoverCategory, _uiState.value.discoverFilter)

        // Silent background sync / load user library
        loadUserLibrary()

        // Reactive Debounced Search (300ms)
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300L)
                .distinctUntilChanged()
                .flatMapLatest { (query, type) ->
                    flow {
                        val trimmed = query.trim()
                        if (trimmed.length < 2) {
                            emit(emptyList<MediaItem>())
                        } else {
                            _uiState.update { it.copy(isSearching = true) }
                            val results = if (type == MediaType.ANIME) {
                                repository.searchAnime(trimmed)
                            } else {
                                repository.searchManga(trimmed)
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
                CacheManager.pruneExpired()
            }
        }
    }

    /**
     * Loads the authoritative library from MAL in parallel (or demo dataset if not logged in).
     */
    fun loadUserLibrary(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLibrary = true, syncStatus = SyncStatus.SYNCING) }
            val user = repository.getMalUser()
            if (user.isLoggedIn) {
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
        val updatedItem = item.copy(
            tracking = item.tracking.copy(
                progress = item.tracking.progress + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        // 1. Optimistic UI update
        val optimisticList = currentList.map { if (it.id == item.id) updatedItem else it }
        updateLibraryData(optimisticList, _uiState.value.mangaList)
        showSnackbar("+1 Episode ditambahkan!")

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
        val updatedItem = item.copy(
            tracking = item.tracking.copy(
                progress = item.tracking.progress + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        val optimisticList = currentList.map { if (it.id == item.id) updatedItem else it }
        updateLibraryData(_uiState.value.animeList, optimisticList)
        showSnackbar("+1 Chapter ditambahkan!")

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

    fun saveAnime(item: UserMediaItem) {
        val currentList = _uiState.value.animeList
        val exists = currentList.any { it.id == item.id }
        val optimisticList = if (exists) {
            currentList.map { if (it.id == item.id) item else it }
        } else {
            currentList + item
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
        _uiState.update { it.copy(searchQuery = query, searchType = type) }
        _searchQueryFlow.value = Pair(query, type)
    }

    fun search(query: String, type: MediaType) {
        onSearchQueryChange(query, type)
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
                randomSort = null,
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
                page = nextPage,
                randomSort = current.randomSort
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

    /**
     * Fixed Randomizer:
     * - Genuinely random selection.
     * - Strictly excludes completed anime.
     * - If page has insufficient eligible items, fetches more pages instead of reintroducing completed entries.
     */
    fun randomizeAnime(filter: DiscoverFilter = _uiState.value.discoverFilter) {
        discoverJob?.cancel()
        val token = ++discoverRequestToken

        _uiState.update {
            it.copy(
                selectedDiscoverCategory = DiscoverCategory.RANDOM_FILTER,
                discoverFilter = filter,
                randomSort = null,
                isDiscoverLoading = true,
                discoverPage = 1,
                canLoadMoreDiscover = false
            )
        }

        discoverJob = viewModelScope.launch(Dispatchers.IO) {
            val completedIds = _uiState.value.completedAnimeMalIds
            val candidates = mutableListOf<MediaItem>()
            var searchPage = 1
            val maxPages = 3

            while (candidates.size < 15 && searchPage <= maxPages) {
                val pageItems = repository.getDiscoverMedia(
                    category = DiscoverCategory.RANDOM_FILTER,
                    filter = filter,
                    page = searchPage,
                    forceRefresh = true
                )
                if (pageItems.isEmpty()) break

                val eligible = pageItems.filter { item ->
                    val mId = item.malId
                    mId == null || !completedIds.contains(mId)
                }
                candidates.addAll(eligible)
                searchPage++
            }

            // Genuinely shuffle and pick distinct
            val shuffled = candidates.distinctBy { it.malId ?: it.anilistId }.shuffled()

            if (token == discoverRequestToken) {
                _uiState.update {
                    it.copy(
                        discoverItems = shuffled,
                        isDiscoverLoading = false,
                        canLoadMoreDiscover = false
                    )
                }
            }
        }
    }

    /**
     * Fixed Randomizer for Manga:
     * - Genuinely random selection.
     * - Strictly excludes completed manga.
     * - If page has insufficient eligible items, fetches more pages instead of reintroducing completed entries.
     */
    fun randomizeManga(filter: DiscoverFilter = _uiState.value.discoverFilter.copy(format = "MANGA")) {
        discoverJob?.cancel()
        val token = ++discoverRequestToken
        val mangaFilter = filter.copy(format = "MANGA")

        _uiState.update {
            it.copy(
                selectedDiscoverCategory = DiscoverCategory.RANDOM_FILTER,
                discoverFilter = mangaFilter,
                randomSort = null,
                isDiscoverLoading = true,
                discoverPage = 1,
                canLoadMoreDiscover = false
            )
        }

        discoverJob = viewModelScope.launch(Dispatchers.IO) {
            val completedIds = _uiState.value.completedMangaMalIds
            val candidates = mutableListOf<MediaItem>()
            var searchPage = 1
            val maxPages = 3

            while (candidates.size < 15 && searchPage <= maxPages) {
                val pageItems = repository.getDiscoverMedia(
                    category = DiscoverCategory.RANDOM_FILTER,
                    filter = mangaFilter,
                    page = searchPage,
                    forceRefresh = true
                )
                if (pageItems.isEmpty()) break

                val eligible = pageItems.filter { item ->
                    val mId = item.malId
                    mId == null || !completedIds.contains(mId)
                }
                candidates.addAll(eligible)
                searchPage++
            }

            // Genuinely shuffle and pick distinct
            val shuffled = candidates.distinctBy { it.malId ?: it.anilistId }.shuffled()

            if (token == discoverRequestToken) {
                _uiState.update {
                    it.copy(
                        discoverItems = shuffled,
                        isDiscoverLoading = false,
                        canLoadMoreDiscover = false
                    )
                }
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

                _uiState.update {
                    it.copy(
                        selectedDetailItem = resolvedItem,
                        detailMediaType = type,
                        isDetailOpen = true,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = false,
                        isStatsOpen = false,
                        isAddTitleSheetOpen = false,
                        extendedDetail = null,
                        isLoadingExtendedDetail = true
                    )
                }

                detailJob?.cancel()
                detailJob = viewModelScope.launch(Dispatchers.IO) {
                    val detail = repository.getExtendedDetails(anilistId, malId, type)
                    val effectiveMalId = detail?.malId ?: malId

                    // Unified tracking resolution: if not in local library, fetch live MAL tracking
                    if (resolvedItem !is UserMediaItem && effectiveMalId != null && _uiState.value.malUser.isLoggedIn) {
                        try {
                            val tracking = repository.getMalTrackingStatus(effectiveMalId, type)
                            if (tracking != null && tracking.status != null) {
                                val media = resolvedItem as? MediaItem
                                val itemTitle = media?.title ?: detail?.title ?: ""
                                val itemImageUrl = media?.imageUrl ?: detail?.coverImage ?: ""
                                val metadata = MediaMetadata(
                                    title = itemTitle,
                                    titleEnglish = media?.titleEnglish ?: detail?.titleEnglish,
                                    titleNative = detail?.nativeTitle,
                                    imageUrl = itemImageUrl,
                                    type = type,
                                    score = detail?.malScore ?: detail?.averageScore ?: media?.score,
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

                    _uiState.update {
                        it.copy(
                            extendedDetail = detail,
                            isLoadingExtendedDetail = false
                        )
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
            is ScreenRoute.StudioFilmography -> {
                loadStudioFilmography(route.studioId, route.studioName, 1)
            }
            null -> {
                detailJob?.cancel()
                studioJob?.cancel()
                _uiState.update {
                    it.copy(
                        selectedDetailItem = null,
                        isDetailOpen = false,
                        extendedDetail = null,
                        isLoadingExtendedDetail = false,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = false,
                        isStatsOpen = false,
                        isAddTitleSheetOpen = false,
                        studioFilmographyStudioId = null,
                        studioFilmographyStudioName = "",
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

    // --- Studio Filmography ---
    fun openStudio(studioId: Int, studioName: String) {
        pushScreen(ScreenRoute.StudioFilmography(studioId, studioName))
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
            _uiState.update {
                it.copy(
                    studioFilmographyStudioId = studioId,
                    studioFilmographyStudioName = studioName,
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
            val pageResult = repository.getStudioFilmography(studioId = studioId, page = page)
            _uiState.update {
                val newItems = if (page == 1) {
                    pageResult?.items ?: emptyList()
                } else {
                    val existingIds = it.studioFilmographyItems.map { item -> item.id }.toSet()
                    val added = (pageResult?.items ?: emptyList()).filter { item -> item.id !in existingIds }
                    it.studioFilmographyItems + added
                }
                val totalEntries = if (page == 1) (pageResult?.total ?: 0) else it.studioFilmographyTotalEntries
                it.copy(
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
    private val repository: CanimRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CanimViewModel::class.java)) {
            return CanimViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
