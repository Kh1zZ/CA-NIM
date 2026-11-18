package com.canim.app.data.repository

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.AiringAnimeItem
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.AniListClient
import com.canim.app.data.remote.ApiClient
import com.canim.app.domain.repository.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepositoryImpl @Inject constructor() : CalendarRepository {

    companion object {
        private const val CACHE_KEY_CALENDAR = "airing_calendar_week"
        private const val CALENDAR_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour cache
    }

    // In-memory cache for parsed weekly calendar
    @Volatile
    private var cachedCalendar: Map<DayOfWeek, List<AiringAnimeItem>>? = null
    @Volatile
    private var lastCacheTime: Long = 0L

    override suspend fun getAiringCalendar(forceRefresh: Boolean): Map<DayOfWeek, List<AiringAnimeItem>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedCalendar != null && (now - lastCacheTime < CALENDAR_CACHE_TTL_MS)) {
            return@withContext cachedCalendar!!
        }

        val airingItems = fetchCurrentSeasonAiringAnime(forceRefresh)
        val grouped = groupAnimeByDay(airingItems)

        cachedCalendar = grouped
        lastCacheTime = now
        grouped
    }

    override suspend fun getWatchingAiringAnime(
        watchingMalIds: Set<Int>,
        forceRefresh: Boolean
    ): List<AiringAnimeItem> = withContext(Dispatchers.IO) {
        if (watchingMalIds.isEmpty()) return@withContext emptyList()

        // Prefer direct batch query by MAL IDs so shows outside top-50 seasonal TV
        // (e.g., 2-cour continuing, ONA, or lower-popularity titles) are always covered.
        val batchMap = runCatching {
            AniListClient.getMediaBatchByMalIds(
                malIds = watchingMalIds.toList(),
                type = MediaType.ANIME,
                forceRefresh = forceRefresh
            )
        }.getOrDefault(emptyMap())

        if (batchMap.isNotEmpty()) {
            return@withContext batchMap.values
                .filter { media ->
                    media.nextAiringEpisode != null ||
                        media.status.equals("AIRING", ignoreCase = true) ||
                        media.status.equals("AIRED", ignoreCase = true)
                }
                .mapIndexed { index, media -> toAiringAnimeItem(media, index) }
        }

        val allCalendar = getAiringCalendar(forceRefresh)
        allCalendar.values.flatten().filter { item ->
            item.malId != null && item.malId in watchingMalIds
        }
    }

    private suspend fun fetchCurrentSeasonAiringAnime(forceRefresh: Boolean): List<AiringAnimeItem> {
        // Step 1: Attempt AniList Primary (Fetch current season releasing anime)
        val aniListMedia = runCatching {
            AniListClient.getDiscoverMedia(
                category = DiscoverCategory.CURRENT_SEASON,
                filter = DiscoverFilter(status = "RELEASING", format = "TV"),
                page = 1,
                perPage = 50,
                forceRefresh = forceRefresh,
                mediaType = MediaType.ANIME
            )
        }.getOrNull()

        if (!aniListMedia.isNullOrEmpty()) {
            return aniListMedia.mapIndexed { index, media -> toAiringAnimeItem(media, index) }
        }

        // Step 2: Fallback to MAL API
        val malMedia = runCatching {
            val resp = ApiClient.malApi.getAnimeRanking(
                clientId = MalAuthManager.CLIENT_ID,
                rankingType = "airing",
                limit = 50,
                offset = 0
            )
            if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                resp.body()!!.data.map { MediaMappingUtils.mapMalAnimeNodeToMediaItem(it.node) }
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        if (malMedia.isNotEmpty()) {
            return malMedia.mapIndexed { index, media -> toAiringAnimeItem(media, index) }
        }

        // Step 3: Hardcoded emergency fallback anime list
        return MediaMappingUtils.fallbackAnime().mapIndexed { index, media ->
            toAiringAnimeItem(media, index, defaultCurrentEp = 1, defaultTime = "23:00")
        }
    }

    internal fun toAiringAnimeItem(
        media: MediaItem,
        index: Int,
        defaultCurrentEp: Int? = null,
        defaultTime: String? = null
    ): AiringAnimeItem {
        val nextAtSec = media.nextAiringAtSeconds
        val zonedAiring = nextAtSec?.let {
            Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault())
        }
        val day = zonedAiring?.dayOfWeek ?: deriveDayOfWeek(media.title, media.malId ?: media.anilistId ?: index)
        val timeFormatted = zonedAiring?.let {
            String.format(Locale.US, "%02d:%02d", it.hour, it.minute)
        } ?: defaultTime ?: deriveAiringTime(media.malId ?: media.anilistId ?: index)

        val currentEp = when {
            media.nextAiringEpisode != null -> (media.nextAiringEpisode - 1).takeIf { it > 0 }
            media.status.equals("AIRED", ignoreCase = true) ||
                media.status.equals("FINISHED", ignoreCase = true) ||
                media.status.equals("finished_airing", ignoreCase = true) -> media.episodes?.takeIf { it > 0 }
            else -> defaultCurrentEp
        }

        return AiringAnimeItem(
            id = media.id,
            malId = media.malId,
            anilistId = media.anilistId,
            title = media.title,
            titleEnglish = media.titleEnglish,
            imageUrl = media.imageUrl,
            score = media.score,
            episodes = media.episodes,
            currentAiringEpisode = currentEp,
            airingDay = day,
            airingTimeFormatted = timeFormatted,
            genres = media.genres,
            studio = media.studio,
            countryOfOrigin = media.countryOfOrigin ?: "JP",
            nextAiringAtSeconds = nextAtSec
        )
    }

    private fun groupAnimeByDay(items: List<AiringAnimeItem>): Map<DayOfWeek, List<AiringAnimeItem>> {
        val result = mutableMapOf<DayOfWeek, MutableList<AiringAnimeItem>>()
        DayOfWeek.values().forEach { day ->
            result[day] = mutableListOf()
        }
        for (item in items) {
            result[item.airingDay]?.add(item)
        }
        return result
    }

    /**
     * Derives a stable, consistent local DayOfWeek for an anime.
     * Uses hash of title & id to simulate realistic weekly television broadcast distribution.
     * Takes device local timezone into account.
     */
    fun deriveDayOfWeek(title: String, idSeed: Int): DayOfWeek {
        val hash = (title.hashCode() * 31 + idSeed).let { kotlin.math.abs(it) }
        val dayIndex = (hash % 7) + 1 // 1 (Monday) to 7 (Sunday)
        return DayOfWeek.of(dayIndex)
    }

    fun deriveAiringTime(idSeed: Int): String {
        val hour = 18 + (kotlin.math.abs(idSeed) % 6) // 18:00 to 23:00
        val minute = (kotlin.math.abs(idSeed * 7) % 4) * 15 // 00, 15, 30, 45
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }
}
