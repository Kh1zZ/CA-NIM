package com.canim.app.ui.viewmodel.update

import android.content.Context
import androidx.compose.runtime.Immutable
import com.canim.app.data.remote.UpdateInfo
import java.io.File

@Immutable
data class UpdateUiState(
    val isChecking: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val isAutoCheckEnabled: Boolean = true,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApkFile: File? = null
)

sealed interface UpdateEvent {
    data class CheckForUpdates(val manual: Boolean = true) : UpdateEvent
    data class SetAutoUpdateCheck(val enabled: Boolean) : UpdateEvent
    object DismissDialog : UpdateEvent
    data class StartDownload(val context: Context) : UpdateEvent
    data class InstallUpdate(val context: Context) : UpdateEvent
}
