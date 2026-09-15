package com.canim.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.canim.app.BuildConfig
import com.canim.app.data.model.MalFetchResult
import com.canim.app.domain.repository.LibraryRepository
import com.canim.app.domain.usecase.CheckForUpdatesUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles periodic alarms and boot completion to check for new airing episodes,
 * plan-to-watch anime premieres, and app updates.
 *
 * Utilizes BroadcastReceiver.goAsync() with a short-lived coroutine so the process
 * finishes immediately after checking without retaining background memory.
 */
@AndroidEntryPoint
class NotificationAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var airingAlertManager: AiringAlertManager
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var checkForUpdatesUseCase: CheckForUpdatesUseCase
    @Inject lateinit var notificationManager: CanimNotificationManager

    companion object {
        const val ACTION_CHECK_NOTIFICATIONS = "com.canim.app.ACTION_CHECK_NOTIFICATIONS"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            NotificationScheduler.schedulePeriodicCheck(context)
        }

        if (action == ACTION_CHECK_NOTIFICATIONS || action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    // 1. Fetch library items (from cache or local Room)
                    val animeResult = libraryRepository.getUserAnimeList(forceRefresh = false)
                    val animeList = when (animeResult) {
                        is MalFetchResult.Success -> animeResult.data
                        is MalFetchResult.Partial -> animeResult.data
                        else -> libraryRepository.getCachedTracking("ANIME") ?: emptyList()
                    }

                    if (animeList.isNotEmpty()) {
                        // 2. Airing alerts for watching anime
                        airingAlertManager.checkAndDispatchAiringAlerts(animeList, forceRefresh = false)

                        // 3. Airing alerts for plan to watch anime
                        airingAlertManager.checkAndDispatchPlanToWatchAiringAlerts(animeList, forceRefresh = false)
                    }

                    // 4. App update check (respects 24h throttling)
                    val prefs = context.getSharedPreferences("canim_update_prefs", Context.MODE_PRIVATE)
                    val isAutoEnabled = prefs.getBoolean("auto_check_updates", true)
                    if (isAutoEnabled) {
                        val lastCheck = prefs.getLong("last_update_check_time", 0L)
                        val oneDayMs = 24L * 60L * 60L * 1000L
                        if (System.currentTimeMillis() - lastCheck > oneDayMs) {
                            val result = checkForUpdatesUseCase(BuildConfig.VERSION_NAME)
                            val info = result.getOrNull()
                            if (info != null) {
                                prefs.edit().putLong("last_update_check_time", System.currentTimeMillis()).apply()
                                if (info.isUpdateAvailable) {
                                    val lastNotified = prefs.getString("last_notified_version", null)
                                    if (lastNotified != info.latestVersion) {
                                        notificationManager.showUpdateNotification(info.latestVersion, info.releaseNotes)
                                        prefs.edit().putString("last_notified_version", info.latestVersion).apply()
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fail silently to prevent crashing background receiver
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
