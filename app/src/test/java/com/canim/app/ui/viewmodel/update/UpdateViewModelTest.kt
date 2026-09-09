package com.canim.app.ui.viewmodel.update

import com.canim.app.ui.viewmodel.createTestUpdateViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UpdateViewModelTest {

    private lateinit var viewModel: UpdateViewModel

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        viewModel = createTestUpdateViewModel(context = app)
    }

    @Test
    fun testUpdateEventUpdatesState() {
        viewModel.onUpdateEvent(UpdateEvent.SetAutoUpdateCheck(false))
        assertFalse(viewModel.updateState.value.isAutoCheckEnabled)

        viewModel.onUpdateEvent(UpdateEvent.SetAutoUpdateCheck(true))
        assertTrue(viewModel.updateState.value.isAutoCheckEnabled)

        viewModel.onUpdateEvent(UpdateEvent.DismissDialog)
        assertNull(viewModel.updateState.value.updateInfo)
        assertFalse(viewModel.updateState.value.isDownloading)
        assertNull(viewModel.updateState.value.downloadedApkFile)
    }

    @Test
    fun testPublicMethodsUpdateState() {
        viewModel.setAutoUpdateCheck(false)
        assertFalse(viewModel.updateState.value.isAutoCheckEnabled)

        viewModel.setAutoUpdateCheck(true)
        assertTrue(viewModel.updateState.value.isAutoCheckEnabled)

        viewModel.dismissUpdateDialog()
        assertFalse(viewModel.updateState.value.isDownloading)
        assertNull(viewModel.updateState.value.updateInfo)
    }
}
