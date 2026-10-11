package com.canim.app.data.model

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek

/**
 * Domain model for an airing anime displayed on the weekly Airing Calendar.
 */
@Immutable
data class AiringAnimeItem(
    val id: String,
    val malId: Int?,
    val anilistId: Int?,
    val title: String,
    val titleEnglish: String?,
    val imageUrl: String,
    val score: Double?,
    val episodes: Int?,
    val currentAiringEpisode: Int?,
    val airingDay: DayOfWeek,
    val airingTimeFormatted: String?,
    val genres: List<String> = emptyList(),
    val studio: String? = null,
    val countryOfOrigin: String? = "JP"
) {
    val scoreFormatted: String get() = if (score != null && score > 0) "%.1f".format(score) else ""
    val isDonghua: Boolean get() = countryOfOrigin.equals("CN", ignoreCase = true)
    val isAeni: Boolean get() = countryOfOrigin.equals("KR", ignoreCase = true)
    val isJapaneseAnime: Boolean get() = countryOfOrigin.isNullOrBlank() || countryOfOrigin.equals("JP", ignoreCase = true)
    val originBadge: String get() = when {
        isDonghua -> "🇨🇳 Donghua"
        isAeni -> "🇰🇷 Aeni"
        else -> "🇯🇵 Anime"
    }
}

/**
 * Represents the 7-day weekly schedule state.
 */
@Immutable
data class AiringCalendarWeek(
 val days: Map<DayOfWeek, List<AiringAnimeItem>> = emptyMap()
) {
 fun getItemsForDay(day: DayOfWeek): List<AiringAnimeItem> = days[day] ?: emptyList()
}
