package com.canim.app.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.canim.app.data.local.AnimeEntity
import com.canim.app.data.local.MangaEntity
import com.canim.app.data.model.MediaType
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.CanimUiState

@Composable
fun LibraryScreen(
    state: CanimUiState,
    onSelectMediaType: (MediaType) -> Unit,
    onSelectStatusFilter: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectSort: (String) -> Unit = {},
    onQuickAddAnime: (String) -> Unit,
    onQuickDecrementAnime: (String) -> Unit = {},
    onQuickAddManga: (String) -> Unit,
    onQuickDecrementManga: (String) -> Unit = {},
    onSelectItem: (Any, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val isAnime = state.libraryFilterType == MediaType.ANIME

    // Optimized Filtering & Sorting with remember to avoid re-sorting on every frame (Task B2)
    val filteredAnime = remember(state.animeList, state.libraryStatusFilter, state.librarySearchQuery, state.librarySortBy) {
        val filtered = state.animeList
            .filter { anime ->
                (state.libraryStatusFilter == null || anime.status == state.libraryStatusFilter) &&
                (state.librarySearchQuery.isBlank() || anime.title.contains(state.librarySearchQuery, ignoreCase = true))
            }
        when (state.librarySortBy) {
            "title" -> filtered.sortedBy { it.title.lowercase() }
            "score" -> filtered.sortedByDescending { it.score }
            "progress" -> filtered.sortedByDescending { it.progress }
            else -> filtered.sortedByDescending { it.updatedAt }
        }
    }

    val filteredManga = remember(state.mangaList, state.libraryStatusFilter, state.librarySearchQuery, state.librarySortBy) {
        val filtered = state.mangaList
            .filter { manga ->
                (state.libraryStatusFilter == null || manga.status == state.libraryStatusFilter) &&
                (state.librarySearchQuery.isBlank() || manga.title.contains(state.librarySearchQuery, ignoreCase = true))
            }
        when (state.librarySortBy) {
            "title" -> filtered.sortedBy { it.title.lowercase() }
            "score" -> filtered.sortedByDescending { it.score }
            "progress" -> filtered.sortedByDescending { it.progressChapters }
            else -> filtered.sortedByDescending { it.updatedAt }
        }
    }

    val statuses = if (isAnime) {
        listOf(
            null to "Semua",
            "watching" to "Ditonton",
            "completed" to "Selesai",
            "on_hold" to "Ditunda",
            "dropped" to "Ditinggalkan",
            "plan_to_watch" to "Rencana"
        )
    } else {
        listOf(
            null to "Semua",
            "reading" to "Dibaca",
            "completed" to "Selesai",
            "on_hold" to "Ditunda",
            "dropped" to "Ditinggalkan",
            "plan_to_read" to "Rencana"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Media Type Selector (Anime vs Manga)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .padding(4.dp)
            ) {
                TabButton(
                    selected = isAnime,
                    text = "Anime (${state.animeList.size})",
                    onClick = { onSelectMediaType(MediaType.ANIME) },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    selected = !isAnime,
                    text = "Manga (${state.mangaList.size})",
                    onClick = { onSelectMediaType(MediaType.MANGA) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Search Bar in Library
        item {
            OutlinedTextField(
                value = state.librarySearchQuery,
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
                    if (state.librarySearchQuery.isNotBlank()) {
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

        // Status Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(statuses) { (statusVal, label) ->
                    val isSelected = state.libraryStatusFilter == statusVal
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectStatusFilter(statusVal) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            selectedLabelColor = Color.White,
                            containerColor = CardBg,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = CardBorder,
                            selectedBorderColor = AccentBlue
                        )
                    )
                }
            }
        }

        // Sort By Chips (Task B2)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(sortOptions) { (key, label) ->
                        val isSelected = state.librarySortBy == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AccentBlue.copy(alpha = 0.2f) else CardBg)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentBlue else CardBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectSort(key) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = if (isSelected) AccentBlueLight else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
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
                        onQuickAdd = { onQuickAddAnime(anime.id) },
                        onQuickDecrement = { onQuickDecrementAnime(anime.id) },
                        onClick = { onSelectItem(anime, MediaType.ANIME) }
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
                        onQuickAdd = { onQuickAddManga(manga.id) },
                        onQuickDecrement = { onQuickDecrementManga(manga.id) },
                        onClick = { onSelectItem(manga, MediaType.MANGA) }
                    )
                }
            }
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
    anime: AnimeEntity,
    onQuickAdd: () -> Unit,
    onQuickDecrement: () -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("anime_card_${anime.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = remember(anime.imageUrl) {
                    ImageRequest.Builder(context)
                        .data(anime.imageUrl)
                        .size(160, 220)
                        .crossfade(false)
                        .build()
                },
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            text = "★ ${anime.score}",
                            color = StarGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                StatusPill(status = anime.status)

                val progressFrac = if (anime.totalEpisodes > 0) {
                    (anime.progress.toFloat() / anime.totalEpisodes).coerceIn(0f, 1f)
                } else 0.5f

                LinearProgressIndicator(
                    progress = { progressFrac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = AccentGreen,
                    trackColor = CardElevated
                )

                Text(
                    text = "${anime.progress}/${if (anime.totalEpisodes > 0) anime.totalEpisodes else "?"} ep",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onQuickDecrement,
                    enabled = anime.progress > 0,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("anime_decrement_btn_${anime.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CardElevated,
                        contentColor = AccentGreen,
                        disabledContainerColor = CardElevated.copy(alpha = 0.4f),
                        disabledContentColor = TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Kurangi Progres",
                        modifier = Modifier.size(16.dp)
                    )
                }

                FilledIconButton(
                    onClick = onQuickAdd,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("anime_increment_btn_${anime.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CardElevated,
                        contentColor = AccentGreen
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah Progres",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MangaLibraryCard(
    manga: MangaEntity,
    onQuickAdd: () -> Unit,
    onQuickDecrement: () -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("manga_card_${manga.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = remember(manga.imageUrl) {
                    ImageRequest.Builder(context)
                        .data(manga.imageUrl)
                        .size(160, 220)
                        .crossfade(false)
                        .build()
                },
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            text = "★ ${manga.score}",
                            color = StarGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                StatusPill(status = manga.status)

                val progressFrac = if (manga.totalChapters > 0) {
                    (manga.progressChapters.toFloat() / manga.totalChapters).coerceIn(0f, 1f)
                } else 0.5f

                LinearProgressIndicator(
                    progress = { progressFrac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF38BDF8),
                    trackColor = CardElevated
                )

                Text(
                    text = "Ch. ${manga.progressChapters}${if (manga.totalChapters > 0) "/${manga.totalChapters}" else ""}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onQuickDecrement,
                    enabled = manga.progressChapters > 0,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("manga_decrement_btn_${manga.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CardElevated,
                        contentColor = Color(0xFF38BDF8),
                        disabledContainerColor = CardElevated.copy(alpha = 0.4f),
                        disabledContentColor = TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Kurangi Progres",
                        modifier = Modifier.size(16.dp)
                    )
                }

                FilledIconButton(
                    onClick = onQuickAdd,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("manga_increment_btn_${manga.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CardElevated,
                        contentColor = Color(0xFF38BDF8)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah Progres",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
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
            .clip(RoundedCornerShape(4.dp))
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
