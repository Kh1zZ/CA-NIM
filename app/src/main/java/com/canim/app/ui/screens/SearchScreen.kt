package com.canim.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
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
    modifier: Modifier = Modifier
) {
    var searchInput by remember { mutableStateOf(state.searchQuery) }
    var searchType by remember { mutableStateOf(state.searchType) }
    val focusManager = LocalFocusManager.current
    var itemToAdd by remember { mutableStateOf<MediaItem?>(null) }
    var itemToEdit by remember { mutableStateOf<MediaItem?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var tempGenres by remember(state.searchGenres) { mutableStateOf(state.searchGenres.toSet()) }
    var tempYear by remember(state.searchYear) { mutableStateOf(state.searchYear) }
    var tempFormat by remember(state.searchFormat) { mutableStateOf(state.searchFormat) }
    val hasActiveFilters = state.searchGenres.isNotEmpty() || state.searchYear != null || state.searchFormat != null

    val allGenres = remember {
        listOf(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy",
            "Horror", "Mystery", "Romance", "Sci-Fi", "Slice of Life",
            "Sports", "Supernatural", "Thriller", "Mecha", "Music",
            "Psychological", "Isekai", "Shounen", "Shoujo", "Seinen"
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
        if (trimmed.length >= 2 && trimmed != state.searchQuery) {
            delay(350)
            onSearch(trimmed, searchType)
        } else if (trimmed.isEmpty() && state.searchResults.isNotEmpty()) {
            onSearch("", searchType)
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

        // Media Type Selector (Anime / Manga)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .padding(4.dp)
            ) {
                SearchTabButton(
                    selected = searchType == MediaType.ANIME,
                    text = "Cari Anime",
                    onClick = {
                        searchType = MediaType.ANIME
                        if (searchInput.isNotBlank()) onSearch(searchInput.trim(), MediaType.ANIME)
                    },
                    modifier = Modifier.weight(1f)
                )
                SearchTabButton(
                    selected = searchType == MediaType.MANGA,
                    text = "Cari Manga",
                    onClick = {
                        searchType = MediaType.MANGA
                        if (searchInput.isNotBlank()) onSearch(searchInput.trim(), MediaType.MANGA)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Search Bar with Tactile Clear & Submit
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_input_field"),
                    placeholder = {
                        Text(
                            text = if (searchType == MediaType.ANIME) "Ketik judul anime (misal: Frieren)..." else "Ketik judul manga (misal: Berserk)...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = AccentBlue
                        )
                    },
                    trailingIcon = {
                        if (searchInput.isNotEmpty()) {
                            IconButton(onClick = {
                                searchInput = ""
                                onSearch("", searchType)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hapus",
                                    tint = TextMuted
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        if (searchInput.isNotBlank()) {
                            onSearch(searchInput.trim(), searchType)
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Filter Button
                IconButton(
                    onClick = {
                        tempGenres = state.searchGenres.toSet()
                        tempYear = state.searchYear
                        tempFormat = state.searchFormat
                        showFilterSheet = true
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hasActiveFilters) (if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue) else CardBg)
                        .border(1.dp, if (hasActiveFilters) (if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue) else CardBorder, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter",
                        tint = if (hasActiveFilters) Color.White else TextPrimary
                    )
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (searchInput.isNotBlank() || hasActiveFilters) {
                            onSearch(searchInput.trim(), searchType)
                        }
                    },
                    modifier = Modifier.testTag("search_submit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
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
                    if (state.searchFormat != null) {
                        item {
                            Surface(
                                color = CardBg,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    text = state.searchFormat,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    if (state.searchYear != null) {
                        item {
                            Surface(
                                color = CardBg,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    text = "${state.searchYear}",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    items(state.searchGenres) { g ->
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
        if (state.searchResults.isEmpty() && !state.isSearching) {
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
        if (state.isSearching) {
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
        if (!state.isSearching && state.searchResults.isNotEmpty()) {
            item {
                Text(
                    text = "Hasil Pencarian (${state.searchResults.size}) - Ketuk judul untuk detail lengkap",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(
                state.searchResults,
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

    // Modal Filter Sheet (Bagian B.8: Type, 20 genres multi-select, Year, Format, Reset, Done)
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
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter Pencarian",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (tempGenres.isNotEmpty() || tempYear != null || tempFormat != null) {
                        TextButton(
                            onClick = {
                                tempGenres = emptySet()
                                tempYear = null
                                tempFormat = null
                                onResetFilters()
                            }
                        ) {
                            Text("Reset", color = StatusDroppedColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                // Media Type Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Tipe Media", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardBg)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (searchType == MediaType.ANIME) AccentBlue else Color.Transparent)
                                .clickable {
                                    searchType = MediaType.ANIME
                                    tempFormat = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Anime",
                                color = if (searchType == MediaType.ANIME) Color.White else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (searchType == MediaType.MANGA) MangaAccentDarkBlue else Color.Transparent)
                                .clickable {
                                    searchType = MediaType.MANGA
                                    tempFormat = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Manga",
                                color = if (searchType == MediaType.MANGA) Color.White else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Format Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Format", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val formats = if (searchType == MediaType.ANIME) animeFormats else mangaFormats
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        formats.forEach { fmt ->
                            val isSelected = tempFormat == fmt
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    tempFormat = if (isSelected) null else fmt
                                },
                                label = { Text(fmt, fontSize = 11.sp) },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Year Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Tahun Rilis", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        yearPresets.forEach { yr ->
                            val isSelected = tempYear == yr
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    tempYear = if (isSelected) null else yr
                                },
                                label = { Text("$yr", fontSize = 11.sp) },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // 20 Genres Multi-Select
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Genre (${tempGenres.size} dipilih)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allGenres.forEach { genre ->
                            val isSelected = tempGenres.contains(genre)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    tempGenres = if (isSelected) tempGenres - genre else tempGenres + genre
                                },
                                label = { Text(genre, fontSize = 11.sp) },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Bottom Done / Apply Button
                Button(
                    onClick = {
                        onApplyFilters(tempGenres.toList(), tempYear, tempFormat)
                        showFilterSheet = false
                        onSearch(searchInput.trim(), searchType)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (searchType == MediaType.MANGA) MangaAccentDarkBlue else AccentBlue
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Terapkan Filter", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
