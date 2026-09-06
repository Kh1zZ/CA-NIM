package com.canim.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.StudioBioInfo
import com.canim.app.data.model.StudioFilmographySort
import com.canim.app.data.model.StudioYearGroup
import com.canim.app.data.model.groupAndSortFilmography
import com.canim.app.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun StudioFilmographyScreen(
    studioId: Int,
    studioName: String,
    items: List<MediaItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenDetail: (MediaItem, MediaType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bioInfo: StudioBioInfo? = null,
    sort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC,
    onSortChanged: (StudioFilmographySort) -> Unit = {},
    totalEntries: Int = 0
) {
    val gridState = rememberLazyGridState()

    // Robust pagination: detect when user scrolls near the bottom without re-trigger loops
    LaunchedEffect(gridState, canLoadMore, isLoading, isLoadingMore) {
        snapshotFlow {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
        .distinctUntilChanged()
        .collect { nearBottom ->
            if (nearBottom && canLoadMore && !isLoading && !isLoadingMore) {
                onLoadMore()
            }
        }
    }

    // Memoized grouping and sorting to completely avoid recomposition overhead
    val groupedItems = remember(items, sort) {
        groupAndSortFilmography(items, sort)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBg)
    ) {
        if (isLoading && items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentBlue)
            }
        } else if (!isLoading && items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Karya studio tidak ditemukan",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val dynamicGridTopPadding = statusBarTop + 42.dp + 16.dp

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(
                    top = dynamicGridTopPadding,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 32.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Header section: Studio Title & Hero Banner (Tugas 3c)
                item(span = { GridItemSpan(maxLineSpan) }, key = "studio_header") {
                    val heroCover = bioInfo?.coverUrl ?: items.firstOrNull()?.imageUrlHd ?: items.firstOrNull()?.imageUrl
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            if (!heroCover.isNullOrBlank()) {
                                AsyncImage(
                                    model = heroCover,
                                    contentDescription = studioName,
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
                                                Color.Black.copy(alpha = 0.45f),
                                                Color.Black.copy(alpha = 0.90f)
                                            )
                                        )
                                    )
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Studio Logo / Monogram
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CardBg)
                                        .border(1.dp, AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!bioInfo?.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = bioInfo!!.logoUrl,
                                            contentDescription = studioName,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    } else {
                                        Text(
                                            text = studioName.take(1).uppercase(),
                                            color = AccentBlue,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = studioName,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val countLabel = when {
                                        totalEntries >= 500 -> "500+ judul"
                                        totalEntries > 0 -> "$totalEntries judul"
                                        items.isNotEmpty() -> "${items.size} judul"
                                        else -> ""
                                    }
                                    Text(
                                        text = if (countLabel.isNotEmpty()) "Katalog Produksi • $countLabel" else "Katalog Produksi",
                                        fontSize = 12.sp,
                                        color = AccentBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Studio Bio Card (Tugas 1)
                item(span = { GridItemSpan(maxLineSpan) }, key = "studio_bio_card") {
                    StudioBioCard(
                        bioInfo = bioInfo,
                        totalEntries = totalEntries
                    )
                }

                // Minimalist Sorting Controls (Tugas 3)
                item(span = { GridItemSpan(maxLineSpan) }, key = "studio_sort_bar") {
                    StudioSortBar(
                        currentSort = sort,
                        onSortChanged = onSortChanged
                    )
                }

                // Grouped Filmography Sections (Tugas 2 & 3)
                groupedItems.forEach { group ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "year_header_${group.header}") {
                        YearSectionHeader(header = group.header, count = group.items.size)
                    }

                    itemsIndexed(
                        items = group.items,
                        key = { index, media -> "${media.malId ?: media.anilistId ?: media.title}_${group.header}_$index" }
                    ) { _, media ->
                        StudioMediaCard(
                            item = media,
                            onClick = { onOpenDetail(media, MediaType.ANIME) }
                        )
                    }
                }

                // Loading more indicator
                if (isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "studio_loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                                color = AccentBlue
                            )
                        }
                    }
                }
            }
        }

        // Top Gradient Scrim for FAB
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.75f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Persistent Back Floating Action Button (Top-Left)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StudioBioCard(
    bioInfo: StudioBioInfo?,
    totalEntries: Int,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Quick Facts Badges (Horizontal scroll / row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Founded Year
                bioInfo?.foundedYear?.let { year ->
                    FactBadge(
                        icon = Icons.Default.CalendarToday,
                        text = "Est. $year"
                    )
                }

                // Country
                FactBadge(
                    icon = Icons.Default.LocationOn,
                    text = bioInfo?.country ?: "Jepang"
                )

                // Total Anime
                val totalCount = bioInfo?.totalAnime ?: totalEntries
                if (totalCount > 0) {
                    FactBadge(
                        icon = Icons.Default.Tv,
                        text = "$totalCount Anime"
                    )
                }


            }

            // Narrative Bio with Expand/Collapse
            bioInfo?.bio?.takeIf { it.isNotBlank() }?.let { bioText ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = bioText,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isExpanded) "Lebih Sedikit" else "Selengkapnya...",
                        color = AccentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { isExpanded = !isExpanded }
                    )
                }
            }

            // Official Website Link
            bioInfo?.officialSite?.takeIf { it.isNotBlank() }?.let { siteUrl ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardElevated)
                        .clickable {
                            try { uriHandler.openUri(siteUrl) } catch (_: Exception) {}
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = siteUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                        color = AccentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FactBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconTint: Color = AccentBlue
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CardElevated,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StudioSortBar(
    currentSort: StudioFilmographySort,
    onSortChanged: (StudioFilmographySort) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "URUTKAN:",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = currentSort.label,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(CardElevated)
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            ) {
                StudioFilmographySort.entries.forEach { sortOption ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = sortOption.label,
                                color = if (currentSort == sortOption) AccentBlue else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (currentSort == sortOption) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSortChanged(sortOption)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun YearSectionHeader(header: String, count: Int) {
    val isTba = header.contains("TBA", ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isTba) StarGold else AccentBlue)
        )
        Text(
            text = header,
            color = if (isTba) StarGold else TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "($count)",
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun StudioMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(CardElevated)
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Rating Badge (Single Star)
                if (item.score != null && item.score > 0) {
                    val scoreDisplay = String.format("%.1f", item.score.toFloat())
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = StarGold,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = scoreDisplay,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Format badge (bottom start of poster)
                if (!item.format.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = item.format,
                            color = AccentBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.genres.take(2).joinToString(", "),
                        color = TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
