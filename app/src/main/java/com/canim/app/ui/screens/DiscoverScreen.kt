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
import com.canim.app.data.repository.StudioBioRegistry
import androidx.compose.ui.graphics.Brush
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.CanimUiState

private val ItemCardShape = RoundedCornerShape(12.dp)
private val ItemImageShape = RoundedCornerShape(8.dp)
private val AnimeBorderStroke = BorderStroke(1.dp, CardBorderSubtle)
private val MangaBorderStroke = BorderStroke(1.dp, MangaCardBorder.copy(alpha = 0.45f))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    state: CanimUiState,
    onSelectCategory: (DiscoverCategory, DiscoverFilter) -> Unit,
    onAddMedia: (MediaItem, MediaStatus) -> Unit,
    onSelectItem: (Any, MediaType) -> Unit,
    onLoadMore: () -> Unit = {},
    onSaveAnime: (UserMediaItem) -> Unit = {},
    onSaveManga: (UserMediaItem) -> Unit = {},
    onOpenStudio: ((studioId: Int, studioName: String) -> Unit)? = null,
    onSearchStudio: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
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

    val animeCategories = remember {
        listOf(
            DiscoverCategory.CURRENT_SEASON,
            DiscoverCategory.NEXT_SEASON,
            DiscoverCategory.STUDIO,
            DiscoverCategory.TOP_ANIME,
            DiscoverCategory.TRENDING_NOW,
            DiscoverCategory.UPCOMING,
            DiscoverCategory.TBA
        )
    }

    val mangaCategories = remember {
        listOf(
            DiscoverCategory.TRENDING_NOW,
            DiscoverCategory.TOP_MANGA,
            DiscoverCategory.RECENTLY_DONE_MANGA,
            DiscoverCategory.NEWLY_ADDED_MANGA
        )
    }

    var discoverMediaType by remember {
        mutableStateOf(
            if (state.selectedDiscoverCategory in mangaCategories && state.selectedDiscoverCategory !in animeCategories) {
                MediaType.MANGA
            } else {
                MediaType.ANIME
            }
        )
    }

    val currentCategories = if (discoverMediaType == MediaType.ANIME) animeCategories else mangaCategories
    val currentAccent = if (discoverMediaType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue
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
                }
            }

            // 2-State Media Mode Toggle (Anime / Manga)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (discoverMediaType == MediaType.ANIME) AccentBlue else Color.Transparent)
                            .clickable {
                                if (discoverMediaType != MediaType.ANIME) {
                                    discoverMediaType = MediaType.ANIME
                                    onSelectCategory(DiscoverCategory.CURRENT_SEASON, DiscoverFilter())
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Anime",
                            color = if (discoverMediaType == MediaType.ANIME) Color.White else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (discoverMediaType == MediaType.ANIME) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (discoverMediaType == MediaType.MANGA) MangaAccentDarkBlue else Color.Transparent)
                            .clickable {
                                if (discoverMediaType != MediaType.MANGA) {
                                    discoverMediaType = MediaType.MANGA
                                    onSelectCategory(DiscoverCategory.TRENDING_NOW, DiscoverFilter())
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Manga",
                            color = if (discoverMediaType == MediaType.MANGA) Color.White else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (discoverMediaType == MediaType.MANGA) FontWeight.Bold else FontWeight.Medium
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
                    items(currentCategories, key = { it.name }, contentType = { "category_chip" }) { category ->
                        val isStudio = category == DiscoverCategory.STUDIO
                        val isSelected = state.selectedDiscoverCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isStudio) {
                                    showStudioPickerSheet = true
                                } else if (!isSelected) {
                                    onSelectCategory(category, DiscoverFilter())
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
                                selectedContainerColor = currentAccent,
                                selectedLabelColor = Color.White,
                                containerColor = if (isStudio) AccentBlue.copy(alpha = 0.18f) else CardBg,
                                labelColor = if (isStudio) AccentBlue else TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isStudio) AccentBlue.copy(alpha = 0.4f) else CardBorderSubtle,
                                selectedBorderColor = currentAccent
                            )
                        )
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
            onDismissRequest = {
                showStudioPickerSheet = false
                studioSearchQuery = ""
                onSearchStudio?.invoke("")
            },
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
                    6222 to "CloverWorks",
                    561 to "A-1 Pictures",
                    44 to "Shaft",
                    10 to "Production I.G",
                    803 to "Trigger",
                    7 to "J.C.Staff",
                    18 to "Toei Animation",
                    291 to "CoMix Wave Films",
                    95 to "Doga Kobo",
                    287 to "David Production"
                )
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
                    text = "Cari atau pilih studio animasi untuk melihat katalog seluruh karya anime yang diproduksi",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = studioSearchQuery,
                    onValueChange = {
                        studioSearchQuery = it
                        onSearchStudio?.invoke(it)
                    },
                    placeholder = { Text("Cari nama studio (misal: A-1 Pictures, Passione, Nexus)...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AccentBlue)
                    },
                    trailingIcon = if (studioSearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = {
                                studioSearchQuery = ""
                                onSearchStudio?.invoke("")
                            }) {
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

                if (studioSearchQuery.isBlank()) {
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
                        items(popularStudios, key = { it.first }, contentType = { "popular_studio" }) { (sId, sName) ->
                            val studioInfo = remember(sId, sName) { StudioBioRegistry.getStudioInfo(sId, sName) }
                            Card(
                                onClick = {
                                    showStudioPickerSheet = false
                                    studioSearchQuery = ""
                                    onSearchStudio?.invoke("")
                                    onOpenStudio?.invoke(sId, sName)
                                },
                                colors = CardDefaults.cardColors(containerColor = CardElevated),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(88.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (!studioInfo.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = studioInfo.coverUrl,
                                            contentDescription = sName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color.Black.copy(alpha = 0.40f),
                                                        Color.Black.copy(alpha = 0.88f)
                                                    )
                                                )
                                            )
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CardBg.copy(alpha = 0.9f))
                                                .border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = sName.take(1).uppercase(),
                                                color = AccentBlue,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sName,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (studioInfo.foundedYear != null) "Est. ${studioInfo.foundedYear}" else studioInfo.country,
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Live Search Results View
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HASIL PENCARIAN (${state.studioSearchResults.size})",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (state.isSearchingStudios) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentBlue
                                )
                                Text(
                                    text = "Mencari di AniList...",
                                    color = AccentBlue,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    if (state.isSearchingStudios && state.studioSearchResults.isEmpty()) {
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 2.5.dp,
                                    color = AccentBlue
                                )
                                Text(
                                    text = "Mencari studio di AniList...",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else if (state.studioSearchResults.isEmpty() && !state.isSearchingStudios) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Tidak ditemukan studio \"$studioSearchQuery\"",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Periksa ejaan nama studio dan coba lagi",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.studioSearchResults, key = { it.studioId }, contentType = { "studio_search_result" }) { studioInfo ->
                                val sId = studioInfo.studioId
                                val sName = studioInfo.name
                                Card(
                                    onClick = {
                                        showStudioPickerSheet = false
                                        studioSearchQuery = ""
                                        onSearchStudio?.invoke("")
                                        onOpenStudio?.invoke(sId, sName)
                                    },
                                    colors = CardDefaults.cardColors(containerColor = CardElevated),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(88.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (!studioInfo.coverUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = studioInfo.coverUrl,
                                                contentDescription = sName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Black.copy(alpha = 0.40f),
                                                            Color.Black.copy(alpha = 0.88f)
                                                        )
                                                    )
                                                )
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(CardBg.copy(alpha = 0.9f))
                                                    .border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = sName.take(1).uppercase(),
                                                    color = AccentBlue,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = sName,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val subLabel = when {
                                                    studioInfo.foundedYear != null -> "Est. ${studioInfo.foundedYear}"
                                                    !studioInfo.country.isNullOrBlank() -> studioInfo.country
                                                    else -> "Studio Animasi"
                                                }
                                                Text(
                                                    text = subLabel,
                                                    color = TextSecondary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
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
    val themeAccent = if (isManga) MangaAccentDarkBlue else AccentBlue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item) }
            .testTag("discover_card_${item.malId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 62.dp, height = 88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardElevated)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

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
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isInLibrary) AccentGreen.copy(alpha = 0.16f) else CardElevated)
                    .size(36.dp)
                    .testTag(if (isInLibrary) "discover_edit_btn_${item.malId}" else "discover_add_btn_${item.malId}")
            ) {
                Icon(
                    imageVector = if (isInLibrary) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = if (isInLibrary) "Ubah Status" else "Tambah ke Koleksi",
                    tint = if (isInLibrary) AccentGreen else TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerSubtle)
        )
    }
}
