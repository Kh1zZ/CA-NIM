package com.canim.app.data.local

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

class GachaCreditManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "canim_gacha_prefs"
        private const val KEY_CREDITS = "gacha_credits"
        private const val KEY_LAST_RESET_WEEK = "last_reset_week"
        private const val KEY_HIGHEST_PROGRESS_PREFIX = "highest_prog_"

        const val BASE_WEEKLY_CREDITS = 5
        const val CREDIT_PER_EPISODE = 1

        @Volatile
        private var INSTANCE: GachaCreditManager? = null

        fun getInstance(context: Context): GachaCreditManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GachaCreditManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getStartOfCurrentWeekMillis(now: Long = System.currentTimeMillis()): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
                add(Calendar.DAY_OF_YEAR, -daysFromMonday)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun getNextResetTimeMillis(now: Long = System.currentTimeMillis()): Long {
            return getStartOfCurrentWeekMillis(now) + (7L * 24 * 60 * 60 * 1000L)
        }
    }

    @Synchronized
    fun getCredits(): Int {
        checkAndApplyWeeklyReset()
        return prefs.getInt(KEY_CREDITS, BASE_WEEKLY_CREDITS)
    }

    @Synchronized
    fun consumeCredit(): Boolean {
        checkAndApplyWeeklyReset()
        val current = prefs.getInt(KEY_CREDITS, BASE_WEEKLY_CREDITS)
        return if (current > 0) {
            prefs.edit().putInt(KEY_CREDITS, current - 1).apply()
            true
        } else {
            false
        }
    }

    @Synchronized
    fun addCredit(amount: Int = CREDIT_PER_EPISODE): Int {
        checkAndApplyWeeklyReset()
        val current = prefs.getInt(KEY_CREDITS, BASE_WEEKLY_CREDITS)
        val updated = current + amount
        prefs.edit().putInt(KEY_CREDITS, updated).apply()
        return updated
    }

    @Synchronized
    fun setCredits(amount: Int) {
        checkAndApplyWeeklyReset()
        prefs.edit()
            .putInt(KEY_CREDITS, maxOf(0, amount))
            .putLong(KEY_LAST_RESET_WEEK, getStartOfCurrentWeekMillis())
            .apply()
    }

    @Synchronized
    fun checkAndApplyWeeklyReset() {
        val now = System.currentTimeMillis()
        val currentWeekStart = getStartOfCurrentWeekMillis(now)
        val lastReset = prefs.getLong(KEY_LAST_RESET_WEEK, 0L)

        if (lastReset < currentWeekStart) {
            val currentCredits = prefs.getInt(KEY_CREDITS, BASE_WEEKLY_CREDITS)
            // Floor minimum 5 credits upon weekly reset
            val newCredits = maxOf(currentCredits, BASE_WEEKLY_CREDITS)
            prefs.edit()
                .putInt(KEY_CREDITS, newCredits)
                .putLong(KEY_LAST_RESET_WEEK, currentWeekStart)
                .apply()
        }
    }

    @Synchronized
    fun getHighestProgress(mediaId: String): Int {
        val sanitized = mediaId.replace(":", "_").replace("/", "_")
        return prefs.getInt(KEY_HIGHEST_PROGRESS_PREFIX + sanitized, 0)
    }

    @Synchronized
    fun initBaselineProgress(mediaId: String, currentProgress: Int) {
        val sanitized = mediaId.replace(":", "_").replace("/", "_")
        val key = KEY_HIGHEST_PROGRESS_PREFIX + sanitized
        if (!prefs.contains(key)) {
            prefs.edit().putInt(key, maxOf(0, currentProgress)).apply()
        }
    }

    /**
     * Anti-abuse: Awards credits ONLY for genuinely new progress (strictly exceeding highest recorded progress).
     * Lowering and raising progress to or below the recorded peak yields 0 new credits.
     * Returns the number of credits awarded.
     */
    @Synchronized
    fun recordProgressAndAwardCredits(mediaId: String, newProgress: Int): Int {
        if (mediaId.isBlank() || newProgress <= 0) return 0
        val sanitized = mediaId.replace(":", "_").replace("/", "_")
        val key = KEY_HIGHEST_PROGRESS_PREFIX + sanitized
        val highestRecorded = prefs.getInt(key, 0)

        return if (newProgress > highestRecorded) {
            val netIncrease = newProgress - highestRecorded
            prefs.edit().putInt(key, newProgress).apply()
            addCredit(netIncrease * CREDIT_PER_EPISODE)
            netIncrease
        } else {
            0
        }
    }
}
