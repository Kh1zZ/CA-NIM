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
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibraryEntry
import com.canim.app.data.local.LocalDatabase
import kotlinx.coroutines.runBlocking

class WatchingProgressRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WatchingRemoteViewsFactory(this.applicationContext, intent)
    }
}

class WatchingRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<LibraryEntry> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val db = LocalDatabase(context)
        val dao = LibraryDao(db)
        val entries = dao.getAllEntries("ANIME")
        items = entries.filter { it.status.equals("watching", ignoreCase = true) }
            .sortedByDescending { it.localUpdatedAt }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in items.indices) return null
        val item = items[position]
        val rv = RemoteViews(context.packageName, R.layout.item_widget_watching)

        rv.setTextViewText(R.id.item_watching_title, item.title)

        val maxEp = item.totalEpisodes
        val progressText = if (maxEp > 0) "Eps ${item.progress}/$maxEp" else "Eps ${item.progress}"
        rv.setTextViewText(R.id.item_watching_progress_text, progressText)

        val progressPercent = if (maxEp > 0) {
            ((item.progress.toFloat() / maxEp.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            0
        }
        rv.setProgressBar(R.id.item_watching_progress_bar, 100, progressPercent, false)

        // Load cover image (software bitmap for RemoteViews IPC)
        if (!item.imageUrl.isNullOrBlank()) {
            val bitmap = loadSoftwareBitmap(context, item.imageUrl)
            if (bitmap != null) {
                rv.setImageViewBitmap(R.id.item_watching_image, bitmap)
            } else {
                rv.setImageViewResource(R.id.item_watching_image, R.drawable.ic_app_icon)
            }
        } else {
            rv.setImageViewResource(R.id.item_watching_image, R.drawable.ic_app_icon)
        }

        // Fill-in Intent for clicking the item root (opens detail/app)
        val clickIntent = Intent().apply {
            action = "com.canim.app.widget.ACTION_OPEN_DETAIL"
            putExtra("extra_mal_id", item.malId)
        }
        rv.setOnClickFillInIntent(R.id.widget_watching_item_root, clickIntent)

        // Fill-in Intent for +1 button (triggers quick increment)
        val incrementIntent = Intent().apply {
            action = WatchingWidgetActionReceiver.ACTION_QUICK_INCREMENT
            putExtra(WatchingWidgetActionReceiver.EXTRA_MAL_ID, item.malId)
        }
        rv.setOnClickFillInIntent(R.id.item_watching_btn_increment, incrementIntent)

 return rv
 }

 override fun getLoadingView(): RemoteViews? = null

 override fun getViewTypeCount(): Int = 1

 override fun getItemId(position: Int): Long = items.getOrNull(position)?.malId?.toLong() ?: position.toLong()

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
