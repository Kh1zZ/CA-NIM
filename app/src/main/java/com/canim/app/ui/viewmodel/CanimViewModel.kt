package com.canim.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.AnimeEntity
import com.canim.app.data.local.MangaEntity
import com.canim.app.data.model.*
import com.canim.app.data.repository.CanimRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CanimUiState(
    val animeList: List<AnimeEntity> = emptyList(),
    val mangaList: List<MangaEntity> = emptyList(),
    // Off-main-thread filtered lists for fast UI rendering
    val watchingAnime: List<AnimeEntity> = emptyList(),
    val readingManga: List<MangaEntity> = emptyList(),
    val completedAnimeMalIds: Set<Int> = emptySet(),

    val stats: TrackerStats = TrackerStats(),
    val activeTab: String = "dashboard",
    val libraryFilterType: MediaType = MediaType.ANIME,
    val libraryStatusFilter: String? = null,
    val librarySearchQuery: String = "",
    val librarySortBy: String = "updated",

    // Discover state (On-demand per category)
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

    // App & Auth state
    val snackbarMessage: String? = null,
    val appMode: String = "offline", // "offline" or "online_sync"
    val malUser: MalUser = MalUser(),
    val isSyncingMal: Boolean = false,
    val isExchangingToken: Boolean = false
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

    // Reactive search flow
    private val _searchQueryFlow = MutableStateFlow(Pair("", MediaType.ANIME))

    private var discoverJob: Job? = null
    private var detailJob: Job? = null

    init {
        // Initial on-demand load of default discovery category
        loadDiscoverCategory(_uiState.value.selectedDiscoverCategory, _uiState.value.discoverFilter)

        // Seed sample data if database is totally empty
        viewModelScope.launch {
            val existing = repository.allAnime.firstOrNull()
            if (existing.isNullOrEmpty()) {
                repository.loadDemoData()
            }
        }

        // Off-Main-Thread Computation & State Management (Task 1.3)
        viewModelScope.launch {
            combine(repository.allAnime, repository.allManga) { anime, manga ->
                Pair(anime, manga)
            }
            .flowOn(Dispatchers.Default)
            .collect { (anime, manga) ->
                // Filter background tasks without blocking main thread
                val watching = anime.filter { it.status == "watching" }
                val reading = manga.filter { it.status == "reading" }
                val completedAnimeIds = anime.filter { it.status == "completed" }.map { it.malId }.toSet()

                val totalEp = anime.sumOf { it.progress }
                val totalCh = manga.sumOf { it.progressChapters }
                val totalVol = manga.sumOf { it.progressVolumes }
                val completedAnimeCount = anime.count { it.status == "completed" }
                val completedMangaCount = manga.count { it.status == "completed" }
                val totalRated = anime.count { it.score > 0 } + manga.count { it.score > 0 }
                val totalScore = anime.sumOf { it.score } + manga.sumOf { it.score }
                val mean = if (totalRated > 0) totalScore.toDouble() / totalRated else 0.0
                val daysWatched = (totalEp * 24.0) / (60.0 * 24.0)

                val animeWatching = anime.count { it.status == "watching" }
                val animeOnHold = anime.count { it.status == "on_hold" }
                val animeDropped = anime.count { it.status == "dropped" }
                val animePlanToWatch = anime.count { it.status == "plan_to_watch" }

                val mangaReading = manga.count { it.status == "reading" }
                val mangaOnHold = manga.count { it.status == "on_hold" }
                val mangaDropped = manga.count { it.status == "dropped" }
                val mangaPlanToRead = manga.count { it.status == "plan_to_read" }

                val stats = TrackerStats(
                    totalAnime = anime.size,
                    totalManga = manga.size,
                    episodesWatched = totalEp,
                    chaptersRead = totalCh,
                    volumesRead = totalVol,
                    completedCount = completedAnimeCount + completedMangaCount,
                    meanScore = (mean * 10).toInt() / 10.0,
                    daysWatched = (daysWatched * 10).toInt() / 10.0,
                    animeWatching = animeWatching,
                    animeCompleted = completedAnimeCount,
                    animeOnHold = animeOnHold,
                    animeDropped = animeDropped,
                    animePlanToWatch = animePlanToWatch,
                    mangaReading = mangaReading,
                    mangaCompleted = completedMangaCount,
                    mangaOnHold = mangaOnHold,
                    mangaDropped = mangaDropped,
                    mangaPlanToRead = mangaPlanToRead
                )

                _uiState.update { current ->
                    current.copy(
                        animeList = anime,
                        mangaList = manga,
                        watchingAnime = watching,
                        readingManga = reading,
                        completedAnimeMalIds = completedAnimeIds,
                        stats = stats
                    )
                }
            }
        }

        // Reactive Debounced Search (Task 1.5)
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

        // Memory Cache Auto-Pruning every 15 minutes (Task 1.6)
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15 * 60 * 1000L) // 15 minutes
                CacheManager.pruneExpired()
            }
        }
    }

    fun setTab(tab: String) {
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

    fun quickIncrementAnime(id: String) {
        viewModelScope.launch {
            repository.incrementAnime(id, 1)
            showSnackbar("+1 Episode ditambahkan!")
        }
    }

    fun quickDecrementAnime(id: String) {
        viewModelScope.launch {
            repository.incrementAnime(id, -1)
            showSnackbar("-1 Episode dikurangi.")
        }
    }

    fun quickIncrementManga(id: String) {
        viewModelScope.launch {
            repository.incrementManga(id, 1)
            showSnackbar("+1 Chapter ditambahkan!")
        }
    }

    fun quickDecrementManga(id: String) {
        viewModelScope.launch {
            repository.incrementManga(id, -1)
            showSnackbar("-1 Chapter dikurangi.")
        }
    }

    fun saveAnime(anime: AnimeEntity) {
        viewModelScope.launch {
            repository.upsertAnime(anime)
            closeDetail()
            showSnackbar("\"${anime.title}\" berhasil diperbarui!")
        }
    }

    fun saveManga(manga: MangaEntity) {
        viewModelScope.launch {
            repository.upsertManga(manga)
            closeDetail()
            showSnackbar("\"${manga.title}\" berhasil diperbarui!")
        }
    }

    fun deleteAnime(id: String) {
        viewModelScope.launch {
            repository.deleteAnime(id)
            closeDetail()
            showSnackbar("Anime berhasil dihapus dari Library.")
        }
    }

    fun deleteManga(id: String) {
        viewModelScope.launch {
            repository.deleteManga(id)
            closeDetail()
            showSnackbar("Manga berhasil dihapus dari Library.")
        }
    }

    fun addFromCatalog(item: MediaItem, status: MediaStatus) {
        viewModelScope.launch {
            if (item.type == MediaType.ANIME) {
                val anime = AnimeEntity(
                    id = "anime_${item.malId}",
                    malId = item.malId,
                    anilistId = item.anilistId,
                    title = item.title,
                    titleEnglish = item.titleEnglish,
                    imageUrl = item.imageUrl,
                    status = status.apiValue,
                    score = 0,
                    progress = if (status == MediaStatus.COMPLETED) (item.episodes ?: 0) else 0,
                    totalEpisodes = item.episodes ?: 0,
                    airingStatus = item.status ?: "Finished Airing",
                    genres = item.genres.joinToString(", "),
                    synopsis = item.synopsis ?: "",
                    year = item.year,
                    season = item.season,
                    studio = item.studio,
                    notes = ""
                )
                repository.upsertAnime(anime)
            } else {
                val manga = MangaEntity(
                    id = "manga_${item.malId}",
                    malId = item.malId,
                    anilistId = item.anilistId,
                    title = item.title,
                    titleEnglish = item.titleEnglish,
                    imageUrl = item.imageUrl,
                    status = status.apiValue,
                    score = 0,
                    progressChapters = if (status == MediaStatus.COMPLETED) (item.chapters ?: 0) else 0,
                    progressVolumes = 0,
                    totalChapters = item.chapters ?: 0,
                    totalVolumes = item.volumes ?: 0,
                    publishingStatus = item.status ?: "Finished",
                    genres = item.genres.joinToString(", "),
                    synopsis = item.synopsis ?: "",
                    year = item.year,
                    notes = ""
                )
                repository.upsertManga(manga)
            }
            showSnackbar("\"${item.title}\" ditambahkan ke Library!")
        }
    }

    // --- Reactive Search (Task 1.5) ---
    fun onSearchQueryChange(query: String, type: MediaType) {
        _uiState.update { it.copy(searchQuery = query, searchType = type) }
        _searchQueryFlow.value = Pair(query, type)
    }

    fun search(query: String, type: MediaType) {
        onSearchQueryChange(query, type)
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            _uiState.update { it.copy(isSearching = true) }
            viewModelScope.launch(Dispatchers.IO) {
                val results = if (type == MediaType.ANIME) {
                    repository.searchAnime(trimmed)
                } else {
                    repository.searchManga(trimmed)
                }
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            }
        }
    }

    // --- Discover & Randomizer (Task 2.4 & Task A1) ---
    fun loadDiscoverCategory(
        category: DiscoverCategory,
        filter: DiscoverFilter = _uiState.value.discoverFilter,
        forceRefresh: Boolean = false
    ) {
        discoverJob?.cancel()
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

        discoverJob = viewModelScope.launch {
            val items = repository.getDiscoverMedia(category, filter, page = 1, forceRefresh = forceRefresh)
            _uiState.update {
                it.copy(
                    discoverItems = items,
                    isDiscoverLoading = false,
                    canLoadMoreDiscover = items.size >= 20
                )
            }
        }
    }

    private fun filterAndDedupDiscoverItems(
        newItems: List<MediaItem>,
        existingItems: List<MediaItem>,
        isRandom: Boolean
    ): List<MediaItem> {
        val existingMalIds = existingItems.map { it.malId }.toSet()
        val completedIds = if (isRandom) _uiState.value.completedAnimeMalIds else emptySet()

        return newItems.filter { item ->
            !existingMalIds.contains(item.malId) && (!isRandom || !completedIds.contains(item.malId))
        }
    }

    fun loadMoreDiscover() {
        val current = _uiState.value
        if (current.isDiscoverLoading || current.isDiscoverLoadingMore || !current.canLoadMoreDiscover) return
        val nextPage = current.discoverPage + 1
        val isRandom = current.selectedDiscoverCategory == DiscoverCategory.RANDOM_FILTER

        _uiState.update { it.copy(isDiscoverLoadingMore = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val nextItems = repository.getDiscoverMedia(
                current.selectedDiscoverCategory,
                current.discoverFilter,
                page = nextPage,
                randomSort = if (isRandom) current.randomSort else null
            )

            val newFiltered = filterAndDedupDiscoverItems(nextItems, current.discoverItems, isRandom = isRandom)

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

    /**
     * Prominent Anime Randomizer with automatic exclusion of completed anime (Task 2.4 & A1).
     * The sort method is chosen ONCE at page 1 and preserved across pagination until reshuffled.
     */
    fun randomizeAnime(filter: DiscoverFilter = _uiState.value.discoverFilter) {
        discoverJob?.cancel()
        val sortOptions = listOf("POPULARITY_DESC", "SCORE_DESC", "TRENDING_DESC", "ID_DESC")
        val chosenSort = sortOptions.random()

        _uiState.update {
            it.copy(
                selectedDiscoverCategory = DiscoverCategory.RANDOM_FILTER,
                discoverFilter = filter,
                randomSort = chosenSort,
                isDiscoverLoading = true,
                discoverPage = 1,
                canLoadMoreDiscover = true
            )
        }

        discoverJob = viewModelScope.launch(Dispatchers.IO) {
            val items = repository.getDiscoverMedia(
                DiscoverCategory.RANDOM_FILTER,
                filter,
                page = 1,
                forceRefresh = true,
                randomSort = chosenSort
            )
            val filtered = filterAndDedupDiscoverItems(items, emptyList(), isRandom = true)
            val finalResult = if (filtered.isNotEmpty()) filtered else items

            _uiState.update {
                it.copy(
                    discoverItems = finalResult,
                    isDiscoverLoading = false,
                    canLoadMoreDiscover = items.size >= 20
                )
            }
            showSnackbar("Rekomendasi anime acak siap! (${finalResult.size} judul belum ditonton)")
        }
    }

    // --- Extended Detail & Search Item Click (Task 2.5) ---
    fun openDetail(item: Any, type: MediaType) {
        if (item is MediaItem) {
            // Check if already in local database
            val localAnime = if (type == MediaType.ANIME) _uiState.value.animeList.find { it.malId == item.malId } else null
            val localManga = if (type == MediaType.MANGA) _uiState.value.mangaList.find { it.malId == item.malId } else null

            val targetItem: Any = localAnime ?: localManga ?: if (type == MediaType.ANIME) {
                AnimeEntity(
                    id = "anime_${item.malId}",
                    malId = item.malId,
                    anilistId = item.anilistId,
                    title = item.title,
                    titleEnglish = item.titleEnglish,
                    imageUrl = item.imageUrl,
                    status = "plan_to_watch",
                    score = 0,
                    progress = 0,
                    totalEpisodes = item.episodes ?: 0,
                    airingStatus = item.status ?: "Finished Airing",
                    genres = item.genres.joinToString(", "),
                    synopsis = item.synopsis ?: "",
                    year = item.year,
                    season = item.season,
                    studio = item.studio,
                    notes = ""
                )
            } else {
                MangaEntity(
                    id = "manga_${item.malId}",
                    malId = item.malId,
                    anilistId = item.anilistId,
                    title = item.title,
                    titleEnglish = item.titleEnglish,
                    imageUrl = item.imageUrl,
                    status = "plan_to_read",
                    score = 0,
                    progressChapters = 0,
                    progressVolumes = 0,
                    totalChapters = item.chapters ?: 0,
                    totalVolumes = item.volumes ?: 0,
                    publishingStatus = item.status ?: "Finished",
                    genres = item.genres.joinToString(", "),
                    synopsis = item.synopsis ?: "",
                    year = item.year,
                    notes = ""
                )
            }

            _uiState.update {
                it.copy(
                    selectedDetailItem = targetItem,
                    detailMediaType = type,
                    isDetailOpen = true,
                    extendedDetail = null,
                    isLoadingExtendedDetail = false
                )
            }
            fetchExtendedDetails(item.anilistId, item.malId, type)
            return
        }

        val anime = item as? AnimeEntity
        val manga = item as? MangaEntity
        val aniListId = anime?.anilistId ?: manga?.anilistId
        val malId = anime?.malId ?: manga?.malId

        _uiState.update {
            it.copy(
                selectedDetailItem = item,
                detailMediaType = type,
                isDetailOpen = true,
                extendedDetail = null,
                isLoadingExtendedDetail = false
            )
        }

        // Fetch extended details asynchronously in background without blocking dialog
        fetchExtendedDetails(aniListId, malId, type)
    }

    fun fetchExtendedDetails(aniListId: Int?, malId: Int?, type: MediaType) {
        detailJob?.cancel()
        _uiState.update { it.copy(isLoadingExtendedDetail = true) }
        detailJob = viewModelScope.launch {
            val detail = repository.getExtendedDetails(aniListId, malId, type)
            _uiState.update {
                it.copy(
                    extendedDetail = detail,
                    isLoadingExtendedDetail = false
                )
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                isDetailOpen = false,
                selectedDetailItem = null,
                extendedDetail = null,
                isLoadingExtendedDetail = false
            )
        }
    }

    // --- Cache Management ---
    fun clearImageCache(context: Context) {
        viewModelScope.launch {
            repository.clearImageCache(context)
            showSnackbar("Cache gambar berhasil dibersihkan.")
        }
    }

    fun clearMetadataCache() {
        repository.clearMetadataCache()
        showSnackbar("Cache metadata & pencarian berhasil dibersihkan.")
    }

    fun clearAllCache(context: Context) {
        viewModelScope.launch {
            repository.clearAllCache(context)
            showSnackbar("Semua cache berhasil dibersihkan. Data Library & akun tetap aman.")
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            repository.loadDemoData()
            showSnackbar("Dataset demo berhasil dimuat ke Room DB lokal!")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            showSnackbar("Semua data lokal Room DB telah dibersihkan.")
        }
    }

    fun setAppMode(mode: String) {
        _uiState.update { it.copy(appMode = mode) }
        showSnackbar(if (mode == "offline") "Beralih ke mode Offline Room DB." else "Beralih ke mode Online Sync MAL.")
    }

    fun loginWithMal(context: Context) {
        val authUrl = repository.buildMalAuthorizeUrl()
        if (authUrl == null) {
            showSnackbar("Layanan otentikasi MyAnimeList belum siap.")
        } else {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                showSnackbar("Tidak dapat membuka browser sistem: ${e.localizedMessage}")
            }
        }
    }

    fun handleOAuthCallback(code: String, state: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExchangingToken = true) }
            showSnackbar("Menghubungkan akun MyAnimeList...")
            val result = repository.handleMalOAuthCallback(code, state)
            result.onSuccess { user ->
                _uiState.update {
                    it.copy(
                        malUser = user,
                        appMode = "online_sync",
                        isExchangingToken = false
                    )
                }
                showSnackbar("Berhasil terhubung ke MyAnimeList sebagai ${user.username}!")
                syncWithMal()
            }.onFailure { error ->
                _uiState.update { it.copy(isExchangingToken = false) }
                showSnackbar("Gagal login MyAnimeList: ${error.localizedMessage ?: "Terjadi kesalahan"}")
            }
        }
    }

    fun syncWithMal() {
        viewModelScope.launch {
            val user = _uiState.value.malUser
            if (!user.isLoggedIn) {
                showSnackbar("Silakan hubungkan akun MyAnimeList terlebih dahulu.")
                return@launch
            }
            _uiState.update { it.copy(isSyncingMal = true) }
            showSnackbar("Menyinkronkan daftar anime & manga dari MyAnimeList...")
            val result = repository.syncWithMal()
            if (result.isSuccess) {
                showSnackbar("Sinkronisasi sukses! ${result.animeSynced} anime & ${result.mangaSynced} manga tersinkron.")
            } else {
                showSnackbar("Gagal sinkronisasi: ${result.errorMessage ?: "Koneksi terganggu"}")
            }
            _uiState.update { it.copy(isSyncingMal = false) }
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
        showSnackbar("Akun MyAnimeList telah diputuskan.")
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
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CanimViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CanimViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
