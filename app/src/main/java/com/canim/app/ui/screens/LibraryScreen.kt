package com.canim.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.UserMediaItem
import com.canim.app.ui.components.CanimPullToRefreshLayout
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.library.LibraryUiState

private val ItemCardShape = RoundedCornerShape(12.dp)
private val ItemImageShape = RoundedCornerShape(8.dp)
private val ProgressClipShape = RoundedCornerShape(2.dp)
private val PillShape = RoundedCornerShape(4.dp)
private val ItemBorderStroke = BorderStroke(1.dp, CardBorderSubtle)

@Composable
fun LibraryScreen(
    libraryState: LibraryUiState,
    onSelectMediaType: (MediaType) -> Unit,
    onSelectStatusFilter: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectSort: (String) -> Unit = {},
    onQuickAddAnime: (String) -> Unit,
    onQuickDecrementAnime: (String) -> Unit = {},
    onQuickAddManga: (String) -> Unit,
    onQuickDecrementManga: (String) -> Unit = {},
    onSelectItem: (Any, MediaType) -> Unit,
    onOpenCalendar: () -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentFilterType = libraryState.filterType
    val currentAnimeList = libraryState.animeList
    val currentMangaList = libraryState.mangaList
    val currentStatusFilter = libraryState.statusFilter
    val currentSearchQuery = libraryState.searchQuery
    val currentSortBy = libraryState.sortBy

    val isAnime = currentFilterType == MediaType.ANIME

    // Optimized Filtering & Sorting with remember to avoid re-sorting on every frame (Task B2)
    val filteredAnime = remember(currentAnimeList, currentStatusFilter, currentSearchQuery, currentSortBy) {
        val filtered = currentAnimeList
            .filter { anime ->
                (currentStatusFilter == null || anime.status == currentStatusFilter) &&
                (currentSearchQuery.isBlank() || anime.title.contains(currentSearchQuery, ignoreCase = true) || (anime.titleEnglish?.contains(currentSearchQuery, ignoreCase = true) == true))
            }
        when (currentSortBy) {
            "title" -> filtered.sortedBy { (it.title.takeIf { t -> t.isNotBlank() } ?: it.titleEnglish ?: "").lowercase() }
            "score" -> filtered.sortedByDescending { if (it.score > 0) it.score.toDouble() else (it.metadata.score ?: 0.0) }
            "progress" -> filtered.sortedByDescending { it.progress }
            else -> filtered.sortedByDescending { maxOf(it.updatedAt, it.tracking.updatedAt) }
        }
    }

    val filteredManga = remember(currentMangaList, currentStatusFilter, currentSearchQuery, currentSortBy) {
        val filtered = currentMangaList
            .filter { manga ->
                (currentStatusFilter == null || manga.status == currentStatusFilter) &&
                (currentSearchQuery.isBlank() || manga.title.contains(currentSearchQuery, ignoreCase = true) || (manga.titleEnglish?.contains(currentSearchQuery, ignoreCase = true) == true))
            }
        when (currentSortBy) {
            "title" -> filtered.sortedBy { (it.title.takeIf { t -> t.isNotBlank() } ?: it.titleEnglish ?: "").lowercase() }
            "score" -> filtered.sortedByDescending { if (it.score > 0) it.score.toDouble() else (it.metadata.score ?: 0.0) }
            "progress" -> filtered.sortedByDescending { it.progressChapters }
            else -> filtered.sortedByDescending { maxOf(it.updatedAt, it.tracking.updatedAt) }
        }
    }

    val onSelectAnimeItem: (UserMediaItem) -> Unit = remember(onSelectItem) {
        { anime -> onSelectItem(anime, MediaType.ANIME) }
    }
    val onSelectMangaItem: (UserMediaItem) -> Unit = remember(onSelectItem) {
        { manga -> onSelectItem(manga, MediaType.MANGA) }
    }

    val statuses = if (isAnime) {
        listOf(
            "watching" to "Ditonton",
            null to "Semua",
            "completed" to "Selesai",
            "on_hold" to "Ditunda",
            "dropped" to "Ditinggalkan",
            "plan_to_watch" to "Rencana"
        )
    } else {
        listOf(
            "reading" to "Dibaca",
            null to "Semua",
            "completed" to "Selesai",
            "on_hold" to "Ditunda",
            "dropped" to "Ditinggalkan",
            "plan_to_read" to "Rencana"
        )
    }

    CanimPullToRefreshLayout(
        isRefreshing = libraryState.isLoading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().background(BlackBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        // Media Type Selector (Anime vs Manga) with Smooth Sliding Indicator
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                com.canim.app.ui.components.SmoothSegmentedSelector(
                    options = listOf(MediaType.ANIME, MediaType.MANGA),
                    selectedOption = currentFilterType,
                    onOptionSelected = { onSelectMediaType(it) },
                    labelProvider = { type ->
                        if (type == MediaType.ANIME) "Anime (${currentAnimeList.size})" else "Manga (${currentMangaList.size})"
                    },
                    highlightColor = if (currentFilterType == MediaType.ANIME) AccentBlue else MangaAccentDarkBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .height(44.dp)
                )
            }
        }

        // Search Bar in Library
        item {
            OutlinedTextField(
                value = currentSearchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_field"),
                placeholder = {
                    Text(
                        text = if (isAnime) "Cari anime di library..." else "Cari manga di library...",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Cari",
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (currentSearchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hapus",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = CardBorder,
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )
        }

        // Status Filter Chips with Sliding Highlight
        item {
            com.canim.app.ui.components.SlidingPillSelector(
                items = statuses,
                selectedItem = statuses.firstOrNull { it.first == currentStatusFilter } ?: statuses.first(),
                onItemSelected = { onSelectStatusFilter(it.first) },
                labelProvider = { it.second },
                highlightColor = if (isAnime) AccentBlue else MangaAccentDarkBlue,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Sort By with Sliding Highlight
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Urutkan",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Urutkan:",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val sortOptions = listOf(
                    "updated" to "Terbaru",
                    "title" to "Judul",
                    "score" to "Skor",
                    "progress" to "Progres"
                )
                com.canim.app.ui.components.SlidingPillSelector(
                    items = sortOptions,
                    selectedItem = sortOptions.firstOrNull { it.first == currentSortBy } ?: sortOptions.first(),
                    onItemSelected = { onSelectSort(it.first) },
                    labelProvider = { it.second },
                    highlightColor = if (isAnime) AccentBlue.copy(alpha = 0.85f) else MangaAccentDarkBlue.copy(alpha = 0.85f),
                    cornerRadius = 14.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 4.dp,
                    fontSize = 11f,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        // Content List
        if (isAnime) {
            if (filteredAnime.isEmpty()) {
                item {
                    EmptyLibraryPlaceholder(isAnime = true)
                }
            } else {
                items(filteredAnime, key = { it.id }, contentType = { "anime_card" }) { anime ->
                    AnimeLibraryCard(
                        anime = anime,
                        onQuickAdd = onQuickAddAnime,
                        onQuickDecrement = onQuickDecrementAnime,
                        onClick = onSelectAnimeItem
                    )
                }
            }
        } else {
            if (filteredManga.isEmpty()) {
                item {
                    EmptyLibraryPlaceholder(isAnime = false)
                }
            } else {
                items(filteredManga, key = { it.id }, contentType = { "manga_card" }) { manga ->
                    MangaLibraryCard(
                        manga = manga,
                        onQuickAdd = onQuickAddManga,
                        onQuickDecrement = onQuickDecrementManga,
                        onClick = onSelectMangaItem
                    )
                }
            }
        }
    }

    // Calendar Floating Action Button (FAB)
    FloatingActionButton(
        onClick = onOpenCalendar,
        containerColor = AccentBlue,
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 84.dp)
            .testTag("library_calendar_fab")
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = "Jadwal Tayang Anime",
            modifier = Modifier.size(24.dp)
        )
    }
}
}

@Composable
fun TabButton(
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
fun AnimeLibraryCard(
    anime: UserMediaItem,
    onQuickAdd: (String) -> Unit,
    onQuickDecrement: (String) -> Unit = {},
    onClick: (UserMediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(anime) }
            .testTag("anime_card_${anime.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.canim.app.ui.components.CanimAsyncImage(
                model = anime.imageUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 60.dp, height = 84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardElevated)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = anime.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (anime.score > 0) {
                        Text(
                            text = anime.scoreFormatted,
                            color = StarGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                StatusPill(status = anime.status)

                // Sleek progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(ProgressClipShape)
                        .background(CardElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = anime.progressFrac)
                            .fillMaxHeight()
                            .background(AccentBlue)
                    )
                }

                val isMovie = anime.metadata.format?.equals("movie", ignoreCase = true) == true
                Text(
                    text = if (isMovie) {
                        if (anime.progress >= 1 || anime.status.equals("completed", ignoreCase = true)) "Ditonton (Movie)"
                        else "Belum Ditonton"
                    } else {
                        "${anime.progress}/${if (anime.totalEpisodes > 0) anime.totalEpisodes else "?"} ep"
                    },
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { onQuickDecrement(anime.id) },
                    enabled = anime.progress > 0,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("anime_decrement_btn_${anime.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CardElevated,
                        contentColor = AccentBlue,
                        disabledContainerColor = CardElevated.copy(alpha = 0.4f),
                        disabledContentColor = TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Kurangi Progres",
                        modifier = Modifier.size(15.dp)
                    )
                }

                val canIncrementAnime = !anime.status.equals("completed", ignoreCase = true) &&
                    (anime.totalEpisodes <= 0 || anime.progress < anime.totalEpisodes)

                FilledIconButton(
                    onClick = { onQuickAdd(anime.id) },
                    enabled = canIncrementAnime,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("anime_increment_btn_${anime.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CardElevated,
                        contentColor = AccentBlue,
                        disabledContainerColor = CardElevated.copy(alpha = 0.4f),
                        disabledContentColor = TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah Progres",
                        modifier = Modifier.size(15.dp)
                    )
                }
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

@Composable
fun MangaLibraryCard(
    manga: UserMediaItem,
    onQuickAdd: (String) -> Unit,
    onQuickDecrement: (String) -> Unit = {},
    onClick: (UserMediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(manga) }
            .testTag("manga_card_${manga.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.canim.app.ui.components.CanimAsyncImage(
                model = manga.imageUrl,
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 60.dp, height = 84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardElevated)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = manga.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (manga.score > 0) {
                        Text(
                            text = manga.scoreFormatted,
                            color = StarGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                StatusPill(status = manga.status)

                // Sleek progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(ProgressClipShape)
                        .background(CardElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = manga.progressChaptersFrac)
                            .fillMaxHeight()
                            .background(MangaAccentDarkBlue)
                    )
                }

                Text(
                    text = "Ch. ${manga.progressChapters}/${if (manga.totalChapters > 0) manga.totalChapters else "?"}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { onQuickDecrement(manga.id) },
                    enabled = manga.progressChapters > 0,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("manga_decrement_btn_${manga.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CardElevated,
                        contentColor = MangaAccentDarkBlue,
                        disabledContainerColor = CardElevated.copy(alpha = 0.4f),
                        disabledContentColor = TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Kurangi Progres",
                        modifier = Modifier.size(15.dp)
                    )
                }

                val canIncrementManga = !manga.status.equals("completed", ignoreCase = true) &&
                    (manga.totalChapters <= 0 || manga.progressChapters < manga.totalChapters)

                FilledIconButton(
                    onClick = { onQuickAdd(manga.id) },
                    enabled = canIncrementManga,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("manga_increment_btn_${manga.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CardElevated,
                        contentColor = MangaAccentDarkBlue,
                        disabledContainerColor = CardElevated.copy(alpha = 0.4f),
                        disabledContentColor = TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah Progres",
                        modifier = Modifier.size(15.dp)
                    )
                }
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

@Composable
fun StatusPill(status: String) {
    val (label, color) = when (status) {
        "watching" -> "Ditonton" to StatusWatchingColor
        "reading" -> "Dibaca" to Color(0xFF38BDF8)
        "completed" -> "Selesai" to StatusCompletedColor
        "on_hold" -> "Ditunda" to StatusOnHoldColor
        "dropped" -> "Ditinggalkan" to StatusDroppedColor
        "plan_to_watch", "plan_to_read" -> "Rencana" to StatusPlanColor
        else -> status to TextSecondary
    }

    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyLibraryPlaceholder(isAnime: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isAnime) "Tidak ada anime yang cocok dengan filter." else "Tidak ada manga yang cocok dengan filter.",
            color = TextMuted,
            fontSize = 13.sp
        )
    }
}
