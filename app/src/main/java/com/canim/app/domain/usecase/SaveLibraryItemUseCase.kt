package com.canim.app.domain.usecase

import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.UserMediaItem
import com.canim.app.domain.repository.LibraryRepository
import javax.inject.Inject

class SaveLibraryItemUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val consumeGachaCreditUseCase: ConsumeGachaCreditUseCase? = null,
    private val getMalUserUseCase: GetMalUserUseCase? = null
) {
    fun recordProgressAndAwardCredits(id: String, progress: Int): Int =
        consumeGachaCreditUseCase?.recordProgressAndAwardCredits(id, progress) ?: 0

    suspend fun saveAnime(malId: Int?, tracking: MalTracking, mediaId: String? = null): Result<Int> {
        val awarded = if (mediaId != null) {
            recordProgressAndAwardCredits(mediaId, tracking.progress)
        } else 0
        val isLoggedIn = getMalUserUseCase?.invoke()?.isLoggedIn != false
        if (malId == null || !isLoggedIn) {
            return Result.success(awarded)
        }
        return repository.updateAnimeTracking(malId, tracking).map { awarded }
    }

    suspend fun saveManga(malId: Int?, tracking: MalTracking, mediaId: String? = null): Result<Int> {
        val awarded = if (mediaId != null) {
            recordProgressAndAwardCredits(mediaId, tracking.progress)
        } else 0
        val isLoggedIn = getMalUserUseCase?.invoke()?.isLoggedIn != false
        if (malId == null || !isLoggedIn) {
            return Result.success(awarded)
        }
        return repository.updateMangaTracking(malId, tracking).map { awarded }
    }

    suspend operator fun invoke(item: UserMediaItem): Result<Int> {
        val awarded = recordProgressAndAwardCredits(item.id, item.tracking.progress)
        val malId = item.malId
        val isLoggedIn = getMalUserUseCase?.invoke()?.isLoggedIn != false
        if (malId == null || !isLoggedIn) {
            return Result.success(awarded)
        }
        val remoteResult = if (item.isAnime) {
            repository.updateAnimeTracking(malId, item.tracking)
        } else {
            repository.updateMangaTracking(malId, item.tracking)
        }
        return remoteResult.map { awarded }
    }
}
