package com.canim.app.domain.usecase

import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.domain.repository.DiscoverRepository
import com.canim.app.domain.repository.LibraryRepository
import javax.inject.Inject

class LoadFlashcardDeckUseCase @Inject constructor(
    private val discoverRepository: DiscoverRepository,
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(customExcludedIds: Set<Int> = emptySet()): List<MediaItem> {
        val cachedAnime = libraryRepository.getCachedTracking("ANIME") ?: libraryRepository.getDemoAnime()
        val libraryExcludedIds = cachedAnime.mapNotNull { it.malId }.toSet()
        val excludedMalIds = libraryExcludedIds + customExcludedIds

        val currentSeason = discoverRepository.getDiscoverMedia(DiscoverCategory.CURRENT_SEASON, DiscoverFilter(), page = 1)
        val upcoming = discoverRepository.getDiscoverMedia(DiscoverCategory.UPCOMING, DiscoverFilter(), page = 1)

        var rawPool = (currentSeason + upcoming)
            .filter { item ->
                val mId = item.malId
                mId == null || !excludedMalIds.contains(mId)
            }
            .distinctBy { it.malId ?: it.anilistId }

        if (rawPool.isEmpty()) {
            val trending = discoverRepository.getDiscoverMedia(DiscoverCategory.TRENDING_NOW, DiscoverFilter(), page = 1)
            val topAnime = discoverRepository.getDiscoverMedia(DiscoverCategory.TOP_ANIME, DiscoverFilter(), page = 1)
            rawPool = (trending + topAnime)
                .filter { item ->
                    val mId = item.malId
                    mId == null || !excludedMalIds.contains(mId)
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
            }
        }

        return rawPool.shuffled().take(15)
    }
}
