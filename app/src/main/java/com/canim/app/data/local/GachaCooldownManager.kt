package com.canim.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.canim.app.data.model.MediaItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages 14-day persistent cooldown for anime items drawn via Flashcard Gacha.
 *
 * Requirements:
 * - Persists MAL ID and/or AniList ID and draw timestamp.
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
        private const val KEY_COOLDOWN_ANI_MAP = "cooldown_anilist_timestamps"
        const val COOLDOWN_DURATION_MS = 14L * 24 * 60 * 60 * 1000L // 14 days

        @Volatile
        private var INSTANCE: GachaCooldownManager? = null

        fun getInstance(context: Context): GachaCooldownManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GachaCooldownManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun loadMap(key: String): MutableMap<String, Long> {
        val json = prefs.getString(key, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            gson.fromJson<Map<String, Long>>(json, type)?.toMutableMap() ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveMap(key: String, map: Map<String, Long>) {
        prefs.edit().putString(key, gson.toJson(map)).apply()
    }

    @Synchronized
    fun recordGachaDrawn(malId: Int, timestamp: Long = System.currentTimeMillis()) {
        recordGachaDrawn(malId = malId, anilistId = null, timestamp = timestamp)
    }

    @Synchronized
    fun recordGachaDrawn(malId: Int?, anilistId: Int?, timestamp: Long = System.currentTimeMillis()) {
        if ((malId == null || malId <= 0) && (anilistId == null || anilistId <= 0)) return

        if (malId != null && malId > 0) {
            val malMap = loadMap(KEY_COOLDOWN_MAP)
            malMap[malId.toString()] = timestamp
            saveMap(KEY_COOLDOWN_MAP, malMap)
        }

        if (anilistId != null && anilistId > 0) {
            val aniMap = loadMap(KEY_COOLDOWN_ANI_MAP)
            aniMap[anilistId.toString()] = timestamp
            saveMap(KEY_COOLDOWN_ANI_MAP, aniMap)
        }
    }

    @Synchronized
    fun recordGachaDrawn(item: MediaItem, timestamp: Long = System.currentTimeMillis()) {
        recordGachaDrawn(malId = item.malId, anilistId = item.anilistId, timestamp = timestamp)
    }

    @Synchronized
    fun isUnderCooldown(malId: Int, now: Long = System.currentTimeMillis()): Boolean {
        return isUnderCooldown(malId = malId, anilistId = null, now = now)
    }

    @Synchronized
    fun isUnderCooldown(malId: Int?, anilistId: Int?, now: Long = System.currentTimeMillis()): Boolean {
        if (malId != null && malId > 0) {
            val malMap = loadMap(KEY_COOLDOWN_MAP)
            val drawnAt = malMap[malId.toString()]
            if (drawnAt != null && (now - drawnAt) < COOLDOWN_DURATION_MS) {
                return true
            }
        }

        if (anilistId != null && anilistId > 0) {
            val aniMap = loadMap(KEY_COOLDOWN_ANI_MAP)
            val drawnAt = aniMap[anilistId.toString()]
            if (drawnAt != null && (now - drawnAt) < COOLDOWN_DURATION_MS) {
                return true
            }
        }

        return false
    }

    @Synchronized
    fun isUnderCooldown(item: MediaItem, now: Long = System.currentTimeMillis()): Boolean {
        return isUnderCooldown(malId = item.malId, anilistId = item.anilistId, now = now)
    }

    @Synchronized
    fun getCooldownMalIds(now: Long = System.currentTimeMillis()): Set<Int> {
        return getActiveIdsPruningExpired(KEY_COOLDOWN_MAP, now)
    }

    @Synchronized
    fun getCooldownAniListIds(now: Long = System.currentTimeMillis()): Set<Int> {
        return getActiveIdsPruningExpired(KEY_COOLDOWN_ANI_MAP, now)
    }

    private fun getActiveIdsPruningExpired(prefKey: String, now: Long): Set<Int> {
        val map = loadMap(prefKey)
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
            saveMap(prefKey, map)
        }

        return activeIds
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
