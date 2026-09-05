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
    RATIO_16_9("16:9", 1920, 1080, true),
    RATIO_19_6("19:6", 1900, 600, true),
    RATIO_1_1("1:1", 1200, 1200, false),
    RATIO_4_5("4:5", 1080, 1350, false),
    RATIO_3_4("3:4", 1080, 1440, false)
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
        aspectRatio: ExportAspectRatio = ExportAspectRatio.RATIO_16_9
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            // Apply Top 5 Sequel exclusion rule for Anime
            val filteredTopAnime = AnimeFranchiseFilter.selectTopAnimeNonSequel(topAnime, 5)
            val filteredTopManga = topManga.take(5)

            val bitmap = renderStatsBitmap(context, stats, malUser, filteredTopAnime, filteredTopManga, aspectRatio)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "canim_stats_${malUser.username.ifBlank { "user" }}_${aspectRatio.label.replace(":", "_")}_$timeStamp.${format.extension}"

            // 1. Save locally for FileProvider sharing
            val statsDir = File(context.cacheDir, "stats").apply { mkdirs() }
            val localFile = File(statsDir, filename)

            FileOutputStream(localFile).use { fos ->
                when (format) {
                    StatsExportFormat.JPG -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                    StatsExportFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    StatsExportFormat.PDF -> {
                        val pdfDoc = PdfDocument()
                        val pageInfo = PdfDocument.PageInfo.Builder(aspectRatio.width, aspectRatio.height, 1).create()
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
        val width = aspectRatio.width
        val height = aspectRatio.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Preload cover bitmaps in parallel
        val animeCoversDeferred = withContext(Dispatchers.IO) {
            topAnime.take(5).map { item ->
                async { loadBitmap(context, item.imageUrl) }
            }
        }
        val mangaCoversDeferred = withContext(Dispatchers.IO) {
            topManga.take(5).map { item ->
                async { loadBitmap(context, item.imageUrl) }
            }
        }
        val animeBitmaps = animeCoversDeferred.awaitAll()
        val mangaBitmaps = mangaCoversDeferred.awaitAll()

        // Background Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.rgb(10, 15, 29), Color.rgb(15, 23, 42), Color.rgb(2, 6, 23)),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Decorative background glow
        val glowPaint = Paint().apply {
            shader = RadialGradient(
                width * 0.85f, height * 0.15f, width * 0.35f,
                Color.argb(40, 56, 189, 248), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.85f, height * 0.15f, width * 0.35f, glowPaint)

        val cardBgPaint = Paint().apply {
            color = Color.rgb(17, 24, 39)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardBorderPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        val divPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            strokeWidth = 2f
        }

        // Header Section with App Logo Bitmap
        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_logo)
        }.getOrNull()

        val headerMargin = if (width >= 1200) 70f else 40f
        var headerTextX = headerMargin
        val headerY = if (height <= 700) 24f else 32f
        val logoHeight = if (height <= 700) 48f else 60f

        if (logoBitmap != null) {
            val logoWidth = logoBitmap.width * (logoHeight / logoBitmap.height)
            val destRect = RectF(headerMargin, headerY, headerMargin + logoWidth, headerY + logoHeight)
            canvas.drawBitmap(logoBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            headerTextX = headerMargin + logoWidth + 20f
        }

        val appSubPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = if (height <= 700) 18f else 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        // Requirement 4: Remove "• Terhubung dengan MAL"
        canvas.drawText("Lacak Anime dan Mangamu", headerTextX, headerY + (logoHeight * 0.65f), appSubPaint)

        // Header Divider
        val divY = headerY + logoHeight + (if (height <= 700) 14f else 20f)
        canvas.drawLine(headerMargin, divY, width - headerMargin, divY, divPaint)

        // Status Distribution Pie Slices
        val pieSlices = listOf(
            CanvasPieSlice("Ditonton / Baca", stats.animeWatching + stats.mangaReading, Color.rgb(56, 189, 248)),
            CanvasPieSlice("Selesai", stats.animeCompleted + stats.mangaCompleted, Color.rgb(34, 197, 94)),
            CanvasPieSlice("Ditunda", stats.animeOnHold + stats.mangaOnHold, Color.rgb(245, 158, 11)),
            CanvasPieSlice("Drop", stats.animeDropped + stats.mangaDropped, Color.rgb(239, 68, 68)),
            CanvasPieSlice("Rencana", stats.animePlanToWatch + stats.mangaPlanToRead, Color.rgb(168, 85, 247))
        ).filter { it.count > 0 }

        when (aspectRatio) {
            ExportAspectRatio.RATIO_16_9 -> {
                renderLayout16x9(
                    canvas = canvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    topY = divY + 22f,
                    stats = stats,
                    malUser = malUser,
                    topAnime = topAnime,
                    topManga = topManga,
                    animeBitmaps = animeBitmaps,
                    mangaBitmaps = mangaBitmaps,
                    pieSlices = pieSlices,
                    cardBgPaint = cardBgPaint,
                    cardBorderPaint = cardBorderPaint,
                    divPaint = divPaint
                )
            }
            ExportAspectRatio.RATIO_19_6 -> {
                renderLayout19x6(
                    canvas = canvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    topY = divY + 16f,
                    stats = stats,
                    malUser = malUser,
                    topAnime = topAnime,
                    topManga = topManga,
                    animeBitmaps = animeBitmaps,
                    mangaBitmaps = mangaBitmaps,
                    pieSlices = pieSlices,
                    cardBgPaint = cardBgPaint,
                    cardBorderPaint = cardBorderPaint,
                    divPaint = divPaint
                )
            }
            ExportAspectRatio.RATIO_1_1 -> {
                renderLayout1x1(
                    canvas = canvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    topY = divY + 20f,
                    stats = stats,
                    malUser = malUser,
                    topAnime = topAnime,
                    topManga = topManga,
                    animeBitmaps = animeBitmaps,
                    mangaBitmaps = mangaBitmaps,
                    pieSlices = pieSlices,
                    cardBgPaint = cardBgPaint,
                    cardBorderPaint = cardBorderPaint,
                    divPaint = divPaint
                )
            }
            ExportAspectRatio.RATIO_4_5, ExportAspectRatio.RATIO_3_4 -> {
                renderLayoutPortrait(
                    canvas = canvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    topY = divY + 20f,
                    stats = stats,
                    malUser = malUser,
                    topAnime = topAnime,
                    topManga = topManga,
                    animeBitmaps = animeBitmaps,
                    mangaBitmaps = mangaBitmaps,
                    pieSlices = pieSlices,
                    cardBgPaint = cardBgPaint,
                    cardBorderPaint = cardBorderPaint,
                    divPaint = divPaint
                )
            }
        }

        // FOOTER: Low Opacity Credit
        val footerPaint = Paint().apply {
            color = Color.argb(102, 255, 255, 255)
            textSize = if (height <= 700) 14f else 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("Dibuat dengan CA-NIM • github.com/Kh1zZ/CA-NIM", headerMargin, height - (if (height <= 700) 16f else 24f), footerPaint)

        return bitmap
    }

    // --- LAYOUT 16:9 (1920 x 1080) ---
    private fun renderLayout16x9(
        canvas: Canvas,
        width: Float,
        height: Float,
        topY: Float,
        stats: TrackerStats,
        malUser: MalUser,
        topAnime: List<UserMediaItem>,
        topManga: List<UserMediaItem>,
        animeBitmaps: List<Bitmap?>,
        mangaBitmaps: List<Bitmap?>,
        pieSlices: List<CanvasPieSlice>,
        cardBgPaint: Paint,
        cardBorderPaint: Paint,
        divPaint: Paint
    ) {
        // Left Column: Profile, Simplified Metrics Matrix, and Pie Chart (Width: 500px, x: 80f to 580f)
        val leftCardRect = RectF(80f, topY, 580f, height - 60f)
        canvas.drawRoundRect(leftCardRect, 24f, 24f, cardBgPaint)
        canvas.drawRoundRect(leftCardRect, 24f, 24f, cardBorderPaint)

        val secHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // 1. Profile Section
        canvas.drawText("PROFIL MYANIMELIST", 110f, topY + 40f, secHeaderPaint)
        drawProfileSection(canvas, 110f, topY + 75f, malUser)

        // 2. Divider
        val div1Y = topY + 160f
        canvas.drawLine(110f, div1Y, 550f, div1Y, divPaint)

        // 3. Simplified Metrics Matrix (Col 1 & Col 2)
        canvas.drawText("RINGKASAN METRIK", 110f, div1Y + 36f, secHeaderPaint)
        drawMetricsMatrix(canvas, 110f, div1Y + 68f, colWidth = 220f, rowHeight = 60f, stats = stats)

        // 4. Divider
        val div2Y = div1Y + 260f
        canvas.drawLine(110f, div2Y, 550f, div2Y, divPaint)

        // 5. Pie Chart & Legend
        canvas.drawText("DISTRIBUSI STATUS KOLEKSI", 110f, div2Y + 36f, secHeaderPaint)
        drawPieChartAndLegend(
            canvas = canvas,
            centerX = 210f,
            centerY = div2Y + 160f,
            radius = 85f,
            slices = pieSlices,
            legendX = 325f,
            legendStartY = div2Y + 85f,
            legendItemSpacing = 28f
        )

        // Right Area: Top 5 Anime & Top 5 Manga (Width: 1220px, x: 620f to 1840f)
        val cardWidth = 224f
        val cardHeight = 336f
        val cardSpacing = 20f
        val rightStartX = 620f

        // Top 5 Anime
        val animeSecPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP 5 ANIME PRIBADI (NON-SEQUEL)", rightStartX, topY + 30f, animeSecPaint)

        val topAnimeY = topY + 50f
        topAnime.take(5).forEachIndexed { idx, item ->
            val cardX = rightStartX + idx * (cardWidth + cardSpacing)
            val cardRect = RectF(cardX, topAnimeY, cardX + cardWidth, topAnimeY + cardHeight)
            drawCoverCard(
                canvas = canvas,
                rect = cardRect,
                bitmap = animeBitmaps.getOrNull(idx),
                rank = idx + 1,
                title = item.title,
                score = item.score,
                subtitle = "${item.progress} / ${if (item.totalEpisodes > 0) item.totalEpisodes else "?"} Ep",
                accentColor = Color.rgb(56, 189, 248)
            )
        }

        // Top 5 Manga
        val mangaSecPaint = Paint().apply {
            color = Color.rgb(96, 165, 250)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val topMangaLabelY = topAnimeY + cardHeight + 40f
        canvas.drawText("TOP 5 MANGA PRIBADI (SKOR TERTINGGI)", rightStartX, topMangaLabelY, mangaSecPaint)

        val topMangaY = topMangaLabelY + 20f
        topManga.take(5).forEachIndexed { idx, item ->
            val cardX = rightStartX + idx * (cardWidth + cardSpacing)
            val cardRect = RectF(cardX, topMangaY, cardX + cardWidth, topMangaY + cardHeight)
            drawCoverCard(
                canvas = canvas,
                rect = cardRect,
                bitmap = mangaBitmaps.getOrNull(idx),
                rank = idx + 1,
                title = item.title,
                score = item.score,
                subtitle = "${item.progressChapters} Bab",
                accentColor = Color.rgb(96, 165, 250)
            )
        }
    }

    // --- LAYOUT 19:6 (1900 x 600) ---
    private fun renderLayout19x6(
        canvas: Canvas,
        width: Float,
        height: Float,
        topY: Float,
        stats: TrackerStats,
        malUser: MalUser,
        topAnime: List<UserMediaItem>,
        topManga: List<UserMediaItem>,
        animeBitmaps: List<Bitmap?>,
        mangaBitmaps: List<Bitmap?>,
        pieSlices: List<CanvasPieSlice>,
        cardBgPaint: Paint,
        cardBorderPaint: Paint,
        divPaint: Paint
    ) {
        val leftCardRect = RectF(60f, topY, 560f, height - 35f)
        canvas.drawRoundRect(leftCardRect, 20f, 20f, cardBgPaint)
        canvas.drawRoundRect(leftCardRect, 20f, 20f, cardBorderPaint)

        val secHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Profile & Metrics in left card
        canvas.drawText("PROFIL & METRIK", 85f, topY + 30f, secHeaderPaint)
        drawProfileSection(canvas, 85f, topY + 56f, malUser, fontSize = 14f, rowSpacing = 22f)

        val div1Y = topY + 115f
        canvas.drawLine(85f, div1Y, 535f, div1Y, divPaint)

        drawMetricsMatrix(canvas, 85f, div1Y + 28f, colWidth = 220f, rowHeight = 44f, stats = stats, labelSize = 12f, valSize = 16f)

        val div2Y = div1Y + 160f
        canvas.drawLine(85f, div2Y, 535f, div2Y, divPaint)

        // Small Pie Chart
        drawPieChartAndLegend(
            canvas = canvas,
            centerX = 160f,
            centerY = div2Y + 80f,
            radius = 55f,
            slices = pieSlices,
            legendX = 250f,
            legendStartY = div2Y + 35f,
            legendItemSpacing = 20f,
            legendFontSize = 12f
        )

        // Right Area: 2 compact rows of 5 cards
        val rightStartX = 600f
        val cardWidth = 230f
        val cardHeight = 185f
        val cardSpacing = 20f

        val animeSecPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP 5 ANIME PRIBADI (NON-SEQUEL)", rightStartX, topY + 24f, animeSecPaint)

        val topAnimeY = topY + 36f
        topAnime.take(5).forEachIndexed { idx, item ->
            val cardX = rightStartX + idx * (cardWidth + cardSpacing)
            val cardRect = RectF(cardX, topAnimeY, cardX + cardWidth, topAnimeY + cardHeight)
            drawCoverCard(
                canvas = canvas,
                rect = cardRect,
                bitmap = animeBitmaps.getOrNull(idx),
                rank = idx + 1,
                title = item.title,
                score = item.score,
                subtitle = "${item.progress} / ${if (item.totalEpisodes > 0) item.totalEpisodes else "?"} Ep",
                accentColor = Color.rgb(56, 189, 248)
            )
        }

        val mangaSecPaint = Paint().apply {
            color = Color.rgb(96, 165, 250)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val topMangaLabelY = topAnimeY + cardHeight + 28f
        canvas.drawText("TOP 5 MANGA PRIBADI (SKOR TERTINGGI)", rightStartX, topMangaLabelY, mangaSecPaint)

        val topMangaY = topMangaLabelY + 12f
        topManga.take(5).forEachIndexed { idx, item ->
            val cardX = rightStartX + idx * (cardWidth + cardSpacing)
            val cardRect = RectF(cardX, topMangaY, cardX + cardWidth, topMangaY + cardHeight)
            drawCoverCard(
                canvas = canvas,
                rect = cardRect,
                bitmap = mangaBitmaps.getOrNull(idx),
                rank = idx + 1,
                title = item.title,
                score = item.score,
                subtitle = "${item.progressChapters} Bab",
                accentColor = Color.rgb(96, 165, 250)
            )
        }
    }

    // --- LAYOUT 1:1 (1200 x 1200) ---
    private fun renderLayout1x1(
        canvas: Canvas,
        width: Float,
        height: Float,
        topY: Float,
        stats: TrackerStats,
        malUser: MalUser,
        topAnime: List<UserMediaItem>,
        topManga: List<UserMediaItem>,
        animeBitmaps: List<Bitmap?>,
        mangaBitmaps: List<Bitmap?>,
        pieSlices: List<CanvasPieSlice>,
        cardBgPaint: Paint,
        cardBorderPaint: Paint,
        divPaint: Paint
    ) {
        // Top Card: Left = Profile & Metrics, Right = Pie Chart
        val topCardRect = RectF(60f, topY, width - 60f, topY + 310f)
        canvas.drawRoundRect(topCardRect, 22f, 22f, cardBgPaint)
        canvas.drawRoundRect(topCardRect, 22f, 22f, cardBorderPaint)

        val secHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Left Side
        canvas.drawText("PROFIL & RINGKASAN METRIK", 85f, topY + 32f, secHeaderPaint)
        drawProfileSection(canvas, 85f, topY + 60f, malUser, fontSize = 14f, rowSpacing = 22f)

        val divInnerY = topY + 118f
        canvas.drawLine(85f, divInnerY, 560f, divInnerY, divPaint)
        drawMetricsMatrix(canvas, 85f, divInnerY + 28f, colWidth = 230f, rowHeight = 46f, stats = stats, labelSize = 12f, valSize = 17f)

        // Vertical divider between metrics and pie chart
        canvas.drawLine(590f, topY + 20f, 590f, topY + 290f, divPaint)

        // Right Side: Pie Chart
        canvas.drawText("DISTRIBUSI STATUS KOLEKSI", 620f, topY + 32f, secHeaderPaint)
        drawPieChartAndLegend(
            canvas = canvas,
            centerX = 730f,
            centerY = topY + 175f,
            radius = 80f,
            slices = pieSlices,
            legendX = 850f,
            legendStartY = topY + 80f,
            legendItemSpacing = 28f
        )

        // Middle Area: Top 5 Anime
        val startX = 60f
        val cardSpacing = 16f
        val cardWidth = (width - 120f - (4 * cardSpacing)) / 5f
        val cardHeight = 295f

        val animeSecPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val animeLabelY = topY + 345f
        canvas.drawText("TOP 5 ANIME PRIBADI (NON-SEQUEL)", startX, animeLabelY, animeSecPaint)

        val topAnimeY = animeLabelY + 18f
        topAnime.take(5).forEachIndexed { idx, item ->
            val cardX = startX + idx * (cardWidth + cardSpacing)
            val cardRect = RectF(cardX, topAnimeY, cardX + cardWidth, topAnimeY + cardHeight)
            drawCoverCard(
                canvas = canvas,
                rect = cardRect,
                bitmap = animeBitmaps.getOrNull(idx),
                rank = idx + 1,
                title = item.title,
                score = item.score,
                subtitle = "${item.progress} / ${if (item.totalEpisodes > 0) item.totalEpisodes else "?"} Ep",
                accentColor = Color.rgb(56, 189, 248)
            )
        }

        // Bottom Area: Top 5 Manga
        val mangaSecPaint = Paint().apply {
            color = Color.rgb(96, 165, 250)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val mangaLabelY = topAnimeY + cardHeight + 35f
        canvas.drawText("TOP 5 MANGA PRIBADI (SKOR TERTINGGI)", startX, mangaLabelY, mangaSecPaint)

        val topMangaY = mangaLabelY + 18f
        topManga.take(5).forEachIndexed { idx, item ->
            val cardX = startX + idx * (cardWidth + cardSpacing)
            val cardRect = RectF(cardX, topMangaY, cardX + cardWidth, topMangaY + cardHeight)
            drawCoverCard(
                canvas = canvas,
                rect = cardRect,
                bitmap = mangaBitmaps.getOrNull(idx),
                rank = idx + 1,
                title = item.title,
                score = item.score,
                subtitle = "${item.progressChapters} Bab",
                accentColor = Color.rgb(96, 165, 250)
            )
        }
    }

    // --- LAYOUT PORTRAIT (4:5 and 3:4) ---
    private fun renderLayoutPortrait(
        canvas: Canvas,
        width: Float,
        height: Float,
        topY: Float,
        stats: TrackerStats,
        malUser: MalUser,
        topAnime: List<UserMediaItem>,
        topManga: List<UserMediaItem>,
        animeBitmaps: List<Bitmap?>,
        mangaBitmaps: List<Bitmap?>,
        pieSlices: List<CanvasPieSlice>,
        cardBgPaint: Paint,
        cardBorderPaint: Paint,
        divPaint: Paint
    ) {
        val margin = 50f
        val topCardRect = RectF(margin, topY, width - margin, topY + 340f)
        canvas.drawRoundRect(topCardRect, 22f, 22f, cardBgPaint)
        canvas.drawRoundRect(topCardRect, 22f, 22f, cardBorderPaint)

        val secHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Left Side: Profile + Metrics
        canvas.drawText("PROFIL & METRIK", margin + 25f, topY + 32f, secHeaderPaint)
        drawProfileSection(canvas, margin + 25f, topY + 60f, malUser, fontSize = 13f, rowSpacing = 22f)

        val divInnerY = topY + 118f
        canvas.drawLine(margin + 25f, divInnerY, margin + 460f, divInnerY, divPaint)
        drawMetricsMatrix(canvas, margin + 25f, divInnerY + 28f, colWidth = 210f, rowHeight = 46f, stats = stats, labelSize = 12f, valSize = 16f)

        // Vertical divider
        canvas.drawLine(margin + 480f, topY + 20f, margin + 480f, topY + 320f, divPaint)

        // Right Side: Pie Chart
        canvas.drawText("DISTRIBUSI STATUS", margin + 505f, topY + 32f, secHeaderPaint)
        drawPieChartAndLegend(
            canvas = canvas,
            centerX = margin + 610f,
            centerY = topY + 195f,
            radius = 78f,
            slices = pieSlices,
            legendX = margin + 725f,
            legendStartY = topY + 95f,
            legendItemSpacing = 26f,
            legendFontSize = 13f
        )

        // 5 cards per row
        val cardSpacing = 14f
        val cardWidth = (width - (margin * 2f) - (4 * cardSpacing)) / 5f
        val cardHeight = 280f

        val animeSecPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val animeLabelY = topY + 380f
        canvas.drawText("TOP 5 ANIME PRIBADI (NON-SEQUEL)", margin, animeLabelY, animeSecPaint)

        val topAnimeY = animeLabelY + 18f
        topAnime.take(5).forEachIndexed { idx, item ->
            val cardX = margin + idx * (cardWidth + cardSpacing)
            val cardRect = RectF(cardX, topAnimeY, cardX + cardWidth, topAnimeY + cardHeight)
            drawCoverCard(
                canvas = canvas,
                rect = cardRect,
                bitmap = animeBitmaps.getOrNull(idx),
                rank = idx + 1,
                title = item.title,
                score = item.score,
                subtitle = "${item.progress} / ${if (item.totalEpisodes > 0) item.totalEpisodes else "?"} Ep",
                accentColor = Color.rgb(56, 189, 248)
            )
        }

        val mangaSecPaint = Paint().apply {
            color = Color.rgb(96, 165, 250)
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val mangaLabelY = topAnimeY + cardHeight + 35f
        canvas.drawText("TOP 5 MANGA PRIBADI (SKOR TERTINGGI)", margin, mangaLabelY, mangaSecPaint)

        val topMangaY = mangaLabelY + 18f
        topManga.take(5).forEachIndexed { idx, item ->
            val cardX = margin + idx * (cardWidth + cardSpacing)
            val cardRect = RectF(cardX, topMangaY, cardX + cardWidth, topMangaY + cardHeight)
            drawCoverCard(
                canvas = canvas,
                rect = cardRect,
                bitmap = mangaBitmaps.getOrNull(idx),
                rank = idx + 1,
                title = item.title,
                score = item.score,
                subtitle = "${item.progressChapters} Bab",
                accentColor = Color.rgb(96, 165, 250)
            )
        }
    }

    private fun drawProfileSection(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        malUser: MalUser,
        fontSize: Float = 16f,
        rowSpacing: Float = 26f
    ) {
        val labelPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = fontSize
            isAntiAlias = true
        }
        val valPaint = Paint().apply {
            color = Color.WHITE
            textSize = fontSize + 2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var curY = startY
        fun drawRow(label: String, value: String) {
            canvas.drawText(label, startX, curY, labelPaint)
            val cleanVal = if (value.isBlank()) "-" else value
            canvas.drawText(cleanVal, startX + 90f, curY, valPaint)
            curY += rowSpacing
        }

        drawRow("Username:", "@${malUser.username.ifBlank { "Tamu" }}")
        drawRow("Lokasi:", malUser.location ?: "-")
        drawRow("Gender:", malUser.gender?.replaceFirstChar { it.uppercase() } ?: "-")
    }

    private fun drawMetricsMatrix(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        colWidth: Float,
        rowHeight: Float,
        stats: TrackerStats,
        labelSize: Float = 14f,
        valSize: Float = 20f
    ) {
        val labelPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = labelSize
            isAntiAlias = true
        }
        val valPaint = Paint().apply {
            color = Color.WHITE
            textSize = valSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val unitPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = labelSize
            isAntiAlias = true
        }

        fun drawCell(x: Float, y: Float, label: String, value: String, unit: String) {
            canvas.drawText(label, x, y, labelPaint)
            canvas.drawText(value, x, y + valSize + 4f, valPaint)
            if (unit.isNotEmpty()) {
                val vWidth = valPaint.measureText(value)
                canvas.drawText(unit, x + vWidth + 6f, y + valSize + 3f, unitPaint)
            }
        }

        // Row 1
        drawCell(startX, startY, "Total Anime", "${stats.totalAnime}", "Judul")
        drawCell(startX + colWidth, startY, "Total Manga", "${stats.totalManga}", "Judul")

        // Row 2
        drawCell(startX, startY + rowHeight, "Waktu Tonton", "${stats.daysWatched}", "Hari")
        drawCell(startX + colWidth, startY + rowHeight, "Bab Dibaca", "${stats.chaptersRead}", "Bab")

        // Row 3
        drawCell(startX, startY + (rowHeight * 2), "Rata-Rata Skor", if (stats.meanScore > 0) "★ ${stats.meanScore}" else "—", "/ 10")
        drawCell(startX + colWidth, startY + (rowHeight * 2), "Ditamatkan", "${stats.completedCount}", "Total")
    }

    private fun drawPieChartAndLegend(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        slices: List<CanvasPieSlice>,
        legendX: Float,
        legendStartY: Float,
        legendItemSpacing: Float = 26f,
        legendFontSize: Float = 14f
    ) {
        val totalCount = slices.sumOf { it.count }.toFloat()
        val pieRect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        if (totalCount > 0f) {
            var startAngle = -90f
            slices.forEach { slice ->
                val sweepAngle = (slice.count / totalCount) * 360f
                val slicePaint = Paint().apply {
                    color = slice.color
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawArc(pieRect, startAngle, sweepAngle, true, slicePaint)
                startAngle += sweepAngle
            }

            // Inner doughnut hole
            val holePaint = Paint().apply {
                color = Color.rgb(17, 24, 39)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val holeRadius = radius * 0.56f
            canvas.drawCircle(centerX, centerY, holeRadius, holePaint)

            // Center text
            val totalCountPaint = Paint().apply {
                color = Color.WHITE
                textSize = (holeRadius * 0.50f).coerceIn(13f, 22f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val totalSubPaint = Paint().apply {
                color = Color.rgb(156, 163, 175)
                textSize = (holeRadius * 0.32f).coerceIn(9f, 13f)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("${totalCount.toInt()}", centerX, centerY + 2f, totalCountPaint)
            canvas.drawText("Total", centerX, centerY + (holeRadius * 0.40f) + 4f, totalSubPaint)
        }

        // Legend
        val legendTextPaint = Paint().apply {
            color = Color.rgb(209, 213, 219)
            textSize = legendFontSize
            isAntiAlias = true
        }
        val legendValPaint = Paint().apply {
            color = Color.WHITE
            textSize = legendFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var currY = legendStartY
        slices.forEach { slice ->
            val dotPaint = Paint().apply {
                color = slice.color
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(legendX + 6f, currY - (legendFontSize * 0.35f), 5f, dotPaint)
            canvas.drawText(slice.label, legendX + 16f, currY, legendTextPaint)
            val pct = if (totalCount > 0f) ((slice.count / totalCount) * 100).toInt() else 0
            canvas.drawText("${slice.count} ($pct%)", legendX + 140f, currY, legendValPaint)
            currY += legendItemSpacing
        }
    }

    private fun drawCoverCard(
        canvas: Canvas,
        rect: RectF,
        bitmap: Bitmap?,
        rank: Int,
        title: String,
        score: Int,
        subtitle: String,
        accentColor: Int
    ) {
        val cornerRadius = 16f
        val path = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(path)

        // 1. Draw Cover or Fallback Background
        if (bitmap != null) {
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            canvas.drawBitmap(bitmap, srcRect, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val fallbackPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
            }
            canvas.drawRect(rect, fallbackPaint)
        }

        // 2. High-contrast Dark Fading Gradient at Bottom of Cover
        val gradPaint = Paint().apply {
            shader = LinearGradient(
                rect.left, rect.top + rect.height() * 0.30f,
                rect.left, rect.bottom,
                intArrayOf(Color.TRANSPARENT, Color.argb(195, 10, 15, 29), Color.argb(252, 2, 6, 23)),
                floatArrayOf(0f, 0.40f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(rect, gradPaint)

        // 3. Top Rank Badge
        val rankBadgePaint = Paint().apply {
            color = Color.argb(210, 15, 23, 42)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val rankRect = RectF(rect.left + 8f, rect.top + 8f, rect.left + 44f, rect.top + 38f)
        canvas.drawRoundRect(rankRect, 8f, 8f, rankBadgePaint)

        val rankTextPaint = Paint().apply {
            color = accentColor
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("#$rank", rankRect.centerX(), rankRect.centerY() + 5f, rankTextPaint)

        // 4. Score Badge inside Cover
        if (score > 0) {
            val scoreBadgePaint = Paint().apply {
                color = Color.argb(210, 15, 23, 42)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val scoreRect = RectF(rect.right - 68f, rect.top + 8f, rect.right - 8f, rect.top + 38f)
            canvas.drawRoundRect(scoreRect, 8f, 8f, scoreBadgePaint)

            val scoreTextPaint = Paint().apply {
                color = Color.rgb(250, 204, 21) // Gold
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("★ $score", scoreRect.centerX(), scoreRect.centerY() + 5f, scoreTextPaint)
        }

        // 5. Complete Title without Truncation & Subtitle
        drawCardTitleAndSubtitle(canvas, rect, title, subtitle)

        canvas.restore()

        // Card Border
        val borderPaint = Paint().apply {
            color = Color.argb(80, 56, 189, 248)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
    }

    /**
     * Draws complete anime/manga title dynamically scaling down font size and allowing up to 3 lines
     * so that titles are fully readable without being cut off.
     */
    private fun drawCardTitleAndSubtitle(
        canvas: Canvas,
        rect: RectF,
        title: String,
        subtitle: String
    ) {
        val maxAvailableWidth = rect.width() - 16f
        var titleFontSize = if (rect.height() < 220f) 14f else 16f
        val titlePaint = Paint().apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var lines: List<String>
        do {
            titlePaint.textSize = titleFontSize
            lines = wrapText(title, titlePaint, maxAvailableWidth)
            if (lines.size <= 3 || titleFontSize <= 10f) break
            titleFontSize -= 1f
        } while (titleFontSize >= 10f)

        val lineHeight = titleFontSize * 1.25f
        val subPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = (titleFontSize - 2f).coerceAtLeast(10f)
            isAntiAlias = true
        }

        val subtitleY = rect.bottom - 10f
        var titleStartY = subtitleY - 14f - ((lines.size - 1) * lineHeight)
        // Ensure title does not overlap with top rank badge
        titleStartY = titleStartY.coerceAtLeast(rect.top + 46f)

        lines.forEach { line ->
            canvas.drawText(line, rect.left + 10f, titleStartY, titlePaint)
            titleStartY += lineHeight
        }
        canvas.drawText(subtitle, rect.left + 10f, subtitleY, subPaint)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine = candidate
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                // If single word exceeds maxWidth, break character by character
                if (paint.measureText(word) > maxWidth) {
                    var chunk = ""
                    for (char in word) {
                        if (paint.measureText(chunk + char) <= maxWidth) {
                            chunk += char
                        } else {
                            if (chunk.isNotEmpty()) lines.add(chunk)
                            chunk = char.toString()
                        }
                    }
                    currentLine = chunk
                } else {
                    currentLine = word
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }
}
