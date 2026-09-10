package com.canim.app.ui.viewmodel.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canim.app.BuildConfig
import com.canim.app.domain.usecase.CheckForUpdatesUseCase
import com.canim.app.domain.usecase.InstallUpdateUseCase
import com.canim.app.domain.usecase.StartDownloadUpdateUseCase
import com.canim.app.notification.CanimNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val checkForUpdatesUseCase: CheckForUpdatesUseCase,
    private val startDownloadUpdateUseCase: StartDownloadUpdateUseCase,
    private val installUpdateUseCase: InstallUpdateUseCase,
    private val notificationManager: CanimNotificationManager
) : ViewModel() {

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _snackbarEvent = Channel<String>(Channel.BUFFERED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    init {
        initUpdateChecker()
    }

    private fun getPrefs(): android.content.SharedPreferences? {
        return try {
            appContext.getSharedPreferences("canim_update_prefs", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    private fun initUpdateChecker() {
        val prefs = getPrefs()
        val isAutoEnabled = prefs?.getBoolean("auto_check_updates", true) ?: true
        _updateState.update { it.copy(isAutoCheckEnabled = isAutoEnabled) }

        if (isAutoEnabled) {
            val lastCheck = prefs?.getLong("last_update_check_time", 0L) ?: 0L
            val oneDayMs = 24L * 60L * 60L * 1000L
            if (System.currentTimeMillis() - lastCheck > oneDayMs) {
                checkForUpdates(manual = false)
            }
        }
    }

    fun onUpdateEvent(event: UpdateEvent) {
        when (event) {
            is UpdateEvent.CheckForUpdates -> checkForUpdates(manual = event.manual)
            is UpdateEvent.SetAutoUpdateCheck -> setAutoUpdateCheck(enabled = event.enabled)
            is UpdateEvent.DismissDialog -> dismissUpdateDialog()
            is UpdateEvent.StartDownload -> startDownloadUpdate(context = event.context)
            is UpdateEvent.InstallUpdate -> installDownloadedUpdate(context = event.context)
        }
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        try {
            getPrefs()?.edit()?.putBoolean("auto_check_updates", enabled)?.apply()
        } catch (_: Exception) {}
        _updateState.update { it.copy(isAutoCheckEnabled = enabled) }
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (_updateState.value.isChecking) return
        _updateState.update { it.copy(isChecking = true) }
        viewModelScope.launch {
            val result = checkForUpdatesUseCase(BuildConfig.VERSION_NAME)
            val info = result.getOrNull()
            if (info != null) {
                val prefs = getPrefs()
                try {
                    prefs?.edit()?.putLong("last_update_check_time", System.currentTimeMillis())?.apply()
                } catch (_: Exception) {}

                if (info.isUpdateAvailable) {
                    _updateState.update { it.copy(updateInfo = info, isChecking = false) }
                    // Trigger minimalist Android system notification
                    val lastNotified = prefs?.getString("last_notified_version", null)
                    if (lastNotified != info.latestVersion) {
                        notificationManager.showUpdateNotification(info.latestVersion, info.releaseNotes)
                        try {
                            prefs?.edit()?.putString("last_notified_version", info.latestVersion)?.apply()
                        } catch (_: Exception) {}
                    }
                    if (manual) {
                        showSnackbar("Pembaruan tersedia: ${info.latestVersion}!")
                    }
                } else {
                    _updateState.update { it.copy(isChecking = false) }
                    if (manual) {
                        showSnackbar("CA\'NIM sudah versi terbaru (${BuildConfig.VERSION_NAME})")
                    }
                }
            } else {
                _updateState.update { it.copy(isChecking = false) }
                if (manual) {
                    val rawMsg = result.exceptionOrNull()?.message ?: "Jaringan bermasalah"
                    val userMsg = if (rawMsg.contains("403") || rawMsg.contains("rate limit", ignoreCase = true)) {
                        "Batas permintaan GitHub terlampaui. Coba beberapa saat lagi."
                    } else {
                        rawMsg
                    }
                    showSnackbar("Gagal memeriksa pembaruan: $userMsg")
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateState.update { it.copy(updateInfo = null, isDownloading = false, downloadedApkFile = null) }
    }

    fun startDownloadUpdate(context: Context) {
        val info = _updateState.value.updateInfo ?: return
        val downloadUrl = info.apkDownloadUrl ?: info.htmlUrl
        val apkName = info.apkName ?: "canim-release-${info.latestVersion}.apk"

        if (_updateState.value.isDownloading) return

        _updateState.update {
            it.copy(
                isDownloading = true,
                downloadProgress = 0f,
                downloadedApkFile = null
            )
        }

        viewModelScope.launch {
            val result = startDownloadUpdateUseCase(
                context = context,
                downloadUrl = downloadUrl,
                fileName = apkName,
                onProgress = { progress ->
                    _updateState.update { it.copy(downloadProgress = progress) }
                }
            )

            result.fold(
                onSuccess = { file ->
                    _updateState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
                            downloadedApkFile = file
                        )
                    }
                    showSnackbar("Update berhasil diunduh! Membuka installer...")
                    installDownloadedUpdate(context)
                },
                onFailure = { error ->
                    _updateState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 0f
                        )
                    }
                    showSnackbar("Gagal mengunduh update: ${error.message}")
                }
            )
        }
    }

    fun installDownloadedUpdate(context: Context) {
        val file = _updateState.value.downloadedApkFile ?: return
        val result = installUpdateUseCase(context, file)
        if (result.isFailure) {
            showSnackbar("Gagal membuka installer APK: ${result.exceptionOrNull()?.message}")
        }
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.send(message)
        }
    }
}
