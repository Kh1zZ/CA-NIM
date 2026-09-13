package com.canim.app.ui.viewmodel.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.CacheRefreshType
import com.canim.app.domain.usecase.GetDiscoverCategoryUseCase
import com.canim.app.domain.usecase.ObserveCacheRefreshUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val getDiscoverCategoryUseCase: GetDiscoverCategoryUseCase,
    private val observeCacheRefreshUseCase: ObserveCacheRefreshUseCase
) : ViewModel() {

    private val _discoverState = MutableStateFlow(DiscoverUiState())
    val discoverState: StateFlow<DiscoverUiState> = _discoverState.asStateFlow()

    private val _snackbarEvent = Channel<String>(Channel.BUFFERED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private var discoverJob: Job? = null
    private var discoverRequestToken = 0L

    init {
        // Prefetch initial discover category after short delay
        viewModelScope.launch(Dispatchers.IO) {
            delay(2500L)
            if (_discoverState.value.items.isEmpty() && !_discoverState.value.isLoading) {
                loadDiscoverCategory(_discoverState.value.selectedCategory, _discoverState.value.filter)
            }
        }

        // Observe background SWR cache refresh events and update active discover UI
        viewModelScope.launch {
            observeCacheRefreshUseCase.events.collect { event ->
                if (event.type == CacheRefreshType.DISCOVER) {
                    val token = discoverRequestToken
                    val currentDiscoverState = _discoverState.value
                    val categoryKey = getDiscoverCategoryUseCase.discoverFilterKey(
                        currentDiscoverState.selectedCategory,
                        currentDiscoverState.filter,
                        page = 1
                    )
                    if (getDiscoverCategoryUseCase.matchesDiscoverKey(event.key, categoryKey)) {
                        val fresh = getDiscoverCategoryUseCase.getCachedDiscover(categoryKey)
                        if (fresh != null && token == discoverRequestToken) {
                            _discoverState.update {
                                it.copy(
                                    items = fresh,
                                    canLoadMore = fresh.size >= 20
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun onDiscoverEvent(event: DiscoverEvent) {
        when (event) {
            is DiscoverEvent.CategorySelected -> {
                discoverJob?.cancel()
                val token = ++discoverRequestToken
                val category = event.category
                val mediaType = event.mediaType ?: _discoverState.value.mediaType
                val filter = event.filter ?: _discoverState.value.filter

                _discoverState.update {
                    it.copy(
                        selectedCategory = category,
                        mediaType = mediaType,
                        filter = filter,
                        isLoading = true,
                        page = 1,
                        canLoadMore = true
                    )
                }

                discoverJob = viewModelScope.launch(Dispatchers.IO) {
                    val items = getDiscoverCategoryUseCase(
                        category = category,
                        filter = filter,
                        page = 1,
                        forceRefresh = event.forceRefresh,
                        mediaType = mediaType
                    )
                    if (token == discoverRequestToken) {
                        _discoverState.update {
                            it.copy(
                                items = items,
                                isLoading = false,
                                canLoadMore = items.size >= 20
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
                        forceRefresh = false,
                        mediaType = _discoverState.value.mediaType
                    )
                )
            }
            is DiscoverEvent.LoadMore -> {
                val current = _discoverState.value
                if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return
                val nextPage = current.page + 1
                val token = discoverRequestToken

                _discoverState.update { it.copy(isLoadingMore = true) }

                viewModelScope.launch(Dispatchers.IO) {
                    val nextItems = getDiscoverCategoryUseCase(
                        category = current.selectedCategory,
                        filter = current.filter,
                        page = nextPage,
                        mediaType = current.mediaType
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
                    }
                }
            }
            is DiscoverEvent.Refresh -> {
                val current = _discoverState.value
                onDiscoverEvent(
                    DiscoverEvent.CategorySelected(
                        category = current.selectedCategory,
                        filter = current.filter,
                        forceRefresh = true,
                        mediaType = current.mediaType
                    )
                )
            }
        }
    }

    fun loadDiscoverCategory(
        category: DiscoverCategory,
        filter: DiscoverFilter = _discoverState.value.filter,
        forceRefresh: Boolean = false,
        mediaType: MediaType? = null
    ) {
        onDiscoverEvent(DiscoverEvent.CategorySelected(category, filter, forceRefresh, mediaType))
    }

    fun loadMoreDiscover() {
        onDiscoverEvent(DiscoverEvent.LoadMore)
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.send(message)
        }
    }
}
