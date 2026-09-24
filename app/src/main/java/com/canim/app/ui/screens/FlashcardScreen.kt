package com.canim.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.canim.app.ui.components.CanimAsyncImage
import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.ui.theme.*
import kotlinx.coroutines.launch

private val PhysicalCardShape = RoundedCornerShape(20.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    deck: List<MediaItem>,
    credits: Int,
    isLoading: Boolean,
    onBack: () -> Unit,
    onConsumeCredit: () -> Boolean,
    onSwipeCard: (MediaItem) -> Unit,
    onOpenDetail: (MediaItem, MediaType) -> Unit,
    onRefreshDeck: () -> Unit,
    onSavePlanToWatch: (MediaItem, (Boolean) -> Unit) -> Unit = { _, cb -> cb(true) },
    onCardRevealed: (MediaItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenWidthPx = with(density) { screenWidth.toPx() }
    val swipeThreshold = screenWidthPx * 0.35f

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(deck) {
        val imageLoader = coil.Coil.imageLoader(context)
        deck.take(5).forEach { item ->
            val url = item.imageUrlHd ?: item.imageUrl
            if (url.isNotBlank()) {
                val req = coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
                imageLoader.enqueue(req)
            }
        }
    }

    // Animated translation and rotation for the top card
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var isSwiping by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BlackBg,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = "Flashcard Gacha",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Eksplorasi Anime",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    // Credit indicator badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardElevated,
                        border = BorderStroke(1.dp, if (credits > 0) AccentBlue.copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.5f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = null,
                                tint = if (credits > 0) AccentBlue else Color(0xFFEF4444),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$credits Tiket",
                                color = if (credits > 0) AccentBlue else Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBg)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                // A.1: "Mengacak kartu" loading state with staggered face-down stacking & locked user interaction
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                // Block all touch gestures during shuffle
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .fillMaxHeight(0.90f),
                            contentAlignment = Alignment.Center
                        ) {
                            for (i in 2 downTo 0) {
                                val scale = 1f - (i * 0.05f)
                                val verticalOffset = (i * 14).dp
                                CardBack(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset(y = verticalOffset)
                                        .scale(scale)
                                        .graphicsLayer { alpha = 1f - (i * 0.25f) }
                                )
                            }

                            // Shuffling overlay pill
                            Surface(
                                color = Color.Black.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp,
                                        color = AccentBlue
                                    )
                                    Text(
                                        text = "Mengacak kartu...",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                credits <= 0 -> {
                    EmptyCreditState(onBack = onBack)
                }
                deck.isEmpty() -> {
                    LaunchedEffect(Unit) {
                        onRefreshDeck()
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(color = AccentBlue, strokeWidth = 3.dp)
                            Text(
                                text = "Menyiapkan kartu gacha...",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                else -> {
                    val topCard = deck.first()
                    val backgroundCards = deck.drop(1).take(2)

                    // Card Flip State for current topCard
                    var isCardFlipped by remember(topCard.id) { mutableStateOf(false) }
                    var isFlipping by remember(topCard.id) { mutableStateOf(false) }
                    var isAnimating by remember(topCard.id) { mutableStateOf(false) }
                    val flipRotation = remember(topCard.id) { Animatable(0f) }

                    val flipToFront: () -> Unit = {
                        if (!isCardFlipped && !isFlipping && !isAnimating && credits > 0) {
                            isFlipping = true
                            isAnimating = true
                            onCardRevealed(topCard)
                            coroutineScope.launch {
                                flipRotation.animateTo(
                                    targetValue = 180f,
                                    animationSpec = tween(durationMillis = 380, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                )
                                isCardFlipped = true
                                isFlipping = false
                                isAnimating = false
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Card Stack Container (Face-Down Stack -> Tap to Flip 180°)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Background cards render face-down
                            backgroundCards.asReversed().forEachIndexed { index, _ ->
                                val depth = backgroundCards.size - index
                                val scale = 1f - (depth * 0.05f)
                                val verticalOffset = (depth * 14).dp

                                CardBack(
                                    modifier = Modifier
                                        .fillMaxWidth(0.88f)
                                        .fillMaxHeight(0.90f)
                                        .offset(y = verticalOffset)
                                        .scale(scale)
                                        .graphicsLayer { alpha = 1f - (depth * 0.25f) }
                                )
                            }

                            // Top Card with Flip & Drag Physics
                            val rotationZ = (offsetX.value / screenWidthPx) * 20f

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .fillMaxHeight(0.90f)
                                    .offset(
                                        x = with(density) { offsetX.value.toDp() },
                                        y = with(density) { offsetY.value.toDp() }
                                    )
                                    .then(
                                        if (isCardFlipped && !isAnimating) {
                                            Modifier.pointerInput(topCard.id) {
                                                detectDragGestures(
                                                    onDragStart = { isSwiping = true },
                                                    onDragEnd = {
                                                        isSwiping = false
                                                        if (isAnimating) return@detectDragGestures
                                                        coroutineScope.launch {
                                                            if (kotlin.math.abs(offsetX.value) > swipeThreshold) {
                                                                val isRight = offsetX.value > 0
                                                                isAnimating = true
                                                                if (isRight) {
                                                                    onSavePlanToWatch(topCard) { success ->
                                                                        coroutineScope.launch {
                                                                            if (success) {
                                                                                onConsumeCredit()
                                                                                offsetX.animateTo(screenWidthPx * 1.5f, tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                                                                onSwipeCard(topCard)
                                                                                offsetX.snapTo(0f)
                                                                                offsetY.snapTo(0f)
                                                                                isCardFlipped = false
                                                                                flipRotation.snapTo(0f)
                                                                            } else {
                                                                                offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
                                                                                offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
                                                                            }
                                                                            isAnimating = false
                                                                        }
                                                                    }
                                                                } else {
                                                                    // Swipe Left = Lewati / Discard (hanya dismiss, kurangi 1 tiket gacha)
                                                                    onConsumeCredit()
                                                                    offsetX.animateTo(-screenWidthPx * 1.5f, tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                                                    onSwipeCard(topCard)
                                                                    offsetX.snapTo(0f)
                                                                    offsetY.snapTo(0f)
                                                                    isCardFlipped = false
                                                                    flipRotation.snapTo(0f)
                                                                    isAnimating = false
                                                                }
                                                            } else {
                                                                launch {
                                                                    offsetX.animateTo(
                                                                        0f,
                                                                        spring(
                                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                                            stiffness = Spring.StiffnessMedium
                                                                        )
                                                                    )
                                                                }
                                                                launch {
                                                                    offsetY.animateTo(
                                                                        0f,
                                                                        spring(
                                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                                            stiffness = Spring.StiffnessMedium
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        isSwiping = false
                                                        coroutineScope.launch {
                                                            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                                            offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                                        }
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        if (isAnimating) return@detectDragGestures
                                                        change.consume()
                                                        coroutineScope.launch {
                                                            offsetX.snapTo(offsetX.value + dragAmount.x)
                                                            offsetY.snapTo(offsetY.value + dragAmount.y * 0.35f)
                                                        }
                                                    }
                                                )
                                            }
                                        } else if (!isCardFlipped && !isAnimating) {
                                            Modifier.clickable { flipToFront() }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .graphicsLayer {
                                        this.rotationZ = rotationZ
                                        this.rotationY = flipRotation.value
                                        cameraDistance = 12f * density.density
                                    }
                            ) {
                                if (flipRotation.value <= 90f) {
                                    CardBack(
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    // Face-up card: counter-rotated Y to avoid mirroring
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                this.rotationY = 180f
                                            }
                                    ) {
                                        PhysicalCard(
                                            item = topCard,
                                            modifier = Modifier.fillMaxSize(),
                                            isInteractive = false,
                                            onClick = { /* Body tap must NOT open detail! */ }
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Controls
                        if (!isCardFlipped) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { flipToFront() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Buka Kartu Gacha",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Trash / Lewati Button (DeleteOutline)
                                IconButton(
                                    onClick = {
                                        if (isAnimating) return@IconButton
                                        isAnimating = true
                                        coroutineScope.launch {
                                            onConsumeCredit()
                                            offsetX.animateTo(-screenWidthPx * 1.5f, tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                            onSwipeCard(topCard)
                                            offsetX.snapTo(0f)
                                            offsetY.snapTo(0f)
                                            isCardFlipped = false
                                            flipRotation.snapTo(0f)
                                            isAnimating = false
                                        }
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(CardElevated)
                                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Lewati Kartu",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // 2. Info Button (Info - Sole navigation path to detail)
                                IconButton(
                                    onClick = {
                                        if (isAnimating) return@IconButton
                                        onOpenDetail(topCard, topCard.type)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(CardElevated)
                                        .border(1.dp, AccentBlue.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Lihat Detail Anime",
                                        tint = AccentBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // 3. Plus Button (Add - Add to Library as "Rencana")
                                var isSavingCard by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = {
                                        if (isSavingCard || isAnimating) return@IconButton
                                        isSavingCard = true
                                        isAnimating = true
                                        onSavePlanToWatch(topCard) { success ->
                                            isSavingCard = false
                                            if (success) {
                                                coroutineScope.launch {
                                                    onConsumeCredit()
                                                    offsetX.animateTo(screenWidthPx * 1.5f, tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                                    onSwipeCard(topCard)
                                                    offsetX.snapTo(0f)
                                                    offsetY.snapTo(0f)
                                                    isCardFlipped = false
                                                    flipRotation.snapTo(0f)
                                                    isAnimating = false
                                                }
                                            } else {
                                                isAnimating = false
                                            }
                                        }
                                    },
                                    enabled = !isSavingCard && !isAnimating,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(AccentBlue)
                                        .border(1.dp, AccentBlue.copy(alpha = 0.8f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Tambah ke Rencana",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
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

/**
 * Cyber/Neon Card Back for Face-Down Gacha Stacking
 */
@Composable
private fun CardBack(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(
                width = 1.8.dp,
                brush = Brush.linearGradient(
                    listOf(
                        AccentBlue.copy(alpha = 0.9f),
                        Color(0xFF8B5CF6).copy(alpha = 0.7f),
                        AccentBlue.copy(alpha = 0.9f)
                    )
                ),
                shape = PhysicalCardShape
            )
            .clip(PhysicalCardShape),
        shape = PhysicalCardShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0F172A),
                            Color(0xFF020617)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    AccentBlue.copy(alpha = 0.25f),
                                    Color(0xFF8B5CF6).copy(alpha = 0.35f),
                                    AccentBlue.copy(alpha = 0.25f)
                                )
                            )
                        )
                        .border(2.dp, AccentBlue.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "CA'NIM",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )

                Text(
                    text = "GACHA CARD",
                    color = AccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(26.dp))

                Surface(
                    color = CardElevated.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Ketuk untuk membuka",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhysicalCard(
    item: MediaItem,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    listOf(AccentBlue.copy(alpha = 0.8f), Color(0xFF6366F1).copy(alpha = 0.3f))
                ),
                shape = PhysicalCardShape
            )
            .clip(PhysicalCardShape)
            .clickable(enabled = isInteractive, onClick = onClick),
        shape = PhysicalCardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // High-resolution Cover Poster (HD preferred on Flashcards)
            CanimAsyncImage(
                model = item.imageUrlHd ?: item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top gradient overlay for chips
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
            )

            // Top Badges (Format & Rating)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Format Chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text(
                        text = item.format ?: "ANIME",
                        color = AccentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Score Chip
                if (item.score != null && item.score > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, StarGold.copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = StarGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "%.1f".format(item.score),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Bottom gradient overlay for info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f),
                                Color.Black
                            )
                        )
                    )
            )

            // Bottom Metadata Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Title
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Secondary Title (English if different)
                if (!item.titleEnglish.isNullOrBlank() && item.titleEnglish != item.title) {
                    Text(
                        text = item.titleEnglish,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Quick specs (Studio • Episodes • Year)
                val specs = listOfNotNull(
                    item.studio?.takeIf { it.isNotBlank() },
                    item.episodes?.let { "$it Ep" },
                    item.year?.toString()
                ).joinToString(" • ")

                if (specs.isNotBlank()) {
                    Text(
                        text = specs,
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Genres
                if (item.genres.isNotEmpty()) {
                    Text(
                        text = item.genres.take(3).joinToString(" • "),
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Synopsis Preview
                if (!item.synopsis.isNullOrBlank()) {
                    Text(
                        text = item.synopsis,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCreditState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Style,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "Tiket Gacha Habis!",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Kamu telah menggunakan seluruh tiket flashcard minggu ini. Kuota dasar 5 tiket diperbarui setiap Senin 00:00:00.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CardElevated,
                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Tips: Tonton 1 episode anime apa pun di Library untuk langsung mendapatkan +1 tiket gacha!",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Kembali ke Beranda", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyDeckState(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Tumpukan Kartu Kosong",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Semua rekomendasi telah dilihat. Muat ulang tumpukan baru dari katalog!",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = "Kocok Kartu Baru", fontWeight = FontWeight.Bold)
        }
    }
}
