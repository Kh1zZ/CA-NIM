package com.canim.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.canim.app.R
import com.canim.app.data.model.AiringAnimeItem
import com.canim.app.data.repository.CalendarRepositoryImpl
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate

class TodayAiringRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return AiringRemoteViewsFactory(this.applicationContext, intent)
    }
}

class AiringRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<AiringAnimeItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val calendarRepo = CalendarRepositoryImpl()
        val weekSchedule = runCatching {
            runBlocking { calendarRepo.getAiringCalendar(forceRefresh = false) }
        }.getOrDefault(emptyMap())

        val dayOffset = intent.getIntExtra("extra_day_offset", 0)
        val targetDay = LocalDate.now().plusDays(dayOffset.toLong()).dayOfWeek
        val rawItems = weekSchedule[targetDay] ?: emptyList()

        // Sort nearest to farthest by airing time
        items = rawItems.sortedWith(
            compareBy<AiringAnimeItem> { it.airingTimeFormatted.isNullOrBlank() }
                .thenBy { it.airingTimeFormatted ?: "99:99" }
        )
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in items.indices) return null
        val item = items[position]
        val rv = RemoteViews(context.packageName, R.layout.item_widget_airing)

        rv.setTextViewText(R.id.item_airing_title, item.title)

        val timeText = if (!item.airingTimeFormatted.isNullOrBlank()) "${item.airingTimeFormatted} WIB" else "WIB --:--"
        val epText = if (item.episodes != null && item.episodes > 0) " • Eps ${item.episodes}" else ""
        rv.setTextViewText(R.id.item_airing_time, "$timeText$epText")

        // Load cover image (software bitmap for RemoteViews IPC)
        if (!item.imageUrl.isNullOrBlank()) {
            val bitmap = loadSoftwareBitmap(context, item.imageUrl)
            if (bitmap != null) {
                rv.setImageViewBitmap(R.id.item_airing_image, bitmap)
            } else {
                rv.setImageViewResource(R.id.item_airing_image, R.drawable.ic_app_icon)
            }
        } else {
            rv.setImageViewResource(R.id.item_airing_image, R.drawable.ic_app_icon)
        }

        // Fill-in Intent for clicking item
        val fillInIntent = Intent().apply {
            putExtra("extra_open_airing_calendar", true)
            if (item.malId != null) putExtra("extra_mal_id", item.malId)
        }
        rv.setOnClickFillInIntent(R.id.widget_airing_item_root, fillInIntent)

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun loadSoftwareBitmap(ctx: Context, url: String): Bitmap? {
        return try {
            val loader = ImageLoader(ctx)
            val request = ImageRequest.Builder(ctx)
                .data(url)
                .size(64, 88)
                .allowHardware(false) // RemoteViews IPC does NOT support hardware bitmaps
                .build()
            val result = runBlocking { loader.execute(request) }
            if (result is SuccessResult) {
                (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
