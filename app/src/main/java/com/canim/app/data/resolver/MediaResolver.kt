package com.canim.app.data.resolver

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.AniListClient
import com.canim.app.data.remote.AniListResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralized ID Resolver for AniList <-> MyAnimeList namespaces.
 * Strictly adheres to:
 * - AniList ID and MAL ID are separate namespaces.
 * - Never fabricates an ID (never does malId = anilistId).
 * - Caches positive and negative lookups.
 * - Negative cache is ONLY written on verified NotFound, NEVER on timeout, network error, 429, or 5xx.
 */
object MediaResolver {

    /**
     * Resolves an AniList ID for a given MyAnimeList ID.
     */
    suspend fun resolveAniListIdForMalId(malId: Int, type: MediaType): Int? = withContext(Dispatchers.IO) {
        if (malId <= 0) return@withContext null

        // 1. Check cached mapping
        val cached = CacheManager.getAniListIdForMalId(malId)
        if (cached != null) return@withContext cached

        // 2. Check negative cache
        val negativeKey = "neg_mal_$malId"
        if (CacheManager.isNegativeCached(negativeKey)) return@withContext null

        // 3. Query AniList
        val result = AniListClient.resolveIdMal(malId, type)
        when (result) {
            is AniListResult.Success -> {
                CacheManager.putIdMapping(malId = malId, aniListId = result.data)
                result.data
            }
            is AniListResult.NotFound -> {
                // MANDATORY: Negative cache allowed ONLY on verified NotFound
                CacheManager.putNegativeCache(negativeKey)
                null
            }
            else -> {
                // MANDATORY: Timeout, 429, 5xx, NetworkError, GraphQLError -> NO negative cache
                null
            }
        }
    }

    /**
     * Resolves a MyAnimeList ID for a given AniList ID.
     */
    suspend fun resolveMalIdForAniListId(aniListId: Int): Int? = withContext(Dispatchers.IO) {
        if (aniListId <= 0) return@withContext null

        val cached = CacheManager.getMalIdForAniListId(aniListId)
        if (cached != null) return@withContext cached

        val negativeKey = "neg_ani_$aniListId"
        if (CacheManager.isNegativeCached(negativeKey)) return@withContext null

        val result = AniListClient.resolveAniListId(aniListId)
        when (result) {
            is AniListResult.Success -> {
                CacheManager.putIdMapping(malId = result.data, aniListId = aniListId)
                result.data
            }
            is AniListResult.NotFound -> {
                // MANDATORY: Negative cache allowed ONLY on verified NotFound
                CacheManager.putNegativeCache(negativeKey)
                null
            }
            else -> {
                // MANDATORY: Timeout, 429, 5xx, NetworkError, GraphQLError -> NO negative cache
                null
            }
        }
    }

    /**
     * Registers a verified mapping into the cache.
     */
    fun registerMapping(anilistId: Int?, malId: Int?) {
        if (anilistId != null && malId != null && anilistId > 0 && malId > 0) {
            CacheManager.putIdMapping(malId = malId, aniListId = anilistId)
        }
    }
}
