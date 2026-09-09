package com.canim.app.domain.usecase

import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.UserMediaItem
import com.canim.app.domain.repository.LibraryRepository
import javax.inject.Inject

class UpdateTrackingUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val consumeGachaCreditUseCase: ConsumeGachaCreditUseCase? = null,
    private val getMalUserUseCase: GetMalUserUseCase? = null
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

    fun recordProgressAndAwardCredits(id: String, progress: Int): Int =
        consumeGachaCreditUseCase?.recordProgressAndAwardCredits(id, progress) ?: 0

    suspend fun updateAnime(malId: Int?, tracking: MalTracking, mediaId: String? = null): Result<Int> {
        val awarded = if (mediaId != null) {
            recordProgressAndAwardCredits(mediaId, tracking.progress)
        } else 0

        val isLoggedIn = getMalUserUseCase?.invoke()?.isLoggedIn != false
        if (malId == null || !isLoggedIn) {
            return Result.success(awarded)
        }
        return repository.updateAnimeTracking(malId, tracking).map { awarded }
    }

    suspend fun updateManga(malId: Int?, tracking: MalTracking, mediaId: String? = null): Result<Int> {
        val awarded = if (mediaId != null) {
            recordProgressAndAwardCredits(mediaId, tracking.progress)
        } else 0

        val isLoggedIn = getMalUserUseCase?.invoke()?.isLoggedIn != false
        if (malId == null || !isLoggedIn) {
            return Result.success(awarded)
        }
        return repository.updateMangaTracking(malId, tracking).map { awarded }
    }

    suspend operator fun invoke(malId: Int?, tracking: MalTracking, isAnime: Boolean, mediaId: String? = null): Result<Int> =
        if (isAnime) updateAnime(malId, tracking, mediaId) else updateManga(malId, tracking, mediaId)
}
