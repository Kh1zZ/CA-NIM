package com.canim.app.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Animite-Style High-Performance Seamless Navigation Container for CA'NIM.
 *
 * Kinematic Architecture:
 * 1. Card Expansion & Hero Reveal (Stack 0 -> 1):
 *    - Opening media detail expands vertically with scale (0.94f -> 1.0f) and fade (0f -> 1f).
 *    - Background tabs dim with a translucent 35% scrim, remaining visible underneath.
 *
 * 2. Card Collapse on Dismiss (Stack 1 -> 0):
 *    - Programmatic dismiss collapses vertically (1.0f -> 0.94f, slide-down, fade-out).
 *    - Reveals the underlying tabs with zero black flash or layout stutter.
 *
 * 3. Pure Linear Edge-Swipe Kinematics:
 *    - Gesture back follows the user's thumb horizontally with translationX only (translationY = 0f).
 *    - Eliminates all diagonal / slanted ("nyerong") movement.
 *    - Underneath, tabs or previous screen are fully visible through the gesture window.
 *
 * 4. Reliable Gesture Completion:
 *    - PredictiveBackHandler immediately commits onPopScreen() upon gesture completion.
 *    - Finishes the exit slide smoothly in LaunchedEffect, immune to AndroidX coroutine scope cancellations.
 *    - Spring return on gesture cancellation runs in NonCancellable.
 *
 * 5. Leak-Proof Lifecycle (try/finally):
 *    - Guaranteed cleanup of all transition states preventing screen freeze ("layar nyangkut").
 */
