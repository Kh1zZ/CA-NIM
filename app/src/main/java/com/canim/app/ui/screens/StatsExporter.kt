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
    JPG("Gambar JPG (1080p)", "jpg", "image/jpeg"),
    PNG("Gambar PNG (1080p)", "png", "image/png")
}

object StatsExporter {

    suspend fun exportAndShareStats(
        context: Context,
        stats: TrackerStats,
        malUser: MalUser,
        topAnime: List<UserMediaItem>,
        topManga: List<UserMediaItem>,
        format: StatsExportFormat
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val bitmap = renderStatsBitmap(context, stats, malUser, topAnime, topManga)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "canim_stats_${malUser.username.ifBlank { "user" }}_$timeStamp.${format.extension}"

            // 1. Save locally for FileProvider sharing
            val statsDir = File(context.cacheDir, "stats").apply { mkdirs() }
            val localFile = File(statsDir, filename)

            FileOutputStream(localFile).use { fos ->
                when (format) {
                    StatsExportFormat.JPG -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                    StatsExportFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    StatsExportFormat.PDF -> {
                        val pdfDoc = PdfDocument()
                        val pageInfo = PdfDocument.PageInfo.Builder(1920, 1080, 1).create()
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
        topManga: List<UserMediaItem>
    ): Bitmap {
        val width = 1920
        val height = 1080
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

        // Decorative background accents
        val glowPaint = Paint().apply {
            shader = RadialGradient(
                width * 0.85f, height * 0.15f, 500f,
                Color.argb(45, 56, 189, 248), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.85f, height * 0.15f, 500f, glowPaint)

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

        // Header Section with App Logo Bitmap
        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_logo)
        }.getOrNull()

        var headerTextX = 80f
        if (logoBitmap != null) {
            val logoHeight = 64f
            val logoWidth = logoBitmap.width * (logoHeight / logoBitmap.height)
            val destRect = RectF(80f, 32f, 80f + logoWidth, 32f + logoHeight)
            canvas.drawBitmap(logoBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            headerTextX = 80f + logoWidth + 24f
        }

        val appSubPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Lacak Anime dan Mangamu • Terhubung dengan MAL", headerTextX, 72f, appSubPaint)

        // Divider
        val divPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            strokeWidth = 2f
        }
        canvas.drawLine(80f, 115f, width - 80f, 115f, divPaint)

        // LEFT COLUMN: MAL Profile & Key Metrics (Width: 500px, x: 80f to 580f)
        val leftCardRect = RectF(80f, 140f, 580f, 1000f)
        canvas.drawRoundRect(leftCardRect, 24f, 24f, cardBgPaint)
        canvas.drawRoundRect(leftCardRect, 24f, 24f, cardBorderPaint)

        val secHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("PROFIL MYANIMELIST", 110f, 185f, secHeaderPaint)

        // MAL Profile Box (Username, Lokasi, Gender - Tanpa Tanggal Lahir)
        val profileLabelPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = 18f
            isAntiAlias = true
        }
        val profileValPaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var profileY = 225f
        fun drawProfileRow(label: String, value: String) {
            canvas.drawText(label, 110f, profileY, profileLabelPaint)
            val cleanVal = if (value.isBlank()) "-" else value
            canvas.drawText(cleanVal, 220f, profileY, profileValPaint)
            profileY += 38f
        }

        drawProfileRow("Username:", "@${malUser.username.ifBlank { "Tamu" }}")
        drawProfileRow("Lokasi:", malUser.location ?: "-")
        drawProfileRow("Gender:", malUser.gender?.replaceFirstChar { it.uppercase() } ?: "-")

        // Metric divider
        profileY += 10f
        canvas.drawLine(110f, profileY, 550f, profileY, divPaint)
        profileY += 45f

        canvas.drawText("RINGKASAN METRIK", 110f, profileY, secHeaderPaint)
        profileY += 40f

        val metricLabelPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = 18f
            isAntiAlias = true
        }
        val metricValuePaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        fun drawMetric(label: String, value: String, unit: String = "") {
            canvas.drawText(label, 110f, profileY, metricLabelPaint)
            canvas.drawText(value, 110f, profileY + 36f, metricValuePaint)
            if (unit.isNotEmpty()) {
                val valWidth = metricValuePaint.measureText(value)
                val unitPaint = Paint().apply {
                    color = Color.rgb(148, 163, 184)
                    textSize = 18f
                    isAntiAlias = true
                }
                canvas.drawText(unit, 110f + valWidth + 10f, profileY + 34f, unitPaint)
            }
            profileY += 80f
        }

        drawMetric("Total Judul Anime", "${stats.totalAnime}", "Judul")
        drawMetric("Total Judul Manga", "${stats.totalManga}", "Judul")
        drawMetric("Total Waktu Tonton", "${stats.daysWatched}", "Hari (${(stats.episodesWatched * 24) / 60} Jam)")
        drawMetric("Total Bab Dibaca", "${stats.chaptersRead}", "Bab")
        drawMetric("Rata-Rata Skor", if (stats.meanScore > 0) "★ ${stats.meanScore}" else "-", "/ 10")
        drawMetric("Total Ditamatkan", "${stats.completedCount}", "Anime + Manga")

        // RIGHT AREA: TOP 5 ANIME & TOP 5 MANGA COVERS WITH GRADIENT FADING
        val cardWidth = 224f
        val cardHeight = 336f
        val cardSpacing = 20f
        val rightStartX = 620f

        // 1. TOP 5 ANIME
        val animeSecHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP 5 ANIME PRIBADI (SKOR TERTINGGI)", rightStartX, 170f, animeSecHeaderPaint)

        val topAnimeY = 195f
        if (topAnime.isEmpty()) {
            val emptyPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 20f
                isAntiAlias = true
            }
            canvas.drawText("Belum ada anime yang diberi skor personal di koleksi Anda", rightStartX, topAnimeY + 60f, emptyPaint)
        } else {
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
        }

