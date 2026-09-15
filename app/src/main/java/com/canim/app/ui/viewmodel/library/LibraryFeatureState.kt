package com.canim.app.ui.viewmodel.library

import androidx.compose.runtime.Immutable
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.TrackerStats
import com.canim.app.data.model.UserMediaItem

@Immutable
data class LibraryUiState(
    val animeList: List<UserMediaItem> = emptyList(),
    val mangaList: List<UserMediaItem> = emptyList(),
    val watchingAnime: List<UserMediaItem> = emptyList(),
    val readingManga: List<UserMediaItem> = emptyList(),
    val completedAnimeMalIds: Set<Int> = emptySet(),
    val completedMangaMalIds: Set<Int> = emptySet(),
    val stats: TrackerStats = TrackerStats(),
    val filterType: MediaType = MediaType.ANIME,
    val statusFilter: String? = "watching",
    val searchQuery: String = "",
    val sortBy: String = "updated",
    val isLoading: Boolean = false
)

sealed interface LibraryEvent {
    data class SetFilterType(val type: MediaType) : LibraryEvent
    data class SetStatusFilter(val status: String?) : LibraryEvent
    data class SetSearchQuery(val query: String) : LibraryEvent
    data class SetSortBy(val sort: String) : LibraryEvent
    data class SaveAnime(val item: UserMediaItem) : LibraryEvent
    data class SaveManga(val item: UserMediaItem) : LibraryEvent
    data class DeleteAnime(val animeId: String) : LibraryEvent
    data class DeleteManga(val mangaId: String) : LibraryEvent
    data class IncrementProgress(val identifier: Any) : LibraryEvent
    object ReloadLibrary : LibraryEvent
}
