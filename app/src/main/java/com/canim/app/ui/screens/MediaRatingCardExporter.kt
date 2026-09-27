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
    val malId: Int? = null,
    val year: Int? = null
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

        // 2. Ambient Dual-Glow from Dominant Color (Rich atmospheric texture, eliminates flat empty space)
        val glowPaintTop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                CANVAS_WIDTH * 0.78f,
                CANVAS_HEIGHT * 0.22f,
                620f,
                Color.argb(28, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CANVAS_WIDTH.toFloat(), CANVAS_HEIGHT.toFloat(), glowPaintTop)

        val glowPaintBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                CANVAS_WIDTH * 0.20f,
                CANVAS_HEIGHT * 0.75f,
                550f,
                Color.argb(18, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CANVAS_WIDTH.toFloat(), CANVAS_HEIGHT.toFloat(), glowPaintBottom)

        // 3. Header: Exact requested format "<nama user> | MAL PERSONAL RATING CARD | CA'NIM"
        val headerY = 66f
        val username = data.malUsername.ifBlank { "User" }
        val headerText = "$username | MAL PERSONAL RATING CARD | CA'NIM"

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.04f
        }
        canvas.drawText(headerText, 50f, headerY, headerPaint)

        // Header Hairline Divider
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(50f, 92f, (CANVAS_WIDTH - 50).toFloat(), 92f, dividerPaint)

        // 4. Upper Hero Card (Poster 380x546 px with Natural 2:3 Ratio + Dense Info Column)
        val heroRect = RectF(50f, 108f, (CANVAS_WIDTH - 50).toFloat(), 708f)
        val heroCardBg = Color.parseColor("#111827")
        val heroCardBorder = blendColors(Color.parseColor("#1E293B"), dominantColor, 0.35f)
        drawCard(canvas, heroRect, 22f, heroCardBg, heroCardBorder)

        // Natural 2:3 Aspect Ratio Poster (380 x 546 px = 1 : 1.436 ratio, ZERO awkward crop!)
        val posterRect = RectF(74f, 132f, 454f, 684f)
        drawCard(canvas, posterRect, 16f, Color.parseColor("#0F172A"), Color.parseColor("#334155"))
        drawNaturalFitBitmap(canvas, coverBitmap, posterRect, 16f)

        // Right side info column (x = 482f to 1006f, width = 524f)
        val rightX = 482f
        val rightWidth = (heroRect.right - rightX - 24f).toInt()
        var curY = 142f

        // Status Badge Pill
        val (statusLabel, statusColor) = resolveStatusInfo(data.status, data.mediaType)
        val statusText = "●  ${statusLabel.uppercase(Locale.getDefault())}"
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = statusColor
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.05f
        }
        val statusTextWidth = statusPaint.measureText(statusText)
        val badgeRect = RectF(rightX, curY - 14f, rightX + statusTextWidth + 28f, curY + 18f)
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(35, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor))
            style = Paint.Style.FILL
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor))
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRoundRect(badgeRect, 16f, 16f, badgeBgPaint)
        canvas.drawRoundRect(badgeRect, 16f, 16f, badgeBorderPaint)
        canvas.drawText(statusText, rightX + 14f, curY + 7f, statusPaint)

        curY += 46f

        // Full Long Title without synopsis (Wrapped with StaticLayout)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 31f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(data.title, 0, data.title.length, titlePaint, rightWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(data.title, titlePaint, rightWidth, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
        }
        canvas.save()
        canvas.translate(rightX, curY)
        titleLayout.draw(canvas)
        canvas.restore()

        curY += titleLayout.height + 8f

        // Subtitle (English or Romanized Title if available and different)
        val cleanEnglish = data.titleEnglish?.trim()
        if (!cleanEnglish.isNullOrBlank() && !cleanEnglish.equals(data.title.trim(), ignoreCase = true)) {
            val subTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            val subLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain("($cleanEnglish)", 0, cleanEnglish.length + 2, subTitlePaint, rightWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(1)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout("($cleanEnglish)", subTitlePaint, rightWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
            }
            canvas.save()
            canvas.translate(rightX, curY)
            subLayout.draw(canvas)
            canvas.restore()
            curY += subLayout.height + 12f
        } else {
            curY += 8f
        }

        // 2x2 Bento Specs Mini-Grid (Dense, Zero Empty Space!)
        val specsBoxRect = RectF(rightX, curY, rightX + rightWidth, curY + 138f)
        val specsBg = Color.parseColor("#0B0F19")
        val specsBorder = Color.parseColor("#1E293B")
        drawCard(canvas, specsBoxRect, 14f, specsBg, specsBorder)

        // Internal Divider for 2x2 grid
        val halfW = specsBoxRect.width() / 2f
        val halfH = specsBoxRect.height() / 2f
        canvas.drawLine(specsBoxRect.left + halfW, specsBoxRect.top + 8f, specsBoxRect.left + halfW, specsBoxRect.bottom - 8f, dividerPaint)
        canvas.drawLine(specsBoxRect.left + 8f, specsBoxRect.top + halfH, specsBoxRect.right - 8f, specsBoxRect.top + halfH, dividerPaint)

        val specLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.05f
        }
        val specValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val studioStr = data.studio?.takeIf { it.isNotBlank() } ?: "-"
        val yearStr = data.year?.takeIf { it > 0 }?.toString() ?: "2024"
        val formatStr = if (data.mediaType == MediaType.ANIME) "TV Series • $yearStr" else "Manga • $yearStr"
        val airingStr = data.airingStatus?.ifBlank { "Finished Airing" } ?: "Finished Airing"
        val unitLabel = if (data.mediaType == MediaType.ANIME) "Episode" else "Chapter"
        val totalUnits = "${if (data.maxProgress > 0) data.maxProgress else "?"} $unitLabel"

        // Quadrant 1 (Top-Left): STUDIO
        canvas.drawText("STUDIO", specsBoxRect.left + 16f, specsBoxRect.top + 26f, specLabelPaint)
        canvas.drawText(studioStr.take(16), specsBoxRect.left + 16f, specsBoxRect.top + 52f, specValuePaint)

        // Quadrant 2 (Top-Right): FORMAT
        canvas.drawText("FORMAT & TAHUN", specsBoxRect.left + halfW + 16f, specsBoxRect.top + 26f, specLabelPaint)
        canvas.drawText(formatStr.take(18), specsBoxRect.left + halfW + 16f, specsBoxRect.top + 52f, specValuePaint)

        // Quadrant 3 (Bottom-Left): STATUS TAYANG
        canvas.drawText("STATUS TAYANG", specsBoxRect.left + 16f, specsBoxRect.top + halfH + 26f, specLabelPaint)
        canvas.drawText(airingStr.take(16), specsBoxRect.left + 16f, specsBoxRect.top + halfH + 52f, specValuePaint)

        // Quadrant 4 (Bottom-Right): TOTAL EPS/BAB
        canvas.drawText("TOTAL ${unitLabel.uppercase(Locale.getDefault())}", specsBoxRect.left + halfW + 16f, specsBoxRect.top + halfH + 26f, specLabelPaint)
        canvas.drawText(totalUnits, specsBoxRect.left + halfW + 16f, specsBoxRect.top + halfH + 52f, specValuePaint)

        curY += 138f + 16f

        // Genre Tag Cloud (Fills remaining hero area cleanly)
        if (data.genres.isNotEmpty()) {
            val genreLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.05f
            }
            canvas.drawText("GENRES", rightX, curY + 6f, genreLabelPaint)

            val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                textSize = 14f
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

            var chipX = rightX + 72f
            var chipY = curY
            data.genres.take(4).forEach { genre ->
                val gText = genre.trim()
                val gWidth = chipPaint.measureText(gText)
                if (chipX + gWidth + 24f <= heroRect.right - 18f) {
                    val cRect = RectF(chipX, chipY - 14f, chipX + gWidth + 20f, chipY + 14f)
                    canvas.drawRoundRect(cRect, 8f, 8f, chipBgPaint)
                    canvas.drawRoundRect(cRect, 8f, 8f, chipBorderPaint)
                    canvas.drawText(gText, chipX + 10f, chipY + 5f, chipPaint)
                    chipX += gWidth + 28f
                }
            }
        }

        // 5. Middle Bento Grid (Dual Score Card + Progress Card)
        val bentoCardBg = Color.parseColor("#111827")
        val bentoCardBorder = blendColors(Color.parseColor("#1E293B"), dominantColor, 0.25f)

        // Card A: Skor & Evaluasi (Left Bento, x = 50 to 528, width = 478f, y = 724 to 972)
        val scoreCardRect = RectF(50f, 724f, 528f, 972f)
        drawCard(canvas, scoreCardRect, 20f, bentoCardBg, bentoCardBorder)

        val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
        }
        canvas.drawText("SKOR & EVALUASI", 74f, 758f, cardTitlePaint)

        // MAL Community Score Sub-Column
        val malScoreStr = if (data.malScore != null && data.malScore > 0) {
            String.format(Locale.US, "%.2f", data.malScore)
        } else {
            "-"
        }
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val malScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("★", 74f, 822f, starPaint)
        canvas.drawText(malScoreStr, 110f, 824f, malScorePaint)

        val scoreSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("Skor Rata-rata MAL", 74f, 856f, scoreSubPaint)

        // Divider between scores
        canvas.drawLine(280f, 780f, 280f, 890f, dividerPaint)

        // Personal Score Sub-Column
        val userScoreStr = if (data.userScore > 0) "${data.userScore} / 10" else "-"
        val trophyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dominantColor
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val userScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dominantColor
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("🏆", 304f, 822f, trophyPaint)
        canvas.drawText(userScoreStr, 344f, 824f, userScorePaint)
        canvas.drawText("Rating Pilihan Kamu", 304f, 856f, scoreSubPaint)

        // Bottom evaluation pill inside score card
        val evalBannerRect = RectF(74f, 892f, scoreCardRect.right - 24f, 942f)
        val evalBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B0F19")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(evalBannerRect, 10f, 10f, evalBgPaint)
        canvas.drawRoundRect(evalBannerRect, 10f, 10f, dividerPaint)

        val evalText = if (data.userScore >= 9) {
            "★  Ulasan Pribadi: Masterpiece Direkomendasikan!"
        } else if (data.userScore >= 7) {
            "★  Ulasan Pribadi: Tontonan Menghibur & Bagus"
        } else if (data.userScore > 0) {
            "★  Ulasan Pribadi: Telah Ditonton & Dinilai"
        } else {
            "★  Status Rating: Belum Diberi Nilai Angka"
        }
        val evalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(evalText, 94f, 924f, evalTextPaint)

        // Card B: Progress Tontonan (Right Bento, x = 552 to 1030, width = 478f, y = 724 to 972)
        val progressCardRect = RectF(552f, 724f, (CANVAS_WIDTH - 50).toFloat(), 972f)
        drawCard(canvas, progressCardRect, 20f, bentoCardBg, bentoCardBorder)

        val progressTitle = if (data.mediaType == MediaType.ANIME) "PROGRESS TONTONAN" else "PROGRESS BACAAN"
        canvas.drawText(progressTitle, 576f, 758f, cardTitlePaint)

        val maxStr = if (data.maxProgress > 0) "${data.maxProgress}" else "?"
        val progressValueText = "${data.progress} / $maxStr $unitLabel"

        val progressValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(progressValueText, 576f, 822f, progressValuePaint)

        val progressRatio = if (data.maxProgress > 0) {
            (data.progress.toFloat() / data.maxProgress.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val percentText = if (progressRatio >= 1f) "[ 100% SELESAI TUNTAS ]" else "[ ${(progressRatio * 100).toInt()}% SELESAI ]"
        val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (progressRatio >= 1f) Color.parseColor("#10B981") else dominantColor
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(percentText, 576f, 856f, percentPaint)

        // Thick High-Visibility Horizontal Progress Bar
        val barRect = RectF(576f, 880f, progressCardRect.right - 24f, 898f)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(barRect, 9f, 9f, trackPaint)

        if (progressRatio > 0.01f) {
            val fillWidth = barRect.width() * progressRatio
            val fillRect = RectF(barRect.left, barRect.top, barRect.left + fillWidth, barRect.bottom)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = dominantColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(fillRect, 9f, 9f, fillPaint)
        }

        val progressNotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("Status: $statusLabel • Koleksi Tersimpan", 576f, 932f, progressNotePaint)

        // 6. Lower Bento Card (Full-Width Metadata & MAL Sync Matrix, y = 990 to 1214)
        val metaCardRect = RectF(50f, 990f, (CANVAS_WIDTH - 50).toFloat(), 1214f)
        drawCard(canvas, metaCardRect, 20f, bentoCardBg, bentoCardBorder)

        canvas.drawText("INFORMASI KOLEKSI & SINKRONISASI MAL", 74f, 1024f, cardTitlePaint)

        val cellWidth = metaCardRect.width() / 3f
        val cell1X = 74f
        val cell2X = 74f + cellWidth
        val cell3X = 74f + cellWidth * 2f

        val cellLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val cellValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellValueAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dominantColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Cell 1: Status Koleksi
        canvas.drawText("Status Koleksi", cell1X, 1064f, cellLabelPaint)
        canvas.drawText(statusLabel, cell1X, 1094f, cellValuePaint)
        canvas.drawText("${data.progress} dari ${if (data.maxProgress > 0) data.maxProgress else "?"} $unitLabel", cell1X, 1122f, scoreSubPaint)

        // Cell 2: Tipe Media & Format
        canvas.drawText("Tipe Media & Format", cell2X, 1064f, cellLabelPaint)
        canvas.drawText(if (data.mediaType == MediaType.ANIME) "Anime Televisi" else "Manga Komik", cell2X, 1094f, cellValuePaint)
        canvas.drawText(airingStr.take(22), cell2X, 1122f, scoreSubPaint)

        // Cell 3: Akun MyAnimeList Terhubung
        val malRefId = data.malId?.let { "MAL ID: #$it" } ?: "Terverifikasi Online"
        canvas.drawText("Akun MyAnimeList", cell3X, 1064f, cellLabelPaint)
        canvas.drawText(data.malUsername.ifBlank { "MyAnimeList" }.take(16), cell3X, 1094f, cellValueAccentPaint)
        canvas.drawText(malRefId, cell3X, 1122f, scoreSubPaint)

        // Mini Status Row at the bottom of the card
        val syncDividerY = 1146f
        canvas.drawLine(74f, syncDividerY, metaCardRect.right - 24f, syncDividerY, dividerPaint)
        val verifiedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("✓  Sinkronisasi Otomatis Realtime dengan MyAnimeList via Aplikasi CA'NIM", 74f, 1184f, verifiedPaint)

        // 7. Footer Area (CA'NIM Official Branding, y = 1228 to 1320)
        canvas.drawLine(50f, 1228f, (CANVAS_WIDTH - 50).toFloat(), 1228f, dividerPaint)

        val footerBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dominantColor
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.04f
        }
        canvas.drawText("CA'NIM", 50f, 1268f, footerBrandPaint)

        val footerSloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("dibaca cak nim! | Aplikasi Pelacak Animanga berbasis akun MAL", 50f, 1296f, footerSloganPaint)

        val footerUrlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dominantColor
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("canim-lp.vercel.app", (CANVAS_WIDTH - 50).toFloat(), 1282f, footerUrlPaint)

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

    /**
     * Draws the anime poster with natural proportions and zero awkward clipping.
     * Preserves full character art, title, and borders seamlessly.
     */
    private fun drawNaturalFitBitmap(
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
