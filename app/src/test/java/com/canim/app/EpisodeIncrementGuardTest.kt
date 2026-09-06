package com.canim.app

import com.canim.app.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeIncrementGuardTest {

    private fun createAnime(status: String, progress: Int, totalEpisodes: Int): UserMediaItem {
        return UserMediaItem(
            identity = MediaRef(anilistId = 1, malId = 1),
            metadata = MediaMetadata(
                title = "Test Anime",
                imageUrl = "https://example.com/cover.jpg",
                totalEpisodes = totalEpisodes,
                type = MediaType.ANIME
            ),
            tracking = MalTracking(
                status = status,
                progress = progress,
                score = 9
            )
        )
    }

    private fun canIncrement(item: UserMediaItem): Boolean {
        val isCompleted = item.tracking.status.equals("completed", ignoreCase = true)
        val totalEp = item.metadata.totalEpisodes ?: 0
        val isMaxProgress = totalEp > 0 && item.tracking.progress >= totalEp
        return !isCompleted && !isMaxProgress
    }

    @Test
    fun testIncrementAllowedWhenWatching() {
        val anime = createAnime(status = "watching", progress = 5, totalEpisodes = 12)
        assertTrue("Watching with progress < total should be incrementable", canIncrement(anime))
    }

    @Test
    fun testIncrementBlockedWhenCompleted() {
        val anime = createAnime(status = "completed", progress = 12, totalEpisodes = 12)
        assertFalse("Completed anime must not be incrementable", canIncrement(anime))
    }

    @Test
    fun testIncrementBlockedWhenProgressReachesTotal() {
        val anime = createAnime(status = "watching", progress = 12, totalEpisodes = 12)
        assertFalse("Anime at total episodes must not be incrementable", canIncrement(anime))
    }

    @Test
    fun testIncrementAllowedForOngoingWithoutTotal() {
        val anime = createAnime(status = "watching", progress = 1050, totalEpisodes = 0)
        assertTrue("Ongoing anime with unknown total episodes should be incrementable", canIncrement(anime))
    }
}
