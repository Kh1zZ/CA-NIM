package com.canim.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaStatus
import com.canim.app.data.model.MediaType
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.FilterList
import com.canim.app.data.model.UserMediaItem
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.CanimUiState
import com.canim.app.ui.viewmodel.search.SearchUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: CanimUiState,
    onSearch: (String, MediaType) -> Unit,
    onAddMedia: (MediaItem, MediaStatus) -> Unit,
    onSelectItem: (Any, MediaType) -> Unit,
    onSaveAnime: (UserMediaItem) -> Unit = {},
    onSaveManga: (UserMediaItem) -> Unit = {},
    onApplyFilters: (genres: List<String>, year: Int?, format: String?) -> Unit = { _, _, _ -> },
    onResetFilters: () -> Unit = {},
    searchState: SearchUiState? = null,
    modifier: Modifier = Modifier
) {
    val currentQuery = searchState?.query ?: state.searchQuery
    val currentType = searchState?.type ?: state.searchType
    val currentResults = searchState?.results ?: state.searchResults
    val currentIsSearching = searchState?.isSearching ?: state.isSearching
    val currentGenres = searchState?.genres ?: state.searchGenres
    val currentYear = searchState?.year ?: state.searchYear
    val currentFormat = searchState?.format ?: state.searchFormat

    var searchInput by remember { mutableStateOf(currentQuery) }
    var searchType by remember { mutableStateOf(currentType) }
    val focusManager = LocalFocusManager.current
    var itemToAdd by remember { mutableStateOf<MediaItem?>(null) }
    var itemToEdit by remember { mutableStateOf<MediaItem?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var tempGenres by remember(currentGenres) { mutableStateOf(currentGenres.toSet()) }
    var tempYear by remember(currentYear) { mutableStateOf(currentYear) }
    var typedYearText by remember(currentYear) { mutableStateOf(currentYear?.toString() ?: "") }
    var tempFormat by remember(currentFormat) { mutableStateOf(currentFormat) }
    val hasActiveFilters = currentGenres.isNotEmpty() || currentYear != null || currentFormat != null

    val allGenres = remember {
        listOf(
            "Action", "Adventure", "Award Winning", "Comedy", "Drama",
            "Ecchi", "Fantasy", "Harem", "Horror", "Isekai",
            "Josei", "Mahou Shoujo", "Mecha", "Music", "Mystery",
            "Psychological", "Romance", "Sci-Fi", "Seinen", "Shoujo",
            "Shounen", "Slice of Life", "Sports", "Supernatural", "Suspense", "Thriller"
        )
    }
    val animeFormats = remember { listOf("TV", "MOVIE", "ONA", "OVA", "SPECIAL", "MUSIC") }
    val mangaFormats = remember { listOf("MANGA", "NOVEL", "ONE_SHOT") }
    val yearPresets = remember { listOf(2026, 2025, 2024, 2023, 2022, 2020, 2015, 2010) }

    val libraryAnimeMalIds = remember(state.animeList) { state.animeList.map { it.malId }.toSet() }
    val libraryMangaMalIds = remember(state.mangaList) { state.mangaList.map { it.malId }.toSet() }

    // Automatic debounced live search as user types
    LaunchedEffect(searchInput, searchType) {
        val trimmed = searchInput.trim()
        if (trimmed.length >= 2 && trimmed != currentQuery) {
            delay(350)
            onSearch(trimmed, searchType)
        } else if (trimmed.isEmpty() && currentResults.isNotEmpty() && !hasActiveFilters) {
            onSearch("", searchType)
        }
    }

    LaunchedEffect(currentType) {
        if (searchType != currentType) {
            searchType = currentType
            tempGenres = currentGenres.toSet()
            tempYear = currentYear
            typedYearText = currentYear?.toString() ?: ""
            tempFormat = currentFormat
        }
    }

    val popularAnimeSuggestions = remember {
        listOf("Frieren", "Jujutsu Kaisen", "Solo Leveling", "One Piece", "Attack on Titan", "Demon Slayer", "Spy x Family", "Naruto")
    }
    val popularMangaSuggestions = remember {
        listOf("Berserk", "Chainsaw Man", "One Piece", "Oshi no Ko", "Tokyo Ghoul", "Vagabond", "Monster", "Jujutsu Kaisen")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Pencarian Media",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Cari judul anime & manga secara instan & akurat via AniList GraphQL",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Media Type Selector (Anime / Manga) with Smooth Sliding Indicator
        item {
            com.canim.app.ui.components.SmoothSegmentedSelector(
                options = listOf(MediaType.ANIME, MediaType.MANGA),
                selectedOption = searchType,
                onOptionSelected = { selected ->
                    if (searchType != selected) {
                        searchType = selected
                        tempGenres = emptySet()
                        tempYear = null
                        typedYearText = ""
                        tempFormat = null
                        onResetFilters()
                        onSearch(searchInput.trim(), selected)
                    }
                },
                labelProvider = { type -> if (type == MediaType.ANIME) "Cari Anime" else "Cari Manga" },
                highlightColor = if (searchType == MediaType.ANIME) AccentBlue else MangaAccentDarkBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            )
        }

        // Search Bar with Tactile Clear & Submit (Harmonized 44.dp height)
        item {
            var isSearchFocused by remember { mutableStateOf(false) }
            val activeColor = if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact Search Input Box (Height: 44.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(
                            width = 1.dp,
                            color = if (isSearchFocused) activeColor else CardBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = activeColor,
                            modifier = Modifier.size(18.dp)
                        )

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchInput.isEmpty()) {
                                Text(
                                    text = if (searchType == MediaType.ANIME) "Ketik judul anime..." else "Ketik judul manga...",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchInput,
                                onValueChange = { searchInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_input_field")
                                    .onFocusChanged { isSearchFocused = it.isFocused },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                ),
                                cursorBrush = SolidColor(activeColor),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    if (searchInput.isNotBlank() || hasActiveFilters) {
                                        onSearch(searchInput.trim(), searchType)
                                    }
                                })
                            )
                        }

                        if (searchInput.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        searchInput = ""
                                        onSearch("", searchType)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hapus",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Filter Button (Height/Size: 44.dp)
                IconButton(
                    onClick = {
                        searchType = currentType
                        tempGenres = currentGenres.toSet()
                        tempYear = currentYear
                        typedYearText = currentYear?.toString() ?: ""
                        tempFormat = currentFormat
                        showFilterSheet = true
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hasActiveFilters) activeColor else CardBg)
                        .border(
                            1.dp,
                            if (hasActiveFilters) activeColor else CardBorder,
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter",
                        tint = if (hasActiveFilters) Color.White else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Cari Button (Height: 44.dp)
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (searchInput.isNotBlank() || hasActiveFilters) {
                            onSearch(searchInput.trim(), searchType)
                        }
                    },
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("search_submit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Text(text = "Cari", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }



        // Active filters chip row
        if (hasActiveFilters) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Surface(
                            onClick = {
                                onResetFilters()
                                onSearch(searchInput.trim(), searchType)
                            },
                            color = StatusDroppedColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Reset", tint = StatusDroppedColor, modifier = Modifier.size(12.dp))
                                Text("Reset", color = StatusDroppedColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (currentFormat != null) {
                        item {
                            Surface(
                                color = CardBg,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    text = currentFormat,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    if (currentYear != null) {
                        item {
                            Surface(
                                color = CardBg,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    text = "${currentYear}",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    items(currentGenres) { g ->
                        Surface(
                            color = CardBg,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(
                                text = g,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Suggestions when query is empty
        if (currentResults.isEmpty() && !currentIsSearching) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Rekomendasi Pencarian Populer:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val suggestions = if (searchType == MediaType.ANIME) popularAnimeSuggestions else popularMangaSuggestions

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(suggestions) { keyword ->
                            SuggestionChip(
                                onClick = {
                                    searchInput = keyword
                                    onSearch(keyword, searchType)
                                },
                                label = { Text(keyword, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = CardBg,
                                    labelColor = TextPrimary
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = CardBorder
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = CardBorder,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Ketik minimal 2 karakter untuk mencari katalog",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Loading State
        if (currentIsSearching) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = AccentBlue)
                        Text(
                            text = "Mencari data di katalog AniList...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Search Results List (Task 2.5: Clicking Card opens Detail Dialog)
        if (!currentIsSearching && currentResults.isNotEmpty()) {
            item {
                Text(
                    text = "Hasil Pencarian (${currentResults.size}) - Ketuk judul untuk detail lengkap",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(
                currentResults,
                key = { "${it.type}_${it.malId}_${it.anilistId}" },
                contentType = { "search_card" }
            ) { item ->
                val isInLibrary = if (item.type == MediaType.MANGA) {
                    libraryMangaMalIds.contains(item.malId)
                } else {
                    libraryAnimeMalIds.contains(item.malId)
                }
                SearchResultCard(
                    item = item,
                    isInLibrary = isInLibrary,
                    onClick = { onSelectItem(item, item.type) },
                    onAddClick = { itemToAdd = item },
                    onEditClick = { itemToEdit = item }
                )
            }
        }
    }

    // Modal Status Selector Dialog
    if (itemToAdd != null) {
        val target = itemToAdd!!
        val isAnime = target.type == MediaType.ANIME

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
            onDismissRequest = { itemToAdd = null },
            containerColor = CardElevated,
            title = {
                Text(
                    text = "Tambah ke Koleksi",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = target.title,
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
                                onAddMedia(target, statusOption)
                                itemToAdd = null
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
                TextButton(onClick = { itemToAdd = null }) {
                    Text("Batal", color = TextMuted)
                }
            }
        )
    }

    // Quick Status Editor Dialog for items already in library (Task A2)
    if (itemToEdit != null) {
        val target = itemToEdit!!
        val isAnime = target.type == MediaType.ANIME
        val currentStatus = if (isAnime) {
            state.animeList.find { it.malId == target.malId }?.status
        } else {
            state.mangaList.find { it.malId == target.malId }?.status
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
            onDismissRequest = { itemToEdit = null },
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
                        text = target.title,
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
                                    state.animeList.find { it.malId == target.malId }?.let { entity ->
                                        onSaveAnime(entity.withStatus(statusOption.apiValue))
                                    }
                                } else {
                                    state.mangaList.find { it.malId == target.malId }?.let { entity ->
                                        onSaveManga(entity.withStatus(statusOption.apiValue))
                                    }
                                }
                                itemToEdit = null
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
                    val editItem = itemToEdit
                    itemToEdit = null
                    if (editItem != null) {
                        onSelectItem(editItem, editItem.type)
                    }
                }) {
                    Text("Detail Lengkap...", color = AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEdit = null }) {
                    Text("Batal", color = TextMuted)
                }
            }
        )
    }

    // Modal Filter Sheet - Compact & Space-Efficient
    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = CardElevated,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .padding(bottom = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Compact Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Filter Pencarian",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val activeFilterCount = tempGenres.size + (if (tempYear != null || typedYearText.isNotEmpty()) 1 else 0) + (if (tempFormat != null) 1 else 0)
                        if (activeFilterCount > 0) {
                            Surface(
                                color = (if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "$activeFilterCount aktif",
                                    color = if (searchType == MediaType.MANGA) Color(0xFF90CAF9) else AccentBlueLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (tempGenres.isNotEmpty() || tempYear != null || tempFormat != null || typedYearText.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                tempGenres = emptySet()
                                tempYear = null
                                typedYearText = ""
                                tempFormat = null
                                onResetFilters()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Reset", color = StatusDroppedColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Media Type Selector with Smooth Sliding Indicator (Compact 34dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Tipe Media", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    com.canim.app.ui.components.SmoothSegmentedSelector(
                        options = listOf(MediaType.ANIME, MediaType.MANGA),
                        selectedOption = searchType,
                        onOptionSelected = { selected ->
                            if (searchType != selected) {
                                searchType = selected
                                tempGenres = emptySet()
                                tempYear = null
                                typedYearText = ""
                                tempFormat = null
                                onResetFilters()
                            }
                        },
                        labelProvider = { if (it == MediaType.ANIME) "Anime" else "Manga" },
                        highlightColor = if (searchType == MediaType.ANIME) AccentBlue else MangaAccentDarkBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    )
                }

                // Format Selector (Compact Horizontal Row)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Format", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (tempFormat != null) {
                            Text(
                                text = "Dipilih: $tempFormat",
                                color = if (searchType == MediaType.MANGA) Color(0xFF90CAF9) else AccentBlueLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    val formats = if (searchType == MediaType.ANIME) animeFormats else mangaFormats
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(formats) { fmt ->
                            val isSelected = tempFormat == fmt
                            Surface(
                                onClick = { tempFormat = if (isSelected) null else fmt },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) (if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue) else CardBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color.Transparent else CardBorder),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = fmt,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Year Selector (Compact Presets + Direct Input)
                val minYear = if (searchType == MediaType.ANIME) 1917 else 1874
                val maxYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 2

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Tahun Rilis", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (tempYear != null) {
                            Text(
                                text = "Dipilih: $tempYear",
                                color = if (searchType == MediaType.MANGA) Color(0xFF90CAF9) else AccentBlueLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Quick Presets Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(yearPresets) { yr ->
                            val isSelected = tempYear == yr
                            Surface(
                                onClick = {
                                    if (isSelected) {
                                        tempYear = null
                                        typedYearText = ""
                                    } else {
                                        tempYear = yr
                                        typedYearText = "$yr"
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) (if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue) else CardBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color.Transparent else CardBorder),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "$yr",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Direct typed year input (compact field)
                    OutlinedTextField(
                        value = typedYearText,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(4)
                            typedYearText = digits
                            val parsed = digits.toIntOrNull()
                            if (parsed != null && parsed in minYear..maxYear) {
                                tempYear = parsed
                            } else if (digits.isEmpty()) {
                                tempYear = null
                            }
                        },
                        placeholder = {
                            Text("Atau ketik tahun manual ($minYear - $maxYear)...", fontSize = 11.sp, color = TextMuted)
                        },
                        trailingIcon = {
                            if (typedYearText.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        typedYearText = ""
                                        tempYear = null
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus Tahun", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue,
                            focusedContainerColor = CardBg,
                            unfocusedContainerColor = CardBg
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Genres Multi-Select (Compact Grid)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Genre (${tempGenres.size} dipilih)",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (tempGenres.isNotEmpty()) {
                            Text(
                                text = "Bersihkan",
                                color = StatusDroppedColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { tempGenres = emptySet() }
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        allGenres.forEach { genre ->
                            val isSelected = tempGenres.contains(genre)
                            Surface(
                                onClick = {
                                    tempGenres = if (isSelected) tempGenres - genre else tempGenres + genre
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) (if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue) else CardBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color.Transparent else CardBorder),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 7.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Done / Apply Button (Compact 42dp)
                Button(
                    onClick = {
                        val parsed = typedYearText.toIntOrNull()
                        val finalYear = if (parsed != null && parsed in minYear..maxYear) parsed else tempYear
                        if (searchType != currentType) {
                            onSearch(searchInput.trim(), searchType)
                        }
                        onApplyFilters(tempGenres.toList(), finalYear, tempFormat)
                        showFilterSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Terapkan Filter", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SearchTabButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AccentBlue else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun SearchResultCard(
    item: MediaItem,
    isInLibrary: Boolean = false,
    onClick: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("search_card_${item.malId}"),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
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
                    .clip(RoundedCornerShape(8.dp))
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
                    maxLines = 1,
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

                    if (item.year != null) {
                        Text(
                            text = "${item.year}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    if (item.format != null) {
                        Text(
                            text = item.format,
                            color = AccentBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
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

                if (!item.synopsis.isNullOrBlank()) {
                    Text(
                        text = item.synopsis,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }

            IconButton(
                onClick = if (isInLibrary) onEditClick else onAddClick,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isInLibrary) AccentGreen.copy(alpha = 0.16f) else CardElevated)
                    .size(36.dp)
                    .testTag(if (isInLibrary) "search_edit_btn_${item.malId}" else "search_add_btn_${item.malId}")
            ) {
                Icon(
                    imageVector = if (isInLibrary) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = if (isInLibrary) "Ubah Status" else "Tambah ke Koleksi",
                    tint = if (isInLibrary) AccentGreen else AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
