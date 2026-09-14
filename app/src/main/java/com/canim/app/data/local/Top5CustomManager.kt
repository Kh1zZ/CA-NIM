package com.canim.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages persistent storage for user-curated Top 5 Anime and Manga selections.
 */
class Top5CustomManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "canim_top5_custom_prefs"
        private const val KEY_TOP5_ANIME_IDS = "top5_anime_ids"
        private const val KEY_TOP5_MANGA_IDS = "top5_manga_ids"

        @Volatile
        private var INSTANCE: Top5CustomManager? = null

        fun getInstance(context: Context): Top5CustomManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Top5CustomManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getTop5AnimeIds(): List<String> {
        return loadIdList(KEY_TOP5_ANIME_IDS)
    }

    fun saveTop5AnimeIds(ids: List<String>) {
        saveIdList(KEY_TOP5_ANIME_IDS, ids.take(5))
    }

    fun getTop5MangaIds(): List<String> {
        return loadIdList(KEY_TOP5_MANGA_IDS)
    }

    fun saveTop5MangaIds(ids: List<String>) {
        saveIdList(KEY_TOP5_MANGA_IDS, ids.take(5))
    }

    private fun loadIdList(key: String): List<String> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveIdList(key: String, list: List<String>) {
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }
}
