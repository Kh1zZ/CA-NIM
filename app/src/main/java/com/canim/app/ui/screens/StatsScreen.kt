package com.canim.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.canim.app.ui.components.CanimAsyncImage
import com.canim.app.data.local.Top5CustomManager
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.UserMediaItem
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.library.LibraryUiState
import com.canim.app.ui.viewmodel.global.GlobalUiState
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
    libraryState: LibraryUiState,
    globalState: GlobalUiState,
    onBack: () -> Unit,
    onSelectItem: (Any, MediaType) -> Unit,
    onSaveScrollPosition: ((index: Int, offset: Int) -> Unit)? = null,
    onGetScrollPosition: (() -> Pair<Int, Int>)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val initialScroll = remember { onGetScrollPosition?.invoke() ?: Pair(0, 0) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScroll.first,
        initialFirstVisibleItemScrollOffset = initialScroll.second
    )

    DisposableEffect(Unit) {
        onDispose {
            onSaveScrollPosition?.invoke(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    val top5Manager = remember { Top5CustomManager.getInstance(context) }
    var top5AnimeIds by remember { mutableStateOf(top5Manager.getTop5AnimeIds()) }
    var top5MangaIds by remember { mutableStateOf(top5Manager.getTop5MangaIds()) }

    var isEditingAnime by remember { mutableStateOf(false) }
    var isEditingManga by remember { mutableStateOf(false) }

    // Picker BottomSheet state
    var pickingMediaType by remember { mutableStateOf<MediaType?>(null) } // ANIME or MANGA
    var pickingSlotIndex by remember { mutableIntStateOf(-1) }

    // Resolve IDs to UserMediaItem or null for empty slots
    val top5AnimeItems: List<UserMediaItem?> = remember(top5AnimeIds, libraryState.animeList) {
        val list = mutableListOf<UserMediaItem?>()
        for (i in 0 until 5) {
            val id = top5AnimeIds.getOrNull(i)
            val item = if (id != null) libraryState.animeList.find { it.id == id } else null
            list.add(item)
        }
        list
    }

    val top5MangaItems: List<UserMediaItem?> = remember(top5MangaIds, libraryState.mangaList) {
        val list = mutableListOf<UserMediaItem?>()
        for (i in 0 until 5) {
            val id = top5MangaIds.getOrNull(i)
            val item = if (id != null) libraryState.mangaList.find { it.id == id } else null
            list.add(item)
        }
        list
    }

    // Filter completed items sorted by score descending for picker bottom sheet
    val completedAnimeList = remember(libraryState.animeList) {
        libraryState.animeList
            .filter { it.status == "completed" }
            .sortedWith(compareByDescending<UserMediaItem> { it.score }.thenBy { it.title })
    }

    val completedMangaList = remember(libraryState.mangaList) {
        libraryState.mangaList
            .filter { it.status == "completed" }
            .sortedWith(compareByDescending<UserMediaItem> { it.score }.thenBy { it.title })
    }

    // TopAnime and TopManga for export
    val topAnime = remember(top5AnimeItems) { top5AnimeItems.filterNotNull() }
    val topManga = remember(top5MangaItems) { top5MangaItems.filterNotNull() }

    // Pie chart data with unified StatsColors
    val animeSlices = remember(libraryState.stats) {
        listOf(
            PieSlice("Ditonton", libraryState.stats.animeWatching, StatsColors.Watching),
            PieSlice("Selesai", libraryState.stats.animeCompleted, StatsColors.Completed),
            PieSlice("Ditunda", libraryState.stats.animeOnHold, StatsColors.OnHold),
            PieSlice("Drop", libraryState.stats.animeDropped, StatsColors.Dropped),
            PieSlice("Rencana", libraryState.stats.animePlanToWatch, StatsColors.PlanTo)
        ).filter { it.count > 0 }
    }

    val mangaSlices = remember(libraryState.stats) {
        listOf(
            PieSlice("Dibaca", libraryState.stats.mangaReading, StatsColors.Reading),
            PieSlice("Selesai", libraryState.stats.mangaCompleted, StatsColors.Completed),
            PieSlice("Ditunda", libraryState.stats.mangaOnHold, StatsColors.OnHold),
            PieSlice("Drop", libraryState.stats.mangaDropped, StatsColors.Dropped),
            PieSlice("Rencana", libraryState.stats.mangaPlanToRead, StatsColors.PlanTo)
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
                        onClick = {
                            val animeFull = top5AnimeItems.all { it != null }
                            val mangaFull = top5MangaItems.all { it != null }
                            if (!animeFull || !mangaFull) {
                                Toast.makeText(
                                    context,
                                    "Isi penuh Top 5 Anime dan Top 5 Manga (5 judul masing-masing) sebelum mengekspor!",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                showExportDialog = true
                            }
                        },
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
            state = listState,
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
                        if (!globalState.malUser.pictureUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = globalState.malUser.pictureUrl,
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
                                    text = globalState.malUser.username.take(1).uppercase().ifBlank { "U" },
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = globalState.malUser.username.ifBlank { "Tamu (Mode Offline)" },
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
                                        .background(if (globalState.malUser.isLoggedIn) AccentGreen else TextMuted)
                                )
                                Text(
                                    text = if (globalState.malUser.isLoggedIn) "Tersinkronisasi MyAnimeList" else "Mode Tamu / Offline",
                                    color = if (globalState.malUser.isLoggedIn) AccentGreen else TextMuted,
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
                            value = "${libraryState.stats.daysWatched}",
                            unit = "Hari",
                            subtitle = "${(libraryState.stats.episodesWatched * 24) / 60} Jam Total",
                            icon = Icons.Default.Schedule,
                            accentColor = AccentBlue
                        )
                        BigMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Bab Dibaca",
                            value = "${libraryState.stats.chaptersRead}",
                            unit = "Bab",
                            subtitle = "${libraryState.stats.volumesRead} Volume",
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
                            value = "${libraryState.stats.totalAnime + libraryState.stats.totalManga}",
                            unit = "Judul",
                            subtitle = "${libraryState.stats.totalAnime} Anime • ${libraryState.stats.totalManga} Manga",
                            icon = Icons.Default.LibraryBooks,
                            accentColor = Color(0xFFA855F7)
                        )
                        BigMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "Rata-Rata Skor",
                            value = if (libraryState.stats.meanScore > 0) "★ ${libraryState.stats.meanScore}" else "-",
                            unit = "",
                            subtitle = "${libraryState.stats.completedCount} Judul Tamat",
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
                    totalItems = libraryState.stats.totalAnime,
                    slices = animeSlices,
                    icon = Icons.Default.Tv,
                    accentColor = AccentBlue
                )
            }

            // Pie Chart 2: Manga Distribution
            item {
                StatusPieChartCard(
                    title = "Distribusi Status Manga",
                    totalItems = libraryState.stats.totalManga,
                    slices = mangaSlices,
                    icon = Icons.Default.AutoStories,
                    accentColor = MangaAccentDarkBlue
                )
            }

            // Top 5 Anime Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TOP 5 ANIME PRIBADI",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = {
                            if (isEditingAnime) {
                                // Save action: validate that all 5 slots are populated
                                val isComplete = top5AnimeItems.all { it != null }
                                if (!isComplete) {
                                    Toast.makeText(
                                        context,
                                        "Top 5 Anime harus terisi lengkap (5 judul) sebelum disimpan.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    top5Manager.saveTop5AnimeIds(top5AnimeIds)
                                    isEditingAnime = false
                                    Toast.makeText(context, "Top 5 Anime berhasil disimpan", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                isEditingAnime = true
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditingAnime) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditingAnime) "Simpan Top 5 Anime" else "Ubah Top 5 Anime",
                            tint = if (isEditingAnime) AccentGreen else AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            itemsIndexed(top5AnimeItems) { index, item ->
                if (item != null) {
                    TopRankItemCard(
                        rank = index + 1,
                        item = item,
                        isAnime = true,
                        isEditing = isEditingAnime,
                        onClick = {
                            if (isEditingAnime) {
                                pickingMediaType = MediaType.ANIME
                                pickingSlotIndex = index
                            } else {
                                onSaveScrollPosition?.invoke(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                                onSelectItem(item, MediaType.ANIME)
                            }
                        }
                    )
                } else {
                    EmptyTopSlotCard(
                        rank = index + 1,
                        label = "Tambah Anime",
                        isEditing = isEditingAnime,
                        onClick = {
                            if (isEditingAnime) {
                                pickingMediaType = MediaType.ANIME
                                pickingSlotIndex = index
                            } else {
                                Toast.makeText(context, "Tekan tombol edit (ikon pensil) untuk memilih anime", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Top 5 Manga Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TOP 5 MANGA PRIBADI",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = {
                            if (isEditingManga) {
                                // Save action: validate that all 5 slots are populated
                                val isComplete = top5MangaItems.all { it != null }
                                if (!isComplete) {
                                    Toast.makeText(
                                        context,
                                        "Top 5 Manga harus terisi lengkap (5 judul) sebelum disimpan.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    top5Manager.saveTop5MangaIds(top5MangaIds)
                                    isEditingManga = false
                                    Toast.makeText(context, "Top 5 Manga berhasil disimpan", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                isEditingManga = true
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditingManga) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditingManga) "Simpan Top 5 Manga" else "Ubah Top 5 Manga",
                            tint = if (isEditingManga) AccentGreen else MangaAccentDarkBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            itemsIndexed(top5MangaItems) { index, item ->
                if (item != null) {
                    TopRankItemCard(
                        rank = index + 1,
                        item = item,
                        isAnime = false,
                        isEditing = isEditingManga,
                        onClick = {
                            if (isEditingManga) {
                                pickingMediaType = MediaType.MANGA
                                pickingSlotIndex = index
                            } else {
                                onSaveScrollPosition?.invoke(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                                onSelectItem(item, MediaType.MANGA)
                            }
                        }
                    )
                } else {
                    EmptyTopSlotCard(
                        rank = index + 1,
                        label = "Tambah Manga",
                        isEditing = isEditingManga,
                        onClick = {
                            if (isEditingManga) {
                                pickingMediaType = MediaType.MANGA
                                pickingSlotIndex = index
                            } else {
                                Toast.makeText(context, "Tekan tombol edit (ikon pensil) untuk memilih manga", Toast.LENGTH_SHORT).show()
                            }
                        }
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

                    com.canim.app.ui.components.SmoothSegmentedSelector(
                        options = ExportAspectRatio.entries,
                        selectedOption = selectedRatio,
                        onOptionSelected = { if (!isExporting) selectedRatio = it },
                        labelProvider = { it.label },
                        highlightColor = AccentBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    )

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
                                        stats = libraryState.stats,
                                        malUser = globalState.malUser,
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
        )
    }

    // Modal Bottom Sheet for picking anime / manga
    if (pickingMediaType != null && pickingSlotIndex in 0..4) {
        val currentType = pickingMediaType!!
        val isAnime = currentType == MediaType.ANIME
        val candidateList = if (isAnime) completedAnimeList else completedMangaList
        val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = {
                pickingMediaType = null
                pickingSlotIndex = -1
            },
            sheetState = modalBottomSheetState,
            containerColor = CardElevated,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Pilih ${if (isAnime) "Anime" else "Manga"} untuk Slot #${pickingSlotIndex + 1}",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Hanya menampilkan judul berstatus tamat/selesai di koleksi Anda, diurutkan dari rating skor tertinggi.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (candidateList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada ${if (isAnime) "anime" else "manga"} dengan status tamat/selesai di koleksi.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(candidateList) { _, item ->
                            val isAlreadySelected = if (isAnime) {
                                top5AnimeIds.contains(item.id)
                            } else {
                                top5MangaIds.contains(item.id)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (isAlreadySelected) AccentBlue.copy(alpha = 0.6f) else CardBorderSubtle,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        if (isAnime) {
                                            val currentList = top5AnimeIds.toMutableList()
                                            while (currentList.size < 5) currentList.add("")
                                            // Check if item is already in another slot, if so swap or replace
                                            val existingIndex = currentList.indexOf(item.id)
                                            if (existingIndex != -1 && existingIndex != pickingSlotIndex) {
                                                currentList[existingIndex] = currentList[pickingSlotIndex]
                                            }
                                            currentList[pickingSlotIndex] = item.id
                                            top5AnimeIds = currentList.take(5)
                                        } else {
                                            val currentList = top5MangaIds.toMutableList()
                                            while (currentList.size < 5) currentList.add("")
                                            val existingIndex = currentList.indexOf(item.id)
                                            if (existingIndex != -1 && existingIndex != pickingSlotIndex) {
                                                currentList[existingIndex] = currentList[pickingSlotIndex]
                                            }
                                            currentList[pickingSlotIndex] = item.id
                                            top5MangaIds = currentList.take(5)
                                        }
                                        pickingMediaType = null
                                        pickingSlotIndex = -1
                                    },
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
                                    CanimAsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.title,
                                        modifier = Modifier
                                            .width(42.dp)
                                            .height(58.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )

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
                                    }

                                    if (item.score > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(StarGold.copy(alpha = 0.15f))
                                                .border(1.dp, StarGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = StarGold,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "${item.score}",
                                                    color = StarGold,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
        modifier = modifier.border(1.dp, CardBorderSubtle, RoundedCornerShape(14.dp)),
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
            .border(1.dp, CardBorderSubtle, RoundedCornerShape(16.dp)),
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

                        Text(
                            text = "$totalItems",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
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
    isEditing: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isEditing) AccentBlue else CardBorder,
                RoundedCornerShape(12.dp)
            )
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
            CanimAsyncImage(
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

            // Score Pill or Edit indicator
            if (isEditing) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentBlue.copy(alpha = 0.15f))
                        .border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Ganti",
                        tint = AccentBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
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
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(14.dp)
                        )
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
}

@Composable
private fun EmptyTopSlotCard(
    rank: Int,
    label: String,
    isEditing: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isEditing) AccentBlue else CardBorderSubtle
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .drawBehind {
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                )
                drawRoundRect(
                    color = borderColor,
                    style = stroke,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                )
            }
            .clip(shape)
            .background(CardBg.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Rank Badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CardElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = if (isEditing) AccentBlue else TextMuted,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = label,
                color = if (isEditing) AccentBlue else TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
