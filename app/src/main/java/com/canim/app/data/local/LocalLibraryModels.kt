package com.canim.app.data.local

import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.MediaMetadata
import com.canim.app.data.model.MediaRef
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.UserMediaItem

/**
 * Represents a single library entry stored locally in the SQLite database.
 *
 * MAL is the authoritative source for all tracking fields.
 * Metadata fields are denormalized (copied from MAL fetch) to allow offline reads
 * without requiring a separate join.
 *
 * [localUpdatedAt] is set by the local device clock when a mutation is applied locally.
 * [syncedAt] is set when the entry has been successfully confirmed written to MAL.
 * An entry with [syncedAt] == 0 has never been synced.
 */
data class LibraryEntry(
    // Primary key
    val malId: Int,
    val mediaType: String, // "ANIME" | "MANGA"

    // MAL-authoritative tracking fields
    val status: String = "watching",
    val score: Int = 0,
    val progress: Int = 0,
    val progressVolumes: Int = 0,
    val isRepeating: Int = 0,         // 0 = false, 1 = true (SQLite has no BOOLEAN)
    val numTimesRewatched: Int = 0,
    val rewatchValue: Int = 0,
    val priority: Int = 0,
    val tagsJson: String = "[]",      // JSON-serialized List<String>
    val comments: String? = null,
    val startDate: String? = null,
    val finishDate: String? = null,

    // Denormalized metadata (read-only from MAL fetch, not mutated by tracking ops)
    val title: String = "",
    val titleEnglish: String? = null,
    val imageUrl: String = "",
    val totalEpisodes: Int = 0,
    val totalChapters: Int = 0,
    val totalVolumes: Int = 0,
    val airingStatus: String? = null,
    val year: Int = 0,
    val season: String? = null,
    val genresJson: String = "[]",    // JSON-serialized List<String>
    val format: String? = null,
    val studio: String? = null,
    val anilistId: Int? = null,

    // Sync bookkeeping
    val localUpdatedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long = 0L           // 0 = never synced
) {
    /**
     * Converts this [LibraryEntry] to a [UserMediaItem] domain model.
     * Parses JSON fields for genres and tags.
     */
    fun toUserMediaItem(parseJsonList: (String) -> List<String>): UserMediaItem {
        val type = if (mediaType == "ANIME") MediaType.ANIME else MediaType.MANGA
        return UserMediaItem(
            identity = MediaRef(anilistId = anilistId, malId = malId),
            metadata = MediaMetadata(
                title = title,
                titleEnglish = titleEnglish,
                imageUrl = imageUrl,
                type = type,
                totalEpisodes = if (type == MediaType.ANIME) totalEpisodes else null,
                totalChapters = if (type == MediaType.MANGA) totalChapters else null,
                totalVolumes = if (type == MediaType.MANGA) totalVolumes else null,
                status = airingStatus,
                year = if (year > 0) year else null,
                season = season,
                genres = parseJsonList(genresJson),
                format = format,
                studio = studio
            ),
            tracking = MalTracking(
                status = status,
                score = score,
                progress = progress,
                progressVolumes = progressVolumes,
                isRepeating = isRepeating == 1,
                numTimesRewatched = numTimesRewatched,
                rewatchValue = rewatchValue,
                priority = priority,
                tags = parseJsonList(tagsJson),
                comments = comments,
                startDate = startDate,
                finishDate = finishDate,
                updatedAt = localUpdatedAt
            )
        )
    }
}

/**
 * Represents a pending mutation that has not yet been confirmed sent to MAL.
 *
 * The queue is drained by [LibrarySyncEngine] when network is available.
 * Coalescing rules (enforced by [PendingMutationDao]):
 * - Two UPDATE mutations for the same malId: only the newer one survives.
 * - DELETE + UPDATE for the same malId: DELETE always wins (UPDATE is discarded).
 * - UPDATE + DELETE for the same malId: DELETE replaces the UPDATE.
 *
 * [attempts] is incremented each time a sync attempt is made for this mutation.
 * After [attempts] >= maxAttempts (3), status becomes [STATUS_FAILED_PERMANENTLY].
 * FAILED_PERMANENTLY mutations are not drained automatically; only force-refresh resets them.
 *
 * Status lifecycle:
 *   PENDING → IN_FLIGHT → SUCCEEDED
 *                       → PENDING (if transient failure, attempts < max)
 *                       → FAILED_PERMANENTLY (if attempts >= max)
 */
data class PendingMutation(
    val id: Long = 0L,                  // AUTO_INCREMENT, 0 = not yet inserted
    val malId: Int,
    val mediaType: String,              // "ANIME" | "MANGA"
    val mutationType: String,           // "UPDATE" | "DELETE"
    val payloadJson: String = "",       // JSON MalTracking for UPDATE; empty for DELETE
    val localUpdatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_FLIGHT = "IN_FLIGHT"
        const val STATUS_SUCCEEDED = "SUCCEEDED"
        const val STATUS_FAILED_PERMANENTLY = "FAILED_PERMANENTLY"

        const val TYPE_UPDATE = "UPDATE"
        const val TYPE_DELETE = "DELETE"
    }
}
