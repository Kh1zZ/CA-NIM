package com.canim.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages 14-day persistent cooldown for anime items drawn via Flashcard Gacha.
 *
 * Requirements:
 * - Persists MAL ID and draw timestamp.
 * - Excludes anime for 14 days (14 * 24 * 60 * 60 * 1000 ms).
 * - Cooldown survives app restart.
 * - After 14 days, the anime becomes eligible again (provided library exclusion allows it).
 */
class GachaCooldownManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "canim_gacha_cooldown_prefs"
        private const val KEY_COOLDOWN_MAP = "cooldown_timestamps"
        const val COOLDOWN_DURATION_MS = 14L * 24 * 60 * 60 * 1000L // 14 days

        @Volatile
        private var INSTANCE: GachaCooldownManager? = null

        fun getInstance(context: Context): GachaCooldownManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GachaCooldownManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun loadMap(): MutableMap<String, Long> {
        val json = prefs.getString(KEY_COOLDOWN_MAP, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            gson.fromJson<Map<String, Long>>(json, type)?.toMutableMap() ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveMap(map: Map<String, Long>) {
        prefs.edit().putString(KEY_COOLDOWN_MAP, gson.toJson(map)).apply()
    }

    @Synchronized
    fun recordGachaDrawn(malId: Int, timestamp: Long = System.currentTimeMillis()) {
        if (malId <= 0) return
        val map = loadMap()
        map[malId.toString()] = timestamp
        saveMap(map)
    }

    @Synchronized
    fun isUnderCooldown(malId: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (malId <= 0) return false
        val map = loadMap()
        val drawnAt = map[malId.toString()] ?: return false
        return (now - drawnAt) < COOLDOWN_DURATION_MS
    }

    @Synchronized
    fun getCooldownMalIds(now: Long = System.currentTimeMillis()): Set<Int> {
        val map = loadMap()
        val activeIds = mutableSetOf<Int>()
        val expiredKeys = mutableListOf<String>()

        for ((key, timestamp) in map) {
            if ((now - timestamp) < COOLDOWN_DURATION_MS) {
                key.toIntOrNull()?.let { activeIds.add(it) }
            } else {
                expiredKeys.add(key)
            }
        }

        // Lazy prune expired keys
        if (expiredKeys.isNotEmpty()) {
            expiredKeys.forEach { map.remove(it) }
            saveMap(map)
        }

        return activeIds
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
