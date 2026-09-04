package com.canim.app.ui.screens

import android.content.Intent
import android.net.Uri
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

@Composable
fun SettingsScreen(
    state: CanimUiState,
    onLoginMal: () -> Unit,
    onSyncMal: () -> Unit,
    onLogoutMal: () -> Unit,
    onSetAppMode: (String) -> Unit,
    onLoadDemoData: () -> Unit,
    onClearAllData: () -> Unit,
    onClearImageCache: () -> Unit,
    onClearMetadataCache: () -> Unit,
    onClearAllCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showClearDataDialog by remember { mutableStateOf(false) }

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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onSyncMal,
                                enabled = !state.isSyncingMal,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sync_mal_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen,
                                    contentColor = BlackBg
                                ),
                                shape = RoundedCornerShape(8.dp)
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
                                Text(text = if (state.isSyncingMal) "Sinkronisasi..." else "Sinkron MAL")
                            }

                            OutlinedButton(
                                onClick = onLogoutMal,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("logout_mal_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDroppedColor),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = "Putuskan")
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

        // Database & Demo Actions
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
                        text = "Tindakan Data Library",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = onLoadDemoData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("load_demo_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CardElevated,
                            contentColor = AccentGreen
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Muat Ulang Dataset Demo",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { showClearDataDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_data_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CardElevated,
                            contentColor = StatusDroppedColor
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kosongkan Semua Data Library",
                            fontWeight = FontWeight.SemiBold
                        )
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
                            painter = painterResource(id = R.drawable.ic_app_icon),
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

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            containerColor = CardElevated,
            title = {
                Text(
                    text = "Konfirmasi Hapus Data",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Apakah kamu yakin ingin mengosongkan semua data anime dan manga dari library lokal?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDroppedColor)
                ) {
                    Text("Hapus Semua", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }
}
