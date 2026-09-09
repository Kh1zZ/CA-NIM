package com.canim.app.ui.viewmodel.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canim.app.data.model.*
import com.canim.app.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibraryUseCase: GetLibraryUseCase,
    private val saveLibraryItemUseCase: SaveLibraryItemUseCase,
    private val deleteLibraryItemUseCase: DeleteLibraryItemUseCase,
    private val updateTrackingUseCase: UpdateTrackingUseCase
) : ViewModel() {

    private val _libraryState = MutableStateFlow(LibraryUiState())
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

    private val _snackbarEvent = Channel<String>(Channel.BUFFERED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    init {
        // Cold-start instant cache-first load from disk/memory
        val cachedAnime = getLibraryUseCase.getCachedTracking("ANIME")
        val cachedManga = getLibraryUseCase.getCachedTracking("MANGA")
        if (!cachedAnime.isNullOrEmpty() || !cachedManga.isNullOrEmpty()) {
            updateLibraryData(cachedAnime ?: emptyList(), cachedManga ?: emptyList())
        }

        // Silent background sync / load user library
        loadUserLibrary()
    }

    fun loadUserLibrary(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _libraryState.update { it.copy(isLoading = true) }
            val isLoggedIn = getLibraryUseCase.isUserLoggedIn()
            if (isLoggedIn) {
                // SWR: If cached items exist and synced within 30 minutes, skip network on startup unless forced
                val hasCachedData = _libraryState.value.animeList.isNotEmpty() || _libraryState.value.mangaList.isNotEmpty()
                val lastSynced = getLibraryUseCase.getLastSyncedTime()
                val isCacheFresh = (System.currentTimeMillis() - lastSynced) < 30 * 60 * 1000L
                if (!forceRefresh && hasCachedData && isCacheFresh) {
                    _libraryState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val animeDeferred = async(Dispatchers.IO) { getLibraryUseCase.getUserAnimeList(forceRefresh) }
                val mangaDeferred = async(Dispatchers.IO) { getLibraryUseCase.getUserMangaList(forceRefresh) }

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
                        _libraryState.value.animeList
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
                        _libraryState.value.mangaList
                    }
                }

                updateLibraryData(animes, mangas)
                _libraryState.update { it.copy(isLoading = false) }
            } else {
                // In-memory demo data for unauthenticated mode
                if (_libraryState.value.animeList.isEmpty() && _libraryState.value.mangaList.isEmpty()) {
                    updateLibraryData(getLibraryUseCase.getDemoAnime(), getLibraryUseCase.getDemoManga())
                }
                _libraryState.update { it.copy(isLoading = false) }
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
            }
        }
    }

    fun onLibraryEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SetFilterType -> {
                _libraryState.update { it.copy(filterType = event.type) }
            }
            is LibraryEvent.SetStatusFilter -> {
                _libraryState.update { it.copy(statusFilter = event.status) }
            }
            is LibraryEvent.SetSearchQuery -> {
                _libraryState.update { it.copy(searchQuery = event.query) }
            }
            is LibraryEvent.SetSortBy -> {
                _libraryState.update { it.copy(sortBy = event.sort) }
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

    fun quickIncrementAnime(identifier: Any) {
        val currentList = _libraryState.value.animeList
        val item = findAnimeItem(identifier) ?: return
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
        val awarded = updateTrackingUseCase.recordProgressAndAwardCredits(item.id, updatedItem.tracking.progress)
        if (awarded > 0) {
            showSnackbar("+1 Episode ditambahkan! (+$awarded Tiket Gacha)")
        } else {
            showSnackbar("+1 Episode ditambahkan!")
        }

        val optimisticList = currentList.map { if (it.id == item.id) updatedItem else it }
        updateLibraryData(optimisticList, _libraryState.value.mangaList)

        viewModelScope.launch {
            val result = updateTrackingUseCase.updateAnime(item.malId, updatedItem.tracking)
            if (result.isFailure) {
                updateLibraryData(currentList, _libraryState.value.mangaList)
                showSnackbar("Gagal update MAL: ${result.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
            }
        }
    }

    fun quickDecrementAnime(identifier: Any) {
        val currentList = _libraryState.value.animeList
        val item = findAnimeItem(identifier) ?: return
        if (item.tracking.progress <= 0) return
        val updatedItem = item.copy(
            tracking = item.tracking.copy(
                progress = item.tracking.progress - 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        val optimisticList = currentList.map { if (it.id == item.id) updatedItem else it }
        updateLibraryData(optimisticList, _libraryState.value.mangaList)
        showSnackbar("-1 Episode dikurangkan!")

        viewModelScope.launch {
            val result = updateTrackingUseCase.updateAnime(item.malId, updatedItem.tracking)
            if (result.isFailure) {
                updateLibraryData(currentList, _libraryState.value.mangaList)
                showSnackbar("Gagal update MAL: ${result.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
            }
        }
    }

    fun quickIncrementManga(identifier: Any) {
        val currentList = _libraryState.value.mangaList
        val item = findMangaItem(identifier) ?: return
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
        val awarded = updateTrackingUseCase.recordProgressAndAwardCredits(item.id, updatedItem.tracking.progress)
        if (awarded > 0) {
            showSnackbar("+1 Chapter ditambahkan! (+$awarded Tiket Gacha)")
        } else {
            showSnackbar("+1 Chapter ditambahkan!")
        }

        val optimisticList = currentList.map { if (it.id == item.id) updatedItem else it }
        updateLibraryData(_libraryState.value.animeList, optimisticList)

        viewModelScope.launch {
            val result = updateTrackingUseCase.updateManga(item.malId, updatedItem.tracking)
            if (result.isFailure) {
                updateLibraryData(_libraryState.value.animeList, currentList)
                showSnackbar("Gagal update MAL: ${result.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
            }
        }
    }

    fun quickDecrementManga(identifier: Any) {
        val currentList = _libraryState.value.mangaList
        val item = findMangaItem(identifier) ?: return
        if (item.tracking.progress <= 0) return
        val updatedItem = item.copy(
            tracking = item.tracking.copy(
                progress = item.tracking.progress - 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        val optimisticList = currentList.map { if (it.id == item.id) updatedItem else it }
        updateLibraryData(_libraryState.value.animeList, optimisticList)
        showSnackbar("-1 Chapter dikurangkan!")

        viewModelScope.launch {
            val result = updateTrackingUseCase.updateManga(item.malId, updatedItem.tracking)
            if (result.isFailure) {
                updateLibraryData(_libraryState.value.animeList, currentList)
                showSnackbar("Gagal update MAL: ${result.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
            }
        }
    }

    fun saveAnime(item: UserMediaItem) {
        val preparedItem = updateTrackingUseCase.applyStatusChange(item, item.tracking.status)
        val currentList = _libraryState.value.animeList
        val exists = currentList.any { it.id == preparedItem.id }
        val optimisticList = if (exists) {
            currentList.map { if (it.id == preparedItem.id) preparedItem else it }
        } else {
            currentList + preparedItem
        }
        saveLibraryItemUseCase.recordProgressAndAwardCredits(preparedItem.id, preparedItem.tracking.progress)
        updateLibraryData(optimisticList, _libraryState.value.mangaList)
        showSnackbar("Perubahan \"${preparedItem.title}\" disimpan!")

        viewModelScope.launch {
            val result = saveLibraryItemUseCase.saveAnime(preparedItem.malId, preparedItem.tracking)
            if (result.isFailure) {
                updateLibraryData(currentList, _libraryState.value.mangaList)
                showSnackbar("Gagal menyimpan ke MAL: ${result.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
            }
        }
    }

    fun saveManga(item: UserMediaItem) {
        val preparedItem = updateTrackingUseCase.applyStatusChange(item, item.tracking.status)
        val currentList = _libraryState.value.mangaList
        val exists = currentList.any { it.id == preparedItem.id }
        val optimisticList = if (exists) {
            currentList.map { if (it.id == preparedItem.id) preparedItem else it }
        } else {
            currentList + preparedItem
        }
        saveLibraryItemUseCase.recordProgressAndAwardCredits(preparedItem.id, preparedItem.tracking.progress)
        updateLibraryData(_libraryState.value.animeList, optimisticList)
        showSnackbar("Perubahan \"${preparedItem.title}\" disimpan!")

        viewModelScope.launch {
            val result = saveLibraryItemUseCase.saveManga(preparedItem.malId, preparedItem.tracking)
            if (result.isFailure) {
                updateLibraryData(_libraryState.value.animeList, currentList)
                showSnackbar("Gagal menyimpan ke MAL: ${result.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
            }
        }
    }

    fun deleteAnime(idOrItem: Any) {
        val currentList = _libraryState.value.animeList
        val item = findAnimeItem(idOrItem) ?: return
        val optimisticList = currentList.filter { it.id != item.id }
        updateLibraryData(optimisticList, _libraryState.value.mangaList)
        showSnackbar("\"${item.title}\" dihapus dari Library")

        viewModelScope.launch {
            val result = deleteLibraryItemUseCase.deleteAnime(item.malId)
            if (result.isFailure) {
                updateLibraryData(currentList, _libraryState.value.mangaList)
                showSnackbar("Gagal menghapus dari MAL: ${result.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
            }
        }
    }

    fun deleteManga(idOrItem: Any) {
        val currentList = _libraryState.value.mangaList
        val item = findMangaItem(idOrItem) ?: return
        val optimisticList = currentList.filter { it.id != item.id }
        updateLibraryData(_libraryState.value.animeList, optimisticList)
        showSnackbar("\"${item.title}\" dihapus dari Library")

        viewModelScope.launch {
            val result = deleteLibraryItemUseCase.deleteManga(item.malId)
            if (result.isFailure) {
                updateLibraryData(_libraryState.value.animeList, currentList)
                showSnackbar("Gagal menghapus dari MAL: ${result.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
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

    fun saveFlashcardPlanToWatch(item: MediaItem, onResult: (Boolean) -> Unit) {
        val currentList = _libraryState.value.animeList
        val isAlreadyInList = currentList.any { it.id == item.id }
        if (isAlreadyInList) {
            showSnackbar("\"${item.title}\" sudah ada di Library!")
            onResult(true)
            return
        }

        val tracking = MalTracking(
            status = "plan_to_watch",
            score = 0,
            progress = 0,
            comments = "",
            updatedAt = System.currentTimeMillis()
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
        val userItem = UserMediaItem(identity = item.identity, metadata = metadata, tracking = tracking)

        val optimisticList = currentList + userItem
        updateLibraryData(optimisticList, _libraryState.value.mangaList)
        showSnackbar("Ditambahkan ke Rencana Ditonton")

        if (item.malId != null) {
            viewModelScope.launch {
                val result = saveLibraryItemUseCase.saveAnime(item.malId, tracking)
                if (result.isFailure) {
                    updateLibraryData(currentList, _libraryState.value.mangaList)
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

    fun loadDemoData() {
        updateLibraryData(getLibraryUseCase.getDemoAnime(), getLibraryUseCase.getDemoManga())
        showSnackbar("Dataset demo dimuat!")
    }

    fun clearAllData() {
        updateLibraryData(emptyList(), emptyList())
        showSnackbar("Daftar tampilan telah dibersihkan.")
    }

    fun findAnimeItem(identifier: Any): UserMediaItem? {
        val list = _libraryState.value.animeList
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

    fun findMangaItem(identifier: Any): UserMediaItem? {
        val list = _libraryState.value.mangaList
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

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.send(message)
        }
    }
}
