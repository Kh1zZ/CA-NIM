package com.canim.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.canim.app.R
import com.canim.app.data.model.MalUser
import com.canim.app.data.model.TrackerStats
import com.canim.app.data.model.UserMediaItem
import com.canim.app.util.AnimeFranchiseFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class StatsExportFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF Dokumen", "pdf", "application/pdf"),
    JPG("Gambar JPG", "jpg", "image/jpeg"),
    PNG("Gambar PNG", "png", "image/png")
}

enum class ExportAspectRatio(
    val label: String,
    val width: Int,
    val height: Int,
    val isLandscape: Boolean
) {
    STORY_9_16("9:16", 1080, 1920, false),
    PORTRAIT_4_5("4:5", 1080, 1350, false),
    PORTRAIT_3_4("3:4", 1080, 1440, false),
    SQUARE_1_1("1:1", 1080, 1080, false),
    LANDSCAPE_16_9("16:9", 1920, 1080, true)
}

private data class CanvasPieSlice(val label: String, val count: Int, val color: Int)

object StatsExporter {

    suspend fun exportAndShareStats(
        context: Context,
        stats: TrackerStats,
        malUser: MalUser,
        topAnime: List<UserMediaItem>,
        topManga: List<UserMediaItem>,
        format: StatsExportFormat,
        aspectRatio: ExportAspectRatio = ExportAspectRatio.STORY_9_16
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val filteredTopAnime = topAnime.take(5)
            val filteredTopManga = topManga.take(5)

            val bitmap = renderStatsBitmap(context, stats, malUser, filteredTopAnime, filteredTopManga, aspectRatio)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val ratioTag = aspectRatio.name.lowercase()
            val filename = "canim_stats_${malUser.username.ifBlank { "user" }}_${ratioTag}_$timeStamp.${format.extension}"

            // 1. Save locally for FileProvider sharing
            val statsDir = File(context.cacheDir, "stats").apply { mkdirs() }
            val localFile = File(statsDir, filename)

            FileOutputStream(localFile).use { fos ->
                when (format) {
                    StatsExportFormat.JPG -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                    StatsExportFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    StatsExportFormat.PDF -> {
                        val pdfDoc = PdfDocument()
                        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                        val page = pdfDoc.startPage(pageInfo)
                        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        pdfDoc.finishPage(page)
                        pdfDoc.writeTo(fos)
                        pdfDoc.close()
                    }
                }
            }

            // 2. Try saving to MediaStore (Gallery / Downloads) for permanent access
            runCatching {
                saveToMediaStore(context, localFile, filename, format)
            }

            // 3. Obtain shareable Uri via FileProvider
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localFile
            )

