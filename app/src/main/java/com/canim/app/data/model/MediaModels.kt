package com.canim.app.data.model

import androidx.compose.runtime.Immutable

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

/**
 * Explicit Media identity separating AniList and MyAnimeList namespaces.
 * Never assumes anilistId == malId.
 */
@Immutable
data class MediaRef(
    val anilistId: Int? = null,
    val malId: Int? = null
) {
    val isResolved: Boolean get() = anilistId != null || malId != null
    val hasBoth: Boolean get() = anilistId != null && malId != null
}

/**
 * Authoritative user tracking fields owned strictly by MyAnimeList.
 */
@Immutable
data class MalTracking(
    val status: String = "watching", // "watching", "reading", "completed", "on_hold", "dropped", "plan_to_watch", "plan_to_read"
    val score: Int = 0, // 0-10
    val progress: Int = 0, // watched episodes or read chapters
    val progressVolumes: Int = 0, // read volumes for manga
    val isRepeating: Boolean = false, // is_rewatching or is_rereading
    val numTimesRewatched: Int = 0,
    val rewatchValue: Int = 0,
    val priority: Int = 0,
    val tags: List<String> = emptyList(),
    val comments: String? = null,
    val startDate: String? = null,
    val finishDate: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Rich metadata provided primarily by AniList, with MAL fallback.
 */
@Immutable
data class MediaMetadata(
    val title: String,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val imageUrl: String,
    val type: MediaType,
    val score: Double? = null,
    val synopsis: String? = null,
    val totalEpisodes: Int? = null,
    val totalChapters: Int? = null,
    val totalVolumes: Int? = null,
    val status: String? = null,
    val year: Int? = null,
    val season: String? = null,
    val genres: List<String> = emptyList(),
    val format: String? = null,
    val studio: String? = null
)

/**
 * Combined domain item representing an entry in the user's library.
 * Couples rich metadata with authoritative MAL tracking data.
 */
@Immutable
data class UserMediaItem(
    val identity: MediaRef,
    val metadata: MediaMetadata,
    val tracking: MalTracking
) {
    val id: String get() = identity.malId?.let { "mal_$it" } ?: identity.anilistId?.let { "ani_$it" } ?: ""
    val malId: Int? get() = identity.malId
    val anilistId: Int? get() = identity.anilistId
    val title: String get() = metadata.title
    val titleEnglish: String? get() = metadata.titleEnglish
    val imageUrl: String get() = metadata.imageUrl
    val type: MediaType get() = metadata.type
    val status: String get() = tracking.status
    val score: Int get() = tracking.score
    val scoreFormatted: String get() = if (score > 0) "★ $score" else ""
    val progress: Int get() = tracking.progress
    val progressChapters: Int get() = tracking.progress
    val progressVolumes: Int get() = tracking.progressVolumes
    val totalEpisodes: Int get() = metadata.totalEpisodes ?: 0
    val totalChapters: Int get() = metadata.totalChapters ?: 0
    val totalVolumes: Int get() = metadata.totalVolumes ?: 0
    val progressFrac: Float get() = if (totalEpisodes > 0) (progress.toFloat() / totalEpisodes).coerceIn(0f, 1f) else 0.5f
    val progressChaptersFrac: Float get() = if (totalChapters > 0) (progressChapters.toFloat() / totalChapters).coerceIn(0f, 1f) else 0.5f
    val airingStatus: String get() = metadata.status ?: "Finished Airing"
    val publishingStatus: String get() = metadata.status ?: "Finished"
    val genres: String get() = metadata.genres.joinToString(", ")
    val synopsis: String get() = metadata.synopsis ?: ""
    val year: Int? get() = metadata.year
    val season: String? get() = metadata.season
    val notes: String get() = tracking.comments ?: ""
    val rewatches: Int get() = tracking.numTimesRewatched
    val studio: String? get() = metadata.studio
    val updatedAt: Long get() = tracking.updatedAt

    fun withStatus(newStatus: String): UserMediaItem =
        copy(tracking = tracking.copy(status = newStatus, updatedAt = System.currentTimeMillis()))
}

@Immutable
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
 * Domain model for Anime & Manga catalog exploration (Search & Discover).
 */
@Immutable
data class MediaItem(
    val malId: Int? = null,
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
) {
    val identity: MediaRef get() = MediaRef(anilistId = anilistId, malId = malId)
    val genresFormatted: String get() = if (genres.isNotEmpty()) genres.take(3).joinToString(" • ") else ""
    val scoreFormatted: String get() = if (score != null && score > 0) "%.1f".format(score) else ""
}

@Immutable
data class CharacterCastItem(
    val characterName: String,
    val characterImage: String? = null,
    val actorName: String? = null,
    val actorImage: String? = null,
    val role: String? = null
)

@Immutable
data class StaffMemberItem(
    val name: String,
    val role: String,
    val image: String? = null
)

@Immutable
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

@Immutable
data class DiscoverFilter(
    val genre: String? = null,
    val format: String? = null, // "TV", "MOVIE", "MANGA"
    val year: Int? = null,
    val status: String? = null,
    val season: String? = null,
    val minScore: Int? = null
)
