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
import com.canim.app.data.model.AiringAnimeItem
import com.canim.app.data.repository.CalendarRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class TodayAiringWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val appScope = (context.applicationContext as? CanimApplication)?.appScope
            ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        appScope.launch(Dispatchers.IO) {
            renderWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        suspend fun renderWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val calendarRepo = CalendarRepositoryImpl()
            val weekSchedule = runCatching { calendarRepo.getAiringCalendar(forceRefresh = false) }.getOrDefault(emptyMap())

            val today = LocalDate.now().dayOfWeek
            val todayItems = weekSchedule[today] ?: emptyList()

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_today_airing)
                bindTodayAiring(context, views, today, todayItems, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        fun bindTodayAiring(
            context: Context,
            views: RemoteViews,
            today: DayOfWeek,
            items: List<AiringAnimeItem>,
            appWidgetId: Int
        ) {
            val dayNameIndonesian = when (today) {
                DayOfWeek.MONDAY -> "SENIN"
                DayOfWeek.TUESDAY -> "SELASA"
                DayOfWeek.WEDNESDAY -> "RABU"
                DayOfWeek.THURSDAY -> "KAMIS"
                DayOfWeek.FRIDAY -> "JUMAT"
                DayOfWeek.SATURDAY -> "SABTU"
                DayOfWeek.SUNDAY -> "MINGGU"
            }

            views.setTextViewText(R.id.widget_airing_day_text, "TAYANG • $dayNameIndonesian")
            views.setTextViewText(R.id.widget_airing_count_badge, "${items.size} Anime")

            // Clicking any part of the widget opens MainActivity directly to Airing Calendar
            val openCalendarIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("extra_open_airing_calendar", true)
            }
            val pendingOpen = PendingIntent.getActivity(
                context,
                appWidgetId * 20,
                openCalendarIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_airing_root, pendingOpen)

            if (items.isEmpty()) {
                views.setViewVisibility(R.id.widget_airing_empty_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_airing_content_container, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_airing_empty_container, View.GONE)
                views.setViewVisibility(R.id.widget_airing_content_container, View.VISIBLE)

                // Populate up to 3 preview items
                val rowViews = listOf(
                    Triple(R.id.widget_airing_row_1, R.id.widget_airing_time_1, R.id.widget_airing_title_1),
                    Triple(R.id.widget_airing_row_2, R.id.widget_airing_time_2, R.id.widget_airing_title_2),
                    Triple(R.id.widget_airing_row_3, R.id.widget_airing_time_3, R.id.widget_airing_title_3)
                )

                for (i in rowViews.indices) {
                    val (rowId, timeId, titleId) = rowViews[i]
                    if (i < items.size) {
                        val item = items[i]
                        views.setViewVisibility(rowId, View.VISIBLE)
                        views.setTextViewText(timeId, item.airingTimeFormatted)
                        views.setTextViewText(titleId, item.title)
                    } else {
                        views.setViewVisibility(rowId, View.GONE)
                    }
                }
            }
        }
    }
}
