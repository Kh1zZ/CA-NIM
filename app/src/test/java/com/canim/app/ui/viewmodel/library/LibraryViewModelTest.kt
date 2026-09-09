package com.canim.app.ui.viewmodel.library

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.model.*
import com.canim.app.ui.viewmodel.createTestLibraryViewModel
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
class LibraryViewModelTest {

    private lateinit var viewModel: LibraryViewModel
    private lateinit var gachaCreditManager: GachaCreditManager

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        gachaCreditManager = GachaCreditManager.getInstance(app)
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
        viewModel = createTestLibraryViewModel(gachaCreditManager = gachaCreditManager)
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    private fun waitUntil(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition() && (System.currentTimeMillis() - start) < timeoutMs) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    @Test
    fun testSetFiltersAndSearchQuery() {
        viewModel.setLibraryFilterType(MediaType.MANGA)
        assertEquals(MediaType.MANGA, viewModel.libraryState.value.filterType)

        viewModel.setLibraryStatusFilter("watching")
        assertEquals("watching", viewModel.libraryState.value.statusFilter)

        viewModel.setLibrarySearch("Frieren")
        assertEquals("Frieren", viewModel.libraryState.value.searchQuery)

        viewModel.setLibrarySort("score")
        assertEquals("score", viewModel.libraryState.value.sortBy)
    }

    @Test
    fun testSaveAndDeleteAnimeAndManga() {
        val anime = UserMediaItem(
            identity = MediaRef(malId = 1, anilistId = 101),
            metadata = MediaMetadata(title = "Test Anime", imageUrl = "https://example.com/anime.jpg", type = MediaType.ANIME, totalEpisodes = 12),
            tracking = MalTracking(status = "watching", progress = 1)
        )
        viewModel.saveAnime(anime)
        waitUntil { viewModel.libraryState.value.animeList.any { it.id == anime.id } }

        var state = viewModel.libraryState.value
        assertTrue(state.animeList.any { it.id == anime.id })

        val manga = UserMediaItem(
            identity = MediaRef(malId = 2, anilistId = 202),
            metadata = MediaMetadata(title = "Test Manga", imageUrl = "https://example.com/manga.jpg", type = MediaType.MANGA, totalChapters = 50),
            tracking = MalTracking(status = "reading", progress = 5)
        )
        viewModel.saveManga(manga)
        waitUntil { viewModel.libraryState.value.mangaList.any { it.id == manga.id } }

        state = viewModel.libraryState.value
        assertTrue(state.mangaList.any { it.id == manga.id })

        viewModel.deleteAnime(anime.id)
        waitUntil { viewModel.libraryState.value.animeList.none { it.id == anime.id } }

        viewModel.deleteManga(manga.id)
        waitUntil { viewModel.libraryState.value.mangaList.none { it.id == manga.id } }

        state = viewModel.libraryState.value
        assertFalse(state.animeList.any { it.id == anime.id })
        assertFalse(state.mangaList.any { it.id == manga.id })
    }

    @Test
    fun testQuickIncrementAndDecrementAnime() {
        val anime = UserMediaItem(
            identity = MediaRef(malId = 10, anilistId = 110),
            metadata = MediaMetadata(title = "Ongoing Anime", imageUrl = "https://example.com/ongoing.jpg", type = MediaType.ANIME, totalEpisodes = 24),
            tracking = MalTracking(status = "watching", progress = 3)
        )
        viewModel.saveAnime(anime)
        waitUntil { viewModel.libraryState.value.animeList.any { it.id == anime.id } }

        viewModel.quickIncrementAnime(anime.id)
        waitUntil { viewModel.findAnimeItem(anime.id)?.tracking?.progress == 4 }
        var updated = viewModel.findAnimeItem(anime.id)
        assertNotNull(updated)
        assertEquals(4, updated!!.tracking.progress)

        viewModel.quickDecrementAnime(anime.id)
        waitUntil { viewModel.findAnimeItem(anime.id)?.tracking?.progress == 3 }
        updated = viewModel.findAnimeItem(anime.id)
        assertNotNull(updated)
        assertEquals(3, updated!!.tracking.progress)
    }
}
