package com.canim.app.ui.viewmodel.studio

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.StudioFilmographySort
import com.canim.app.ui.viewmodel.createTestStudioViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StudioViewModelTest {

    private lateinit var viewModel: StudioViewModel

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
        viewModel = createTestStudioViewModel()
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @Test
    fun testStudioEventSetSort() {
        viewModel.onStudioEvent(StudioEvent.SetSort(StudioFilmographySort.SCORE_DESC))
        assertEquals(StudioFilmographySort.SCORE_DESC, viewModel.studioState.value.sort)

        viewModel.setStudioFilmographySort(StudioFilmographySort.YEAR_ASC)
        assertEquals(StudioFilmographySort.YEAR_ASC, viewModel.studioState.value.sort)
    }

    @Test
    fun testStudioClearSearch() {
        viewModel.onStudioEvent(StudioEvent.ClearStudioSearch)
        assertTrue(viewModel.studioState.value.searchResults.isEmpty())
        assertFalse(viewModel.studioState.value.isSearchingStudios)
    }

    @Test
    fun testOpenAndCloseStudio() {
        viewModel.openStudio(43, "ufotable")
        assertEquals(43, viewModel.studioState.value.studioId)
        assertEquals("ufotable", viewModel.studioState.value.studioName)

        viewModel.closeStudio()
        assertNull(viewModel.studioState.value.studioId)
        assertEquals("", viewModel.studioState.value.studioName)
    }
}
