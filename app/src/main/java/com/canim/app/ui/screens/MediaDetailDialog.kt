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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.canim.app.data.model.*
import com.canim.app.ui.theme.*

@Composable
fun MediaDetailDialog(
    item: Any,
    type: MediaType,
    extendedDetail: ExtendedMediaDetail?,
    isLoadingExtendedDetail: Boolean,
    onSaveAnime: (UserMediaItem) -> Unit,
    onSaveManga: (UserMediaItem) -> Unit,
    onDeleteAnime: (String) -> Unit,
    onDeleteManga: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isAnime = type == MediaType.ANIME
    val isManga = !isAnime
    val themeAccent = if (isManga) MangaAccentDarkBlue else AccentBlue
    val themeBorder = if (isManga) MangaCardBorder else CardBorder

    val userItem = item as? UserMediaItem
    val mediaItem = item as? MediaItem

    var status by remember { mutableStateOf(userItem?.status ?: if (isAnime) "watching" else "reading") }
    var score by remember { mutableIntStateOf(userItem?.score ?: 0) }
    var progress by remember { mutableIntStateOf(userItem?.progress ?: 0) }
    var notes by remember { mutableStateOf(userItem?.notes ?: "") }
    var activeSubTab by remember { mutableIntStateOf(0) } // 0: Tracking, 1: Karakter & VA, 2: Crew / Staff, 3: Detail

    val total = userItem?.let { if (isAnime) it.totalEpisodes else it.totalChapters }
        ?: mediaItem?.let { if (isAnime) it.episodes ?: 0 else it.chapters ?: 0 } ?: 0
    val title = userItem?.title ?: mediaItem?.title ?: ""
    val imageUrl = userItem?.imageUrl ?: mediaItem?.imageUrl ?: ""
    val synopsis = userItem?.synopsis ?: mediaItem?.synopsis ?: ""
    val genres = userItem?.genres ?: mediaItem?.genres?.joinToString(", ") ?: ""

    val animeStatusOptions = listOf(
        "watching" to "Ditonton",
        "completed" to "Selesai",
        "on_hold" to "Ditunda",
        "dropped" to "Ditinggalkan",
        "plan_to_watch" to "Rencana"
    )

    val mangaStatusOptions = listOf(
        "reading" to "Dibaca",
        "completed" to "Selesai",
        "on_hold" to "Ditunda",
        "dropped" to "Ditinggalkan",
        "plan_to_read" to "Rencana"
    )

    val currentStatusOptions = if (isAnime) animeStatusOptions else mangaStatusOptions

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("media_detail_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardElevated)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header (Close & Title)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isAnime) "Anime Tracker • AniList" else "Manga Tracker • AniList",
                            color = themeAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Media summary banner: CLEAN & RINGKAS (Task 2.6: Tanpa metadata berlebih di atas)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, themeBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 54.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(themeAccent.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isAnime) "ANIME" else "MANGA",
                                    color = themeAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (score > 0) {
                                Text(
                                    text = "★ $score / 10",
                                    color = StarGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (genres.isNotBlank()) {
                                Text(
                                    text = genres.split(",").take(2).joinToString(", "),
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sub-tabs: Tracking | Karakter & VA | Crew / Staff | Detail
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardBg)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val tabs = listOf("Tracking", "Karakter", "Staf", "Detail")
                    tabs.forEachIndexed { index, tabName ->
                        val isSelected = activeSubTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) themeAccent else Color.Transparent)
                                .clickable { activeSubTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabName,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Contents
                Box(modifier = Modifier.weight(1f)) {
                    when (activeSubTab) {
                        0 -> {
                            // Tracking Settings (Status, Score, Progress, Notes)
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                // Status UI (Pill chips)
                                item {
                                    Text(
                                        text = "Status",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(currentStatusOptions) { (key, label) ->
                                            val isSelected = status == key
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(if (isSelected) themeAccent else CardBg)
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) themeAccent else themeBorder,
                                                        RoundedCornerShape(16.dp)
                                                    )
                                                    .clickable { status = key }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) Color.White else TextPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }

                                // Personal Score (1-10)
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Skor Personal",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (score > 0) "★ $score / 10" else "Belum Dinilai",
                                            color = if (score > 0) StarGold else TextMuted,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Row 1: 1 to 5
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (s in 1..5) {
                                            val isSelected = score == s
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) StarGold else CardBg)
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) StarGold else CardBorder,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { score = if (score == s) 0 else s }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$s",
                                                    color = if (isSelected) BlackBg else TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Row 2: 6 to 10
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (s in 6..10) {
                                            val isSelected = score == s
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) StarGold else CardBg)
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) StarGold else CardBorder,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { score = if (score == s) 0 else s }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$s",
                                                    color = if (isSelected) BlackBg else TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Progress Stepper
                                item {
                                    Text(
                                        text = if (isAnime) "Episode Ditonton" else "Chapter Dibaca",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CardBg)
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilledIconButton(
                                            onClick = { if (progress > 0) progress-- },
                                            modifier = Modifier.size(36.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = CardElevated,
                                                contentColor = TextPrimary
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Kurang 1",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Text(
                                            text = "$progress / ${if (total > 0) "$total" else "?"}",
                                            color = TextPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )

                                        FilledIconButton(
                                            onClick = { progress++ },
                                            modifier = Modifier.size(36.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = themeAccent,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Tambah 1",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Personal Notes
                                item {
                                    Text(
                                        text = "Catatan Pribadi",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = notes,
                                        onValueChange = { notes = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = {
                                            Text(
                                                "Tulis catatan atau review singkat...",
                                                color = TextMuted,
                                                fontSize = 12.sp
                                            )
                                        },
                                        maxLines = 3,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = themeAccent,
                                            unfocusedBorderColor = CardBorder,
                                            focusedContainerColor = CardBg,
                                            unfocusedContainerColor = CardBg,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }

                        1 -> {
                            // Cast & Voice Actors
                            if (isLoadingExtendedDetail) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = themeAccent)
                                }
                            } else if (extendedDetail?.cast.isNullOrEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Informasi karakter & Seiyuu tidak tersedia.", color = TextMuted, fontSize = 12.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 12.dp)
                                ) {
                                    items(extendedDetail!!.cast, contentType = { "cast_item" }) { cast ->
                                        CastItemRow(cast)
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Staff & Crew
                            if (isLoadingExtendedDetail) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = themeAccent)
                                }
                            } else if (extendedDetail?.crew.isNullOrEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Informasi staf & kru produksi tidak tersedia.", color = TextMuted, fontSize = 12.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 12.dp)
                                ) {
                                    items(extendedDetail!!.crew, contentType = { "crew_item" }) { staff ->
                                        StaffItemRow(staff)
                                    }
                                }
                            }
                        }

                        3 -> {
                            // Tab "Detail": Studio, Publisher, Licensor, Source, Airing Status & Synopsis (Task 2.6)
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                // Fallback indicator badge
                                if (extendedDetail?.isFromFallback == true) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(StarGold.copy(alpha = 0.15f))
                                                .border(1.dp, StarGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "ℹ Metadata dilengkapi melalui MyAnimeList Fallback Provider.",
                                                color = StarGold,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Technical metadata table
                                item {
                                    Text(
                                        text = "Informasi Produksi & Lisensi",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CardBg)
                                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val studioVal = extendedDetail?.studio ?: userItem?.studio ?: mediaItem?.studio ?: "-"
                                        val publisherVal = extendedDetail?.publisher ?: "-"
                                        val licensorVal = extendedDetail?.licensor ?: "-"
                                        val sourceVal = extendedDetail?.source ?: "-"
                                        val airingVal = extendedDetail?.airingStatus ?: userItem?.airingStatus ?: mediaItem?.status ?: "-"
                                        val durVal = if (extendedDetail?.durationMinutes != null && extendedDetail.durationMinutes > 0) "${extendedDetail.durationMinutes} menit" else "-"
                                        val season = userItem?.season ?: mediaItem?.season
                                        val year = userItem?.year ?: mediaItem?.year
                                        val seasonVal = if (season != null) "$season ${year ?: ""}".trim() else if (year != null) "$year" else "-"
                                        val nativeVal = extendedDetail?.nativeTitle ?: "-"
                                        val startVal = extendedDetail?.startDate ?: "-"
                                        val endVal = extendedDetail?.endDate ?: "-"

                                        DetailInfoRow(label = if (isAnime) "Studio Produksi" else "Penulis / Author", value = if (isAnime) studioVal else publisherVal)
                                        if (isAnime) {
                                            DetailInfoRow(label = "Publisher / Distributor", value = publisherVal)
                                        }
                                        DetailInfoRow(label = "Licensor Resmi", value = licensorVal)
                                        DetailInfoRow(label = "Sumber Adaptasi", value = sourceVal)
                                        DetailInfoRow(label = "Status Rilis", value = airingVal)
                                        DetailInfoRow(label = if (isAnime) "Tanggal Airing (Mulai)" else "Tanggal Rilis (Mulai)", value = startVal)
                                        DetailInfoRow(label = if (isAnime) "Tanggal Selesai (Finished)" else "Tanggal Tamat (Finished)", value = endVal)
                                        if (isAnime) {
                                            DetailInfoRow(label = "Durasi Episode", value = durVal)
                                        }
                                        DetailInfoRow(label = "Musim Rilis", value = seasonVal)
                                        DetailInfoRow(label = "Judul Asli (JP)", value = nativeVal)
                                    }
                                }

                                // Full Genre List (Request: tambah full genre list)
                                item {
                                    Text(
                                        text = "Daftar Genre Lengkap",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val allGenres = if (!extendedDetail?.genres.isNullOrEmpty()) {
                                        extendedDetail!!.genres
                                    } else if (genres.isNotBlank()) {
                                        genres.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    } else {
                                        emptyList()
                                    }

                                    if (allGenres.isNotEmpty()) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(allGenres) { g ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(CardBg)
                                                        .border(1.dp, themeBorder, RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = g,
                                                        color = themeAccent,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text("Tidak ada informasi genre.", color = TextMuted, fontSize = 12.sp)
                                    }
                                }

                                // Full Synopsis
                                item {
                                    Text(
                                        text = "Sinopsis Lengkap",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, themeBorder, RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(containerColor = CardBg),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = if (synopsis.isNotBlank()) synopsis else "Tidak ada sinopsis tersedia untuk judul ini.",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Actions (Delete & Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val id = userItem?.id ?: mediaItem?.malId?.let { "mal_$it" } ?: mediaItem?.anilistId?.let { "ani_$it" } ?: ""
                            if (id.isNotBlank()) {
                                if (isAnime) onDeleteAnime(id)
                                else onDeleteManga(id)
                            }
                        },
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("dialog_delete_button"),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDroppedColor),
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(StatusDroppedColor))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Hapus",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = {
                            val updatedItem = if (userItem != null) {
                                userItem.copy(
                                    tracking = userItem.tracking.copy(
                                        status = status,
                                        score = score,
                                        progress = progress,
                                        comments = notes,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            } else if (mediaItem != null) {
                                val identity = MediaRef(
                                    anilistId = mediaItem.anilistId,
                                    malId = mediaItem.malId
                                )
                                val metadata = MediaMetadata(
                                    title = mediaItem.title,
                                    titleEnglish = mediaItem.titleEnglish,
                                    titleNative = null,
                                    imageUrl = mediaItem.imageUrl,
                                    type = type,
                                    score = mediaItem.score,
                                    synopsis = mediaItem.synopsis,
                                    totalEpisodes = mediaItem.episodes,
                                    totalChapters = mediaItem.chapters,
                                    totalVolumes = mediaItem.volumes,
                                    status = mediaItem.status,
                                    year = mediaItem.year,
                                    season = mediaItem.season,
                                    genres = mediaItem.genres,
                                    format = mediaItem.format,
                                    studio = mediaItem.studio
                                )
                                val tracking = MalTracking(
                                    status = status,
                                    score = score,
                                    progress = progress,
                                    comments = notes,
                                    updatedAt = System.currentTimeMillis()
                                )
                                UserMediaItem(identity = identity, metadata = metadata, tracking = tracking)
                            } else null

                            if (updatedItem != null) {
                                if (isAnime) onSaveAnime(updatedItem)
                                else onSaveManga(updatedItem)
                            }
                        },
                        modifier = Modifier
                            .weight(2.1f)
                            .testTag("dialog_save_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Simpan Perubahan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.3f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CastItemRow(cast: CharacterCastItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Character info
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = cast.characterImage,
                contentDescription = cast.characterName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Column {
                Text(
                    text = cast.characterName,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cast.role ?: "Supporting",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }

        // Voice actor info
        if (cast.actorName != null) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = cast.actorName,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Seiyuu (JP)",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                AsyncImage(
                    model = cast.actorImage,
                    contentDescription = cast.actorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun StaffItemRow(staff: StaffMemberItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = staff.image,
            contentDescription = staff.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
        Column {
            Text(
                text = staff.name,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = staff.role,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
