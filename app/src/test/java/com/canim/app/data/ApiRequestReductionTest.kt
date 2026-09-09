package com.canim.app.data

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.metrics.AppMetrics
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.AniListMetrics
import com.canim.app.data.remote.anilist.AniListApolloClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiRequestReductionTest {

    @Before
    fun setUp() {
        AppMetrics.reset()
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @Test
    fun testMetadataKeyGenerationAndCaching() {
        val animeKey = CacheManager.mediaMetadataKey(1234, MediaType.ANIME)
        val mangaKey = CacheManager.mediaMetadataKey(5678, MediaType.MANGA)

        assertEquals("meta_ANIME_1234", animeKey)
        assertEquals("meta_MANGA_5678", mangaKey)

        val item = MediaItem(
            malId = 1234,
            title = "Test Anime",
            imageUrl = "https://example.com/anime.jpg",
            type = MediaType.ANIME
        )

        CacheManager.putMetadata(1234, MediaType.ANIME, item)
        val cached = CacheManager.getMetadata(1234, MediaType.ANIME)

        assertNotNull(cached)
        assertEquals("Test Anime", cached!!.title)
        assertEquals(1234, cached.malId)
    }

    @Test
    fun testBatchByMalIdsReusesCachedMetadataWithoutNetwork() = runBlocking {
        val item1 = MediaItem(malId = 101, title = "Anime 101", imageUrl = "https://example.com/101.jpg", type = MediaType.ANIME)
        val item2 = MediaItem(malId = 102, title = "Anime 102", imageUrl = "https://example.com/102.jpg", type = MediaType.ANIME)

        CacheManager.putMetadata(101, MediaType.ANIME, item1)
        CacheManager.putMetadata(102, MediaType.ANIME, item2)

        val initialHits = AniListMetrics.cacheHitCount
        val initialRequests = AniListMetrics.requestCount

        // Requesting only already-cached items
        val result = AniListApolloClient.getMediaBatchByMalIds(listOf(101, 102, 101), MediaType.ANIME)

        assertEquals(2, result.size)
        assertEquals("Anime 101", result[101]?.title)
        assertEquals("Anime 102", result[102]?.title)

        // Verifies ZERO network requests made
        assertEquals(initialRequests, AniListMetrics.requestCount)
        // Verifies cache hits recorded
        assertTrue(AniListMetrics.cacheHitCount > initialHits)
    }

    @Test
    fun testBatchByMalIdsDeduplicatesInput() = runBlocking {
        val item = MediaItem(malId = 201, title = "Anime 201", imageUrl = "https://example.com/201.jpg", type = MediaType.ANIME)
        CacheManager.putMetadata(201, MediaType.ANIME, item)

        val initialHits = AniListMetrics.cacheHitCount

        // 5 duplicate IDs
        val result = AniListApolloClient.getMediaBatchByMalIds(listOf(201, 201, 201, 201, 201), MediaType.ANIME)

        assertEquals(1, result.size)
        // Because IDs were deduplicated into a set of 1, it only checks cache once for this ID
        assertEquals(initialHits + 1, AniListMetrics.cacheHitCount)
    }

    @Test
    fun testNegativeCacheSkipsKnownNotFoundIds() = runBlocking {
        val negKey = "resolve_mal_999999_ANIME"
        CacheManager.putNegativeCache(negKey)

        assertTrue(CacheManager.isNegativeCached(negKey))

        val initialRequests = AniListMetrics.requestCount
        val result = AniListApolloClient.getMediaBatchByMalIds(listOf(999999), MediaType.ANIME)

        // Should return empty map and zero network requests
        assertTrue(result.isEmpty())
        assertEquals(initialRequests, AniListMetrics.requestCount)
    }

    @Test
    fun testResolveIdMalNegativeCaching() = runBlocking {
        val negKey = "resolve_mal_888888_ANIME"
        CacheManager.putNegativeCache(negKey)

        val initialRequests = AniListMetrics.requestCount
        val res = AniListApolloClient.resolveIdMal(888888, MediaType.ANIME)

        assertTrue(res is com.canim.app.data.remote.AniListResult.NotFound)
        assertEquals(initialRequests, AniListMetrics.requestCount)
    }

    @Test
    fun testResolveAniListIdNegativeCaching() = runBlocking {
        val negKey = "resolve_ani_777777"
        CacheManager.putNegativeCache(negKey)

        val initialRequests = AniListMetrics.requestCount
        val res = AniListApolloClient.resolveAniListId(777777)

        assertTrue(res is com.canim.app.data.remote.AniListResult.NotFound)
        assertEquals(initialRequests, AniListMetrics.requestCount)
    }
}
