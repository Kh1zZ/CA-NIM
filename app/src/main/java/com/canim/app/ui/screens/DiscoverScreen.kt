package com.canim.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.canim.app.ui.components.CanimAsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import com.canim.app.data.model.*
import androidx.compose.ui.graphics.Brush
import com.canim.app.ui.components.CanimPullToRefreshLayout
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.discover.DiscoverUiState
import com.canim.app.ui.viewmodel.library.LibraryUiState
import com.canim.app.ui.viewmodel.studio.StudioViewModel

private val ItemCardShape = RoundedCornerShape(12.dp)
private val ItemImageShape = RoundedCornerShape(8.dp)
private val AnimeBorderStroke = BorderStroke(1.dp, CardBorderSubtle)
private val MangaBorderStroke = BorderStroke(1.dp, MangaCardBorder.copy(alpha = 0.45f))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    discoverState: DiscoverUiState,
    libraryState: LibraryUiState,
    studioViewModel: StudioViewModel,
    isAniListDown: Boolean = false,
    onSelectCategory: (DiscoverCategory, DiscoverFilter, MediaType) -> Unit,
    onAddMedia: (MediaItem, MediaStatus) -> Unit,
    onSelectItem: (Any, MediaType) -> Unit,
    onLoadMore: () -> Unit = {},
    onSaveAnime: (UserMediaItem) -> Unit = {},
    onSaveManga: (UserMediaItem) -> Unit = {},
    onOpenStudio: ((studioId: Int, studioName: String) -> Unit)? = null,
    onGetStudioInfo: ((studioId: Int, studioName: String) -> StudioBioInfo)? = null,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val studioState by studioViewModel.studioState.collectAsState()
    val currentSelectedCategory = discoverState.selectedCategory
    val currentDiscoverItems = discoverState.items
    val currentIsLoading = discoverState.isLoading
    val currentIsLoadingMore = discoverState.isLoadingMore
    val currentCanLoadMore = discoverState.canLoadMore

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

    val libraryAnimeMalIds = remember(libraryState.animeList) { libraryState.animeList.map { it.malId }.toSet() }
    val libraryMangaMalIds = remember(libraryState.mangaList) { libraryState.mangaList.map { it.malId }.toSet() }

    val animeCategories = remember {
        listOf(
            DiscoverCategory.STUDIO,
            DiscoverCategory.CURRENT_SEASON,
            DiscoverCategory.NEXT_SEASON,
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
            if (currentSelectedCategory in mangaCategories && currentSelectedCategory !in animeCategories) {
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
            if (nearBottom && currentCanLoadMore && !currentIsLoadingMore && !currentIsLoading) {
                onLoadMore()
            }
        }
    }

    CanimPullToRefreshLayout(
        isRefreshing = currentIsLoading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().background(BlackBg)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
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

            // 2-State Media Mode Toggle (Anime / Manga) with Smooth Sliding Indicator
            item {
                com.canim.app.ui.components.SmoothSegmentedSelector(
                    options = listOf(MediaType.ANIME, MediaType.MANGA),
                    selectedOption = discoverMediaType,
                    onOptionSelected = { selected ->
                        if (discoverMediaType != selected) {
                            discoverMediaType = selected
                            if (selected == MediaType.ANIME) {
                                onSelectCategory(DiscoverCategory.CURRENT_SEASON, DiscoverFilter(), MediaType.ANIME)
                            } else {
                                val defaultMangaCat = if (isAniListDown) DiscoverCategory.TOP_MANGA else DiscoverCategory.TRENDING_NOW
                                val mangaFilter = DiscoverFilter(format = "MANGA")
                                onSelectCategory(defaultMangaCat, mangaFilter, MediaType.MANGA)
                            }
                        }
                    },
                    labelProvider = { if (it == MediaType.ANIME) "Anime" else "Manga" },
                    highlightColor = if (discoverMediaType == MediaType.ANIME) AccentBlue else MangaAccentDarkBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )
            }

            // Horizontal Category Tabs with Smooth Sliding Highlight Pill
            item {
                val selectedCatIndex = currentCategories.indexOf(currentSelectedCategory).coerceAtLeast(0)
                ScrollableTabRow(
                    selectedTabIndex = selectedCatIndex,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedCatIndex in tabPositions.indices) {
                            Box(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[selectedCatIndex])
                                    .zIndex(-1f)
                                    .fillMaxHeight()
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(currentAccent)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    currentCategories.forEach { category ->
                        val isStudio = category == DiscoverCategory.STUDIO
                        val isSelected = currentSelectedCategory == category
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else TextSecondary,
                            animationSpec = tween(180),
                            label = "discover_tab_text_color"
                        )
                        Tab(
                            selected = isSelected,
                            onClick = {
                                if (isStudio) {
                                    showStudioPickerSheet = true
                                } else if (!isSelected) {
                                    val filter = if (discoverMediaType == MediaType.MANGA) DiscoverFilter(format = "MANGA") else DiscoverFilter()
                                    onSelectCategory(category, filter, discoverMediaType)
                                }
                            },
                            modifier = Modifier
                                .zIndex(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .padding(horizontal = 4.dp),
                            selectedContentColor = Color.White,
                            unselectedContentColor = TextSecondary,
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.zIndex(2f)
                                ) {
                                    if (isStudio) {
                                        Icon(
                                            imageVector = Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else AccentBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = category.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else if (isStudio) FontWeight.Bold else FontWeight.Medium,
                                        color = textColor
                                    )
                                }
                            }
                        )
                    }
                }
            }

        if (currentIsLoading) {
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
                        CircularProgressIndicator(color = currentAccent)
                        Text(
                            text = "Mengambil data ${currentSelectedCategory.label}...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else if (currentDiscoverItems.isEmpty()) {
            val isAniListExclusiveCategory = currentSelectedCategory == DiscoverCategory.TRENDING_NOW ||
                currentSelectedCategory == DiscoverCategory.RECENTLY_DONE_MANGA ||
                currentSelectedCategory == DiscoverCategory.NEWLY_ADDED_MANGA ||
                currentSelectedCategory == DiscoverCategory.STUDIO

            item {
                if (isAniListExclusiveCategory) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = CardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(StatusDroppedColor.copy(alpha = 0.15f))
                                    .border(1.dp, StatusDroppedColor.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Server AniList Offline",
                                    tint = StatusDroppedColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "Layanan AniList Sedang Tidak Merespon",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Kategori \"${currentSelectedCategory.label}\" membutuhkan data langsung dari AniList yang saat ini sedang mengalami gangguan atau dinonaktifkan sementara (HTTP 403).\n\nSilakan jelajahi kategori lain yang didukung penuh oleh MyAnimeList:",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                if (discoverMediaType == MediaType.ANIME) {
                                    OutlinedButton(
                                        onClick = { onSelectCategory(DiscoverCategory.CURRENT_SEASON, DiscoverFilter(), MediaType.ANIME) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f))
                                    ) {
                                        Text("Musim Ini", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onSelectCategory(DiscoverCategory.TOP_ANIME, DiscoverFilter(), MediaType.ANIME) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f))
                                    ) {
                                        Text("Anime Teratas", fontSize = 12.sp)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onSelectCategory(DiscoverCategory.TOP_MANGA, DiscoverFilter(), MediaType.MANGA) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MangaAccentDarkBlue),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MangaAccentDarkBlue.copy(alpha = 0.5f))
                                    ) {
                                        Text("Manga Teratas (MAL)", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
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
            }
        } else {
            val isTopRanking = currentSelectedCategory == DiscoverCategory.TOP_ANIME || currentSelectedCategory == DiscoverCategory.TOP_MANGA
            itemsIndexed(
                currentDiscoverItems,
                key = { _, it -> "${it.type}_${it.malId}_${it.anilistId}" },
                contentType = { _, _ -> "discover_item" }
            ) { index, media ->
                val isInLibrary = if (media.type == MediaType.MANGA) {
                    libraryMangaMalIds.contains(media.malId)
                } else {
                    libraryAnimeMalIds.contains(media.malId)
                }
                DiscoverItemCard(
                    item = media,
                    isInLibrary = isInLibrary,
                    rank = if (isTopRanking) index + 1 else null,
                    onClick = onSelectDiscoverMedia,
                    onAddClick = onAddDiscoverMedia,
                    onEditClick = onEditDiscoverMedia
                )
            }

            // Pagination loading more indicator (Request 2)
            if (currentIsLoadingMore) {
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
            libraryState.animeList.find { it.malId == targetItem.malId }?.status
        } else {
            libraryState.mangaList.find { it.malId == targetItem.malId }?.status
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
                                    libraryState.animeList.find { it.malId == targetItem.malId }?.let { entity ->
                                        onSaveAnime(entity.withStatus(statusOption.apiValue))
                                    }
                                } else {
                                    libraryState.mangaList.find { it.malId == targetItem.malId }?.let { entity ->
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
                studioViewModel.searchStudios("")
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
                        studioViewModel.searchStudios(it)
                    },
                    placeholder = { Text("Cari nama studio (misal: A-1 Pictures, Passione, Nexus)...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AccentBlue)
                    },
                    trailingIcon = if (studioSearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = {
                                studioSearchQuery = ""
                                studioViewModel.searchStudios("")
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
                            val studioInfo = remember(sId, sName) { onGetStudioInfo?.invoke(sId, sName) ?: StudioBioInfo(studioId = sId, name = sName) }
                            Card(
                                onClick = {
                                    showStudioPickerSheet = false
                                    studioSearchQuery = ""
                                    studioViewModel.searchStudios("")
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
                                        CanimAsyncImage(
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
                            text = "HASIL PENCARIAN (${studioState.searchResults.size})",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (studioState.isSearchingStudios) {
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

                    if (studioState.isSearchingStudios && studioState.searchResults.isEmpty()) {
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
                    } else if (studioState.searchResults.isEmpty() && !studioState.isSearchingStudios) {
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
                            items(studioState.searchResults, key = { it.studioId }, contentType = { "studio_search_result" }) { studioInfo ->
                                val sId = studioInfo.studioId
                                val sName = studioInfo.name
                                Card(
                                    onClick = {
                                        showStudioPickerSheet = false
                                        studioSearchQuery = ""
                                        studioViewModel.searchStudios("")
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
                                            CanimAsyncImage(
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
    rank: Int? = null,
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Continuity Rank Badge for Top Anime / Top Manga
            if (rank != null) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when (rank) {
                                1 -> StarGold
                                in 2..3 -> themeAccent
                                else -> CardElevated
                            }
                        )
                        .border(
                            1.dp,
                            when (rank) {
                                1 -> StarGold.copy(alpha = 0.5f)
                                in 2..3 -> themeAccent.copy(alpha = 0.5f)
                                else -> CardBorder
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        color = if (rank == 1) BlackBg else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            CanimAsyncImage(
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



