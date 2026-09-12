package com.canim.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.canim.app.MainActivity
import com.canim.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized Android System Notification Manager for CA'NIM.
 * Designed with an extensible architecture to support additional notification types in the future
 * (e.g. library airing alerts, streak reminders, sync progress).
 *
 * Current design:
 * - Minimalist styling: plain text and application icon only, avoiding bloated or cluttered layouts.
 */
@Singleton
class CanimNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID_UPDATES = "canim_app_updates"
        const val CHANNEL_NAME_UPDATES = "Pembaruan Aplikasi"
        const val CHANNEL_DESC_UPDATES = "Notifikasi rilis versi terbaru CA'NIM"

        const val CHANNEL_ID_AIRING = "canim_airing_alerts"
        const val CHANNEL_NAME_AIRING = "Episode Baru Anime"
        const val CHANNEL_DESC_AIRING = "Notifikasi saat episode baru anime favoritmu rilis"

        const val NOTIFICATION_ID_UPDATE = 1001
        const val REQUEST_CODE_UPDATE = 2001
        const val REQUEST_CODE_AIRING = 3001

        const val EXTRA_OPEN_UPDATE = "extra_open_update"
        const val EXTRA_UPDATE_VERSION = "extra_update_version"
        const val EXTRA_OPEN_AIRING_MAL_ID = "extra_open_airing_mal_id"
    }

    init {
        initChannels()
    }

    /**
     * Initializes all Android notification channels. Safe to call multiple times.
     */
    fun initChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Channel 1: App Updates
            val updateChannel = NotificationChannel(
                CHANNEL_ID_UPDATES,
                CHANNEL_NAME_UPDATES,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC_UPDATES
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(updateChannel)

            // Channel 2: Airing Alerts
            val airingChannel = NotificationChannel(
                CHANNEL_ID_AIRING,
                CHANNEL_NAME_AIRING,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC_AIRING
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(airingChannel)
        }
    }

    /**
     * Checks if notification posting is permitted by the system and user permissions.
     */
    fun canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            return permissionStatus == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /**
     * Dispatches a minimalist Android system notification when a new app update is detected.
     * Design: Only app icon and clear, concise text.
     */
    fun showUpdateNotification(latestVersion: String, releaseNotes: String? = null) {
        if (!canPostNotifications()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE, true)
            putExtra(EXTRA_UPDATE_VERSION, latestVersion)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_UPDATE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Pembaruan Aplikasi Tersedia"
        val message = "CA\'NIM $latestVersion sudah tersedia. Ketuk untuk melihat detail dan memperbarui."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    /**
     * Dispatches a minimalist Android system notification when a new episode airs for a watching anime.
     * Guaranteed deduplication per episode is handled prior to dispatch via EpisodeNotificationTracker.
     * Minimalist layout: small icon, concise title, clear message.
     */
    fun showAiringNotification(animeTitle: String, episodeNumber: Int, malId: Int) {
        if (!canPostNotifications()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_AIRING_MAL_ID, malId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_AIRING + (malId % 10000),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Episode Baru Rilis!"
        val message = "$animeTitle Episode $episodeNumber kini sudah tayang."

        val notificationId = 50000 + (malId % 10000)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_AIRING)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    /**
     * Extensible generic notification dispatcher for future features.
     */
    fun showGenericNotification(
        notificationId: Int,
        channelId: String,
        title: String,
        message: String,
        contentIntent: PendingIntent? = null
    ) {
        if (!canPostNotifications()) return

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }
}
