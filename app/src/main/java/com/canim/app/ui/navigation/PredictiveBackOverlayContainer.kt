package com.canim.app.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.canim.app.ui.theme.BlackBg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * High-performance overlay navigation container inspired by Animite.
 *
 * Architecture:
 * 1. Stage-Based Predictive Back Gesture:
 *    - Native Android 14+ PredictiveBackHandler with real-time progress callbacks.
 *    - Universal Left-Edge Touch Drag fallback (detectHorizontalDragGestures) ensuring interactive
 *      drag-to-back works on every Android version and OEM ROM without lag or suppression.
 * 2. True Two-Layer Stack Rendering (Transitions Only):
 *    - During active push, pop, or swipe gesture, the underlying screen is composed with
 *      isTopScreen = false (preventing secondary ViewModel fetches or state corruption),
 *      accompanied by parallax slide, subtle scale, and darkening scrim.
 * 3. Idle State Single-Screen Optimization:
 *    - When idle, only the active top screen is composed. Zero background composable overhead,
 *      zero memory leaks, and solid BlackBg backdrop shielding the active bottom tabs.
 * 4. Unified Stack Boundaries:
 *    - Stack 0 -> 1 enters smoothly via vertical slide-up from bottom.
 *    - Stack 1 -> 0 exits cleanly via vertical slide-down / fade-out to active tabs.
 */
