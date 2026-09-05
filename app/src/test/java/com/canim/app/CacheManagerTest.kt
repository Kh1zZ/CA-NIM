package com.canim.app

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CacheManagerTest {

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.invalidateTracking()
    }

    @Test
    fun testCanonicalKeyGeneration() {
        assertEquals("detail_ani_100_mal_200", CacheManager.detailKey(100, 200))
        assertEquals("detail_ani_100", CacheManager.detailKey(100, null))
        assertEquals("detail_mal_200", CacheManager.detailKey(null, 200))
        assertEquals("detail_unknown", CacheManager.detailKey(null, null))

        assertEquals("search_ANIME_frieren", CacheManager.searchKey("  Frieren  ", "anime"))
        assertEquals("discover_trending", CacheManager.discoverKey("trending"))
        assertEquals("mal_fallback_52991_ANIME", CacheManager.malFallbackKey(52991, "anime"))
    }

    @Test
    fun testSearchCachePutAndGet() {
        val dummyItems = listOf(
            MediaItem(anilistId = 1, malId = 10, title = "Test Anime", imageUrl = "https://example.com/img.jpg", type = MediaType.ANIME)
        )

        CacheManager.putSearch("frieren", "anime", dummyItems)
        val cached = CacheManager.getSearch("frieren", "anime")
        assertNotNull(cached)
        assertEquals(1, cached?.size)
        assertEquals("Test Anime", cached?.first()?.title)

        // Case-insensitive & trimmed search
        val cachedVariant = CacheManager.getSearch("  FRIEREN  ", "anime")
        assertNotNull(cachedVariant)
    }

    @Test
    fun testIdMappingCache() {
        CacheManager.putIdMapping(malId = 52991, aniListId = 16498)

        assertEquals(16498, CacheManager.getAniListIdForMalId(52991))
        assertEquals(52991, CacheManager.getMalIdForAniListId(16498))
    }

    @Test
    fun testInvalidateMedia() {
        val key = CacheManager.detailKey(16498, 52991)
        CacheManager.invalidateMedia(anilistId = 16498, malId = 52991)

        // Verifying detail for key is null
        val detail = CacheManager.getDetail(key)
        assertNull(detail)
    }

    @Test
    fun testCastCrewProfileCache() {
        val profile = com.canim.app.data.model.CastCrewProfile(
            id = 101,
            isStaff = false,
            name = "Frieren",
            nativeName = "フリーレン",
            biography = "Mage of the Hero Party",
            filmography = listOf(
                com.canim.app.data.model.FilmographyItem(
                    id = 154587,
                    title = "Sousou no Frieren",
                    type = MediaType.ANIME,
                    role = "Main"
                )
            )
        )

        CacheManager.putCastCrewProfile(101, isStaff = false, profile)
        val cached = CacheManager.getCastCrewProfile(101, isStaff = false)
        assertNotNull(cached)
        assertEquals("Frieren", cached?.name)
        assertEquals(1, cached?.filmography?.size)
        assertEquals("Sousou no Frieren", cached?.filmography?.first()?.title)
    }

    @Test
    fun testStudioFilmographyCache() {
        val page = com.canim.app.data.cache.StudioFilmographyPage(
            studioId = 569,
            studioName = "MAPPA",
            items = listOf(
                MediaItem(
                    anilistId = 113415,
                    malId = 40748,
                    title = "Jujutsu Kaisen",
                    imageUrl = "https://example.com/jjk.jpg",
                    type = MediaType.ANIME
                )
            ),
            hasNextPage = true,
            currentPage = 1,
            total = 500
        )

        CacheManager.putStudioFilmography(569, 1, page)
        val cached = CacheManager.getStudioFilmography(569, 1)
        assertNotNull(cached)
        assertEquals("MAPPA", cached?.studioName)
        assertEquals(1, cached?.items?.size)
        assertEquals("Jujutsu Kaisen", cached?.items?.first()?.title)
        assertTrue(cached?.hasNextPage == true)
        assertEquals(500, cached?.total)
    }

    @Test
    fun testCompletedStatusAutoFillProgress() {
        val animeItem = com.canim.app.data.model.UserMediaItem(
            identity = com.canim.app.data.model.MediaRef(malId = 100),
            metadata = com.canim.app.data.model.MediaMetadata(
                title = "Sousou no Frieren",
                imageUrl = "https://example.com/frieren.jpg",
                type = MediaType.ANIME,
                totalEpisodes = 28
            ),
            tracking = com.canim.app.data.model.MalTracking(
                status = "watching",
                progress = 12
            )
        )

        val completedAnime = animeItem.withStatus("completed")
        assertEquals("completed", completedAnime.status)
        assertEquals(28, completedAnime.progress)

        val mangaItem = com.canim.app.data.model.UserMediaItem(
            identity = com.canim.app.data.model.MediaRef(malId = 200),
            metadata = com.canim.app.data.model.MediaMetadata(
                title = "Berserk",
                imageUrl = "https://example.com/berserk.jpg",
                type = MediaType.MANGA,
                totalChapters = 364
            ),
            tracking = com.canim.app.data.model.MalTracking(
                status = "reading",
                progress = 50
            )
        )

        val completedManga = mangaItem.withStatus("completed")
        assertEquals("completed", completedManga.status)
        assertEquals(364, completedManga.progress)
    }
}
