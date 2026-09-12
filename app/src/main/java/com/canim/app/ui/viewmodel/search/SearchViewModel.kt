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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

private data class SearchTrigger(
    val query: String,
    val type: MediaType,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val format: String? = null,
    val forceRefresh: Boolean = false,
    val triggerId: Long = 0L
)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel(
    private val searchMediaUseCase: SearchMediaUseCase,
    private val observeCacheRefreshUseCase: ObserveCacheRefreshUseCase,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    @Inject
    constructor(
        searchMediaUseCase: SearchMediaUseCase,
        observeCacheRefreshUseCase: ObserveCacheRefreshUseCase
    ) : this(searchMediaUseCase, observeCacheRefreshUseCase, Dispatchers.IO)

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _searchQueryFlow = MutableStateFlow(SearchTrigger("", MediaType.ANIME))

    private val _snackbarEvent = Channel<String>(Channel.BUFFERED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private var loadMoreJob: Job? = null
    private var triggerSequence = 0L

    init {
        // Reactive Debounced Search (300ms)
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300L)
                .flatMapLatest { trigger ->
                    flow {
                        val trimmed = trigger.query.trim()
                        val type = trigger.type
                        val genres = trigger.genres
                        val year = trigger.year
                        val format = trigger.format
                        val hasFilters = genres.isNotEmpty() || year != null || !format.isNullOrBlank()
                        if (trimmed.length < 2 && !hasFilters) {
                            emit(emptyList<MediaItem>())
                        } else {
                            _searchState.update { it.copy(isSearching = true, page = 1) }
                            val results = searchMediaUseCase(
                                query = trimmed,
                                type = type,
                                genres = genres,
                                year = year,
                                format = format,
                                page = 1,
                                forceRefresh = trigger.forceRefresh
                            )
                            emit(results)
                        }
                    }
                }
                .flowOn(ioDispatcher)
                .collect { results ->
                    _searchState.update {
                        it.copy(
                            results = results,
                            isSearching = false,
                            page = 1,
                            canLoadMore = results.size >= 20
                        )
                    }
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
                    val filterKey = searchMediaUseCase.searchFilterKey(trimmed, genres, year, format, page = 1)
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
                val newGenres = if (typeChanged) emptyList() else _searchState.value.genres
                val newYear = if (typeChanged) null else _searchState.value.year
                val newFormat = if (typeChanged) null else _searchState.value.format

                _searchState.update { current ->
                    current.copy(
                        query = event.query,
                        type = event.type,
                        genres = newGenres,
                        year = newYear,
                        format = newFormat,
                        page = 1
                    )
                }
                _searchQueryFlow.value = SearchTrigger(
                    query = event.query,
                    type = event.type,
                    genres = newGenres,
                    year = newYear,
                    format = newFormat,
                    forceRefresh = event.forceRefresh,
                    triggerId = ++triggerSequence
                )
            }
            is SearchEvent.TypeChanged -> {
                if (_searchState.value.type != event.type) {
                    _searchState.update {
                        it.copy(
                            type = event.type,
                            genres = emptyList(),
                            year = null,
                            format = null,
                            page = 1
                        )
                    }
                    _searchQueryFlow.value = SearchTrigger(
                        query = _searchState.value.query,
                        type = event.type,
                        genres = emptyList(),
                        year = null,
                        format = null,
                        triggerId = ++triggerSequence
                    )
                }
            }
            is SearchEvent.FilterApplied -> {
                _searchState.update {
                    it.copy(
                        genres = event.genres,
                        year = event.year,
                        format = event.format,
                        page = 1
                    )
                }
                _searchQueryFlow.value = SearchTrigger(
                    query = _searchState.value.query,
                    type = _searchState.value.type,
                    genres = event.genres,
                    year = event.year,
                    format = event.format,
                    triggerId = ++triggerSequence
                )
            }
            is SearchEvent.FilterReset -> {
                _searchState.update {
                    it.copy(
                        genres = emptyList(),
                        year = null,
                        format = null,
                        page = 1
                    )
                }
                _searchQueryFlow.value = SearchTrigger(
                    query = _searchState.value.query,
                    type = _searchState.value.type,
                    genres = emptyList(),
                    year = null,
                    format = null,
                    triggerId = ++triggerSequence
                )
            }
            is SearchEvent.LoadMore -> {
                val current = _searchState.value
                if (current.isSearching || current.isLoadingMore || !current.canLoadMore) return
                val nextPage = current.page + 1
                val trimmed = current.query.trim()
                val type = current.type
                val genres = current.genres
                val year = current.year
                val format = current.format

                _searchState.update { it.copy(isLoadingMore = true) }

                loadMoreJob?.cancel()
                loadMoreJob = viewModelScope.launch(ioDispatcher) {
                    val nextItems = searchMediaUseCase(
                        query = trimmed,
                        type = type,
                        genres = genres,
                        year = year,
                        format = format,
                        page = nextPage
                    )
                    _searchState.update { state ->
                        val existingIds = state.results.map { it.malId to it.anilistId }.toSet()
                        val newUnique = nextItems.filter { (it.malId to it.anilistId) !in existingIds }
                        state.copy(
                            results = state.results + newUnique,
                            page = nextPage,
                            isLoadingMore = false,
                            canLoadMore = nextItems.size >= 20
                        )
                    }
                }
            }
            is SearchEvent.Refresh -> {
                val s = _searchState.value
                _searchQueryFlow.value = SearchTrigger(
                    query = s.query,
                    type = s.type,
                    genres = s.genres,
                    year = s.year,
                    format = s.format,
                    forceRefresh = true,
                    triggerId = ++triggerSequence
                )
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

    fun loadMore() {
        onSearchEvent(SearchEvent.LoadMore)
    }

    fun applySearchFilters(genres: List<String>, year: Int?, format: String?) {
        onSearchEvent(SearchEvent.FilterApplied(genres, year, format))
    }

    fun resetSearchFilters() {
        onSearchEvent(SearchEvent.FilterReset)
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.send(message)
        }
    }
}
