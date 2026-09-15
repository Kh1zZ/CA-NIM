package com.canim.app.data.repository

import com.canim.app.data.model.MalAnimeNode
import com.canim.app.data.model.MalMangaNode
import com.canim.app.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMappingUtilsTest {

    @Test
    fun testMapMalAnimeNodeToMediaItemMovieFormat() {
        val node = MalAnimeNode(
            id = 32281,
            title = "Kimi no Na wa.",
            mediaType = "movie",
            numEpisodes = 1,
            mean = 8.84
        )

        val item = MediaMappingUtils.mapMalAnimeNodeToMediaItem(node)
        assertEquals(32281, item.malId)
        assertEquals("Kimi no Na wa.", item.title)
        assertEquals(MediaType.ANIME, item.type)
        assertEquals("MOVIE", item.format)
        assertEquals(1, item.episodes)
    }

    @Test
    fun testMapMalAnimeNodeToMediaItemTvFormat() {
        val node = MalAnimeNode(
            id = 52991,
            title = "Sousou no Frieren",
            mediaType = "tv",
            numEpisodes = 28,
            mean = 9.35
        )

        val item = MediaMappingUtils.mapMalAnimeNodeToMediaItem(node)
        assertEquals(52991, item.malId)
        assertEquals(MediaType.ANIME, item.type)
        assertEquals("TV", item.format)
        assertEquals(28, item.episodes)
    }

    @Test
    fun testMapMalMangaNodeToMediaItemMangaFormat() {
        val node = MalMangaNode(
            id = 12345,
            title = "Sample Manga",
            mediaType = "manga",
            numChapters = 50,
            numVolumes = 5
        )

        val item = MediaMappingUtils.mapMalMangaNodeToMediaItem(node)
        assertEquals(12345, item.malId)
        assertEquals(MediaType.MANGA, item.type)
        assertEquals("MANGA", item.format)
        assertEquals(50, item.chapters)
        assertEquals(5, item.volumes)
    }
}
