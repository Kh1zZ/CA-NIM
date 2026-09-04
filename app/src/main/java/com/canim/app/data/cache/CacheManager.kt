package com.canim.app.data.cache

import android.content.Context
import coil.Coil
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap

data class CacheEntry<T>(
    val data: T,
    val timestamp: Long = System.currentTimeMillis(),
    val ttlMillis: Long
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() - timestamp > ttlMillis
}

/**
 * Centralized Cache Management for CA'NIM.
 * Manages memory & disk caching with bounded size, LRU eviction, configurable TTL,
 * stale-while-revalidate support, negative caching, and explicit cache invalidation.
 */
object CacheManager {

    // Configurable TTLs
    const val TTL_STATIC_METADATA = 24 * 60 * 60 * 1000L // 24 hours
    const val TTL_SEARCH = 60 * 60 * 1000L               // 1 hour
    const val TTL_DISCOVER = 30 * 60 * 1000L             // 30 minutes
    const val TTL_DETAIL = 12 * 60 * 60 * 1000L          // 12 hours
    const val TTL_MAL_FALLBACK = 24 * 60 * 60 * 1000L    // 24 hours
    const val TTL_ID_MAPPING = 30L * 24 * 60 * 60 * 1000L // 30 days
    const val TTL_NEGATIVE = 5 * 60 * 1000L              // 5 minutes for negative caching

    // Maximum capacities
    private const val MAX_METADATA_ENTRIES = 500
    private const val MAX_SEARCH_ENTRIES = 150
    private const val MAX_DISCOVER_ENTRIES = 50
    private const val MAX_DETAIL_ENTRIES = 200
    private const val MAX_ID_MAPPINGS = 2500

