package com.canim.app.data.repository

import android.util.Log
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.AniListClient
import com.canim.app.data.remote.ApiClient
import com.canim.app.domain.repository.SearchRepository
import com.canim.app.util.LogRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val swrCoordinator: SwrCoordinator
) : SearchRepository {

    override fun searchFilterKey(query: String, genres: List<String>?, year: Int?, format: String?, page: Int): String {
        val trimmed = query.trim()
        val genresKey = if (genres.isNullOrEmpty()) "" else genres.sorted().joinToString(",")
        val baseKey = "${trimmed}_${genresKey}_${year}_${format}"
        return if (page <= 1) baseKey else "${baseKey}_p$page"
    }

    override fun getCachedSearch(filterKey: String, type: String): List<MediaItem>? =
        CacheManager.getSearch(filterKey, type)

    override fun matchesSearchKey(eventKey: String, filterKey: String, type: String): Boolean {
        val searchKey = CacheManager.searchKey(filterKey, type)
        return eventKey == searchKey || eventKey == filterKey ||
               eventKey.removeSuffix("_p1") == searchKey.removeSuffix("_p1") ||
               eventKey.removeSuffix("_p1") == filterKey.removeSuffix("_p1")
    }

    override suspend fun searchAnime(
        query: String,
        genres: List<String>?,
        year: Int?,
        format: String?,
        page: Int,
        forceRefresh: Boolean
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val filterKey = searchFilterKey(trimmed, genres, year, format, page)
        if (trimmed.isEmpty() && genres.isNullOrEmpty() && year == null && format == null) return@withContext emptyList()

        if (!forceRefresh) {
            val swrHit = CacheManager.getSearchSwr(filterKey, "ANIME")
            if (swrHit != null) {
                if (swrHit.isStale && page == 1) {
                    val cacheKey = CacheManager.searchKey(filterKey, "ANIME")
                    swrCoordinator.launchSwrJob(cacheKey) {
                        runCatching {
                            val fresh = AniListClient.searchMedia(
                                trimmed, MediaType.ANIME, genres, year, format, page = 1, forceRefresh = true
                            )
                            if (fresh.isNotEmpty()) {
                                CacheManager.putSearch(filterKey, "ANIME", fresh)
                                if (fresh != swrHit.data) {
                                    swrCoordinator.emitRefreshEvent(cacheKey, CacheRefreshType.SEARCH)
                                }
                            }
                        }
                    }
                }
                return@withContext swrHit.data
            }
        }

        AniListClient.deduplicateInFlight("search_anime_$filterKey") {
            var result = if (!ApiClient.aniListLimiter.isCooldownActive()) {
                runCatching {
                    AniListClient.searchMedia(
                        trimmed, MediaType.ANIME, genres, year, format, page = page, forceRefresh = forceRefresh
                    )
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val hasExplicitFilters = !genres.isNullOrEmpty() || year != null || !format.isNullOrBlank()
            val pageSize = 30
            val offset = (page - 1) * pageSize

            if (result.isEmpty()) {
                try {
                    val malItems = if (trimmed.isNotBlank()) {
                        val malResp = ApiClient.malApi.searchAnime(MalAuthManager.CLIENT_ID, trimmed, limit = pageSize, offset = offset)
                        if (malResp.isSuccessful && malResp.body()?.data?.isNotEmpty() == true) {
                            malResp.body()!!.data.map { MediaMappingUtils.mapMalAnimeNodeToMediaItem(it.node) }
                        } else emptyList()
                    } else if (hasExplicitFilters) {
                        val malRankingType = when {
                            format.equals("MOVIE", ignoreCase = true) -> "movie"
                            format.equals("TV", ignoreCase = true) -> "tv"
                            format.equals("OVA", ignoreCase = true) -> "ova"
                            format.equals("SPECIAL", ignoreCase = true) -> "special"
                            else -> "bypopularity"
                        }
                        val malResp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, malRankingType, limit = pageSize, offset = offset)
                        if (malResp.isSuccessful && malResp.body()?.data?.isNotEmpty() == true) {
                            malResp.body()!!.data.map { MediaMappingUtils.mapMalAnimeNodeToMediaItem(it.node) }
                        } else emptyList()
                    } else emptyList()

                    if (malItems.isNotEmpty()) {
                        result = malItems
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("SearchRepository", "MAL search anime fallback failed: ${LogRedactor.redact(e.message ?: "")}")
                }
            }

            if (result.isEmpty() && page == 1) {
                val baseList = MediaMappingUtils.fallbackAnime()
                var localMatches = baseList
                if (trimmed.isNotBlank()) {
                    localMatches = localMatches.filter {
                        it.title.contains(trimmed, ignoreCase = true) ||
                        (it.titleEnglish?.contains(trimmed, ignoreCase = true) == true)
                    }
                }
                if (!genres.isNullOrEmpty()) {
                    localMatches = localMatches.filter { item ->
                        item.genres.any { g -> genres.any { sel -> g.contains(sel, ignoreCase = true) } }
                    }
                }
                if (year != null) {
                    localMatches = localMatches.filter { item -> item.year == year }
                }
                if (!format.isNullOrBlank()) {
                    localMatches = localMatches.filter { item -> item.format.equals(format, ignoreCase = true) }
                }
                if (localMatches.isNotEmpty() && (!hasExplicitFilters || localMatches != baseList)) {
                    result = localMatches
                }
            }

            if (result.isNotEmpty()) {
                CacheManager.putSearch(filterKey, "ANIME", result)
            }
            result
        }
    }

    override suspend fun searchManga(
        query: String,
        genres: List<String>?,
        year: Int?,
        format: String?,
        page: Int,
        forceRefresh: Boolean
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val filterKey = searchFilterKey(trimmed, genres, year, format, page)
        if (trimmed.isEmpty() && genres.isNullOrEmpty() && year == null && format == null) return@withContext emptyList()

        if (!forceRefresh) {
            val swrHit = CacheManager.getSearchSwr(filterKey, "MANGA")
            if (swrHit != null) {
                if (swrHit.isStale && page == 1) {
                    val cacheKey = CacheManager.searchKey(filterKey, "MANGA")
                    swrCoordinator.launchSwrJob(cacheKey) {
                        runCatching {
                            val fresh = AniListClient.searchMedia(
                                trimmed, MediaType.MANGA, genres, year, format, page = 1, forceRefresh = true
                            )
                            if (fresh.isNotEmpty()) {
                                CacheManager.putSearch(filterKey, "MANGA", fresh)
                                if (fresh != swrHit.data) {
                                    swrCoordinator.emitRefreshEvent(cacheKey, CacheRefreshType.SEARCH)
                                }
                            }
                        }
                    }
                }
                return@withContext swrHit.data
            }
        }

        AniListClient.deduplicateInFlight("search_manga_$filterKey") {
            var result = if (!ApiClient.aniListLimiter.isCooldownActive()) {
                runCatching {
                    AniListClient.searchMedia(
                        trimmed, MediaType.MANGA, genres, year, format, page = page, forceRefresh = forceRefresh
                    )
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val hasExplicitFilters = !genres.isNullOrEmpty() || year != null || !format.isNullOrBlank()
            val pageSize = 30
            val offset = (page - 1) * pageSize

            if (result.isEmpty()) {
                try {
                    val malItems = if (trimmed.isNotBlank()) {
                        val malResp = ApiClient.malApi.searchManga(MalAuthManager.CLIENT_ID, trimmed, limit = pageSize, offset = offset)
                        if (malResp.isSuccessful && malResp.body()?.data?.isNotEmpty() == true) {
                            malResp.body()!!.data.map { MediaMappingUtils.mapMalMangaNodeToMediaItem(it.node) }
                        } else emptyList()
                    } else if (hasExplicitFilters) {
                        val malRankingType = when {
                            format.equals("NOVEL", ignoreCase = true) -> "novels"
                            format.equals("ONE_SHOT", ignoreCase = true) -> "oneshots"
                            else -> "bypopularity"
                        }
                        val malResp = ApiClient.malApi.getMangaRanking(MalAuthManager.CLIENT_ID, malRankingType, limit = pageSize, offset = offset)
                        if (malResp.isSuccessful && malResp.body()?.data?.isNotEmpty() == true) {
                            malResp.body()!!.data.map { MediaMappingUtils.mapMalMangaNodeToMediaItem(it.node) }
                        } else emptyList()
                    } else emptyList()

                    if (malItems.isNotEmpty()) {
                        result = malItems
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("SearchRepository", "MAL search manga fallback failed: ${LogRedactor.redact(e.message ?: "")}")
                }
            }

            if (result.isEmpty() && page == 1) {
                val baseList = MediaMappingUtils.fallbackManga()
                var localMatches = baseList
                if (trimmed.isNotBlank()) {
                    localMatches = localMatches.filter {
                        it.title.contains(trimmed, ignoreCase = true) ||
                        (it.titleEnglish?.contains(trimmed, ignoreCase = true) == true)
                    }
                }
                if (!genres.isNullOrEmpty()) {
                    localMatches = localMatches.filter { item ->
                        item.genres.any { g -> genres.any { sel -> g.contains(sel, ignoreCase = true) } }
                    }
                }
                if (year != null) {
                    localMatches = localMatches.filter { item -> item.year == year }
                }
                if (!format.isNullOrBlank()) {
                    localMatches = localMatches.filter { item -> item.format.equals(format, ignoreCase = true) }
                }
                if (localMatches.isNotEmpty() && (!hasExplicitFilters || localMatches != baseList)) {
                    result = localMatches
                }
            }

            if (result.isNotEmpty()) {
                CacheManager.putSearch(filterKey, "MANGA", result)
            }
            result
        }
    }
}
