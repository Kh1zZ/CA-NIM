package com.canim.app.data.cache

import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit tests for [CacheManager] stale-while-revalidate (SWR) behaviour.
 *
 * Tests cover:
 * 1. Fresh entry - SWRResult.isStale = false
 * 2. Stale entry (past staleWindowMs, not expired) - SWRResult.isStale = true
 * 3. Type isolation: ANIME vs MANGA search cache isolated
 * 4. Discover type isolation
 * 5. Offline fallback: entry always returned not null
 * 6. Cache miss: null returned when nothing stored
 * 7. Detail SWR: fresh/miss behaviour
 * 8. CacheEntry.isStale and isExpired computed correctly with backdated timestamps
 * 9. CacheEntry default staleWindowMs = ttlMillis/2
 */
class CacheManagerSwrTest {

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    private fun fakeItems(vararg ids: Int): List<MediaItem> = ids.map { id ->
        MediaItem(
            malId = id,
            anilistId = null,
            title = "Title$id",
            titleEnglish = null,
            imageUrl = "",
            type = MediaType.ANIME,
            score = null,
            synopsis = "",
            episodes = null,
            chapters = null,
            volumes = null,
            status = "FINISHED",
            year = null,
            season = null,
            genres = emptyList(),
            format = "TV",
            studio = null
        )
    }

    private fun fakeDetail(anilistId: Int): ExtendedMediaDetail = ExtendedMediaDetail(
        anilistId = anilistId,
        malId = null,
        title = "Detail$anilistId",
        titleEnglish = null,
        coverImage = null,
        synopsis = null
    )

    @Test
    fun freshSearchEntryReturnsIsStalefalse() {
        val items = fakeItems(1, 2, 3)
        CacheManager.putSearch("attack", "ANIME", items)
        val result = CacheManager.getSearchSwr("attack", "ANIME")
        assertNotNull("Fresh entry must be returned", result)
        assertFalse("Fresh entry must NOT be stale", result!!.isStale)
        assertEquals(items, result.data)
    }

    @Test
    fun cacheMissReturnsNull() {
        val result = CacheManager.getSearchSwr("nonexistent_query_xyz", "ANIME")
        assertNull("Missing entry must return null", result)
    }

    @Test
    fun typeIsolationAnimeNotContaminatingManga() {
        val animeItems = fakeItems(10, 20)
        val mangaItems = fakeItems(30, 40)
        CacheManager.putSearch("naruto", "ANIME", animeItems)
        CacheManager.putSearch("naruto", "MANGA", mangaItems)
        val animeResult = CacheManager.getSearchSwr("naruto", "ANIME")
        val mangaResult = CacheManager.getSearchSwr("naruto", "MANGA")
        assertNotNull(animeResult)
        assertNotNull(mangaResult)
        assertEquals(animeItems, animeResult!!.data)
        assertEquals(mangaItems, mangaResult!!.data)
        assertNotEquals(animeResult.data, mangaResult.data)
    }

    @Test
    fun discoverTypeIsolationDifferentCategoriesIndependent() {
        val trendingItems = fakeItems(1, 2)
        val seasonalItems = fakeItems(3, 4)
        CacheManager.putDiscover("trending_p1", trendingItems)
        CacheManager.putDiscover("seasonal_p1", seasonalItems)
        val trendingResult = CacheManager.getDiscoverSwr("trending_p1")
        val seasonalResult = CacheManager.getDiscoverSwr("seasonal_p1")
        assertNotNull(trendingResult)
        assertNotNull(seasonalResult)
        assertEquals(trendingItems, trendingResult!!.data)
        assertEquals(seasonalItems, seasonalResult!!.data)
        assertNotEquals(trendingResult.data, seasonalResult.data)
    }

    @Test
    fun freshDiscoverEntryReturnsIsStalefalse() {
        val items = fakeItems(5, 6, 7)
        CacheManager.putDiscover("top_anime_p1", items)
        val result = CacheManager.getDiscoverSwr("top_anime_p1")
        assertNotNull(result)
        assertFalse("Fresh discover entry must NOT be stale", result!!.isStale)
        assertEquals(items, result.data)
    }

    @Test
    fun discoverCacheMissReturnsNull() {
        val result = CacheManager.getDiscoverSwr("nonexistent_discover_key_xyz")
        assertNull("Missing discover entry must return null", result)
    }

    @Test
    fun freshDetailEntryReturnsIsStaleFalse() {
        val detail = fakeDetail(154587)
        val key = CacheManager.detailKey(154587, 52991)
        CacheManager.putDetail(key, detail)
        val result = CacheManager.getDetailSwr(key)
        assertNotNull(result)
        assertFalse("Fresh detail entry must NOT be stale", result!!.isStale)
        assertEquals(detail, result.data)
    }

    @Test
    fun detailCacheMissReturnsNull() {
        val result = CacheManager.getDetailSwr(CacheManager.detailKey(999999, null))
        assertNull("Missing detail entry must return null", result)
    }

    @Test
    fun offlineFallbackFreshlyWrittenEntryAlwaysReturnedNotNull() {
        val items = fakeItems(100, 200)
        CacheManager.putSearch("one_piece", "ANIME", items)
        val result = CacheManager.getSearchSwr("one_piece", "ANIME")
        assertNotNull("Entry must be returned as offline fallback, never null", result)
        assertEquals(items, result!!.data)
    }

    @Test
    fun swrResultDataClassEquality() {
        val items1 = fakeItems(1)
        val items2 = fakeItems(1)
        assertEquals(SWRResult(items1, false), SWRResult(items2, false))
        assertNotEquals(SWRResult(items1, false), SWRResult(items1, true))
    }

    @Test
    fun cacheEntryIsExpiredAndIsStaleComputedCorrectlyWithBackdatedTimestamps() {
        val now = System.currentTimeMillis()
        val freshEntry = CacheEntry(data = "fresh", timestamp = now, ttlMillis = 10_000L, staleWindowMs = 5_000L)
        assertFalse(freshEntry.isExpired)
        assertFalse(freshEntry.isStale)
        val staleEntry = CacheEntry(data = "stale", timestamp = now - 6_000L, ttlMillis = 10_000L, staleWindowMs = 5_000L)
        assertFalse("Stale entry must not be expired", staleEntry.isExpired)
        assertTrue("Entry past stale window must be stale", staleEntry.isStale)
        val expiredEntry = CacheEntry(data = "expired", timestamp = now - 11_000L, ttlMillis = 10_000L, staleWindowMs = 5_000L)
        assertTrue("Entry past TTL must be expired", expiredEntry.isExpired)
        assertFalse("Expired entry must not be considered stale", expiredEntry.isStale)
    }

    @Test
    fun cacheEntryDefaultStaleWindowMsIsHalfOfTtlMillis() {
        val entry = CacheEntry(data = "test", ttlMillis = 3_600_000L)
        assertEquals("Default staleWindowMs must be ttlMillis / 2", 1_800_000L, entry.staleWindowMs)
    }
}
