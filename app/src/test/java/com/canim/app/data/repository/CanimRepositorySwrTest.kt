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
}
