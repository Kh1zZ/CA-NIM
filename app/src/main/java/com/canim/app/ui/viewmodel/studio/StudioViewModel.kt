package com.canim.app.ui.viewmodel.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canim.app.data.model.StudioBioInfo
import com.canim.app.data.model.StudioFilmographySort
import com.canim.app.domain.usecase.GetStudioFilmographyUseCase
import com.canim.app.domain.usecase.SearchStudiosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudioViewModel @Inject constructor(
    private val getStudioFilmographyUseCase: GetStudioFilmographyUseCase,
    private val searchStudiosUseCase: SearchStudiosUseCase
) : ViewModel() {

    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private var studioJob: Job? = null
    private var studioSearchJob: Job? = null

    fun onStudioEvent(event: StudioEvent) {
        when (event) {
            is StudioEvent.OpenStudio -> {
                openStudio(event.studioId, event.studioName)
            }
            is StudioEvent.CloseStudio -> {
                closeStudio()
            }
            is StudioEvent.SetSort -> {
                setStudioFilmographySort(event.sort)
            }
            is StudioEvent.LoadFilmography -> {
                loadStudioFilmography(event.studioId, event.studioName, event.page)
            }
            is StudioEvent.LoadMore -> {
                loadMoreStudioFilmography()
            }
            is StudioEvent.SearchStudios -> {
                searchStudios(event.query)
            }
            is StudioEvent.ClearStudioSearch -> {
                clearStudioSearch()
            }
        }
    }

    fun openStudio(studioId: Int, studioName: String) {
        val resolvedId = if (studioId > 0) studioId else try { searchStudiosUseCase.getStudioInfo(0, studioName).studioId } catch (_: Exception) { studioId }
        val bio = try { searchStudiosUseCase.getStudioInfo(resolvedId, studioName) } catch (_: Exception) { null }
        _studioState.update {
            it.copy(
                studioId = resolvedId,
                studioName = studioName,
                bio = bio,
                sort = StudioFilmographySort.YEAR_DESC
            )
        }
        loadStudioFilmography(resolvedId, studioName, page = 1)
    }

    fun closeStudio() {
        studioJob?.cancel()
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
    }

    fun setStudioFilmographySort(sort: StudioFilmographySort) {
        _studioState.update { it.copy(sort = sort) }
        val sId = _studioState.value.studioId ?: 0
        val sName = _studioState.value.studioName
        if (sId > 0 || sName.isNotBlank()) {
            loadStudioFilmography(sId, sName, page = 1)
        }
    }

    fun loadStudioFilmography(studioId: Int, studioName: String, page: Int = 1) {
        val resolvedId = if (studioId > 0) studioId else try { searchStudiosUseCase.getStudioInfo(0, studioName).studioId } catch (_: Exception) { studioId }
        if (page == 1) {
            studioJob?.cancel()
            val bio = _studioState.value.bio
                ?: try { searchStudiosUseCase.getStudioInfo(resolvedId, studioName) } catch (_: Exception) { null }
            _studioState.update {
                it.copy(
                    studioId = resolvedId,
                    studioName = studioName,
                    bio = bio,
                    isLoading = true,
                    items = emptyList(),
                    totalEntries = 0,
                    page = 1,
                    canLoadMore = true
                )
            }
        } else {
            _studioState.update { it.copy(isLoadingMore = true) }
        }

        studioJob = viewModelScope.launch(Dispatchers.IO) {
            val sort = _studioState.value.sort
            val pageResult = getStudioFilmographyUseCase(
                studioId = if (resolvedId > 0) resolvedId else null,
                search = if (resolvedId <= 0) studioName else null,
                page = page,
                sort = sort
            )
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
                    studioId = pageResult?.studioId?.takeIf { id -> id > 0 } ?: it.studioId,
                    bio = updatedBio ?: it.bio,
                    items = newItems,
                    totalEntries = totalEntries,
                    isLoading = false,
                    isLoadingMore = false,
                    page = page,
                    canLoadMore = pageResult?.hasNextPage == true
                )
            }
        }
    }

    fun loadMoreStudioFilmography() {
        val s = _studioState.value
        if (s.isLoading || s.isLoadingMore || !s.canLoadMore) return
        val studioId = s.studioId ?: return
        val studioName = s.studioName
        loadStudioFilmography(studioId, studioName, s.page + 1)
    }

    fun searchStudios(query: String) {
        studioSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _studioState.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearchingStudios = false
                )
            }
            return
        }

        val localMatches = try {
            searchStudiosUseCase.searchCuratedStudios(trimmed)
        } catch (_: Exception) {
            emptyList()
        }
        _studioState.update {
            it.copy(
                searchResults = localMatches,
                isSearchingStudios = true
            )
        }

        studioSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250L)
            val remoteResults = try {
                searchStudiosUseCase(trimmed)
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
        }
    }

    fun clearStudioSearch() {
        studioSearchJob?.cancel()
        _studioState.update {
            it.copy(
                searchResults = emptyList(),
                isSearchingStudios = false
            )
        }
    }

    fun getStudioInfo(studioId: Int, studioName: String): StudioBioInfo =
        searchStudiosUseCase.getStudioInfo(studioId, studioName)

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.emit(message)
        }
    }
}
