package com.canim.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Lightweight candidate token holding ONLY MAL ID and genre data.
 * Adheres strictly to Phase 3 PRD:
 * "Background candidate records should contain only:
 * - MAL ID
 * - genre data
 * Do not fetch/store full metadata during candidate expansion."
 */
data class GachaCandidateToken(
    val malId: Int,
    val genres: List<String>
)

class GachaCandidateStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "canim_gacha_candidates_prefs"
        private const val KEY_CANDIDATE_TOKENS = "candidate_tokens"
        private const val MAX_CANDIDATES = 200

        @Volatile
        private var INSTANCE: GachaCandidateStore? = null

        fun getInstance(context: Context): GachaCandidateStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GachaCandidateStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // In-memory bounded LRU cache for candidate tokens
    private val memoryTokens = Collections.synchronizedMap(
        object : LinkedHashMap<Int, GachaCandidateToken>(MAX_CANDIDATES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, GachaCandidateToken>?): Boolean {
                return size > MAX_CANDIDATES
            }
        }
    )

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val json = prefs.getString(KEY_CANDIDATE_TOKENS, null) ?: return
        try {
            val type = object : TypeToken<List<GachaCandidateToken>>() {}.type
            val tokens: List<GachaCandidateToken>? = gson.fromJson(json, type)
            tokens?.forEach { memoryTokens[it.malId] = it }
        } catch (_: Exception) {}
    }

    private fun persistToPrefs() {
        val list = memoryTokens.values.toList().takeLast(MAX_CANDIDATES)
        prefs.edit().putString(KEY_CANDIDATE_TOKENS, gson.toJson(list)).apply()
    }

    @Synchronized
    fun addCandidates(tokens: List<GachaCandidateToken>) {
        if (tokens.isEmpty()) return
        for (token in tokens) {
            if (token.malId > 0) {
                memoryTokens[token.malId] = token
            }
        }
        persistToPrefs()
    }

    @Synchronized
    fun getAllCandidates(): List<GachaCandidateToken> {
        return memoryTokens.values.toList()
    }

    @Synchronized
    fun getCandidateCount(): Int = memoryTokens.size

    @Synchronized
    fun clearAll() {
        memoryTokens.clear()
        prefs.edit().clear().apply()
    }
}
