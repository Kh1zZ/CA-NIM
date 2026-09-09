package com.canim.app.ui.viewmodel

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.CanimRepository
import com.canim.app.domain.repository.CanimRepositoryContract
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.ui.viewmodel.gacha.GachaEvent
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
class GachaStateIsolationTest {

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

    private fun fakeItem(id: Int): MediaItem = MediaItem(
        malId = id,
        anilistId = id + 1000,
        title = "Gacha Card $id",
        titleEnglish = "Gacha Card $id",
        imageUrl = "https://example.com/card.jpg",
        type = MediaType.ANIME,
        score = 8.0,
        synopsis = "Synopsis $id",
        episodes = 12,
        genres = listOf("Action"),
        status = "FINISHED",
        format = "TV"
    )

    @Test
    fun testGachaEventUpdatesGachaState() {
        viewModel.onGachaEvent(GachaEvent.UpdateCredits(10))

        val gachaState = viewModel.gachaState.value
        assertEquals(10, gachaState.credits)

        // Verify backward compatibility on uiState
        assertEquals(10, viewModel.uiState.value.gachaCredits)

        viewModel.onGachaEvent(GachaEvent.ConsumeCredit)
        assertEquals(9, viewModel.gachaState.value.credits)
        assertEquals(9, viewModel.uiState.value.gachaCredits)
    }

    @Test
    fun testGachaEventIsolationDoesNotMutateOtherFeatureState() {
        val initialSearchState = viewModel.searchState.value
        val initialDiscoverState = viewModel.discoverState.value
        val initialDetailState = viewModel.detailState.value
        val initialLibraryState = viewModel.libraryState.value
        val initialUiState = viewModel.uiState.value

        val expectedMalUser = initialUiState.malUser
        val expectedIsAniListDown = initialUiState.isAniListDown
        val expectedIsMalDown = initialUiState.isMalDown
        val expectedStats = initialUiState.stats
        val expectedActiveTab = initialUiState.activeTab
        val expectedSyncStatus = initialUiState.syncStatus
        val expectedAppMode = initialUiState.appMode

        // Dispatch gacha events
        viewModel.onGachaEvent(GachaEvent.UpdateCredits(20))
        viewModel.onGachaEvent(GachaEvent.ConsumeCredit)
        viewModel.onGachaEvent(GachaEvent.SwipeDismiss(fakeItem(999)))

        // Verify that SEARCH feature state remains completely untouched
        assertEquals(initialSearchState, viewModel.searchState.value)

        // Verify that DISCOVER feature state remains completely untouched
        assertEquals(initialDiscoverState, viewModel.discoverState.value)

        // Verify that DETAIL feature state remains completely untouched
        assertEquals(initialDetailState, viewModel.detailState.value)

        // Verify that LIBRARY feature state remains completely untouched
        assertEquals(initialLibraryState, viewModel.libraryState.value)

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
    fun testLegacyPublicMethodsDelegateToGachaState() {
        viewModel.onGachaEvent(GachaEvent.UpdateCredits(5))
        val consumed = viewModel.consumeGachaCredit()
        assertTrue(consumed)
        assertEquals(4, viewModel.gachaState.value.credits)
    }
}
