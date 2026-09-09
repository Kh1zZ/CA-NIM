package com.canim.app.domain.usecase

import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class LoadFlashcardDeckUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    suspend operator fun invoke(excludedMalIds: Set<Int> = emptySet()): List<MediaItem> {
        val currentSeason = repository.getDiscoverMedia(DiscoverCategory.CURRENT_SEASON, DiscoverFilter(), page = 1)
        val upcoming = repository.getDiscoverMedia(DiscoverCategory.UPCOMING, DiscoverFilter(), page = 1)

        var rawPool = (currentSeason + upcoming)
            .filter { item ->
                val mId = item.malId
                mId == null || !excludedMalIds.contains(mId)
            }
            .distinctBy { it.malId ?: it.anilistId }

        if (rawPool.isEmpty()) {
            val trending = repository.getDiscoverMedia(DiscoverCategory.TRENDING_NOW, DiscoverFilter(), page = 1)
            val topAnime = repository.getDiscoverMedia(DiscoverCategory.TOP_ANIME, DiscoverFilter(), page = 1)
            rawPool = (trending + topAnime)
                .filter { item ->
                    val mId = item.malId
                    mId == null || !excludedMalIds.contains(mId)
                }
                .distinctBy { it.malId ?: it.anilistId }
        }

        if (rawPool.isEmpty()) {
            rawPool = repository.getDemoAnime().map { demo ->
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
