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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.canim.app.ui.theme.BlackBg
import kotlinx.coroutines.CancellationException

/**
 * High-performance overlay navigation container inspired by Animite.
 *
 * Architecture:
 * 1. Single-Source Predictive Back Gesture:
 *    - Native PredictiveBackHandler is the ONLY gesture driver. No manual
 *      detectHorizontalDragGestures fallback — that caused dual-gesture conflict where both
 *      systems mutated the same Animatable concurrently, producing jank and double-pops.
 *    - PredictiveBackHandler already covers all cases: system back button, 3-button nav,
 *      and edge-swipe gesture on Android 13+. No fallback needed.
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
    // ── Gesture state ────────────────────────────────────────────────────────
    // Single source of truth: PredictiveBackHandler drives everything.
    var isPredictiveActive by remember { mutableStateOf(false) }
    var predictiveProgress by remember { mutableFloatStateOf(0f) }
    val gestureAnim = remember { Animatable(0f) }

    // Flag that tells the discrete-pop LaunchedEffect to skip its own animation
    // because the gesture already completed the visual exit.
    var handledByGesture by remember { mutableStateOf(false) }

    // ── Discrete navigation transition state ─────────────────────────────────
    var previousStack by remember { mutableStateOf(screenStack) }
    var exitingRoute by remember { mutableStateOf<ScreenRoute?>(null) }
    val popProgress = remember { Animatable(0f) }
    var pushUnderRoute by remember { mutableStateOf<ScreenRoute?>(null) }
    val pushProgress = remember { Animatable(0f) }

    // ── 1. Native Predictive Back Handler ─────────────────────────────────────
    // This is the ONLY gesture driver. CancellationException MUST be rethrown
    // so Compose's structured-concurrency machinery can clean up properly.
    PredictiveBackHandler(enabled = screenStack.isNotEmpty()) { progressFlow ->
        try {
            isPredictiveActive = true
            progressFlow.collect { backEvent ->
                predictiveProgress = backEvent.progress
            }

            // Gesture committed: animate the remaining gap to 1f, then pop.
            gestureAnim.snapTo(predictiveProgress)
            gestureAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
            )
            handledByGesture = true
            onPopScreen()
        } catch (e: CancellationException) {
            // Gesture cancelled: spring crisply back to 0 — NoBouncy so it doesn't overshoot.
            gestureAnim.snapTo(predictiveProgress)
            gestureAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            )
            throw e // MUST rethrow — do NOT swallow CancellationException
        } finally {
            isPredictiveActive = false
            predictiveProgress = 0f
            gestureAnim.snapTo(0f)
        }
    }

    // ── 2. Discrete Navigation Transitions (push & pop via button/action) ─────
    LaunchedEffect(screenStack) {
        val oldStack = previousStack
        previousStack = screenStack

        if (screenStack.size < oldStack.size) {
            // Screen was popped
            if (handledByGesture) {
                // The gesture already animated the exit; skip discrete animation.
                handledByGesture = false
                exitingRoute = null
            } else {
                // Discrete pop (e.g. user tapped the back button)
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

            // Single source of truth for gesture progress.
            // isPredictiveActive  → live drag (read predictiveProgress directly for zero-lag)
            // else                → gestureAnim handles commit/cancel spring
            val activeGestureProgress: Float = when {
                isPredictiveActive -> predictiveProgress
                else -> gestureAnim.value
            }

            val currentTopScreen = screenStack.lastOrNull()
            val isMultiLayer = screenStack.size > 1 || exitingRoute != null || pushUnderRoute != null

            // Determine underlying screen to compose during transitions ONLY.
            // Idle → underScreen is null → 0% CPU/GPU overhead on background screens.
            val underScreen: ScreenRoute? = when {
                activeGestureProgress > 0f && screenStack.size > 1 -> screenStack[screenStack.size - 2]
                exitingRoute != null && popProgress.value > 0f -> currentTopScreen
                pushUnderRoute != null && pushProgress.value < 1f -> pushUnderRoute
                else -> null
            }

            Box(modifier = Modifier.fillMaxSize()) {

                // ── Layer 1: UNDERLYING SCREEN (Screen N-1) ──────────────────────
                // Composed ONLY during transitions with isTopScreen = false so background
                // screens do NOT trigger ViewModel mutations or duplicate network fetches.
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

                        // Darkening scrim clears progressively as the under-screen comes to front
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

                // ── Layer 2: TOP ACTIVE SCREEN (Screen N) ────────────────────────
                if (currentTopScreen != null && exitingRoute == null) {
                    val topTranslationX: Float
                    val topTranslationY: Float
                    val topScale: Float
                    val topCornerRadiusDp: Float
                    val topAlpha: Float

                    when {
                        activeGestureProgress > 0f -> {
                            if (isMultiLayer) {
                                // Slide right with subtle scale + corner rounding
                                topTranslationX = activeGestureProgress * widthPx
                                topTranslationY = 0f
                                topScale = 1f - (activeGestureProgress * 0.05f)
                                topCornerRadiusDp = activeGestureProgress * 16f
                                topAlpha = 1f
                            } else {
                                // Stack 1 → 0: slide + shrink + fade toward tabs
                                topTranslationX = activeGestureProgress * (widthPx * 0.15f)
                                topTranslationY = activeGestureProgress * (heightPx * 0.08f)
                                topScale = 1f - (activeGestureProgress * 0.08f)
                                topCornerRadiusDp = activeGestureProgress * 16f
                                topAlpha = 1f - (activeGestureProgress * 0.25f)
                            }
                        }
                        pushUnderRoute != null && pushProgress.value < 1f -> {
                            // Push in from right
                            topTranslationX = (1f - pushProgress.value) * widthPx
                            topTranslationY = 0f
                            topScale = 1f
                            topCornerRadiusDp = 0f
                            topAlpha = 1f
                        }
                        else -> {
                            // Idle resting state
                            topTranslationX = 0f
                            topTranslationY = 0f
                            topScale = 1f
                            topCornerRadiusDp = 0f
                            topAlpha = 1f
                        }
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

                // ── Layer 3: EXITING SCREEN (during discrete pop) ────────────────
                // Smoothly slides out to the right over the revealed underlying screen.
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
