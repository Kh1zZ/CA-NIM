package com.canim.app.data.repository

import android.util.Log
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.AniListClient
import com.canim.app.data.remote.ApiClient
import com.canim.app.domain.repository.DiscoverRepository
import com.canim.app.util.LogRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscoverRepositoryImpl @Inject constructor(
    private val swrCoordinator: SwrCoordinator
) : DiscoverRepository {

    override fun discoverFilterKey(
        category: DiscoverCategory,
        filter: DiscoverFilter,
        page: Int,
        randomSort: String?,
        mediaType: MediaType?
    ): String {
        val resolvedType = mediaType ?: if (
            filter.format == "MANGA" ||
            category == DiscoverCategory.TOP_MANGA ||
            category == DiscoverCategory.RECENTLY_DONE_MANGA ||
            category == DiscoverCategory.NEWLY_ADDED_MANGA
        ) MediaType.MANGA else MediaType.ANIME
        return "${resolvedType.name.lowercase()}_${category.key}_${filter.genre}_${filter.format}_${filter.year}_${filter.season}_${filter.minScore}_${randomSort}_p$page"
    }

    override fun getCachedDiscover(categoryKey: String): List<MediaItem>? =
        CacheManager.getDiscover(categoryKey)

    override fun matchesDiscoverKey(eventKey: String, categoryKey: String): Boolean {
        val discoverKey = CacheManager.discoverKey(categoryKey)
        return eventKey == discoverKey || eventKey == categoryKey
    }

    override suspend fun getDiscoverMedia(
        category: DiscoverCategory,
        filter: DiscoverFilter,
        page: Int,
        forceRefresh: Boolean,
        randomSort: String?,
        mediaType: MediaType?
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val cacheKey = discoverFilterKey(category, filter, page, randomSort, mediaType)
        if (!forceRefresh) {
            val swrHit = CacheManager.getDiscoverSwr(cacheKey)
            if (swrHit != null) {
                if (swrHit.isStale) {
                    val capCategory = category
                    val capFilter = filter
                    val capPage = page
                    val capRandomSort = randomSort
                    val capMediaType = mediaType
                    val capKey = cacheKey
                    val capStale = swrHit.data
                    val canonicalDiscoverKey = CacheManager.discoverKey(cacheKey)
                    swrCoordinator.launchSwrJob(canonicalDiscoverKey) {
                        runCatching {
                            val fresh = fetchDiscoverInternal(
                                category = capCategory,
                                filter = capFilter,
                                page = capPage,
                                randomSort = capRandomSort,
                                forceRefresh = true,
                                mediaType = capMediaType
                            )
                            if (fresh.isNotEmpty()) {
                                CacheManager.putDiscover(capKey, fresh)
                                if (fresh != capStale) {
                                    swrCoordinator.emitRefreshEvent(canonicalDiscoverKey, CacheRefreshType.DISCOVER)
                                }
                            }
                        }
                    }
                }
                return@withContext swrHit.data
            }
        }

        fetchDiscoverInternal(category, filter, page, randomSort, forceRefresh, mediaType)
    }

    private suspend fun fetchDiscoverInternal(
        category: DiscoverCategory,
        filter: DiscoverFilter,
        page: Int,
        randomSort: String?,
        forceRefresh: Boolean,
        mediaType: MediaType? = null
    ): List<MediaItem> {
        val cacheKey = discoverFilterKey(category, filter, page, randomSort, mediaType)
        val limit = 25
        val offset = (page - 1) * limit

        if (category == DiscoverCategory.TOP_ANIME) {
            try {
                val resp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "all", limit, offset)
                if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                    val malNodes = resp.body()!!.data.map { it.node }
                    val malIds = malNodes.map { it.id }.distinct()
                    val aniMap = runCatching { AniListClient.getMediaBatchByMalIds(malIds, MediaType.ANIME) }.getOrDefault(emptyMap())
                    val items = malNodes.map { node ->
                        val ani = aniMap[node.id]
                        if (ani != null) {
                            ani.copy(
                                score = node.mean ?: ani.score,
                                imageUrl = ani.imageUrl.ifBlank { node.mainPicture?.large ?: node.mainPicture?.medium ?: "" },
                                synopsis = ani.synopsis?.ifBlank { node.synopsis ?: "" } ?: (node.synopsis ?: "")
                            )
                        } else {
                            MediaMappingUtils.mapMalAnimeNodeToMediaItem(node)
                        }
                    }
                    CacheManager.putDiscover(cacheKey, items)
                    return items
                }
            } catch (_: Exception) {}
        } else if (category == DiscoverCategory.TOP_MANGA) {
            try {
                val resp = ApiClient.malApi.getMangaRanking(MalAuthManager.CLIENT_ID, "all", limit, offset)
                if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                    val malNodes = resp.body()!!.data.map { it.node }
                    val malIds = malNodes.map { it.id }.distinct()
                    val aniMap = runCatching { AniListClient.getMediaBatchByMalIds(malIds, MediaType.MANGA) }.getOrDefault(emptyMap())
                    val items = malNodes.map { node ->
                        val ani = aniMap[node.id]
                        if (ani != null) {
                            ani.copy(
                                score = node.mean ?: ani.score,
                                imageUrl = ani.imageUrl.ifBlank { node.mainPicture?.large ?: node.mainPicture?.medium ?: "" },
                                synopsis = ani.synopsis?.ifBlank { node.synopsis ?: "" } ?: (node.synopsis ?: "")
                            )
                        } else {
                            MediaMappingUtils.mapMalMangaNodeToMediaItem(node)
                        }
                    }
                    CacheManager.putDiscover(cacheKey, items)
                    return items
                }
            } catch (_: Exception) {}
        }

        var results = runCatching {
            AniListClient.getDiscoverMedia(
                category = category,
                filter = filter,
                page = page,
                randomSort = randomSort,
                forceRefresh = forceRefresh,
                mediaType = mediaType
            )
        }.getOrDefault(emptyList())

        if (results.isEmpty()) {
            try {
                when (category) {
                    DiscoverCategory.CURRENT_SEASON -> {
                        val resp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "airing", limit, offset)
                        if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                            results = resp.body()!!.data.map { MediaMappingUtils.mapMalAnimeNodeToMediaItem(it.node) }
                        }
                    }
                    DiscoverCategory.NEXT_SEASON, DiscoverCategory.UPCOMING, DiscoverCategory.TBA -> {
                        val resp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "upcoming", limit, offset)
                        if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                            results = resp.body()!!.data.map { MediaMappingUtils.mapMalAnimeNodeToMediaItem(it.node) }
                        }
                    }
                    DiscoverCategory.TRENDING_NOW,
                    DiscoverCategory.RECENTLY_DONE_MANGA,
                    DiscoverCategory.NEWLY_ADDED_MANGA -> {
                        // Exclusively require AniList engine
                    }
                    else -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("DiscoverRepository", "MAL discover fallback failed: ${LogRedactor.redact(e.message ?: "")}")
            }
        }

        if (category != DiscoverCategory.TRENDING_NOW &&
            category != DiscoverCategory.RECENTLY_DONE_MANGA &&
            category != DiscoverCategory.NEWLY_ADDED_MANGA &&
            results.isEmpty() && page == 1) {
            results = if (filter.format == "MANGA") MediaMappingUtils.fallbackManga() else MediaMappingUtils.fallbackAnime()
        }

        if (results.isNotEmpty()) {
            CacheManager.putDiscover(cacheKey, results)
        }

        return results
    }
}
