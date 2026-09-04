package com.canim.app.data.model

enum class MediaType {
    ANIME, MANGA
}

enum class MediaStatus(val label: String, val apiValue: String) {
    WATCHING("Sedang Ditonton", "watching"),
    READING("Sedang Dibaca", "reading"),
    COMPLETED("Selesai", "completed"),
    ON_HOLD("Ditunda", "on_hold"),
    DROPPED("Ditinggalkan", "dropped"),
    PLAN_TO_WATCH("Rencana Tonton", "plan_to_watch"),
    PLAN_TO_READ("Rencana Baca", "plan_to_read");

    companion object {
        fun fromString(value: String): MediaStatus {
            return entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
                ?: WATCHING
        }
    }
}

data class TrackerStats(
    val totalAnime: Int = 0,
    val totalManga: Int = 0,
    val episodesWatched: Int = 0,
    val chaptersRead: Int = 0,
    val volumesRead: Int = 0,
    val completedCount: Int = 0,
    val meanScore: Double = 0.0,
    val daysWatched: Double = 0.0,
    // Detailed Anime Breakdown
    val animeWatching: Int = 0,
    val animeCompleted: Int = 0,
    val animeOnHold: Int = 0,
    val animeDropped: Int = 0,
    val animePlanToWatch: Int = 0,
    // Detailed Manga Breakdown
    val mangaReading: Int = 0,
    val mangaCompleted: Int = 0,
    val mangaOnHold: Int = 0,
    val mangaDropped: Int = 0,
    val mangaPlanToRead: Int = 0
)

/**
 * Domain model for Anime & Manga catalog items across AniList & MAL.
 */
data class MediaItem(
    val malId: Int,
    val anilistId: Int? = null,
    val title: String,
    val titleEnglish: String? = null,
    val imageUrl: String,
    val type: MediaType,
    val score: Double? = null,
    val synopsis: String? = null,
    val episodes: Int? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val status: String? = null,
    val year: Int? = null,
    val season: String? = null,
    val genres: List<String> = emptyList(),
    val format: String? = null,
    val studio: String? = null
)

// Backward compatibility alias for smooth transition
typealias JikanMediaItem = MediaItem

data class CharacterCastItem(
    val characterName: String,
    val characterImage: String? = null,
    val actorName: String? = null,
    val actorImage: String? = null,
    val role: String? = null
)

data class StaffMemberItem(
    val name: String,
    val role: String,
    val image: String? = null
)

data class ExtendedMediaDetail(
    val anilistId: Int? = null,
    val malId: Int? = null,
    val title: String = "",
    val titleEnglish: String? = null,
    val nativeTitle: String? = null,
    val studio: String? = null,
    val publisher: String? = null,
    val licensor: String? = null,
    val durationMinutes: Int? = null,
    val source: String? = null,
    val airingStatus: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val genres: List<String> = emptyList(),
    val openings: List<String> = emptyList(),
    val endings: List<String> = emptyList(),
    val cast: List<CharacterCastItem> = emptyList(),
    val crew: List<StaffMemberItem> = emptyList(),
    val relations: List<String> = emptyList(),
    val isFromFallback: Boolean = false
)

enum class DiscoverCategory(val key: String, val label: String) {
    CURRENT_SEASON("season_now", "Musim Ini"),
    NEXT_SEASON("season_next", "Musim Depan"),
    UPCOMING("upcoming", "Akan Datang"),
    TBA("tba", "TBA"),
    RANDOM_FILTER("random", "Acak (Filter)")
}

data class DiscoverFilter(
    val genre: String? = null,
    val format: String? = null, // "TV", "MOVIE", "MANGA"
    val year: Int? = null,
    val status: String? = null,
    val season: String? = null,
    val minScore: Int? = null
)
