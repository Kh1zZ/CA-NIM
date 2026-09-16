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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.canim.app.ui.theme.BlackBg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * High-performance overlay navigation container inspired by Animite.
 *
 * Key features:
 * 1. Stage-Based Predictive Back Gesture: Tracks swipe distance in real-time with smooth
 *    corner rounding, scaling, and parallax reveal of the previous screen.
 * 2. True Two-Layer Stack Rendering: When stack depth > 1, the immediate underlying screen
 *    is kept fully composed with an opaque background directly behind the active screen,
 *    eliminating blank frames, stutter, and active tab background leaks on multi-back.
 * 3. Unified Stack Boundaries: Initial stack entry slides smoothly up from bottom; final
 *    stack exit slides down off bottom edge.
 */
@Composable
fun PredictiveBackOverlayContainer(
    screenStack: List<ScreenRoute>,
    onPopScreen: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ScreenRoute) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Predictive back gesture state
    var isPredictiveActive by remember { mutableStateOf(false) }
    var predictiveProgress by remember { mutableFloatStateOf(0f) }
    val predictiveProgressAnim = remember { Animatable(0f) }

    // Discrete navigation transition state
    var previousStack by remember { mutableStateOf(screenStack) }
    var exitingRoute by remember { mutableStateOf<ScreenRoute?>(null) }
    val inStackPopProgress = remember { Animatable(0f) }
    val inStackPushProgress = remember { Animatable(0f) }

    // Gesture back handler
    PredictiveBackHandler(enabled = screenStack.isNotEmpty()) { progressFlow ->
        try {
            isPredictiveActive = true
            progressFlow.collect { backEvent ->
                predictiveProgress = backEvent.progress
            }

            // Gesture committed: animate remaining progress smoothly and pop
            predictiveProgressAnim.snapTo(predictiveProgress)
            predictiveProgressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
            isPredictiveActive = false
            predictiveProgress = 0f
            predictiveProgressAnim.snapTo(0f)
            onPopScreen()
        } catch (e: CancellationException) {
            // Gesture cancelled: spring smoothly back to resting position
            predictiveProgressAnim.snapTo(predictiveProgress)
            predictiveProgressAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy
                )
            )
            isPredictiveActive = false
            predictiveProgress = 0f
            predictiveProgressAnim.snapTo(0f)
        }
    }

    // Detect stack changes for discrete transitions (e.g. button click or non-gesture pop)
    LaunchedEffect(screenStack) {
        val oldStack = previousStack
        previousStack = screenStack

        if (screenStack.size < oldStack.size) {
            // Popped
            if (!isPredictiveActive) {
                val popped = oldStack.lastOrNull()
                if (popped != null && oldStack.size > 1) {
                    // In-stack pop (e.g. Detail 2 -> Cast VA)
                    exitingRoute = popped
                    inStackPopProgress.snapTo(0f)
                    inStackPopProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    )
                    exitingRoute = null
                    inStackPopProgress.snapTo(0f)
                } else if (screenStack.isEmpty()) {
                    // Final exit handled by AnimatedVisibility below
                    exitingRoute = null
                }
            }
        } else if (screenStack.size > oldStack.size && oldStack.isNotEmpty()) {
            // In-stack push (e.g. Detail 1 -> Cast VA)
            inStackPushProgress.snapTo(1f)
            inStackPushProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
    }

    val isStackEmpty = screenStack.isEmpty() && exitingRoute == null

    AnimatedVisibility(
        visible = !isStackEmpty,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(260, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(240, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(180)),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(BlackBg)
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            val effectiveProgress = if (isPredictiveActive) {
                predictiveProgress
            } else {
                predictiveProgressAnim.value
            }

            val currentTopScreen = screenStack.lastOrNull()
            val previousScreen = if (screenStack.size > 1) {
                screenStack[screenStack.size - 2]
            } else if (exitingRoute != null && screenStack.isNotEmpty()) {
                screenStack.last()
            } else {
                null
            }

            val activeExitingRoute = exitingRoute

            // 1. UNDERLYING SCREEN (Screen N-1):
            // Kept composed directly underneath the top screen with solid BlackBg,
            // acting as a complete physical shield against background tab leaks.
            if (previousScreen != null) {
                val underParallaxProgress = when {
                    isPredictiveActive || predictiveProgressAnim.value > 0f -> effectiveProgress
                    inStackPopProgress.value > 0f -> inStackPopProgress.value
                    inStackPushProgress.value > 0f -> 1f - inStackPushProgress.value
                    else -> 0f
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BlackBg)
                        .graphicsLayer {
                            translationX = -(1f - underParallaxProgress) * (widthPx * 0.22f)
                            val scale = 0.94f + (underParallaxProgress * 0.06f)
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    content(previousScreen)
                }
            }

            // 2. TOP ACTIVE SCREEN (Screen N):
            // Renders the current top of the stack with gesture or push/pop animations.
            if (currentTopScreen != null) {
                val isMultiLayer = screenStack.size > 1 || activeExitingRoute != null

                val topTranslationX: Float
                val topTranslationY: Float
                val topScale: Float
                val topCornerRadiusDp: Float

                if (isPredictiveActive || predictiveProgressAnim.value > 0f) {
                    if (isMultiLayer) {
                        topTranslationX = effectiveProgress * widthPx
                        topTranslationY = 0f
                        topScale = 1f - (effectiveProgress * 0.08f)
                        topCornerRadiusDp = effectiveProgress * 16f
                    } else {
                        // Single layer stack exit to active tab: slight scale and downward shift
                        topTranslationX = effectiveProgress * (widthPx * 0.12f)
                        topTranslationY = effectiveProgress * (heightPx * 0.08f)
                        topScale = 1f - (effectiveProgress * 0.1f)
                        topCornerRadiusDp = effectiveProgress * 16f
                    }
                } else if (inStackPushProgress.value > 0f) {
                    // Animating in from right on push
                    topTranslationX = inStackPushProgress.value * widthPx
                    topTranslationY = 0f
                    topScale = 1f
                    topCornerRadiusDp = 0f
                } else {
                    topTranslationX = 0f
                    topTranslationY = 0f
                    topScale = 1f
                    topCornerRadiusDp = 0f
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
                            if (topCornerRadiusDp > 0f) {
                                clip = true
                                shape = RoundedCornerShape(topCornerRadiusDp.dp)
                            }
                        }
                ) {
                    content(currentTopScreen)
                }
            }

            // 3. EXITING SCREEN (During discrete pop):
            // Smoothly slides out to the right over the underlying screen.
            if (activeExitingRoute != null && inStackPopProgress.value > 0f) {
                val popExitProgress = inStackPopProgress.value
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BlackBg)
                        .graphicsLayer {
                            translationX = popExitProgress * widthPx
                            val scale = 1f - (popExitProgress * 0.05f)
                            scaleX = scale
                            scaleY = scale
                            if (popExitProgress > 0.05f) {
                                clip = true
                                shape = RoundedCornerShape((popExitProgress * 14f).dp)
                            }
                        }
                ) {
                    content(activeExitingRoute)
                }
            }
        }
    }
}
