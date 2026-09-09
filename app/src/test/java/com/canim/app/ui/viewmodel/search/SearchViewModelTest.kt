package com.canim.app.ui.viewmodel.search

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.MediaType
import com.canim.app.ui.viewmodel.createTestSearchViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SearchViewModelTest {

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
        viewModel = createTestSearchViewModel()
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @Test
    fun testSearchEventQueryChanged() {
        viewModel.onSearchEvent(SearchEvent.QueryChanged("Naruto", MediaType.ANIME))
        val state = viewModel.searchState.value
        assertEquals("Naruto", state.query)
        assertEquals(MediaType.ANIME, state.type)
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

        var state = viewModel.searchState.value
        assertEquals(listOf("Fantasy", "Adventure"), state.genres)
        assertEquals(2021, state.year)
        assertEquals("MOVIE", state.format)

        viewModel.onSearchEvent(SearchEvent.FilterReset)
        state = viewModel.searchState.value
        assertTrue(state.genres.isEmpty())
        assertNull(state.year)
        assertNull(state.format)
    }

    @Test
    fun testDirectMethods() {
        viewModel.search("Frieren", MediaType.ANIME)
        assertEquals("Frieren", viewModel.searchState.value.query)
        assertEquals(MediaType.ANIME, viewModel.searchState.value.type)

        viewModel.applySearchFilters(listOf("Fantasy"), 2023, "TV")
        assertEquals(listOf("Fantasy"), viewModel.searchState.value.genres)
        assertEquals(2023, viewModel.searchState.value.year)
        assertEquals("TV", viewModel.searchState.value.format)

        viewModel.resetSearchFilters()
        assertTrue(viewModel.searchState.value.genres.isEmpty())

        viewModel.setSearchType(MediaType.MANGA)
        assertEquals(MediaType.MANGA, viewModel.searchState.value.type)
    }
}
