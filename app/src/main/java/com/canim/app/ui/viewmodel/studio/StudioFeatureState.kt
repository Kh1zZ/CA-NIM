package com.canim.app.ui.viewmodel.studio

import androidx.compose.runtime.Immutable
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.StudioBioInfo
import com.canim.app.data.model.StudioFilmographySort

@Immutable
data class StudioUiState(
    val studioId: Int? = null,
    val studioName: String = "",
    val bio: StudioBioInfo? = null,
    val sort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC,
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val totalEntries: Int = 0,
    val canLoadMore: Boolean = true,
    val searchResults: List<StudioBioInfo> = emptyList(),
    val isSearchingStudios: Boolean = false
)

sealed interface StudioEvent {
    data class OpenStudio(val studioId: Int, val studioName: String) : StudioEvent
    object CloseStudio : StudioEvent
    data class SetSort(val sort: StudioFilmographySort) : StudioEvent
    data class LoadFilmography(val studioId: Int, val studioName: String, val page: Int = 1) : StudioEvent
    object LoadMore : StudioEvent
    data class SearchStudios(val query: String) : StudioEvent
    object ClearStudioSearch : StudioEvent
}
