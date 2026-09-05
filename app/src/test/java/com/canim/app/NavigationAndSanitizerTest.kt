package com.canim.app

import com.canim.app.data.model.FilmographyItem
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.formatCompactNumber
import com.canim.app.ui.navigation.ScreenRoute
import com.canim.app.util.TextSanitizer
import org.junit.Assert.*
import org.junit.Test

class NavigationAndSanitizerTest {

    @Test
    fun testTextSanitizerHtmlEntitiesAndTags() {
        val input = "<p>He said, &quot;It&#039;s &lt;bold&gt; &amp; beautiful!&quot;</p><br><br/>"
        val sanitized = TextSanitizer.sanitize(input)
        assertEquals("He said, \"It's <bold> & beautiful!\"", sanitized)
    }

    @Test
    fun testTextSanitizerAniListMarkdown() {
        val input = "This is __bold__ and **strong** and *italic* with ~strike~ and [Google](https://google.com) and ~!spoiler alert!~."
        val sanitized = TextSanitizer.sanitize(input)
        assertEquals("This is bold and strong and italic with strike and Google and spoiler alert.", sanitized)
    }

    @Test
    fun testFormatCompactNumber() {
        assertEquals("0", formatCompactNumber(0))
        assertEquals("450", formatCompactNumber(450))
        assertEquals("999", formatCompactNumber(999))
        assertEquals("1.0K", formatCompactNumber(1000))
        assertEquals("1.5K", formatCompactNumber(1500))
        assertEquals("25.0K", formatCompactNumber(25000))
        assertEquals("1.0M", formatCompactNumber(1000000))
        assertEquals("2.5M", formatCompactNumber(2480000))
    }

    @Test
    fun testScreenRouteBackStack() {
        val stack = mutableListOf<ScreenRoute>()
        assertTrue(stack.isEmpty())

        val dummyMedia = MediaItem(
            anilistId = 1,
            malId = 1,
            title = "Sousou no Frieren",
            imageUrl = "https://example.com/cover.jpg",
            type = MediaType.ANIME
        )

        // 1. Open Detail
        val detailRoute = ScreenRoute.Detail(dummyMedia, MediaType.ANIME)
        stack.add(detailRoute)
        assertEquals(1, stack.size)
        assertEquals(detailRoute, stack.last())

        // 2. Open Full Cast List
        val fullCastRoute = ScreenRoute.FullCastList(
            mediaTitle = dummyMedia.title,
            castList = emptyList(),
            staffList = emptyList()
        )
        stack.add(fullCastRoute)
        assertEquals(2, stack.size)
        assertEquals(fullCastRoute, stack.last())

        // 3. Open Cast / Crew profile
        val castCrewRoute = ScreenRoute.CastCrew(id = 100, isStaff = false)
        stack.add(castCrewRoute)
        assertEquals(3, stack.size)
        assertEquals(castCrewRoute, stack.last())

        // Pop step by step
        val popped1 = stack.removeAt(stack.size - 1)
        assertEquals(castCrewRoute, popped1)
        assertEquals(fullCastRoute, stack.last())

        val popped2 = stack.removeAt(stack.size - 1)
        assertEquals(fullCastRoute, popped2)
        assertEquals(detailRoute, stack.last())

        // Clear stack on bottom nav tab click
        stack.clear()
        assertTrue(stack.isEmpty())
    }

    @Test
    fun testCastAndCrewSeparationAndFilmographyMapping() {
        val vaRole = FilmographyItem(
            id = 1,
            title = "Frieren",
            imageUrl = "https://example.com/frieren.jpg",
            type = MediaType.ANIME,
            year = 2023,
            role = "Main",
            characterName = "Frieren",
            characterImage = "https://example.com/frieren_char.jpg"
        )

        val directorRole = FilmographyItem(
            id = 2,
            title = "Frieren",
            imageUrl = "https://example.com/frieren.jpg",
            type = MediaType.ANIME,
            year = 2023,
            role = "Director",
            characterName = null,
            characterImage = null
        )

        val mangakaRole = FilmographyItem(
            id = 3,
            title = "Frieren Manga",
            imageUrl = "https://example.com/manga.jpg",
            type = MediaType.MANGA,
            year = 2020,
            role = "Story & Art",
            characterName = null,
            characterImage = null
        )

        val allItems = listOf(vaRole, directorRole, mangakaRole)

        // Test Voice Acting preservation
        assertNotNull(vaRole.characterName)
        assertEquals("Frieren", vaRole.characterName)
        assertEquals("https://example.com/frieren_char.jpg", vaRole.characterImage)

        // Test Filters
        val animeOnly = allItems.filter { it.type == MediaType.ANIME }
        assertEquals(2, animeOnly.size)

        val mangaOnly = allItems.filter { it.type == MediaType.MANGA }
        assertEquals(1, mangaOnly.size)
        assertEquals("Frieren Manga", mangaOnly[0].title)

        val vaOnly = allItems.filter { it.characterName != null }
        assertEquals(1, vaOnly.size)
        assertEquals("Main", vaOnly[0].role)

        val staffOnly = allItems.filter { it.characterName == null }
        assertEquals(2, staffOnly.size)
        assertTrue(staffOnly.any { it.role == "Director" })
        assertTrue(staffOnly.any { it.role == "Story & Art" })
    }

    @Test
    fun testStudioFilmographyRoute() {
        val route = ScreenRoute.StudioFilmography(studioId = 569, studioName = "MAPPA")
        assertEquals(569, route.studioId)
        assertEquals("MAPPA", route.studioName)
    }

    @Test
    fun testMalAniListSynergyFallbackLogic() {
        // Mock AniList detail with high quality visual but missing MAL metrics
        val aniListOnly = com.canim.app.data.model.ExtendedMediaDetail(
            title = "Jujutsu Kaisen",
            coverImage = "https://anilist.co/cover_hd.jpg",
            bannerImage = "https://anilist.co/banner_hd.jpg",
            studio = "MAPPA",
            studioId = 569,
            averageScore = 8.5,
            rank = 150,
            popularity = 25,
            watchers = 350000,
            malScore = null,
            malRank = null,
            malPopularity = null,
            malMembers = null
        )

        // Effective score should fallback to averageScore if malScore is null
        val effectiveScore1 = aniListOnly.malScore ?: aniListOnly.averageScore
        assertEquals(8.5, effectiveScore1)

        val effectiveRank1 = aniListOnly.malRank ?: aniListOnly.rank
        assertEquals(150, effectiveRank1)

        // When MAL metrics are present, MAL is prioritized over AniList
        val synergized = aniListOnly.copy(
            malScore = 8.8,
            malRank = 45,
            malPopularity = 12,
            malMembers = 500000
        )

        val effectiveScore2 = synergized.malScore ?: synergized.averageScore
        assertEquals(8.8, effectiveScore2)

        val effectiveRank2 = synergized.malRank ?: synergized.rank
        assertEquals(45, effectiveRank2)

        val effectivePopularity2 = synergized.malPopularity ?: synergized.popularity
        assertEquals(12, effectivePopularity2)

        val effectiveMembers2 = synergized.malMembers ?: synergized.watchers
        assertEquals(500000, effectiveMembers2)

        // Visuals remain preserved from AniList
        assertEquals("https://anilist.co/cover_hd.jpg", synergized.coverImage)
        assertEquals("https://anilist.co/banner_hd.jpg", synergized.bannerImage)
        assertEquals(569, synergized.studioId)
    }
}
