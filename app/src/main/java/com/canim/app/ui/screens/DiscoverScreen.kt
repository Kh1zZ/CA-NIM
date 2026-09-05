package com.canim.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import com.canim.app.data.model.*
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.CanimUiState

private val ItemCardShape = RoundedCornerShape(12.dp)
private val ItemImageShape = RoundedCornerShape(8.dp)
private val AnimeBorderStroke = BorderStroke(1.dp, CardBorder)
private val MangaBorderStroke = BorderStroke(1.dp, MangaCardBorder)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    state: CanimUiState,
    onSelectCategory: (DiscoverCategory, DiscoverFilter) -> Unit,
    onAddMedia: (MediaItem, MediaStatus) -> Unit,
    onSelectItem: (Any, MediaType) -> Unit,
    onRandomize: (DiscoverFilter) -> Unit,
    onRandomizeManga: (DiscoverFilter) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onSaveAnime: (UserMediaItem) -> Unit = {},
    onSaveManga: (UserMediaItem) -> Unit = {},
    onOpenStudio: ((studioId: Int, studioName: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showFilterPanel by remember { mutableStateOf(false) }
    var filterMediaType by remember { mutableStateOf(if (state.discoverFilter.format == "MANGA") MediaType.MANGA else MediaType.ANIME) }
    var selectedItemForAdd by remember { mutableStateOf<MediaItem?>(null) }
    var selectedItemForEdit by remember { mutableStateOf<MediaItem?>(null) }
    var showStudioPickerSheet by remember { mutableStateOf(false) }
    var studioSearchQuery by remember { mutableStateOf("") }

    val onSelectDiscoverMedia: (MediaItem) -> Unit = remember(onSelectItem) {
        { media -> onSelectItem(media, media.type) }
    }
    val onAddDiscoverMedia: (MediaItem) -> Unit = remember {
        { media -> selectedItemForAdd = media }
    }
    val onEditDiscoverMedia: (MediaItem) -> Unit = remember {
        { media -> selectedItemForEdit = media }
    }

    val libraryAnimeMalIds = remember(state.animeList) { state.animeList.map { it.malId }.toSet() }
    val libraryMangaMalIds = remember(state.mangaList) { state.mangaList.map { it.malId }.toSet() }

    // Filter controls for Random by Filter
    var filterGenre by remember { mutableStateOf(state.discoverFilter.genre) }
    var filterFormat by remember { mutableStateOf(state.discoverFilter.format ?: "TV") }
    var filterYear by remember { mutableStateOf(state.discoverFilter.year) }
    var filterSeason by remember { mutableStateOf(state.discoverFilter.season) }
    var filterMinScore by remember { mutableStateOf(state.discoverFilter.minScore) }

    // Auto-apply debounced filter (350ms)
    LaunchedEffect(filterGenre, filterFormat, filterYear, filterSeason, filterMinScore) {
        kotlinx.coroutines.delay(350L)
        val updatedFilter = DiscoverFilter(
            genre = filterGenre,
            format = filterFormat,
            year = filterYear,
            season = filterSeason,
            minScore = filterMinScore
        )
        if (showFilterPanel || state.selectedDiscoverCategory == DiscoverCategory.RANDOM_FILTER) {
            onSelectCategory(DiscoverCategory.RANDOM_FILTER, updatedFilter)
        }
    }

    val categories = DiscoverCategory.entries

    // Expanded AniList genres (Request 4)
    val genres = listOf(
        "Semua", "Action", "Adventure", "Comedy", "Drama", "Ecchi",
        "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
        "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
        "Supernatural", "Thriller"
    )

    // Expanded formats with ONA, OVA, Special (Request 4)
    val formats = listOf(
        "TV" to "TV Series",
        "MOVIE" to "Movie",
        "ONA" to "ONA",
        "OVA" to "OVA",
        "SPECIAL" to "Special"
    )

    // Expanded score options with 5+ and 6+ (Request 4)
    val scores = listOf(
        0 to "Semua Skor",
        5 to "★ 5.0+",
        6 to "★ 6.0+",
        7 to "★ 7.0+",
        8 to "★ 8.0+",
        9 to "★ 9.0+"
    )

    val listState = rememberLazyListState()

    // Smooth Pagination Trigger (Request 2)
    LaunchedEffect(listState) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }
        .distinctUntilChanged()
        .collect { nearBottom ->
            if (nearBottom && state.canLoadMoreDiscover && !state.isDiscoverLoadingMore && !state.isDiscoverLoading) {
                onLoadMore()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(BlackBg)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Eksplorasi & Temukan",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Jelajahi rilisan musim ini & katalog lengkap dari AniList",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = { showFilterPanel = !showFilterPanel },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardBg)
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter Kustom",
                            tint = AccentBlue
                        )
                    }
                }
            }

            // Horizontal Category Chips
            item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(categories) { category ->
                    val isStudio = category == DiscoverCategory.STUDIO
                    val isSelected = state.selectedDiscoverCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isStudio) {
                                showStudioPickerSheet = true
                            } else if (!isSelected) {
                                val currentFilter = DiscoverFilter(
                                    genre = filterGenre,
                                    format = filterFormat,
                                    year = filterYear,
                                    season = filterSeason,
                                    minScore = filterMinScore
                                )
                                onSelectCategory(category, currentFilter)
                            }
                        },
                        leadingIcon = if (isStudio) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else AccentBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null,
                        label = {
                            Text(
                                text = category.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected || isStudio) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            selectedLabelColor = Color.White,
                            containerColor = if (isStudio) AccentBlue.copy(alpha = 0.18f) else CardBg,
                            labelColor = if (isStudio) AccentBlue else TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isStudio) AccentBlue.copy(alpha = 0.6f) else CardBorder,
                            selectedBorderColor = AccentBlue
                        )
                    )
                }
            }
        }

        // Expandable Filter Section
        item {
            AnimatedVisibility(visible = showFilterPanel || state.selectedDiscoverCategory == DiscoverCategory.RANDOM_FILTER) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Kustomisasi Filter & Acak",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // 2-state Anime/Manga toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardElevated)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(MediaType.ANIME to "Anime", MediaType.MANGA to "Manga").forEach { (type, label) ->
                                val isSelected = filterMediaType == type
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) (if (type == MediaType.ANIME) AccentBlue else MangaAccentDarkBlue) else Color.Transparent)
                                        .clickable { 
                                            filterMediaType = type
                                            if (type == MediaType.MANGA) {
                                                filterFormat = "MANGA"
                                            } else if (filterFormat == "MANGA") {
                                                filterFormat = "TV"
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Format selector (Only shown for Anime, hidden for Manga)
                        if (filterMediaType == MediaType.ANIME) {
                            Text(text = "Tipe / Format:", color = TextSecondary, fontSize = 11.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(formats) { (key, label) ->
                                    val isSelected = filterFormat == key
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) AccentBlue else CardElevated)
                                            .clickable { filterFormat = key }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Genre selector
                        Text(text = "Genre:", color = TextSecondary, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(genres) { g ->
                                val isSelected = (g == "Semua" && filterGenre == null) || filterGenre == g
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentBlue else CardElevated)
                                        .clickable { filterGenre = if (g == "Semua") null else g }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = g,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Score selector (Scrollable)
                        Text(text = "Minimal Skor:", color = TextSecondary, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(scores) { (sc, label) ->
                                val isSelected = (sc == 0 && filterMinScore == null) || filterMinScore == sc
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) StarGold else CardElevated)
                                        .clickable { filterMinScore = if (sc == 0) null else sc }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) BlackBg else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Single Acak Button
                        Button(
                            onClick = {
                                if (filterMediaType == MediaType.ANIME) {
                                    val activeFilter = DiscoverFilter(
                                        genre = filterGenre,
                                        format = if (filterFormat == "MANGA") "TV" else filterFormat,
                                        year = filterYear,
                                        season = filterSeason,
                                        minScore = filterMinScore
                                    )
                                    onRandomize(activeFilter)
                                } else {
                                    val activeFilter = DiscoverFilter(
                                        genre = filterGenre,
                                        format = "MANGA",
                                        year = filterYear,
                                        season = filterSeason,
                                        minScore = filterMinScore
                                    )
                                    onRandomizeManga(activeFilter)
                                }
                                showFilterPanel = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("discover_filter_randomize_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (filterMediaType == MediaType.ANIME) AccentBlue else MangaAccentDarkBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (filterMediaType == MediaType.ANIME) "Acak Anime" else "Acak Manga",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        if (state.isDiscoverLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = AccentBlue)
                        Text(
                            text = "Mengambil data ${state.selectedDiscoverCategory.label} dari AniList...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else if (state.discoverItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak ada judul yang ditemukan untuk kategori ini.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(
                state.discoverItems,
                key = { "${it.type}_${it.malId}_${it.anilistId}" },
                contentType = { "discover_item" }
            ) { media ->
                val isInLibrary = if (media.type == MediaType.MANGA) {
                    libraryMangaMalIds.contains(media.malId)
                } else {
                    libraryAnimeMalIds.contains(media.malId)
                }
                DiscoverItemCard(
                    item = media,
                    isInLibrary = isInLibrary,
                    onClick = onSelectDiscoverMedia,
                    onAddClick = onAddDiscoverMedia,
                    onEditClick = onEditDiscoverMedia
                )
            }

            // Pagination loading more indicator (Request 2)
            if (state.isDiscoverLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentBlue
                            )
                            Text(
                                text = "Memuat halaman berikutnya...",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Add To Library Status Picker Dialog
    if (selectedItemForAdd != null) {
        val targetItem = selectedItemForAdd!!
        val isAnime = targetItem.type == MediaType.ANIME

        val statusOptions = if (isAnime) {
            listOf(
                MediaStatus.WATCHING,
                MediaStatus.PLAN_TO_WATCH,
                MediaStatus.COMPLETED,
                MediaStatus.ON_HOLD,
                MediaStatus.DROPPED
            )
        } else {
            listOf(
                MediaStatus.READING,
                MediaStatus.PLAN_TO_READ,
                MediaStatus.COMPLETED,
                MediaStatus.ON_HOLD,
                MediaStatus.DROPPED
            )
        }

        AlertDialog(
            onDismissRequest = { selectedItemForAdd = null },
            containerColor = CardElevated,
            title = {
                Text(
                    text = "Tambah ke Library",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = targetItem.title,
                        color = AccentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Pilih status awal untuk item ini:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    statusOptions.forEach { statusOption ->
                        Button(
                            onClick = {
                                onAddMedia(targetItem, statusOption)
                                selectedItemForAdd = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CardBg,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = statusOption.label, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedItemForAdd = null }) {
                    Text("Batal", color = TextMuted)
                }
            }
        )
    }

    // Quick Status Editor Dialog for items already in library (Task A2)
    if (selectedItemForEdit != null) {
        val targetItem = selectedItemForEdit!!
        val isAnime = targetItem.type == MediaType.ANIME
        val currentStatus = if (isAnime) {
            state.animeList.find { it.malId == targetItem.malId }?.status
        } else {
            state.mangaList.find { it.malId == targetItem.malId }?.status
        }

        val statusOptions = if (isAnime) {
            listOf(
                MediaStatus.WATCHING,
                MediaStatus.PLAN_TO_WATCH,
                MediaStatus.COMPLETED,
                MediaStatus.ON_HOLD,
                MediaStatus.DROPPED
            )
        } else {
            listOf(
                MediaStatus.READING,
                MediaStatus.PLAN_TO_READ,
                MediaStatus.COMPLETED,
                MediaStatus.ON_HOLD,
                MediaStatus.DROPPED
            )
        }

        AlertDialog(
            onDismissRequest = { selectedItemForEdit = null },
            containerColor = CardElevated,
            title = {
                Text(
                    text = "Ubah Status di Library",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = targetItem.title,
                        color = AccentBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Item ini sudah ada di Library kamu. Pilih status baru:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    statusOptions.forEach { statusOption ->
                        val isCurrent = currentStatus == statusOption.apiValue
                        Button(
                            onClick = {
                                if (isAnime) {
                                    state.animeList.find { it.malId == targetItem.malId }?.let { entity ->
                                        onSaveAnime(entity.withStatus(statusOption.apiValue))
                                    }
                                } else {
                                    state.mangaList.find { it.malId == targetItem.malId }?.let { entity ->
                                        onSaveManga(entity.withStatus(statusOption.apiValue))
                                    }
                                }
                                selectedItemForEdit = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCurrent) AccentBlue.copy(alpha = 0.25f) else CardBg,
                                contentColor = if (isCurrent) AccentBlueLight else TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = statusOption.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Status Aktif",
                                        tint = AccentBlueLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val editItem = selectedItemForEdit
                    selectedItemForEdit = null
                    if (editItem != null) {
                        onSelectItem(editItem, editItem.type)
                    }
                }) {
                    Text("Detail Lengkap...", color = AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForEdit = null }) {
                    Text("Batal", color = TextMuted)
                }
            }
        )
    }

    // Studio Picker Modal Bottom Sheet
    if (showStudioPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStudioPickerSheet = false },
            containerColor = CardBg,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            val popularStudios = remember {
                listOf(
                    569 to "MAPPA",
                    43 to "Ufotable",
                    2 to "Kyoto Animation",
                    4 to "Bones",
                    858 to "Wit Studio",
                    11 to "Madhouse",
                    6140 to "CloverWorks",
                    56 to "A-1 Pictures",
                    44 to "Shaft",
                    10 to "Production I.G",
                    803 to "Trigger",
                    7 to "J.C.Staff",
                    18 to "Toei Animation",
                    290 to "CoMix Wave Films",
                    95 to "Doga Kobo",
                    287 to "David Production"
                )
            }

            val filteredStudios = remember(studioSearchQuery) {
                if (studioSearchQuery.isBlank()) popularStudios
                else popularStudios.filter { it.second.contains(studioSearchQuery, ignoreCase = true) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Jelajahi Filmografi Studio",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pilih studio animasi ternama untuk melihat katalog karya yang diproduksi",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = studioSearchQuery,
                    onValueChange = { studioSearchQuery = it },
                    placeholder = { Text("Cari nama studio...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AccentBlue)
                    },
                    trailingIcon = if (studioSearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { studioSearchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = TextMuted)
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "STUDIO POPULER",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStudios) { (sId, sName) ->
                        Surface(
                            onClick = {
                                showStudioPickerSheet = false
                                onOpenStudio?.invoke(sId, sName)
                            },
                            color = CardElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = sName,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
}

@Composable
fun DiscoverItemCard(
    item: MediaItem,
    isInLibrary: Boolean = false,
    onClick: (MediaItem) -> Unit,
    onAddClick: (MediaItem) -> Unit,
    onEditClick: (MediaItem) -> Unit = {}
) {
    val isManga = item.type == MediaType.MANGA
    val itemBorder = if (isManga) MangaBorderStroke else AnimeBorderStroke
    val themeAccent = if (isManga) MangaAccentDarkBlue else AccentBlue

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item) }
            .testTag("discover_card_${item.malId}"),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = ItemCardShape,
        border = itemBorder
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(ItemImageShape)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title expanded to 2-3 lines (Request 3)
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Retain rating, type, and studio (Request 3)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.score != null && item.score > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = StarGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = item.scoreFormatted,
                                color = StarGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (item.format != null) {
                        Text(
                            text = item.format,
                            color = themeAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (item.studio != null) {
                        Text(
                            text = item.studio,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Retain genres (Request 3)
                if (item.genres.isNotEmpty()) {
                    Text(
                        text = item.genresFormatted,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { if (isInLibrary) onEditClick(item) else onAddClick(item) },
                modifier = Modifier
                    .clip(ItemImageShape)
                    .background(if (isInLibrary) AccentGreen.copy(alpha = 0.16f) else CardElevated)
                    .size(36.dp)
                    .testTag(if (isInLibrary) "discover_edit_btn_${item.malId}" else "discover_add_btn_${item.malId}")
            ) {
                Icon(
                    imageVector = if (isInLibrary) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = if (isInLibrary) "Ubah Status" else "Tambah ke Koleksi",
                    tint = if (isInLibrary) AccentGreen else themeAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
