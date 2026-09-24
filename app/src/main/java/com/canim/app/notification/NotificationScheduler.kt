package com.canim.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * Lightweight, battery-efficient background notification scheduler.
 * Uses Android's AlarmManager with inexact repeating to schedule periodic notification
 * checks for airing episodes, plan-to-watch premieres, and app updates.
 *
 * Runs without foreground service or continuous wakelocks to maintain near-zero idle RAM and CPU usage.
 */
object NotificationScheduler {

    private const val REQUEST_CODE_ALARM = 4001
    private const val INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

    fun schedulePeriodicCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = NotificationAlarmReceiver.ACTION_CHECK_NOTIFICATIONS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                pendingIntent
            )
        } catch (_: Exception) {
            // In case of system restrictions, safely ignore
        }
    }

    fun cancelPeriodicCheck(context: Context) {
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
