package com.canim.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.canim.app.CanimApplication
import com.canim.app.MainActivity
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.LibraryDao
import com.canim.app.domain.repository.LibraryRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatchingWidgetActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetActionEntryPoint {
        fun libraryRepository(): LibraryRepository
        fun libraryDao(): LibraryDao
    }

    companion object {
        const val ACTION_QUICK_INCREMENT = "com.canim.app.widget.ACTION_QUICK_INCREMENT"
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

            ACTION_QUICK_INCREMENT -> {
                val malId = intent.getIntExtra(EXTRA_MAL_ID, -1)
                if (malId <= 0) return

                val pendingResult = goAsync()
                appScope.launch(Dispatchers.IO) {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            WidgetActionEntryPoint::class.java
                        )
                        val dao = entryPoint.libraryDao()
                        val repository = entryPoint.libraryRepository()
                        val entry = dao.getEntry(malId, "ANIME")
                        if (entry != null) {
                            val maxEp = entry.totalEpisodes
                            val isMax = maxEp > 0 && entry.progress >= maxEp
                            if (!isMax) {
                                val gson = Gson()
                                val listType = object : TypeToken<List<String>>() {}.type
                                val existingTracking = entry.toUserMediaItem { json ->
                                    runCatching { gson.fromJson<List<String>>(json, listType) }.getOrNull() ?: emptyList()
                                }.tracking
                                val updatedTracking = existingTracking.copy(
                                    progress = entry.progress + 1,
                                    updatedAt = System.currentTimeMillis()
                                )
                                repository.updateAnimeTracking(malId, updatedTracking)
                                CacheManager.invalidateTracking()
                                WidgetUpdateHelper.updateWatchingWidgets(context)
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
