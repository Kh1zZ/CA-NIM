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
import androidx.compose.ui.unit.dp
import com.canim.app.ui.theme.BlackBg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * High-Performance Two-Layer Overlay Navigation Container for CA'NIM.
 *
 * Architecture & Design Principles:
 * 1. Pure Horizontal Kinematics (Zero Diagonal / "Nyerong" Movement):
 *    - All push entries slide in from the RIGHT (width -> 0).
 *    - All pop exits slide out to the RIGHT (0 -> width).
 *    - Under-screen follows with a synchronized -25% parallax shift and smooth darkening scrim.
 *    - Absolutely zero translationY is applied, guaranteeing 100% natural, thumb-aligned gestures.
 *
 * 2. Uninterruptible Gesture Cancellation (Zero "Layar Nyangkut"):
 *    - Cancel recovery animation is wrapped inside `withContext(NonCancellable)`.
 *    - When the system cancels the gesture flow, the spring reset to 0f runs to full completion
 *      without being aborted by coroutine cancellation, preventing frozen offset states.
 *
 * 3. 100% Off-screen Commit (Zero "Layar Blink"):
 *    - When a swipe-to-back gesture is committed, the foreground screen is animated fully to
 *      1.0f (entirely outside the right viewport) BEFORE calling `onPopScreen()`.
 *    - Deterministic `wasGesturePop` flag prevents `LaunchedEffect(screenStack)` from triggering
 *      a duplicate discrete exit animation.
 *
 * 4. Transparent Root & Zero-Overhead Idle State:
 *    - The container itself is transparent; when `screenStack.isEmpty()` and no transition is running,
 *      zero layers are drawn and all pointer events pass directly to the bottom tab bar.
 *    - When idle with active screens, only the top screen is composed, preventing background
 *      recomposition or duplicate network calls via `isTopScreen = true`.
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

    // ── Discrete Animation State (Button Pop & Push) ──────────────────────────
    var previousStack by remember { mutableStateOf(screenStack) }
    var exitingRoute by remember { mutableStateOf<ScreenRoute?>(null) }
    val popAnim = remember { Animatable(0f) }

    var pushUnderRoute by remember { mutableStateOf<ScreenRoute?>(null) }
    val pushAnim = remember { Animatable(0f) }

    // ── 1. Native Predictive Back Handler (System Edge Swipe) ─────────────────
    PredictiveBackHandler(enabled = screenStack.isNotEmpty()) { progressFlow ->
        try {
            isGestureActive = true
            progressFlow.collect { backEvent ->
                gestureProgress = backEvent.progress
            }

            // Gesture committed: animate remaining distance to 1.0f (fully offscreen right)
            wasGesturePop = true
            gestureAnim.snapTo(gestureProgress)
            gestureAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
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
                    // Gesture already smoothly slid the screen offscreen to 1f; skip discrete anim
                    wasGesturePop = false
                } else {
                    // Discrete pop (e.g. user clicked the back button)
                    val popped = oldStack.lastOrNull()
                    if (popped != null) {
                        exitingRoute = popped
                        popAnim.snapTo(0f)
                        popAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                        )
                        exitingRoute = null
                        popAnim.snapTo(0f)
                    }
                }
            }

            // ── PUSH (Stack size increased) ───────────────────────────────────
            screenStack.size > oldStack.size -> {
                pushUnderRoute = oldStack.lastOrNull()
                pushAnim.snapTo(0f)
                pushAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
                pushUnderRoute = null
                pushAnim.snapTo(0f)
            }
        }
    }

    // ── 3. Kinematic Render Pass ──────────────────────────────────────────────
    val hasContent = screenStack.isNotEmpty() || exitingRoute != null || isGestureActive

    if (!hasContent) {
        // Completely idle with empty stack: zero layout, zero draw overhead, tabs 100% active
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val currentTopScreen = screenStack.lastOrNull()

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
                showTabsScrim = screenStack.size <= 1
                tabsScrimAlpha = (1f - p) * 0.35f
            }
            exitingRoute != null -> {
                val p = popAnim.value
                underScreen = currentTopScreen
                underProgress = p
                showTabsScrim = screenStack.isEmpty()
                tabsScrimAlpha = (1f - p) * 0.35f
            }
            pushUnderRoute != null -> {
                val p = pushAnim.value
                underScreen = pushUnderRoute
                underProgress = 1f - p
                showTabsScrim = false
                tabsScrimAlpha = 0f
            }
            else -> {
                underScreen = null
                underProgress = 0f
                showTabsScrim = false
                tabsScrimAlpha = 0f
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {

            // ── Background Scrim for Tabs (when Stack is 1 -> 0) ───────────────
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
                                translationX = -(1f - underProgress) * (widthPx * 0.25f)
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

            // ── Determine Layer 2 (Foreground Screen & Exit Progress) ──────────
            val foregroundRoute: ScreenRoute?
            val foregroundProgress: Float // 0f = centered/resting, 1f = fully offscreen to right
            val foregroundIsTop: Boolean

            when {
                exitingRoute != null -> {
                    foregroundRoute = exitingRoute
                    foregroundProgress = popAnim.value
                    foregroundIsTop = false
                }
                isGestureActive -> {
                    foregroundRoute = currentTopScreen
                    foregroundProgress = if (gestureAnim.isRunning) gestureAnim.value else gestureProgress
                    foregroundIsTop = true
                }
                pushUnderRoute != null -> {
                    foregroundRoute = currentTopScreen
                    foregroundProgress = (1f - pushAnim.value).coerceIn(0f, 1f)
                    foregroundIsTop = true
                }
                currentTopScreen != null -> {
                    foregroundRoute = currentTopScreen
                    foregroundProgress = 0f
                    foregroundIsTop = true
                }
                else -> {
                    foregroundRoute = null
                    foregroundProgress = 0f
                    foregroundIsTop = false
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
                                translationX = foregroundProgress * widthPx
                                val scale = 1f - (foregroundProgress * 0.05f)
                                scaleX = scale
                                scaleY = scale
                                val cornerRadius = foregroundProgress * 16f
                                if (cornerRadius > 0.5f) {
                                    clip = true
                                    shape = RoundedCornerShape(cornerRadius.dp)
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

