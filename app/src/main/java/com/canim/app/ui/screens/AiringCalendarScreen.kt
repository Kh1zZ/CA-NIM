package com.canim.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.canim.app.data.model.AiringAnimeItem
import com.canim.app.data.model.MediaType
import com.canim.app.ui.components.CanimPullToRefreshLayout
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.calendar.AiringCalendarUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiringCalendarScreen(
    state: AiringCalendarUiState,
    watchingMalIds: Set<Int>,
    onSelectDay: (DayOfWeek) -> Unit,
    onToggleFilterOnlyWatching: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (Any, MediaType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedItems = remember(state.selectedDay, state.weekSchedule, state.filterOnlyWatching, watchingMalIds) {
        state.currentDayItems(watchingMalIds)
    }

    val daysOfWeek = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )

    val today = remember { LocalDate.now().dayOfWeek }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("airing_calendar_screen"),
        containerColor = BlackBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Jadwal Tayang Mingguan",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Waktu Indonesia Barat (WIB)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
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
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat Ulang",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BlackBg
                )
            )
        }
    ) { innerPadding ->
        CanimPullToRefreshLayout(
            isRefreshing = state.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Continuity Day Selector with Smooth Sliding Indicator (2 Rows)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Row 1: Senin - Kamis
                val row1Days = daysOfWeek.take(4)
                val row1SelectedIndex = row1Days.indexOf(state.selectedDay)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    val segWidth = maxWidth / row1Days.size
                    if (row1SelectedIndex >= 0) {
                        val offset1 by androidx.compose.animation.core.animateDpAsState(
                            targetValue = segWidth * row1SelectedIndex,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.82f,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            ),
                            label = "cal_day_offset_row1"
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = offset1)
                                .width(segWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentBlue)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row1Days.forEach { day ->
                            val isSelected = day == state.selectedDay
                            val isToday = day == today
                            val count = state.countForDay(day, watchingMalIds)
                            DayPill(
                                day = day,
                                isSelected = isSelected,
                                isToday = isToday,
                                count = count,
                                onClick = { onSelectDay(day) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

                // Row 2: Jumat - Minggu
                val row2Days = daysOfWeek.drop(4)
                val row2SelectedIndex = row2Days.indexOf(state.selectedDay)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    val segWidth2 = maxWidth / row2Days.size
                    if (row2SelectedIndex >= 0) {
                        val offset2 by androidx.compose.animation.core.animateDpAsState(
                            targetValue = segWidth2 * row2SelectedIndex,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.82f,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            ),
                            label = "cal_day_offset_row2"
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = offset2)
                                .width(segWidth2)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentBlue)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row2Days.forEach { day ->
                            val isSelected = day == state.selectedDay
                            val isToday = day == today
                            val count = state.countForDay(day, watchingMalIds)
                            DayPill(
                                day = day,
                                isSelected = isSelected,
                                isToday = isToday,
                                count = count,
                                onClick = { onSelectDay(day) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }

            // Watching Filter Toggle Chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.filterOnlyWatching,
                    onClick = onToggleFilterOnlyWatching,
                    label = {
                        Text(
                            text = "Hanya yang Ditonton (${watchingMalIds.size})",
                            fontSize = 12.sp,
                            fontWeight = if (state.filterOnlyWatching) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = if (state.filterOnlyWatching) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue,
                        selectedLabelColor = Color.White,
                        containerColor = CardBg,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = state.filterOnlyWatching,
                        borderColor = CardBorderSubtle,
                        selectedBorderColor = AccentBlue
                    ),
                    modifier = Modifier.testTag("calendar_watching_filter_chip")
                )

                Text(
                    text = "${displayedItems.size} anime tayang",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            // Anime List or Loading / Empty States
            if (state.isLoading && displayedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else if (displayedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (state.filterOnlyWatching) {
                                "Tidak ada anime yang sedang ditonton tayang di hari ini."
                            } else {
                                "Tidak ada jadwal tayang untuk hari ini."
                            },
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedItems, key = { it.id }) { anime ->
                        val isWatching = anime.malId != null && anime.malId in watchingMalIds
                        AiringAnimeCard(
                            item = anime,
                            isWatching = isWatching,
                            onClick = {
                                val mediaItem = com.canim.app.data.model.MediaItem(
                                    malId = anime.malId,
                                    anilistId = anime.anilistId ?: anime.id.toIntOrNull(),
                                    title = anime.title,
                                    titleEnglish = anime.titleEnglish,
                                    imageUrl = anime.imageUrl,
                                    type = MediaType.ANIME,
                                    score = anime.score,
                                    episodes = anime.episodes,
                                    genres = anime.genres,
                                    studio = anime.studio
                                )
                                onOpenDetail(mediaItem, MediaType.ANIME)
                            }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun DayPill(
    day: DayOfWeek,
    isSelected: Boolean,
    isToday: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayNameIndo = when (day) {
        DayOfWeek.MONDAY -> "Senin"
        DayOfWeek.TUESDAY -> "Selasa"
        DayOfWeek.WEDNESDAY -> "Rabu"
        DayOfWeek.THURSDAY -> "Kamis"
        DayOfWeek.FRIDAY -> "Jumat"
        DayOfWeek.SATURDAY -> "Sabtu"
        DayOfWeek.SUNDAY -> "Minggu"
    }

    val containerBg = when {
        isSelected -> Color.Transparent
        isToday -> AccentBlue.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> Color.White
        isToday -> AccentBlueLight
        else -> TextSecondary
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayNameIndo,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "($count)",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextMuted
            )
        }
    }
}

@Composable
private fun AiringAnimeCard(
    item: AiringAnimeItem,
    isWatching: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, if (isWatching) AccentBlue.copy(alpha = 0.4f) else CardBorderSubtle, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.canim.app.ui.components.CanimAsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 56.dp, height = 76.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardElevated)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (item.score != null && item.score > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = item.scoreFormatted,
                            color = StarGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Airing Episode + Airing Time Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Episode Badge
                item.currentAiringEpisode?.let { ep ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentBlue.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Ep $ep",
                            color = AccentBlueLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Airing Time
                item.airingTimeFormatted?.let { time ->
                    Text(
                        text = "≈ $time WIB",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                // Watching in Library indicator
                if (isWatching) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(StatusWatchingColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Ditonton",
                            color = StatusWatchingColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Genres or Studio
            val extraInfo = item.studio ?: item.genres.take(2).joinToString(" • ")
            if (extraInfo.isNotBlank()) {
                Text(
                    text = extraInfo,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
