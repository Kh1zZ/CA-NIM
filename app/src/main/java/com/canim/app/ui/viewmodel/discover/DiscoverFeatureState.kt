package com.canim.app.ui.viewmodel.discover

import androidx.compose.runtime.Immutable
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaItem

@Immutable
data class DiscoverUiState(
    val selectedCategory: DiscoverCategory = DiscoverCategory.CURRENT_SEASON,
    val mediaType: com.canim.app.data.model.MediaType = com.canim.app.data.model.MediaType.ANIME,
    val filter: DiscoverFilter = DiscoverFilter(),
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val page: Int = 1,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true
)

sealed interface DiscoverEvent {
    data class CategorySelected(
        val category: DiscoverCategory,
        val filter: DiscoverFilter? = null,
        val forceRefresh: Boolean = false,
        val mediaType: com.canim.app.data.model.MediaType? = null
    ) : DiscoverEvent

    data class FilterUpdated(val filter: DiscoverFilter) : DiscoverEvent

    object LoadMore : DiscoverEvent

    object Refresh : DiscoverEvent
}
