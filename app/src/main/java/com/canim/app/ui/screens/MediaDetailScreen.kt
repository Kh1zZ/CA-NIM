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
import com.canim.app.ui.components.CanimAsyncImage
import com.canim.app.data.model.*
import com.canim.app.ui.theme.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.canim.app.ui.viewmodel.library.LibraryViewModel
import com.canim.app.ui.viewmodel.library.LibraryUiState
import com.canim.app.util.TextSanitizer
import com.canim.app.util.MediaDisplayFormatter
import com.canim.app.data.repository.StudioBioRegistry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaDetailScreen(
    item: Any,
    type: MediaType,
    extendedDetail: ExtendedMediaDetail?,
    isLoadingExtendedDetail: Boolean,
    libraryViewModel: LibraryViewModel? = null,
    onSaveAnime: (UserMediaItem) -> Unit,
    onSaveManga: (UserMediaItem) -> Unit,
    onDeleteAnime: (String) -> Unit,
    onDeleteManga: (String) -> Unit,
    onOpenCastCrew: (Int, Boolean) -> Unit,
    onOpenFullCast: (isCrew: Boolean) -> Unit,
    onOpenStudio: ((studioId: Int, studioName: String) -> Unit)? = null,
    onOpenMediaDetail: ((MediaItem, MediaType) -> Unit)? = null,
    onResolveAniListId: ((malId: Int) -> Int?)? = null,
    onSaveScrollPosition: ((key: String, index: Int, offset: Int) -> Unit)? = null,
    onGetScrollPosition: ((key: String) -> Pair<Int, Int>)? = null,
    onGenreClick: ((String) -> Unit)? = null,
    onRankClick: ((MediaType) -> Unit)? = null,
    onRefresh: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAnime = type == MediaType.ANIME
    val isManga = !isAnime
    val themeAccent = if (isManga) MangaAccentDarkBlue else AccentBlue
    val themeBorder = if (isManga) MangaCardBorder else CardBorder

    val libraryState = libraryViewModel?.libraryState?.collectAsState()?.value
    val localUserItem: UserMediaItem? = remember(item, type, libraryState?.animeList, libraryState?.mangaList) {
        if (item is UserMediaItem) {
            item
        } else {
            val list = if (isAnime) libraryState?.animeList ?: emptyList() else libraryState?.mangaList ?: emptyList()
            when (item) {
                is MediaItem -> list.firstOrNull {
                    (item.malId != null && it.malId == item.malId) ||
                    (item.anilistId != null && it.anilistId == item.anilistId) ||
                    it.title.equals(item.title, ignoreCase = true)
                }
                is String -> list.firstOrNull { it.id == item || it.malId?.toString() == item }
                is Int -> list.firstOrNull { it.malId == item || it.anilistId == item }
                else -> null
            }
        }
    }
    val userItem: UserMediaItem? = localUserItem ?: (item as? UserMediaItem)
    val mediaItem: MediaItem? = item as? MediaItem

    val itemKey = remember(item) {
        userItem?.anilistId?.toString()
            ?: userItem?.malId?.toString()
            ?: mediaItem?.anilistId?.toString()
            ?: mediaItem?.malId?.toString()
            ?: userItem?.title
            ?: mediaItem?.title
            ?: item.toString()
    }

    val initialPos = remember(itemKey) { onGetScrollPosition?.invoke(itemKey) ?: Pair(0, 0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialPos.first,
        initialFirstVisibleItemScrollOffset = initialPos.second
    )

    DisposableEffect(itemKey) {
        onDispose {
            onSaveScrollPosition?.invoke(
                itemKey,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    val airingItem: com.canim.app.data.model.AiringAnimeItem? = item as? com.canim.app.data.model.AiringAnimeItem
    val title: String = userItem?.title?.takeIf { it.isNotBlank() } ?: mediaItem?.title?.takeIf { it.isNotBlank() } ?: airingItem?.title?.takeIf { it.isNotBlank() } ?: extendedDetail?.title ?: ""
    val titleEnglish: String? = userItem?.metadata?.titleEnglish?.takeIf { it.isNotBlank() } ?: mediaItem?.titleEnglish?.takeIf { it.isNotBlank() } ?: airingItem?.titleEnglish?.takeIf { it.isNotBlank() } ?: extendedDetail?.titleEnglish
    val titleNative: String? = extendedDetail?.nativeTitle
    val imageUrl: String = userItem?.imageUrl?.takeIf { it.isNotBlank() } ?: mediaItem?.imageUrl?.takeIf { it.isNotBlank() } ?: airingItem?.imageUrl?.takeIf { it.isNotBlank() } ?: extendedDetail?.coverImage?.takeIf { it.isNotBlank() } ?: ""
    val bannerUrl: String = imageUrl
    val synopsis: String = userItem?.synopsis?.takeIf { it.isNotBlank() } ?: mediaItem?.synopsis?.takeIf { it.isNotBlank() } ?: extendedDetail?.synopsis ?: ""
    val cleanSynopsis: String = remember(synopsis) {
        TextSanitizer.sanitize(synopsis)
    }

    val totalEpisodes = extendedDetail?.episodes ?: userItem?.totalEpisodes ?: mediaItem?.episodes ?: airingItem?.episodes ?: 0
    val totalChapters = extendedDetail?.chapters ?: userItem?.totalChapters ?: mediaItem?.chapters ?: 0
    val maxProgress = if (isAnime) totalEpisodes else totalChapters

    var showTrackingSheet by remember { mutableStateOf(false) }
    var trackingStatus by remember { mutableStateOf(userItem?.status ?: if (isAnime) "watching" else "reading") }
    var trackingScore by remember { mutableIntStateOf(userItem?.score ?: 0) }
    var trackingProgress by remember { mutableIntStateOf(userItem?.progress ?: 0) }
    var trackingNotes by remember { mutableStateOf(userItem?.notes ?: "") }

    LaunchedEffect(userItem) {
        if (userItem != null) {
            trackingStatus = userItem.status
            trackingScore = userItem.score
            trackingProgress = userItem.progress
            trackingNotes = userItem.notes
        }
    }

    var showFullTitleSynopsisSheet by remember { mutableStateOf(false) }

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

    Box(modifier = modifier.fillMaxSize().background(BlackBg)) {
        val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        com.canim.app.ui.components.CanimPullToRefreshLayout(
            isRefreshing = isLoadingExtendedDetail,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
            // Header Backdrop Image with Gradient Overlay & Back Button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (screenWidthDp >= 600) 300.dp else 220.dp)
                ) {
                    CanimAsyncImage(
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
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Black.copy(alpha = 0.85f),
                                        BlackBg
                                    )
                                )
                            )
                    )
                }
            }

            // MDL-Style Overlapping Info Section (Sesuai Referensi Gambar)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-55).dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Overlapping Poster Image
                    CanimAsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .width(115.dp)
                            .height(165.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, themeBorder, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Titles, Expand Icon & Truncated Synopsis & Badges
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Clickable area for Title + English Title + Synopsis
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showFullTitleSynopsisSheet = true },
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Title & Expand Diagonal Icon Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = title,
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Lihat Detail Judul & Sinopsis",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .padding(start = 4.dp, top = 2.dp)
                                        .size(17.dp)
                                )
                            }

                            if (!titleEnglish.isNullOrBlank() && titleEnglish != title) {
                                Text(
                                    text = titleEnglish,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Naturally truncated synopsis directly under title
                            if (cleanSynopsis.isNotBlank()) {
                                Text(
                                    text = cleanSynopsis,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

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

                            val fmt = extendedDetail?.format ?: userItem?.metadata?.format ?: mediaItem?.format ?: extendedDetail?.source
                            if (!fmt.isNullOrBlank()) {
                                Surface(
                                    color = CardElevated,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = MediaDisplayFormatter.formatFormat(fmt).uppercase(),
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

            // Metrik & Skor Terintegrasi (Invisible Continuity High-Density Metric Flow)
            item {
                val effectiveScore = extendedDetail?.malScore
                val scoreStr = when {
                    effectiveScore != null && effectiveScore > 0 ->
                        String.format(java.util.Locale.US, "%.2f", effectiveScore)
                    isLoadingExtendedDetail -> "..."
                    else -> "Belum Dinilai"
                }
                val userScore = userItem?.score ?: 0
                val effectiveRank = extendedDetail?.malRank ?: extendedDetail?.rank
                val rankStr = MediaDisplayFormatter.formatMetricRank(effectiveRank)
                val effectivePopularity = extendedDetail?.malPopularity ?: extendedDetail?.popularity
                val popStr = MediaDisplayFormatter.formatMetricPopularity(effectivePopularity)
                val effectiveMembers = extendedDetail?.malMembers ?: extendedDetail?.watchers
                val membersStr = MediaDisplayFormatter.formatMetricMembers(effectiveMembers)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-14).dp),
                    shape = RoundedCornerShape(16.dp),
                    color = CardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Baris 1: Hero Score MAL & Rating Pribadi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(StarGold.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = StarGold,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = scoreStr,
                                            color = TextPrimary,
                                            fontSize = if (scoreStr == "Belum Dinilai") 16.sp else 22.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        if (scoreStr != "Belum Dinilai" && scoreStr != "...") {
                                            Text(
                                                text = "/ 10",
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Skor Resmi MyAnimeList",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Personal Rating Chip
                            Surface(
                                color = if (userScore > 0) AccentBlue.copy(alpha = 0.15f) else CardElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (userScore > 0) AccentBlue.copy(alpha = 0.35f) else Color.Transparent
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (userScore > 0) StarGold else TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (userScore > 0) "$userScore / 10" else "Beri Nilai",
                                            color = if (userScore > 0) TextPrimary else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Rating Kamu",
                                            color = TextMuted,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Hairline Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(DividerSubtle)
                        )

                        // Baris 2: 3 Kolom Metrik Terintegrasi (Rank, Popularitas, Anggota)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = onRankClick != null) { onRankClick?.invoke(type) }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = rankStr,
                                    color = AccentBlue,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "PERINGKAT",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp
                                )
                            }

                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(DividerSubtle))

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = popStr,
                                    color = AccentGreen,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "POPULARITAS",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp
                                )
                            }

                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(DividerSubtle))

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = membersStr,
                                    color = Color(0xFFA855F7),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "ANGGOTA",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
            }

            if (extendedDetail?.isFromFallback == true) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(StarGold.copy(alpha = 0.12f))
                            .border(1.dp, StarGold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Fallback Notice",
                                tint = StarGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Server AniList sedang offline. Detail utama, skor, & studio disajikan melalui MyAnimeList.",
                                color = StarGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Media Details Table Section (Card Layout with Icons & Indonesian Formatting)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "INFORMASI DETAIL",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val studio = if (isAnime) {
                                extendedDetail?.studio ?: userItem?.studio ?: mediaItem?.studio
                            } else {
                                extendedDetail?.publisher ?: extendedDetail?.studio ?: userItem?.studio ?: mediaItem?.studio
                            }
                            if (!studio.isNullOrBlank()) {
                                val studioId = extendedDetail?.studioId
                                    ?: StudioBioRegistry.findStudioIdByName(studio)
                                    ?: 0
                                val canOpenStudio = isAnime && onOpenStudio != null
                                DetailRowItem(
                                    label = if (isAnime) "Studio" else "Penerbit/Author",
                                    value = studio,
                                    icon = if (isAnime) Icons.Default.Movie else Icons.Default.AutoStories,
                                    isClickable = canOpenStudio,
                                    onClick = if (canOpenStudio) { { onOpenStudio?.invoke(studioId, studio) } } else null
                                )
                                HorizontalDivider(color = DividerSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            val rawStatus = extendedDetail?.airingStatus ?: userItem?.airingStatus ?: mediaItem?.status
                            if (!rawStatus.isNullOrBlank()) {
                                DetailRowItem(
                                    label = "Status",
                                    value = MediaDisplayFormatter.formatStatus(rawStatus),
                                    icon = Icons.Default.PlayCircleOutline
                                )
                                HorizontalDivider(color = DividerSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            val rawFormat = extendedDetail?.format ?: userItem?.metadata?.format ?: mediaItem?.format ?: extendedDetail?.source
                            val isMovie = rawFormat?.equals("movie", ignoreCase = true) == true

                            val episodes = extendedDetail?.episodes ?: userItem?.metadata?.totalEpisodes ?: mediaItem?.episodes
                            if (isAnime && !isMovie && episodes != null && episodes > 0) {
                                DetailRowItem(
                                    label = "Total Episode",
                                    value = "$episodes Episode",
                                    icon = Icons.Default.VideoLibrary
                                )
                                HorizontalDivider(color = DividerSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            val chapters = extendedDetail?.chapters ?: userItem?.metadata?.totalChapters ?: mediaItem?.chapters
                            if (isManga && chapters != null && chapters > 0) {
                                DetailRowItem(
                                    label = "Total Chapter",
                                    value = "$chapters Chapter",
                                    icon = Icons.Default.LibraryBooks
                                )
                                HorizontalDivider(color = DividerSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            val durationStr = MediaDisplayFormatter.formatDuration(extendedDetail?.durationMinutes, rawFormat)
                            if (durationStr != null) {
                                DetailRowItem(
                                    label = "Durasi",
                                    value = durationStr,
                                    icon = Icons.Default.Schedule
                                )
                                HorizontalDivider(color = DividerSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            if (!rawFormat.isNullOrBlank()) {
                                DetailRowItem(
                                    label = "Format",
                                    value = MediaDisplayFormatter.formatFormat(rawFormat),
                                    icon = if (isMovie) Icons.Default.Movie else Icons.Default.Tv
                                )
                                HorizontalDivider(color = DividerSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            val rawSource = extendedDetail?.source
                            if (!rawSource.isNullOrBlank()) {
                                DetailRowItem(
                                    label = "Sumber Cerita",
                                    value = MediaDisplayFormatter.formatSource(rawSource),
                                    icon = Icons.Default.MenuBook
                                )
                                HorizontalDivider(color = DividerSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            val season = userItem?.metadata?.season ?: mediaItem?.season
                            val year = userItem?.metadata?.year ?: mediaItem?.year
                            val seasonYearStr = MediaDisplayFormatter.formatSeasonYear(season, year)
                            if (!seasonYearStr.isNullOrBlank()) {
                                DetailRowItem(
                                    label = "Musim Rilis",
                                    value = seasonYearStr,
                                    icon = Icons.Default.CalendarToday
                                )
                                HorizontalDivider(color = DividerSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            val startDate = MediaDisplayFormatter.formatDateIndonesian(
                                extendedDetail?.startDate ?: userItem?.metadata?.year?.toString()
                            )
                            if (!startDate.isNullOrBlank()) {
                                DetailRowItem(
                                    label = "Tanggal Rilis",
                                    value = startDate,
                                    icon = Icons.Default.Event
                                )
                                HorizontalDivider(color = DividerSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            val endDate = MediaDisplayFormatter.formatDateIndonesian(extendedDetail?.endDate)
                            if (!endDate.isNullOrBlank()) {
                                DetailRowItem(
                                    label = "Tanggal Selesai",
                                    value = endDate,
                                    icon = Icons.Default.EventAvailable
                                )
                                HorizontalDivider(color = DividerSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }

                            val genres = extendedDetail?.genres?.takeIf { it.isNotEmpty() }
                                ?: userItem?.metadata?.genres ?: mediaItem?.genres ?: emptyList()
                            if (genres.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalOffer,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Genre",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        genres.forEach { genre ->
                                            Surface(
                                                color = CardElevated,
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle),
                                                modifier = Modifier.clickable(enabled = onGenreClick != null) {
                                                    onGenreClick?.invoke(genre)
                                                }
                                            ) {
                                                Text(
                                                    text = genre,
                                                    color = TextPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Cast Section (Pemeran & Karakter)
            val castList: List<CharacterCastItem> = extendedDetail?.cast ?: emptyList()
            val isFallbackDetail = extendedDetail?.isFromFallback == true
            if (castList.isNotEmpty() || isLoadingExtendedDetail || isFallbackDetail) {
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
                            text = if (castList.isNotEmpty()) "PEMERAN & KARAKTER (${castList.size})" else "PEMERAN & KARAKTER",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (castList.isNotEmpty()) {
                            Text(
                                text = "Lihat Semua",
                                color = themeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onOpenFullCast(false) }
                            )
                        }
                    }

                    if (isLoadingExtendedDetail) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = themeAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Memuat daftar pemeran...",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    } else if (castList.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                castList.take(8),
                                key = { "${it.characterId}_${it.actorId}_${it.characterName}" },
                                contentType = { "cast_item" }
                            ) { cast ->
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
                    } else if (isFallbackDetail) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardBg)
                                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Daftar pengisi suara & karakter hanya tersedia di AniList. Karena server AniList sedang tidak merespon / offline (HTTP 403), data ini sementara tidak dapat dimuat.",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
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
                        Text(
                            text = "Lihat Semua",
                            color = themeAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onOpenFullCast(true) }
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            staffList.take(8),
                            key = { "${it.staffId}_${it.name}_${it.role}" },
                            contentType = { "staff_item" }
                        ) { staff ->
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

            // Relations Section (Prekuel, Sekuel, Adaptasi, dsb)
            val relationsList: List<MediaRelationItem> = extendedDetail?.relations ?: emptyList()
            if (relationsList.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "RELASI & ADAPTASI (${relationsList.size})",
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
                        items(
                            relationsList,
                            key = { "${it.id}_${it.relationType}_${it.title}" },
                            contentType = { "relation_item" }
                        ) { rel ->
                            RelationCardItem(
                                relation = rel,
                                onClick = {
                                    onSaveScrollPosition?.invoke(
                                        itemKey,
                                        listState.firstVisibleItemIndex,
                                        listState.firstVisibleItemScrollOffset
                                    )
                                    val effectiveAniId = if (rel.id > 0 && rel.id != rel.malId) {
                                        rel.id
                                    } else {
                                        rel.malId?.let { onResolveAniListId?.invoke(it) }
                                    }
                                    val relMedia = MediaItem(
                                        malId = rel.malId,
                                        anilistId = effectiveAniId,
                                        title = rel.title,
                                        imageUrl = rel.imageUrl ?: "",
                                        type = rel.type,
                                        format = rel.format,
                                        status = rel.status
                                    )
                                    onOpenMediaDetail?.invoke(relMedia, rel.type)
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
                        items(
                            recommendations,
                            key = { "${it.type}_${it.malId}_${it.anilistId}_${it.title}" },
                            contentType = { "rec_item" }
                        ) { rec ->
                            MediaItemMiniCard(
                                item = rec,
                                onClick = {
                                    onSaveScrollPosition?.invoke(
                                        itemKey,
                                        listState.firstVisibleItemIndex,
                                        listState.firstVisibleItemScrollOffset
                                    )
                                    onOpenMediaDetail?.invoke(rec, rec.type)
                                }
                            )
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
            val trackingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showTrackingSheet = false },
                sheetState = trackingSheetState,
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
                        val trackingFormat = extendedDetail?.format ?: userItem?.metadata?.format ?: mediaItem?.format ?: extendedDetail?.source
                        val isMovieTracking = trackingFormat?.equals("movie", ignoreCase = true) == true
                        Text(
                            text = if (isAnime) {
                                if (isMovieTracking) "Status Tonton Film (1 Film):"
                                else "Progres Episode (Total: ${if (totalEpisodes > 0) totalEpisodes else "?"}):"
                            } else "Progres Bab (Total: ${if (totalChapters > 0) totalChapters else "?"}):",
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
                            val effectiveMalId = userItem?.malId ?: mediaItem?.malId ?: airingItem?.malId ?: extendedDetail?.malId
                            val effectiveAniId = userItem?.anilistId ?: mediaItem?.anilistId ?: airingItem?.anilistId ?: extendedDetail?.anilistId
                            val identity = userItem?.identity ?: mediaItem?.identity ?: MediaRef(anilistId = effectiveAniId, malId = effectiveMalId)
                            val initialGenres = userItem?.metadata?.genres
                                ?: mediaItem?.genres
                                ?: airingItem?.genres
                                ?: extendedDetail?.genres
                                ?: emptyList()
                            val targetMetadata = userItem?.metadata ?: MediaMetadata(
                                title = title,
                                titleEnglish = titleEnglish,
                                titleNative = titleNative,
                                imageUrl = imageUrl,
                                type = type,
                                totalEpisodes = totalEpisodes,
                                totalChapters = totalChapters,
                                status = userItem?.airingStatus ?: mediaItem?.status ?: extendedDetail?.airingStatus,
                                studio = userItem?.studio ?: mediaItem?.studio ?: airingItem?.studio ?: extendedDetail?.studio,
                                genres = initialGenres,
                                synopsis = synopsis
                            )
                            val updatedUserItem = UserMediaItem(
                                identity = identity,
                                metadata = targetMetadata,
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

        // Full Title & Synopsis Modal Bottom Sheet (Expand Icon Triggered)
        if (showFullTitleSynopsisSheet) {
            val fullSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showFullTitleSynopsisSheet = false },
                sheetState = fullSheetState,
                containerColor = CardElevated,
                dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!titleEnglish.isNullOrBlank() && titleEnglish != title) {
                        Text(
                            text = "English: $titleEnglish",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if (!titleNative.isNullOrBlank() && titleNative != title) {
                        Text(
                            text = "Native: $titleNative",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    HorizontalDivider(color = CardBorder, thickness = 1.dp)
                    Text(
                        text = "Sinopsis Lengkap",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = cleanSynopsis.ifBlank { "Tidak ada sinopsis tersedia." },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
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
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isClickable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClick() }
                        .padding(vertical = 3.dp)
                } else {
                    Modifier.padding(vertical = 3.dp)
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(0.42f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isClickable) AccentBlue else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = if (isClickable) "$value ↗" else value,
            color = if (isClickable) AccentBlue else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isClickable) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
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
        shape = RoundedCornerShape(10.dp),
        color = CardBg
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
    val imgUrl = cast.characterImage?.takeIf { it.isNotBlank() } ?: (cast.actorImage ?: "")
    val roleText = (cast.role ?: "character").lowercase()

    Column(
        modifier = Modifier
            .width(105.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF242228))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f)
        ) {
            CanimAsyncImage(
                model = imgUrl,
                contentDescription = cast.characterName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = roleText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cast.characterName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
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
    val roleText = staff.role.lowercase()

    Column(
        modifier = Modifier
            .width(105.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF242228))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f)
        ) {
            CanimAsyncImage(
                model = staff.image ?: "",
                contentDescription = staff.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = roleText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = staff.name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RelationCardItem(
    relation: MediaRelationItem,
    onClick: () -> Unit
) {
    val relationLabel = when (relation.relationType.uppercase()) {
        "PREQUEL" -> "Prekuel"
        "SEQUEL" -> "Sekuel"
        "SOURCE" -> "Sumber"
        "SPIN_OFF" -> "Spin-off"
        "SIDE_STORY" -> "Side Story"
        "ALTERNATIVE" -> "Alternatif"
        "CHARACTER" -> "Karakter"
        "SUMMARY" -> "Ringkasan"
        else -> relation.relationType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    Column(
        modifier = Modifier
            .width(105.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF242228))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f)
        ) {
            CanimAsyncImage(
                model = relation.imageUrl ?: "",
                contentDescription = relation.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = relationLabel.lowercase(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = relation.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        CanimAsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            modifier = Modifier
                .width(100.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardElevated, RoundedCornerShape(8.dp)),
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
