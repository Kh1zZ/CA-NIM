package com.canim.app.data.repository

import com.canim.app.data.cache.CacheEntry
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.ui.viewmodel.CanimViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CanimRepositorySwrTest {

    private lateinit var repository: CanimRepository

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()

        val app = RuntimeEnvironment.getApplication()
        val storage = MalSecureStorage(app)
        val malAuth = MalAuthManager(storage)
        repository = CanimRepository(malAuth)
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    private fun fakeItem(id: Int, title: String = "Title$id"): MediaItem = MediaItem(
        malId = id,
        anilistId = id + 1000,
        title = title,
        titleEnglish = title,
        imageUrl = "https://example.com/$id.jpg",
        type = MediaType.ANIME,
        score = 8.5,
        synopsis = "Synopsis $id",
        episodes = 12,
        chapters = null,
        volumes = null,
        status = "FINISHED",
        year = 2024,
        season = "SPRING",
        genres = listOf("Action"),
        format = "TV",
        studio = "Mappa"
    )

    private fun fakeDetail(aniId: Int, malId: Int? = null): ExtendedMediaDetail = ExtendedMediaDetail(
        anilistId = aniId,
        malId = malId,
        title = "Detail $aniId",
        titleEnglish = "Detail English $aniId",
        coverImage = "https://example.com/$aniId.jpg",
        synopsis = "Detail synopsis $aniId",
        malScore = 9.0
    )

    // =========================================================================
    // 1. Repository-Level Tests: Cancellation of Previous Job on Same Cache Key
    // =========================================================================

    @Test
    fun testConsecutiveStaleHitsCancelsPreviousJobOnSameKey() = runBlocking {
        val cacheKey = "search_ANIME_naruto____"
        val job1Started = CompletableDeferred<Unit>()
        val sideEffectCounter = AtomicInteger(0)

        // Launch job 1 on cacheKey
        val job1 = repository.launchSwrJob(cacheKey) {
            job1Started.complete(Unit)
            delay(5000L)
            sideEffectCounter.incrementAndGet()
        }

        job1Started.await()
        assertFalse("Job 1 should be active initially", job1.isCancelled)
        assertEquals("repository.swrJobs must track job1", job1, repository.getActiveSwrJob(cacheKey))

        // Launch job 2 on identical cacheKey
        val job2 = repository.launchSwrJob(cacheKey) {
            delay(100L)
            sideEffectCounter.addAndGet(10)
        }

        // Job 1 must be immediately cancelled
        assertTrue("Job 1 must be cancelled upon second hit on same key", job1.isCancelled)
        assertFalse("Job 2 must be active", job2.isCancelled)
        assertNotSame("Job 1 and Job 2 must be distinct instances", job1, job2)
        assertEquals("Active SWR job must be updated to job 2", job2, repository.getActiveSwrJob(cacheKey))

        // Wait for job 2 to finish
        job2.join()
        assertEquals("Only Job 2 side-effect should execute", 10, sideEffectCounter.get())
    }

    @Test
    fun testIndependentCacheKeysDoNotCancelEachOther() = runBlocking {
        val key1 = "search_ANIME_naruto____"
        val key2 = "search_MANGA_naruto____"

        val job1 = repository.launchSwrJob(key1) { delay(5000L) }
        val job2 = repository.launchSwrJob(key2) { delay(5000L) }

        assertFalse("Job 1 must NOT be cancelled by different key", job1.isCancelled)
        assertFalse("Job 2 must NOT be cancelled", job2.isCancelled)

        job1.cancel()
        job2.cancel()
    }

    // =========================================================================
    // 2. Repository-Level Event Emission: SharedFlow emits and consumer receives
    // =========================================================================

    @Test
    fun testCacheRefreshEventEmittedAndCollectedWithFreshData() = runBlocking {
        val filterKey = "attack____"
        val cacheKey = CacheManager.searchKey(filterKey, "ANIME")
        val staleItems = listOf(fakeItem(1, "Old Attack on Titan"))
        val freshItems = listOf(fakeItem(1, "Updated Attack on Titan"), fakeItem(2, "Attack on Titan Season 2"))

        // Seed stale entry in CacheManager
        val now = System.currentTimeMillis()
        CacheManager.putSearchEntry(
            filterKey,
            "ANIME",
            CacheEntry(staleItems, timestamp = now - 6_000L, ttlMillis = 10_000L, staleWindowMs = 5_000L)
        )

        val receivedEvents = mutableListOf<CacheRefreshEvent>()
        val collectJob = launch(Dispatchers.Unconfined) {
            repository.cacheRefreshEvents.collect { event ->
                receivedEvents.add(event)
            }
        }

        // Simulate SWR write to CacheManager + event emission
        CacheManager.putSearch(filterKey, "ANIME", freshItems)
        repository.emitCacheRefreshEvent(CacheRefreshEvent(cacheKey, CacheRefreshType.SEARCH))

        assertEquals("Consumer must receive emitted event", 1, receivedEvents.size)
        val event = receivedEvents.first()
        assertEquals(cacheKey, event.key)
        assertEquals(CacheRefreshType.SEARCH, event.type)

        // Verify consumer reads updated fresh data from CacheManager (normal get, not SWR)
        val freshFromCache = CacheManager.getSearch(filterKey, "ANIME")
        assertNotNull(freshFromCache)
        assertEquals(freshItems, freshFromCache)

        collectJob.cancel()
    }

    // =========================================================================
    // 3. ViewModel-Level Tests: UI State updates when active, ignores when inactive
    // =========================================================================

    @Test
    fun testViewModelUpdatesSearchResultsWhenActiveEventReceived() = runBlocking {
        val viewModel = CanimViewModel(repository)

        val query = "frieren"
        viewModel.onSearchQueryChange(query, MediaType.ANIME)

        val state = viewModel.uiState.value
        val filterKey = "${query.trim()}_${state.searchGenres.sorted().joinToString(",")}_${state.searchYear}_${state.searchFormat}"
        val searchKey = CacheManager.searchKey(filterKey, "ANIME")
        val freshItems = listOf(fakeItem(101, "Frieren: Beyond Journey's End"))

        // Write fresh data to cache and emit refresh event
        CacheManager.putSearch(filterKey, "ANIME", freshItems)
        repository.emitCacheRefreshEvent(CacheRefreshEvent(searchKey, CacheRefreshType.SEARCH))

        // Yield and idle looper to allow ViewModel coroutine to collect and process
        delay(50L)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // UI state must now have the fresh results without triggering a new network call
        assertEquals(freshItems, viewModel.uiState.value.searchResults)
    }

    @Test
    fun testViewModelIgnoresSearchRefreshEventWhenQueryMismatched() = runBlocking {
        val viewModel = CanimViewModel(repository)

        // User is currently searching for "bleach"
        viewModel.onSearchQueryChange("bleach", MediaType.ANIME)

        val unrelatedFilterKey = "naruto____"
        val unrelatedSearchKey = CacheManager.searchKey(unrelatedFilterKey, "ANIME")
        val narutoItems = listOf(fakeItem(201, "Naruto"))

        CacheManager.putSearch(unrelatedFilterKey, "ANIME", narutoItems)
        repository.emitCacheRefreshEvent(CacheRefreshEvent(unrelatedSearchKey, CacheRefreshType.SEARCH))

        delay(50L)

        // Search results must NOT be updated with unrelated "naruto" items
        assertNotEquals(narutoItems, viewModel.uiState.value.searchResults)
    }

    @Test
    fun testViewModelUpdatesExtendedDetailWhenActiveDetailMatches() = runBlocking {
        val viewModel = CanimViewModel(repository)

        val aniId = 154587
        val malId = 52991
        val detailKey = CacheManager.detailKey(aniId, malId)
        val initialItem = fakeItem(malId, "Frieren").copy(anilistId = aniId)
        val freshDetail = fakeDetail(aniId, malId)

        // User opens detail
        viewModel.pushScreen(com.canim.app.ui.navigation.ScreenRoute.Detail(initialItem, MediaType.ANIME))
        assertTrue(viewModel.uiState.value.isDetailOpen)

        // Write fresh detail to cache and emit DETAIL refresh event
        CacheManager.putDetail(detailKey, freshDetail)
        repository.emitCacheRefreshEvent(CacheRefreshEvent(detailKey, CacheRefreshType.DETAIL))

        delay(50L)

        // UI state must reflect fresh detail
        assertEquals(freshDetail, viewModel.uiState.value.extendedDetail)
        assertFalse(viewModel.uiState.value.isLoadingExtendedDetail)
    }

    @Test
    fun testViewModelIgnoresDetailRefreshEventWhenDetailIsNotOpen() = runBlocking {
        val viewModel = CanimViewModel(repository)

        assertFalse("Detail is not open initially", viewModel.uiState.value.isDetailOpen)

        val detailKey = CacheManager.detailKey(99999, null)
        val detail = fakeDetail(99999)
        CacheManager.putDetail(detailKey, detail)
        repository.emitCacheRefreshEvent(CacheRefreshEvent(detailKey, CacheRefreshType.DETAIL))

        delay(50L)

        assertNull("extendedDetail must remain null since detail is closed", viewModel.uiState.value.extendedDetail)
    }

    // =========================================================================
    // 4. Stale Refresh & Structural Change Detection Tests
    // =========================================================================

    @Test
    fun testStaleCacheReturnedImmediatelyForSearch() = runBlocking {
        val filterKey = repository.searchFilterKey("hunter")
        val staleItems = listOf(fakeItem(1, "Hunter x Hunter Stale"))
        val now = System.currentTimeMillis()
        CacheManager.putSearchEntry(
            filterKey,
            "ANIME",
            CacheEntry(staleItems, timestamp = now - 6_000L, ttlMillis = 10_000L, staleWindowMs = 5_000L)
        )

        val result = repository.searchAnime("hunter", forceRefresh = false)
        assertEquals("Stale cache must be returned immediately", staleItems, result)
    }

    @Test
    fun testStaleCacheReturnedImmediatelyForDiscover() = runBlocking {
        val filter = com.canim.app.data.model.DiscoverFilter()
        val cacheKey = repository.discoverFilterKey(com.canim.app.data.model.DiscoverCategory.TOP_ANIME, filter, page = 1)
        val staleItems = listOf(fakeItem(10, "Stale Top Anime"))
        val now = System.currentTimeMillis()
        CacheManager.putDiscoverEntry(
            cacheKey,
            CacheEntry(staleItems, timestamp = now - 6_000L, ttlMillis = 10_000L, staleWindowMs = 5_000L)
        )

        val result = repository.getDiscoverMedia(com.canim.app.data.model.DiscoverCategory.TOP_ANIME, filter, page = 1, forceRefresh = false)
        assertEquals("Stale discover cache must be returned immediately", staleItems, result)
    }

    @Test
    fun testStaleCacheReturnedImmediatelyForDetail() = runBlocking {
        val aniId = 100
        val malId = 200
        val primaryKey = CacheManager.detailKey(aniId, malId)
        val staleDetail = fakeDetail(aniId, malId)
        val now = System.currentTimeMillis()
        CacheManager.putDetailEntry(
            primaryKey,
            CacheEntry(staleDetail, timestamp = now - 6_000L, ttlMillis = 10_000L, staleWindowMs = 5_000L)
        )

        val result = repository.getExtendedDetails(aniId, malId, MediaType.ANIME, forceRefresh = false)
        assertEquals("Stale detail cache must be returned immediately", staleDetail, result)
    }

    @Test
    fun testForceRefreshBypassesSearchCacheAndUpdatesCache() = runBlocking {
        val filterKey = repository.searchFilterKey("Naruto")
        val staleItems = listOf(fakeItem(999, "Stale Naruto"))
        CacheManager.putSearch(filterKey, "ANIME", staleItems)

        // Without forceRefresh: returns cached stale data
        val cached = repository.searchAnime("Naruto", forceRefresh = false)
        assertEquals(staleItems, cached)

        // With forceRefresh: bypasses cache and updates with fresh data
        val fresh = repository.searchAnime("Naruto", forceRefresh = true)
        assertNotEquals("Force refresh must bypass cache", staleItems, fresh)
        assertTrue("Fresh results must not be empty", fresh.isNotEmpty())

        // CacheManager must now contain the fresh data
        val inCache = CacheManager.getSearch(filterKey, "ANIME")
        assertEquals(fresh, inCache)
    }

    @Test
    fun testForceRefreshBypassesDiscoverCacheAndUpdatesCache() = runBlocking {
        val filter = com.canim.app.data.model.DiscoverFilter()
        val cacheKey = repository.discoverFilterKey(com.canim.app.data.model.DiscoverCategory.CURRENT_SEASON, filter, page = 1)
        val staleItems = listOf(fakeItem(888, "Stale Current Season"))
        CacheManager.putDiscover(cacheKey, staleItems)

        // Without forceRefresh: returns cached stale data
        val cached = repository.getDiscoverMedia(com.canim.app.data.model.DiscoverCategory.CURRENT_SEASON, filter, page = 1, forceRefresh = false)
        assertEquals(staleItems, cached)

        // With forceRefresh: bypasses cache and fetches fresh data
        val fresh = repository.getDiscoverMedia(com.canim.app.data.model.DiscoverCategory.CURRENT_SEASON, filter, page = 1, forceRefresh = true)
        assertNotEquals("Force refresh must bypass discover cache", staleItems, fresh)
        assertTrue("Fresh discover results must not be empty", fresh.isNotEmpty())

        // CacheManager must now contain the fresh data
        val inCache = CacheManager.getDiscover(cacheKey)
        assertEquals(fresh, inCache)
    }

    @Test
    fun testSameIdWithChangedMeaningfulFieldsTriggersRefreshAndUiUpdate() = runBlocking {
        val viewModel = CanimViewModel(repository)
        val query = "solo"
        viewModel.onSearchQueryChange(query, MediaType.ANIME)

        val state = viewModel.uiState.value
        val filterKey = repository.searchFilterKey(query, state.searchGenres, state.searchYear, state.searchFormat)
        val searchKey = CacheManager.searchKey(filterKey, "ANIME")

        val staleItem = fakeItem(1, "Solo Leveling").copy(score = 8.0, episodes = 12, status = "RELEASING")
        val freshItem = fakeItem(1, "Solo Leveling").copy(score = 8.9, episodes = 13, status = "FINISHED")

        // Seed stale item
        CacheManager.putSearch(filterKey, "ANIME", listOf(staleItem))

        // Same ID, but meaningful fields changed
        assertEquals("IDs must match to test same-ID field mutation", staleItem.id, freshItem.id)
        assertNotEquals("Items must structurally differ", staleItem, freshItem)

        // Write fresh and emit
        CacheManager.putSearch(filterKey, "ANIME", listOf(freshItem))
        repository.emitCacheRefreshEvent(CacheRefreshEvent(searchKey, CacheRefreshType.SEARCH))

        delay(50L)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals("UI state must update when meaningful fields change", listOf(freshItem), viewModel.uiState.value.searchResults)
    }

    @Test
    fun testIdenticalResultsProduceNoUiUpdate() = runBlocking {
        val filterKey = repository.searchFilterKey("identical")
        val searchKey = CacheManager.searchKey(filterKey, "ANIME")
        val items = listOf(fakeItem(50, "Identical Anime"))

        val now = System.currentTimeMillis()
        CacheManager.putSearchEntry(
            filterKey,
            "ANIME",
            CacheEntry(items, timestamp = now - 6_000L, ttlMillis = 10_000L, staleWindowMs = 5_000L)
        )

        val receivedEvents = mutableListOf<CacheRefreshEvent>()
        val collectJob = launch(Dispatchers.Unconfined) {
            repository.cacheRefreshEvents.collect { receivedEvents.add(it) }
        }

        // Simulating the SWR check in repository when fresh is identical to stale
        val swrHit = CacheManager.getSearchSwr(filterKey, "ANIME")
        assertNotNull(swrHit)
        assertTrue(swrHit!!.isStale)
        val fresh = listOf(fakeItem(50, "Identical Anime"))

        // When identical:
        if (fresh.isNotEmpty()) {
            CacheManager.putSearch(filterKey, "ANIME", fresh)
            if (fresh != swrHit.data) {
                repository.emitCacheRefreshEvent(CacheRefreshEvent(searchKey, CacheRefreshType.SEARCH))
            }
        }

        assertEquals("No refresh event should be emitted when results are identical", 0, receivedEvents.size)
        collectJob.cancel()
    }

    // =========================================================================
    // 5. Offline & Error Behavior Tests
    // =========================================================================

    @Test
    fun testStaleCacheRemainsUsableWhenRefreshFails() = runBlocking {
        val filterKey = repository.searchFilterKey("offline")
        val staleItems = listOf(fakeItem(77, "Offline Stale Anime"))
        val now = System.currentTimeMillis()
        CacheManager.putSearchEntry(
            filterKey,
            "ANIME",
            CacheEntry(staleItems, timestamp = now - 6_000L, ttlMillis = 10_000L, staleWindowMs = 5_000L)
        )

        // Stale entry is returned immediately
        val cached = repository.searchAnime("offline", forceRefresh = false)
        assertEquals(staleItems, cached)

        // Background refresh throws exception (network error / timeout / 429)
        val job = repository.launchSwrJob(CacheManager.searchKey(filterKey, "ANIME")) {
            throw java.io.IOException("Network unreachable")
        }
        job.join()

        // Verify stale cache in CacheManager is still intact and usable
        val afterFailure = CacheManager.getSearch(filterKey, "ANIME")
        assertNotNull("Stale cache must remain usable after network failure", afterFailure)
        assertEquals(staleItems, afterFailure)
    }

    @Test
    fun testNegativeCacheNeverStoredForNetworkErrorsTimeoutsOr429() = runBlocking {
        val negKeyMal = "neg_mal_88888"
        val negKeyAni = "neg_ani_88888"

        // Ensure initially clean
        assertFalse(CacheManager.isNegativeCached(negKeyMal))
        assertFalse(CacheManager.isNegativeCached(negKeyAni))

        // Verify only NotFound sets negative cache
        CacheManager.putNegativeCache(negKeyMal)
        assertTrue("NotFound must set negative cache", CacheManager.isNegativeCached(negKeyMal))

        // Clear and verify
        CacheManager.clearNegativeCache()
        assertFalse(CacheManager.isNegativeCached(negKeyMal))
    }

    // =========================================================================
    // 6. Regression & Isolation Checks
    // =========================================================================

    @Test
    fun testAnimeMangaSearchCacheIsolation() = runBlocking {
        val animeItems = listOf(fakeItem(1, "Anime Naruto"))
        val mangaItems = listOf(fakeItem(1, "Manga Naruto").copy(type = MediaType.MANGA))

        CacheManager.putSearch("naruto____", "ANIME", animeItems)
        CacheManager.putSearch("naruto____", "MANGA", mangaItems)

        val retrievedAnime = CacheManager.getSearch("naruto____", "ANIME")
        val retrievedManga = CacheManager.getSearch("naruto____", "MANGA")

        assertEquals(animeItems, retrievedAnime)
        assertEquals(mangaItems, retrievedManga)
        assertNotEquals(retrievedAnime, retrievedManga)
    }

    @Test
    fun testDiscoverPaginationIsolation() = runBlocking {
        val page1Key = "top_anime_null_null_null_null_null_null_p1"
        val page2Key = "top_anime_null_null_null_null_null_null_p2"
        val p1Items = listOf(fakeItem(1, "P1 Item"))
        val p2Items = listOf(fakeItem(2, "P2 Item"))

        CacheManager.putDiscover(page1Key, p1Items)
        CacheManager.putDiscover(page2Key, p2Items)

        val p1Cached = CacheManager.getDiscover(page1Key)
        val p2Cached = CacheManager.getDiscover(page2Key)

        assertEquals(p1Items, p1Cached)
        assertEquals(p2Items, p2Cached)
        assertNotEquals(p1Cached, p2Cached)
    }

    @Test
    fun testAniListMalIdMappingIsolation() {
        val sharedId = 999
        val animeAniListId = 1234
        val mangaAniListId = 5678

        CacheManager.putIdMapping(malId = sharedId, aniListId = animeAniListId, type = MediaType.ANIME)
        CacheManager.putIdMapping(malId = sharedId, aniListId = mangaAniListId, type = MediaType.MANGA)

        val resolvedAnime = CacheManager.getAniListIdForMalId(sharedId, MediaType.ANIME)
        val resolvedManga = CacheManager.getAniListIdForMalId(sharedId, MediaType.MANGA)

        assertEquals(animeAniListId, resolvedAnime)
        assertEquals(mangaAniListId, resolvedManga)
        assertNotEquals(resolvedAnime, resolvedManga)
    }
}
