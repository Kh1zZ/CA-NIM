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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.canim.app.ui.theme.BlackBg
import kotlinx.coroutines.CancellationException

// ─────────────────────────────────────────────────────────────────────────────
// Navigation Transition State Machine
//
// Exactly ONE of these states is active at any point. This eliminates every
// race condition between PredictiveBackHandler and LaunchedEffect because the
// "what is currently happening" answer is always unambiguous.
// ─────────────────────────────────────────────────────────────────────────────
private sealed class NavState {
    /** Nothing is animating. Only the top screen is rendered. */
    object Idle : NavState()

    /**
     * User is actively dragging — predictive back gesture in progress.
     * [progress] is 0..1 sourced directly from BackEvent (no Animatable overhead).
     * [underRoute] is the screen beneath, shown as parallax layer.
     */
    data class GestureDragging(val progress: Float, val underRoute: ScreenRoute?) : NavState()

    /**
     * User released and committed the gesture — animating the exit to 1f.
     * [progress] is driven by Animatable, starts from where gesture left off.
     * [underRoute] is the screen beneath, still visible.
     */
    data class GestureCommitting(val progress: Float, val underRoute: ScreenRoute?) : NavState()

    /**
     * User released and cancelled the gesture — spring back to 0.
     * [progress] is driven by Animatable spring.
     */
    data class GestureCancelling(val progress: Float) : NavState()

    /**
     * Discrete pop triggered by back button / programmatic call.
     * [exitRoute] slides out to the right.
     * [progress] goes from 0 → 1.
     */
    data class Popping(val exitRoute: ScreenRoute, val progress: Float) : NavState()

    /**
     * New screen pushed — slides in from the right.
     * [underRoute] is the screen that was on top before the push.
     * [progress] goes from 0 → 1.
     */
    data class Pushing(val underRoute: ScreenRoute, val progress: Float) : NavState()
}

/**
 * High-performance overlay navigation container for CA'NIM.
 *
 * Architecture — State Machine Navigation:
 *
 * A single [NavState] sealed class is the ONLY source of truth for what is
 * happening at any moment. The states are mutually exclusive, eliminating:
 *   - Race conditions between PredictiveBackHandler and LaunchedEffect
 *   - Double-pop bugs from concurrent pop triggers
 *   - Visual flashes from finally-block resets interrupting active animations
 *
 * Gesture driver: Native [PredictiveBackHandler] only (Android 13+ covers all
 * cases — hardware back, 3-button nav, and edge swipe with live progress).
 *
 * Rendering: Dual-layer parallax + scale + scrim (only during transitions;
 * idle state renders only the top screen for zero background overhead).
 */
