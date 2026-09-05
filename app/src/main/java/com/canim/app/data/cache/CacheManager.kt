package com.canim.app.data.cache

import android.content.Context
import coil.Coil
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.UserMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap

data class StudioFilmographyPage(
    val studioId: Int,
    val studioName: String,
    val items: List<MediaItem>,
    val hasNextPage: Boolean,
    val currentPage: Int,
    val total: Int = 0
)

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
 * canonical key generation, negative caching, and verified cache invalidation.
 */
object CacheManager {

    // Configurable TTLs
    const val TTL_STATIC_METADATA = 24 * 60 * 60 * 1000L // 24 hours
    const val TTL_SEARCH = 60 * 60 * 1000L               // 1 hour
    const val TTL_DISCOVER = 30 * 60 * 1000L             // 30 minutes
    const val TTL_DETAIL = 12 * 60 * 60 * 1000L          // 12 hours
    const val TTL_STUDIO = 12 * 60 * 60 * 1000L          // 12 hours
    const val TTL_MAL_FALLBACK = 24 * 60 * 60 * 1000L    // 24 hours
    const val TTL_ID_MAPPING = 30L * 24 * 60 * 60 * 1000L // 30 days
    const val TTL_TRACKING = 5 * 60 * 1000L              // 5 minutes short-lived tracking cache
    const val TTL_NEGATIVE = 5 * 60 * 1000L              // 5 minutes for negative caching

    // Maximum capacities
    private const val MAX_METADATA_ENTRIES = 500
    private const val MAX_SEARCH_ENTRIES = 150
    private const val MAX_DISCOVER_ENTRIES = 50
    private const val MAX_DETAIL_ENTRIES = 200
    private const val MAX_STUDIO_ENTRIES = 100
    private const val MAX_ID_MAPPINGS = 2500
    private const val MAX_TRACKING_ENTRIES = 10

