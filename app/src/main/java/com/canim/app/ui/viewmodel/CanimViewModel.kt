package com.canim.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.*
import com.canim.app.data.repository.CanimRepository
import com.canim.app.CanimApplication
import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.repository.StudioBioRegistry
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
    val studioFilmographyBio: StudioBioInfo? = null,
    val studioFilmographySort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC,
    val studioFilmographyItems: List<MediaItem> = emptyList(),
    val isStudioFilmographyLoading: Boolean = false,
    val isStudioFilmographyLoadingMore: Boolean = false,
    val studioFilmographyPage: Int = 1,
    val studioFilmographyTotalEntries: Int = 0,
    val canLoadMoreStudioFilmography: Boolean = true,

    // Gacha Flashcard state
    val gachaCredits: Int = 5,
    val flashcardDeck: List<MediaItem> = emptyList(),
    val isFlashcardLoading: Boolean = false,

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

        // Initialize Gacha Credits
        val gachaMgr = gachaCreditManager ?: try { GachaCreditManager.getInstance(CanimApplication.instance) } catch (_: Exception) { null }
        val currentCredits = gachaMgr?.getCredits() ?: 5
        _uiState.update { it.copy(gachaCredits = currentCredits) }

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
        val gachaMgr = gachaCreditManager ?: try { GachaCreditManager.getInstance(CanimApplication.instance) } catch (_: Exception) { null }
        gachaMgr?.addCredit(1)?.let { newBal ->
            _uiState.update { it.copy(gachaCredits = newBal) }
        }
        showSnackbar("+1 Episode ditambahkan! (+1 Tiket Gacha)")

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
        val oldItem = currentList.firstOrNull { it.id == item.id }
        val progressDiff = if (oldItem != null) item.tracking.progress - oldItem.tracking.progress else item.tracking.progress
        if (progressDiff > 0) {
            val gachaMgr = gachaCreditManager ?: try { GachaCreditManager.getInstance(CanimApplication.instance) } catch (_: Exception) { null }
            gachaMgr?.addCredit(progressDiff)?.let { newBal ->
                _uiState.update { it.copy(gachaCredits = newBal) }
            }
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
            val pool = (currentSeason + upcoming)
                .filter { item ->
                    val mId = item.malId
                    mId == null || (!completedIds.contains(mId) && !libraryIds.contains(mId))
                }
                .distinctBy { it.malId ?: it.anilistId }
                .shuffled()
                .take(15)

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

                val resolvedAniListId = anilistId ?: (malId?.let { CacheManager.getAniListIdForMalId(it) })
                val resolvedMalId = malId ?: (resolvedAniListId?.let { CacheManager.getMalIdForAniListId(it) })
                val cachedDetail = CacheManager.getDetail(CacheManager.detailKey(resolvedAniListId, resolvedMalId))
                    ?: (resolvedAniListId?.let { CacheManager.getDetail(CacheManager.detailKey(it, null)) })
                    ?: (resolvedMalId?.let { CacheManager.getDetail(CacheManager.detailKey(null, it)) })

                _uiState.update {
                    it.copy(
                        selectedDetailItem = resolvedItem,
                        detailMediaType = type,
                        isDetailOpen = true,
                        selectedCastCrewProfile = null,
                        isLoadingCastCrewProfile = false,
                        isStatsOpen = false,
                        isAddTitleSheetOpen = false,
                        extendedDetail = cachedDetail,
                        isLoadingExtendedDetail = cachedDetail == null
                    )
                }

                detailJob?.cancel()
                detailJob = viewModelScope.launch(Dispatchers.IO) {
                    val detail = repository.getExtendedDetails(anilistId, malId, type)

                    // Immediately update UI with extendedDetail and stop loading spinner
                    _uiState.update {
                        it.copy(
                            extendedDetail = detail ?: it.extendedDetail,
                            isLoadingExtendedDetail = false
                        )
                    }

                    // Unified tracking resolution: if not in local library, fetch live MAL tracking asynchronously
                    val effectiveMalId = detail?.malId ?: malId ?: resolvedMalId
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
            val pageResult = repository.getStudioFilmography(studioId = studioId, page = page)
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
