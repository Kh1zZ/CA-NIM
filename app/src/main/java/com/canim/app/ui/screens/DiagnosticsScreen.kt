package com.canim.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canim.app.data.metrics.AppMetrics
import com.canim.app.data.metrics.MetricsSnapshot
import com.canim.app.ui.theme.*

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var snapshot by remember { mutableStateOf(AppMetrics.getSnapshot()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Header with Back Button and Actions
            item(key = "diag_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CardElevated)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Observabilitas & Diagnostik",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Monitoring API, Rate Limiter & Cache",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Controls Bar (Segarkan & Reset)
            item(key = "diag_controls") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { snapshot = AppMetrics.getSnapshot() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Segarkan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            AppMetrics.reset()
                            snapshot = AppMetrics.getSnapshot()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Metrik", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Section 1: Overview Summary Cards
            item(key = "diag_overview") {
                DiagnosticsCard(title = "Ringkasan Permintaan API") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiagKpiBox("Total", "${snapshot.totalRequests}", AccentBlue, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        DiagKpiBox("AniList", "${snapshot.aniListRequests}", Color(0xFF02A9FF), modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        DiagKpiBox("MAL", "${snapshot.malRequests}", Color(0xFF2E51A2), modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        DiagKpiBox("GitHub", "${snapshot.gitHubRequests}", TextSecondary, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Section 2: Health & Reliability
            item(key = "diag_health") {
                DiagnosticsCard(title = "Kesehatan & Keandalan") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val errColor = if (snapshot.totalErrors > 0) Color(0xFFEF4444) else Color(0xFF10B981)
                        val rlColor = if (snapshot.totalRateLimits > 0) Color(0xFFF59E0B) else Color(0xFF10B981)
                        val toColor = if (snapshot.totalTimeouts > 0) Color(0xFFEF4444) else Color(0xFF10B981)
                        val retColor = if (snapshot.totalRetries > 0) Color(0xFFF59E0B) else Color(0xFF10B981)

                        DiagKpiBox("Errors (5xx)", "${snapshot.totalErrors}", errColor, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        DiagKpiBox("429 Limit", "${snapshot.totalRateLimits}", rlColor, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        DiagKpiBox("Timeouts", "${snapshot.totalTimeouts}", toColor, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        DiagKpiBox("Retries", "${snapshot.totalRetries}", retColor, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Section 3: Adaptive Rate Limiter & Throttling
            item(key = "diag_limiter") {
                DiagnosticsCard(title = "Adaptive Rate Limiter & Throttling") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DiagKpiBox("Throttled", "${snapshot.totalThrottled}", Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            DiagKpiBox("Cooldown Events", "${snapshot.totalCooldownEvents}", Color(0xFF8B5CF6), modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            DiagKpiBox("Cooldown (ms)", "${snapshot.totalCooldownDurationMs}", Color(0xFFEC4899), modifier = Modifier.weight(1f))
                        }
                        if (snapshot.throttledRequestsByHost.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = CardBorderSubtle)
                            Text("Permintaan Tertunda (Throttled) per Host:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                            snapshot.throttledRequestsByHost.forEach { (host, count) ->
                                DiagRow(host, "$count req")
                            }
                        }
                        if (snapshot.cooldownEventsByHost.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = CardBorderSubtle)
                            Text("Cooldown Events & Durasi per Host:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                            snapshot.cooldownEventsByHost.forEach { (host, events) ->
                                val durationMs = snapshot.cooldownDurationMsByHost[host] ?: 0L
                                DiagRow(host, "$events kali ($durationMs ms)")
                            }
                        }
                    }
                }
            }

            // Section 4: Cache & Deduplication
            item(key = "diag_cache") {
                DiagnosticsCard(title = "Kinerja Cache & Deduplikasi") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val totalCache = snapshot.cacheHits + snapshot.cacheMisses
                            val hitPct = if (totalCache > 0) String.format("%.1f%%", snapshot.cacheHitRate * 100) else "0.0%"
                            DiagKpiBox("Hit Rate", hitPct, Color(0xFF10B981), modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            DiagKpiBox("Hits", "${snapshot.cacheHits}", Color(0xFF10B981), modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            DiagKpiBox("Misses", "${snapshot.cacheMisses}", TextSecondary, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            DiagKpiBox("Deduplikasi", "${snapshot.deduplicationCount}", AccentBlue, modifier = Modifier.weight(1f))
                        }

                        if (snapshot.cacheHitsByType.isNotEmpty() || snapshot.cacheMissesByType.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = CardBorderSubtle)
                            Text("Rincian Cache per Kategori:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                            val allTypes = (snapshot.cacheHitsByType.keys + snapshot.cacheMissesByType.keys).sorted()
                            allTypes.forEach { type ->
                                val hits = snapshot.cacheHitsByType[type] ?: 0L
                                val misses = snapshot.cacheMissesByType[type] ?: 0L
                                val total = hits + misses
                                val rate = if (total > 0) String.format("%.0f%%", (hits.toDouble() / total) * 100) else "0%"
                                DiagRow(type, "$hits hits / $misses misses ($rate)")
                            }
                        }

                        if (snapshot.deduplicationsByHost.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = CardBorderSubtle)
                            Text("Deduplikasi In-Flight per Host:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                            snapshot.deduplicationsByHost.forEach { (host, count) ->
                                DiagRow(host, "$count diselamatkan")
                            }
                        }
                    }
                }
            }

            // Section 5: Latency Breakdown
            item(key = "diag_latency") {
                DiagnosticsCard(title = "Latensi Jaringan (ms)") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (snapshot.latencyByHost.isEmpty()) {
                            Text("Belum ada data latensi.", fontSize = 12.sp, color = TextSecondary)
                        } else {
                            snapshot.latencyByHost.forEach { (host, stats) ->
                                val minStr = if (stats.minMs == Long.MAX_VALUE) "-" else "${stats.minMs}ms"
                                val avgStr = String.format("%.0fms", stats.avgMs)
                                val maxStr = "${stats.maxMs}ms"
                                DiagRow(host, "Min: $minStr | Avg: $avgStr | Max: $maxStr (${stats.count} req)")
                            }
                        }
                    }
                }
            }

            // Section 6: Requests by Host & Operation
            item(key = "diag_requests_detail") {
                DiagnosticsCard(title = "Rincian Permintaan (Host & Operasi)") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (snapshot.requestsByHost.isNotEmpty()) {
                            Text("Per Host:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                            snapshot.requestsByHost.forEach { (host, count) ->
                                DiagRow(host, "$count req")
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = CardBorderSubtle)
                        }
                        if (snapshot.requestsByOperation.isNotEmpty()) {
                            Text("Per Operasi GraphQL / REST:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                            snapshot.requestsByOperation.forEach { (op, count) ->
                                DiagRow(op, "$count req")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CardElevated,
        border = BorderStroke(1.dp, CardBorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            content()
        }
    }
}

@Composable
private fun DiagKpiBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DiagRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = AccentBlue,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
