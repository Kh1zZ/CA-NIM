package com.canim.app.ui.screens

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.canim.app.data.model.CharacterCastItem
import com.canim.app.data.model.StaffMemberItem
import com.canim.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullCastListScreen(
    mediaTitle: String,
    castList: List<CharacterCastItem>,
    staffList: List<StaffMemberItem>,
    isCrewInitial: Boolean = false,
    onOpenCastCrew: (id: Int, isStaff: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(if (isCrewInitial) 1 else 0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (selectedTab == 0) "Daftar Pemeran & Karakter" else "Daftar Staf Produksi",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (mediaTitle.isNotBlank()) {
                            Text(
                                text = mediaTitle,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CardBg,
                contentColor = AccentBlue,
                divider = { HorizontalDivider(color = CardBorder) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Pemeran (${castList.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) AccentBlue else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Staf Produksi (${staffList.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) AccentBlue else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                if (castList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tidak ada data pemeran yang tersedia.", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(castList) { cast ->
                            FullCastRowItem(
                                cast = cast,
                                onCharacterClick = {
                                    val charId = cast.characterId ?: 0
                                    if (charId > 0) onOpenCastCrew(charId, false)
                                },
                                onActorClick = {
                                    val actId = cast.actorId ?: 0
                                    if (actId > 0) onOpenCastCrew(actId, true)
                                }
                            )
                            HorizontalDivider(
                                color = CardBorder,
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                if (staffList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tidak ada data staf produksi yang tersedia.", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(staffList) { staff ->
                            FullStaffRowItem(
                                staff = staff,
                                onClick = {
                                    val sId = staff.staffId ?: 0
                                    if (sId > 0) onOpenCastCrew(sId, true)
                                }
                            )
                            HorizontalDivider(
                                color = CardBorder,
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullCastRowItem(
    cast: CharacterCastItem,
    onCharacterClick: () -> Unit,
    onActorClick: () -> Unit
) {
    val roleBadge = when (cast.role?.uppercase()) {
        "MAIN" -> "Main Role"
        "SUPPORTING" -> "Support Role"
        "BACKGROUND" -> "Background Role"
        else -> cast.role ?: "Pemeran"
    }

    val hasActor = !cast.actorName.isNullOrBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Karakter (Left)
        Row(
            modifier = (if (hasActor) Modifier.weight(1f) else Modifier.fillMaxWidth())
                .clickable(onClick = onCharacterClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = cast.characterImage ?: "",
                contentDescription = cast.characterName,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, AccentBlue, CircleShape),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cast.characterName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    color = if (cast.role?.uppercase() == "MAIN") AccentBlue.copy(alpha = 0.2f) else CardElevated,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = roleBadge,
                        color = if (cast.role?.uppercase() == "MAIN") AccentBlue else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Voice Actor (Right, if available)
        if (hasActor) {
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onActorClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = cast.actorName!!,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.End,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "VA / Seiyuu",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                AsyncImage(
                    model = cast.actorImage ?: "",
                    contentDescription = cast.actorName,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, StarGold, CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun FullStaffRowItem(
    staff: StaffMemberItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = staff.image ?: "",
            contentDescription = staff.name,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(1.5.dp, MangaAccentDarkBlue, CircleShape),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = staff.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                lineHeight = 18.sp,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Surface(
                color = CardElevated,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = staff.role,
                    color = AccentGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
