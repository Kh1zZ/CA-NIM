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
    STORY_9_16("9:16 (Story)", 1080, 1920, false),
    PORTRAIT_4_5("4:5", 1080, 1350, false),
    PORTRAIT_3_4("3:4", 1080, 1440, false),
    SQUARE_1_1("1:1", 1080, 1080, false),
    LANDSCAPE_16_9("16:9 (Landscape)", 1920, 1080, true)
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
            // Apply Top 5 Sequel exclusion rule for Anime
            val filteredTopAnime = AnimeFranchiseFilter.selectTopAnimeNonSequel(topAnime, 5)
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
                0f, 0f, width, height,
                intArrayOf(Color.rgb(10, 15, 29), Color.rgb(15, 23, 42), Color.rgb(2, 6, 23)),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // Decorative background glow
        val glowPaint = Paint().apply {
            shader = RadialGradient(
                width * 0.85f, height * 0.15f, width * 0.40f,
                Color.argb(45, 56, 189, 248), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.85f, height * 0.15f, width * 0.40f, glowPaint)

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

        val margin = if (width >= 1200) 65f else 50f
        var headerTextX = margin
        val headerY = if (aspectRatio == ExportAspectRatio.STORY_9_16) 48f else 38f
        val logoHeight = if (aspectRatio == ExportAspectRatio.STORY_9_16) 66f else 56f

        if (logoBitmap != null) {
            val logoWidth = logoBitmap.width * (logoHeight / logoBitmap.height)
            val destRect = RectF(margin, headerY, margin + logoWidth, headerY + logoHeight)
            canvas.drawBitmap(logoBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            headerTextX = margin + logoWidth + 20f
        }

        val appSubPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = if (aspectRatio == ExportAspectRatio.STORY_9_16) 24f else 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Lacak Anime dan Mangamu", headerTextX, headerY + (logoHeight * 0.65f), appSubPaint)

        // Header Divider
        val divY = headerY + logoHeight + 16f
        canvas.drawLine(margin, divY, width - margin, divY, divPaint)

        // Status Distribution Pie Slices
        val pieSlices = listOf(
            CanvasPieSlice("Ditonton / Baca", stats.animeWatching + stats.mangaReading, Color.rgb(56, 189, 248)),
            CanvasPieSlice("Selesai", stats.animeCompleted + stats.mangaCompleted, Color.rgb(34, 197, 94)),
            CanvasPieSlice("Ditunda", stats.animeOnHold + stats.mangaOnHold, Color.rgb(245, 158, 11)),
            CanvasPieSlice("Drop", stats.animeDropped + stats.mangaDropped, Color.rgb(239, 68, 68)),
            CanvasPieSlice("Rencana", stats.animePlanToWatch + stats.mangaPlanToRead, Color.rgb(168, 85, 247))
        ).filter { it.count > 0 }

        when (aspectRatio) {
            ExportAspectRatio.STORY_9_16 -> {
                renderLayoutStory(
                    canvas = canvas,
                    width = width,
                    margin = margin,
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
            ExportAspectRatio.PORTRAIT_4_5 -> {
                renderLayoutPortrait(
                    canvas = canvas,
                    width = width,
                    margin = margin,
                    topY = divY + 16f,
                    topCardHeight = 515f,
                    cardHeight = 265f,
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
            ExportAspectRatio.PORTRAIT_3_4 -> {
                renderLayoutPortrait(
                    canvas = canvas,
                    width = width,
                    margin = margin,
                    topY = divY + 18f,
                    topCardHeight = 560f,
                    cardHeight = 275f,
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
            ExportAspectRatio.SQUARE_1_1 -> {
                renderLayoutSquare(
                    canvas = canvas,
                    width = width,
                    margin = margin,
                    topY = divY + 12f,
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
            ExportAspectRatio.LANDSCAPE_16_9 -> {
                renderLayoutLandscape(
                    canvas = canvas,
                    width = width,
                    height = height,
                    margin = margin,
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
        }

        // FOOTER: Low Opacity Credit
        val footerPaint = Paint().apply {
            color = Color.argb(120, 255, 255, 255)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("Dibuat dengan CA-NIM • github.com/Kh1zZ/CA-NIM", margin, height - 16f, footerPaint)

        return bitmap
    }

    // --- LAYOUT STORY 9:16 (1080 x 1920) ---
    private fun renderLayoutStory(
        canvas: Canvas,
        width: Float,
        margin: Float,
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
        val secHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // 1. Profile Section (Large & Prominent, height = 240)
        val profileCardRect = RectF(margin, topY, width - margin, topY + 240f)
        canvas.drawRoundRect(profileCardRect, 22f, 22f, cardBgPaint)
        canvas.drawRoundRect(profileCardRect, 22f, 22f, cardBorderPaint)

        canvas.drawText("PROFIL MYANIMELIST", margin + 25f, topY + 38f, secHeaderPaint)
        drawLargeProfileSection(canvas, margin + 25f, topY + 80f, malUser)

        // 2. Metrics Summary Card (3 cols x 2 rows, height = 340)
        val metricsTop = topY + 260f
        val metricsCardRect = RectF(margin, metricsTop, width - margin, metricsTop + 340f)
        canvas.drawRoundRect(metricsCardRect, 22f, 22f, cardBgPaint)
        canvas.drawRoundRect(metricsCardRect, 22f, 22f, cardBorderPaint)

        canvas.drawText("RINGKASAN KOLEKSI & STATISTIK", margin + 25f, metricsTop + 38f, secHeaderPaint)
        drawMetricsMatrix(
            canvas = canvas,
            startX = margin + 25f,
            startY = metricsTop + 75f,
            colWidth = 310f,
            rowHeight = 115f,
            stats = stats,
            labelSize = 15f,
            valSize = 28f
        )

        // 3. Giant Status Distribution Pie Chart Card (height = 420)
        val pieCardTop = metricsTop + 360f
        val pieCardRect = RectF(margin, pieCardTop, width - margin, pieCardTop + 420f)
        canvas.drawRoundRect(pieCardRect, 22f, 22f, cardBgPaint)
        canvas.drawRoundRect(pieCardRect, 22f, 22f, cardBorderPaint)

        canvas.drawText("DISTRIBUSI STATUS KOLEKSI", margin + 25f, pieCardTop + 40f, secHeaderPaint)
        drawPieChartAndLegend(
            canvas = canvas,
            centerX = margin + 175f,
            centerY = pieCardTop + 225f,
            radius = 135f,
            slices = pieSlices,
            legendX = margin + 355f,
            legendStartY = pieCardTop + 105f,
            legendItemSpacing = 52f,
            legendFontSize = 18f
        )

        // 4. Top 5 Anime Section (Natural 2:3 poster proportions, height = 265)
        val animeSecY = pieCardTop + 445f
        val animeCardTop = animeSecY + 32f
        val cardSpacing = 16f
        val cardWidth = (width - (margin * 2f) - (4 * cardSpacing)) / 5f
        val cardHeight = 265f

        val animeHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP 5 ANIME PRIBADI (NON-SEQUEL)", margin, animeSecY + 18f, animeHeaderPaint)

        topAnime.take(5).forEachIndexed { idx, item ->
            val cardX = margin + idx * (cardWidth + cardSpacing)
            val cardRect = RectF(cardX, animeCardTop, cardX + cardWidth, animeCardTop + cardHeight)
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

        // 5. Top 5 Manga Section (Natural 2:3 poster proportions, height = 265)
        val mangaSecY = animeCardTop + cardHeight + 28f
        val mangaCardTop = mangaSecY + 32f

        val mangaHeaderPaint = Paint().apply {
            color = Color.rgb(96, 165, 250)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP 5 MANGA PRIBADI (SKOR TERTINGGI)", margin, mangaSecY + 18f, mangaHeaderPaint)

        topManga.take(5).forEachIndexed { idx, item ->
            val cardX = margin + idx * (cardWidth + cardSpacing)
            val cardRect = RectF(cardX, mangaCardTop, cardX + cardWidth, mangaCardTop + cardHeight)
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

    // --- SHARED PORTRAIT LAYOUT (4:5 and 3:4) ---
    private fun renderLayoutPortrait(
        canvas: Canvas,
        width: Float,
        margin: Float,
        topY: Float,
        topCardHeight: Float,
        cardHeight: Float,
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
        val totalUsableWidth = width - (margin * 2f)
        val halfCardWidth = (totalUsableWidth - 20f) / 2f

        val secHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Left Card: Profile & Metrics (Spacious)
        val leftCardRect = RectF(margin, topY, margin + halfCardWidth, topY + topCardHeight)
        canvas.drawRoundRect(leftCardRect, 22f, 22f, cardBgPaint)
        canvas.drawRoundRect(leftCardRect, 22f, 22f, cardBorderPaint)

        canvas.drawText("PROFIL MYANIMELIST", margin + 25f, topY + 36f, secHeaderPaint)
        drawLargeProfileSection(canvas, margin + 25f, topY + 75f, malUser)

        val divInnerY = topY + 185f
        canvas.drawLine(margin + 25f, divInnerY, margin + halfCardWidth - 25f, divInnerY, divPaint)

        canvas.drawText("RINGKASAN METRIK", margin + 25f, divInnerY + 36f, secHeaderPaint)
        drawMetricsMatrix(
            canvas = canvas,
            startX = margin + 25f,
            startY = divInnerY + 68f,
            colWidth = (halfCardWidth - 50f) / 2f,
            rowHeight = if (topCardHeight >= 550f) 72f else 64f,
            stats = stats,
            labelSize = 13f,
            valSize = 22f
        )

        // Right Card: Giant Status Distribution Pie Chart & Legend
        val rightCardLeft = margin + halfCardWidth + 20f
        val rightCardRect = RectF(rightCardLeft, topY, margin + totalUsableWidth, topY + topCardHeight)
        canvas.drawRoundRect(rightCardRect, 22f, 22f, cardBgPaint)
        canvas.drawRoundRect(rightCardRect, 22f, 22f, cardBorderPaint)

        canvas.drawText("DISTRIBUSI STATUS", rightCardLeft + 25f, topY + 36f, secHeaderPaint)
        val pieRadius = if (topCardHeight >= 550f) 125f else 115f
        drawPieChartAndLegend(
            canvas = canvas,
            centerX = rightCardLeft + (halfCardWidth / 2f),
            centerY = topY + 185f,
            radius = pieRadius,
            slices = pieSlices,
            legendX = rightCardLeft + 35f,
            legendStartY = topY + 335f,
            legendItemSpacing = if (topCardHeight >= 550f) 34f else 30f,
            legendFontSize = 15f
        )

        // Middle Section: Top 5 Anime (Natural 2:3 ratio, no stretching)
        val cardSpacing = 15f
        val itemCardWidth = (totalUsableWidth - (4 * cardSpacing)) / 5f

        val animeSecY = topY + topCardHeight + 25f
        val animeCardTop = animeSecY + 28f

        val animeHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP 5 ANIME PRIBADI (NON-SEQUEL)", margin, animeSecY + 14f, animeHeaderPaint)

        topAnime.take(5).forEachIndexed { idx, item ->
            val cardX = margin + idx * (itemCardWidth + cardSpacing)
            val cardRect = RectF(cardX, animeCardTop, cardX + itemCardWidth, animeCardTop + cardHeight)
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

        // Bottom Section: Top 5 Manga (Natural 2:3 ratio, no stretching)
        val mangaSecY = animeCardTop + cardHeight + 25f
        val mangaCardTop = mangaSecY + 28f

        val mangaHeaderPaint = Paint().apply {
            color = Color.rgb(96, 165, 250)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP 5 MANGA PRIBADI (SKOR TERTINGGI)", margin, mangaSecY + 14f, mangaHeaderPaint)

        topManga.take(5).forEachIndexed { idx, item ->
            val cardX = margin + idx * (itemCardWidth + cardSpacing)
            val cardRect = RectF(cardX, mangaCardTop, cardX + itemCardWidth, mangaCardTop + cardHeight)
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

    // --- SQUARE 1:1 LAYOUT (1080 x 1080) ---
    private fun renderLayoutSquare(
        canvas: Canvas,
        width: Float,
        margin: Float,
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
        val totalUsableWidth = width - (margin * 2f)
        val halfCardWidth = (totalUsableWidth - 20f) / 2f
        val topCardHeight = 340f
        val cardHeight = 245f

        val secHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Left Card: Profile & 6 metrics
        val leftCardRect = RectF(margin, topY, margin + halfCardWidth, topY + topCardHeight)
        canvas.drawRoundRect(leftCardRect, 20f, 20f, cardBgPaint)
        canvas.drawRoundRect(leftCardRect, 20f, 20f, cardBorderPaint)

        canvas.drawText("PROFIL & METRIK", margin + 20f, topY + 30f, secHeaderPaint)
        drawLargeProfileSection(canvas, margin + 20f, topY + 60f, malUser, usernameSize = 20f, labelSize = 13f, rowSpacing = 22f)

        val divInnerY = topY + 130f
        canvas.drawLine(margin + 20f, divInnerY, margin + halfCardWidth - 20f, divInnerY, divPaint)

        drawMetricsMatrix(
            canvas = canvas,
            startX = margin + 20f,
            startY = divInnerY + 26f,
            colWidth = (halfCardWidth - 40f) / 2f,
            rowHeight = 46f,
            stats = stats,
            labelSize = 12f,
            valSize = 18f
        )

        // Right Card: Pie Chart + Legend
        val rightCardLeft = margin + halfCardWidth + 20f
        val rightCardRect = RectF(rightCardLeft, topY, margin + totalUsableWidth, topY + topCardHeight)
        canvas.drawRoundRect(rightCardRect, 20f, 20f, cardBgPaint)
        canvas.drawRoundRect(rightCardRect, 20f, 20f, cardBorderPaint)

        canvas.drawText("DISTRIBUSI STATUS", rightCardLeft + 20f, topY + 30f, secHeaderPaint)
        drawPieChartAndLegend(
            canvas = canvas,
            centerX = rightCardLeft + 105f,
            centerY = topY + 185f,
            radius = 85f,
            slices = pieSlices,
            legendX = rightCardLeft + 215f,
            legendStartY = topY + 75f,
            legendItemSpacing = 26f,
            legendFontSize = 13f
        )

        // Top 5 Anime
        val cardSpacing = 15f
        val itemCardWidth = (totalUsableWidth - (4 * cardSpacing)) / 5f

        val animeSecY = topY + topCardHeight + 20f
        val animeCardTop = animeSecY + 26f

        val animeHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP 5 ANIME PRIBADI (NON-SEQUEL)", margin, animeSecY + 14f, animeHeaderPaint)

        topAnime.take(5).forEachIndexed { idx, item ->
            val cardX = margin + idx * (itemCardWidth + cardSpacing)
            val cardRect = RectF(cardX, animeCardTop, cardX + itemCardWidth, animeCardTop + cardHeight)
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
        val mangaSecY = animeCardTop + cardHeight + 20f
        val mangaCardTop = mangaSecY + 26f

        val mangaHeaderPaint = Paint().apply {
            color = Color.rgb(96, 165, 250)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP 5 MANGA PRIBADI (SKOR TERTINGGI)", margin, mangaSecY + 14f, mangaHeaderPaint)

        topManga.take(5).forEachIndexed { idx, item ->
            val cardX = margin + idx * (itemCardWidth + cardSpacing)
            val cardRect = RectF(cardX, mangaCardTop, cardX + itemCardWidth, mangaCardTop + cardHeight)
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

    // --- LAYOUT LANDSCAPE 16:9 (1920 x 1080) ---
    private fun renderLayoutLandscape(
        canvas: Canvas,
        width: Float,
        height: Float,
        margin: Float,
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
        val leftCardRect = RectF(margin, topY, margin + 520f, height - 50f)
        canvas.drawRoundRect(leftCardRect, 22f, 22f, cardBgPaint)
        canvas.drawRoundRect(leftCardRect, 22f, 22f, cardBorderPaint)

        val secHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawText("PROFIL MYANIMELIST", margin + 30f, topY + 40f, secHeaderPaint)
        drawLargeProfileSection(canvas, margin + 30f, topY + 80f, malUser)

        val div1Y = topY + 185f
        canvas.drawLine(margin + 30f, div1Y, margin + 490f, div1Y, divPaint)

        canvas.drawText("RINGKASAN METRIK", margin + 30f, div1Y + 36f, secHeaderPaint)
        drawMetricsMatrix(canvas, margin + 30f, div1Y + 68f, colWidth = 230f, rowHeight = 65f, stats = stats, labelSize = 13f, valSize = 22f)

        val div2Y = div1Y + 285f
        canvas.drawLine(margin + 30f, div2Y, margin + 490f, div2Y, divPaint)

        canvas.drawText("DISTRIBUSI STATUS KOLEKSI", margin + 30f, div2Y + 36f, secHeaderPaint)
        drawPieChartAndLegend(
            canvas = canvas,
            centerX = margin + 125f,
            centerY = div2Y + 165f,
            radius = 95f,
            slices = pieSlices,
            legendX = margin + 250f,
            legendStartY = div2Y + 90f,
            legendItemSpacing = 30f,
            legendFontSize = 15f
        )

        // Right Area: Top 5 Anime & Top 5 Manga (Width: 1220px)
        val rightStartX = margin + 550f
        val cardWidth = 224f
        val cardHeight = 330f
        val cardSpacing = 20f

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

    private fun drawLargeProfileSection(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        malUser: MalUser,
        usernameSize: Float = 26f,
        labelSize: Float = 15f,
        rowSpacing: Float = 28f
    ) {
        val userPaint = Paint().apply {
            color = Color.WHITE
            textSize = usernameSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = labelSize
            isAntiAlias = true
        }
        val valPaint = Paint().apply {
            color = Color.rgb(229, 231, 235)
            textSize = labelSize + 2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Draw bold username
        val username = "@${malUser.username.ifBlank { "Tamu" }}"
        canvas.drawText(username, startX, startY, userPaint)

        var curY = startY + rowSpacing + 4f
        fun drawRow(label: String, value: String) {
            canvas.drawText(label, startX, curY, labelPaint)
            val cleanVal = if (value.isBlank()) "-" else value
            canvas.drawText(cleanVal, startX + 110f, curY, valPaint)
            curY += rowSpacing
        }

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
        valSize: Float = 24f
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
        legendItemSpacing: Float = 28f,
        legendFontSize: Float = 15f
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
                textSize = (holeRadius * 0.52f).coerceIn(14f, 32f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val totalSubPaint = Paint().apply {
                color = Color.rgb(156, 163, 175)
                textSize = (holeRadius * 0.28f).coerceIn(10f, 15f)
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
            canvas.drawCircle(legendX + 8f, currY - (legendFontSize * 0.35f), 6f, dotPaint)
            canvas.drawText(slice.label, legendX + 22f, currY, legendTextPaint)
            val pct = if (totalCount > 0f) ((slice.count / totalCount) * 100).toInt() else 0
            canvas.drawText("${slice.count} ($pct%)", legendX + 160f, currY, legendValPaint)
            currY += legendItemSpacing
        }
    }

    /**
     * Draws a cover card maintaining true aspect-fill center crop so posters are NEVER stretched.
     */
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

        // 1. Draw Cover with Center-Crop (Aspect-Fill) to PREVENT ANY STRETCHING
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val rw = rect.width()
            val rh = rect.height()

            val scale = maxOf(rw / bw, rh / bh)
            val cropW = rw / scale
            val cropH = rh / scale
            val cropX = ((bw - cropW) / 2f).coerceAtLeast(0f)
            val cropY = ((bh - cropH) / 2f).coerceAtLeast(0f)

            val srcRect = Rect(cropX.toInt(), cropY.toInt(), (cropX + cropW).toInt(), (cropY + cropH).toInt())
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
                rect.left, rect.top + rect.height() * 0.25f,
                rect.left, rect.bottom,
                intArrayOf(Color.TRANSPARENT, Color.argb(195, 10, 15, 29), Color.argb(252, 2, 6, 23)),
                floatArrayOf(0f, 0.40f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(rect, gradPaint)

        // 3. Top Rank Badge
        val rankBadgePaint = Paint().apply {
            color = Color.argb(215, 15, 23, 42)
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
                color = Color.argb(215, 15, 23, 42)
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
        var titleFontSize = if (rect.height() < 250f) 13f else 15f
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