    private fun <K, V> createLruMap(maxCapacity: Int): MutableMap<K, V> {
        return Collections.synchronizedMap(
            object : LinkedHashMap<K, V>(maxCapacity, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                    return size > maxCapacity
                }
            }
        )
    }

    // Cache stores
    private val metadataCache = createLruMap<String, CacheEntry<MediaItem>>(MAX_METADATA_ENTRIES)
    private val searchCache = createLruMap<String, CacheEntry<List<MediaItem>>>(MAX_SEARCH_ENTRIES)
    private val discoverCache = createLruMap<String, CacheEntry<List<MediaItem>>>(MAX_DISCOVER_ENTRIES)
    private val detailCache = createLruMap<String, CacheEntry<ExtendedMediaDetail>>(MAX_DETAIL_ENTRIES)
    private val malFallbackCache = createLruMap<String, CacheEntry<MediaItem>>(MAX_METADATA_ENTRIES)
    private val idMappingMalToAniList = createLruMap<Int, CacheEntry<Int>>(MAX_ID_MAPPINGS)
    private val idMappingAniListToMal = createLruMap<Int, CacheEntry<Int>>(MAX_ID_MAPPINGS)
    private val negativeCache = createLruMap<String, CacheEntry<Boolean>>(MAX_METADATA_ENTRIES)

    // --- Search Cache ---
    fun getSearch(query: String, type: String): List<MediaItem>? {
        val key = "${type}_${query.trim().lowercase()}"
        val entry = searchCache[key] ?: return null
        return if (entry.isExpired) {
            searchCache.remove(key)
            null
        } else {
            entry.data
        }
    }

    fun putSearch(query: String, type: String, items: List<MediaItem>) {
        val key = "${type}_${query.trim().lowercase()}"
        searchCache[key] = CacheEntry(items, ttlMillis = TTL_SEARCH)
    }

    // --- Discover Cache ---
    fun getDiscover(categoryKey: String): List<MediaItem>? {
        val entry = discoverCache[categoryKey] ?: return null
        return if (entry.isExpired) {
            discoverCache.remove(categoryKey)
            null
        } else {
            entry.data
        }
    }

    fun putDiscover(categoryKey: String, items: List<MediaItem>) {
        discoverCache[categoryKey] = CacheEntry(items, ttlMillis = TTL_DISCOVER)
    }

    // --- Extended Detail Cache ---
    fun getDetail(id: String): ExtendedMediaDetail? {
        val entry = detailCache[id] ?: return null
        return if (entry.isExpired) {
            detailCache.remove(id)
            null
        } else {
            entry.data
        }
    }

    fun putDetail(id: String, detail: ExtendedMediaDetail) {
        detailCache[id] = CacheEntry(detail, ttlMillis = TTL_DETAIL)
    }

    // --- MAL Fallback Cache ---
    fun getMalFallback(id: String): MediaItem? {
        val entry = malFallbackCache[id] ?: return null
        return if (entry.isExpired) {
            malFallbackCache.remove(id)
            null
        } else {
            entry.data
        }
    }

    fun putMalFallback(id: String, item: MediaItem) {
        malFallbackCache[id] = CacheEntry(item, ttlMillis = TTL_MAL_FALLBACK)
    }

    // --- ID Mapping AniList <-> MAL ---
    fun getAniListIdForMalId(malId: Int): Int? {
        val entry = idMappingMalToAniList[malId] ?: return null
        return if (entry.isExpired) {
            idMappingMalToAniList.remove(malId)
            null
        } else {
            entry.data
        }
    }

    fun getMalIdForAniListId(aniListId: Int): Int? {
        val entry = idMappingAniListToMal[aniListId] ?: return null
        return if (entry.isExpired) {
            idMappingAniListToMal.remove(aniListId)
            null
        } else {
            entry.data
        }
    }

    fun putIdMapping(malId: Int?, aniListId: Int?) {
        if (malId != null && malId > 0 && aniListId != null && aniListId > 0) {
            idMappingMalToAniList[malId] = CacheEntry(aniListId, ttlMillis = TTL_ID_MAPPING)
            idMappingAniListToMal[aniListId] = CacheEntry(malId, ttlMillis = TTL_ID_MAPPING)
        }
    }

    // --- Negative Caching ---
    fun isNegativeCached(key: String): Boolean {
        val entry = negativeCache[key] ?: return false
        return if (entry.isExpired) {
            negativeCache.remove(key)
            false
        } else {
            true
        }
    }

    fun putNegativeCache(key: String) {
        negativeCache[key] = CacheEntry(true, ttlMillis = TTL_NEGATIVE)
    }

    // --- Invalidation on User Tracking Changes ---
    fun invalidateMedia(malId: Int?, anilistId: Int?) {
        malId?.let {
            detailCache.remove("mal_$it")
            malFallbackCache.remove("mal_$it")
        }
        anilistId?.let {
            detailCache.remove("ani_$it")
        }
    }

    // --- Asynchronous Expiration Cleanup ---
    suspend fun pruneExpired() = withContext(Dispatchers.IO) {
        searchCache.entries.removeIf { it.value.isExpired }
        discoverCache.entries.removeIf { it.value.isExpired }
        detailCache.entries.removeIf { it.value.isExpired }
        metadataCache.entries.removeIf { it.value.isExpired }
        malFallbackCache.entries.removeIf { it.value.isExpired }
        negativeCache.entries.removeIf { it.value.isExpired }
    }

    // --- Manual Cache Clear (Preserves Local Library & Auth Credentials) ---
    fun clearMetadataCache() {
        metadataCache.clear()
        searchCache.clear()
        discoverCache.clear()
        detailCache.clear()
        malFallbackCache.clear()
        negativeCache.clear()
    }

    suspend fun clearImageCache(context: Context) = withContext(Dispatchers.IO) {
        try {
            // Clear Coil memory and disk cache via loader
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()

            // Clear Coil disk cache directory
            val imageCacheDir = File(context.cacheDir, "canim_image_cache")
            if (imageCacheDir.exists()) {
                imageCacheDir.deleteRecursively()
                imageCacheDir.mkdirs()
            }
            val legacyDir = File(context.cacheDir, "image_cache")
            if (legacyDir.exists()) {
                legacyDir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }

    suspend fun clearAllCache(context: Context) = withContext(Dispatchers.IO) {
        clearMetadataCache()
        clearImageCache(context)
    }
}
