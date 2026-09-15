package com.canim.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canim.app.NavItem
import com.canim.app.R
import com.canim.app.ui.theme.*

/**
 * Premium Adaptive Navigation Rail for Android Tablets, Foldables, and Wide Displays (>= 600dp).
 * Provides ergonomic start-edge navigation, preserving vertical reading height in landscape mode
 * and eliminating stretched bottom bars.
 */
@Composable
fun AdaptiveNavigationRail(
    navItems: List<NavItem>,
    activeTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CardBg,
        contentColor = TextPrimary,
        tonalElevation = 6.dp,
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .testTag("adaptive_navigation_rail")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .border(
                    width = 1.dp,
                    color = CardBorderSubtle,
                    shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)
                )
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: App Icon / Branding
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 12.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, CardBorderSubtle, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_icon),
                    contentDescription = "CA'NIM",
                    modifier = Modifier.size(30.dp)
                )
            }

            // Center: Navigation Items with Cyber Search Highlight
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                navItems.forEachIndexed { _, item ->
                    val selected = activeTab == item.route
                    val isSearch = item.route == "search"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onTabSelected(item.route)
                            }
                            .padding(vertical = 4.dp)
                            .testTag("rail_tab_${item.route}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isSearch) {
                            // Unique Cyber Floating Search Button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) {
                                            Brush.linearGradient(listOf(AccentBlue, Color(0xFF1D4ED8)))
                                        } else {
                                            Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                                        }
                                    )
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color(0xFF60A5FA) else CardBorderSubtle,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = item.title,
                                    tint = if (selected) Color.White else AccentBlueLight,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            // Standard Tab with animated indicator pill background
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .width(52.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(
                                        if (selected) AccentBlue.copy(alpha = 0.16f) else Color.Transparent
                                    )
                                    .border(
                                        width = if (selected) 1.dp else 0.dp,
                                        color = if (selected) AccentBlue.copy(alpha = 0.35f) else Color.Transparent,
                                        shape = RoundedCornerShape(19.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                    tint = if (selected) AccentBlue else TextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.title,
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) AccentBlue else TextMuted
                        )
                    }
                }
            }

            // Bottom spacer for balance
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
