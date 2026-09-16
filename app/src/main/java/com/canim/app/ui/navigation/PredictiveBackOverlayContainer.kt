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
import com.canim.app.ui.theme.BlackBg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Animite-Style High-Performance Seamless Navigation Container for CA'NIM.
 *
 * Kinematic Architecture:
 * 1. Card Expansion & Hero Reveal (Stack 0 -> 1):
 *    - Opening any media detail from the menu triggers a cinematic expansion: the screen scales
 *      up gracefully from 0.92f to 1.0f, elevates with a subtle 56dp slide-up, fades in, and
 *      smooths its 24dp card corner radius to 0dp. The background tabs dim with a 40% scrim.
 *
 * 2. Card Collapse on Dismiss (Stack 1 -> 0):
 *    - Returning to the menu collapses the detail screen back into card dimensions (1.0f -> 0.92f,
 *      56dp slide-down, fade-out, corner radius returning to 24dp), revealing the ready tabs
 *      underneath with zero black flash or layout stutter.
 *
 * 3. True Multi-Layer Parallax (Stack N -> N+1 & N+1 -> N):
 *    - Nested navigation (Detail -> Cast/Crew -> Full Cast) uses synchronized horizontal kinematics:
 *      foreground slides in/out from the right, while the underlying screen recedes with a
 *      -22% parallax shift and a 35% darkening scrim.
 *
 * 4. Responsive & Uninterruptible Gesture Navigation:
 *    - System predictive back gesture tracks the user's thumb live with organic scale and corner
 *      rounding.
 *    - Gesture cancellation recovery is executed inside `withContext(NonCancellable)`, guaranteeing
 *      a crisp spring back to 0f without ever freezing ("layar nyangkut").
 *    - Commit path animates to full 1.0f completion before updating `screenStack`, eliminating
 *      instant unmount visual blinks.
 *
 * 5. Zero-Overhead Idle State:
 *    - When `screenStack.isEmpty()` and no transition is running, the container renders nothing,
 *      letting all pointer events pass directly to the bottom tab bar.
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
    val gestureAnim = remember { Animatable(0f) }
    var wasGesturePop by remember { mutableStateOf(false) }

    // ── Discrete Animation State (Push & Pop) ─────────────────────────────────
    var previousStack by remember { mutableStateOf(screenStack) }

    // Pop tracking:
    var exitingRoute by remember { mutableStateOf<ScreenRoute?>(null) }
    var exitingFromSingleLayer by remember { mutableStateOf(false) }
    val popAnim = remember { Animatable(0f) }

    // Push tracking:
    var isPushing by remember { mutableStateOf(false) }
    var isPushingFromEmpty by remember { mutableStateOf(false) }
    var pushUnderRoute by remember { mutableStateOf<ScreenRoute?>(null) }
    val pushAnim = remember { Animatable(0f) }

    // ── 1. Native Predictive Back Handler (System Edge Swipe) ─────────────────
    PredictiveBackHandler(enabled = screenStack.isNotEmpty()) { progressFlow ->
        try {
            isGestureActive = true
            progressFlow.collect { backEvent ->
                gestureProgress = backEvent.progress
            }

            // Gesture committed: animate remaining distance to 1.0f (fully offscreen/collapsed)
            wasGesturePop = true
            gestureAnim.snapTo(gestureProgress)
            gestureAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )

            // Screen is now 100% offscreen; safe to update stack without visual flicker
            onPopScreen()
            isGestureActive = false
            gestureProgress = 0f
            gestureAnim.snapTo(0f)

        } catch (e: CancellationException) {
            // Gesture cancelled: spring smoothly back to resting position.
            // MUST run inside NonCancellable so the animation isn't cancelled immediately!
            withContext(NonCancellable) {
                gestureAnim.snapTo(gestureProgress)
                gestureAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
                isGestureActive = false
                gestureProgress = 0f
                gestureAnim.snapTo(0f)
            }
            throw e
        }
    }

    // ── 2. Discrete Navigation Transitions (Button Clicks / Programmatic) ─────
    LaunchedEffect(screenStack) {
        val oldStack = previousStack
        previousStack = screenStack

        when {
            // ── POP (Stack size decreased) ────────────────────────────────────
            screenStack.size < oldStack.size -> {
                if (wasGesturePop) {
                    // Gesture already smoothly animated the exit to 1f; clear flag
                    wasGesturePop = false
                } else {
                    val popped = oldStack.lastOrNull()
                    if (popped != null) {
                        exitingRoute = popped
                        exitingFromSingleLayer = oldStack.size == 1 // Stack was 1 -> now 0 (Card Collapse)
                        popAnim.snapTo(0f)
                        popAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = if (exitingFromSingleLayer) 260 else 240,
                                easing = FastOutSlowInEasing
                            )
                        )
                        exitingRoute = null
                        popAnim.snapTo(0f)
                    }
                }
            }

            // ── PUSH (Stack size increased) ───────────────────────────────────
            screenStack.size > oldStack.size -> {
                isPushing = true
                isPushingFromEmpty = oldStack.isEmpty() // Stack was 0 -> now 1 (Card Expansion)
                pushUnderRoute = oldStack.lastOrNull()
                pushAnim.snapTo(0f)
                pushAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = if (isPushingFromEmpty) 300 else 260,
                        easing = FastOutSlowInEasing
                    )
                )
                isPushing = false
                pushUnderRoute = null
                pushAnim.snapTo(0f)
            }
        }
    }

    // ── 3. Kinematic Render Pass ──────────────────────────────────────────────
    val hasContent = screenStack.isNotEmpty() || exitingRoute != null || isGestureActive || isPushing

    if (!hasContent) {
        // Completely idle with empty stack: zero layout, zero draw overhead, tabs 100% active
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        val elevationSlidePx = with(density) { 56.dp.toPx() }
        val currentTopScreen = screenStack.lastOrNull()
        val isSingleLayer = screenStack.size <= 1

        // ── Determine Layer 1 (Underlying Screen & Parallax Progress) ──────────
        val underScreen: ScreenRoute?
        val underProgress: Float // 0f = fully covered/receded, 1f = fully revealed/centered
        val showTabsScrim: Boolean
        val tabsScrimAlpha: Float

        when {
            isGestureActive -> {
                val p = if (gestureAnim.isRunning) gestureAnim.value else gestureProgress
                underScreen = if (screenStack.size > 1) screenStack[screenStack.size - 2] else null
                underProgress = p
                showTabsScrim = isSingleLayer
                tabsScrimAlpha = ((1f - p) * 0.40f).coerceIn(0f, 0.40f)
            }
            exitingRoute != null -> {
                val p = popAnim.value
                underScreen = if (exitingFromSingleLayer) null else currentTopScreen
                underProgress = p
                showTabsScrim = exitingFromSingleLayer
                tabsScrimAlpha = ((1f - p) * 0.40f).coerceIn(0f, 0.40f)
            }
            isPushing -> {
                val p = pushAnim.value
                underScreen = pushUnderRoute
                underProgress = 1f - p
                showTabsScrim = isPushingFromEmpty
                tabsScrimAlpha = (p * 0.40f).coerceIn(0f, 0.40f)
            }
            else -> {
                underScreen = null
                underProgress = 0f
                showTabsScrim = false
                tabsScrimAlpha = 0f
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {

            // ── Background Scrim for Tabs (when Stack is 1 -> 0 or 0 -> 1) ─────────
            if (showTabsScrim && tabsScrimAlpha > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = tabsScrimAlpha))
                )
            }

            // ── Layer 1: UNDERLYING SCREEN (Screen N-1) ───────────────────────
            // Composed ONLY during transitions with isTopScreen = false.
            if (underScreen != null) {
                key(underScreen) {
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

                        // Darkening scrim that clears as under-screen comes to foreground
                        val scrimAlpha = (1f - underProgress).coerceIn(0f, 1f) * 0.35f
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

            // ── Determine Layer 2 (Foreground Screen Kinematics) ──────────────
            val foregroundRoute: ScreenRoute?
            val foregroundIsTop: Boolean
            val transX: Float
            val transY: Float
            val fgScale: Float
            val fgAlpha: Float
            val cornerRadiusDp: Float

            when {
                exitingRoute != null -> {
                    foregroundRoute = exitingRoute
                    foregroundIsTop = false
                    val p = popAnim.value
                    if (exitingFromSingleLayer) {
                        // Card Collapse back to tabs (Animite style)
                        transX = 0f
                        transY = p * elevationSlidePx
                        fgScale = 1f - (p * 0.08f)
                        fgAlpha = (1f - p).coerceIn(0f, 1f)
                        cornerRadiusDp = p * 24f
                    } else {
                        // Multi-layer slide to right
                        transX = p * widthPx
                        transY = 0f
                        fgScale = 1f - (p * 0.05f)
                        fgAlpha = 1f
                        cornerRadiusDp = p * 16f
                    }
                }
                isGestureActive -> {
                    foregroundRoute = currentTopScreen
                    foregroundIsTop = true
                    val p = if (gestureAnim.isRunning) gestureAnim.value else gestureProgress
                    if (isSingleLayer) {
                        // Gesture back to tabs (follows thumb with organic shrink & radius)
                        transX = p * (widthPx * 0.70f)
                        transY = p * (elevationSlidePx * 0.40f)
                        fgScale = 1f - (p * 0.10f)
                        fgAlpha = (1f - (p * 0.30f)).coerceIn(0f, 1f)
                        cornerRadiusDp = p * 24f
                    } else {
                        // Multi-layer gesture slide
                        transX = p * widthPx
                        transY = 0f
                        fgScale = 1f - (p * 0.05f)
                        fgAlpha = 1f
                        cornerRadiusDp = p * 16f
                    }
                }
                isPushing -> {
                    foregroundRoute = currentTopScreen
                    foregroundIsTop = true
                    val p = pushAnim.value // 0f -> 1f
                    if (isPushingFromEmpty) {
                        // Card Expansion from menu into detail (Animite style!)
                        transX = 0f
                        transY = (1f - p) * elevationSlidePx
                        fgScale = 0.92f + (p * 0.08f)
                        fgAlpha = p.coerceIn(0f, 1f)
                        cornerRadiusDp = (1f - p) * 24f
                    } else {
                        // Multi-layer push from right
                        transX = (1f - p) * widthPx
                        transY = 0f
                        fgScale = 1f
                        fgAlpha = 1f
                        cornerRadiusDp = 0f
                    }
                }
                currentTopScreen != null -> {
                    foregroundRoute = currentTopScreen
                    foregroundIsTop = true
                    transX = 0f
                    transY = 0f
                    fgScale = 1f
                    fgAlpha = 1f
                    cornerRadiusDp = 0f
                }
                else -> {
                    foregroundRoute = null
                    foregroundIsTop = false
                    transX = 0f
                    transY = 0f
                    fgScale = 1f
                    fgAlpha = 1f
                    cornerRadiusDp = 0f
                }
            }

            // ── Layer 2: FOREGROUND ACTIVE SCREEN (Screen N) ───────────────────
            if (foregroundRoute != null) {
                key(foregroundRoute) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BlackBg)
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
                        content(foregroundRoute, foregroundIsTop)
                    }
                }
            }
        }
    }
}

