package com.canim.app.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.canim.app.data.model.*
import com.canim.app.ui.theme.*
import com.canim.app.util.TextSanitizer

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
    onOpenFullCast: (isCrew: Boolean) -> Unit,
    onOpenStudio: ((studioId: Int, studioName: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAnime = type == MediaType.ANIME
    val isManga = !isAnime
    val themeAccent = if (isManga) MangaAccentDarkBlue else AccentBlue
    val themeBorder = if (isManga) MangaCardBorder else CardBorder

    val userItem: UserMediaItem? = item as? UserMediaItem
    val mediaItem: MediaItem? = item as? MediaItem

    val title: String = userItem?.title ?: mediaItem?.title ?: ""
    val titleEnglish: String? = userItem?.metadata?.titleEnglish ?: mediaItem?.titleEnglish ?: extendedDetail?.titleEnglish
    val titleNative: String? = extendedDetail?.nativeTitle
    val imageUrl: String = userItem?.imageUrl ?: mediaItem?.imageUrl ?: ""
    val bannerUrl: String = imageUrl
    val synopsis: String = userItem?.synopsis ?: mediaItem?.synopsis ?: ""
    val cleanSynopsis: String = remember(synopsis) {
        TextSanitizer.sanitize(synopsis)
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

    val animeStatusOptions = listOf(
        "watching" to "Ditonton",
        "completed" to "Selesai",
        "on_hold" to "Ditunda",
        "dropped" to "Ditinggalkan",
        "plan_to_watch" to "Rencana Tonton"
    )
    val mangaStatusOptions = listOf(
        "reading" to "Dibaca",
        "completed" to "Selesai",
        "on_hold" to "Ditunda",
        "dropped" to "Ditinggalkan",
        "plan_to_read" to "Rencana Baca"
    )
    val currentStatusOptions = if (isAnime) animeStatusOptions else mangaStatusOptions

    Box(modifier = modifier.fillMaxSize().background(BlackBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Header Backdrop Image with Gradient Overlay (Optimized 220dp)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    AsyncImage(
                        model = bannerUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.85f),
                                        BlackBg
                                    )
                                )
                            )
                    )
                }
            }

            // MDL-Style Overlapping Info Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-55).dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Overlapping Poster Image
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

                    // Titles & Metadata
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
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
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Type & Format Badge
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (isAnime) AccentBlue.copy(alpha = 0.2f) else MangaAccentDarkBlue.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, themeAccent.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = if (isAnime) "ANIME" else "MANGA",
                                    color = themeAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            val fmt = userItem?.metadata?.format ?: mediaItem?.format ?: extendedDetail?.source
                            if (!fmt.isNullOrBlank()) {
                                Surface(
                                    color = CardElevated,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = fmt.uppercase(),
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Metrik & Statistik Utama (Rating MAL, Rating Pribadi, Peringkat, Popularitas, Anggota, Status Koleksi)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-14).dp)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "METRIK & STATISTIK",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // Baris 1: Rating MAL & Rating Pribadi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val effectiveScore = extendedDetail?.malScore
                                ?: extendedDetail?.averageScore
                                ?: (userItem?.score?.takeIf { it > 0 }?.toDouble() ?: mediaItem?.score)
                            val scoreStr = if (effectiveScore != null && effectiveScore > 0) {
                                String.format(java.util.Locale.US, "%.2f", effectiveScore)
                            } else {
                                "—"
                            }
                            val malLabel = if (extendedDetail?.malScore != null) "Rating MAL" else "Rating Publik"
                            MDLStatTile(
                                icon = Icons.Default.Star,
                                label = malLabel,
                                value = if (scoreStr == "—") scoreStr else "$scoreStr / 10",
                                color = StarGold,
                                modifier = Modifier.weight(1f)
                            )

                            val userScore = userItem?.score ?: 0
                            val userRatingStr = if (userScore > 0) "$userScore / 10" else "Belum Dinilai"
                            MDLStatTile(
                                icon = Icons.Default.Person,
                                label = "Rating Pribadi",
                                value = userRatingStr,
                                color = if (userScore > 0) StarGold else TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Baris 2: Peringkat & Popularitas
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val effectiveRank = extendedDetail?.malRank ?: extendedDetail?.rank
                            val rankStr = if (effectiveRank != null && effectiveRank > 0) "#${formatCompactNumber(effectiveRank)}" else "—"
                            MDLStatTile(
                                icon = Icons.Default.EmojiEvents,
                                label = "Peringkat",
                                value = rankStr,
                                color = AccentBlue,
                                modifier = Modifier.weight(1f)
                            )

                            val effectivePopularity = extendedDetail?.malPopularity ?: extendedDetail?.popularity
                            val popStr = if (effectivePopularity != null && effectivePopularity > 0) "#${formatCompactNumber(effectivePopularity)}" else "—"
                            MDLStatTile(
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                label = "Popularitas",
                                value = popStr,
                                color = AccentGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Baris 3: Status Koleksi & Anggota
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val currentStatusName = if (userItem != null) {
                                currentStatusOptions.firstOrNull { it.first == userItem.status }?.second ?: userItem.status
                            } else {
                                "Belum Ada di List"
                            }
                            MDLStatTile(
                                icon = Icons.Default.Bookmark,
                                label = "Status Koleksi",
                                value = currentStatusName,
                                color = if (userItem != null) themeAccent else TextMuted,
                                modifier = Modifier.weight(1f)
                            )

                            val effectiveMembers = extendedDetail?.malMembers ?: extendedDetail?.watchers
                            val membersStr = if (effectiveMembers != null && effectiveMembers > 0) formatCompactNumber(effectiveMembers) else "—"
                            MDLStatTile(
                                icon = Icons.Default.People,
                                label = "Anggota",
                                value = membersStr,
                                color = Color(0xFFA855F7),
                                modifier = Modifier.weight(1f)
                            )
                        }
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

            // Media Details Table Card
            item {
                Spacer(modifier = Modifier.height(12.dp))
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
                            text = "INFORMASI DETAIL",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        val studio = extendedDetail?.studio ?: userItem?.studio ?: mediaItem?.studio
                        if (!studio.isNullOrBlank()) {
                            val studioId = extendedDetail?.studioId
                            val canOpenStudio = isAnime && studioId != null && onOpenStudio != null
                            DetailRowItem(
                                label = if (isAnime) "Studio" else "Penerbit/Author",
                                value = studio,
                                isClickable = canOpenStudio,
                                onClick = if (canOpenStudio) { { onOpenStudio?.invoke(studioId!!, studio) } } else null
                            )
                        }

                        val duration = extendedDetail?.durationMinutes
                        if (duration != null && duration > 0) {
                            DetailRowItem(label = "Durasi", value = "$duration Menit/Ep")
                        }

                        val airingStatus = extendedDetail?.airingStatus ?: userItem?.airingStatus ?: mediaItem?.status
                        if (!airingStatus.isNullOrBlank()) {
                            DetailRowItem(label = "Status", value = airingStatus)
                        }

                        val startDate = extendedDetail?.startDate ?: userItem?.metadata?.year?.toString()
                        if (!startDate.isNullOrBlank()) {
                            DetailRowItem(label = "Tanggal Rilis", value = startDate)
                        }

                        val endDate = extendedDetail?.endDate
                        if (!endDate.isNullOrBlank()) {
                            DetailRowItem(label = "Tanggal Selesai", value = endDate)
                        }

                        val genres = extendedDetail?.genres?.takeIf { it.isNotEmpty() }
                            ?: userItem?.metadata?.genres ?: mediaItem?.genres ?: emptyList()
                        if (genres.isNotEmpty()) {
                            DetailRowItem(label = "Genre", value = genres.joinToString(", "))
                        }
                    }
                }
            }

            // Cast Section (Pemeran & Karakter)
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
                                text = "Lihat Semua",
                                color = themeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onOpenFullCast(false) }
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(castList.take(8)) { cast ->
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
                                text = "Lihat Semua",
                                color = themeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onOpenFullCast(true) }
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(staffList.take(8)) { staff ->
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recommendations) { rec ->
                            MediaItemMiniCard(item = rec, onClick = {})
                        }
                    }
                }
            }
        }

        // Floating Action Button (FAB) - Open Tracking Sheet
        FloatingActionButton(
            onClick = { showTrackingSheet = true },
            containerColor = themeAccent,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp)
        ) {
            Icon(
                imageVector = if (userItem != null) Icons.Default.Edit else Icons.Default.Add,
                contentDescription = if (userItem != null) "Edit Status" else "Tambah ke Library",
                modifier = Modifier.size(24.dp)
            )
        }

        // Bottom Sheet: Track Progress Dialog (Bagian 3.1: 2-Baris tanpa side-scroll)
        if (showTrackingSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTrackingSheet = false },
                containerColor = CardElevated,
                dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
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

                    // Status Chips: 2 Rows without horizontal scroll
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Status:", color = TextSecondary, fontSize = 12.sp)
                        val row1 = currentStatusOptions.take(3)
                        val row2 = currentStatusOptions.drop(3)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row1.forEach { (key, label) ->
                                val isSelected = trackingStatus == key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) themeAccent else CardBg)
                                        .border(1.dp, if (isSelected) themeAccent else CardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            trackingStatus = key
                                            if (key == "completed" && maxProgress > 0) {
                                                trackingProgress = maxProgress
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row2.forEach { (key, label) ->
                                val isSelected = trackingStatus == key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) themeAccent else CardBg)
                                        .border(1.dp, if (isSelected) themeAccent else CardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            trackingStatus = key
                                            if (key == "completed" && maxProgress > 0) {
                                                trackingProgress = maxProgress
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

                    // Personal Score (0 - 10): 2 Rows without horizontal scroll
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Rating Pribadi: ${if (trackingScore > 0) "★ $trackingScore / 10" else "Belum dinilai"}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        val scoreRow1 = (0..5).toList()
                        val scoreRow2 = (6..10).toList()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            scoreRow1.forEach { sc ->
                                val isSelected = trackingScore == sc
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) StarGold else CardBg)
                                        .border(1.dp, if (isSelected) StarGold else CardBorder, RoundedCornerShape(8.dp))
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            scoreRow2.forEach { sc ->
                                val isSelected = trackingScore == sc
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) StarGold else CardBg)
                                        .border(1.dp, if (isSelected) StarGold else CardBorder, RoundedCornerShape(8.dp))
                                        .clickable { trackingScore = sc },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$sc",
                                        color = if (isSelected) BlackBg else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Personal Notes
                    OutlinedTextField(
                        value = trackingNotes,
                        onValueChange = { trackingNotes = it },
                        label = { Text("Catatan Pribadi", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeAccent,
                            unfocusedBorderColor = CardBorder
                        )
                    )

                    // Save Button
                    Button(
                        onClick = {
                            val finalProgress = if (trackingStatus == "completed" && maxProgress > 0 && trackingProgress < maxProgress) {
                                maxProgress
                            } else {
                                trackingProgress
                            }
                            val tracking = MalTracking(
                                status = trackingStatus,
                                score = trackingScore,
                                progress = finalProgress,
                                comments = trackingNotes
                            )
                            val identity = userItem?.identity ?: mediaItem?.identity ?: MediaRef()
                            val metadata = userItem?.metadata ?: MediaMetadata(
                                title = title,
                                titleEnglish = titleEnglish,
                                titleNative = titleNative,
                                imageUrl = imageUrl,
                                type = type,
                                totalEpisodes = totalEpisodes,
                                totalChapters = totalChapters
                            )
                            val updatedUserItem = UserMediaItem(
                                identity = identity,
                                metadata = metadata,
                                tracking = tracking
                            )

                            if (isAnime) onSaveAnime(updatedUserItem) else onSaveManga(updatedUserItem)
                            showTrackingSheet = false
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = themeAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Simpan ke Library", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Top Gradient Scrim for Persistent Floating Action Buttons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Pinned Top-Left Back FAB
        IconButton(
            onClick = onDismiss,
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

        // Pinned Top-Right Delete FAB
        if (userItem != null) {
            var showDeleteDialog by remember { mutableStateOf(false) }
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 16.dp, top = 8.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, StatusDroppedColor.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus",
                    tint = StatusDroppedColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Hapus dari Koleksi", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    text = { Text("Apakah kamu yakin ingin menghapus \"$title\" dari koleksimu?", color = TextSecondary) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                if (isAnime) onDeleteAnime(userItem.id) else onDeleteManga(userItem.id)
                                onDismiss()
                            }
                        ) {
                            Text("Hapus", color = StatusDroppedColor, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Batal", color = TextSecondary)
                        }
                    },
                    containerColor = CardBg,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRowItem(
    label: String,
    value: String,
    isClickable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(0.4f))
        Text(
            text = if (isClickable) "$value ↗" else value,
            color = if (isClickable) AccentBlue else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isClickable) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun MDLStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = CardElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    color = if (value == "—" || value == "Belum Dinilai" || value == "Belum Ada di List") TextSecondary else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            .width(92.dp)
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
            maxLines = 2,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )

        val subText = (cast.actorName ?: "").ifBlank { cast.role ?: "" }
        if (subText.isNotBlank()) {
            Text(
                text = subText,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 2,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
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
            .width(92.dp)
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
            maxLines = 2,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = staff.role,
            color = TextMuted,
            fontSize = 10.sp,
            maxLines = 2,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MediaItemMiniCard(
    item: MediaItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            modifier = Modifier
                .width(100.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
