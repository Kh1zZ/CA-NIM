package com.canim.app.ui.viewmodel.gacha

import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.ui.viewmodel.createTestGachaViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GachaViewModelTest {

    private lateinit var viewModel: GachaViewModel

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        val gachaCreditManager = GachaCreditManager.getInstance(app)
        viewModel = createTestGachaViewModel(gachaCreditManager = gachaCreditManager)
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
    fun testGachaEventUpdatesCredits() {
        viewModel.onGachaEvent(GachaEvent.UpdateCredits(10))
        assertEquals(10, viewModel.gachaState.value.credits)

        viewModel.onGachaEvent(GachaEvent.ConsumeCredit)
        assertEquals(9, viewModel.gachaState.value.credits)
    }

    @Test
    fun testConsumeGachaCreditMethod() {
        viewModel.onGachaEvent(GachaEvent.UpdateCredits(5))
        val consumed = viewModel.consumeGachaCredit()
        assertTrue(consumed)
        assertEquals(4, viewModel.gachaState.value.credits)
    }

    @Test
    fun testSwipeDismissRemovesCard() {
        val card1 = fakeItem(1)
        viewModel.onGachaEvent(GachaEvent.SwipeDismiss(card1))
        assertFalse(viewModel.gachaState.value.deck.contains(card1))
    }
}
