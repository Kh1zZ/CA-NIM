package com.canim.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.canim.app.CanimApplication
import com.canim.app.MainActivity
import com.canim.app.R
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.local.PendingMutation
import com.canim.app.data.local.PendingMutationDao
import com.canim.app.data.model.MalTracking
import com.canim.app.data.repository.MalAuthManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatchingWidgetActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_QUICK_INCREMENT = "com.canim.app.widget.ACTION_QUICK_INCREMENT"
        const val ACTION_PREV_ANIME = "com.canim.app.widget.ACTION_PREV_ANIME"
        const val ACTION_NEXT_ANIME = "com.canim.app.widget.ACTION_NEXT_ANIME"
        const val ACTION_OPEN_DETAIL = "com.canim.app.widget.ACTION_OPEN_DETAIL"
        const val EXTRA_MAL_ID = "extra_mal_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appScope = (context.applicationContext as? CanimApplication)?.appScope
            ?: kotlinx.coroutines.CoroutineScope(Dispatchers.IO)

        when (action) {
            ACTION_OPEN_DETAIL -> {
                val malId = intent.getIntExtra(EXTRA_MAL_ID, -1)
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (malId > 0) putExtra(EXTRA_MAL_ID, malId)
                }
                context.startActivity(openIntent)
            }
            ACTION_PREV_ANIME, ACTION_NEXT_ANIME -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

                val pendingResult = goAsync()
                appScope.launch(Dispatchers.IO) {
                    try {
                        val db = LocalDatabase(context)
                        val dao = LibraryDao(db)
                        val entries = dao.getAllEntries("ANIME")
                        val watchingItems = entries.filter { it.status.equals("watching", ignoreCase = true) }

                        if (watchingItems.isNotEmpty()) {
                            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                            val currentIndex = prefs.getInt("watching_index_$appWidgetId", 0)
                            val newIndex = if (action == ACTION_PREV_ANIME) {
                                (currentIndex - 1 + watchingItems.size) % watchingItems.size
                            } else {
                                (currentIndex + 1) % watchingItems.size
                            }
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            WatchingProgressWidgetProvider.renderWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
                        }
                    } catch (_: Exception) {
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_QUICK_INCREMENT -> {
                val malId = intent.getIntExtra(EXTRA_MAL_ID, -1)
                if (malId <= 0) return

                val pendingResult = goAsync()
                appScope.launch(Dispatchers.IO) {
                    try {
                        val db = LocalDatabase(context)
                        val dao = LibraryDao(db)
                        val mutationDao = PendingMutationDao(db)

                        val entry = dao.getEntry(malId, "ANIME")
                        if (entry != null) {
                            val maxEp = entry.totalEpisodes
                            val isMax = maxEp > 0 && entry.progress >= maxEp
                            if (!isMax) {
                                val newProgress = entry.progress + 1
                                val updatedEntry = entry.copy(
                                    progress = newProgress,
                                    localUpdatedAt = System.currentTimeMillis()
                                )
                                val tracking = MalTracking(
                                    status = entry.status,
                                    score = entry.score,
                                    progress = newProgress
                                )
                                val payloadJson = Gson().toJson(tracking)
                                val pendingMutation = PendingMutation(
                                    malId = malId,
                                    mediaType = "ANIME",
                                    mutationType = PendingMutation.TYPE_UPDATE,
                                    payloadJson = payloadJson,
                                    localUpdatedAt = System.currentTimeMillis(),
                                    createdAt = System.currentTimeMillis(),
                                    attempts = 0,
                                    status = PendingMutation.STATUS_PENDING
                                )
                                dao.upsertWithMutation(updatedEntry, pendingMutation, mutationDao)

                                // Update widgets immediately on home screen
                                WidgetUpdateHelper.updateWatchingWidgets(context)

                                // If user is authenticated, attempt background sync with MAL
                                val secureStorage = MalSecureStorage(context)
                                val authManager = MalAuthManager(secureStorage)
                                if (secureStorage.getUser().isLoggedIn) {
                                    val syncResult = authManager.updateAnimeTracking(malId, tracking)
                                    if (syncResult.isSuccess) {
                                        val active = mutationDao.getActiveMutationForMalId(malId, "ANIME")
                                        if (active != null) {
                                            mutationDao.markSucceeded(active.id)
                                            mutationDao.clearSucceeded()
                                        }
                                        dao.markSynced(malId, "ANIME")
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
