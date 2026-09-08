package com.canim.app.ui.viewmodel

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.repository.CanimRepository
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.ui.viewmodel.global.GlobalEvent
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
class GlobalStateIsolationTest {

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
    fun testGlobalEventUpdatesGlobalState() {
        viewModel.onGlobalEvent(GlobalEvent.SetActiveTab("library"))
        assertEquals("library", viewModel.globalState.value.activeTab)
        assertEquals("library", viewModel.uiState.value.activeTab)

        viewModel.onGlobalEvent(GlobalEvent.SetAppMode("offline"))
        assertEquals("offline", viewModel.globalState.value.appMode)
        assertEquals("offline", viewModel.uiState.value.appMode)

        viewModel.onGlobalEvent(GlobalEvent.ShowSnackbar("Notifikasi berhasil"))
        assertEquals("Notifikasi berhasil", viewModel.globalState.value.snackbarMessage)
        assertEquals("Notifikasi berhasil", viewModel.uiState.value.snackbarMessage)

        viewModel.onGlobalEvent(GlobalEvent.DismissSnackbar)
        assertNull(viewModel.globalState.value.snackbarMessage)
        assertNull(viewModel.uiState.value.snackbarMessage)

        viewModel.onGlobalEvent(GlobalEvent.SetStatsOpen(true))
        assertTrue(viewModel.globalState.value.isStatsOpen)
        assertTrue(viewModel.uiState.value.isStatsOpen)

        viewModel.onGlobalEvent(GlobalEvent.SetStatsOpen(false))
        assertFalse(viewModel.globalState.value.isStatsOpen)
        assertFalse(viewModel.uiState.value.isStatsOpen)

        viewModel.onGlobalEvent(GlobalEvent.SetAddTitleSheetOpen(true))
        assertTrue(viewModel.globalState.value.isAddTitleSheetOpen)
        assertTrue(viewModel.uiState.value.isAddTitleSheetOpen)

        viewModel.onGlobalEvent(GlobalEvent.SetAddTitleSheetOpen(false))
        assertFalse(viewModel.globalState.value.isAddTitleSheetOpen)
        assertFalse(viewModel.uiState.value.isAddTitleSheetOpen)
    }

    @Test
    fun testGlobalEventIsolationDoesNotMutateFeatureStates() {
        val initialSearchState = viewModel.searchState.value
        val initialDiscoverState = viewModel.discoverState.value
        val initialDetailState = viewModel.detailState.value
        val initialLibraryState = viewModel.libraryState.value
        val initialGachaState = viewModel.gachaState.value
        val initialStudioState = viewModel.studioState.value
        val initialUpdateState = viewModel.updateState.value

        // Dispatch various global events
        viewModel.onGlobalEvent(GlobalEvent.SetActiveTab("profile"))
        viewModel.onGlobalEvent(GlobalEvent.ShowSnackbar("Global state test"))
        viewModel.onGlobalEvent(GlobalEvent.DismissSnackbar)
        viewModel.onGlobalEvent(GlobalEvent.SetAppMode("offline"))

        // Verify that ALL modular feature states remain completely untouched
        assertEquals(initialSearchState, viewModel.searchState.value)
        assertEquals(initialDiscoverState, viewModel.discoverState.value)
        assertEquals(initialDetailState, viewModel.detailState.value)
        assertEquals(initialLibraryState, viewModel.libraryState.value)
        assertEquals(initialGachaState, viewModel.gachaState.value)
        assertEquals(initialStudioState, viewModel.studioState.value)
        assertEquals(initialUpdateState, viewModel.updateState.value)
    }

    @Test
    fun testLegacyPublicMethodsDelegateToGlobalState() {
        viewModel.setTab("search")
        assertEquals("search", viewModel.globalState.value.activeTab)

        viewModel.showSnackbar("Pesan legacy")
        assertEquals("Pesan legacy", viewModel.globalState.value.snackbarMessage)

        viewModel.dismissSnackbar()
        assertNull(viewModel.globalState.value.snackbarMessage)

        viewModel.setAppMode("online_sync")
        assertEquals("online_sync", viewModel.globalState.value.appMode)
    }
}
