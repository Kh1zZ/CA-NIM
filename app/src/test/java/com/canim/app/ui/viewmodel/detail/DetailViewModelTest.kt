package com.canim.app.ui.viewmodel.detail

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.ui.viewmodel.createTestDetailViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DetailViewModelTest {

    private lateinit var viewModel: DetailViewModel

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
        viewModel = createTestDetailViewModel()
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
    fun testOpenAndCloseDetail() {
        val media = fakeItem(100, "Death Note")
        viewModel.onDetailEvent(DetailEvent.OpenDetail(media, MediaType.ANIME))

        val state = viewModel.detailState.value
        assertTrue(state.isOpen)
        assertEquals(MediaType.ANIME, state.mediaType)
        assertNotNull(state.selectedItem)

        viewModel.onDetailEvent(DetailEvent.CloseDetail)
        val closedState = viewModel.detailState.value
        assertFalse(closedState.isOpen)
        assertNull(closedState.selectedItem)
    }

    @Test
    fun testScrollPositionPreservation() {
        viewModel.saveDetailScrollPosition("media_100", 3, 150)
        val pos = viewModel.getDetailScrollPosition("media_100")
        assertEquals(3, pos.first)
        assertEquals(150, pos.second)
    }

    @Test
    fun testDetailCaching() {
        val media = fakeItem(100, "Death Note")
        val detail = ExtendedMediaDetail(
            malId = 100,
            anilistId = 1100,
            title = "Death Note",
            titleEnglish = "Death Note",
            coverImage = "https://example.com/cover.jpg"
        )
        viewModel.cacheDetail(media, detail)
        val cached = viewModel.getCachedDetail(media)
        assertNotNull(cached)
        assertEquals(100, cached?.malId)
        assertEquals("Death Note", cached?.title)
    }

    @Test
    fun testStudioAndFallbackPreservation() {
        val media = fakeItem(101, "Attack on Titan")
        val fallbackDetail = ExtendedMediaDetail(
            malId = 101,
            title = "Attack on Titan",
            studio = "Wit Studio",
            studioId = 858,
            publisher = "Kodansha",
            isFromFallback = true
        )
        viewModel.cacheDetail(media, fallbackDetail)
        val cached = viewModel.getCachedDetail(media)
        assertNotNull(cached)
        assertEquals("Wit Studio", cached?.studio)
        assertEquals(858, cached?.studioId)
        assertEquals("Kodansha", cached?.publisher)
        assertTrue(cached?.isFromFallback == true)
    }

    @Test
    fun testInitialStateHasAniListAvailable() {
        val state = viewModel.detailState.value
        assertFalse(state.isAniListUnavailable)
    }
}
