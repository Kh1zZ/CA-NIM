package com.canim.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canim.app.R
import com.canim.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Cold-start splash screen animation displayed once upon initial application boot.
 * Modular and easily replaceable when a custom Lottie or video asset is supplied.
 */
@Composable
fun ColdStartSplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 1200L
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cold_start_anim")

    // Pulsing glowing ring scale
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    // Pulsing ring alpha
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )

    // Smooth logo entrance scale
    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }

    // Loading progress line
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Parallel launch entrance animation
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, animationSpec = tween(400))
        progress.animateTo(1f, animationSpec = tween(durationMs.toInt() - 200, easing = FastOutSlowInEasing))
        delay(150L)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBg),
        contentAlignment = Alignment.Center
    ) {
        // Subtle cyber radial glow background
        Box(
            modifier = Modifier
                .size(320.dp)
                .scale(ringScale)
                .alpha(ringAlpha * 0.4f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlue.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
        )

        // Outer cyber ring
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(ringScale)
                .alpha(ringAlpha)
                .border(1.5.dp, AccentBlue.copy(alpha = 0.6f), CircleShape)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(logoScale.value)
                .alpha(logoAlpha.value)
        ) {
            // App Logo
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "CA'NIM Logo",
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(2.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "CA'NIM",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Text(
                text = "Tracker & Discovery",
                color = AccentBlueLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(AccentBlue, Color(0xFF38BDF8), AccentGreen)
                            )
                        )
                )
            }
        }
    }
}
