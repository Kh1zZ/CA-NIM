package com.canim.app.domain.usecase

import com.canim.app.domain.repository.LibraryRepository
import javax.inject.Inject

class DeleteLibraryItemUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val getMalUserUseCase: GetMalUserUseCase? = null
) {
    suspend fun deleteAnime(malId: Int?): Result<Unit> {
        val isLoggedIn = getMalUserUseCase?.invoke()?.isLoggedIn != false
        if (malId == null || !isLoggedIn) {
            return Result.success(Unit)
        }
        return repository.deleteAnimeTracking(malId)
    }

    suspend fun deleteManga(malId: Int?): Result<Unit> {
        val isLoggedIn = getMalUserUseCase?.invoke()?.isLoggedIn != false
        if (malId == null || !isLoggedIn) {
            return Result.success(Unit)
        }
        return repository.deleteMangaTracking(malId)
    }

    suspend operator fun invoke(malId: Int?, isAnime: Boolean): Result<Unit> =
        if (isAnime) deleteAnime(malId) else deleteManga(malId)
}
