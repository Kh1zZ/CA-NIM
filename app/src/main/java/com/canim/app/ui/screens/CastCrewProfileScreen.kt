package com.canim.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.canim.app.data.model.CastCrewProfile
import com.canim.app.data.model.FilmographyItem
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastCrewProfileScreen(
    profile: CastCrewProfile?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSelectMedia: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var isBioExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = profile?.name ?: "Profil Cast / Crew",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBg)
            )
        },
        containerColor = BlackBg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (isLoading || profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                    Text(
                        text = "Memuat data biografi dari AniList...",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Avatar & Names
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = profile.image,
                            contentDescription = profile.name,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .border(3.dp, AccentBlue, CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Text(
                            text = profile.name,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )

                        if (!profile.nativeName.isNullOrBlank()) {
                            Text(
                                text = profile.nativeName,
                                color = AccentBlueLight,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (profile.isStaff) AccentBlue.copy(alpha = 0.15f) else Color(0xFFA855F7).copy(alpha = 0.15f))
                                .border(
                                    1.dp,
                                    if (profile.isStaff) AccentBlue.copy(alpha = 0.5f) else Color(0xFFA855F7).copy(alpha = 0.5f),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (profile.isStaff) "Staf / Pengisi Suara" else "Karakter Fiksi",
                                color = if (profile.isStaff) AccentBlue else Color(0xFFA855F7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Public Details Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "DETAIL PUBLIK",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            if (!profile.nativeName.isNullOrBlank()) {
                                ProfileDetailRow("Nama Asli / Native", profile.nativeName)
                            }
                            if (!profile.gender.isNullOrBlank()) {
                                ProfileDetailRow("Jenis Kelamin", profile.gender)
                            }
                            if (!profile.birthday.isNullOrBlank()) {
                                val dobText = if (!profile.age.isNullOrBlank()) {
                                    "${profile.birthday} (${profile.age} Tahun)"
                                } else {
                                    profile.birthday
                                }
                                ProfileDetailRow("Tanggal Lahir", dobText)
                            }
                            if (!profile.nationality.isNullOrBlank()) {
                                ProfileDetailRow("Asal / Kebangsaan", profile.nationality)
                            }
                        }
                    }
                }

                // Biography Section
                if (!profile.biography.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "BIOGRAFI",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )

                                val cleanBio = remember(profile.biography) {
                                    profile.biography
                                        .replace(Regex("<br\\s*/?>"), "\n")
                                        .replace(Regex("<[^>]*>"), "")
                                        .replace(Regex("~!|!~"), "")
                                        .trim()
                                }

                                Text(
                                    text = cleanBio,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    maxLines = if (isBioExpanded) Int.MAX_VALUE else 5,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (cleanBio.length > 200) {
                                    Text(
                                        text = if (isBioExpanded) "Tutup Selengkapnya" else "Baca Selengkapnya...",
                                        color = AccentBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { isBioExpanded = !isBioExpanded }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Filmography Section
                item {
                    Text(
                        text = "FILMOGRAFI (${profile.filmography.size})",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                if (profile.filmography.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Tidak ada riwayat filmografi yang terdaftar di AniList.",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    items(profile.filmography) { item ->
                        FilmographyCard(
                            item = item,
                            onClick = {
                                val media = MediaItem(
                                    malId = item.malId,
                                    anilistId = item.id,
                                    title = item.title,
                                    titleEnglish = item.title,
                                    imageUrl = item.imageUrl ?: "",
                                    type = if (item.format == "MANGA") MediaType.MANGA else MediaType.ANIME,
                                    score = 0.0,
                                    synopsis = "",
                                    year = item.year,
                                    format = item.format
                                )
                                onSelectMedia(media)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilmographyCard(
    item: FilmographyItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .width(46.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.role.isNullOrBlank()) {
                    Text(
                        text = item.role,
                        color = AccentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${item.format ?: "TV"}${if (item.year != null) " • ${item.year}" else ""}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
