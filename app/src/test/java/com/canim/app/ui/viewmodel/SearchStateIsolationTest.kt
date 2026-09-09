package com.canim.app.ui.viewmodel

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.MediaType
import com.canim.app.ui.viewmodel.search.SearchEvent
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
class SearchStateIsolationTest {

    private lateinit var viewModel: CanimViewModel

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()

        val app = RuntimeEnvironment.getApplication()
        val gachaCreditManager = com.canim.app.data.local.GachaCreditManager.getInstance(app)
        viewModel = createTestCanimViewModel(gachaCreditManager = gachaCreditManager)
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @Test
    fun testSearchEventQueryChangedUpdatesSearchState() {
        viewModel.onSearchEvent(SearchEvent.QueryChanged("Naruto", MediaType.ANIME))

        val searchState = viewModel.searchState.value
        assertEquals("Naruto", searchState.query)
        assertEquals(MediaType.ANIME, searchState.type)

        // Verify backward-compatible mirror on legacy uiState
        val uiState = viewModel.uiState.value
        assertEquals("Naruto", uiState.searchQuery)
        assertEquals(MediaType.ANIME, uiState.searchType)
    }

    @Test
    fun testSearchEventIsolationDoesNotMutateOtherFeatureState() {
        val initialUiState = viewModel.uiState.value

        val expectedDiscoverCategory = initialUiState.selectedDiscoverCategory
        val expectedDiscoverFilter = initialUiState.discoverFilter
        val expectedDiscoverItems = initialUiState.discoverItems
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

        // Dispatch a succession of search events
        viewModel.onSearchEvent(SearchEvent.QueryChanged("Bleach", MediaType.ANIME))
        viewModel.onSearchEvent(SearchEvent.FilterApplied(listOf("Action", "Supernatural"), 2004, "TV"))
        viewModel.onSearchEvent(SearchEvent.TypeChanged(MediaType.MANGA))
        viewModel.onSearchEvent(SearchEvent.FilterReset)
        viewModel.onSearchEvent(SearchEvent.Refresh)

        // Verify that Search state updated as expected
        val finalSearchState = viewModel.searchState.value
        assertEquals("Bleach", finalSearchState.query)
        assertEquals(MediaType.MANGA, finalSearchState.type)
        assertTrue(finalSearchState.genres.isEmpty())
        assertNull(finalSearchState.year)
        assertNull(finalSearchState.format)

        // Verify that UNRELATED feature and global states remained 100% UNTOUCHED
        val currentUiState = viewModel.uiState.value
        assertEquals(expectedDiscoverCategory, currentUiState.selectedDiscoverCategory)
        assertEquals(expectedDiscoverFilter, currentUiState.discoverFilter)
        assertEquals(expectedDiscoverItems, currentUiState.discoverItems)
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
    fun testSearchEventTypeChangedResetsFilters() {
        viewModel.onSearchEvent(SearchEvent.QueryChanged("Attack on Titan", MediaType.ANIME))
        viewModel.onSearchEvent(SearchEvent.FilterApplied(listOf("Action", "Drama"), 2013, "TV"))

        var searchState = viewModel.searchState.value
        assertEquals(listOf("Action", "Drama"), searchState.genres)
        assertEquals(2013, searchState.year)
        assertEquals("TV", searchState.format)

        // Switching type should reset active filters
        viewModel.onSearchEvent(SearchEvent.TypeChanged(MediaType.MANGA))

        searchState = viewModel.searchState.value
        assertEquals(MediaType.MANGA, searchState.type)
        assertTrue(searchState.genres.isEmpty())
        assertNull(searchState.year)
        assertNull(searchState.format)
    }

    @Test
    fun testSearchEventFilterAppliedAndReset() {
        viewModel.onSearchEvent(
            SearchEvent.FilterApplied(
                genres = listOf("Fantasy", "Adventure"),
                year = 2021,
                format = "MOVIE"
            )
        )

        var searchState = viewModel.searchState.value
        assertEquals(listOf("Fantasy", "Adventure"), searchState.genres)
        assertEquals(2021, searchState.year)
        assertEquals("MOVIE", searchState.format)

        viewModel.onSearchEvent(SearchEvent.FilterReset)

        searchState = viewModel.searchState.value
        assertTrue(searchState.genres.isEmpty())
        assertNull(searchState.year)
        assertNull(searchState.format)
    }

    @Test
    fun testLegacyPublicMethodsDelegateToSearchState() {
        viewModel.search("Frieren", MediaType.ANIME)
        assertEquals("Frieren", viewModel.searchState.value.query)
        assertEquals(MediaType.ANIME, viewModel.searchState.value.type)

        viewModel.applySearchFilters(listOf("Fantasy"), 2023, "TV")
        assertEquals(listOf("Fantasy"), viewModel.searchState.value.genres)
        assertEquals(2023, viewModel.searchState.value.year)
        assertEquals("TV", viewModel.searchState.value.format)

        viewModel.resetSearchFilters()
        assertTrue(viewModel.searchState.value.genres.isEmpty())
        assertNull(viewModel.searchState.value.year)

        viewModel.setSearchType(MediaType.MANGA)
        assertEquals(MediaType.MANGA, viewModel.searchState.value.type)
    }
}
