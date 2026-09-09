package com.canim.app.ui.viewmodel

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.repository.CanimRepository
import com.canim.app.domain.repository.CanimRepositoryContract
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.ui.viewmodel.discover.DiscoverEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DiscoverStateIsolationTest {

    private lateinit var repository: CanimRepositoryContract
    private lateinit var viewModel: CanimViewModel

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()

        val app = RuntimeEnvironment.getApplication()
        val storage = MalSecureStorage(app)
        val malAuth = MalAuthManager(storage)
        repository = CanimRepository(malAuth)
        val gachaCreditManager = com.canim.app.data.local.GachaCreditManager.getInstance(app)
        viewModel = createTestCanimViewModel(repository, gachaCreditManager)
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @Test
    fun testDiscoverEventCategorySelectedUpdatesDiscoverState() {
        val filter = DiscoverFilter(genre = "Action", year = 2023)
        viewModel.onDiscoverEvent(DiscoverEvent.CategorySelected(DiscoverCategory.TOP_ANIME, filter))

        val discoverState = viewModel.discoverState.value
        assertEquals(DiscoverCategory.TOP_ANIME, discoverState.selectedCategory)
        assertEquals("Action", discoverState.filter.genre)
        assertEquals(2023, discoverState.filter.year)

        // Verify backward compatibility mirror on legacy uiState
        val uiState = viewModel.uiState.value
        assertEquals(DiscoverCategory.TOP_ANIME, uiState.selectedDiscoverCategory)
        assertEquals("Action", uiState.discoverFilter.genre)
        assertEquals(2023, uiState.discoverFilter.year)
    }

    @Test
    fun testDiscoverEventIsolationDoesNotMutateOtherFeatureState() {
        val initialSearchState = viewModel.searchState.value
        val initialUiState = viewModel.uiState.value

        val expectedAnimeList = initialUiState.animeList
        val expectedMangaList = initialUiState.mangaList
        val expectedMalUser = initialUiState.malUser
        val expectedIsAniListDown = initialUiState.isAniListDown
        val expectedIsMalDown = initialUiState.isMalDown
        val expectedGachaCredits = initialUiState.gachaCredits
        val expectedStats = initialUiState.stats
        val expectedActiveTab = initialUiState.activeTab
        val expectedSyncStatus = initialUiState.syncStatus
        val expectedAppMode = initialUiState.appMode

        // Dispatch a sequence of discover events
        viewModel.onDiscoverEvent(DiscoverEvent.CategorySelected(DiscoverCategory.UPCOMING))
        viewModel.onDiscoverEvent(DiscoverEvent.FilterUpdated(DiscoverFilter(format = "TV", season = "WINTER")))
        viewModel.onDiscoverEvent(DiscoverEvent.LoadMore)
        viewModel.onDiscoverEvent(DiscoverEvent.Refresh)

        // Verify that Discover state updated properly
        val finalDiscoverState = viewModel.discoverState.value
        assertEquals(DiscoverCategory.UPCOMING, finalDiscoverState.selectedCategory)
        assertEquals("TV", finalDiscoverState.filter.format)
        assertEquals("WINTER", finalDiscoverState.filter.season)

        // Verify that SEARCH feature state remains completely untouched
        val currentSearchState = viewModel.searchState.value
        assertEquals(initialSearchState, currentSearchState)

        // Verify that other feature and global states remain 100% UNTOUCHED
        val currentUiState = viewModel.uiState.value
        assertEquals(expectedAnimeList, currentUiState.animeList)
        assertEquals(expectedMangaList, currentUiState.mangaList)
        assertEquals(expectedMalUser, currentUiState.malUser)
        assertEquals(expectedIsAniListDown, currentUiState.isAniListDown)
        assertEquals(expectedIsMalDown, currentUiState.isMalDown)
        assertEquals(expectedGachaCredits, currentUiState.gachaCredits)
        assertEquals(expectedStats, currentUiState.stats)
        assertEquals(expectedActiveTab, currentUiState.activeTab)
        assertEquals(expectedSyncStatus, currentUiState.syncStatus)
        assertEquals(expectedAppMode, currentUiState.appMode)
    }

    @Test
    fun testDiscoverEventFilterUpdated() {
        val updatedFilter = DiscoverFilter(genre = "Comedy", format = "MOVIE")
        viewModel.onDiscoverEvent(DiscoverEvent.FilterUpdated(updatedFilter))

        val discoverState = viewModel.discoverState.value
        assertEquals("Comedy", discoverState.filter.genre)
        assertEquals("MOVIE", discoverState.filter.format)
        assertEquals(1, discoverState.page)
    }

    @Test
    fun testLegacyPublicMethodsDelegateToDiscoverState() {
        viewModel.loadDiscoverCategory(DiscoverCategory.TOP_MANGA)
        assertEquals(DiscoverCategory.TOP_MANGA, viewModel.discoverState.value.selectedCategory)

        viewModel.loadMoreDiscover()
        // Page should either remain or prepare for pagination loading
        assertEquals(DiscoverCategory.TOP_MANGA, viewModel.discoverState.value.selectedCategory)
    }
}
