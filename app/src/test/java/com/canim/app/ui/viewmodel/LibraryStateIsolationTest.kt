package com.canim.app.ui.viewmodel

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.CanimRepository
import com.canim.app.domain.repository.CanimRepositoryContract
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.ui.viewmodel.library.LibraryEvent
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
class LibraryStateIsolationTest {

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
        viewModel = CanimViewModel(repository)
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @Test
    fun testLibraryEventFilterAndSortUpdatesLibraryState() {
        viewModel.onLibraryEvent(LibraryEvent.SetFilterType(MediaType.MANGA))
        viewModel.onLibraryEvent(LibraryEvent.SetStatusFilter("reading"))
        viewModel.onLibraryEvent(LibraryEvent.SetSearchQuery("Chainsaw"))
        viewModel.onLibraryEvent(LibraryEvent.SetSortBy("title"))

        val libraryState = viewModel.libraryState.value
        assertEquals(MediaType.MANGA, libraryState.filterType)
        assertEquals("reading", libraryState.statusFilter)
        assertEquals("Chainsaw", libraryState.searchQuery)
        assertEquals("title", libraryState.sortBy)

        // Verify backward compatibility on uiState
        val uiState = viewModel.uiState.value
        assertEquals(MediaType.MANGA, uiState.libraryFilterType)
        assertEquals("reading", uiState.libraryStatusFilter)
        assertEquals("Chainsaw", uiState.librarySearchQuery)
        assertEquals("title", uiState.librarySortBy)
    }

    @Test
    fun testLibraryEventIsolationDoesNotMutateOtherFeatureState() {
        val initialSearchState = viewModel.searchState.value
        val initialDiscoverState = viewModel.discoverState.value
        val initialDetailState = viewModel.detailState.value
        val initialUiState = viewModel.uiState.value

        val expectedMalUser = initialUiState.malUser
        val expectedIsAniListDown = initialUiState.isAniListDown
        val expectedIsMalDown = initialUiState.isMalDown
        val expectedGachaCredits = initialUiState.gachaCredits
        val expectedActiveTab = initialUiState.activeTab
        val expectedSyncStatus = initialUiState.syncStatus
        val expectedAppMode = initialUiState.appMode

        // Dispatch library events
        viewModel.onLibraryEvent(LibraryEvent.SetFilterType(MediaType.MANGA))
        viewModel.onLibraryEvent(LibraryEvent.SetStatusFilter("completed"))
        viewModel.onLibraryEvent(LibraryEvent.SetSearchQuery("Solo Leveling"))
        viewModel.onLibraryEvent(LibraryEvent.SetSortBy("score"))

        // Verify that SEARCH feature state remains completely untouched
        assertEquals(initialSearchState, viewModel.searchState.value)

        // Verify that DISCOVER feature state remains completely untouched
        assertEquals(initialDiscoverState, viewModel.discoverState.value)

        // Verify that DETAIL feature state remains completely untouched
        assertEquals(initialDetailState, viewModel.detailState.value)

        // Verify that global auth and other states remain 100% UNTOUCHED
        val currentUiState = viewModel.uiState.value
        assertEquals(expectedMalUser, currentUiState.malUser)
        assertEquals(expectedIsAniListDown, currentUiState.isAniListDown)
        assertEquals(expectedIsMalDown, currentUiState.isMalDown)
        assertEquals(expectedGachaCredits, currentUiState.gachaCredits)
        assertEquals(expectedActiveTab, currentUiState.activeTab)
        assertEquals(expectedSyncStatus, currentUiState.syncStatus)
        assertEquals(expectedAppMode, currentUiState.appMode)
    }

    @Test
    fun testLegacyPublicMethodsDelegateToLibraryState() {
        viewModel.setLibraryFilterType(MediaType.MANGA)
        assertEquals(MediaType.MANGA, viewModel.libraryState.value.filterType)

        viewModel.setLibraryStatusFilter("on_hold")
        assertEquals("on_hold", viewModel.libraryState.value.statusFilter)

        viewModel.setLibrarySearch("Monster")
        assertEquals("Monster", viewModel.libraryState.value.searchQuery)

        viewModel.setLibrarySort("score")
        assertEquals("score", viewModel.libraryState.value.sortBy)
    }
}
