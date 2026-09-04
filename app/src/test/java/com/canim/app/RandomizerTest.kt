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
}
