package com.canim.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Lightweight, battery-efficient background notification scheduler.
 * Uses AndroidX WorkManager backed by JobScheduler to reliably execute periodic
 * checks for airing anime episodes, plan-to-watch premieres, and app updates,
 * even when the app is completely closed or device enters Doze mode.
 */
object NotificationScheduler {

    const val WORK_NAME_PERIODIC = "canim_periodic_notification_check"
    const val WORK_NAME_IMMEDIATE = "canim_immediate_notification_check"
    private const val REQUEST_CODE_ALARM = 4001
    private const val INTERVAL_MINUTES = 15L
    private const val FLEX_MINUTES = 5L

    fun schedulePeriodicCheck(context: Context) {
        // 1. AndroidX WorkManager (Primary, resilient background scheduler)
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES,
                FLEX_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (_: Exception) {
            // Safe fallback if WorkManager initialization encounters system limitations
        }

        // Clean up legacy AlarmManager intent to prevent duplicate wakeups
        cancelLegacyAlarm(context)
    }

    /**
     * Triggers an immediate one-time background check when connected to network.
     */
    fun triggerImmediateCheck(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (_: Exception) {}
    }

    fun cancelPeriodicCheck(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        } catch (_: Exception) {}
        cancelLegacyAlarm(context)
    }

    private fun cancelLegacyAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = NotificationAlarmReceiver.ACTION_CHECK_NOTIFICATIONS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            try {
                alarmManager.cancel(pendingIntent)
            } catch (_: Exception) {}
        }
    }
}
