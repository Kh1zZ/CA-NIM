package com.canim.app.ui.viewmodel.search

import androidx.compose.runtime.Immutable
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType

@Immutable
data class SearchUiState(
    val query: String = "",
    val type: MediaType = MediaType.ANIME,
    val results: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val page: Int = 1,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val format: String? = null
)

sealed interface SearchEvent {
    data class QueryChanged(
        val query: String,
        val type: MediaType,
        val forceRefresh: Boolean = false
    ) : SearchEvent

    data class TypeChanged(val type: MediaType) : SearchEvent

    data class FilterApplied(
        val genres: List<String>,
        val year: Int?,
        val format: String?
    ) : SearchEvent

    object FilterReset : SearchEvent

    object LoadMore : SearchEvent

    object Refresh : SearchEvent
}