            // 4. Trigger share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Statistik Anime & Manga CA'NIM - ${malUser.username}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Statistik MyAnimeList saya via CA'NIM: ${stats.totalAnime} Anime, ${stats.totalManga} Manga, ${stats.episodesWatched} Episode ditonton!"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan Statistik").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            Result.success(contentUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveToMediaStore(
        context: Context,
        sourceFile: File,
        filename: String,
        format: StatsExportFormat
    ) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (format == StatsExportFormat.PDF) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Canim")
                } else {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Canim")
                }
            }
        }

        val targetCollection = if (format == StatsExportFormat.PDF) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(targetCollection, contentValues) ?: return
        resolver.openOutputStream(uri)?.use { os ->
            sourceFile.inputStream().use { `is` -> `is`.copyTo(os) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    private suspend fun loadBitmap(context: Context, url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun renderStatsBitmap(
        context: Context,
        stats: TrackerStats,
        malUser: MalUser,
        topAnime: List<UserMediaItem>,
        topManga: List<UserMediaItem>,
        aspectRatio: ExportAspectRatio
    ): Bitmap {
        val width = aspectRatio.width.toFloat()
        val height = aspectRatio.height.toFloat()
        val bitmap = Bitmap.createBitmap(aspectRatio.width, aspectRatio.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val animeCoversDeferred = withContext(Dispatchers.IO) {
            topAnime.take(5).map { item -> async { loadBitmap(context, item.imageUrl) } }
        }
        val mangaCoversDeferred = withContext(Dispatchers.IO) {
            topManga.take(5).map { item -> async { loadBitmap(context, item.imageUrl) } }
        }
        val animeBitmaps = animeCoversDeferred.awaitAll()
        val mangaBitmaps = mangaCoversDeferred.awaitAll()

        // 1. Deep Midnight Navy Background Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height,
                intArrayOf(
                    Color.parseColor("#050813"),
                    Color.parseColor("#0B132B"),
                    Color.parseColor("#070B16"),
                    Color.parseColor("#040711")
                ),
                floatArrayOf(0f, 0.35f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // 2. Ambient Dual-Glow (Cyan for Anime zone, Indigo for Manga zone)
        val animeGlow = Paint().apply {
            shader = RadialGradient(
                width * 0.25f, height * 0.42f, width * 0.55f,
                Color.argb(28, 56, 189, 248), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.25f, height * 0.42f, width * 0.55f, animeGlow)

        val mangaGlow = Paint().apply {
            shader = RadialGradient(
                width * 0.75f, height * 0.78f, width * 0.55f,
                Color.argb(28, 129, 140, 248), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.75f, height * 0.78f, width * 0.55f, mangaGlow)

        val pieSlices = listOf(
            CanvasPieSlice("Ditonton / Baca", stats.animeWatching + stats.mangaReading, Color.rgb(56, 189, 248)),
            CanvasPieSlice("Selesai", stats.animeCompleted + stats.mangaCompleted, Color.rgb(34, 197, 94)),
            CanvasPieSlice("Ditunda", stats.animeOnHold + stats.mangaOnHold, Color.rgb(245, 158, 11)),
            CanvasPieSlice("Drop", stats.animeDropped + stats.mangaDropped, Color.rgb(239, 68, 68)),
            CanvasPieSlice("Rencana", stats.animePlanToWatch + stats.mangaPlanToRead, Color.rgb(168, 85, 247))
        ).filter { it.count > 0 }

        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_logo)
        }.getOrNull()

        when (aspectRatio) {
            ExportAspectRatio.STORY_9_16 -> renderLayoutVertical(canvas, width, height, stats, malUser, topAnime, topManga, animeBitmaps, mangaBitmaps, pieSlices, logoBitmap)
            ExportAspectRatio.PORTRAIT_4_5 -> renderLayoutVertical(canvas, width, height, stats, malUser, topAnime, topManga, animeBitmaps, mangaBitmaps, pieSlices, logoBitmap)
            ExportAspectRatio.PORTRAIT_3_4 -> renderLayoutVertical(canvas, width, height, stats, malUser, topAnime, topManga, animeBitmaps, mangaBitmaps, pieSlices, logoBitmap)
            ExportAspectRatio.SQUARE_1_1 -> renderLayoutSquare(canvas, width, height, stats, malUser, topAnime, topManga, animeBitmaps, mangaBitmaps, pieSlices, logoBitmap)
            ExportAspectRatio.LANDSCAPE_16_9 -> renderLayoutLandscape(canvas, width, height, stats, malUser, topAnime, topManga, animeBitmaps, mangaBitmaps, pieSlices, logoBitmap)
        }

        return bitmap
    }

    private fun drawGlobalHeader(canvas: Canvas, rect: RectF, logoBitmap: Bitmap?) {
        val margin = 20f
        var textX = rect.left + margin

        if (logoBitmap != null) {
            val logoHeight = rect.height() - (margin * 1.4f)
            val logoWidth = logoBitmap.width * (logoHeight / logoBitmap.height)
            val logoRect = RectF(rect.left + margin, rect.top + margin * 0.7f, rect.left + margin + logoWidth, rect.bottom - margin * 0.7f)
            canvas.drawBitmap(logoBitmap, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
            textX = logoRect.right + 20f

            val divPaint = Paint().apply {
                color = Color.argb(80, 56, 189, 248)
                strokeWidth = 2.5f
            }
            canvas.drawLine(textX - 10f, rect.top + margin * 0.8f, textX - 10f, rect.bottom - margin * 0.8f, divPaint)
        }

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val sloganPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val primaryText = "CA'NIM"
        val textY = rect.centerY() + 7f
        canvas.drawText(primaryText, textX, textY, titlePaint)

        val primaryWidth = titlePaint.measureText(primaryText)
        val separator = "  |  "
        val sepWidth = sloganPaint.measureText(separator)
        canvas.drawText(separator, textX + primaryWidth, textY, sloganPaint)
        canvas.drawText("dibaca cak nim! | Aplikasi Pelacak Animanga berbasis akun MAL", textX + primaryWidth + sepWidth, textY, sloganPaint)
    }

    private fun drawGlobalFooter(canvas: Canvas, rect: RectF) {
        val linePaint = Paint().apply {
            color = Color.argb(40, 56, 189, 248)
            strokeWidth = 1.5f
        }
        canvas.drawLine(rect.left + 24f, rect.top, rect.right - 24f, rect.top, linePaint)

        val textPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val textY = rect.centerY() + 7f
        canvas.drawText("Dapatkan CA'NIM sekarang di https://canim-lp.vercel.app/", rect.centerX(), textY, textPaint)
    }

    private fun drawBentoCard(canvas: Canvas, rect: RectF, cornerRadius: Float = 26f) {
        // Outer Bezel Stroke
        val borderPaint = Paint().apply {
            color = Color.argb(38, 56, 189, 248)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        // Inner Glass Fill
        val fillPaint = Paint().apply {
            color = Color.argb(230, 11, 19, 43)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
    }

    private fun drawCenterCropBitmap(canvas: Canvas, bitmap: Bitmap?, rect: RectF, radius: Float) {
        if (bitmap == null) return
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)

        val scale: Float
        val dx: Float
        val dy: Float
        if (bitmap.width * rect.height() > rect.width() * bitmap.height) {
            scale = rect.height() / bitmap.height.toFloat()
            dx = (rect.width() - bitmap.width * scale) * 0.5f
            dy = 0f
        } else {
            scale = rect.width() / bitmap.width.toFloat()
            dx = 0f
            dy = (rect.height() - bitmap.height * scale) * 0.5f
        }
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(rect.left + dx, rect.top + dy)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        canvas.restore()
    }

    // --- VERTICAL LAYOUT (STORY 9:16, PORTRAIT 4:5, 3:4) ---
    private fun renderLayoutVertical(
        canvas: Canvas, width: Float, height: Float, stats: TrackerStats, malUser: MalUser,
        topAnime: List<UserMediaItem>, topManga: List<UserMediaItem>,
        animeBitmaps: List<Bitmap?>, mangaBitmaps: List<Bitmap?>, pieSlices: List<CanvasPieSlice>, logoBitmap: Bitmap?
    ) {
        val margin = 24f
        val gap = 16f
        val contentW = width - (margin * 2)

        val headerH = if (height > 1600f) 100f else 85f
        val footerH = if (height > 1600f) 56f else 48f

        // Calculate body space so Top 5 Anime & Manga dominate ~72% of the canvas
        val totalBodyH = height - (margin * 2) - headerH - footerH - (gap * 3)
        val summaryH = totalBodyH * 0.22f
        val showcaseH = (totalBodyH - summaryH) / 2f

        var curY = margin

        // Header
        val headerRect = RectF(margin, curY, margin + contentW, curY + headerH)
        drawGlobalHeader(canvas, headerRect, logoBitmap)
        curY += headerH + gap

        // Compact Profile & Stats Bar
        val summaryRect = RectF(margin, curY, margin + contentW, curY + summaryH)
        drawBentoCard(canvas, summaryRect)
        drawCompactSummaryBar(canvas, summaryRect, malUser, stats, pieSlices)
        curY += summaryH + gap

        // HERO 1: Top 5 Anime Showcase
        val animeRect = RectF(margin, curY, margin + contentW, curY + showcaseH)
        drawBentoCard(canvas, animeRect)
        drawHeroShowcaseRow(canvas, animeRect, "TOP 5 ANIME FAVORIT", Color.rgb(56, 189, 248), topAnime, animeBitmaps)
        curY += showcaseH + gap

        // HERO 2: Top 5 Manga Showcase
        val mangaRect = RectF(margin, curY, margin + contentW, curY + showcaseH)
        drawBentoCard(canvas, mangaRect)
        drawHeroShowcaseRow(canvas, mangaRect, "TOP 5 MANGA FAVORIT", Color.rgb(129, 140, 248), topManga, mangaBitmaps)
        curY += showcaseH + gap

        // Footer
        val footerRect = RectF(margin, curY, margin + contentW, curY + footerH)
        drawGlobalFooter(canvas, footerRect)
    }

    // --- SQUARE LAYOUT (1:1) ---
    private fun renderLayoutSquare(
        canvas: Canvas, width: Float, height: Float, stats: TrackerStats, malUser: MalUser,
        topAnime: List<UserMediaItem>, topManga: List<UserMediaItem>,
        animeBitmaps: List<Bitmap?>, mangaBitmaps: List<Bitmap?>, pieSlices: List<CanvasPieSlice>, logoBitmap: Bitmap?
    ) {
        val margin = 20f
        val gap = 12f
        val contentW = width - (margin * 2)

        val headerH = 75f
        val footerH = 42f
        val totalBodyH = height - (margin * 2) - headerH - footerH - (gap * 3)

        val summaryH = totalBodyH * 0.18f
        val showcaseH = (totalBodyH - summaryH) / 2f

        var curY = margin

        // Header
        val headerRect = RectF(margin, curY, margin + contentW, curY + headerH)
        drawGlobalHeader(canvas, headerRect, logoBitmap)
        curY += headerH + gap

        // Compact Summary
        val summaryRect = RectF(margin, curY, margin + contentW, curY + summaryH)
        drawBentoCard(canvas, summaryRect)
        drawCompactSummaryBar(canvas, summaryRect, malUser, stats, pieSlices)
        curY += summaryH + gap

        // HERO 1: Top 5 Anime
        val animeRect = RectF(margin, curY, margin + contentW, curY + showcaseH)
        drawBentoCard(canvas, animeRect)
        drawHeroShowcaseRow(canvas, animeRect, "TOP 5 ANIME FAVORIT", Color.rgb(56, 189, 248), topAnime, animeBitmaps)
        curY += showcaseH + gap

        // HERO 2: Top 5 Manga
        val mangaRect = RectF(margin, curY, margin + contentW, curY + showcaseH)
        drawBentoCard(canvas, mangaRect)
        drawHeroShowcaseRow(canvas, mangaRect, "TOP 5 MANGA FAVORIT", Color.rgb(129, 140, 248), topManga, mangaBitmaps)
        curY += showcaseH + gap

        // Footer
        val footerRect = RectF(margin, curY, margin + contentW, curY + footerH)
        drawGlobalFooter(canvas, footerRect)
    }

    // --- LANDSCAPE LAYOUT (16:9) ---
    private fun renderLayoutLandscape(
        canvas: Canvas, width: Float, height: Float, stats: TrackerStats, malUser: MalUser,
        topAnime: List<UserMediaItem>, topManga: List<UserMediaItem>,
        animeBitmaps: List<Bitmap?>, mangaBitmaps: List<Bitmap?>, pieSlices: List<CanvasPieSlice>, logoBitmap: Bitmap?
    ) {
        val margin = 24f
        val gap = 16f
        val contentW = width - (margin * 2)
        val contentH = height - (margin * 2)

        val headerH = 75f
        val footerH = 45f
        val bodyH = contentH - headerH - footerH - (gap * 2)

        val col1W = contentW * 0.28f
        val col2W = contentW - col1W - gap

        var curY = margin
        val headerRect = RectF(margin, curY, margin + contentW, curY + headerH)
        drawGlobalHeader(canvas, headerRect, logoBitmap)
        curY += headerH + gap

        val bodyY = curY

        // Col 1: Profile & Collection Stats Card
        val sidebarRect = RectF(margin, bodyY, margin + col1W, bodyY + bodyH)
        drawBentoCard(canvas, sidebarRect)
        drawSidebarContent(canvas, sidebarRect, malUser, stats, pieSlices)

        // Col 2: Top 5 Anime & Manga Hero Showcases
        val showcaseH = (bodyH - gap) / 2f
        val animeRect = RectF(margin + col1W + gap, bodyY, margin + contentW, bodyY + showcaseH)
        drawBentoCard(canvas, animeRect)
        drawHeroShowcaseRow(canvas, animeRect, "TOP 5 ANIME FAVORIT", Color.rgb(56, 189, 248), topAnime, animeBitmaps)

        val mangaRect = RectF(margin + col1W + gap, bodyY + showcaseH + gap, margin + contentW, bodyY + bodyH)
        drawBentoCard(canvas, mangaRect)
        drawHeroShowcaseRow(canvas, mangaRect, "TOP 5 MANGA FAVORIT", Color.rgb(129, 140, 248), topManga, mangaBitmaps)

        // Footer
        val footerRect = RectF(margin, bodyY + bodyH + gap, margin + contentW, height - margin)
        drawGlobalFooter(canvas, footerRect)
    }

    // --- COMPACT SUMMARY BAR (FOR VERTICAL & SQUARE) ---
    private fun drawCompactSummaryBar(
        canvas: Canvas, rect: RectF, malUser: MalUser, stats: TrackerStats, slices: List<CanvasPieSlice>
    ) {
        val pad = 24f
        val username = if (malUser.username.isBlank()) "Tamu" else malUser.username

        // User Avatar & Name Section (Left 32%)
        val userW = rect.width() * 0.32f
        val avatarSize = minOf(rect.height() - (pad * 2f), 80f)
        val avatarY = rect.centerY() - (avatarSize / 2f)

        // Monogram Circle
        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(46, 81, 162) }
        val cx = rect.left + pad + (avatarSize / 2f)
        val cy = avatarY + (avatarSize / 2f)
        canvas.drawCircle(cx, cy, avatarSize / 2f, avatarPaint)

        val avatarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 189, 248)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(cx, cy, avatarSize / 2f, avatarBorderPaint)

        val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = avatarSize * 0.45f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val initialText = username.take(1).uppercase()
        val initY = cy - ((initialPaint.descent() + initialPaint.ascent()) / 2f)
        canvas.drawText(initialText, cx, initY, initialPaint)

        val nameX = rect.left + pad + avatarSize + 16f
        val userPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 189, 248)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var displayUser = "@$username"
        if (userPaint.measureText(displayUser) > (userW - avatarSize - 20f)) {
            displayUser = displayUser.take(10) + "..."
        }
        canvas.drawText(displayUser, nameX, rect.centerY() - 4f, userPaint)
        canvas.drawText("TERHUBUNG MAL", nameX, rect.centerY() + 22f, badgeSubPaint)

        // Divider
        val divPaint = Paint().apply { color = Color.argb(40, 255, 255, 255); strokeWidth = 1.5f }
        val div1X = rect.left + userW
        canvas.drawLine(div1X, rect.top + 16f, div1X, rect.bottom - 16f, divPaint)

        // 4 Stat Metric Columns (Right 68%)
        val statW = (rect.width() - userW) / 4f
        val statLabels = listOf("Anime", "Manga", "Hari Tonton", "Bab Dibaca")
        val statValues = listOf("${stats.totalAnime}", "${stats.totalManga}", "${stats.daysWatched}", "${stats.chaptersRead}")

        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(156, 163, 175)
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }

        for (i in 0..3) {
            val sCenterX = div1X + (statW * i) + (statW / 2f)
            canvas.drawText(statValues[i], sCenterX, rect.centerY() - 4f, valPaint)
            canvas.drawText(statLabels[i], sCenterX, rect.centerY() + 24f, lblPaint)

            if (i < 3) {
                val subDivX = div1X + (statW * (i + 1))
                canvas.drawLine(subDivX, rect.centerY() - 20f, subDivX, rect.centerY() + 20f, divPaint)
            }
        }
    }

    // --- SIDEBAR CONTENT (FOR LANDSCAPE) ---
    private fun drawSidebarContent(
        canvas: Canvas, rect: RectF, malUser: MalUser, stats: TrackerStats, slices: List<CanvasPieSlice>
    ) {
        val pad = 24f
        var curY = rect.top + 36f

        val username = if (malUser.username.isBlank()) "Tamu" else malUser.username
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 189, 248)
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("KOLEKSI MYANIMELIST", rect.left + pad, curY, headerPaint)
        curY += 40f

        val userPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("@$username", rect.left + pad, curY, userPaint)
        curY += 36f

        val loc = if (!malUser.location.isNullOrBlank()) malUser.location else "Indonesia"
        val locPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(156, 163, 175)
            textSize = 20f
        }
        canvas.drawText("📍 $loc", rect.left + pad, curY, locPaint)
        curY += 40f

        val divPaint = Paint().apply { color = Color.argb(40, 255, 255, 255); strokeWidth = 1.5f }
        canvas.drawLine(rect.left + pad, curY, rect.right - pad, curY, divPaint)
        curY += 36f

        // Stats Matrix (2x2 Grid)
        val colW = (rect.width() - (pad * 2f)) / 2f
        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(156, 163, 175); textSize = 19f }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

        canvas.drawText("Total Anime", rect.left + pad, curY, lblPaint)
        canvas.drawText("${stats.totalAnime}", rect.left + pad, curY + 36f, valPaint)

        canvas.drawText("Total Manga", rect.left + pad + colW, curY, lblPaint)
        canvas.drawText("${stats.totalManga}", rect.left + pad + colW, curY + 36f, valPaint)

        curY += 85f
        canvas.drawText("Hari Tonton", rect.left + pad, curY, lblPaint)
        canvas.drawText("${stats.daysWatched}", rect.left + pad, curY + 36f, valPaint)

        canvas.drawText("Bab Dibaca", rect.left + pad + colW, curY, lblPaint)
        canvas.drawText("${stats.chaptersRead}", rect.left + pad + colW, curY + 36f, valPaint)

        curY += 85f
        canvas.drawLine(rect.left + pad, curY, rect.right - pad, curY, divPaint)
        curY += 36f

        // Mini Donut Chart
        if (slices.isNotEmpty()) {
            canvas.drawText("STATUS DISTRIBUSI", rect.left + pad, curY, headerPaint)
            curY += 24f

            val chartSize = minOf(rect.width() - (pad * 2f), rect.bottom - curY - 20f)
            val radius = chartSize * 0.38f
            val cx = rect.left + pad + radius + 10f
            val cy = curY + radius + 15f
            val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            val piePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 26f }

            val total = slices.sumOf { it.count }.toFloat()
            var startAngle = -90f
            for (slice in slices) {
                val sweep = (slice.count / total) * 360f
                piePaint.color = slice.color
                canvas.drawArc(oval, startAngle, sweep, false, piePaint)
                startAngle += sweep
            }

            // Legend on right
            val legendX = cx + radius + 32f
            var legendY = cy - radius + 20f
            val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(203, 213, 225); textSize = 18f }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

            for (slice in slices.take(4)) {
                dotPaint.color = slice.color
                canvas.drawCircle(legendX, legendY - 6f, 7f, dotPaint)
                canvas.drawText("${slice.label}: ${slice.count}", legendX + 16f, legendY, legPaint)
                legendY += 30f
            }
        }
    }

    // --- HERO SHOWCASE ROW (THE HERO COMPONENT) ---
    private fun drawHeroShowcaseRow(
        canvas: Canvas,
        rect: RectF,
        sectionTitle: String,
        accentColor: Int,
        items: List<UserMediaItem>,
        bitmaps: List<Bitmap?>
    ) {
        val pad = 20f
        val headerH = 46f
        var curY = rect.top + pad

        // 1. Sleek Section Chip Header
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(38, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            style = Paint.Style.FILL
        }
        val chipBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textW = textPaint.measureText(sectionTitle)
        val chipRect = RectF(rect.left + pad, curY, rect.left + pad + textW + 36f, curY + 38f)
        canvas.drawRoundRect(chipRect, 19f, 19f, chipPaint)
        canvas.drawRoundRect(chipRect, 19f, 19f, chipBorder)
        canvas.drawText(sectionTitle, chipRect.left + 18f, chipRect.centerY() + 7f, textPaint)

        curY += headerH

        if (items.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 22f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Belum ada data media favorit", rect.centerX(), rect.centerY() + 10f, emptyPaint)
            return
        }

        // 2. 5 Hero Cards in a Row (Maximizing Poster Space)
        val gap = 14f
        val totalAvailableW = rect.width() - (pad * 2f)
        val cardW = (totalAvailableW - (gap * 4f)) / 5f
        val cardH = rect.bottom - pad - curY

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(251, 191, 36) // Gold Amber
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        for (i in 0 until 5) {
            val cardX = rect.left + pad + (i * (cardW + gap))
            val cardRect = RectF(cardX, curY, cardX + cardW, curY + cardH)

            // Card Base Fill
            val cardBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(cardRect, 18f, 18f, cardBasePaint)

            // Media Cover (CenterCrop with Matrix math to guarantee zero stretch)
            val bmp = bitmaps.getOrNull(i)
            drawCenterCropBitmap(canvas, bmp, cardRect, 18f)

            // Cinematic Bottom Scrim Gradient
            val scrimPaint = Paint().apply {
                shader = LinearGradient(
                    cardRect.left, cardRect.top + (cardH * 0.40f),
                    cardRect.left, cardRect.bottom,
                    intArrayOf(Color.TRANSPARENT, Color.argb(160, 4, 7, 15), Color.argb(245, 3, 6, 12)),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, 18f, 18f, scrimPaint)

            // Outer Card Border
            val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (i == 0) Color.argb(160, 245, 158, 11) else Color.argb(40, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = if (i == 0) 2.5f else 1.5f
            }
            canvas.drawRoundRect(cardRect, 18f, 18f, cardBorderPaint)

            // Rank Badge (#1 Gold, #2 Silver, #3 Bronze, #4-5 Dark Glass)
            drawMedalRankBadge(canvas, cardRect, i + 1)

            // Title & Score overlay at bottom of card
            val item = items.getOrNull(i)
            if (item != null) {
                val textPad = 12f
                val bottomY = cardRect.bottom - 14f

                // Score Chip
                val scoreText = "⭐ ${if (item.score > 0) item.score else "-"}"
                canvas.drawText(scoreText, cardRect.left + textPad, bottomY, scorePaint)

                // Title (Truncated if too long)
                var titleText = item.title
                val maxTitleW = cardW - (textPad * 2f)
                if (titlePaint.measureText(titleText) > maxTitleW) {
                    while (titleText.length > 3 && titlePaint.measureText(titleText + "...") > maxTitleW) {
                        titleText = titleText.dropLast(1)
                    }
                    titleText += "..."
                }
                canvas.drawText(titleText, cardRect.left + textPad, bottomY - 26f, titlePaint)
            }
        }
    }

    private fun drawMedalRankBadge(canvas: Canvas, cardRect: RectF, rank: Int) {
        val badgeW = 60f
        val badgeH = 34f
        val badgeX = cardRect.left + 10f
        val badgeY = cardRect.top + 10f
        val rect = RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        when (rank) {
            1 -> {
                bgPaint.color = Color.rgb(245, 158, 11) // Amber Gold
                borderPaint.color = Color.rgb(253, 230, 138)
                textPaint.color = Color.rgb(30, 20, 3)
            }
            2 -> {
                bgPaint.color = Color.rgb(203, 213, 225) // Silver
                borderPaint.color = Color.WHITE
                textPaint.color = Color.rgb(15, 23, 42)
            }
            3 -> {
                bgPaint.color = Color.rgb(217, 119, 6) // Bronze
                borderPaint.color = Color.rgb(251, 191, 36)
                textPaint.color = Color.WHITE
            }
            else -> {
                bgPaint.color = Color.argb(215, 11, 19, 43)
                borderPaint.color = Color.argb(80, 255, 255, 255)
                textPaint.color = Color.WHITE
            }
        }

        canvas.drawRoundRect(rect, 10f, 10f, bgPaint)
        canvas.drawRoundRect(rect, 10f, 10f, borderPaint)

        val label = if (rank == 1) "👑 #1" else "#$rank"
        val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(label, rect.centerX(), textY, textPaint)
    }
}
