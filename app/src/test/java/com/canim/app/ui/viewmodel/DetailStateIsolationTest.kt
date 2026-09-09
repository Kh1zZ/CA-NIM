package com.canim.app.ui.viewmodel

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.CanimRepository
import com.canim.app.domain.repository.CanimRepositoryContract
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.ui.viewmodel.detail.DetailEvent
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
class DetailStateIsolationTest {

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

    private fun fakeItem(id: Int, title: String = "Test Media $id"): MediaItem = MediaItem(
        malId = id,
        anilistId = id + 1000,
        title = title,
        titleEnglish = title,
        imageUrl = "https://example.com/cover.jpg",
        type = MediaType.ANIME,
        score = 8.8,
        synopsis = "Synopsis for $title",
        episodes = 24,
        genres = listOf("Action", "Mystery"),
        status = "FINISHED",
        format = "TV"
    )

    @Test
    fun testDetailEventOpenAndCloseUpdatesDetailState() {
        val media = fakeItem(100, "Death Note")
        viewModel.onDetailEvent(DetailEvent.OpenDetail(media, MediaType.ANIME))

        val detailState = viewModel.detailState.value
        assertTrue(detailState.isOpen)
        assertEquals(MediaType.ANIME, detailState.mediaType)
        assertNotNull(detailState.selectedItem)

        // Verify backward compatibility on uiState
        val uiState = viewModel.uiState.value
        assertTrue(uiState.isDetailOpen)
        assertEquals(MediaType.ANIME, uiState.detailMediaType)
        assertNotNull(uiState.selectedDetailItem)

        viewModel.onDetailEvent(DetailEvent.CloseDetail)

        val closedDetailState = viewModel.detailState.value
        assertFalse(closedDetailState.isOpen)
        assertNull(closedDetailState.selectedItem)
        assertFalse(viewModel.uiState.value.isDetailOpen)
    }

    @Test
    fun testDetailEventIsolationDoesNotMutateOtherFeatureState() {
        val initialSearchState = viewModel.searchState.value
        val initialDiscoverState = viewModel.discoverState.value
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

        val media = fakeItem(200, "Steins;Gate")

        // Dispatch detail events
        viewModel.onDetailEvent(DetailEvent.OpenDetail(media, MediaType.ANIME))
        viewModel.onDetailEvent(DetailEvent.OpenCastCrewProfile(101, isStaff = true))
        viewModel.onDetailEvent(DetailEvent.CloseCastCrewProfile)
        viewModel.onDetailEvent(DetailEvent.CloseDetail)

        // Verify SEARCH feature state remains completely untouched
        assertEquals(initialSearchState, viewModel.searchState.value)

        // Verify DISCOVER feature state remains completely untouched
        assertEquals(initialDiscoverState, viewModel.discoverState.value)

        // Verify other feature and global states remain 100% UNTOUCHED
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
    fun testLegacyPublicMethodsDelegateToDetailState() {
        val media = fakeItem(300, "Fullmetal Alchemist")
        viewModel.openDetail(media, MediaType.ANIME)

        assertTrue(viewModel.detailState.value.isOpen)
        assertEquals(MediaType.ANIME, viewModel.detailState.value.mediaType)

        viewModel.closeDetail()
        assertFalse(viewModel.detailState.value.isOpen)
    }
}
