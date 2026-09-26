package com.canim.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.canim.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaRatingExportData(
    val title: String,
    val titleEnglish: String? = null,
    val mediaType: MediaType = MediaType.ANIME,
    val imageUrl: String?,
    val malScore: Double?,
    val userScore: Int,
    val status: String,
    val progress: Int,
    val maxProgress: Int,
    val genres: List<String> = emptyList(),
    val studio: String?,
    val malUsername: String = "",
    val airingStatus: String? = null,
    val malId: Int? = null
)

object MediaRatingCardExporter {

    private const val CANVAS_WIDTH = 1080
    private const val CANVAS_HEIGHT = 1350 // Exact 4:5 aspect ratio

    suspend fun exportAndShareRatingCard(
        context: Context,
        data: MediaRatingExportData
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val coverBitmap = loadBitmap(context, data.imageUrl)
            val dominantColor = coverBitmap?.let { extractDominantColor(it) } ?: 0xFF3B82F6.toInt()

            val bitmap = renderRatingCardBitmap(data, coverBitmap, dominantColor)

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cleanUser = data.malUsername.ifBlank { "user" }.replace(Regex("[^a-zA-Z0-9_]"), "")
            val filename = "canim_rating_${cleanUser}_$timeStamp.jpg"

            val statsDir = File(context.cacheDir, "stats").apply { mkdirs() }
            val localFile = File(statsDir, filename)

            FileOutputStream(localFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localFile
            )

            val mediaLabel = if (data.mediaType == MediaType.ANIME) "Anime" else "Manga"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "${data.title} - $mediaLabel Rating Card")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Rating & status untuk \"${data.title}\" via CA'NIM — https://canim-lp.vercel.app"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan Rating $mediaLabel").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            Result.success(contentUri)
        } catch (e: Exception) {
            Result.failure(e)
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

    /**
     * Extracts a refined dominant/vibrant color from the bitmap for dark UI theme.
     * Takes < 1ms via 36x36 downsampling and HSV quantization.
     */
    private fun extractDominantColor(bitmap: Bitmap): Int {
        val scaled = Bitmap.createScaledBitmap(bitmap, 36, 36, false)
        val colorCounts = mutableMapOf<Int, Int>()
        var maxCount = 0
        var bestColor = 0xFF3B82F6.toInt()

        val hsv = FloatArray(3)
        for (y in 0 until scaled.height) {
            for (x in 0 until scaled.width) {
                val pixel = scaled.getPixel(x, y)
                val alpha = (pixel shr 24) and 0xff
                if (alpha < 128) continue

                Color.colorToHSV(pixel, hsv)
                val s = hsv[1]
                val v = hsv[2]

                // Discard near-black, near-white, or muted achromatic grays
                if (v < 0.18f || v > 0.92f || s < 0.15f) continue

                // Quantize hue into 15-degree steps
                val quantizedHue = (hsv[0] / 15f).toInt() * 15f
                val quantizedS = (s * 4).toInt() / 4f
                val quantizedV = (v * 4).toInt() / 4f
                val quantized = Color.HSVToColor(floatArrayOf(quantizedHue, quantizedS, quantizedV))

                val count = (colorCounts[quantized] ?: 0) + 1
                colorCounts[quantized] = count
                if (count > maxCount) {
                    maxCount = count
                    bestColor = quantized
                }
            }
        }
        if (scaled != bitmap) {
            scaled.recycle()
        }

        // Calibrate saturation and value for maximum clarity on Obsidian/Navy background
        Color.colorToHSV(bestColor, hsv)
        hsv[1] = hsv[1].coerceIn(0.55f, 0.85f)
        hsv[2] = hsv[2].coerceIn(0.70f, 0.95f)
        return Color.HSVToColor(hsv)
    }

    private fun renderRatingCardBitmap(
        data: MediaRatingExportData,
        coverBitmap: Bitmap?,
        dominantColor: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Base Midnight Obsidian Background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#0A0E17")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, CANVAS_WIDTH.toFloat(), CANVAS_HEIGHT.toFloat(), bgPaint)

        // 2. Ambient Glow from Dominant Color (Top-Right subtle atmosphere)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                CANVAS_WIDTH * 0.75f,
                CANVAS_HEIGHT * 0.25f,
                550f,
                Color.argb(22, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CANVAS_WIDTH.toFloat(), CANVAS_HEIGHT.toFloat(), glowPaint)

        // 3. Header: Single line "<nama> MAL PERSONAL RATING CARD by CA'NIM"
        val headerY = 72f
        val username = data.malUsername.ifBlank { "User" }
        val headerText = "$username MAL PERSONAL RATING CARD by CA'NIM"

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.03f
        }
        canvas.drawText(headerText, 56f, headerY, headerPaint)

        // Header Hairline Divider
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(56f, 98f, (CANVAS_WIDTH - 56).toFloat(), 98f, dividerPaint)

        // 4. Upper Hero Card (Poster + Titles + Studio + Genres)
        val heroRect = RectF(56f, 126f, (CANVAS_WIDTH - 56).toFloat(), 660f)
        val heroCardBg = Color.parseColor("#111827")
        val heroCardBorder = blendColors(Color.parseColor("#1E293B"), dominantColor, 0.25f)
        drawCard(canvas, heroRect, 22f, heroCardBg, heroCardBorder)

        // Poster Box (Inside Hero Card)
        val posterRect = RectF(82f, 152f, 412f, 634f)
        drawCenterCropBitmap(canvas, coverBitmap, posterRect, 16f)

        // Subtle border around poster
        val posterBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(posterRect, 16f, 16f, posterBorderPaint)

        // Right side info coordinates
        val rightX = 438f
        val rightWidth = heroRect.right - rightX - 26f
        var curY = 176f

        // Status Badge Pill (e.g. ● SEDANG DITONTON / ● SELESAI)
        val (statusLabel, statusColor) = resolveStatusInfo(data.status, data.mediaType)
        val statusText = "●  ${statusLabel.uppercase(Locale.getDefault())}"
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = statusColor
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.04f
        }
        val statusTextWidth = statusPaint.measureText(statusText)
        val badgeRect = RectF(rightX, curY - 20f, rightX + statusTextWidth + 28f, curY + 16f)
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor))
            style = Paint.Style.FILL
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor))
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRoundRect(badgeRect, 18f, 18f, badgeBgPaint)
        canvas.drawRoundRect(badgeRect, 18f, 18f, badgeBorderPaint)
        canvas.drawText(statusText, rightX + 14f, curY + 4f, statusPaint)

        curY += 46f

        // Full Long Title without synopsis (Wrapped with StaticLayout)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 31f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(data.title, 0, data.title.length, titlePaint, rightWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(data.title, titlePaint, rightWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
        }
        canvas.save()
        canvas.translate(rightX, curY)
        titleLayout.draw(canvas)
        canvas.restore()

        curY += titleLayout.height + 16f

        // Studio & Media Format Row
        val studioLabel = data.studio?.takeIf { it.isNotBlank() } ?: "-"
        val formatLabel = if (data.mediaType == MediaType.ANIME) "TV SERIES" else "MANGA"

        val metaTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.05f
        }
        val metaValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText("STUDIO", rightX, curY, metaTitlePaint)
        canvas.drawText("FORMAT", rightX + 220f, curY, metaTitlePaint)
        curY += 24f

        canvas.drawText(studioLabel.take(20), rightX, curY, metaValuePaint)
        canvas.drawText(formatLabel, rightX + 220f, curY, metaValuePaint)
        curY += 36f

        // Genre Chips
        if (data.genres.isNotEmpty()) {
            canvas.drawText("GENRES", rightX, curY, metaTitlePaint)
            curY += 20f

            var chipX = rightX
            val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                style = Paint.Style.FILL
            }
            val chipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            data.genres.take(4).forEach { genre ->
                val gText = genre.trim()
                val gWidth = chipPaint.measureText(gText)
                if (chipX + gWidth + 24f <= heroRect.right - 20f) {
                    val cRect = RectF(chipX, curY - 14f, chipX + gWidth + 22f, curY + 14f)
                    canvas.drawRoundRect(cRect, 10f, 10f, chipBgPaint)
                    canvas.drawRoundRect(cRect, 10f, 10f, chipBorderPaint)
                    canvas.drawText(gText, chipX + 11f, curY + 5f, chipPaint)
                    chipX += gWidth + 30f
                }
            }
        }

        // 5. Middle Bento Grid (Scores Card + Progress Card)
        val bentoCardBg = Color.parseColor("#111827")
        val bentoCardBorder = blendColors(Color.parseColor("#1E293B"), dominantColor, 0.20f)

        // Card A: Skor & Rating (Left Bento)
        val scoreCardRect = RectF(56f, 686f, 524f, 920f)
        drawCard(canvas, scoreCardRect, 20f, bentoCardBg, bentoCardBorder)

        val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
        }
        canvas.drawText("SKOR & RATING", 82f, 722f, cardTitlePaint)

        // MAL Score Sub-Column
        val malScoreStr = if (data.malScore != null && data.malScore > 0) {
            String.format(Locale.US, "%.2f", data.malScore)
        } else {
            "-"
        }
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val malScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("★", 82f, 786f, starPaint)
        canvas.drawText(malScoreStr, 116f, 788f, malScorePaint)

        val scoreSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("Skor Komunitas MAL", 82f, 822f, scoreSubPaint)

        // Personal Score Sub-Column (Highlighted with Dynamic Accent)
        val userScoreStr = if (data.userScore > 0) "${data.userScore} / 10" else "-"
        val trophyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dominantColor
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val userScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dominantColor
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("🏆", 312f, 786f, trophyPaint)
        canvas.drawText(userScoreStr, 350f, 788f, userScorePaint)
        canvas.drawText("Skor Pribadi Kamu", 312f, 822f, scoreSubPaint)

        // Card B: Progress Card (Right Bento)
        val progressCardRect = RectF(556f, 686f, (CANVAS_WIDTH - 56).toFloat(), 920f)
        drawCard(canvas, progressCardRect, 20f, bentoCardBg, bentoCardBorder)

        val progressTitle = if (data.mediaType == MediaType.ANIME) "PROGRESS EPISODE" else "PROGRESS CHAPTER"
        canvas.drawText(progressTitle, 582f, 722f, cardTitlePaint)

        val maxStr = if (data.maxProgress > 0) "${data.maxProgress}" else "?"
        val unitLabel = if (data.mediaType == MediaType.ANIME) "Episode" else "Chapter"
        val progressValueText = "${data.progress} / $maxStr $unitLabel"

        val progressValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(progressValueText, 582f, 786f, progressValuePaint)

        val progressRatio = if (data.maxProgress > 0) {
            (data.progress.toFloat() / data.maxProgress.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val percentText = "${(progressRatio * 100).toInt()}% Selesai"
        canvas.drawText(percentText, 582f, 822f, scoreSubPaint)

        // Horizontal Progress Bar
        val barRect = RectF(582f, 852f, progressCardRect.right - 26f, 866f)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(barRect, 7f, 7f, trackPaint)

        if (progressRatio > 0.01f) {
            val fillWidth = barRect.width() * progressRatio
            val fillRect = RectF(barRect.left, barRect.top, barRect.left + fillWidth, barRect.bottom)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dominantColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(fillRect, 7f, 7f, fillPaint)
        }

        // Card C: Full-Width Metadata Overview (Bottom Bento)
        val metaCardRect = RectF(56f, 946f, (CANVAS_WIDTH - 56).toFloat(), 1184f)
        drawCard(canvas, metaCardRect, 20f, bentoCardBg, bentoCardBorder)

        canvas.drawText("RINGKASAN & STATUS", 82f, 982f, cardTitlePaint)

        val cellWidth = metaCardRect.width() / 3f
        val cell1X = 82f
        val cell2X = 82f + cellWidth
        val cell3X = 82f + cellWidth * 2f

        val cellLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val cellValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Cell 1: Status Tontonan
        canvas.drawText("Status Tontonan", cell1X, 1032f, cellLabelPaint)
        canvas.drawText(statusLabel, cell1X, 1062f, cellValuePaint)
        canvas.drawText(if (data.mediaType == MediaType.ANIME) "Anime Library" else "Manga Library", cell1X, 1088f, scoreSubPaint)

        // Cell 2: Status Penayangan
        val airingStr = data.airingStatus?.ifBlank { "Finished Airing" } ?: "Finished Airing"
        canvas.drawText("Status Penayangan", cell2X, 1032f, cellLabelPaint)
        canvas.drawText(airingStr.take(18), cell2X, 1062f, cellValuePaint)
        canvas.drawText("${if (data.maxProgress > 0) data.maxProgress else "?"} Total $unitLabel", cell2X, 1088f, scoreSubPaint)

        // Cell 3: Sinkronisasi Akun MAL
        val malRefId = data.malId?.let { "MAL ID: #$it" } ?: "Tersinkronisasi"
        canvas.drawText("Akun Terhubung", cell3X, 1032f, cellLabelPaint)
        canvas.drawText(data.malUsername.ifBlank { "MyAnimeList" }.take(16), cell3X, 1062f, cellValuePaint)
        canvas.drawText(malRefId, cell3X, 1088f, scoreSubPaint)

        // 6. Footer Area (CA'NIM Branding)
        canvas.drawLine(56f, 1222f, (CANVAS_WIDTH - 56).toFloat(), 1222f, dividerPaint)

        val footerBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dominantColor
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.04f
        }
        canvas.drawText("CA'NIM", 56f, 1262f, footerBrandPaint)

        val footerSloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("dibaca cak nim! | Aplikasi Pelacak Animanga berbasis akun MAL", 56f, 1290f, footerSloganPaint)

        val footerUrlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dominantColor
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("canim-lp.vercel.app", (CANVAS_WIDTH - 56).toFloat(), 1276f, footerUrlPaint)

        return bitmap
    }

    private fun resolveStatusInfo(statusKey: String, type: MediaType): Pair<String, Int> {
        return when (statusKey.lowercase(Locale.getDefault())) {
            "watching" -> "Sedang Ditonton" to Color.parseColor("#3B82F6")
            "reading" -> "Sedang Dibaca" to Color.parseColor("#3B82F6")
            "completed" -> "Selesai" to Color.parseColor("#10B981")
            "on_hold", "onhold" -> "Ditunda" to Color.parseColor("#F59E0B")
            "dropped" -> "Ditinggalkan" to Color.parseColor("#EF4444")
            "plan_to_watch" -> "Rencana Nonton" to Color.parseColor("#8B5CF6")
            "plan_to_read" -> "Rencana Baca" to Color.parseColor("#8B5CF6")
            else -> (if (type == MediaType.ANIME) "Ditonton" else "Dibaca") to Color.parseColor("#3B82F6")
        }
    }

    private fun drawCard(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        fillColor: Int,
        borderColor: Int
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
    }

    private fun drawCenterCropBitmap(
        canvas: Canvas,
        bitmap: Bitmap?,
        rect: RectF,
        radius: Float
    ) {
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

    private fun blendColors(baseColor: Int, accentColor: Int, ratio: Float): Int {
        val clampedRatio = ratio.coerceIn(0f, 1f)
        val invRatio = 1f - clampedRatio
        val r = (Color.red(baseColor) * invRatio + Color.red(accentColor) * clampedRatio).toInt()
        val g = (Color.green(baseColor) * invRatio + Color.green(accentColor) * clampedRatio).toInt()
        val b = (Color.blue(baseColor) * invRatio + Color.blue(accentColor) * clampedRatio).toInt()
        return Color.rgb(r, g, b)
    }
}
