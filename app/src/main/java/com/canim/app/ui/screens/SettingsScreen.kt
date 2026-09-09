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

import com.canim.app.ui.viewmodel.update.UpdateUiState

@Composable
fun SettingsScreen(
    state: CanimUiState,
    updateState: UpdateUiState? = null,
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
    onOpenObservability: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCheckingUpdate = updateState?.isChecking ?: state.isCheckingUpdate
    val isAutoUpdateCheckEnabled = updateState?.isAutoCheckEnabled ?: state.isAutoUpdateCheckEnabled
    val updateInfo = updateState?.updateInfo ?: state.updateInfo
    val isDownloadingUpdate = updateState?.isDownloading ?: state.isDownloadingUpdate
    val updateDownloadProgress = updateState?.downloadProgress ?: state.updateDownloadProgress
    val downloadedApkFile = updateState?.downloadedApkFile ?: state.downloadedApkFile

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
                            enabled = !isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                contentColor = Color.White,
                                disabledContainerColor = CardElevated,
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (isCheckingUpdate) {
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
                            checked = isAutoUpdateCheckEnabled,
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

        // Observabilitas & Diagnostik Sistem (Compact Overview Card)
        item {
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
                                text = "Ringkasan metrik API, rate limiter & cache",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    HorizontalDivider(color = CardBorder)

                    // 1. API Requests Overview: Total, AniList, MAL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticKpiItem(label = "Total API", value = "${snapshot.totalRequests}", modifier = Modifier.weight(1f))
                        DiagnosticKpiItem(label = "AniList", value = "${snapshot.aniListRequests}", modifier = Modifier.weight(1f))
                        DiagnosticKpiItem(label = "MAL", value = "${snapshot.malRequests}", modifier = Modifier.weight(1f))
                    }

                    // 2. Health & Cache Overview: Errors, 429, Timeouts, Cache Hit Rate
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
                        val totalCache = snapshot.cacheHits + snapshot.cacheMisses
                        val hitRatePct = if (totalCache > 0) String.format("%.1f%%", snapshot.cacheHitRate * 100) else "0.0%"
                        DiagnosticKpiItem(
                            label = "Hit Rate",
                            value = hitRatePct,
                            valueColor = AccentBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = CardBorder)

                    // Dedicated Screen Entry Button: Buka Observabilitas
                    Button(
                        onClick = onOpenObservability,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_buka_observabilitas"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Buka Observabilitas",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Action buttons (Segarkan & Reset Metrik)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { snapshot = AppMetrics.getSnapshot() },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Segarkan", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = StatusDroppedColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Metrik", color = StatusDroppedColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
    if (updateInfo != null && updateInfo.isUpdateAvailable) {
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
                        text = "Versi baru ${updateInfo.latestVersion} telah dirilis di GitHub (Versi saat ini: ${com.canim.app.BuildConfig.VERSION_NAME}).",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    if (updateInfo.releaseNotes.isNotBlank()) {
                        Text(
                            text = "Catatan Rilis:\n" + updateInfo.releaseNotes.take(300) + if (updateInfo.releaseNotes.length > 300) "..." else "",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    if (isDownloadingUpdate) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Mengunduh file pembaruan... ${(updateDownloadProgress * 100).toInt()}%",
                                color = AccentBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            LinearProgressIndicator(
                                progress = { updateDownloadProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = AccentBlue,
                                trackColor = CardBg
                            )
                        }
                    } else if (downloadedApkFile != null) {
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
                    if (downloadedApkFile != null) {
                        Button(
                            onClick = onInstallDownloadedUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Pasang Sekarang", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else if (updateInfo.apkDownloadUrl != null && !isDownloadingUpdate) {
                        Button(
                            onClick = onStartDownloadUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Unduh & Pasang", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (!isDownloadingUpdate) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.htmlUrl))
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
                if (!isDownloadingUpdate) {
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

