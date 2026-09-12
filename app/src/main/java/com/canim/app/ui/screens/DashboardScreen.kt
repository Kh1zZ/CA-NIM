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
import com.canim.app.ui.viewmodel.library.LibraryUiState
import com.canim.app.ui.viewmodel.global.GlobalUiState
import com.canim.app.ui.viewmodel.gacha.GachaUiState

private fun isNetworkOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun DashboardScreen(
    libraryState: LibraryUiState,
    globalState: GlobalUiState,
    gachaState: GachaUiState,
    onQuickAddEpisode: (String) -> Unit,
    onQuickAddChapter: (String) -> Unit,
    onSelectItem: (Any, MediaType) -> Unit,
    onLoadDemoData: () -> Unit,
    onNavigateTab: (String) -> Unit,
    onLoginMal: () -> Unit = {},
    onSyncMal: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenFlashcard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val watchingAnime = libraryState.watchingAnime
    val readingManga = libraryState.readingManga
    val isDeviceOnline = remember { isNetworkOnline(context) }
    val onSelectAnimeItem: (UserMediaItem) -> Unit = remember(onSelectItem) {
        { anime -> onSelectItem(anime, MediaType.ANIME) }
    }
    val onSelectMangaItem: (UserMediaItem) -> Unit = remember(onSelectItem) {
        { manga -> onSelectItem(manga, MediaType.MANGA) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top App Bar: CA'NIM + Logo & Sync Status Badge
        item(key = "dashboard_top_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: User Avatar & Username from MAL
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .testTag("dashboard_user_header")
                ) {
                    if (!globalState.malUser.pictureUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = globalState.malUser.pictureUrl,
                            contentDescription = "Avatar Pengguna",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, AccentGreen.copy(alpha = 0.8f), CircleShape)
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
                                text = (globalState.malUser.username.take(1).ifBlank { "M" }).uppercase(),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column {
                        Text(
                            text = globalState.malUser.username.ifBlank { "MyAnimeList" },
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "MAL Terhubung",
                            color = AccentGreen,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Right: Sync Status & Connectivity indicator
                when (globalState.syncStatus) {
                    com.canim.app.data.model.SyncStatus.SYNCING -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(AccentBlue.copy(alpha = 0.15f))
                                .border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = AccentBlue,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Sinkron...",
                                    color = AccentBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    com.canim.app.data.model.SyncStatus.SUCCESS -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(AccentGreen.copy(alpha = 0.15f))
                                .border(1.dp, AccentGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Tersinkron",
                                    color = AccentGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    com.canim.app.data.model.SyncStatus.FAILED -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .clickable { onSyncMal() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Coba lagi",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Gagal • Coba Lagi",
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    com.canim.app.data.model.SyncStatus.IDLE -> {
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
        }
    }

    // API Outage Alert Banner (MAL only - AniList is handled transparently by Adaptive Rate Limiter)
    if (globalState.isMalDown) {
            item(key = "dashboard_api_outage_banner") {
                val title = "Layanan MyAnimeList Terkendala / Maintenance"
                val desc = "Sinkronisasi progress MAL tertunda sementara. Data tetap tersimpan di lokal."

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF451A03).copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Peringatan",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color(0xFFFBBF24),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = desc,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Catatan: Aplikasi tidak akan sepenuhnya berfungsi secara normal selama gangguan layanan berlangsung.",
                                color = Color(0xFFF59E0B).copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // MAL Sync Banner
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color(0xFF2E51A2).copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp)
                    ),
                color = CardBg,
                shape = RoundedCornerShape(14.dp)
            ) {
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
                        if (!globalState.malUser.pictureUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = globalState.malUser.pictureUrl,
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
                                    text = (globalState.malUser.username.take(1).ifBlank { "M" }).uppercase(),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = globalState.malUser.username.ifBlank { "MyAnimeList" },
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
                        enabled = !globalState.isSyncingMal
                    ) {
                        if (globalState.isSyncingMal) {
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
            }
        }

        // Unified Hero Metrics Strip (Compact)
        item(key = "dashboard_hero_metrics") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBg,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentBlue)
                            )
                            Text(
                                text = "RINGKASAN STATISTIK",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        TextButton(
                            onClick = onOpenStats,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Detail dan Ekspor",
                                color = AccentBlueLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = AccentBlueLight,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // 4 Integrated Metrics Flow with Hairline Separators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeroMetricItem(
                            label = "ANIME",
                            value = "${libraryState.stats.totalAnime}",
                            unit = "Judul",
                            color = AccentBlue,
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(DividerSubtle)
                        )

                        HeroMetricItem(
                            label = "MANGA",
                            value = "${libraryState.stats.totalManga}",
                            unit = "Judul",
                            color = MangaAccentDarkBlue,
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(DividerSubtle)
                        )

                        HeroMetricItem(
                            label = "WAKTU",
                            value = "${libraryState.stats.daysWatched}",
                            unit = "Hari",
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(DividerSubtle)
                        )

                        HeroMetricItem(
                            label = "BACA",
                            value = "${libraryState.stats.chaptersRead}",
                            unit = "Ch.",
                            color = Color(0xFF06B6D4),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Quick Action: Flashcard Gacha (Single unified card, Analisis Lengkap removed)
        item(key = "dashboard_flashcard_action") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFlashcard),
                color = CardBg,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(AccentBlue.copy(alpha = 0.35f), Color(0xFF8B5CF6).copy(alpha = 0.35f))
                    )
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Flashcard Gacha",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (gachaState.credits > 0) Color(0xFF8B5CF6) else Color(0xFFEF4444))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${gachaState.credits} Tiket",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Text(
                                text = "Tarik kartu acak untuk eksplorasi anime dan manga pilihan",
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Continue Watching Section
        item(key = "dashboard_continue_watching") {
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
                                onQuickAdd = onQuickAddEpisode,
                                onClick = onSelectAnimeItem
                            )
                        }
                    }
                }
            }
        }

        // Continue Reading Section
        item(key = "dashboard_continue_reading") {
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
                                onQuickAdd = onQuickAddChapter,
                                onClick = onSelectMangaItem
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
private fun HeroMetricItem(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
        Text(
            text = unit,
            color = TextMuted,
            fontSize = 8.sp
        )
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
    Surface(
        modifier = modifier,
        color = CardBg,
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
                fontSize = 17.sp,
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
    onQuickAdd: (String) -> Unit,
    onClick: (UserMediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable { onClick(anime) }
            .testTag("watching_card_${anime.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Poster with Score Badge and Sleek Overlay Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
        ) {
            AsyncImage(
                model = anime.imageUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom Gradient Shade for Progress Visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            // Score Badge
            if (anime.score > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = anime.scoreFormatted,
                            color = StarGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sleek Progress Bar right at bottom of poster
            LinearProgressIndicator(
                progress = { anime.progressFrac },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = AccentBlue,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }

        // Title + Episode Progress + Quick Add Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Ep. ${anime.progress}/${if (anime.totalEpisodes > 0) anime.totalEpisodes else "?"}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            val canIncrementAnime = !anime.status.equals("completed", ignoreCase = true) &&
                (anime.totalEpisodes <= 0 || anime.progress < anime.totalEpisodes)

            FilledIconButton(
                onClick = { onQuickAdd(anime.id) },
                enabled = canIncrementAnime,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("quick_add_${anime.id}"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = AccentBlue,
                    contentColor = Color.White,
                    disabledContainerColor = CardElevated.copy(alpha = 0.4f),
                    disabledContentColor = TextMuted.copy(alpha = 0.3f)
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

@Composable
fun ReadingCard(
    manga: UserMediaItem,
    onQuickAdd: (String) -> Unit,
    onClick: (UserMediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable { onClick(manga) }
            .testTag("reading_card_${manga.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Poster with Score Badge and Sleek Overlay Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
        ) {
            AsyncImage(
                model = manga.imageUrl,
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom Gradient Shade for Progress Visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            // Score Badge
            if (manga.score > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = manga.scoreFormatted,
                            color = StarGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sleek Progress Bar right at bottom of poster
            LinearProgressIndicator(
                progress = { manga.progressChaptersFrac },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = MangaAccentDarkBlue,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }

        // Title + Chapter Progress + Quick Add Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manga.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Ch. ${manga.progressChapters}/${if (manga.totalChapters > 0) manga.totalChapters else "?"}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            val canIncrementManga = !manga.status.equals("completed", ignoreCase = true) &&
                (manga.totalChapters <= 0 || manga.progressChapters < manga.totalChapters)

            FilledIconButton(
                onClick = { onQuickAdd(manga.id) },
                enabled = canIncrementManga,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("quick_add_${manga.id}"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MangaAccentDarkBlue,
                    contentColor = Color.White,
                    disabledContainerColor = CardElevated.copy(alpha = 0.4f),
                    disabledContentColor = TextMuted.copy(alpha = 0.3f)
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