@Composable
fun PredictiveBackOverlayContainer(
    screenStack: List<ScreenRoute>,
    onPopScreen: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (route: ScreenRoute, isTopScreen: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Gesture state
    var isPredictiveActive by remember { mutableStateOf(false) }
    var predictiveProgress by remember { mutableFloatStateOf(0f) }
    val gestureAnim = remember { Animatable(0f) }
    var isTouchDragging by remember { mutableStateOf(false) }
    var touchDragOffset by remember { mutableFloatStateOf(0f) }
    var handledByGesture by remember { mutableStateOf(false) }

    // Discrete navigation transition state
    var previousStack by remember { mutableStateOf(screenStack) }
    var exitingRoute by remember { mutableStateOf<ScreenRoute?>(null) }
    val popProgress = remember { Animatable(0f) }
    var pushUnderRoute by remember { mutableStateOf<ScreenRoute?>(null) }
    val pushProgress = remember { Animatable(0f) }

    // 1. Native Android 14+ Predictive Back Handler
    PredictiveBackHandler(enabled = screenStack.isNotEmpty()) { progressFlow ->
        try {
            isPredictiveActive = true
            progressFlow.collect { backEvent ->
                predictiveProgress = backEvent.progress
            }

            // Gesture committed: animate remaining progress to 1f and pop
            gestureAnim.snapTo(predictiveProgress)
            gestureAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
            )
            handledByGesture = true
            onPopScreen()
        } catch (e: CancellationException) {
            // Gesture cancelled: spring smoothly back to resting 0f
            gestureAnim.snapTo(predictiveProgress)
            gestureAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy
                )
            )
        } finally {
            isPredictiveActive = false
            predictiveProgress = 0f
            gestureAnim.snapTo(0f)
        }
    }

    // 2. Discrete Navigation Transitions (Push & Pop via button/action)
    LaunchedEffect(screenStack) {
        val oldStack = previousStack
        previousStack = screenStack

        if (screenStack.size < oldStack.size) {
            // Screen was popped
            if (handledByGesture) {
                // Exit was already smoothly animated by gesture; no discrete animation needed
                handledByGesture = false
                exitingRoute = null
            } else {
                // Discrete pop (e.g. user clicked "Kembali" button)
                val popped = oldStack.lastOrNull()
                if (popped != null && oldStack.size > 1) {
                    exitingRoute = popped
                    popProgress.snapTo(0f)
                    popProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    )
                    exitingRoute = null
                    popProgress.snapTo(0f)
                } else if (screenStack.isEmpty()) {
                    exitingRoute = null
                }
            }
        } else if (screenStack.size > oldStack.size && oldStack.isNotEmpty()) {
            // Screen was pushed onto existing stack
            pushUnderRoute = oldStack.lastOrNull()
            pushProgress.snapTo(0f)
            pushProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
            pushUnderRoute = null
            pushProgress.snapTo(0f)
        }
    }

    val isStackEmpty = screenStack.isEmpty() && exitingRoute == null

    AnimatedVisibility(
        visible = !isStackEmpty,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(240, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(220, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(180)),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(BlackBg)
        ) {
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val edgeThresholdPx = with(density) { 36.dp.toPx() }

            // Unified gesture progress calculation
            val activeGestureProgress = when {
                isTouchDragging -> (touchDragOffset / widthPx).coerceIn(0f, 1f)
                isPredictiveActive -> predictiveProgress
                else -> gestureAnim.value
            }

            val currentTopScreen = screenStack.lastOrNull()
            val isMultiLayer = screenStack.size > 1 || exitingRoute != null || pushUnderRoute != null

            // Determine underlying screen to compose during transitions ONLY
            val underScreen: ScreenRoute? = when {
                activeGestureProgress > 0f && screenStack.size > 1 -> screenStack[screenStack.size - 2]
                exitingRoute != null && popProgress.value > 0f -> currentTopScreen
                pushUnderRoute != null && pushProgress.value < 1f -> pushUnderRoute
                else -> null // Idle state: underScreen is null, 0% CPU/GPU waste
            }

            // Universal Left-Edge Drag-to-Dismiss Gesture Modifier
            val gestureModifier = if (screenStack.isNotEmpty()) {
                Modifier.pointerInput(screenStack.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (!isPredictiveActive && offset.x <= edgeThresholdPx) {
                                isTouchDragging = true
                                touchDragOffset = 0f
                            }
                        },
                        onDragEnd = {
                            if (isTouchDragging) {
                                val progress = (touchDragOffset / widthPx).coerceIn(0f, 1f)
                                coroutineScope.launch {
                                    if (progress > 0.28f) {
                                        // Committed: animate out to right and pop
                                        gestureAnim.snapTo(progress)
                                        gestureAnim.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                                        )
                                        handledByGesture = true
                                        onPopScreen()
                                    } else {
                                        // Cancelled: spring back
                                        gestureAnim.snapTo(progress)
                                        gestureAnim.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                dampingRatio = Spring.DampingRatioLowBouncy
                                            )
                                        )
                                    }
                                    isTouchDragging = false
                                    touchDragOffset = 0f
                                    gestureAnim.snapTo(0f)
                                }
                            }
                        },
                        onDragCancel = {
                            if (isTouchDragging) {
                                coroutineScope.launch {
                                    gestureAnim.snapTo((touchDragOffset / widthPx).coerceIn(0f, 1f))
                                    gestureAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioLowBouncy
                                        )
                                    )
                                    isTouchDragging = false
                                    touchDragOffset = 0f
                                    gestureAnim.snapTo(0f)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isTouchDragging) {
                                change.consume()
                                touchDragOffset = (touchDragOffset + dragAmount).coerceIn(0f, widthPx)
                            }
                        }
                    )
                }
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(gestureModifier)
            ) {
                // 1. UNDERLYING SCREEN (Screen N-1):
                // Composed ONLY during transitions with isTopScreen = false so background screens
                // do NOT trigger ViewModel state mutations or duplicate network fetches.
                if (underScreen != null) {
                    val underProgress = when {
                        activeGestureProgress > 0f -> activeGestureProgress
                        exitingRoute != null -> popProgress.value
                        pushUnderRoute != null -> 1f - pushProgress.value
                        else -> 0f
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BlackBg)
                            .graphicsLayer {
                                translationX = -(1f - underProgress) * (widthPx * 0.22f)
                                val scale = 0.95f + (underProgress * 0.05f)
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        content(underScreen, false)

                        // Darkening scrim overlay that clears as screen comes to front
                        val scrimAlpha = (1f - underProgress) * 0.35f
                        if (scrimAlpha > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = scrimAlpha))
                            )
                        }
                    }
                }

                // 2. TOP ACTIVE SCREEN (Screen N):
                if (currentTopScreen != null && exitingRoute == null) {
                    val topTranslationX: Float
                    val topTranslationY: Float
                    val topScale: Float
                    val topCornerRadiusDp: Float
                    val topAlpha: Float

                    if (activeGestureProgress > 0f) {
                        if (isMultiLayer) {
                            topTranslationX = activeGestureProgress * widthPx
                            topTranslationY = 0f
                            topScale = 1f - (activeGestureProgress * 0.05f)
                            topCornerRadiusDp = activeGestureProgress * 16f
                            topAlpha = 1f
                        } else {
                            // Stack 1 -> 0 to tabs
                            topTranslationX = activeGestureProgress * (widthPx * 0.15f)
                            topTranslationY = activeGestureProgress * (heightPx * 0.08f)
                            topScale = 1f - (activeGestureProgress * 0.08f)
                            topCornerRadiusDp = activeGestureProgress * 16f
                            topAlpha = 1f - (activeGestureProgress * 0.25f)
                        }
                    } else if (pushUnderRoute != null && pushProgress.value < 1f) {
                        // Push in from right
                        topTranslationX = (1f - pushProgress.value) * widthPx
                        topTranslationY = 0f
                        topScale = 1f
                        topCornerRadiusDp = 0f
                        topAlpha = 1f
                    } else {
                        // Idle resting state
                        topTranslationX = 0f
                        topTranslationY = 0f
                        topScale = 1f
                        topCornerRadiusDp = 0f
                        topAlpha = 1f
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BlackBg)
                            .graphicsLayer {
                                translationX = topTranslationX
                                translationY = topTranslationY
                                scaleX = topScale
                                scaleY = topScale
                                alpha = topAlpha
                                if (topCornerRadiusDp > 0f) {
                                    clip = true
                                    shape = RoundedCornerShape(topCornerRadiusDp.dp)
                                }
                            }
                    ) {
                        content(currentTopScreen, true)
                    }
                }

                // 3. EXITING SCREEN (During discrete pop):
                // Smoothly slides out to the right over the revealed underlying screen
                if (exitingRoute != null && popProgress.value > 0f) {
                    val p = popProgress.value
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BlackBg)
                            .graphicsLayer {
                                translationX = p * widthPx
                                val scale = 1f - (p * 0.05f)
                                scaleX = scale
                                scaleY = scale
                                if (p > 0.02f) {
                                    clip = true
                                    shape = RoundedCornerShape((p * 16f).dp)
                                }
                            }
                    ) {
                        content(exitingRoute!!, false)
                    }
                }
            }
        }
    }
}
