package com.canim.app.notification

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.TIRAMISU])
class CanimNotificationManagerTest {

    private lateinit var notificationManager: CanimNotificationManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        notificationManager = CanimNotificationManager(context)
    }

    @Test
    fun testInitChannelsCreatesAppUpdatesChannel() {
        notificationManager.initChannels()

        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = systemManager.getNotificationChannel(CanimNotificationManager.CHANNEL_ID_UPDATES)

        assertNotNull(channel)
        assertEquals(CanimNotificationManager.CHANNEL_ID_UPDATES, channel.id)
        assertEquals(CanimNotificationManager.CHANNEL_NAME_UPDATES, channel.name.toString())
    }

    @Test
    fun testShowUpdateNotificationDoesNotCrash() {
        notificationManager.initChannels()
        // Should execute smoothly without throwing exceptions
        notificationManager.showUpdateNotification(
            latestVersion = "v6.3.4",
            releaseNotes = "New update available"
        )
    }

    @Test
    fun testShowGenericNotificationDoesNotCrash() {
        notificationManager.initChannels()
        notificationManager.showGenericNotification(
            notificationId = 1002,
            channelId = CanimNotificationManager.CHANNEL_ID_UPDATES,
            title = "Test Title",
            message = "Test Message"
        )
    }
}
