package com.canim.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.canim.app.R
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.CanimUiState
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.font.FontFamily
import com.canim.app.data.metrics.AppMetrics

@Composable
fun SettingsScreen(
    state: CanimUiState,
    onLoginMal: () -> Unit,
    onSyncMal: () -> Unit,
    onLogoutMal: () -> Unit,
    onSetAppMode: (String) -> Unit = {},
    onLoadDemoData: () -> Unit = {},
    onClearAllData: () -> Unit = {},
    onClearImageCache: () -> Unit,
    onClearMetadataCache: () -> Unit,
    onClearAllCache: () -> Unit,
    onCheckForUpdates: () -> Unit = {},
    onSetAutoUpdateCheck: (Boolean) -> Unit = {},
    onDismissUpdateDialog: () -> Unit = {},
    onStartDownloadUpdate: () -> Unit = {},
    onInstallDownloadedUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                text = "Pengaturan & Akun",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Kelola sinkronisasi MyAnimeList, cache, dan data aplikasi",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // MAL Account Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .testTag("mal_account_card"),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Integrasi MyAnimeList",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val isConnected = state.malUser.isLoggedIn
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isConnected) AccentGreen.copy(alpha = 0.15f) else Color.DarkGray)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isConnected) "Terhubung" else "Belum Terhubung",
                                color = if (isConnected) AccentGreen else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (state.malUser.isLoggedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!state.malUser.pictureUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = state.malUser.pictureUrl,
                                    contentDescription = "Avatar Pengguna",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(AccentGreen.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = AccentGreen
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.malUser.username,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ID: ${state.malUser.id}",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onSyncMal,
                                enabled = !state.isSyncingMal,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("sync_mal_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen,
                                    contentColor = BlackBg
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                if (state.isSyncingMal) {
                                    CircularProgressIndicator(
                                        color = BlackBg,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.isSyncingMal) "Sinkronisasi..." else "Sinkron MAL",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onLogoutMal,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("logout_mal_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDroppedColor),
                                border = BorderStroke(1.dp, StatusDroppedColor.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = StatusDroppedColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Putuskan",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Hubungkan akun MyAnimeList milikmu untuk melakukan sinkronisasi otomatis anime & manga secara penuh tanpa batas.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )

                        Button(
                            onClick = onLoginMal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_mal_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = BlackBg
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Login dengan MyAnimeList",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Cache Management Section (PART 14 & PART 8)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Manajemen Cache Terpusat",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Membersihkan cache hanya menghapus file sementara gambar & query API. Data koleksi library dan akun MAL kamu tidak akan terhapus.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = onClearImageCache,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_image_cache_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CardElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Bersihkan Cache Gambar")
                    }

                    Button(
                        onClick = onClearMetadataCache,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_metadata_cache_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CardElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentYellow)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Bersihkan Cache API & Metadata")
                    }

                    Button(
                        onClick = onClearAllCache,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_all_cache_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CardElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Bersihkan Semua Cache")
                    }
                }
            }
        }


        // Pembaruan Aplikasi (Revisi 6)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Pembaruan Aplikasi",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Versi terpasang & tombol cek
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Versi Terpasang",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "CA\'NIM ${com.canim.app.BuildConfig.VERSION_NAME} (Production)",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = onCheckForUpdates,
                            enabled = !state.isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                contentColor = Color.White,
                                disabledContainerColor = CardElevated,
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (state.isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentBlue
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Memeriksa...", fontSize = 11.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cek Update", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Divider(color = CardBorder)

                    // Toggle Cek Update Otomatis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cek Update Otomatis",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Pengecekan versi baru saat aplikasi dibuka (1x per 24 jam)",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Switch(
                            checked = state.isAutoUpdateCheckEnabled,
                            onCheckedChange = onSetAutoUpdateCheck,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentBlue,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = CardElevated
                            )
                        )
                    }
                }
            }
        }

        // Observabilitas & Diagnostik Sistem (In-memory, Zero PII)
        // Observabilitas & Diagnostik Sistem (In-memory, Zero PII)
        item {
            var showDetails by remember { mutableStateOf(false) }
            var snapshot by remember { mutableStateOf(AppMetrics.getSnapshot()) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .testTag("observability_diagnostics_card"),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Observabilitas & Diagnostik",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Metrik performa, cache, & reliabilitas API (Zero PII)",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Divider(color = CardBorder)

                    // 1. API Usage
                    Text(text = "API Usage", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticKpiItem(label = "Total", value = "${snapshot.totalRequests}", modifier = Modifier.weight(1f))
                        DiagnosticKpiItem(label = "AniList", value = "${snapshot.aniListRequests}", modifier = Modifier.weight(1f))
                        DiagnosticKpiItem(label = "MAL", value = "${snapshot.malRequests}", modifier = Modifier.weight(1f))
                        DiagnosticKpiItem(label = "GitHub", value = "${snapshot.gitHubRequests}", modifier = Modifier.weight(1f))
                    }

                    // 2. Health
                    Text(text = "Health", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticKpiItem(
                            label = "Errors",
                            value = "${snapshot.totalErrors}",
                            valueColor = if (snapshot.totalErrors > 0) StatusDroppedColor else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        DiagnosticKpiItem(
                            label = "429",
                            value = "${snapshot.totalRateLimits}",
                            valueColor = if (snapshot.totalRateLimits > 0) StatusOnHoldColor else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        DiagnosticKpiItem(
                            label = "Timeouts",
                            value = "${snapshot.totalTimeouts}",
                            valueColor = if (snapshot.totalTimeouts > 0) StatusOnHoldColor else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        DiagnosticKpiItem(
                            label = "Retries",
                            value = "${snapshot.totalRetries}",
                            valueColor = if (snapshot.totalRetries > 0) StatusOnHoldColor else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 3. Cache & Latency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cache
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Cache", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val hitRatePct = if (snapshot.cacheHits + snapshot.cacheMisses > 0) {
                                    String.format("%.1f%%", snapshot.cacheHitRate * 100)
                                } else "0.0%"
                                DiagnosticKpiItem(label = "Hit Rate", value = hitRatePct, valueColor = AccentBlue, modifier = Modifier.weight(1f))
                                DiagnosticKpiItem(label = "Hits / Misses", value = "${snapshot.cacheHits}/${snapshot.cacheMisses}", modifier = Modifier.weight(1f))
                            }
                        }
                        // Latency
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Latency", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val aniAvg = snapshot.aniListAvgLatencyMs?.let { "${String.format("%.0f", it)} ms" } ?: "-"
                                val malAvg = snapshot.malAvgLatencyMs?.let { "${String.format("%.0f", it)} ms" } ?: "-"
                                DiagnosticKpiItem(label = "AniList Rata²", value = aniAvg, modifier = Modifier.weight(1f))
                                DiagnosticKpiItem(label = "MAL Rata²", value = malAvg, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // Expandable "Lihat Detail" toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                snapshot = AppMetrics.getSnapshot()
                                showDetails = !showDetails
                            }
                        ) {
                            Text(
                                text = if (showDetails) "Tutup Detail ▲" else "Lihat Detail ▼",
                                color = AccentBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Expandable details container
                    if (showDetails) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardElevated, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Requests by Host
                            Text(text = "Permintaan per Host", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (snapshot.requestsByHost.isNotEmpty()) {
                                snapshot.requestsByHost.forEach { (host, count) ->
                                    DiagnosticDetailRow(label = host, value = "$count req")
                                }
                            } else {
                                Text(text = "Belum ada permintaan", color = TextMuted, fontSize = 10.sp)
                            }

                            Divider(color = CardBorderSubtle)

                            // Requests by Operation
                            Text(text = "Permintaan per Operasi", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (snapshot.requestsByOperation.isNotEmpty()) {
                                snapshot.requestsByOperation.forEach { (op, count) ->
                                    DiagnosticDetailRow(label = op, value = "$count")
                                }
                            } else {
                                Text(text = "Belum ada operasi", color = TextMuted, fontSize = 10.sp)
                            }

                            Divider(color = CardBorderSubtle)

                            // Cache Statistics
                            Text(text = "Statistik Cache", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            DiagnosticDetailRow(label = "Total Hits", value = "${snapshot.cacheHits}")
                            DiagnosticDetailRow(label = "Total Misses", value = "${snapshot.cacheMisses}")
                            if (snapshot.cacheHitsByType.isNotEmpty()) {
                                snapshot.cacheHitsByType.forEach { (type, hits) ->
                                    val misses = snapshot.cacheMissesByType[type] ?: 0L
                                    DiagnosticDetailRow(label = "Tipe: $type", value = "Hits: $hits, Misses: $misses")
                                }
                            }

                            Divider(color = CardBorderSubtle)

                            // In-flight Deduplication
                            Text(text = "Deduplikasi In-Flight", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            DiagnosticDetailRow(label = "Total Duplikasi Dihemat", value = "${snapshot.deduplicationCount}")
                            if (snapshot.deduplicationsByHost.isNotEmpty()) {
                                snapshot.deduplicationsByHost.forEach { (host, count) ->
                                    DiagnosticDetailRow(label = "Host: $host", value = "$count")
                                }
                            }

                            Divider(color = CardBorderSubtle)

                            // Errors & Retries breakdown
                            Text(text = "Rincian Error & Retry", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (snapshot.totalErrors == 0L && snapshot.totalRateLimits == 0L && snapshot.totalTimeouts == 0L && snapshot.totalRetries == 0L) {
                                Text(text = "Kondisi sehat — tidak ada error atau retry.", color = TextMuted, fontSize = 10.sp)
                            } else {
                                snapshot.http5xxByHost.forEach { (host, count) ->
                                    DiagnosticDetailRow(label = "Server 5xx ($host)", value = "$count", valueColor = StatusDroppedColor)
                                }
                                snapshot.rateLimitsByHost.forEach { (host, count) ->
                                    DiagnosticDetailRow(label = "Rate Limit 429 ($host)", value = "$count", valueColor = StatusOnHoldColor)
                                }
                                snapshot.timeoutsByHost.forEach { (host, count) ->
                                    DiagnosticDetailRow(label = "Timeout ($host)", value = "$count", valueColor = StatusOnHoldColor)
                                }
                                snapshot.retriesByHost.forEach { (host, count) ->
                                    DiagnosticDetailRow(label = "Retry ($host)", value = "$count", valueColor = StatusOnHoldColor)
                                }
                            }

                            Divider(color = CardBorderSubtle)

                            // Latency Min / Avg / Max
                            Text(text = "Rincian Latensi (Min / Rata² / Maks)", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (snapshot.latencyByHost.isNotEmpty()) {
                                snapshot.latencyByHost.forEach { (host, stats) ->
                                    DiagnosticDetailRow(
                                        label = host,
                                        value = "min: ${stats.minMs}ms | avg: ${String.format("%.0f", stats.avgMs)}ms | max: ${stats.maxMs}ms (n=${stats.count})"
                                    )
                                }
                            } else {
                                Text(text = "Belum ada pengukuran latensi", color = TextMuted, fontSize = 10.sp)
                            }
                        }
                    }

                    // Action buttons (Segarkan & Reset)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                snapshot = AppMetrics.getSnapshot()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Segarkan", color = TextSecondary, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                AppMetrics.reset()
                                snapshot = AppMetrics.getSnapshot()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Metrik", color = StatusDroppedColor, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // PART 30 - Developer Credit & About
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "Logo CA'NIM",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column {
                            Text(
                                text = "CA\'NIM",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Versi: ${com.canim.app.BuildConfig.VERSION_NAME} (MAL Single Source of Truth & AniList GraphQL)",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Aplikasi client modern pelacak anime & manga dengan MyAnimeList sebagai Single Source of Truth dan AniList GraphQL rich metadata.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Divider(color = CardBorder)

                    Spacer(modifier = Modifier.height(4.dp))

                    // Developer Credit & Clickable GitHub Link (PART 30)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Developer",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Kh1zZ",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardElevated)
                                .clickable {
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kh1zZ")).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(browserIntent)
                                    } catch (_: Exception) {}
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "github.com/Kh1zZ",
                                color = AccentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Buka Profil GitHub",
                                tint = AccentGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }


    // Dialog Pembaruan Tersedia
    if (state.updateInfo != null && state.updateInfo.isUpdateAvailable) {
        AlertDialog(
            onDismissRequest = onDismissUpdateDialog,
            containerColor = CardElevated,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = AccentBlue
                    )
                    Text(
                        text = "Pembaruan Tersedia!",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Versi baru ${state.updateInfo.latestVersion} telah dirilis di GitHub (Versi saat ini: ${com.canim.app.BuildConfig.VERSION_NAME}).",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    if (state.updateInfo.releaseNotes.isNotBlank()) {
                        Text(
                            text = "Catatan Rilis:\n" + state.updateInfo.releaseNotes.take(300) + if (state.updateInfo.releaseNotes.length > 300) "..." else "",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    if (state.isDownloadingUpdate) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Mengunduh file pembaruan... ${(state.updateDownloadProgress * 100).toInt()}%",
                                color = AccentBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            LinearProgressIndicator(
                                progress = { state.updateDownloadProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = AccentBlue,
                                trackColor = CardBg
                            )
                        }
                    } else if (state.downloadedApkFile != null) {
                        Text(
                            text = "✓ File update berhasil diunduh. Tekan 'Pasang Sekarang' untuk menginstal.",
                            color = AccentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Unduh dan pasang pembaruan resmi secara otomatis di dalam aplikasi.",
                            color = AccentBlue,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.downloadedApkFile != null) {
                        Button(
                            onClick = onInstallDownloadedUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Pasang Sekarang", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else if (state.updateInfo.apkDownloadUrl != null && !state.isDownloadingUpdate) {
                        Button(
                            onClick = onStartDownloadUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Unduh & Pasang", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (!state.isDownloadingUpdate) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.updateInfo.htmlUrl))
                                context.startActivity(intent)
                                onDismissUpdateDialog()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text("Buka GitHub", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            },
            dismissButton = {
                if (!state.isDownloadingUpdate) {
                    TextButton(onClick = onDismissUpdateDialog) {
                        Text("Nanti Saja", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        )
    }
}

@Composable
private fun DiagnosticKpiItem(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(CardElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DiagnosticDetailRow(
    label: String,
    value: String,
    valueColor: Color = TextSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

