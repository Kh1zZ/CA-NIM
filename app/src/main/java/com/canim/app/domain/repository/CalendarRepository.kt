package com.canim.app.domain.repository

import com.canim.app.data.model.AiringAnimeItem
import java.time.DayOfWeek

interface CalendarRepository {
    /**
     * Fetches current-season airing anime grouped by local day of the week (Monday through Sunday).
     * AniList is primary, MAL is fallback.
     */
    suspend fun getAiringCalendar(forceRefresh: Boolean = false): Map<DayOfWeek, List<AiringAnimeItem>>

    /**
     * Gets airing anime that are currently in the user's library with status "watching".
     */
    suspend fun getWatchingAiringAnime(
        watchingMalIds: Set<Int>,
        forceRefresh: Boolean = false
    ): List<AiringAnimeItem>
}
