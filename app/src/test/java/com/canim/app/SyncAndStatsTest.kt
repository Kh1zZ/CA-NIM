package com.canim.app

import com.canim.app.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SyncAndStatsTest {

    @Test
    fun testSyncStatusEnum() {
        assertEquals(4, SyncStatus.entries.size)
        assertEquals(SyncStatus.IDLE, SyncStatus.valueOf("IDLE"))
        assertEquals(SyncStatus.SYNCING, SyncStatus.valueOf("SYNCING"))
        assertEquals(SyncStatus.SUCCESS, SyncStatus.valueOf("SUCCESS"))
        assertEquals(SyncStatus.FAILED, SyncStatus.valueOf("FAILED"))
    }

    @Test
    fun testTrackerStatsBreakdown() {
        val stats = TrackerStats(
            totalAnime = 10,
            totalManga = 5,
            episodesWatched = 240,
            chaptersRead = 150,
            volumesRead = 12,
            completedCount = 8,
            meanScore = 8.5,
            daysWatched = 4.2,
            animeWatching = 3,
            animeCompleted = 5,
            animeOnHold = 1,
            animeDropped = 0,
            animePlanToWatch = 1,
            mangaReading = 2,
            mangaCompleted = 2,
            mangaOnHold = 0,
            mangaDropped = 0,
            mangaPlanToRead = 1
        )

        assertEquals(10, stats.totalAnime)
        assertEquals(5, stats.totalManga)
        assertEquals(3, stats.animeWatching)
        assertEquals(5, stats.animeCompleted)
        assertEquals(2, stats.mangaReading)
        assertEquals(2, stats.mangaCompleted)
    }

    @Test
    fun testDiscoverFilterFormats() {
        val filterOna = DiscoverFilter(format = "ONA")
        val filterOva = DiscoverFilter(format = "OVA")
        val filterSpecial = DiscoverFilter(format = "SPECIAL")

        assertEquals("ONA", filterOna.format)
        assertEquals("OVA", filterOva.format)
        assertEquals("SPECIAL", filterSpecial.format)
    }

    @Test
    fun testExtendedMediaDetailStructure() {
        val detail = ExtendedMediaDetail(
            title = "Attack on Titan",
            nativeTitle = "進撃の巨人",
            durationMinutes = 24,
            averageScore = 85.0,
            popularity = 500000,
            rank = 1,
            watchers = 350000,
            cast = listOf(
                CharacterCastItem(characterId = 1, characterName = "Eren Yeager", role = "MAIN")
            ),
            crew = listOf(
                StaffMemberItem(staffId = 10, name = "Tetsuro Araki", role = "Director")
            ),
            recommendations = listOf(
                MediaItem(malId = 16498, title = "Frieren", imageUrl = "", type = MediaType.ANIME)
            )
        )

        assertEquals("Attack on Titan", detail.title)
        assertEquals("進撃の巨人", detail.nativeTitle)
        assertEquals(1, detail.cast.size)
        assertEquals(1, detail.crew.size)
        assertEquals(1, detail.recommendations.size)
        assertEquals(85.0, detail.averageScore ?: 0.0, 0.01)
    }
}
