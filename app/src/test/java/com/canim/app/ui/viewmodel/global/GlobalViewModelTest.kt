package com.canim.app.ui.viewmodel.global

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.MediaType
import com.canim.app.ui.navigation.ScreenRoute
import com.canim.app.ui.viewmodel.createTestGlobalViewModel
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
class GlobalViewModelTest {

    private lateinit var viewModel: GlobalViewModel

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
        viewModel = createTestGlobalViewModel()
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
    fun testInitialState() {
        val state = viewModel.globalState.value
        assertEquals("dashboard", state.activeTab)
        assertEquals("offline", state.appMode)
        assertFalse(state.malUser.isLoggedIn)
        assertNull(state.snackbarMessage)
        assertTrue(viewModel.screenStack.value.isEmpty())
    }

    @Test
    fun testNavigationStackOperations() {
        assertTrue(viewModel.screenStack.value.isEmpty())

        // Push screen
        viewModel.pushScreen(ScreenRoute.Stats)
        assertEquals(1, viewModel.screenStack.value.size)
        assertEquals(ScreenRoute.Stats, viewModel.screenStack.value.last())

        // Push another screen
        viewModel.pushScreen(ScreenRoute.AddTitleSheet)
        assertEquals(2, viewModel.screenStack.value.size)
        assertEquals(ScreenRoute.AddTitleSheet, viewModel.screenStack.value.last())

        // Pop screen
        val popped1 = viewModel.popScreen()
        assertTrue(popped1)
        assertEquals(1, viewModel.screenStack.value.size)
        assertEquals(ScreenRoute.Stats, viewModel.screenStack.value.last())

        // Pop last screen
        val popped2 = viewModel.popScreen()
        assertTrue(popped2)
        assertTrue(viewModel.screenStack.value.isEmpty())

        // Pop empty stack returns false
        val popped3 = viewModel.popScreen()
        assertFalse(popped3)
        assertTrue(viewModel.screenStack.value.isEmpty())
    }

    @Test
    fun testHelperNavigationMethods() {
        viewModel.openStats()
        assertEquals(ScreenRoute.Stats, viewModel.screenStack.value.last())
        viewModel.closeStats()
        assertTrue(viewModel.screenStack.value.isEmpty())

        viewModel.openAddTitleSheet()
        assertEquals(ScreenRoute.AddTitleSheet, viewModel.screenStack.value.last())
        viewModel.closeAddTitleSheet()
        assertTrue(viewModel.screenStack.value.isEmpty())

        viewModel.openStudio(10, "Kyoto Animation")
        assertEquals(ScreenRoute.StudioFilmography(10, "Kyoto Animation"), viewModel.screenStack.value.last())
        viewModel.popScreen()

        viewModel.openFlashcard()
        assertEquals(ScreenRoute.Flashcard, viewModel.screenStack.value.last())
        viewModel.popScreen()

        viewModel.openDetail("item_key", MediaType.ANIME)
        assertEquals(ScreenRoute.Detail("item_key", MediaType.ANIME), viewModel.screenStack.value.last())
        viewModel.popScreen()

        viewModel.openCastCrewProfile(100, true)
        assertEquals(ScreenRoute.CastCrew(100, true), viewModel.screenStack.value.last())
        viewModel.popScreen()

        viewModel.openFullCastList("Media Title", emptyList(), emptyList(), false)
        assertTrue(viewModel.screenStack.value.last() is ScreenRoute.FullCastList)
        viewModel.clearScreenStack()
        assertTrue(viewModel.screenStack.value.isEmpty())
    }

    @Test
    fun testTabSwitchingClearsScreenStack() {
        viewModel.pushScreen(ScreenRoute.Stats)
        assertEquals(1, viewModel.screenStack.value.size)

        viewModel.setTab("library")
        assertEquals("library", viewModel.globalState.value.activeTab)
        assertTrue(viewModel.screenStack.value.isEmpty())
    }

    @Test
    fun testStatsScrollPositionPreservation() {
        assertEquals(Pair(0, 0), viewModel.getStatsScrollPosition())

        viewModel.saveStatsScrollPosition(4, 250)
        assertEquals(Pair(4, 250), viewModel.getStatsScrollPosition())

        viewModel.resetStatsScrollPosition()
        assertEquals(Pair(0, 0), viewModel.getStatsScrollPosition())
    }

    @Test
    fun testSnackbarAndEvents() {
        viewModel.showSnackbar("Operasi berhasil")
        assertEquals("Operasi berhasil", viewModel.globalState.value.snackbarMessage)

        viewModel.dismissSnackbar()
        assertNull(viewModel.globalState.value.snackbarMessage)

        // Test GlobalEvent
        viewModel.onGlobalEvent(GlobalEvent.SetActiveTab("settings"))
        assertEquals("settings", viewModel.globalState.value.activeTab)

        viewModel.onGlobalEvent(GlobalEvent.SetAppMode("online_sync"))
        assertEquals("online_sync", viewModel.globalState.value.appMode)

        viewModel.onGlobalEvent(GlobalEvent.ShowSnackbar("Pesan GlobalEvent"))
        assertEquals("Pesan GlobalEvent", viewModel.globalState.value.snackbarMessage)

        viewModel.onGlobalEvent(GlobalEvent.DismissSnackbar)
        assertNull(viewModel.globalState.value.snackbarMessage)

        viewModel.onGlobalEvent(GlobalEvent.DismissColdStartOutageBanner)
        assertFalse(viewModel.globalState.value.showColdStartOutageBanner)

        viewModel.onGlobalEvent(GlobalEvent.RefreshHealth)
        assertNotNull(viewModel.globalState.value)
    }

    @Test
    fun testAuthAndCacheOperations() {
        val app = RuntimeEnvironment.getApplication()

        var callbackExecuted = false
        viewModel.handleOAuthCallback("code123", "state123") {
            callbackExecuted = true
        }
        waitUntil { callbackExecuted }
        assertTrue(callbackExecuted)
        assertEquals("online_sync", viewModel.globalState.value.appMode)

        var logoutExecuted = false
        viewModel.logoutMal {
            logoutExecuted = true
        }
        assertTrue(logoutExecuted)
        assertEquals("offline", viewModel.globalState.value.appMode)
        assertFalse(viewModel.globalState.value.malUser.isLoggedIn)

        var syncExecuted = false
        viewModel.syncWithMal {
            syncExecuted = true
        }
        waitUntil { syncExecuted }
        assertTrue(syncExecuted)

        viewModel.clearImageCache(app)
        viewModel.clearMetadataCache()
        viewModel.clearAllCache(app)
    }
}
