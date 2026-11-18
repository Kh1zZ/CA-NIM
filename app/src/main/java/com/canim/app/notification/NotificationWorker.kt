package com.canim.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.canim.app.BuildConfig
import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.model.MalFetchResult
import com.canim.app.domain.repository.LibraryRepository
import com.canim.app.domain.usecase.CheckForUpdatesUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker that periodically checks for new airing episodes,
 * plan-to-watch anime premieres, and app updates.
 *
 * Runs reliably with WorkManager constraints (NetworkType.CONNECTED),
 * ensuring execution even when the app is completely closed, surviving
 * process death, doze mode, and device reboots.
 */
class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationWorkerEntryPoint {
        fun airingAlertManager(): AiringAlertManager
        fun libraryRepository(): LibraryRepository
        fun librarySyncEngine(): LibrarySyncEngine
        fun checkForUpdatesUseCase(): CheckForUpdatesUseCase
        fun notificationManager(): CanimNotificationManager
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                NotificationWorkerEntryPoint::class.java
            )
            val libraryRepository = entryPoint.libraryRepository()
            val librarySyncEngine = entryPoint.librarySyncEngine()
            val airingAlertManager = entryPoint.airingAlertManager()
            val checkForUpdatesUseCase = entryPoint.checkForUpdatesUseCase()
            val notificationManager = entryPoint.notificationManager()

            // Flush any queued offline/widget mutations once during background wakeup
            runCatching { librarySyncEngine.drainQueue() }

            // 1. Fetch library items (from Room local database or cache)
            val animeResult = libraryRepository.getUserAnimeList(forceRefresh = false)
            val animeList = when (animeResult) {
                is MalFetchResult.Success -> animeResult.data
                is MalFetchResult.Partial -> animeResult.data
                else -> libraryRepository.getCachedTracking("ANIME") ?: emptyList()
            }

            if (animeList.isNotEmpty()) {
                // 2. Airing alerts for watching anime (bypass stale caches)
                airingAlertManager.checkAndDispatchAiringAlerts(animeList, forceRefresh = true)

                // 3. Airing alerts for plan-to-watch anime (bypass stale caches)
                airingAlertManager.checkAndDispatchPlanToWatchAiringAlerts(animeList, forceRefresh = true)
            }

            // 4. App update check (respects 24h throttling)
            val prefs = applicationContext.getSharedPreferences("canim_update_prefs", Context.MODE_PRIVATE)
            val isAutoEnabled = prefs.getBoolean("auto_check_updates", true)
            if (isAutoEnabled) {
                val lastCheck = prefs.getLong("last_update_check_time", 0L)
                val oneDayMs = 24L * 60L * 60L * 1000L
                if (System.currentTimeMillis() - lastCheck > oneDayMs) {
                    val updateResult = checkForUpdatesUseCase(BuildConfig.VERSION_NAME)
                    val info = updateResult.getOrNull()
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

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
