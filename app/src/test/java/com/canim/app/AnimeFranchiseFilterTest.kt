package com.canim.app

import com.canim.app.data.model.*
import com.canim.app.util.AnimeFranchiseFilter
import org.junit.Assert.*
import org.junit.Test

class AnimeFranchiseFilterTest {

    @Test
    fun testGetFranchiseRoot() {
        assertEquals("attack on titan", AnimeFranchiseFilter.getFranchiseRoot("Attack on Titan"))
        assertEquals("attack on titan", AnimeFranchiseFilter.getFranchiseRoot("Attack on Titan Season 2"))
        assertEquals("attack on titan", AnimeFranchiseFilter.getFranchiseRoot("Attack on Titan: The Final Season"))
        assertEquals("attack on titan", AnimeFranchiseFilter.getFranchiseRoot("Attack on Titan: The Final Season Part 3"))
        assertEquals("jujutsu kaisen", AnimeFranchiseFilter.getFranchiseRoot("Jujutsu Kaisen 2nd Season"))
        assertEquals("kaguya sama wa kokurasetai", AnimeFranchiseFilter.getFranchiseRoot("Kaguya-sama wa Kokurasetai: Tensai-tachi no Renai Zunousen"))
        assertEquals("kaguya sama wa kokurasetai", AnimeFranchiseFilter.getFranchiseRoot("Kaguya-sama wa Kokurasetai: Ultra Romantic"))
        assertEquals("mushoku tensei", AnimeFranchiseFilter.getFranchiseRoot("Mushoku Tensei: Isekai Ittara Honki Dasu Part 2"))
        assertEquals("bleach", AnimeFranchiseFilter.getFranchiseRoot("Bleach: Sennen Kessen-hen"))
        assertEquals("sword art online", AnimeFranchiseFilter.getFranchiseRoot("Sword Art Online II"))
        assertEquals("sword art online", AnimeFranchiseFilter.getFranchiseRoot("Sword Art Online the Movie: Ordinal Scale"))
    }

    @Test
    fun testIsSequelOrSameFranchise() {
        assertTrue(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Attack on Titan",
            "Attack on Titan Season 2"
        ))
        assertTrue(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Attack on Titan Season 2",
            "Attack on Titan: The Final Season Part 2"
        ))
        assertTrue(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Jujutsu Kaisen",
            "Jujutsu Kaisen 2nd Season"
        ))
        assertTrue(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Kaguya-sama wa Kokurasetai: Ultra Romantic",
            "Kaguya-sama wa Kokurasetai: Tensai-tachi no Renai Zunousen"
        ))
        assertTrue(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Sword Art Online",
            "Sword Art Online II"
        ))
        assertTrue(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Vinland Saga",
            "Vinland Saga Season 2"
        ))
        assertTrue(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Bleach",
            "Bleach: Sennen Kessen-hen"
        ))

        // Different franchises must NOT be considered sequels
        assertFalse(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Attack on Titan",
            "Bleach"
        ))
        assertFalse(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Jujutsu Kaisen",
            "Naruto"
        ))
        assertFalse(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Death Note",
            "Steins;Gate"
        ))
        assertFalse(AnimeFranchiseFilter.isSequelOrSameFranchise(
            "Frieren: Beyond Journey's End",
            "Fullmetal Alchemist: Brotherhood"
        ))
    }

    @Test
    fun testSelectTopAnimeNonSequel() {
        fun makeItem(id: Int, title: String, score: Int): UserMediaItem {
            return UserMediaItem(
                identity = MediaRef(anilistId = id * 10, malId = id),
                metadata = MediaMetadata(
                    title = title,
                    imageUrl = "https://example.com/$id.jpg",
                    type = MediaType.ANIME,
                    totalEpisodes = 12
                ),
                tracking = MalTracking(
                    status = "completed",
                    score = score,
                    progress = 12
                )
            )
        }

        val items = listOf(
            makeItem(1, "Attack on Titan Season 3 Part 2", 10),
            makeItem(2, "Attack on Titan The Final Season", 10),
            makeItem(3, "Steins;Gate", 10),
            makeItem(4, "Attack on Titan", 9),
            makeItem(5, "Frieren: Beyond Journey's End", 9),
            makeItem(6, "Jujutsu Kaisen Season 2", 9),
            makeItem(7, "Hunter x Hunter (2011)", 9),
            makeItem(8, "Jujutsu Kaisen", 8),
            makeItem(9, "Monster", 8),
            makeItem(10, "Vinland Saga Season 2", 8)
        )

        val top5 = AnimeFranchiseFilter.selectTopAnimeNonSequel(items, 5)

        assertEquals(5, top5.size)
        // Item 1 is Attack on Titan Season 3 Part 2 (score 10)
        assertEquals("Attack on Titan Season 3 Part 2", top5[0].title)
        // Item 2 (AoT Final Season) and Item 4 (AoT) must be EXCLUDED because AoT franchise is already at top 1
        assertEquals("Steins;Gate", top5[1].title)
        assertEquals("Frieren: Beyond Journey's End", top5[2].title)
        assertEquals("Jujutsu Kaisen Season 2", top5[3].title)
        // Item 8 (Jujutsu Kaisen) must be EXCLUDED because JJK franchise is at top 4
        assertEquals("Hunter x Hunter (2011)", top5[4].title)

        // Ensure no sequels of top items exist in the result
        val titles = top5.map { it.title }
        assertFalse(titles.contains("Attack on Titan The Final Season"))
        assertFalse(titles.contains("Attack on Titan"))
        assertFalse(titles.contains("Jujutsu Kaisen"))
    }
}
