package com.canim.app.ui.viewmodel

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.StudioFilmographySort
import com.canim.app.data.repository.CanimRepository
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.ui.viewmodel.studio.StudioEvent
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
class StudioStateIsolationTest {

    private lateinit var repository: CanimRepository
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
        viewModel = CanimViewModel(repository)
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @Test
    fun testStudioEventUpdatesStudioState() {
        viewModel.onStudioEvent(StudioEvent.SetSort(StudioFilmographySort.SCORE_DESC))
        assertEquals(StudioFilmographySort.SCORE_DESC, viewModel.studioState.value.sort)
        assertEquals(StudioFilmographySort.SCORE_DESC, viewModel.uiState.value.studioFilmographySort)

        viewModel.onStudioEvent(StudioEvent.ClearStudioSearch)
        assertTrue(viewModel.studioState.value.searchResults.isEmpty())
        assertFalse(viewModel.studioState.value.isSearchingStudios)
        assertTrue(viewModel.uiState.value.studioSearchResults.isEmpty())
        assertFalse(viewModel.uiState.value.isSearchingStudios)
    }

    @Test
    fun testStudioEventIsolationDoesNotMutateOtherFeatureState() {
        val initialSearchState = viewModel.searchState.value
        val initialDiscoverState = viewModel.discoverState.value
        val initialDetailState = viewModel.detailState.value
        val initialLibraryState = viewModel.libraryState.value
        val initialGachaState = viewModel.gachaState.value
        val initialUiState = viewModel.uiState.value

        val expectedMalUser = initialUiState.malUser
        val expectedIsAniListDown = initialUiState.isAniListDown
        val expectedIsMalDown = initialUiState.isMalDown
        val expectedStats = initialUiState.stats
        val expectedActiveTab = initialUiState.activeTab
        val expectedSyncStatus = initialUiState.syncStatus
        val expectedAppMode = initialUiState.appMode

        // Dispatch studio events
        viewModel.onStudioEvent(StudioEvent.SetSort(StudioFilmographySort.POPULARITY_DESC))
        viewModel.onStudioEvent(StudioEvent.ClearStudioSearch)

        // Verify that all previously split features remain completely untouched
        assertEquals(initialSearchState, viewModel.searchState.value)
        assertEquals(initialDiscoverState, viewModel.discoverState.value)
        assertEquals(initialDetailState, viewModel.detailState.value)
        assertEquals(initialLibraryState, viewModel.libraryState.value)
        assertEquals(initialGachaState, viewModel.gachaState.value)

        // Verify that global auth and other states remain 100% UNTOUCHED
        val currentUiState = viewModel.uiState.value
        assertEquals(expectedMalUser, currentUiState.malUser)
        assertEquals(expectedIsAniListDown, currentUiState.isAniListDown)
        assertEquals(expectedIsMalDown, currentUiState.isMalDown)
        assertEquals(expectedStats, currentUiState.stats)
        assertEquals(expectedActiveTab, currentUiState.activeTab)
        assertEquals(expectedSyncStatus, currentUiState.syncStatus)
        assertEquals(expectedAppMode, currentUiState.appMode)
    }

    @Test
    fun testLegacyPublicMethodsDelegateToStudioState() {
        viewModel.setStudioFilmographySort(StudioFilmographySort.YEAR_ASC)
        assertEquals(StudioFilmographySort.YEAR_ASC, viewModel.studioState.value.sort)

        viewModel.clearStudioSearch()
        assertTrue(viewModel.studioState.value.searchResults.isEmpty())
    }
}