@Composable
fun PredictiveBackOverlayContainer(
    screenStack: List<ScreenRoute>,
    onPopScreen: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (route: ScreenRoute, isTopScreen: Boolean) -> Unit
) {
    // ── Gesture State ─────────────────────────────────────────────────────────
    var isGestureActive by remember { mutableStateOf(false) }
    var gestureProgress by remember { mutableFloatStateOf(0f) }
    var wasGesturePop by remember { mutableStateOf(false) }
    var lastGestureProgress by remember { mutableFloatStateOf(0f) }

    // ── Stack Animation Tracking ──────────────────────────────────────────────
    var previousStack by remember { mutableStateOf(screenStack) }

    // Exiting route (popped screen animating out)
    var exitingRoute by remember { mutableStateOf<ScreenRoute?>(null) }
    var exitingFromSingleLayer by remember { mutableStateOf(false) }
    val popAnim = remember { Animatable(0f) }

    // Push tracking
    var isPushing by remember { mutableStateOf(false) }
    var isPushingFromEmpty by remember { mutableStateOf(false) }
    val pushAnim = remember { Animatable(0f) }

    // ── 1. Native Predictive Back Handler ─────────────────────────────────────
    PredictiveBackHandler(enabled = screenStack.isNotEmpty()) { progressFlow ->
        try {
            isGestureActive = true
            progressFlow.collect { backEvent ->
                gestureProgress = backEvent.progress
            }

            // GESTURE COMMITTED:
            // AndroidX cancels this coroutine scope immediately when onBackInvoked completes.
            // We MUST NOT run a suspend animation here. Immediately notify pop:
            lastGestureProgress = gestureProgress
            wasGesturePop = true
            onPopScreen()
        } catch (e: CancellationException) {
            // GESTURE CANCELLED:
            // Spring smoothly back to 0f without popping
            withContext(NonCancellable) {
                val cancelAnim = Animatable(gestureProgress)
                cancelAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                ) {
                    gestureProgress = this.value
                }
            }
        } finally {
            isGestureActive = false
            gestureProgress = 0f
        }
    }

    // ── 2. Navigation Transitions ─────────────────────────────────────────────
    LaunchedEffect(screenStack) {
        val oldStack = previousStack
        previousStack = screenStack

        try {
            when {
                // ── POP (Stack size decreased) ────────────────────────────────────
                screenStack.size < oldStack.size -> {
                    val popped = oldStack.lastOrNull()
                    if (popped != null) {
                        exitingRoute = popped
                        exitingFromSingleLayer = oldStack.size == 1

                        if (wasGesturePop) {
                            wasGesturePop = false
                            // Smoothly finish the remaining distance from finger release to 1.0f
                            popAnim.snapTo(lastGestureProgress)
                            val remainingDistance = (1f - lastGestureProgress).coerceIn(0f, 1f)
                            val duration = (remainingDistance * 180).toInt().coerceAtLeast(80)
                            popAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                            )
                        } else {
                            // Programmatic / in-app button pop
                            popAnim.snapTo(0f)
                            popAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = if (exitingFromSingleLayer) 220 else 200,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                    }
                }

                // ── PUSH (Stack size increased) ───────────────────────────────────
                screenStack.size > oldStack.size -> {
                    isPushing = true
                    isPushingFromEmpty = oldStack.isEmpty()
                    pushAnim.snapTo(0f)
                    pushAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = if (isPushingFromEmpty) 260 else 220,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        } finally {
            // Guaranteed cleanup: no state leaks, no frozen screens
            exitingRoute = null
            popAnim.snapTo(0f)
            isPushing = false
            pushAnim.snapTo(0f)
            lastGestureProgress = 0f
        }
    }

    // ── 3. Kinematic Render Pass ──────────────────────────────────────────────
    val hasContent = screenStack.isNotEmpty() || exitingRoute != null || isGestureActive || isPushing
    if (!hasContent) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        val elevationSlidePx = with(density) { 48.dp.toPx() }

        // Top active screen in current stack
        val currentTopScreen = screenStack.lastOrNull()

        // Underlying screen (if nested stack):
        // If an exitingRoute is present, underlying screen is currentTopScreen!
        // If no exitingRoute and gesture/push is active, underlying screen is the second-to-last item!
        val underlyingRoute: ScreenRoute? = when {
            exitingRoute != null -> if (exitingFromSingleLayer) null else currentTopScreen
            isGestureActive || isPushing -> if (screenStack.size > 1) screenStack[screenStack.size - 2] else null
            else -> null
        }

        // Active foreground screen being animated:
        val foregroundRoute: ScreenRoute? = exitingRoute ?: currentTopScreen

        // Transition progress for underlying and foreground
        val underProgress: Float
        val showTabsScrim: Boolean
        val tabsScrimAlpha: Float

        when {
            exitingRoute != null -> {
                val p = popAnim.value
                underProgress = p
                showTabsScrim = exitingFromSingleLayer
                tabsScrimAlpha = ((1f - p) * 0.35f).coerceIn(0f, 0.35f)
            }
            isGestureActive -> {
                val p = gestureProgress
                underProgress = p
                showTabsScrim = screenStack.size <= 1
                tabsScrimAlpha = ((1f - p) * 0.35f).coerceIn(0f, 0.35f)
            }
            isPushing -> {
                val p = pushAnim.value
                underProgress = 1f - p
                showTabsScrim = isPushingFromEmpty
                tabsScrimAlpha = (p * 0.35f).coerceIn(0f, 0.35f)
            }
            else -> {
                underProgress = 0f
                showTabsScrim = false
                tabsScrimAlpha = 0f
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim over underlying tabs (semi-transparent, tabs remain visible underneath!)
            if (showTabsScrim && tabsScrimAlpha > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = tabsScrimAlpha))
                )
            }

            // ── Underlying Screen (Screen N-1) ────────────────────────────────
            if (underlyingRoute != null) {
                key(underlyingRoute) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = -(1f - underProgress) * (widthPx * 0.20f)
                                val scale = 0.95f + (underProgress * 0.05f)
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        content(underlyingRoute, false)

                        // Subtle darkening scrim that fades as screen comes to front
                        val scrimAlpha = (1f - underProgress).coerceIn(0f, 1f) * 0.30f
                        if (scrimAlpha > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = scrimAlpha))
                            )
                        }
                    }
                }
            }

            // ── Foreground Screen (Screen N) ──────────────────────────────────
            if (foregroundRoute != null) {
                val transX: Float
                val transY: Float
                val fgScale: Float
                val fgAlpha: Float
                val cornerRadiusDp: Float

                when {
                    exitingRoute != null -> {
                        val p = popAnim.value
                        if (exitingFromSingleLayer) {
                            // Card collapse down to tabs
                            transX = 0f
                            transY = p * elevationSlidePx
                            fgScale = 1f - (p * 0.06f)
                            fgAlpha = (1f - p).coerceIn(0f, 1f)
                            cornerRadiusDp = p * 20f
                        } else {
                            // Multi-layer slide to right
                            transX = p * widthPx
                            transY = 0f
                            fgScale = 1f
                            fgAlpha = 1f
                            cornerRadiusDp = p * 16f
                        }
                    }
                    isGestureActive -> {
                        val p = gestureProgress
                        // PURE HORIZONTAL GESTURE - translationY is ALWAYS 0f (NO DIAGONAL!)
                        transX = p * widthPx
                        transY = 0f
                        fgScale = 1f - (p * 0.06f)
                        fgAlpha = 1f
                        cornerRadiusDp = p * 20f
                    }
                    isPushing -> {
                        val p = pushAnim.value
                        if (isPushingFromEmpty) {
                            // Card expansion up from tabs
                            transX = 0f
                            transY = (1f - p) * elevationSlidePx
                            fgScale = 0.94f + (p * 0.06f)
                            fgAlpha = p.coerceIn(0f, 1f)
                            cornerRadiusDp = (1f - p) * 20f
                        } else {
                            // Multi-layer push from right
                            transX = (1f - p) * widthPx
                            transY = 0f
                            fgScale = 1f
                            fgAlpha = 1f
                            cornerRadiusDp = 0f
                        }
                    }
                    else -> {
                        // Resting state
                        transX = 0f
                        transY = 0f
                        fgScale = 1f
                        fgAlpha = 1f
                        cornerRadiusDp = 0f
                    }
                }

                key(foregroundRoute) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = transX
                                translationY = transY
                                scaleX = fgScale
                                scaleY = fgScale
                                alpha = fgAlpha
                                if (cornerRadiusDp > 0.5f) {
                                    clip = true
                                    shape = RoundedCornerShape(cornerRadiusDp.dp)
                                }
                            }
                    ) {
                        content(foregroundRoute, exitingRoute == null)
                    }
                }
            }
        }
    }
}