    private fun <K, V> createLruMap(maxCapacity: Int): MutableMap<K, V> {
        return Collections.synchronizedMap(
            object : LinkedHashMap<K, V>(maxCapacity, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                    return size > maxCapacity
                }
            }
        )
    }

    // Canonical Key Generators (Section 14)
    fun detailKey(anilistId: Int?, malId: Int?): String {
        return when {
            anilistId != null && malId != null -> "detail_ani_${anilistId}_mal_${malId}"
            anilistId != null -> "detail_ani_${anilistId}"
            malId != null -> "detail_mal_${malId}"
            else -> "detail_unknown"
        }
    }

    fun malFallbackKey(malId: Int, type: String): String = "mal_fallback_${malId}_${type.uppercase()}"

    fun searchKey(query: String, type: String): String = "search_${type.uppercase()}_${query.trim().lowercase()}"

    fun discoverKey(categoryKey: String): String = "discover_$categoryKey"

    fun studioKey(studioId: Int, page: Int): String = "studio_${studioId}_page_$page"

    // Cache stores
    private val metadataCache = createLruMap<String, CacheEntry<MediaItem>>(MAX_METADATA_ENTRIES)
    private val searchCache = createLruMap<String, CacheEntry<List<MediaItem>>>(MAX_SEARCH_ENTRIES)
    private val discoverCache = createLruMap<String, CacheEntry<List<MediaItem>>>(MAX_DISCOVER_ENTRIES)
    private val detailCache = createLruMap<String, CacheEntry<ExtendedMediaDetail>>(MAX_DETAIL_ENTRIES)
    private val studioCache = createLruMap<String, CacheEntry<StudioFilmographyPage>>(MAX_STUDIO_ENTRIES)
    private val malFallbackCache = createLruMap<String, CacheEntry<MediaItem>>(MAX_METADATA_ENTRIES)
    private val idMappingMalToAniList = createLruMap<Int, CacheEntry<Int>>(MAX_ID_MAPPINGS)
    private val idMappingAniListToMal = createLruMap<Int, CacheEntry<Int>>(MAX_ID_MAPPINGS)
    private val negativeCache = createLruMap<String, CacheEntry<Boolean>>(MAX_METADATA_ENTRIES)
    private val trackingCache = createLruMap<String, CacheEntry<List<UserMediaItem>>>(MAX_TRACKING_ENTRIES)

    // --- Search Cache ---
    fun getSearch(query: String, type: String): List<MediaItem>? {
        val key = searchKey(query, type)
        val entry = searchCache[key] ?: return null
        return if (entry.isExpired) {
            searchCache.remove(key)
            null
        } else {
            entry.data
        }
    }

    fun putSearch(query: String, type: String, items: List<MediaItem>) {
        val key = searchKey(query, type)
        searchCache[key] = CacheEntry(items, ttlMillis = TTL_SEARCH)
    }

    // --- Discover Cache ---
    fun getDiscover(categoryKey: String): List<MediaItem>? {
        val key = discoverKey(categoryKey)
        val entry = discoverCache[key] ?: return null
        return if (entry.isExpired) {
            discoverCache.remove(key)
            null
        } else {
            entry.data
        }
    }

    fun putDiscover(categoryKey: String, items: List<MediaItem>) {
        val key = discoverKey(categoryKey)
        discoverCache[key] = CacheEntry(items, ttlMillis = TTL_DISCOVER)
    }

    // --- Detail Cache ---
    fun getDetail(key: String): ExtendedMediaDetail? {
        val entry = detailCache[key] ?: return null
        return if (entry.isExpired) {
            detailCache.remove(key)
            null
        } else {
            entry.data
        }
    }

    fun putDetail(key: String, detail: ExtendedMediaDetail) {
        detailCache[key] = CacheEntry(detail, ttlMillis = TTL_DETAIL)
    }

    // --- Studio Filmography Cache ---
    fun getStudioFilmography(studioId: Int, page: Int): StudioFilmographyPage? {
        val key = studioKey(studioId, page)
        val entry = studioCache[key] ?: return null
        return if (entry.isExpired) {
            studioCache.remove(key)
            null
        } else {
            entry.data
        }
    }

    fun putStudioFilmography(studioId: Int, page: Int, pageData: StudioFilmographyPage) {
        val key = studioKey(studioId, page)
        studioCache[key] = CacheEntry(pageData, ttlMillis = TTL_STUDIO)
    }

    // --- Static Metadata Cache ---
    fun getMetadata(key: String): MediaItem? {
        val entry = metadataCache[key] ?: return null
        return if (entry.isExpired) {
            metadataCache.remove(key)
            null
        } else {
            entry.data
        }
    }

    fun putMetadata(key: String, item: MediaItem) {
        metadataCache[key] = CacheEntry(item, ttlMillis = TTL_STATIC_METADATA)
    }

    // --- MAL Fallback Cache ---
    fun getMalFallback(key: String): MediaItem? {
        val entry = malFallbackCache[key] ?: return null
        return if (entry.isExpired) {
            malFallbackCache.remove(key)
            null
        } else {
            entry.data
        }
    }

    fun putMalFallback(key: String, item: MediaItem) {
        malFallbackCache[key] = CacheEntry(item, ttlMillis = TTL_MAL_FALLBACK)
    }

    fun getMalFallback(malId: Int, type: String): MediaItem? = getMalFallback(malFallbackKey(malId, type))
    fun putMalFallback(malId: Int, type: String, item: MediaItem) = putMalFallback(malFallbackKey(malId, type), item)

    // --- Short-lived Tracking Cache & Disk Persistence ---
    private val gson = com.google.gson.Gson()

    fun getTracking(type: String): List<UserMediaItem>? {
        val entry = trackingCache[type] ?: return null
        return if (entry.isExpired) {
            trackingCache.remove(type)
            null
        } else {
            entry.data
        }
    }

    fun putTracking(type: String, items: List<UserMediaItem>) {
        trackingCache[type] = CacheEntry(items, ttlMillis = TTL_TRACKING)
    }

    fun saveTrackingToDisk(context: Context, type: String, items: List<UserMediaItem>) {
        try {
            val file = File(context.filesDir, "cached_tracking_${type.lowercase()}.json")
            file.writeText(gson.toJson(items))
        } catch (_: Exception) {}
    }

    fun loadTrackingFromDisk(context: Context, type: String): List<UserMediaItem>? {
        return try {
            val file = File(context.filesDir, "cached_tracking_${type.lowercase()}.json")
            if (file.exists()) {
                val json = file.readText()
                val listType = object : com.google.gson.reflect.TypeToken<List<UserMediaItem>>() {}.type
                val items = gson.fromJson<List<UserMediaItem>>(json, listType)
                if (items != null) {
                    putTracking(type, items)
                }
                items
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun updateSingleTrackingItem(item: UserMediaItem) {
        val typeStr = item.type.name
        val current = trackingCache[typeStr]?.data?.toMutableList() ?: return
        val index = current.indexOfFirst { it.id == item.id || it.malId == item.malId }
        if (index >= 0) {
            current[index] = item
        } else {
            current.add(0, item)
        }
        trackingCache[typeStr] = CacheEntry(current, ttlMillis = TTL_TRACKING)
    }

    fun removeSingleTrackingItem(id: String, type: String) {
        val current = trackingCache[type]?.data?.toMutableList() ?: return
        current.removeAll { it.id == id || it.malId?.toString() == id }
        trackingCache[type] = CacheEntry(current, ttlMillis = TTL_TRACKING)
    }

    fun getTrackingItem(id: String, type: String): UserMediaItem? {
        return try {
            val list = trackingCache[type]?.data
            if (list != null && !trackingCache[type]!!.isExpired) {
                list.firstOrNull { it.id == id || it.malId?.toString() == id }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun invalidateTracking(type: String? = null) {
        if (type != null) {
            trackingCache.remove(type)
        } else {
            trackingCache.clear()
        }
    }

    // --- Cast & Crew Profile Cache ---
    private val castCrewCache = createLruMap<String, CacheEntry<com.canim.app.data.model.CastCrewProfile>>(100)

    fun castCrewKey(id: Int, isStaff: Boolean): String = "castcrew_${if (isStaff) "staff" else "char"}_$id"

    fun getCastCrewProfile(id: Int, isStaff: Boolean): com.canim.app.data.model.CastCrewProfile? {
        val key = castCrewKey(id, isStaff)
        val entry = castCrewCache[key] ?: return null
        return if (entry.isExpired) {
            castCrewCache.remove(key)
            null
        } else {
            entry.data
        }
    }

    fun putCastCrewProfile(id: Int, isStaff: Boolean, profile: com.canim.app.data.model.CastCrewProfile) {
        castCrewCache[castCrewKey(id, isStaff)] = CacheEntry(profile, ttlMillis = TTL_DETAIL)
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

    // --- Invalidation on User Tracking Changes (Section 14) ---
    fun invalidateMedia(anilistId: Int?, malId: Int?) {
        val keysToRemove = mutableSetOf<String>()
        if (anilistId != null && malId != null) {
            keysToRemove.add(detailKey(anilistId, malId))
        }
        if (anilistId != null) {
            keysToRemove.add(detailKey(anilistId, null))
            detailCache.keys.filter { it.contains("ani_$anilistId") }.forEach { keysToRemove.add(it) }
        }
        if (malId != null) {
            keysToRemove.add(detailKey(null, malId))
            detailCache.keys.filter { it.contains("mal_$malId") }.forEach { keysToRemove.add(it) }
            malFallbackCache.remove(malFallbackKey(malId, "ANIME"))
            malFallbackCache.remove(malFallbackKey(malId, "MANGA"))
        }
        keysToRemove.forEach { detailCache.remove(it) }

        // Also invalidate tracking list cache upon media mutation
        invalidateTracking()
    }

    // --- Asynchronous Expiration Cleanup ---
    suspend fun pruneExpired() = withContext(Dispatchers.IO) {
        searchCache.entries.removeIf { it.value.isExpired }
        discoverCache.entries.removeIf { it.value.isExpired }
        detailCache.entries.removeIf { it.value.isExpired }
        metadataCache.entries.removeIf { it.value.isExpired }
        malFallbackCache.entries.removeIf { it.value.isExpired }
        negativeCache.entries.removeIf { it.value.isExpired }
        trackingCache.entries.removeIf { it.value.isExpired }
        castCrewCache.entries.removeIf { it.value.isExpired }
        studioCache.entries.removeIf { it.value.isExpired }
    }

    // --- Manual Cache Clear ---
    fun clearIdMappings() {
        idMappingMalToAniList.clear()
        idMappingAniListToMal.clear()
    }

    fun clearMetadataCache() {
        metadataCache.clear()
        searchCache.clear()
        discoverCache.clear()
        detailCache.clear()
        castCrewCache.clear()
        malFallbackCache.clear()
        negativeCache.clear()
        trackingCache.clear()
        studioCache.clear()
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearImageCache(context: Context) = withContext(Dispatchers.IO) {
        try {
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()

            val imageCacheDir = File(context.cacheDir, "canim_image_cache")
            if (imageCacheDir.exists()) {
                imageCacheDir.deleteRecursively()
                imageCacheDir.mkdirs()
            }
        } catch (_: Exception) {}
    }

    suspend fun clearAllCache(context: Context) = withContext(Dispatchers.IO) {
        clearMetadataCache()
        clearImageCache(context)
    }
}
