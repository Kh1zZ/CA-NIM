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
    var isExitingGesturePop by remember { mutableStateOf(false) }
    var popStartProgress by remember { mutableFloatStateOf(0f) }
    val popAnim = remember { Animatable(0f) }

    // Push tracking
    var isPushing by remember { mutableStateOf(false) }
    var isPushingFromEmpty by remember { mutableStateOf(false) }
    val pushAnim = remember { Animatable(0f) }

    // ── Synchronous Frame-1 State Detection (Eliminates 1-frame visual flash/gap) ───
    if (screenStack != previousStack) {
        val oldStack = previousStack
        previousStack = screenStack

        if (screenStack.size < oldStack.size) {
            // POP: synchronously latch exiting route on Frame 1 before render pass
            val popped = oldStack.lastOrNull()
            if (popped != null) {
                exitingRoute = popped
                exitingFromSingleLayer = oldStack.size == 1
                isExitingGesturePop = wasGesturePop
                popStartProgress = if (wasGesturePop) lastGestureProgress else 0f
                wasGesturePop = false
                isPushing = false
            }
        } else if (screenStack.size > oldStack.size) {
            // PUSH: synchronously latch pushing state on Frame 1 so screen starts at alpha=0
            isPushing = true
            isPushingFromEmpty = oldStack.isEmpty()
            exitingRoute = null
            isExitingGesturePop = false
            popStartProgress = 0f
        }
    }

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

    // ── 2. Navigation Transition Driver ───────────────────────────────────────
    LaunchedEffect(screenStack) {
        try {
            if (exitingRoute != null) {
                popAnim.snapTo(0f)
                val duration = if (isExitingGesturePop) {
                    val remainingDistance = (1f - popStartProgress).coerceIn(0f, 1f)
                    (remainingDistance * 200).toInt().coerceAtLeast(80)
                } else {
                    if (exitingFromSingleLayer) 220 else 200
                }
                popAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                )
            } else if (isPushing) {
                pushAnim.snapTo(0f)
                pushAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = if (isPushingFromEmpty) 240 else 220,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        } finally {
            // Guaranteed cleanup: no state leaks, no frozen screens
            exitingRoute = null
            isExitingGesturePop = false
            popStartProgress = 0f
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

        val activeRoutes: List<ScreenRoute> = remember(screenStack, exitingRoute) {
            val exiting = exitingRoute
            if (exiting != null) {
                screenStack + exiting
            } else {
                screenStack
            }
        }

        // Calculate actual pop progress (starts at popStartProgress if gesture pop):
        val actualPopProgress = if (isExitingGesturePop) {
            popStartProgress + (popAnim.value * (1f - popStartProgress))
        } else {
            popAnim.value
        }

        // Transition progress for underlying and foreground
        val underProgress: Float
        val showTabsScrim: Boolean
        val tabsScrimAlpha: Float

        when {
            exitingRoute != null -> {
                val p = actualPopProgress
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
                showTabsScrim = screenStack.size == 1
                tabsScrimAlpha = if (screenStack.size == 1) 0.35f else 0f
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

            // ── Persistent Multi-Layer Screen Stack ───────────────────────────
            // Maintains Composables in their exact tree positions, preserving
            // LazyListState, Coil caches, and viewmodels across push and pop.
            activeRoutes.forEachIndexed { index, route ->
                val isTop = (index == activeRoutes.size - 1)
                val isUnderlying = (index == activeRoutes.size - 2)
                val isEffectivelyTop = isTop && (exitingRoute == null)

                val layerTransX: Float
                val layerTransY: Float
                val layerScale: Float
                val layerAlpha: Float
                val layerCornerRadiusDp: Float
                val layerScrimAlpha: Float

                when {
                    isTop -> {
                        layerScrimAlpha = 0f
                        when {
                            exitingRoute != null -> {
                                val p = actualPopProgress
                                if (isExitingGesturePop) {
                                    // Gesture Pop: continue smooth horizontal slide to right until off-screen!
                                    layerTransX = p * widthPx
                                    layerTransY = 0f
                                    layerScale = 1f - (p * 0.06f)
                                    layerAlpha = 1f
                                    layerCornerRadiusDp = p * 20f
                                } else if (exitingFromSingleLayer) {
                                    // Programmatic pop from single layer: Card collapse down to tabs
                                    layerTransX = 0f
                                    layerTransY = p * elevationSlidePx
                                    layerScale = 1f - (p * 0.06f)
                                    layerAlpha = (1f - p).coerceIn(0f, 1f)
                                    layerCornerRadiusDp = p * 20f
                                } else {
                                    // Multi-layer programmatic slide to right
                                    layerTransX = p * widthPx
                                    layerTransY = 0f
                                    layerScale = 1f
                                    layerAlpha = 1f
                                    layerCornerRadiusDp = p * 16f
                                }
                            }
                            isGestureActive -> {
                                val p = gestureProgress
                                // PURE HORIZONTAL GESTURE - translationY is ALWAYS 0f (NO DIAGONAL!)
                                layerTransX = p * widthPx
                                layerTransY = 0f
                                layerScale = 1f - (p * 0.06f)
                                layerAlpha = 1f
                                layerCornerRadiusDp = p * 20f
                            }
                            isPushing -> {
                                val p = pushAnim.value
                                if (isPushingFromEmpty) {
                                    // Card expansion up from tabs
                                    layerTransX = 0f
                                    layerTransY = (1f - p) * elevationSlidePx
                                    layerScale = 0.94f + (p * 0.06f)
                                    layerAlpha = p.coerceIn(0f, 1f)
                                    layerCornerRadiusDp = (1f - p) * 20f
                                } else {
                                    // Multi-layer push from right
                                    layerTransX = (1f - p) * widthPx
                                    layerTransY = 0f
                                    layerScale = 1f
                                    layerAlpha = 1f
                                    layerCornerRadiusDp = 0f
                                }
                            }
                            else -> {
                                // Resting state
                                layerTransX = 0f
                                layerTransY = 0f
                                layerScale = 1f
                                layerAlpha = 1f
                                layerCornerRadiusDp = 0f
                            }
                        }
                    }
                    isUnderlying -> {
                        layerTransX = -(1f - underProgress) * (widthPx * 0.20f)
                        layerTransY = 0f
                        layerScale = 0.95f + (underProgress * 0.05f)
                        layerAlpha = 1f
                        layerCornerRadiusDp = 0f
                        layerScrimAlpha = (1f - underProgress).coerceIn(0f, 1f) * 0.30f
                    }
                    else -> {
                        // Deeper layers (index < activeRoutes.size - 2)
                        // Keep composition state alive without drawing overhead
                        layerTransX = -(widthPx * 0.20f)
                        layerTransY = 0f
                        layerScale = 0.95f
                        layerAlpha = 0f
                        layerCornerRadiusDp = 0f
                        layerScrimAlpha = 0f
                    }
                }

                key(index, route) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = layerTransX
                                translationY = layerTransY
                                scaleX = layerScale
                                scaleY = layerScale
                                alpha = layerAlpha
                                if (layerCornerRadiusDp > 0.5f) {
                                    clip = true
                                    shape = RoundedCornerShape(layerCornerRadiusDp.dp)
                                }
                            }
                    ) {
                        content(route, isEffectivelyTop)

                        // Subtle darkening scrim that fades as screen comes to front
                        if (layerScrimAlpha > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = layerScrimAlpha))
                            )
                        }
                    }
                }
            }
        }
    }
}

