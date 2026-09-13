package com.canim.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.canim.app.CanimApplication
import com.canim.app.MainActivity
import com.canim.app.R
import com.canim.app.data.model.AiringAnimeItem
import com.canim.app.data.repository.CalendarRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class TodayAiringWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_SET_DAY_OFFSET) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val offset = intent.getIntExtra(EXTRA_OFFSET, 0)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                prefs.edit().putInt("airing_offset_$appWidgetId", offset).apply()

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appScope = (context.applicationContext as? CanimApplication)?.appScope
                    ?: kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
                appScope.launch(Dispatchers.IO) {
                    renderWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
                }
            }
        }
    }

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
        const val ACTION_SET_DAY_OFFSET = "com.canim.app.widget.ACTION_SET_DAY_OFFSET"
        const val EXTRA_OFFSET = "extra_offset"

        suspend fun renderWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val calendarRepo = CalendarRepositoryImpl()
            val weekSchedule = runCatching { calendarRepo.getAiringCalendar(forceRefresh = false) }.getOrDefault(emptyMap())

            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

            for (appWidgetId in appWidgetIds) {
                val dayOffset = prefs.getInt("airing_offset_$appWidgetId", 0)
                val targetDate = LocalDate.now().plusDays(dayOffset.toLong())
                val targetDay = targetDate.dayOfWeek
                val items = (weekSchedule[targetDay] ?: emptyList()).sortedWith(
                    compareBy<AiringAnimeItem> { it.airingTimeFormatted.isNullOrBlank() }
                        .thenBy { it.airingTimeFormatted ?: "99:99" }
                )

                val views = RemoteViews(context.packageName, R.layout.widget_today_airing)
                bindTodayAiring(context, views, dayOffset, targetDate, items, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_airing_list)
            }
        }

        fun bindTodayAiring(
            context: Context,
            views: RemoteViews,
            dayOffset: Int,
            targetDate: LocalDate,
            items: List<AiringAnimeItem>,
            appWidgetId: Int
        ) {
            val targetDay = targetDate.dayOfWeek
            val dayNameIndo = when (targetDay) {
                java.time.DayOfWeek.MONDAY -> "SENIN"
                java.time.DayOfWeek.TUESDAY -> "SELASA"
                java.time.DayOfWeek.WEDNESDAY -> "RABU"
                java.time.DayOfWeek.THURSDAY -> "KAMIS"
                java.time.DayOfWeek.FRIDAY -> "JUMAT"
                java.time.DayOfWeek.SATURDAY -> "SABTU"
                java.time.DayOfWeek.SUNDAY -> "MINGGU"
            }

            views.setTextViewText(R.id.widget_airing_count_badge, "${items.size} Anime")

            // 3-Day switcher labels
            views.setTextViewText(
                R.id.widget_airing_day_prev,
                if (dayOffset == -1) "● KEMARIN" else "KEMARIN"
            )
            views.setTextViewText(
                R.id.widget_airing_day_today,
                if (dayOffset == 0) "● HARI INI ($dayNameIndo)" else "HARI INI"
            )
            views.setTextViewText(
                R.id.widget_airing_day_next,
                if (dayOffset == 1) "● BESOK" else "BESOK"
            )

            // Day switch pending intents
            fun createDayOffsetPendingIntent(offset: Int, reqCode: Int): PendingIntent {
                val intent = Intent(context, TodayAiringWidgetProvider::class.java).apply {
                    action = ACTION_SET_DAY_OFFSET
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(EXTRA_OFFSET, offset)
                }
                return PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 100 + reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            views.setOnClickPendingIntent(R.id.widget_airing_day_prev, createDayOffsetPendingIntent(-1, 1))
            views.setOnClickPendingIntent(R.id.widget_airing_day_today, createDayOffsetPendingIntent(0, 2))
            views.setOnClickPendingIntent(R.id.widget_airing_day_next, createDayOffsetPendingIntent(1, 3))

            // Bind RemoteViews adapter for ListView
            val serviceIntent = Intent(context, TodayAiringRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("extra_day_offset", dayOffset)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_airing_list, serviceIntent)
            views.setEmptyView(R.id.widget_airing_list, R.id.widget_airing_empty_container)

            // ListView item click template
            val openCalendarIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_open_airing_calendar", true)
            }
            val pendingItemClick = PendingIntent.getActivity(
                context,
                appWidgetId * 20,
                openCalendarIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_airing_list, pendingItemClick)
        }
    }
}
