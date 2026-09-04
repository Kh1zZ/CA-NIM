package com.canim.app.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.canim.app.R
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.UserMediaItem
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.CanimUiState

private fun isNetworkOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun DashboardScreen(
    state: CanimUiState,
    onQuickAddEpisode: (String) -> Unit,
    onQuickAddChapter: (String) -> Unit,
    onSelectItem: (Any, MediaType) -> Unit,
    onLoadDemoData: () -> Unit,
    onNavigateTab: (String) -> Unit,
    onLoginMal: () -> Unit = {},
    onSyncMal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val watchingAnime = state.watchingAnime
    val readingManga = state.readingManga
    val isDeviceOnline = remember { isNetworkOnline(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top App Bar: CA'NIM + Logo (Left padding) & Online/Offline status (Right) (Request 8)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Padding + Logo + CA'NIM
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_icon),
                        contentDescription = "Logo CA'NIM",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

                    Column {
                        Text(
                            text = "CA'NIM",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Tracker & Discovery",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Right: Online / Offline status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDeviceOnline) AccentGreen.copy(alpha = 0.15f) else CardElevated)
                        .border(
                            1.dp,
                            if (isDeviceOnline) AccentGreen.copy(alpha = 0.4f) else CardBorder,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isDeviceOnline) AccentGreen else TextMuted)
                        )
                        Icon(
                            imageVector = if (isDeviceOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = if (isDeviceOnline) "Online" else "Offline",
                            tint = if (isDeviceOnline) AccentGreen else TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isDeviceOnline) "Online" else "Offline",
                            color = if (isDeviceOnline) AccentGreen else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // MAL Sync Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (state.malUser.isLoggedIn) Color(0xFF2E51A2).copy(alpha = 0.6f) else CardBorder,
                        RoundedCornerShape(14.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.malUser.isLoggedIn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!state.malUser.pictureUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = state.malUser.pictureUrl,
                                    contentDescription = "MAL User Avatar",
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, AccentGreen, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2E51A2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = state.malUser.username.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = state.malUser.username,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "MAL Terhubung",
                                    color = AccentGreen,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onSyncMal,
                            modifier = Modifier
                                .wrapContentWidth()
                                .testTag("dashboard_sync_mal_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E51A2),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            enabled = !state.isSyncingMal
                        ) {
                            if (state.isSyncingMal) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync...", fontSize = 12.sp, maxLines = 1)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sinkron",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2E51A2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "MAL",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Column {
                                Text(
                                    text = "Hubungkan MyAnimeList",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sinkronisasi progress secara aman",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Button(
                            onClick = onLoginMal,
                            modifier = Modifier.testTag("dashboard_login_mal_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E51A2),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            enabled = !state.isExchangingToken
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Login MAL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Stats Overview & Detailed Breakdown (Request 10)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "STATISTIK & OVERVIEW LENGKAP",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Anime",
                        value = "${state.stats.totalAnime}",
                        subtitle = "${state.stats.episodesWatched} ep (${state.stats.daysWatched}h)",
                        icon = Icons.Default.Tv,
                        iconColor = AccentBlue
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Manga",
                        value = "${state.stats.totalManga}",
                        subtitle = "${state.stats.chaptersRead} ch",
                        icon = Icons.Default.AutoStories,
                        iconColor = MangaAccentDarkBlue
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Skor",
                        value = if (state.stats.meanScore > 0) "★ ${state.stats.meanScore}" else "-",
                        subtitle = "${state.stats.completedCount} selesai",
                        icon = Icons.Default.Star,
                        iconColor = StarGold
                    )
                }

                // Detailed Anime Breakdown Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Distribusi Status Anime",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${state.stats.episodesWatched} Episode Total",
                                color = AccentBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusBadge(label = "Ditonton", count = state.stats.animeWatching, color = AccentBlue, modifier = Modifier.weight(1f))
                            StatusBadge(label = "Selesai", count = state.stats.animeCompleted, color = AccentGreen, modifier = Modifier.weight(1f))
                            StatusBadge(label = "Ditunda", count = state.stats.animeOnHold, color = Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                            StatusBadge(label = "Drop", count = state.stats.animeDropped, color = Color(0xFFEF4444), modifier = Modifier.weight(1f))
                            StatusBadge(label = "Rencana", count = state.stats.animePlanToWatch, color = Color(0xFFA855F7), modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Detailed Manga Breakdown Card (Dark Blue Manga Style - Request 6)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MangaCardBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = MangaAccentDarkBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Distribusi Status Manga",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${state.stats.chaptersRead} Ch • ${state.stats.volumesRead} Vol",
                                color = MangaAccentDarkBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusBadge(label = "Dibaca", count = state.stats.mangaReading, color = MangaAccentDarkBlue, modifier = Modifier.weight(1f))
                            StatusBadge(label = "Selesai", count = state.stats.mangaCompleted, color = AccentGreen, modifier = Modifier.weight(1f))
                            StatusBadge(label = "Ditunda", count = state.stats.mangaOnHold, color = Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                            StatusBadge(label = "Drop", count = state.stats.mangaDropped, color = Color(0xFFEF4444), modifier = Modifier.weight(1f))
                            StatusBadge(label = "Rencana", count = state.stats.mangaPlanToRead, color = Color(0xFFA855F7), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Continue Watching Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Lanjut Nonton (${watchingAnime.size})",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Lihat Semua",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onNavigateTab("library") }
                            .padding(4.dp)
                    )
                }

                if (watchingAnime.isEmpty()) {
                    EmptySectionCard(
                        message = "Belum ada anime dengan status 'Sedang Ditonton'.",
                        actionText = "Muat Demo Data",
                        onAction = onLoadDemoData
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(watchingAnime, key = { it.id }, contentType = { "watching_card" }) { anime ->
                            WatchingCard(
                                anime = anime,
                                onQuickAdd = { onQuickAddEpisode(anime.id) },
                                onClick = { onSelectItem(anime, MediaType.ANIME) }
                            )
                        }
                    }
                }
            }
        }

        // Continue Reading Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Lanjut Baca (${readingManga.size})",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Lihat Semua",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { onNavigateTab("library") }
                            .padding(4.dp)
                    )
                }

                if (readingManga.isEmpty()) {
                    EmptySectionCard(
                        message = "Belum ada manga dengan status 'Sedang Dibaca'.",
                        actionText = "Eksplor Katalog",
                        onAction = { onNavigateTab("discover") }
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(readingManga, key = { it.id }, contentType = { "reading_card" }) { manga ->
                            ReadingCard(
                                manga = manga,
                                onQuickAdd = { onQuickAddChapter(manga.id) },
                                onClick = { onSelectItem(manga, MediaType.MANGA) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$count",
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WatchingCard(
    anime: UserMediaItem,
    onQuickAdd: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("watching_card_${anime.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = anime.imageUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (anime.score > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = anime.scoreFormatted,
                            color = StarGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = anime.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val progressFrac = anime.progressFrac

                LinearProgressIndicator(
                    progress = { progressFrac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = AccentBlue,
                    trackColor = CardElevated
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${anime.progress}/${if (anime.totalEpisodes > 0) anime.totalEpisodes else "?"} ep",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    FilledIconButton(
                        onClick = onQuickAdd,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("quick_add_${anime.id}"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = AccentBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Episode",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReadingCard(
    manga: UserMediaItem,
    onQuickAdd: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, MangaCardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("reading_card_${manga.id}"),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = manga.imageUrl,
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (manga.score > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = manga.scoreFormatted,
                            color = StarGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = manga.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val progressFrac = manga.progressChaptersFrac

                LinearProgressIndicator(
                    progress = { progressFrac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MangaAccentDarkBlue,
                    trackColor = CardElevated
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ch. ${manga.progressChapters}${if (manga.totalChapters > 0) "/${manga.totalChapters}" else ""}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    FilledIconButton(
                        onClick = onQuickAdd,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("quick_add_manga_${manga.id}"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MangaAccentDarkBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Chapter",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptySectionCard(
    message: String,
    actionText: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 13.sp
            )
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = actionText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
