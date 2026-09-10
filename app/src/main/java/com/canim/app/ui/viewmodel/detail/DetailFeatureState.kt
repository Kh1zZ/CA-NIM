package com.canim.app.ui.viewmodel.detail

import androidx.compose.runtime.Immutable
import com.canim.app.data.model.CastCrewProfile
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MediaType

@Immutable
data class DetailUiState(
    val selectedItem: Any? = null,
    val mediaType: MediaType = MediaType.ANIME,
    val isOpen: Boolean = false,
    val extendedDetail: ExtendedMediaDetail? = null,
    val isLoadingExtendedDetail: Boolean = false,
    val selectedCastCrewProfile: CastCrewProfile? = null,
    val isLoadingCastCrewProfile: Boolean = false,
    val isAniListUnavailable: Boolean = false
)

sealed interface DetailEvent {
    data class OpenDetail(val item: Any, val type: MediaType) : DetailEvent
    object CloseDetail : DetailEvent
    data class OpenCastCrewProfile(val id: Int, val isStaff: Boolean) : DetailEvent
    object CloseCastCrewProfile : DetailEvent
    data class ItemUpdated(val updatedItem: Any) : DetailEvent
}
