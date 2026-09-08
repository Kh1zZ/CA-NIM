package com.canim.app.ui.viewmodel.gacha

import androidx.compose.runtime.Immutable
import com.canim.app.data.model.MediaItem

@Immutable
data class GachaUiState(
    val credits: Int = 5,
    val deck: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false
)

sealed interface GachaEvent {
    object OpenGacha : GachaEvent
    object ConsumeCredit : GachaEvent
    data class SwipeDismiss(val item: MediaItem) : GachaEvent
    object LoadDeck : GachaEvent
    data class UpdateCredits(val newCredits: Int) : GachaEvent
}
