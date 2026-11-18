package com.canim.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles boot completion, app updates, and legacy alarm triggers by delegating
 * network checks to WorkManager via [NotificationScheduler], avoiding BroadcastReceiver
 * 10-second timeouts and unready boot-time networking.
 */
class NotificationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK_NOTIFICATIONS = "com.canim.app.ACTION_CHECK_NOTIFICATIONS"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            NotificationScheduler.schedulePeriodicCheck(context)
        }

        if (action == ACTION_CHECK_NOTIFICATIONS || action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            NotificationScheduler.triggerImmediateCheck(context)
        }
    }
}