        // 2. TOP 5 MANGA
        val mangaSecHeaderPaint = Paint().apply {
            color = Color.rgb(96, 165, 250)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val topMangaLabelY = 590f
        canvas.drawText("TOP 5 MANGA PRIBADI (SKOR TERTINGGI)", rightStartX, topMangaLabelY, mangaSecHeaderPaint)

        val topMangaY = 615f
        if (topManga.isEmpty()) {
            val emptyPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 20f
                isAntiAlias = true
            }
            canvas.drawText("Belum ada manga yang diberi skor personal di koleksi Anda", rightStartX, topMangaY + 60f, emptyPaint)
        } else {
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

        // FOOTER: Low Opacity (0.4 alpha) Credit
        val footerPaint = Paint().apply {
            color = Color.argb(102, 255, 255, 255) // 0.4 alpha
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("Dibuat dengan CA-NIM • github.com/Kh1zZ/CA-NIM", 80f, 1045f, footerPaint)

        return bitmap
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
        val cornerRadius = 18f
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

        // 2. Dark Fading Gradient at Bottom of the Cover
        val gradPaint = Paint().apply {
            shader = LinearGradient(
                rect.left, rect.top + rect.height() * 0.35f,
                rect.left, rect.bottom,
                intArrayOf(Color.TRANSPARENT, Color.argb(190, 10, 15, 29), Color.argb(250, 2, 6, 23)),
                floatArrayOf(0f, 0.45f, 1f),
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
        val rankRect = RectF(rect.left + 10f, rect.top + 10f, rect.left + 48f, rect.top + 42f)
        canvas.drawRoundRect(rankRect, 8f, 8f, rankBadgePaint)

        val rankTextPaint = Paint().apply {
            color = accentColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("#$rank", rankRect.centerX(), rankRect.centerY() + 6f, rankTextPaint)

        // 4. Score Badge inside Cover
        if (score > 0) {
            val scoreBadgePaint = Paint().apply {
                color = Color.argb(210, 15, 23, 42)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val scoreRect = RectF(rect.right - 72f, rect.top + 10f, rect.right - 10f, rect.top + 42f)
            canvas.drawRoundRect(scoreRect, 8f, 8f, scoreBadgePaint)

            val scoreTextPaint = Paint().apply {
                color = Color.rgb(250, 204, 21) // Gold
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("★ $score", scoreRect.centerX(), scoreRect.centerY() + 6f, scoreTextPaint)
        }

        // 5. Title & Progress Inside the Bottom Gradient
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = 14f
            isAntiAlias = true
        }

        val maxWidth = rect.width() - 24f
        val lines = wrapText(title, titlePaint, maxWidth, maxLines = 2)

        var textY = rect.bottom - 20f - (lines.size * 22f)
        lines.forEach { line ->
            canvas.drawText(line, rect.left + 12f, textY, titlePaint)
            textY += 22f
        }
        canvas.drawText(subtitle, rect.left + 12f, rect.bottom - 12f, subPaint)

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

    private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
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
                currentLine = word
                if (lines.size == maxLines - 1) break
            }
        }

        if (currentLine.isNotEmpty() && lines.size < maxLines) {
            lines.add(currentLine)
        }

        // If last line overflows, truncate with ellipsis
        if (lines.isNotEmpty()) {
            val lastIdx = lines.size - 1
            if (paint.measureText(lines[lastIdx]) > maxWidth) {
                var truncated = lines[lastIdx]
                while (truncated.isNotEmpty() && paint.measureText("$truncated...") > maxWidth) {
                    truncated = truncated.dropLast(1)
                }
                lines[lastIdx] = "$truncated..."
            }
        }

        return lines
    }
}
