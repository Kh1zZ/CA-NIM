package com.canim.app

import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.StudioFilmographySort
import com.canim.app.data.model.groupAndSortFilmography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioFilmographyGroupingTest {

    private val sampleItems = listOf(
        MediaItem(malId = 1, anilistId = 101, title = "TBA Anime 1", imageUrl = "", type = MediaType.ANIME, year = null, score = 8.0, popularity = 1000),
        MediaItem(malId = 2, anilistId = 102, title = "TBA Anime 2", imageUrl = "", type = MediaType.ANIME, year = 0, score = 7.5, popularity = 500),
        MediaItem(malId = 3, anilistId = 103, title = "Anime 2024 A", imageUrl = "", type = MediaType.ANIME, year = 2024, score = 8.5, popularity = 8000),
        MediaItem(malId = 4, anilistId = 104, title = "Anime 2024 B", imageUrl = "", type = MediaType.ANIME, year = 2024, score = 9.0, popularity = 6000),
        MediaItem(malId = 5, anilistId = 105, title = "Anime 2022", imageUrl = "", type = MediaType.ANIME, year = 2022, score = 7.0, popularity = 3000),
        MediaItem(malId = 6, anilistId = 106, title = "Anime 2026", imageUrl = "", type = MediaType.ANIME, year = 2026, score = 8.8, popularity = 12000)
    )

    @Test
    fun testGroupingYearDescPlacesTbaAtTopFollowedByDescendingYears() {
        val groups = groupAndSortFilmography(sampleItems, StudioFilmographySort.YEAR_DESC)

        // First group must be TBA
        assertEquals("Akan Datang / TBA", groups[0].header)
        assertEquals(2, groups[0].items.size)

        // Subsequent groups must be in descending order: 2026, 2024, 2022
        assertEquals("2026", groups[1].header)
        assertEquals(2026, groups[1].year)

        assertEquals("2024", groups[2].header)
        assertEquals(2024, groups[2].year)
        // Inside 2024, items sorted by score descending: 9.0 then 8.5
        assertEquals(9.0, groups[2].items[0].score ?: 0.0, 0.01)
        assertEquals(8.5, groups[2].items[1].score ?: 0.0, 0.01)

        assertEquals("2022", groups[3].header)
        assertEquals(2022, groups[3].year)
    }

    @Test
    fun testGroupingYearAscPlacesTbaAtTopFollowedByAscendingYears() {
        val groups = groupAndSortFilmography(sampleItems, StudioFilmographySort.YEAR_ASC)

        // First group is TBA
        assertEquals("Akan Datang / TBA", groups[0].header)

        // Subsequent groups ascending: 2022, 2024, 2026
        assertEquals("2022", groups[1].header)
        assertEquals("2024", groups[2].header)
        assertEquals("2026", groups[3].header)
    }

    @Test
    fun testSortingByPopularityDesc() {
        val groups = groupAndSortFilmography(sampleItems, StudioFilmographySort.POPULARITY_DESC)

        assertEquals(1, groups.size)
        val items = groups[0].items
        assertEquals(6, items.size)
        // Top popularity is Anime 2026 with 12000
        assertEquals(12000, items[0].popularity)
        assertEquals(8000, items[1].popularity)
        assertEquals(6000, items[2].popularity)
    }

    @Test
    fun testSortingByScoreDesc() {
        val groups = groupAndSortFilmography(sampleItems, StudioFilmographySort.SCORE_DESC)

        assertEquals(1, groups.size)
        val items = groups[0].items
        assertEquals(6, items.size)
        // Top score is Anime 2024 B with 9.0
        assertEquals(9.0, items[0].score ?: 0.0, 0.01)
        assertEquals(8.8, items[1].score ?: 0.0, 0.01)
        assertEquals(8.5, items[2].score ?: 0.0, 0.01)
    }

    @Test
    fun testEmptyItemsReturnsEmptyList() {
        val groups = groupAndSortFilmography(emptyList(), StudioFilmographySort.YEAR_DESC)
        assertTrue(groups.isEmpty())
    }
}
