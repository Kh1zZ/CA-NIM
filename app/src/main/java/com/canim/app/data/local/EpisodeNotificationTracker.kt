package com.canim.app.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the last notified episode number for anime to ensure
 * notifications are strictly deduplicated per episode and prevent notification spam.
 */
@Singleton
class EpisodeNotificationTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_NAME = "canim_airing_episode_notifications"
        private const val KEY_PREFIX = "notified_ep_"
        private const val KEY_PREFIX_AIRING_STARTED = "notified_airing_started_"
    }

    /**
     * Checks if a notification should be sent for the given anime and episode.
     * Returns true ONLY IF this episode has not yet been notified (or is higher than the last notified episode).
     */
    fun shouldNotify(malId: Int, episodeNumber: Int): Boolean {
        if (episodeNumber <= 0) return false
        val lastNotified = prefs.getInt("${KEY_PREFIX}$malId", 0)
        return episodeNumber > lastNotified
    }

    /**
     * Marks the episode as notified so it will never notify again.
     */
    fun markNotified(malId: Int, episodeNumber: Int) {
        val currentMax = prefs.getInt("${KEY_PREFIX}$malId", 0)
        if (episodeNumber > currentMax) {
            prefs.edit().putInt("${KEY_PREFIX}$malId", episodeNumber).apply()
        }
    }

    /**
     * Retrieves the last notified episode number for the given anime.
     */
    fun getLastNotifiedEpisode(malId: Int): Int {
        return prefs.getInt("${KEY_PREFIX}$malId", 0)
    }

    /**
     * Checks if the "started airing" notification has already been sent for the given anime.
     */
    fun shouldNotifyAiringStarted(malId: Int): Boolean {
        if (malId <= 0) return false
        return !prefs.getBoolean("${KEY_PREFIX_AIRING_STARTED}$malId", false)
    }

    /**
     * Marks the "started airing" notification as sent for the given anime.
     */
    fun markAiringStartedNotified(malId: Int) {
        if (malId > 0) {
            prefs.edit().putBoolean("${KEY_PREFIX_AIRING_STARTED}$malId", true).apply()
        }
    }

    /**
     * Clears all notification history (useful for testing or cache reset).
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
