package com.canim.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canim.app.R
import com.canim.app.ui.theme.*

/**
 * Dedicated login screen requiring MyAnimeList authentication before normal CA'NIM access.
 * Fits the modern dark cyber aesthetic of CA'NIM.
 */
@Composable
fun LoginScreen(
    onLoginMal: () -> Unit,
    isExchangingToken: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Branding Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(CardElevated, CardBg)
                        )
                    )
                    .border(1.5.dp, CardBorderSubtle, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "Logo CA'NIM",
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "CA'NIM",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Text(
                text = "Anime & Manga Hub",
                color = AccentBlueLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Info Card with Feature Highlights
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Masuk untuk Memulai",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Aplikasi CA'NIM membutuhkan otentikasi resmi MyAnimeList agar seluruh progress, watchlist, dan riwayat anime/manga Anda tersinkronisasi aman.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(
                        color = CardBorderSubtle,
                        thickness = 1.dp
                    )

                    FeatureHighlightItem(
                        icon = Icons.Default.Sync,
                        title = "Sinkronisasi Otomatis",
                        desc = "Watchlist & riwayat tersimpan dua arah dengan MAL"
                    )

                    FeatureHighlightItem(
                        icon = Icons.Default.Lock,
                        title = "Aman & Resmi (OAuth2)",
                        desc = "Login langsung via situs resmi MyAnimeList PKCE"
                    )

                    FeatureHighlightItem(
                        icon = Icons.Default.CheckCircle,
                        title = "Offline & Real-Time",
                        desc = "Tetap dapat dibuka offline dengan pemulihan cerdas"
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Prominent Login Action Button
            Button(
                onClick = onLoginMal,
                enabled = !isExchangingToken,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_screen_mal_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E51A2), // MAL Brand Blue
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isExchangingToken) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Menghubungkan Akun...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Login dengan MyAnimeList",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Dengan masuk, Anda menyetujui izin sinkronisasi profil & daftar tontonan MAL Anda di CA'NIM.",
                color = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun FeatureHighlightItem(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AccentBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(17.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = desc,
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}
