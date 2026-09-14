package com.canim.app.domain.usecase

import com.canim.app.data.local.GachaCandidateStore
import com.canim.app.data.local.GachaCooldownManager
import com.canim.app.data.model.*
import com.canim.app.data.repository.MediaMappingUtils
import com.canim.app.domain.gacha.AdaptivePreferenceModel
import com.canim.app.domain.repository.DetailRepository
import com.canim.app.domain.repository.DiscoverRepository
import com.canim.app.domain.repository.LibraryRepository
import javax.inject.Inject

class LoadFlashcardDeckUseCase @Inject constructor(
    private val discoverRepository: DiscoverRepository,
    private val libraryRepository: LibraryRepository,
    private val cooldownManager: GachaCooldownManager? = null,
    private val candidateStore: GachaCandidateStore? = null,
    private val expandCandidatesUseCase: ExpandGachaCandidatesUseCase? = null,
    private val detailRepository: DetailRepository? = null
) {
    suspend operator fun invoke(customExcludedIds: Set<Int> = emptySet()): List<MediaItem> {
        // 1. Library Exclusions (Highest Priority - Never return items already in user library)
        // Read directly from Room DB via getUserAnimeList to avoid cold-start in-memory null cache.
        val userTrackedAnime = try {
            when (val result = libraryRepository.getUserAnimeList(forceRefresh = false)) {
                is MalFetchResult.Success -> result.data.ifEmpty { libraryRepository.getCachedTracking("ANIME") ?: emptyList() }
                is MalFetchResult.Partial -> result.data.ifEmpty { libraryRepository.getCachedTracking("ANIME") ?: emptyList() }
                else -> libraryRepository.getCachedTracking("ANIME") ?: emptyList()
            }
        } catch (_: Exception) {
            libraryRepository.getCachedTracking("ANIME") ?: emptyList()
        }

        val libraryMalIds = userTrackedAnime.mapNotNull { it.malId }.toSet()
        val libraryAniListIds = userTrackedAnime.mapNotNull { it.anilistId }.toSet()

        // 2. 14-Day Cooldown Exclusions (Dual-Engine: MAL ID + AniList ID)
        val cooldownMalIds = cooldownManager?.getCooldownMalIds() ?: emptySet()
        val cooldownAniListIds = cooldownManager?.getCooldownAniListIds() ?: emptySet()

        val allExcludedMalIds = libraryMalIds + cooldownMalIds + customExcludedIds
        val allExcludedAniListIds = libraryAniListIds + cooldownAniListIds

        fun isExcluded(item: MediaItem): Boolean {
            val mId = item.malId
            val aId = item.anilistId
            if (mId != null && mId > 0 && allExcludedMalIds.contains(mId)) return true
            if (aId != null && aId > 0 && allExcludedAniListIds.contains(aId)) return true
            if (cooldownManager != null && cooldownManager.isUnderCooldown(item)) return true
            return false
        }

        // 3. Derive Dynamic Genre Tendencies: use user's tracked library, or bootstrap from demo if library is empty
        val animeForTendencies = userTrackedAnime.ifEmpty { libraryRepository.getDemoAnime() }
        val genreTendencies = AdaptivePreferenceModel.deriveTendencies(animeForTendencies)
        val topGenres = AdaptivePreferenceModel.getTopGenres(genreTendencies, limit = 3)

        // 4. Candidate Pool Acquisition (Targeted Pool Architecture)
        val targetedPool = mutableListOf<MediaItem>()

        // 4a. Candidates from CandidateStore if present
        val candidateTokens = candidateStore?.getAllCandidates()?.filter { token ->
            token.malId > 0 && !allExcludedMalIds.contains(token.malId)
        }?.toMutableList() ?: mutableListOf()

        if (candidateTokens.size < 15 && expandCandidatesUseCase != null) {
            try {
                expandCandidatesUseCase(forceExpand = false)
                candidateStore?.getAllCandidates()?.forEach { token ->
                    if (token.malId > 0 && !allExcludedMalIds.contains(token.malId) && candidateTokens.none { it.malId == token.malId }) {
                        candidateTokens.add(token)
                    }
                }
            } catch (_: Exception) {}
        }

        if (candidateTokens.isNotEmpty()) {
            val selectedTokens = candidateTokens.shuffled().sortedByDescending { token ->
                AdaptivePreferenceModel.calculateAffinityScore(token.genres, genreTendencies)
            }.take(15)

            for (token in selectedTokens) {
                val item = resolveCandidateDetails(token.malId, token.genres)
                if (item != null && !isExcluded(item)) {
                    targetedPool.add(item)
                }
            }
        }

        // 4b. Genre-Targeted Discovery Pool: Fetch top anime matching user's top genres + Trending + Top Anime
        if (targetedPool.size < 15) {
            val genreDiscoveryItems = mutableListOf<MediaItem>()

            // Fetch Top Anime per user's top genres
            for (genre in topGenres) {
                try {
                    val genreMedia = discoverRepository.getDiscoverMedia(
                        category = DiscoverCategory.TOP_ANIME,
                        filter = DiscoverFilter(genre = genre),
                        page = 1
                    )
                    genreDiscoveryItems.addAll(genreMedia)
                } catch (_: Exception) {}
            }

            // Also fetch all-time Top Anime and Trending Now to enrich diversity
            try {
                val topAnime = discoverRepository.getDiscoverMedia(
                    category = DiscoverCategory.TOP_ANIME,
                    filter = DiscoverFilter(),
                    page = 1
                )
                genreDiscoveryItems.addAll(topAnime)
            } catch (_: Exception) {}

            try {
                val trending = discoverRepository.getDiscoverMedia(
                    category = DiscoverCategory.TRENDING_NOW,
                    filter = DiscoverFilter(),
                    page = 1
                )
                genreDiscoveryItems.addAll(trending)
            } catch (_: Exception) {}

            for (item in genreDiscoveryItems) {
                if (!isExcluded(item) && targetedPool.none { it.id == item.id || (it.malId != null && it.malId == item.malId) }) {
                    targetedPool.add(item)
                }
            }
        }

        // 4c. Demo Anime Fallback only if network/data pool is completely dry
        if (targetedPool.isEmpty()) {
            val fallbackDemo = libraryRepository.getDemoAnime().map { demo ->
                MediaItem(
                    malId = demo.malId,
                    anilistId = demo.anilistId,
                    title = demo.title,
                    titleEnglish = demo.metadata.titleEnglish,
                    imageUrl = demo.imageUrl,
                    type = MediaType.ANIME,
                    score = demo.metadata.score,
                    synopsis = demo.synopsis,
                    episodes = demo.totalEpisodes,
                    status = demo.status,
                    year = demo.metadata.year,
                    genres = demo.metadata.genres,
                    studio = demo.studio
                )
            }.filter { !isExcluded(it) }
            targetedPool.addAll(fallbackDemo)
        }

        // 5. Deduplicate and rank candidates using Adaptive Preference Model
        val distinctPool = targetedPool
            .distinctBy { it.malId?.let { m -> "mal_$m" } ?: it.anilistId?.let { a -> "ani_$a" } ?: it.id }

        return distinctPool
            .shuffled()
            .sortedByDescending { item ->
                AdaptivePreferenceModel.calculateAffinityScore(item.genres, genreTendencies)
            }
            .take(15)
    }

    private suspend fun resolveCandidateDetails(malId: Int, genres: List<String>): MediaItem? {
        if (detailRepository != null) {
            val ext = try {
                detailRepository.getMalExtendedDetailFallback(malId, MediaType.ANIME)
                    ?: detailRepository.getExtendedDetails(null, malId, MediaType.ANIME)
            } catch (_: Exception) {
                null
            }

            if (ext != null) {
                return MediaItem(
                    malId = ext.malId ?: malId,
                    anilistId = ext.anilistId,
                    title = ext.title,
                    titleEnglish = ext.titleEnglish,
                    imageUrl = ext.coverImage ?: "",
                    imageUrlHd = ext.bannerImage ?: ext.coverImage,
                    type = MediaType.ANIME,
                    score = ext.malScore ?: ext.averageScore,
                    synopsis = ext.synopsis ?: "",
                    episodes = null,
                    status = ext.airingStatus ?: "FINISHED",
                    year = ext.startDate?.take(4)?.toIntOrNull(),
                    genres = if (ext.genres.isNotEmpty()) ext.genres else genres,
                    format = "TV",
                    studio = ext.studio
                )
            }
        }

        val matchedFallback = MediaMappingUtils.fallbackAnime().find { it.malId == malId }
        return matchedFallback
    }
}

