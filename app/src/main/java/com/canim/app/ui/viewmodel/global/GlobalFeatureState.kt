package com.canim.app.ui.viewmodel.global

import androidx.compose.runtime.Immutable
import com.canim.app.data.model.MalUser
import com.canim.app.data.model.SyncStatus

@Immutable
data class ThrottleNotificationState(
    val host: String,
    val totalSeconds: Long,
    val remainingSeconds: Long,
    val isActive: Boolean = true
)

@Immutable
data class GlobalUiState(
    val malUser: MalUser = MalUser(),
    val isAniListDown: Boolean = false,
    val isMalDown: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val appMode: String = "online_sync",
    val activeTab: String = "dashboard",
    val snackbarMessage: String? = null,
    val isSyncingMal: Boolean = false,
    val isExchangingToken: Boolean = false,
    val throttleNotification: ThrottleNotificationState? = null,
    val showColdStartOutageBanner: Boolean = false
)

sealed interface GlobalEvent {
    data class SetActiveTab(val tab: String) : GlobalEvent
    data class SetAppMode(val mode: String) : GlobalEvent
    data class ShowSnackbar(val message: String) : GlobalEvent
    object DismissSnackbar : GlobalEvent
    object RefreshHealth : GlobalEvent
    object DismissColdStartOutageBanner : GlobalEvent
}
