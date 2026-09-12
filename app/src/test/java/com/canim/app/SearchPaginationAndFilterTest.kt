package com.canim.app

import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.SearchRepositoryImpl
import com.canim.app.data.repository.SwrCoordinator
import com.canim.app.domain.usecase.ObserveCacheRefreshUseCase
import com.canim.app.domain.usecase.SearchMediaUseCase
import com.canim.app.ui.viewmodel.FakeCanimRepository
import com.canim.app.ui.viewmodel.search.SearchEvent
import com.canim.app.ui.viewmodel.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchPaginationAndFilterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val swrCoordinator = SwrCoordinator()
    private val searchRepo = SearchRepositoryImpl(swrCoordinator)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun dummyMedia(id: Int, type: MediaType, title: String): MediaItem = MediaItem(
        malId = id,
        anilistId = id + 100,
        title = title,
        titleEnglish = title,
        imageUrl = "https://example.com/$id.jpg",
        type = type,
        score = 8.0,
        synopsis = "Synopsis for $title",
        episodes = 12,
        status = "FINISHED",
        year = 2024,
        genres = listOf("Action", "Fantasy"),
        format = "TV"
    )

    @Test
    fun testSearchFilterKeyConsistency() {
        val keyPage1 = searchRepo.searchFilterKey("Naruto", listOf("Action"), 2024, "TV", page = 1)
        val keyPage2 = searchRepo.searchFilterKey("Naruto", listOf("Action"), 2024, "TV", page = 2)

        assertEquals("Naruto_Action_2024_TV", keyPage1)
        assertEquals("Naruto_Action_2024_TV_p2", keyPage2)
        val searchKey = com.canim.app.data.cache.CacheManager.searchKey(keyPage1, "ANIME")
        assertTrue(searchRepo.matchesSearchKey(searchKey, keyPage1, "ANIME"))
        assertTrue(searchRepo.matchesSearchKey(keyPage1, keyPage1, "ANIME"))
    }

    @Test
    fun testSearchViewModelFilterAppliedTriggersSearchAndResetsPage() = runTest(testDispatcher) {
        val fakeRepo = object : FakeCanimRepository() {
            var searchCalls = 0
            override suspend fun searchAnime(
                query: String,
                genres: List<String>?,
                year: Int?,
                format: String?,
                page: Int,
                forceRefresh: Boolean
            ): List<MediaItem> {
                searchCalls++
                return listOf(dummyMedia(1, MediaType.ANIME, "Frieren"))
            }
        }

        val searchUseCase = SearchMediaUseCase(fakeRepo)
        val observeUseCase = ObserveCacheRefreshUseCase(fakeRepo, fakeRepo, fakeRepo)
        val viewModel = SearchViewModel(searchUseCase, observeUseCase, testDispatcher)

        // Apply filters
        viewModel.onSearchEvent(
            SearchEvent.FilterApplied(
                genres = listOf("Action", "Fantasy"),
                year = 2024,
                format = "TV"
            )
        )

        advanceTimeBy(350L)
        testScheduler.runCurrent()

        val state = viewModel.searchState.value
        assertEquals(listOf("Action", "Fantasy"), state.genres)
        assertEquals(2024, state.year)
        assertEquals("TV", state.format)
        assertEquals(1, state.page)
        assertTrue(fakeRepo.searchCalls >= 1)
    }

    @Test
    fun testSearchViewModelPaginationLoadMore() = runTest(testDispatcher) {
        val fakeRepo = object : FakeCanimRepository() {
            var requestedPage = 1
            override suspend fun searchAnime(
                query: String,
                genres: List<String>?,
                year: Int?,
                format: String?,
                page: Int,
                forceRefresh: Boolean
            ): List<MediaItem> {
                requestedPage = page
                return (1..25).map { dummyMedia(it + (page * 100), MediaType.ANIME, "Item $it p$page") }
            }
        }

        val searchUseCase = SearchMediaUseCase(fakeRepo)
        val observeUseCase = ObserveCacheRefreshUseCase(fakeRepo, fakeRepo, fakeRepo)
        val viewModel = SearchViewModel(searchUseCase, observeUseCase, testDispatcher)

        // Initial search
        viewModel.onSearchEvent(SearchEvent.QueryChanged("Solo", MediaType.ANIME))
        advanceTimeBy(350L)
        testScheduler.runCurrent()

        assertEquals(1, viewModel.searchState.value.page)
        assertEquals(25, viewModel.searchState.value.results.size)
        assertTrue(viewModel.searchState.value.canLoadMore)

        // Load More (Page 2)
        viewModel.loadMore()
        testScheduler.runCurrent()

        assertEquals(2, viewModel.searchState.value.page)
        assertEquals(50, viewModel.searchState.value.results.size)
    }

    @Test
    fun testSearchViewModelMangaFilterAndPagination() = runTest(testDispatcher) {
        val fakeRepo = object : FakeCanimRepository() {
            var lastRequestedType: MediaType = MediaType.ANIME
            var lastPage: Int = 1

            override suspend fun searchManga(
                query: String,
                genres: List<String>?,
                year: Int?,
                format: String?,
                page: Int,
                forceRefresh: Boolean
            ): List<MediaItem> {
                lastRequestedType = MediaType.MANGA
                lastPage = page
                return (1..20).map { dummyMedia(it + (page * 100), MediaType.MANGA, "Manga $it p$page") }
            }
        }

        val searchUseCase = SearchMediaUseCase(fakeRepo)
        val observeUseCase = ObserveCacheRefreshUseCase(fakeRepo, fakeRepo, fakeRepo)
        val viewModel = SearchViewModel(searchUseCase, observeUseCase, testDispatcher)

        // Switch to Manga
        viewModel.setSearchType(MediaType.MANGA)
        viewModel.onSearchEvent(SearchEvent.QueryChanged("Berserk", MediaType.MANGA))
        advanceTimeBy(350L)
        testScheduler.runCurrent()

        assertEquals(MediaType.MANGA, viewModel.searchState.value.type)
        assertEquals(1, viewModel.searchState.value.page)
        assertEquals(20, viewModel.searchState.value.results.size)
        assertTrue(viewModel.searchState.value.canLoadMore)

        // Load More Manga
        viewModel.loadMore()
        testScheduler.runCurrent()

        assertEquals(2, viewModel.searchState.value.page)
        assertEquals(40, viewModel.searchState.value.results.size)
    }
}