@Composable
fun PredictiveBackOverlayContainer(
    screenStack: List<ScreenRoute>,
    onPopScreen: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (route: ScreenRoute, isTopScreen: Boolean) -> Unit
) {
    // The one and only state. All renders and animations derive from this.
    var navState by remember { mutableStateOf<NavState>(NavState.Idle) }

    // Animatable used only for commit and cancel springs — NOT for live drag.
    // Live drag reads progress directly from BackEvent for zero latency.
    val springAnim = remember { Animatable(0f) }

    // Tracks the previous stack so LaunchedEffect can detect push vs pop.
    var previousStack by remember { mutableStateOf(screenStack) }

    // ── Predictive Back Handler ───────────────────────────────────────────────
    //
    // This is the ONLY gesture driver. CancellationException MUST be rethrown
    // so Compose structured concurrency can clean up. The finally block ONLY
    // resets the state flag — it does NOT touch springAnim, because by the
    // time finally runs the spring has already completed (or was cancelled).
    PredictiveBackHandler(enabled = screenStack.isNotEmpty()) { progressFlow ->
        val underRoute = if (screenStack.size > 1) screenStack[screenStack.size - 2] else null
        var lastProgress = 0f

        try {
            // Live drag — read progress directly, no Animatable overhead.
            progressFlow.collect { backEvent ->
                lastProgress = backEvent.progress
                navState = NavState.GestureDragging(lastProgress, underRoute)
            }

            // Gesture committed. Animate remaining gap to 1f, then pop.
            // The springAnim drives rendering from here via GestureCommitting state.
            springAnim.snapTo(lastProgress)
            navState = NavState.GestureCommitting(lastProgress, underRoute)
            springAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
            )
            // Pop AFTER the exit animation fully completes.
            // State transition to Idle happens in LaunchedEffect(screenStack)
            // once the stack update propagates — no flag needed.
            onPopScreen()

        } catch (e: CancellationException) {
            // Gesture cancelled — spring back to resting position.
            springAnim.snapTo(lastProgress)
            navState = NavState.GestureCancelling(lastProgress)
            springAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            )
            navState = NavState.Idle
            throw e  // MUST rethrow — do NOT swallow CancellationException

        } finally {
            // Only reached after try or catch fully complete.
            // springAnim is already at its final value; do NOT snapTo(0f) here
            // or it will teleport the animation mid-render on the commit path.
        }
    }

    // ── Discrete Navigation Transitions ──────────────────────────────────────
    //
    // Handles push and pop triggered by buttons / programmatic calls.
    // Gesture-initiated pops are handled above and will cause navState to be
    // GestureCommitting when this LaunchedEffect runs — so we skip them by
    // checking the current state rather than a boolean flag.
    LaunchedEffect(screenStack) {
        val oldStack = previousStack
        previousStack = screenStack

        when {
            // ── POP ──────────────────────────────────────────────────────────
            screenStack.size < oldStack.size -> {
                val currentState = navState

                if (currentState is NavState.GestureCommitting ||
                    currentState is NavState.GestureDragging) {
                    // Gesture handled this pop — transition to Idle and let
                    // the handler coroutine clean up springAnim naturally.
                    navState = NavState.Idle
                } else if (oldStack.size > 1) {
                    // Discrete pop: slide the exiting screen out to the right.
                    val exitRoute = oldStack.last()
                    springAnim.snapTo(0f)
                    navState = NavState.Popping(exitRoute, 0f)
                    springAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    )
                    navState = NavState.Idle
                } else {
                    // Stack went to empty (e.g. clearScreenStack) — just idle.
                    navState = NavState.Idle
                }
            }

            // ── PUSH ─────────────────────────────────────────────────────────
            screenStack.size > oldStack.size && oldStack.isNotEmpty() -> {
                val underRoute = oldStack.last()
                springAnim.snapTo(0f)
                navState = NavState.Pushing(underRoute, 0f)
                springAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
                navState = NavState.Idle
            }
        }
    }

    // ── AnimatedVisibility: entire stack slides in/out from below ─────────────
    val isVisible = screenStack.isNotEmpty() ||
        navState is NavState.Popping ||
        navState is NavState.GestureCommitting

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(260, easing = FastOutSlowInEasing)
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

            val state = navState
            val currentTopScreen = screenStack.lastOrNull()
            val isMultiLayer = screenStack.size > 1

            Box(modifier = Modifier.fillMaxSize()) {

                // ── UNDERLYING SCREEN ─────────────────────────────────────────
                // Rendered only when a transition requires it. In Idle state
                // this block is skipped entirely → zero background overhead.

                val underScreen: ScreenRoute?
                val underProgress: Float

                when (state) {
                    is NavState.GestureDragging -> {
                        underScreen = state.underRoute
                        underProgress = state.progress
                    }
                    is NavState.GestureCommitting -> {
                        underScreen = state.underRoute
                        underProgress = springAnim.value
                    }
                    is NavState.GestureCancelling -> {
                        underScreen = if (isMultiLayer) screenStack.getOrNull(screenStack.size - 2) else null
                        underProgress = springAnim.value
                    }
                    is NavState.Popping -> {
                        underScreen = currentTopScreen
                        underProgress = springAnim.value
                    }
                    is NavState.Pushing -> {
                        underScreen = state.underRoute
                        underProgress = 1f - springAnim.value
                    }
                    NavState.Idle -> {
                        underScreen = null
                        underProgress = 0f
                    }
                }

                if (underScreen != null && underProgress >= 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BlackBg)
                            .graphicsLayer {
                                // Parallax: slides in from -22% to 0 as underProgress → 1
                                translationX = -(1f - underProgress) * (widthPx * 0.22f)
                                val scale = 0.95f + (underProgress * 0.05f)
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        content(underScreen, false)

                        // Scrim darkens under-screen; clears as it comes to front
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

                // ── TOP / ACTIVE SCREEN ───────────────────────────────────────
                // The screen the user is currently on. Its transform depends
                // entirely on the current NavState.

                if (currentTopScreen != null) {
                    val topTranslationX: Float
                    val topTranslationY: Float
                    val topScale: Float
                    val topCornerDp: Float
                    val topAlpha: Float
                    val showTop: Boolean

                    when (state) {
                        is NavState.GestureDragging -> {
                            val p = state.progress
                            showTop = true
                            if (isMultiLayer) {
                                // Multi-layer: slide right, subtle scale, corner rounding
                                topTranslationX = p * widthPx
                                topTranslationY = 0f
                                topScale = 1f - (p * 0.05f)
                                topCornerDp = p * 16f
                                topAlpha = 1f
                            } else {
                                // Back to tabs: slide + shrink + fade
                                topTranslationX = p * (widthPx * 0.15f)
                                topTranslationY = p * (heightPx * 0.08f)
                                topScale = 1f - (p * 0.08f)
                                topCornerDp = p * 16f
                                topAlpha = 1f - (p * 0.25f)
                            }
                        }
                        is NavState.GestureCommitting -> {
                            val p = springAnim.value
                            showTop = true
                            if (isMultiLayer) {
                                topTranslationX = p * widthPx
                                topTranslationY = 0f
                                topScale = 1f - (p * 0.05f)
                                topCornerDp = p * 16f
                                topAlpha = 1f
                            } else {
                                topTranslationX = p * (widthPx * 0.15f)
                                topTranslationY = p * (heightPx * 0.08f)
                                topScale = 1f - (p * 0.08f)
                                topCornerDp = p * 16f
                                topAlpha = 1f - (p * 0.25f)
                            }
                        }
                        is NavState.GestureCancelling -> {
                            val p = springAnim.value
                            showTop = true
                            topTranslationX = p * widthPx
                            topTranslationY = 0f
                            topScale = 1f - (p * 0.05f)
                            topCornerDp = p * 16f
                            topAlpha = 1f
                        }
                        is NavState.Pushing -> {
                            // New screen slides in from the right
                            showTop = true
                            topTranslationX = (1f - springAnim.value) * widthPx
                            topTranslationY = 0f
                            topScale = 1f
                            topCornerDp = 0f
                            topAlpha = 1f
                        }
                        is NavState.Popping -> {
                            // During pop, the exiting screen is drawn by the block below;
                            // currentTopScreen is the newly revealed screen — keep it at rest.
                            showTop = true
                            topTranslationX = 0f
                            topTranslationY = 0f
                            topScale = 1f
                            topCornerDp = 0f
                            topAlpha = 1f
                        }
                        NavState.Idle -> {
                            showTop = true
                            topTranslationX = 0f
                            topTranslationY = 0f
                            topScale = 1f
                            topCornerDp = 0f
                            topAlpha = 1f
                        }
                    }

                    if (showTop) {
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
                                    if (topCornerDp > 0f) {
                                        clip = true
                                        shape = RoundedCornerShape(topCornerDp.dp)
                                    }
                                }
                        ) {
                            content(currentTopScreen, true)
                        }
                    }
                }

                // ── EXITING SCREEN (discrete pop only) ────────────────────────
                // Slides out to the right while the under-screen is revealed.
                // Gesture commits do NOT use this block — they animate the top
                // screen directly via GestureCommitting state above.
                if (state is NavState.Popping) {
                    val p = springAnim.value
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BlackBg)
                            .graphicsLayer {
                                translationX = p * widthPx
                                val scale = 1f - (p * 0.05f)
                                scaleX = scale
                                scaleY = scale
                                if (p > 0.01f) {
                                    clip = true
                                    shape = RoundedCornerShape((p * 16f).dp)
                                }
                            }
                    ) {
                        content(state.exitRoute, false)
                    }
                }
            }
        }
    }
}
