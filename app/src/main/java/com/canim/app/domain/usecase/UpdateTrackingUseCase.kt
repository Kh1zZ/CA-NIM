package com.canim.app.domain.usecase

import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.UserMediaItem
import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class UpdateTrackingUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    /**
     * Business rule: if new status is "completed" and total episodes/chapters > 0,
     * progress is automatically set to the total episodes/chapters.
     * Otherwise, progress remains unchanged.
     */
    fun applyStatusChange(item: UserMediaItem, newStatus: String): UserMediaItem {
        val maxP = if (item.isAnime) item.totalEpisodes else item.totalChapters
        val newProgress = if (newStatus == "completed" && maxP > 0) maxP else item.tracking.progress
        return item.copy(
            tracking = item.tracking.copy(
                status = newStatus,
                progress = newProgress,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateAnime(malId: Int, tracking: MalTracking): Result<Unit> =
        repository.updateAnimeTracking(malId, tracking)

    suspend fun updateManga(malId: Int, tracking: MalTracking): Result<Unit> =
        repository.updateMangaTracking(malId, tracking)

    suspend operator fun invoke(malId: Int, tracking: MalTracking, isAnime: Boolean): Result<Unit> =
        if (isAnime) repository.updateAnimeTracking(malId, tracking) else repository.updateMangaTracking(malId, tracking)
}
