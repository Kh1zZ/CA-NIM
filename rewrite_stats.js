
const fs = require('fs');
const path = 'app/src/main/java/com/canim/app/ui/screens/StatsExporter.kt';
const content = fs.readFileSync(path, 'utf8');
const anchor = '    private suspend fun renderStatsBitmap(';
const index = content.indexOf(anchor);
const top = content.substring(0, index);
fs.writeFileSync(path, top + `    private suspend fun renderStatsBitmap(
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
            topAnime.take(5).map { item -> async { loadBitmap(context, item.imageUrl) } }
        }
        val mangaCoversDeferred = withContext(Dispatchers.IO) {
            topManga.take(5).map { item -> async { loadBitmap(context, item.imageUrl) } }
        }
        val animeBitmaps = animeCoversDeferred.awaitAll()
        val mangaBitmaps = mangaCoversDeferred.awaitAll()

        // Background Gradient - Ethereal Glass
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width, height,
                intArrayOf(Color.parseColor("#050505"), Color.parseColor("#0F172A"), Color.parseColor("#020617")),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // Subtle glowing orbs
        val glowPaint = Paint().apply {
            shader = RadialGradient(
                width * 0.8f, height * 0.2f, width * 0.5f,
                Color.argb(30, 56, 189, 248), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.8f, height * 0.2f, width * 0.5f, glowPaint)

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
        val margin = 24f
        var textX = rect.left + margin

        if (logoBitmap != null) {
            val logoHeight = rect.height() - (margin * 2)
            val logoWidth = logoBitmap.width * (logoHeight / logoBitmap.height)
            val logoRect = RectF(rect.left + margin, rect.top + margin, rect.left + margin + logoWidth, rect.bottom - margin)
            canvas.drawBitmap(logoBitmap, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
            textX = logoRect.right + 16f
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val textY = rect.centerY() - 4f
        val primaryText = "CA'NIM | "
        canvas.drawText(primaryText, textX, textY, textPaint)
        
        val primaryWidth = textPaint.measureText(primaryText)
        canvas.drawText("dibaca ca'nim!, aplikasi pelacak animanga berbasis MAL", textX + primaryWidth, textY, subPaint)
    }

    private fun drawGlobalFooter(canvas: Canvas, rect: RectF) {
        val textPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Dapatkan ca'nim sekarang di (link github | https://canim-lp.vercel.app/)", rect.centerX(), rect.centerY() + 8f, textPaint)
    }

    private fun drawBentoCard(canvas: Canvas, rect: RectF) {
        // Outer Shell
        val outerPaint = Paint().apply {
            color = Color.argb(10, 255, 255, 255) // bg-white/5
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.argb(25, 255, 255, 255) // border-white/10
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 32f, 32f, outerPaint)
        canvas.drawRoundRect(rect, 32f, 32f, borderPaint)
        
        // Inner Core
        val innerRect = RectF(rect.left + 4f, rect.top + 4f, rect.right - 4f, rect.bottom - 4f)
        val innerPaint = Paint().apply {
            color = Color.rgb(15, 23, 42) // CardBg
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(innerRect, 28f, 28f, innerPaint)
    }

    private fun drawCenterCropBitmap(canvas: Canvas, bitmap: Bitmap?, rect: RectF, radius: Float) {
        if (bitmap == null) return
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        
        val scale: Float
        var dx = 0f
        var dy = 0f
        if (bitmap.width * rect.height() > rect.width() * bitmap.height) {
            scale = rect.height() / bitmap.height.toFloat()
            dx = (rect.width() - bitmap.width * scale) * 0.5f
        } else {
            scale = rect.width() / bitmap.width.toFloat()
            dy = (rect.height() - bitmap.height * scale) * 0.5f
        }
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(rect.left + dx, rect.top + dy)
        
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        canvas.restore()
    }

    // --- VERTICAL STACK (9:16, 4:5, 3:4) ---
    private fun renderLayoutVertical(
        canvas: Canvas, width: Float, height: Float, stats: TrackerStats, malUser: MalUser,
        topAnime: List<UserMediaItem>, topManga: List<UserMediaItem>,
        animeBitmaps: List<Bitmap?>, mangaBitmaps: List<Bitmap?>, pieSlices: List<CanvasPieSlice>, logoBitmap: Bitmap?
    ) {
        val margin = 24f
        val gap = 16f
        val contentW = width - (margin * 2)

        val headerH = 100f
        val footerH = 80f
        val profileH = 220f
        val statsH = 260f
        val pieH = 320f
        
        // Dynamic media height based on remaining space
        val remainingH = height - (margin * 2) - headerH - footerH - profileH - statsH - pieH - (gap * 6)
        val mediaH = remainingH / 2f

        var curY = margin

        // Header
        val headerRect = RectF(margin, curY, margin + contentW, curY + headerH)
        drawGlobalHeader(canvas, headerRect, logoBitmap)
        curY += headerH + gap

        // Profile
        val profileRect = RectF(margin, curY, margin + contentW, curY + profileH)
        drawBentoCard(canvas, profileRect)
        drawProfileContent(canvas, profileRect, malUser)
        curY += profileH + gap

        // Stats
        val statsRect = RectF(margin, curY, margin + contentW, curY + statsH)
        drawBentoCard(canvas, statsRect)
        drawStatsContent(canvas, statsRect, stats)
        curY += statsH + gap

        // Pie
        val pieRect = RectF(margin, curY, margin + contentW, curY + pieH)
        drawBentoCard(canvas, pieRect)
        drawPieChartContent(canvas, pieRect, pieSlices)
        curY += pieH + gap

        // Top Anime
        val animeRect = RectF(margin, curY, margin + contentW, curY + mediaH)
        drawBentoCard(canvas, animeRect)
        drawMediaGridContent(canvas, animeRect, "TOP 5 ANIME", topAnime, animeBitmaps)
        curY += mediaH + gap

        // Top Manga
        val mangaRect = RectF(margin, curY, margin + contentW, curY + mediaH)
        drawBentoCard(canvas, mangaRect)
        drawMediaGridContent(canvas, mangaRect, "TOP 5 MANGA", topManga, mangaBitmaps)
        curY += mediaH + gap

        // Footer
        val footerRect = RectF(margin, curY, margin + contentW, curY + footerH)
        drawGlobalFooter(canvas, footerRect)
    }

    // --- SQUARE (1:1) ---
    private fun renderLayoutSquare(
        canvas: Canvas, width: Float, height: Float, stats: TrackerStats, malUser: MalUser,
        topAnime: List<UserMediaItem>, topManga: List<UserMediaItem>,
        animeBitmaps: List<Bitmap?>, mangaBitmaps: List<Bitmap?>, pieSlices: List<CanvasPieSlice>, logoBitmap: Bitmap?
    ) {
        val margin = 24f
        val gap = 16f
        val contentW = width - (margin * 2)
        val contentH = height - (margin * 2)

        val headerH = 100f
        val footerH = 60f
        
        val bodyH = contentH - headerH - footerH - (gap * 2)
        val row1H = bodyH * 0.45f
        val row2H = bodyH * 0.55f
        val col1W = (contentW - gap) / 2f
        val col2W = col1W

        // Header
        var curY = margin
        val headerRect = RectF(margin, curY, margin + contentW, curY + headerH)
        drawGlobalHeader(canvas, headerRect, logoBitmap)
        curY += headerH + gap

        // Row 1 Left (Profile + Stats)
        val r1LeftRect = RectF(margin, curY, margin + col1W, curY + row1H)
        drawBentoCard(canvas, r1LeftRect)
        // Split inner rect for profile and stats
        val pRect = RectF(r1LeftRect.left, r1LeftRect.top, r1LeftRect.right, r1LeftRect.top + (row1H * 0.4f))
        val sRect = RectF(r1LeftRect.left, r1LeftRect.top + (row1H * 0.4f), r1LeftRect.right, r1LeftRect.bottom)
        drawProfileContent(canvas, pRect, malUser)
        drawStatsContent(canvas, sRect, stats)

        // Row 1 Right (Pie)
        val r1RightRect = RectF(margin + col1W + gap, curY, margin + contentW, curY + row1H)
        drawBentoCard(canvas, r1RightRect)
        drawPieChartContent(canvas, r1RightRect, pieSlices)
        curY += row1H + gap

        // Row 2 Left (Anime)
        val r2LeftRect = RectF(margin, curY, margin + col1W, curY + row2H)
        drawBentoCard(canvas, r2LeftRect)
        drawMediaGridContent(canvas, r2LeftRect, "TOP 5 ANIME", topAnime, animeBitmaps)

        // Row 2 Right (Manga)
        val r2RightRect = RectF(margin + col1W + gap, curY, margin + contentW, curY + row2H)
        drawBentoCard(canvas, r2RightRect)
        drawMediaGridContent(canvas, r2RightRect, "TOP 5 MANGA", topManga, mangaBitmaps)
        curY += row2H + gap

        // Footer
        val footerRect = RectF(margin, curY, margin + contentW, curY + footerH)
        drawGlobalFooter(canvas, footerRect)
    }

    // --- LANDSCAPE (16:9) ---
    private fun renderLayoutLandscape(
        canvas: Canvas, width: Float, height: Float, stats: TrackerStats, malUser: MalUser,
        topAnime: List<UserMediaItem>, topManga: List<UserMediaItem>,
        animeBitmaps: List<Bitmap?>, mangaBitmaps: List<Bitmap?>, pieSlices: List<CanvasPieSlice>, logoBitmap: Bitmap?
    ) {
        val margin = 24f
        val gap = 16f
        val contentW = width - (margin * 2)
        val contentH = height - (margin * 2)

        val headerH = 100f
        val footerH = 60f
        
        val bodyH = contentH - headerH - footerH - (gap * 2)
        
        val col1W = contentW * 0.35f
        val col2W = contentW * 0.65f - gap

        // Header
        var curY = margin
        val headerRect = RectF(margin, curY, margin + contentW, curY + headerH)
        drawGlobalHeader(canvas, headerRect, logoBitmap)
        curY += headerH + gap

        val bodyY = curY

        // Col 1 (Profile, Stats, Pie)
        val pHeight = bodyH * 0.25f
        val sHeight = bodyH * 0.3f
        val pieHeight = bodyH - pHeight - sHeight - (gap * 2)
        
        val pRect = RectF(margin, bodyY, margin + col1W, bodyY + pHeight)
        drawBentoCard(canvas, pRect)
        drawProfileContent(canvas, pRect, malUser)

        val sRect = RectF(margin, pRect.bottom + gap, margin + col1W, pRect.bottom + gap + sHeight)
        drawBentoCard(canvas, sRect)
        drawStatsContent(canvas, sRect, stats)

        val pieRect = RectF(margin, sRect.bottom + gap, margin + col1W, sRect.bottom + gap + pieHeight)
        drawBentoCard(canvas, pieRect)
        drawPieChartContent(canvas, pieRect, pieSlices)

        // Col 2 (Anime, Manga horizontally split)
        val mediaW = (col2W - gap) / 2f
        val animeRect = RectF(margin + col1W + gap, bodyY, margin + col1W + gap + mediaW, bodyY + bodyH)
        drawBentoCard(canvas, animeRect)
        drawMediaGridContent(canvas, animeRect, "TOP 5 ANIME", topAnime, animeBitmaps)

        val mangaRect = RectF(animeRect.right + gap, bodyY, margin + contentW, bodyY + bodyH)
        drawBentoCard(canvas, mangaRect)
        drawMediaGridContent(canvas, mangaRect, "TOP 5 MANGA", topManga, mangaBitmaps)

        // Footer
        val footerRect = RectF(margin, bodyY + bodyH + gap, margin + contentW, height - margin)
        drawGlobalFooter(canvas, footerRect)
    }

    // --- CONTENT DRAWERS ---
    private fun drawProfileContent(canvas: Canvas, rect: RectF, malUser: MalUser) {
        val titlePaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val userPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = 22f
            isAntiAlias = true
        }
        
        val padX = 32f
        var currentY = rect.top + 40f
        canvas.drawText("PROFIL MYANIMELIST", rect.left + padX, currentY, titlePaint)
        
        currentY += 50f
        canvas.drawText("@\${malUser.username.ifBlank { "Tamu" }}", rect.left + padX, currentY, userPaint)
        
        currentY += 40f
        val loc = malUser.location?.takeIf { it.isNotBlank() } ?: "Tidak ada lokasi"
        canvas.drawText("📍 \$loc", rect.left + padX, currentY, subPaint)
    }

    private fun drawStatsContent(canvas: Canvas, rect: RectF, stats: TrackerStats) {
        val titlePaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("RINGKASAN KOLEKSI", rect.left + 32f, rect.top + 40f, titlePaint)

        val labelPaint = Paint().apply { color = Color.rgb(156, 163, 175); textSize = 20f; isAntiAlias = true }
        val valPaint = Paint().apply { color = Color.WHITE; textSize = 32f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }

        val startX = rect.left + 32f
        val col2X = rect.left + (rect.width() / 2f)
        var startY = rect.top + 90f
        val rowH = 75f

        fun drawStat(x: Float, y: Float, label: String, value: String) {
            canvas.drawText(label, x, y, labelPaint)
            canvas.drawText(value, x, y + 36f, valPaint)
        }

        drawStat(startX, startY, "Total Anime", "\${stats.totalAnime}")
        drawStat(col2X, startY, "Total Manga", "\${stats.totalManga}")
        startY += rowH + 20f
        drawStat(startX, startY, "Hari Tonton", "\${stats.daysWatched}")
        drawStat(col2X, startY, "Bab Dibaca", "\${stats.chaptersRead}")
    }

    private fun drawPieChartContent(canvas: Canvas, rect: RectF, slices: List<CanvasPieSlice>) {
        val titlePaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("DISTRIBUSI STATUS", rect.left + 32f, rect.top + 40f, titlePaint)

        if (slices.isEmpty()) return
        
        val total = slices.sumOf { it.count }.toFloat()
        var currentAngle = -90f
        
        val radius = minOf(rect.width(), rect.height() - 60f) * 0.35f
        // Offset center slightly to the left if width allows it
        val cx = rect.left + (rect.width() * 0.35f)
        val cy = rect.top + 50f + (rect.height() - 50f) / 2f
        
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val piePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        for (slice in slices) {
            val sweepAngle = (slice.count / total) * 360f
            piePaint.color = slice.color
            canvas.drawArc(oval, currentAngle, sweepAngle, true, piePaint)
            currentAngle += sweepAngle
        }

        // Legend
        val legendX = cx + radius + 40f
        var legendY = cy - radius + 20f
        val legendTitlePaint = Paint().apply { color = Color.WHITE; textSize = 22f; isAntiAlias = true }
        val legendPaint = Paint().apply { color = Color.rgb(200, 200, 200); textSize = 20f; isAntiAlias = true }
        
        for (slice in slices) {
            piePaint.color = slice.color
            canvas.drawCircle(legendX, legendY - 6f, 10f, piePaint)
            canvas.drawText("\${slice.label}: \${slice.count}", legendX + 25f, legendY, legendPaint)
            legendY += 35f
        }
    }

    private fun drawMediaGridContent(
        canvas: Canvas, rect: RectF, title: String, 
        items: List<UserMediaItem>, bitmaps: List<Bitmap?>
    ) {
        val titlePaint = Paint().apply {
            color = Color.rgb(56, 189, 248)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(title, rect.left + 32f, rect.top + 40f, titlePaint)

        if (items.isEmpty()) return

        val padX = 24f
        val gap = 16f
        val startY = rect.top + 60f
        val startX = rect.left + padX
        val contentW = rect.width() - (padX * 2)
        val contentH = rect.height() - 60f - padX

        // Auto-arrange in columns or grid depending on width vs height
        val isHorizontalFlow = contentW > contentH * 1.5f

        if (isHorizontalFlow) {
            // Layout horizontally (1 row, 5 cols)
            val cardW = (contentW - gap * 4) / 5f
            for (i in items.indices) {
                if (i >= 5) break
                val item = items[i]
                val bmp = bitmaps.getOrNull(i)
                val cardX = startX + (cardW + gap) * i
                val cardRect = RectF(cardX, startY, cardX + cardW, startY + contentH)
                drawCenterCropBitmap(canvas, bmp, cardRect, 16f)
                
                // Add Rank Badge
                drawRankBadge(canvas, cardRect, i + 1)
            }
        } else {
            // Layout as a vertical list (5 rows, 1 col)
            val cardH = (contentH - gap * 4) / 5f
            for (i in items.indices) {
                if (i >= 5) break
                val bmp = bitmaps.getOrNull(i)
                val cardY = startY + (cardH + gap) * i
                
                // For vertical list, we split image and text
                val imgW = cardH * 0.7f // Portrait ratio for image
                val imgRect = RectF(startX, cardY, startX + imgW, cardY + cardH)
                drawCenterCropBitmap(canvas, bmp, imgRect, 12f)
                
                drawRankBadge(canvas, imgRect, i + 1)
                
                // Text
                val textX = imgRect.right + 16f
                val titleMainPaint = Paint().apply { color = Color.WHITE; textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
                val scorePaint = Paint().apply { color = Color.rgb(250, 204, 21); textSize = 20f; isAntiAlias = true }
                
                // Truncate title
                var shortTitle = items[i].title
                if (shortTitle.length > 25) shortTitle = shortTitle.take(23) + "..."
                
                canvas.drawText(shortTitle, textX, cardY + cardH * 0.45f, titleMainPaint)
                canvas.drawText("⭐ \${items[i].score}", textX, cardY + cardH * 0.8f, scorePaint)
            }
        }
    }

    private fun drawRankBadge(canvas: Canvas, rect: RectF, rank: Int) {
        val badgeRadius = 24f
        val badgeX = rect.left + badgeRadius + 8f
        val badgeY = rect.top + badgeRadius + 8f
        
        val badgePaint = Paint().apply {
            color = Color.argb(220, 15, 23, 42)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        canvas.drawCircle(badgeX, badgeY, badgeRadius, badgePaint)
        canvas.drawText("#\$rank", badgeX, badgeY + 8f, textPaint)
    }
}
`);
console.log('Successfully rewrote StatsExporter.kt');
