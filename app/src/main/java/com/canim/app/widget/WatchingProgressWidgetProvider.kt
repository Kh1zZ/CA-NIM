package com.canim.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.canim.app.CanimApplication
import com.canim.app.MainActivity
import com.canim.app.R
import com.canim.app.data.local.LibraryDao
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
            ?: kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        appScope.launch(Dispatchers.IO) {
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
            val watchingItems = entries.filter { it.status.equals("watching", ignoreCase = true) }

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_watching_progress)
                views.setTextViewText(R.id.widget_watching_count_badge, "${watchingItems.size} Anime")

                // Bind RemoteViews adapter for scrollable ListView
                val serviceIntent = Intent(context, WatchingProgressRemoteViewsService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.widget_watching_list, serviceIntent)
                views.setEmptyView(R.id.widget_watching_list, R.id.widget_empty_container)

                // PendingIntent template for ListView child actions
                val actionIntent = Intent(context, WatchingWidgetActionReceiver::class.java)
                val pendingAction = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 40,
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_watching_list, pendingAction)

                // Header click opens app
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingOpenApp = PendingIntent.getActivity(
                    context,
                    appWidgetId * 40 + 1,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_header, pendingOpenApp)

                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_watching_list)
            }
        }
    }
}
