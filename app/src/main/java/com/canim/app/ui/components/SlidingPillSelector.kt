package com.canim.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canim.app.ui.theme.AccentBlue
import com.canim.app.ui.theme.CardBg
import com.canim.app.ui.theme.CardBorderSubtle
import com.canim.app.ui.theme.TextSecondary

/**
 * Reusable horizontal sliding-highlight pill selector for filter chips and sort options.
 * Accurately measures each child's position and slides the highlight pill smoothly using spring physics.
 */
@Composable
fun <T> SlidingPillSelector(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    badgeProvider: ((T) -> String?)? = null,
    highlightColor: Color = AccentBlue,
    containerColor: Color = CardBg,
    cornerRadius: Dp = 20.dp,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 6.dp,
    fontSize: Float = 12f
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    // Store item layout bounds: x position and width in Dp
    val itemPositions = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)

    val currentTarget = itemPositions[selectedIndex] ?: Pair(0.dp, 0.dp)

    val targetOffsetX by animateDpAsState(
        targetValue = currentTarget.first,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sliding_pill_offset"
    )

    val targetWidth by animateDpAsState(
        targetValue = currentTarget.second,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sliding_pill_width"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .border(1.dp, CardBorderSubtle, RoundedCornerShape(cornerRadius))
            .horizontalScroll(scrollState)
            .padding(3.dp)
    ) {
        // Sliding highlight pill
        if (targetWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = targetOffsetX)
                    .width(targetWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(cornerRadius - 2.dp))
                    .background(highlightColor)
            )
        }

        Row(
            modifier = Modifier.wrapContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else TextSecondary,
                    animationSpec = tween(150),
                    label = "pill_text_color"
                )

                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            val xDp = with(density) { coordinates.positionInParent().x.toDp() }
                            val widthDp = with(density) { coordinates.size.width.toDp() }
                            itemPositions[index] = Pair(xDp, widthDp)
                        }
                        .clip(RoundedCornerShape(cornerRadius - 2.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemSelected(item) }
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = labelProvider(item),
                            fontSize = fontSize.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = textColor,
                            maxLines = 1
                        )
                        badgeProvider?.invoke(item)?.let { badge ->
                            if (badge.isNotBlank()) {
                                Text(
                                    text = badge,
                                    fontSize = (fontSize - 1.5f).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextSecondary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
