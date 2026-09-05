package com.canim.app

import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomizerTest {

    @Test
    fun testCompletedAnimeExclusion() {
        val completedIds = setOf(52991, 21, 1535) // Frieren, One Piece, Death Note

        val incomingPool = listOf(
            MediaItem(anilistId = 1, malId = 52991, title = "Frieren", imageUrl = "", type = MediaType.ANIME),
            MediaItem(anilistId = 2, malId = 21, title = "One Piece", imageUrl = "", type = MediaType.ANIME),
            MediaItem(anilistId = 3, malId = 30000, title = "Unseen Anime", imageUrl = "", type = MediaType.ANIME),
            MediaItem(anilistId = 40000, malId = null, title = "AniList Only Anime", imageUrl = "", type = MediaType.ANIME)
        )

        val eligible = incomingPool.filter { item ->
            val mId = item.malId
            mId == null || !completedIds.contains(mId)
        }

        // Must not contain completed anime
        assertFalse(eligible.any { it.malId == 52991 })
        assertFalse(eligible.any { it.malId == 21 })

        // Must contain uncompleted anime
        assertTrue(eligible.any { it.malId == 30000 })
        assertTrue(eligible.any { it.title == "AniList Only Anime" })
    }

    @Test
    fun testCompletedMangaExclusion() {
        val completedMangaIds = setOf(1, 2) // Berserk, Monster

        val incomingPool = listOf(
            MediaItem(anilistId = 30001, malId = 1, title = "Berserk", imageUrl = "", type = MediaType.MANGA),
            MediaItem(anilistId = 30002, malId = 2, title = "Monster", imageUrl = "", type = MediaType.MANGA),
            MediaItem(anilistId = 30003, malId = 3, title = "20th Century Boys", imageUrl = "", type = MediaType.MANGA),
            MediaItem(anilistId = 40001, malId = null, title = "AniList Only Manga", imageUrl = "", type = MediaType.MANGA)
        )

        val eligible = incomingPool.filter { item ->
            val mId = item.malId
            mId == null || !completedMangaIds.contains(mId)
        }

        // Must not contain completed manga
        assertFalse(eligible.any { it.malId == 1 })
        assertFalse(eligible.any { it.malId == 2 })

        // Must contain uncompleted manga
        assertTrue(eligible.any { it.malId == 3 })
        assertTrue(eligible.any { it.title == "AniList Only Manga" })
    }
}
