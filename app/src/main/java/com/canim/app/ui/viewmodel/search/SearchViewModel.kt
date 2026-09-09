package com.canim.app.ui.viewmodel.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.CacheRefreshType
import com.canim.app.domain.usecase.ObserveCacheRefreshUseCase
import com.canim.app.domain.usecase.SearchMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class SearchTrigger(
    val query: String,
    val type: MediaType,
    val forceRefresh: Boolean = false
)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchMediaUseCase: SearchMediaUseCase,
    private val observeCacheRefreshUseCase: ObserveCacheRefreshUseCase
) : ViewModel() {

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _searchQueryFlow = MutableStateFlow(SearchTrigger("", MediaType.ANIME))

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    init {
        // Reactive Debounced Search (300ms)
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300L)
                .flatMapLatest { trigger ->
                    flow {
                        val trimmed = trigger.query.trim()
                        val type = trigger.type
                        val currentSearchState = _searchState.value
                        val hasFilters = currentSearchState.genres.isNotEmpty() || currentSearchState.year != null || currentSearchState.format != null
                        if (trimmed.length < 2 && !hasFilters) {
                            emit(emptyList<MediaItem>())
                        } else {
                            _searchState.update { it.copy(isSearching = true) }
                            val results = searchMediaUseCase(
                                query = trimmed,
                                type = type,
                                genres = currentSearchState.genres,
                                year = currentSearchState.year,
                                format = currentSearchState.format,
                                forceRefresh = trigger.forceRefresh
                            )
                            emit(results)
                        }
                    }
                }
                .flowOn(Dispatchers.IO)
                .collect { results ->
                    _searchState.update { it.copy(results = results, isSearching = false) }
                }
        }

        // Observe background SWR cache refresh events and update active search UI
        viewModelScope.launch {
            observeCacheRefreshUseCase.events.collect { event ->
                if (event.type == CacheRefreshType.SEARCH) {
                    val currentSearchState = _searchState.value
                    val trimmed = currentSearchState.query.trim()
                    val type = currentSearchState.type.name
                    val genres = currentSearchState.genres
                    val year = currentSearchState.year
                    val format = currentSearchState.format
                    val filterKey = searchMediaUseCase.searchFilterKey(trimmed, genres, year, format)
                    if (searchMediaUseCase.matchesSearchKey(event.key, filterKey, type)) {
                        val fresh = searchMediaUseCase.getCachedSearch(filterKey, type)
                        if (fresh != null) {
                            _searchState.update { it.copy(results = fresh) }
                        }
                    }
                }
            }
        }
    }

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
                _searchQueryFlow.value = SearchTrigger(_searchState.value.query, _searchState.value.type)
            }
            is SearchEvent.Refresh -> {
                val s = _searchState.value
                _searchQueryFlow.value = SearchTrigger(s.query, s.type, forceRefresh = true)
            }
        }
    }

    fun onSearchQueryChange(query: String, type: MediaType = _searchState.value.type, forceRefresh: Boolean = false) {
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

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.emit(message)
        }
    }
}
