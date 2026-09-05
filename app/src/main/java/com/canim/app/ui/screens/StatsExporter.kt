package com.canim.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.canim.app.data.model.MalUser
import com.canim.app.data.model.TrackerStats
import com.canim.app.data.model.UserMediaItem
import kotlinx.coroutines.Dispatchers
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
            val bitmap = renderStatsBitmap(stats, malUser, topAnime, topManga)
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

    private fun renderStatsBitmap(
        stats: TrackerStats,
        malUser: MalUser,
        topAnime: List<UserMediaItem>,
        topManga: List<UserMediaItem>
    ): Bitmap {
        val width = 1920
        val height = 1080
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

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

        // Header Section
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("CA'NIM", 80f, 100f, titlePaint)

        val appSubPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Lacak Anime dan Mangamu • Terhubung dengan MAL", 250f, 98f, appSubPaint)

        val userBadgePaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val usernameStr = malUser.username.ifBlank { "Tamu (Mode Offline)" }
        canvas.drawText("Pengguna: @$usernameStr", width - 480f, 98f, userBadgePaint)

        // Divider
        val divPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            strokeWidth = 2f
        }
        canvas.drawLine(80f, 130f, width - 80f, 130f, divPaint)

        // LEFT COLUMN: Key Metrics & Status Breakdown (Width: 540px)
        val leftCardRect = RectF(80f, 160f, 620f, 980f)
        canvas.drawRoundRect(leftCardRect, 24f, 24f, cardBgPaint)
        canvas.drawRoundRect(leftCardRect, 24f, 24f, cardBorderPaint)

        val secHeaderPaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("RINGKASAN METRIK", 110f, 210f, secHeaderPaint)

        val metricLabelPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = 20f
            isAntiAlias = true
        }
        val metricValuePaint = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var yOffset = 270f
        fun drawMetric(label: String, value: String, unit: String = "") {
            canvas.drawText(label, 110f, yOffset, metricLabelPaint)
            canvas.drawText(value, 110f, yOffset + 40f, metricValuePaint)
            if (unit.isNotEmpty()) {
                val valWidth = metricValuePaint.measureText(value)
                val unitPaint = Paint().apply {
                    color = Color.rgb(148, 163, 184)
                    textSize = 20f
                    isAntiAlias = true
                }
                canvas.drawText(unit, 110f + valWidth + 10f, yOffset + 38f, unitPaint)
            }
            yOffset += 90f
        }

        drawMetric("Total Judul Anime", "${stats.totalAnime}", "Judul")
        drawMetric("Total Judul Manga", "${stats.totalManga}", "Judul")
        drawMetric("Total Waktu Tonton", "${stats.daysWatched}", "Hari (${(stats.episodesWatched * 24) / 60} Jam)")
        drawMetric("Total Bab Dibaca", "${stats.chaptersRead}", "Bab")
        drawMetric("Rata-Rata Skor", if (stats.meanScore > 0) "★ ${stats.meanScore}" else "-", "/ 10")
        drawMetric("Total Ditamatkan", "${stats.completedCount}", "Anime + Manga")

        // Draw Mini Status Bars inside left column
        canvas.drawLine(110f, yOffset + 10f, 590f, yOffset + 10f, divPaint)
        yOffset += 50f
        canvas.drawText("DISTRIBUSI STATUS", 110f, yOffset, secHeaderPaint)
        yOffset += 40f

        val distPaint = Paint().apply {
            color = Color.rgb(209, 213, 219)
            textSize = 18f
            isAntiAlias = true
        }
        canvas.drawText("Anime: ${stats.animeWatching} Menonton • ${stats.animeCompleted} Tamat • ${stats.animePlanToWatch} Rencana", 110f, yOffset, distPaint)
        yOffset += 30f
        canvas.drawText("Manga: ${stats.mangaReading} Membaca • ${stats.mangaCompleted} Tamat • ${stats.mangaPlanToRead} Rencana", 110f, yOffset, distPaint)

        // RIGHT COLUMN TOP: TOP 5 ANIME (Width: 1220px, Height: 380px)
        val rightTopRect = RectF(660f, 160f, 1840f, 550f)
        canvas.drawRoundRect(rightTopRect, 24f, 24f, cardBgPaint)
        canvas.drawRoundRect(rightTopRect, 24f, 24f, cardBorderPaint)

        canvas.drawText("TOP 5 ANIME PRIBADI (SKOR TERTINGGI)", 690f, 210f, secHeaderPaint)

        val itemTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val itemSubPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = 17f
            isAntiAlias = true
        }
        val scorePaint = Paint().apply {
            color = Color.rgb(250, 204, 21) // Gold
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var animeY = 265f
        if (topAnime.isEmpty()) {
            canvas.drawText("Belum ada anime yang diberi skor personal", 690f, animeY + 40f, itemSubPaint)
        } else {
            topAnime.take(5).forEachIndexed { idx, item ->
                val rankText = "#${idx + 1}"
                canvas.drawText(rankText, 690f, animeY, secHeaderPaint)
                val safeTitle = if (item.title.length > 55) item.title.take(52) + "..." else item.title
                canvas.drawText(safeTitle, 750f, animeY, itemTitlePaint)
                canvas.drawText("${item.progress} / ${if (item.totalEpisodes > 0) item.totalEpisodes else "?"} Ep • ${item.status.replace("_", " ").replaceFirstChar { it.uppercase() }}", 750f, animeY + 24f, itemSubPaint)
                canvas.drawText("★ ${item.score}", 1740f, animeY + 10f, scorePaint)
                animeY += 56f
            }
        }

        // RIGHT COLUMN BOTTOM: TOP 5 MANGA (Width: 1220px, Height: 380px)
        val rightBottomRect = RectF(660f, 580f, 1840f, 980f)
        canvas.drawRoundRect(rightBottomRect, 24f, 24f, cardBgPaint)
        canvas.drawRoundRect(rightBottomRect, 24f, 24f, cardBorderPaint)

        val mangaSecHeaderPaint = Paint().apply {
            color = Color.rgb(96, 165, 250) // Blue
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("TOP 5 MANGA PRIBADI (SKOR TERTINGGI)", 690f, 630f, mangaSecHeaderPaint)

        var mangaY = 685f
        if (topManga.isEmpty()) {
            canvas.drawText("Belum ada manga yang diberi skor personal", 690f, mangaY + 40f, itemSubPaint)
        } else {
            topManga.take(5).forEachIndexed { idx, item ->
                val rankText = "#${idx + 1}"
                canvas.drawText(rankText, 690f, mangaY, mangaSecHeaderPaint)
                val safeTitle = if (item.title.length > 55) item.title.take(52) + "..." else item.title
                canvas.drawText(safeTitle, 750f, mangaY, itemTitlePaint)
                canvas.drawText("${item.progressChapters} Ch • ${item.status.replace("_", " ").replaceFirstChar { it.uppercase() }}", 750f, mangaY + 24f, itemSubPaint)
                canvas.drawText("★ ${item.score}", 1740f, mangaY + 10f, scorePaint)
                mangaY += 56f
            }
        }

        // Footer Brand & Disclaimer
        val footerPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 16f
            isAntiAlias = true
        }
        canvas.drawText("Dibuat otomatis oleh CA'NIM v4.1.0 • Single Source of Truth MyAnimeList • AniList Rich Metadata", 80f, 1030f, footerPaint)

        return bitmap
    }
}
