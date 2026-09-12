package com.canim.app.domain.usecase

import com.canim.app.data.local.GachaCandidateStore
import com.canim.app.data.local.GachaCooldownManager
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
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
        val cachedAnime = libraryRepository.getCachedTracking("ANIME") ?: libraryRepository.getDemoAnime()
        val libraryExcludedIds = cachedAnime.mapNotNull { it.malId }.toSet()

        // 2. 14-Day Cooldown Exclusions
        val cooldownExcludedIds = cooldownManager?.getCooldownMalIds() ?: emptySet()

        val allExcludedMalIds = libraryExcludedIds + cooldownExcludedIds + customExcludedIds

        // 3. Derive Dynamic Genre Tendencies from user's current library (Adapts continuously, no hardcoded table)
        val genreTendencies = AdaptivePreferenceModel.deriveTendencies(cachedAnime)

        // 4. Candidate Pool Acquisition (Lightweight tokens: MAL ID + genres)
        val candidateTokens = candidateStore?.getAllCandidates()?.filter { token ->
            token.malId > 0 && !allExcludedMalIds.contains(token.malId)
        }?.toMutableList() ?: mutableListOf()

        // If candidate store is low, opportunistically expand
        if (candidateTokens.size < 15 && expandCandidatesUseCase != null) {
            expandCandidatesUseCase(forceExpand = false)
            candidateStore?.getAllCandidates()?.forEach { token ->
                if (token.malId > 0 && !allExcludedMalIds.contains(token.malId) && candidateTokens.none { it.malId == token.malId }) {
                    candidateTokens.add(token)
                }
            }
        }

        // 5. If candidates are available, sort by affinity score
        val selectedTokens = if (candidateTokens.isNotEmpty()) {
            candidateTokens.shuffled().sortedByDescending { token ->
                AdaptivePreferenceModel.calculateAffinityScore(token.genres, genreTendencies)
            }.take(15)
        } else {
            emptyList()
        }

        // 6. Selected Candidate Resolution: Fetch full details only after selection (MAL first, AniList fallback)
        val resolvedMediaItems = mutableListOf<MediaItem>()
        for (token in selectedTokens) {
            val item = resolveCandidateDetails(token.malId, token.genres)
            if (item != null) {
                resolvedMediaItems.add(item)
            }
        }

        if (resolvedMediaItems.isNotEmpty()) {
            return resolvedMediaItems
        }

        // 7. Fallback to Discover repository (Current Season + Upcoming) if candidate store is not yet populated
        val currentSeason = discoverRepository.getDiscoverMedia(DiscoverCategory.CURRENT_SEASON, DiscoverFilter(), page = 1)
        val upcoming = discoverRepository.getDiscoverMedia(DiscoverCategory.UPCOMING, DiscoverFilter(), page = 1)

        var rawPool = (currentSeason + upcoming)
            .filter { item ->
                val mId = item.malId
                mId == null || !allExcludedMalIds.contains(mId)
            }
            .distinctBy { it.malId ?: it.anilistId }

        if (rawPool.isEmpty()) {
            val trending = discoverRepository.getDiscoverMedia(DiscoverCategory.TRENDING_NOW, DiscoverFilter(), page = 1)
            val topAnime = discoverRepository.getDiscoverMedia(DiscoverCategory.TOP_ANIME, DiscoverFilter(), page = 1)
            rawPool = (trending + topAnime)
                .filter { item ->
                    val mId = item.malId
                    mId == null || !allExcludedMalIds.contains(mId)
                }
                .distinctBy { it.malId ?: it.anilistId }
        }

        if (rawPool.isEmpty()) {
            rawPool = libraryRepository.getDemoAnime().map { demo ->
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
            }.filter { item ->
                val mId = item.malId
                mId == null || !allExcludedMalIds.contains(mId)
            }
        }

        // Apply Adaptive Preference scoring to rank Discover pool
        return rawPool
            .shuffled()
            .sortedByDescending { item ->
                AdaptivePreferenceModel.calculateAffinityScore(item.genres, genreTendencies)
            }
            .take(15)
    }

    private suspend fun resolveCandidateDetails(malId: Int, genres: List<String>): MediaItem? {
        if (detailRepository != null) {
            // Attempt full detail resolution (MAL primary with AniList fallback in DetailRepository)
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

        // Fallback from static fallbacks if available
        val matchedFallback = MediaMappingUtils.fallbackAnime().find { it.malId == malId }
        return matchedFallback
    }
}

