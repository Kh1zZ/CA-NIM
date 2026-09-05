package com.canim.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.UserMediaItem
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.CanimUiState
import com.canim.app.util.AnimeFranchiseFilter
import kotlinx.coroutines.launch

object StatsColors {
    val Completed = Color(0xFF10B981)
    val Watching = AccentBlue
    val Reading = MangaAccentDarkBlue
    val OnHold = Color(0xFFF59E0B)
    val Dropped = Color(0xFFEF4444)
    val PlanTo = Color(0xFFA855F7)
}

data class PieSlice(val label: String, val count: Int, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: CanimUiState,
    onBack: () -> Unit,
    onSelectItem: (Any, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    // Calculate Top 5 by personal score (excluding sequel anime)
    val topAnime = remember(state.animeList) {
        AnimeFranchiseFilter.selectTopAnimeNonSequel(state.animeList, 5)
    }

    val topManga = remember(state.mangaList) {
        state.mangaList
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(5)
    }

    // Pie chart data with unified StatsColors
    val animeSlices = remember(state.stats) {
        listOf(
            PieSlice("Ditonton", state.stats.animeWatching, StatsColors.Watching),
            PieSlice("Selesai", state.stats.animeCompleted, StatsColors.Completed),
            PieSlice("Ditunda", state.stats.animeOnHold, StatsColors.OnHold),
            PieSlice("Drop", state.stats.animeDropped, StatsColors.Dropped),
            PieSlice("Rencana", state.stats.animePlanToWatch, StatsColors.PlanTo)
        ).filter { it.count > 0 }
    }

    val mangaSlices = remember(state.stats) {
        listOf(
            PieSlice("Dibaca", state.stats.mangaReading, StatsColors.Reading),
            PieSlice("Selesai", state.stats.mangaCompleted, StatsColors.Completed),
            PieSlice("Ditunda", state.stats.mangaOnHold, StatsColors.OnHold),
            PieSlice("Drop", state.stats.mangaDropped, StatsColors.Dropped),
            PieSlice("Rencana", state.stats.mangaPlanToRead, StatsColors.PlanTo)
        ).filter { it.count > 0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Statistik & Ringkasan",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showExportDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AccentBlue.copy(alpha = 0.2f),
                            contentColor = AccentBlue
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ekspor", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBg)
            )
        },
        containerColor = BlackBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card (Without Birthday for privacy)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!state.malUser.pictureUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = state.malUser.pictureUrl,
                                contentDescription = "MAL Avatar",
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, AccentBlue, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2E51A2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.malUser.username.take(1).uppercase().ifBlank { "U" },
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.malUser.username.ifBlank { "Tamu (Mode Offline)" },
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (state.malUser.isLoggedIn) AccentGreen else TextMuted)
                                )
                                Text(
                                    text = if (state.malUser.isLoggedIn) "Tersinkronisasi MyAnimeList" else "Mode Tamu / Offline",
                                    color = if (state.malUser.isLoggedIn) AccentGreen else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Big Metrics Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "METRIK UTAMA",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BigMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Waktu Tonton",
                            value = "${state.stats.daysWatched}",
                            unit = "Hari",
                            subtitle = "${(state.stats.episodesWatched * 24) / 60} Jam Total",
                            icon = Icons.Default.Schedule,
                            accentColor = AccentBlue
                        )
                        BigMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Bab Dibaca",
                            value = "${state.stats.chaptersRead}",
                            unit = "Bab",
                            subtitle = "${state.stats.volumesRead} Volume",
                            icon = Icons.Default.MenuBook,
                            accentColor = MangaAccentDarkBlue
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BigMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Total Judul",
                            value = "${state.stats.totalAnime + state.stats.totalManga}",
                            unit = "Judul",
                            subtitle = "${state.stats.totalAnime} Anime • ${state.stats.totalManga} Manga",
                            icon = Icons.Default.LibraryBooks,
                            accentColor = Color(0xFFA855F7)
                        )
                        BigMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Rata-Rata Skor",
                            value = if (state.stats.meanScore > 0) "★ ${state.stats.meanScore}" else "-",
                            unit = "",
                            subtitle = "${state.stats.completedCount} Judul Tamat",
                            icon = Icons.Default.Star,
                            accentColor = StarGold
                        )
                    }
                }
            }

            // Pie Chart 1: Anime Distribution
            item {
                StatusPieChartCard(
                    title = "Distribusi Status Anime",
                    totalItems = state.stats.totalAnime,
                    slices = animeSlices,
                    icon = Icons.Default.Tv,
                    accentColor = AccentBlue
                )
            }

            // Pie Chart 2: Manga Distribution
            item {
                StatusPieChartCard(
                    title = "Distribusi Status Manga",
                    totalItems = state.stats.totalManga,
                    slices = mangaSlices,
                    icon = Icons.Default.AutoStories,
                    accentColor = MangaAccentDarkBlue
                )
            }

            // Top 5 Anime Section
            item {
                Text(
                    text = "TOP 5 ANIME PRIBADI",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (topAnime.isEmpty()) {
                item {
                    EmptyTopScoreCard("Belum ada anime yang diberi rating skor personal.")
                }
            } else {
                itemsIndexed(topAnime) { index, anime ->
                    TopRankItemCard(
                        rank = index + 1,
                        item = anime,
                        isAnime = true,
                        onClick = { onSelectItem(anime, MediaType.ANIME) }
                    )
                }
            }

            // Top 5 Manga Section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "TOP 5 MANGA PRIBADI",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (topManga.isEmpty()) {
                item {
                    EmptyTopScoreCard("Belum ada manga yang diberi rating skor personal.")
                }
            } else {
                itemsIndexed(topManga) { index, manga ->
                    TopRankItemCard(
                        rank = index + 1,
                        item = manga,
                        isAnime = false,
                        onClick = { onSelectItem(manga, MediaType.MANGA) }
                    )
                }
            }
        }
    }

    // Export Dialog
    if (showExportDialog) {
        var selectedRatio by remember { mutableStateOf(ExportAspectRatio.STORY_9_16) }

        AlertDialog(
            onDismissRequest = { if (!isExporting) showExportDialog = false },
            title = {
                Text(text = "Ekspor Statistik", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Pilih Rasio Kanvas:",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Aspect Ratio Selector Chips (9:16 Story, 4:5, 3:4, 1:1, 16:9 Landscape)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExportAspectRatio.entries.forEach { ratio ->
                            val isSelected = ratio == selectedRatio
                            Surface(
                                selected = isSelected,
                                onClick = { if (!isExporting) selectedRatio = ratio },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AccentBlue else CardBg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentBlue else CardBorder
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ratio.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Pilih Format (${selectedRatio.label} - ${selectedRatio.width}x${selectedRatio.height}):",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    StatsExportFormat.entries.forEach { fmt ->
                        Button(
                            onClick = {
                                isExporting = true
                                coroutineScope.launch {
                                    val result = StatsExporter.exportAndShareStats(
                                        context = context,
                                        stats = state.stats,
                                        malUser = state.malUser,
                                        topAnime = topAnime,
                                        topManga = topManga,
                                        format = fmt,
                                        aspectRatio = selectedRatio
                                    )
                                    isExporting = false
                                    showExportDialog = false
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Statistik siap dibagikan (${fmt.extension.uppercase()} - ${selectedRatio.label})", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Gagal mengekspor: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (fmt == StatsExportFormat.PDF) Color(0xFFDC2626) else AccentBlue
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isExporting
                        ) {
                            Text(text = fmt.label, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isExporting) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentBlue)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Merender grafik statistik...", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    enabled = !isExporting
                ) {
                    Text("Tutup", color = TextMuted)
                }
            },
            containerColor = CardElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun BigMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Card(
        modifier = modifier.border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            Text(
                text = subtitle,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusPieChartCard(
    title: String,
    totalItems: Int,
    slices: List<PieSlice>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$totalItems Judul",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (totalItems <= 0 || slices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Belum ada data untuk kategori ini", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Custom Canvas Pie Chart
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(110.dp)) {
                            val strokeWidth = 24.dp.toPx()
                            val totalCount = slices.sumOf { it.count }.toFloat()
                            var startAngle = -90f

                            slices.forEach { slice ->
                                val sweepAngle = (slice.count / totalCount) * 360f
                                drawArc(
                                    color = slice.color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                    style = Stroke(width = strokeWidth)
                                )
                                startAngle += sweepAngle
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalItems",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Total",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Legend Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val totalCount = slices.sumOf { it.count }.toFloat()
                        slices.forEach { slice ->
                            val pct = if (totalCount > 0) ((slice.count / totalCount) * 100).toInt() else 0
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(slice.color)
                                )
                                Text(
                                    text = slice.label,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${slice.count} ($pct%)",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopRankItemCard(
    rank: Int,
    item: UserMediaItem,
    isAnime: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Rank Badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (rank == 1) StarGold else if (rank <= 3) AccentBlue else CardElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    color = if (rank == 1) BlackBg else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Cover thumbnail
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .width(42.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isAnime) {
                        "${item.progress} / ${if (item.totalEpisodes > 0) item.totalEpisodes else "?"} Episode"
                    } else {
                        "${item.progressChapters} Bab"
                    },
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = item.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                    color = AccentBlueLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Score Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(StarGold.copy(alpha = 0.15f))
                    .border(1.dp, StarGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(14.dp))
                    Text(
                        text = "${item.score}",
                        color = StarGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTopScoreCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = message, color = TextMuted, fontSize = 12.sp)
        }
    }
}
