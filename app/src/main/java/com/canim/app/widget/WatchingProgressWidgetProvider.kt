package com.canim.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.canim.app.CanimApplication
import com.canim.app.MainActivity
import com.canim.app.R
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibraryEntry
import com.canim.app.data.local.LocalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatchingProgressWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val appScope = (context.applicationContext as? CanimApplication)?.appScope
        if (appScope != null) {
            appScope.launch(Dispatchers.IO) {
                renderWidgets(context, appWidgetManager, appWidgetIds)
            }
        } else {
            renderWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        fun renderWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val db = LocalDatabase(context)
            val dao = LibraryDao(db)
            val entries = dao.getAllEntries("ANIME")
            val watchingItem = entries.firstOrNull { it.status.equals("watching", ignoreCase = true) }

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_watching_progress)
                bindWatchingItem(context, views, watchingItem, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        fun bindWatchingItem(
            context: Context,
            views: RemoteViews,
            entry: LibraryEntry?,
            appWidgetId: Int
        ) {
            if (entry == null) {
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_content_container, View.GONE)

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingOpen = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingOpen)
            } else {
                views.setViewVisibility(R.id.widget_empty_container, View.GONE)
                views.setViewVisibility(R.id.widget_content_container, View.VISIBLE)

                views.setTextViewText(R.id.widget_anime_title, entry.title)

                val totalEp = entry.totalEpisodes
                val totalEpStr = if (totalEp > 0) totalEp.toString() else "?"
                views.setTextViewText(R.id.widget_progress_text, "Ep ${entry.progress} / $totalEpStr")

                if (totalEp > 0) {
                    views.setProgressBar(R.id.widget_progress_bar, totalEp, entry.progress, false)
                } else {
                    views.setProgressBar(R.id.widget_progress_bar, 100, 0, false)
                }

                // Tapping whole card opens MainActivity & detail
                val openDetailIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("extra_open_mal_id", entry.malId)
                }
                val pendingOpen = PendingIntent.getActivity(
                    context,
                    appWidgetId * 10,
                    openDetailIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingOpen)

                // Quick +1 button action
                val isMaxProgress = totalEp > 0 && entry.progress >= totalEp
                if (isMaxProgress) {
                    views.setViewVisibility(R.id.widget_btn_quick_add, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_btn_quick_add, View.VISIBLE)
                    val quickAddIntent = Intent(context, WatchingWidgetActionReceiver::class.java).apply {
                        action = WatchingWidgetActionReceiver.ACTION_QUICK_INCREMENT
                        putExtra(WatchingWidgetActionReceiver.EXTRA_MAL_ID, entry.malId)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val pendingIncrement = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 10 + 1,
                        quickAddIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_quick_add, pendingIncrement)
                }
            }
        }
    }
}
