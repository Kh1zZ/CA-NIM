package com.canim.app.ui.viewmodel.discover

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.ui.viewmodel.createTestDiscoverViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DiscoverViewModelTest {

    private lateinit var viewModel: DiscoverViewModel

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
        viewModel = createTestDiscoverViewModel()
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @Test
    fun testCategorySelectionUpdatesState() {
        val filter = DiscoverFilter(genre = "Action", year = 2023)
        viewModel.onDiscoverEvent(DiscoverEvent.CategorySelected(DiscoverCategory.TOP_ANIME, filter))

        val state = viewModel.discoverState.value
        assertEquals(DiscoverCategory.TOP_ANIME, state.selectedCategory)
        assertEquals("Action", state.filter.genre)
        assertEquals(2023, state.filter.year)
    }

    @Test
    fun testFilterUpdatedEvent() {
        val filter = DiscoverFilter(genre = "Comedy")
        viewModel.onDiscoverEvent(DiscoverEvent.FilterUpdated(filter))

        val state = viewModel.discoverState.value
        assertEquals("Comedy", state.filter.genre)
    }

    @Test
    fun testDirectMethodLoadDiscoverCategory() {
        val filter = DiscoverFilter(genre = "Sci-Fi", year = 2024)
        viewModel.loadDiscoverCategory(DiscoverCategory.TRENDING_NOW, filter)

        val state = viewModel.discoverState.value
        assertEquals(DiscoverCategory.TRENDING_NOW, state.selectedCategory)
        assertEquals("Sci-Fi", state.filter.genre)
        assertEquals(2024, state.filter.year)
    }
}
