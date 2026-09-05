package com.canim.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.canim.app.util.TextSanitizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastCrewProfileScreen(
    profile: CastCrewProfile?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSelectMedia: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var isBioExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("Semua") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (profile?.isStaff == true) "Profil Staf / Pengisi Suara" else "Profil Karakter",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                    Text("Memuat data profil...", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            val cleanBio = remember(profile.biography) {
                TextSanitizer.sanitize(profile.biography)
            }

            // Available filter options based on available filmography
            val hasAnime = remember(profile.filmography) { profile.filmography.any { it.type == MediaType.ANIME } }
            val hasManga = remember(profile.filmography) { profile.filmography.any { it.type == MediaType.MANGA } }
            val hasVa = remember(profile.filmography) { profile.filmography.any { !it.characterName.isNullOrBlank() } }
            val hasStaffRole = remember(profile.filmography) { profile.filmography.any { it.characterName.isNullOrBlank() } }

            val filterOptions = remember(profile.filmography) {
                val list = mutableListOf("Semua")
                if (hasAnime && hasManga) {
                    list.add("Anime")
                    list.add("Manga")
                }
                if (profile.isStaff && hasVa && hasStaffRole) {
                    list.add("Pengisi Suara")
                    list.add("Staf")
                }
                list
            }

            val filteredFilmography = remember(profile.filmography, selectedFilter) {
                when (selectedFilter) {
                    "Anime" -> profile.filmography.filter { it.type == MediaType.ANIME }
                    "Manga" -> profile.filmography.filter { it.type == MediaType.MANGA }
                    "Pengisi Suara" -> profile.filmography.filter { !it.characterName.isNullOrBlank() }
                    "Staf" -> profile.filmography.filter { it.characterName.isNullOrBlank() }
                    else -> profile.filmography
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Bio Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = profile.image,
                                contentDescription = profile.name,
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, if (profile.isStaff) MangaAccentDarkBlue else AccentBlue, CircleShape),
                                contentScale = ContentScale.Crop
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = profile.name,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!profile.nativeName.isNullOrBlank()) {
                                    Text(
                                        text = profile.nativeName,
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                val badgeTitle = when {
                                    !profile.isStaff -> "KARAKTER"
                                    hasVa -> "VA / SEIYUU"
                                    else -> "STAF PRODUKSI"
                                }
                                val badgeColor = if (!profile.isStaff) AccentBlue else if (hasVa) StarGold else MangaAccentDarkBlue
                                Surface(
                                    color = badgeColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = badgeTitle,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Public Detail Card
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
                                text = "DETAIL INFORMASI",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            if (!profile.gender.isNullOrBlank()) {
                                BioRowItem(label = "Jenis Kelamin", value = profile.gender)
                            }
                            if (!profile.birthday.isNullOrBlank()) {
                                BioRowItem(label = "Tanggal Lahir", value = profile.birthday)
                            }
                            if (!profile.age.isNullOrBlank()) {
                                BioRowItem(label = "Usia", value = "${profile.age} Tahun")
                            }
                            if (!profile.nationality.isNullOrBlank()) {
                                BioRowItem(label = "Nationality", value = profile.nationality)
                            }
                            BioRowItem(label = "Total Entri Filmografi", value = "${profile.filmography.size} Judul")
                        }
                    }
                }

                // Biography Section
                if (cleanBio.isNotBlank()) {
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

                                Text(
                                    text = cleanBio,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    maxLines = if (isBioExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (cleanBio.length > 200) {
                                    Text(
                                        text = if (isBioExpanded) "Tutup Biografi" else "Baca Selengkapnya...",
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

                // Filmography Header with Filter Chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "DAFTAR FILMOGRAFI (${filteredFilmography.size})",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        if (filterOptions.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filterOptions) { filter ->
                                    val isSelected = selectedFilter == filter
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) AccentBlue else CardBg)
                                            .border(1.dp, if (isSelected) AccentBlue else CardBorder, RoundedCornerShape(8.dp))
                                            .clickable { selectedFilter = filter }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = filter,
                                            color = if (isSelected) Color.White else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Filmography Items
                if (filteredFilmography.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tidak ada judul untuk kategori filter ini.", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                } else {
                    items(filteredFilmography) { item ->
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
private fun BioRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(0.58f)
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
            // Media Poster
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .width(48.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )

            // Details
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Character Name (for Voice Acting roles)
                if (!item.characterName.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!item.characterImage.isNullOrBlank()) {
                            AsyncImage(
                                model = item.characterImage,
                                contentDescription = item.characterName,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, AccentBlue, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(
                            text = "Sebagai ${item.characterName}",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Role / Jabatan Badge
                if (!item.role.isNullOrBlank()) {
                    val roleLabel = when (item.role.uppercase()) {
                        "MAIN" -> "Main Role"
                        "SUPPORTING" -> "Support Role"
                        "BACKGROUND" -> "Background Role"
                        else -> item.role
                    }
                    Text(
                        text = roleLabel,
                        color = if (item.characterName.isNullOrBlank()) AccentGreen else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Format & Year
                Text(
                    text = "${item.format ?: if (item.type == MediaType.MANGA) "Manga" else "TV"}${if (item.year != null) " • ${item.year}" else ""}",
                    color = TextMuted,
                    fontSize = 10.sp
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
