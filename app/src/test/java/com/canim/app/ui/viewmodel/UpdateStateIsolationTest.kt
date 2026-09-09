package com.canim.app.ui.viewmodel

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.repository.CanimRepository
import com.canim.app.domain.repository.CanimRepositoryContract
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.ui.viewmodel.update.UpdateEvent
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
class UpdateStateIsolationTest {

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
        viewModel = CanimViewModel(repository, gachaCreditManager)
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @Test
    fun testUpdateEventUpdatesUpdateState() {
        viewModel.onUpdateEvent(UpdateEvent.SetAutoUpdateCheck(false))
        assertFalse(viewModel.updateState.value.isAutoCheckEnabled)
        assertFalse(viewModel.uiState.value.isAutoUpdateCheckEnabled)

        viewModel.onUpdateEvent(UpdateEvent.SetAutoUpdateCheck(true))
        assertTrue(viewModel.updateState.value.isAutoCheckEnabled)
        assertTrue(viewModel.uiState.value.isAutoUpdateCheckEnabled)

        viewModel.onUpdateEvent(UpdateEvent.DismissDialog)
        assertNull(viewModel.updateState.value.updateInfo)
        assertFalse(viewModel.updateState.value.isDownloading)
        assertNull(viewModel.updateState.value.downloadedApkFile)
        assertNull(viewModel.uiState.value.updateInfo)
        assertFalse(viewModel.uiState.value.isDownloadingUpdate)
        assertNull(viewModel.uiState.value.downloadedApkFile)
    }

    @Test
    fun testUpdateEventIsolationDoesNotMutateOtherFeatureState() {
        val initialSearchState = viewModel.searchState.value
        val initialDiscoverState = viewModel.discoverState.value
        val initialDetailState = viewModel.detailState.value
        val initialLibraryState = viewModel.libraryState.value
        val initialGachaState = viewModel.gachaState.value
        val initialStudioState = viewModel.studioState.value
        val initialUiState = viewModel.uiState.value

        val expectedMalUser = initialUiState.malUser
        val expectedIsAniListDown = initialUiState.isAniListDown
        val expectedIsMalDown = initialUiState.isMalDown
        val expectedStats = initialUiState.stats
        val expectedActiveTab = initialUiState.activeTab
        val expectedSyncStatus = initialUiState.syncStatus
        val expectedAppMode = initialUiState.appMode

        // Dispatch update events
        viewModel.onUpdateEvent(UpdateEvent.SetAutoUpdateCheck(false))
        viewModel.onUpdateEvent(UpdateEvent.DismissDialog)

        // Verify that all previously split features remain completely untouched
        assertEquals(initialSearchState, viewModel.searchState.value)
        assertEquals(initialDiscoverState, viewModel.discoverState.value)
        assertEquals(initialDetailState, viewModel.detailState.value)
        assertEquals(initialLibraryState, viewModel.libraryState.value)
        assertEquals(initialGachaState, viewModel.gachaState.value)
        assertEquals(initialStudioState, viewModel.studioState.value)

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
    fun testLegacyPublicMethodsDelegateToUpdateState() {
        viewModel.setAutoUpdateCheck(false)
        assertFalse(viewModel.updateState.value.isAutoCheckEnabled)

        viewModel.setAutoUpdateCheck(true)
        assertTrue(viewModel.updateState.value.isAutoCheckEnabled)

        viewModel.dismissUpdateDialog()
        assertFalse(viewModel.updateState.value.isDownloading)
        assertNull(viewModel.updateState.value.updateInfo)
    }
}
