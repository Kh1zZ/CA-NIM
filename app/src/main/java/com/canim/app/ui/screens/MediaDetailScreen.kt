package com.canim.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.canim.app.data.model.*
import com.canim.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    item: Any,
    type: MediaType,
    extendedDetail: ExtendedMediaDetail?,
    isLoadingExtendedDetail: Boolean,
    onSaveAnime: (UserMediaItem) -> Unit,
    onSaveManga: (UserMediaItem) -> Unit,
    onDeleteAnime: (String) -> Unit,
    onDeleteManga: (String) -> Unit,
    onOpenCastCrew: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val isAnime = type == MediaType.ANIME
    val isManga = !isAnime
    val themeAccent = if (isManga) MangaAccentDarkBlue else AccentBlue
    val themeBorder = if (isManga) MangaCardBorder else CardBorder

    val userItem = item as? UserMediaItem
    val mediaItem = item as? MediaItem

    val title: String = userItem?.title ?: mediaItem?.title ?: ""
    val titleEnglish: String? = userItem?.metadata?.titleEnglish ?: mediaItem?.titleEnglish ?: extendedDetail?.titleEnglish
    val titleNative: String? = extendedDetail?.nativeTitle
    val imageUrl: String = userItem?.imageUrl ?: mediaItem?.imageUrl ?: ""
    val bannerUrl: String = imageUrl
    val synopsis: String = userItem?.synopsis ?: mediaItem?.synopsis ?: ""
    val cleanSynopsis: String = remember(synopsis) {
        synopsis
            .replace(Regex("<br.*?>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("~!|!~"), "")
            .trim()
    }

    val totalEpisodes = userItem?.totalEpisodes ?: mediaItem?.episodes ?: 0
    val totalChapters = userItem?.totalChapters ?: mediaItem?.chapters ?: 0
    val maxProgress = if (isAnime) totalEpisodes else totalChapters

    var showTrackingSheet by remember { mutableStateOf(false) }
    var trackingStatus by remember { mutableStateOf(userItem?.status ?: if (isAnime) "watching" else "reading") }
    var trackingScore by remember { mutableIntStateOf(userItem?.score ?: 0) }
    var trackingProgress by remember { mutableIntStateOf(userItem?.progress ?: 0) }
    var trackingNotes by remember { mutableStateOf(userItem?.notes ?: "") }

    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var showAllCast by remember { mutableStateOf(false) }
    var showAllCrew by remember { mutableStateOf(false) }

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

    Scaffold(
        containerColor = BlackBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTrackingSheet = true },
                containerColor = themeAccent,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(
                    imageVector = if (userItem != null) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = "Lacak",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // MyDramaList (MDL) Header: Large Cover Backdrop
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    AsyncImage(
                        model = bannerUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Dark overlay gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Black.copy(alpha = 0.7f),
                                        BlackBg
                                    )
                                )
                            )
                    )

                    // Top App Bar with back button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }

                        // Badge format
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, themeBorder, RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isAnime) "ANIME" else "MANGA",
                                color = themeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Poster & Title Info Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-40).dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .width(115.dp)
                            .height(165.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, themeBorder, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!titleEnglish.isNullOrBlank() && titleEnglish != title) {
                            Text(
                                text = titleEnglish,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!titleNative.isNullOrBlank()) {
                            Text(
                                text = titleNative,
                                color = AccentBlueLight,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val formatText: String = userItem?.metadata?.format ?: mediaItem?.format ?: if (isAnime) "TV" else "MANGA"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CardElevated)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(text = formatText, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            val yr: Int? = userItem?.year ?: mediaItem?.year
                            if (yr != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CardElevated)
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(text = "$yr", color = TextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Stats & Ranking Badges Row (AniList Score, Rank, Popularity, Watchers)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-20).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scoreVal = extendedDetail?.averageScore
                        ?: (userItem?.score?.toDouble() ?: mediaItem?.score)
                    if (scoreVal != null && scoreVal > 0) {
                        MDLStatPill(
                            icon = Icons.Default.Star,
                            label = "Rating",
                            value = "★ ${(scoreVal * 10).toInt() / 10.0}",
                            color = StarGold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (extendedDetail?.rank != null) {
                        MDLStatPill(
                            icon = Icons.Default.EmojiEvents,
                            label = "Ranked",
                            value = "#${extendedDetail.rank}",
                            color = AccentBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (extendedDetail?.popularity != null) {
                        MDLStatPill(
                            icon = Icons.Default.TrendingUp,
                            label = "Popularitas",
                            value = "#${extendedDetail.popularity}",
                            color = AccentGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (extendedDetail?.watchers != null) {
                        MDLStatPill(
                            icon = Icons.Default.People,
                            label = "Penggemar",
                            value = "${extendedDetail.watchers}",
                            color = Color(0xFFA855F7),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Synopsis Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SINOPSIS",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = cleanSynopsis.ifBlank { "Sinopsis tidak tersedia." },
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (cleanSynopsis.length > 180) {
                            Text(
                                text = if (isSynopsisExpanded) "Tutup Sinopsis" else "Baca Selengkapnya...",
                                color = themeAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Details Section (Genres, Status, Episodes, Dates, Studio - NO TAGS)
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "DETAIL INFORMASI",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        val airingStatus = extendedDetail?.airingStatus ?: (userItem?.status ?: mediaItem?.status)
                        if (!airingStatus.isNullOrBlank()) {
                            DetailRow("Status Penayangan", airingStatus)
                        }

                        if (isAnime && totalEpisodes > 0) {
                            DetailRow("Total Episode", "$totalEpisodes Ep")
                        } else if (isManga && totalChapters > 0) {
                            DetailRow("Total Bab", "$totalChapters Bab")
                        }

                        val duration = extendedDetail?.durationMinutes
                        if (duration != null && duration > 0) {
                            DetailRow("Durasi", "$duration Menit / Ep")
                        }

                        val startDate = extendedDetail?.startDate
                        if (!startDate.isNullOrBlank()) {
                            DetailRow("Tanggal Mulai", startDate)
                        }

                        val endDate = extendedDetail?.endDate
                        if (!endDate.isNullOrBlank()) {
                            DetailRow("Tanggal Selesai", endDate)
                        }

                        val studio = extendedDetail?.studio ?: (userItem?.studio ?: mediaItem?.studio)
                        if (!studio.isNullOrBlank()) {
                            DetailRow(if (isAnime) "Studio Animasi" else "Penerbit", studio)
                        }

                        val source = extendedDetail?.source
                        if (!source.isNullOrBlank()) {
                            DetailRow("Sumber Orisinal", source)
                        }

                        // Genres Section (Tags are strictly excluded as requested)
                        val genres = extendedDetail?.genres ?: (userItem?.metadata?.genres ?: mediaItem?.genres ?: emptyList())
                        if (genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Genre:", color = TextMuted, fontSize = 11.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(genres) { g ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CardElevated)
                                            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = g, color = themeAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Cast Section (Characters & Voice Actors)
            val castList: List<CharacterCastItem> = extendedDetail?.cast ?: emptyList()
            if (castList.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PEMERAN & KARAKTER (${castList.size})",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (castList.size > 6) {
                            Text(
                                text = if (showAllCast) "Tutup" else "Lihat Semua",
                                color = themeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showAllCast = !showAllCast }
                            )
                        }
                    }

                    val displayedCast: List<CharacterCastItem> = if (showAllCast) castList else castList.take(8)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayedCast) { cast ->
                            CastAvatarItem(
                                cast = cast,
                                onClick = {
                                    val targetId = cast.actorId ?: cast.characterId ?: 0
                                    val isStaff = cast.actorId != null
                                    if (targetId > 0) {
                                        onOpenCastCrew(targetId, isStaff)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Crew Section (Staff Members)
            val staffList: List<StaffMemberItem> = extendedDetail?.crew ?: emptyList()
            if (staffList.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STAF PRODUKSI (${staffList.size})",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (staffList.size > 6) {
                            Text(
                                text = if (showAllCrew) "Tutup" else "Lihat Semua",
                                color = themeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showAllCrew = !showAllCrew }
                            )
                        }
                    }

                    val displayedStaff: List<StaffMemberItem> = if (showAllCrew) staffList else staffList.take(8)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayedStaff) { staff ->
                            StaffAvatarItem(
                                staff = staff,
                                onClick = {
                                    val targetId = staff.staffId ?: 0
                                    if (targetId > 0) {
                                        onOpenCastCrew(targetId, true)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Recommendations Section
            val recommendations = extendedDetail?.recommendations ?: emptyList()
            if (recommendations.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "REKOMENDASI TERKAIT",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recommendations) { rec ->
                            Card(
                                modifier = Modifier
                                    .width(110.dp)
                                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
                                colors = CardDefaults.cardColors(containerColor = CardBg),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column {
                                    AsyncImage(
                                        model = rec.imageUrl,
                                        contentDescription = rec.title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Text(
                                        text = rec.title,
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Compact Personal Tracking Bottom Sheet (FAB Triggered)
    if (showTrackingSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTrackingSheet = false },
            containerColor = CardElevated,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Lacak Progres Pribadi",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // Status Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Status:", color = TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(currentStatusOptions) { (key, label) ->
                            val isSelected = trackingStatus == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) themeAccent else CardBg)
                                    .border(1.dp, if (isSelected) themeAccent else CardBorder, RoundedCornerShape(8.dp))
                                    .clickable { trackingStatus = key }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Progress Counter (+1 / -1 / Direct input)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isAnime) "Progres Episode (Total: ${if (totalEpisodes > 0) totalEpisodes else "?"}):"
                        else "Progres Bab (Total: ${if (totalChapters > 0) totalChapters else "?"}):",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = { if (trackingProgress > 0) trackingProgress-- },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBg)
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "-1", tint = TextPrimary)
                        }

                        OutlinedTextField(
                            value = "$trackingProgress",
                            onValueChange = { str ->
                                val num = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                                trackingProgress = if (maxProgress > 0) num.coerceIn(0, maxProgress) else num
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = themeAccent,
                                unfocusedBorderColor = CardBorder
                            )
                        )

                        IconButton(
                            onClick = {
                                if (maxProgress == 0 || trackingProgress < maxProgress) trackingProgress++
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBg)
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "+1", tint = TextPrimary)
                        }
                    }
                }

                // Personal Score (1 - 10)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Rating Pribadi: ★ $trackingScore / 10", color = TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((0..10).toList()) { sc ->
                            val isSelected = trackingScore == sc
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) StarGold else CardBg)
                                    .border(1.dp, if (isSelected) StarGold else CardBorder, CircleShape)
                                    .clickable { trackingScore = sc },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (sc == 0) "-" else "$sc",
                                    color = if (isSelected) BlackBg else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Action Buttons: Save & Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (userItem != null) {
                        OutlinedButton(
                            onClick = {
                                if (isAnime) onDeleteAnime(userItem.id) else onDeleteManga(userItem.id)
                                showTrackingSheet = false
                                onDismiss()
                            },
                            modifier = Modifier.weight(0.4f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Hapus", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            val updatedUserItem = userItem?.copy(
                                tracking = userItem.tracking.copy(
                                    status = trackingStatus,
                                    score = trackingScore,
                                    progress = trackingProgress,
                                    comments = trackingNotes
                                )
                            ) ?: UserMediaItem(
                                identity = mediaItem?.identity ?: MediaRef(),
                                metadata = MediaMetadata(
                                    title = title,
                                    titleEnglish = titleEnglish,
                                    imageUrl = imageUrl,
                                    type = type,
                                    totalEpisodes = totalEpisodes,
                                    totalChapters = totalChapters,
                                    status = extendedDetail?.airingStatus ?: "Finished",
                                    genres = extendedDetail?.genres ?: emptyList(),
                                    year = mediaItem?.year,
                                    format = mediaItem?.format,
                                    studio = extendedDetail?.studio,
                                    synopsis = cleanSynopsis
                                ),
                                tracking = MalTracking(
                                    status = trackingStatus,
                                    score = trackingScore,
                                    progress = trackingProgress,
                                    comments = trackingNotes
                                )
                            )

                            if (isAnime) onSaveAnime(updatedUserItem) else onSaveManga(updatedUserItem)
                            showTrackingSheet = false
                        },
                        modifier = Modifier.weight(if (userItem != null) 0.6f else 1f),
                        colors = ButtonDefaults.buttonColors(containerColor = themeAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Simpan ke Library", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MDLStatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
                Text(text = value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Text(text = label, color = TextMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun CastAvatarItem(
    cast: CharacterCastItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val imgUrl = cast.characterImage?.takeIf { it.isNotBlank() } ?: (cast.actorImage ?: "")
        AsyncImage(
            model = imgUrl,
            contentDescription = cast.characterName,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(2.dp, AccentBlue, CircleShape),
            contentScale = ContentScale.Crop
        )

        Text(
            text = cast.characterName,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val subText = (cast.actorName ?: "").ifBlank { cast.role ?: "" }
        if (subText.isNotBlank()) {
            Text(
                text = subText,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StaffAvatarItem(
    staff: StaffMemberItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AsyncImage(
            model = staff.image ?: "",
            contentDescription = staff.name,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(2.dp, MangaAccentDarkBlue, CircleShape),
            contentScale = ContentScale.Crop
        )

        Text(
            text = staff.name,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = staff.role,
            color = TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
