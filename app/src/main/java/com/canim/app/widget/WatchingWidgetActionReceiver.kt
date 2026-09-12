package com.canim.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.canim.app.CanimApplication
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
        const val EXTRA_MAL_ID = "extra_mal_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_QUICK_INCREMENT) return

        val malId = intent.getIntExtra(EXTRA_MAL_ID, -1)
        if (malId <= 0) return

        val pendingResult = goAsync()
        val appScope = (context.applicationContext as? CanimApplication)?.appScope
            ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

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
