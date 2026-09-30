package com.canim.app.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canim.app.ui.screens.MediaRatingCardExporter
import com.canim.app.ui.screens.MediaRatingExportData
import com.canim.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PRESET_COLORS = listOf(
    0xFF38BDF8.toInt(), // Cyber Blue
    0xFF10B981.toInt(), // Emerald Green
    0xFFA855F7.toInt(), // Neon Purple
    0xFFF59E0B.toInt(), // Amber Gold
    0xFFF43F5E.toInt(), // Crimson Rose
    0xFFEC4899.toInt()  // Hot Pink
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaRatingExportDialog(
    data: MediaRatingExportData,
    initialReview: String,
    onDismiss: () -> Unit,
    onShare: (review: String, accentColor: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Priority IME dismiss handler: close keyboard before closing sheet
    BackHandler(enabled = isImeVisible) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    var reviewText by remember { mutableStateOf(initialReview) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dominantCoverColor by remember { mutableIntStateOf(0xFF38BDF8.toInt()) }
    var selectedColorInt by remember { mutableIntStateOf(0xFF38BDF8.toInt()) }
    var isUsingDominant by remember { mutableStateOf(true) }
    var hueSliderValue by remember { mutableFloatStateOf(200f) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingPreview by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    // 1. Initial Load: Cover Bitmap & Dominant Color
    LaunchedEffect(data.imageUrl) {
        val loaded = withContext(Dispatchers.IO) {
            MediaRatingCardExporter.loadCoverBitmap(context, data.imageUrl)
        }
        coverBitmap = loaded
        if (loaded != null) {
            val dominant = MediaRatingCardExporter.extractDominantColor(loaded)
            dominantCoverColor = dominant
            selectedColorInt = dominant
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(dominant, hsv)
            hueSliderValue = hsv[0]
        }
    }

    // 2. Realtime Preview Rendering on Background Dispatcher
    LaunchedEffect(reviewText, selectedColorInt, coverBitmap) {
        isGeneratingPreview = true
        delay(60) // Small debounce for smooth typing & slider scrubbing
        val bmp = withContext(Dispatchers.Default) {
            val exportDataWithReview = data.copy(
                review = reviewText.trim(),
                customAccentColor = selectedColorInt
            )
            MediaRatingCardExporter.renderRatingCardBitmap(
                context = context,
                data = exportDataWithReview,
                coverBitmap = coverBitmap,
                dominantColor = selectedColorInt
            )
        }
        previewBitmap = bmp
        isGeneratingPreview = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .padding(bottom = 32.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ekspor Rating Card (4:5)",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = Color(selectedColorInt).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(selectedColorInt).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "1080 × 1350",
                        color = Color(selectedColorInt),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Realtime Preview Display (4:5 Aspect Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BlackBg)
                    .border(1.5.dp, Color(selectedColorInt).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val currentPreview = previewBitmap
                if (currentPreview != null) {
                    Image(
                        bitmap = currentPreview.asImageBitmap(),
                        contentDescription = "Realtime Preview Kartu Rating",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        color = Color(selectedColorInt),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Ulasan Singkat Input
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ulasan Singkat",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${reviewText.length} / 100",
                        color = if (reviewText.length > 90) StarGold else TextMuted,
                        fontSize = 11.sp
                    )
                }

                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { if (it.length <= 100) reviewText = it },
                    placeholder = {
                        Text(
                            text = "Tulis kesan atau ulasan singkat untuk kartu ini...",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(selectedColorInt),
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg
                    )
                )
            }

            // Pilihan Warna Aksen (Color Customization)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Warna Aksen Kartu",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Dominant Color Button + Quick Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dominant Color Chip
                    Surface(
                        color = if (isUsingDominant) Color(dominantCoverColor).copy(alpha = 0.22f) else CardBg,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isUsingDominant) 1.5.dp else 1.dp,
                            color = if (isUsingDominant) Color(dominantCoverColor) else CardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable {
                                isUsingDominant = true
                                selectedColorInt = dominantCoverColor
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(dominantCoverColor, hsv)
                                hueSliderValue = hsv[0]
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(dominantCoverColor))
                            )
                            Text(
                                text = "Cover Dominan",
                                color = if (isUsingDominant) Color.White else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isUsingDominant) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                            if (isUsingDominant) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(dominantCoverColor),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Preset Color Swatches
                    PRESET_COLORS.forEach { colorInt ->
                        val isSelected = !isUsingDominant && selectedColorInt == colorInt
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(colorInt))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    isUsingDominant = false
                                    selectedColorInt = colorInt
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(colorInt, hsv)
                                    hueSliderValue = hsv[0]
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Hue Spectrum Slider
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Slider Spektrum Warna (0° - 360°)",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    val rainbowGradient = remember {
                        Brush.horizontalGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Rainbow background track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(rainbowGradient)
                        )

                        Slider(
                            value = hueSliderValue,
                            onValueChange = { hue ->
                                hueSliderValue = hue
                                isUsingDominant = false
                                val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.82f, 0.95f))
                                selectedColorInt = newColor
                            },
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(selectedColorInt),
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Action Buttons (Batal + Bagikan Kartu)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardBg,
                        contentColor = TextSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Text("Batal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (isExporting) return@Button
                        isExporting = true
                        coroutineScope.launch {
                            try {
                                onShare(reviewText.trim(), selectedColorInt)
                                onDismiss()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(selectedColorInt)),
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Bagikan Kartu",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
