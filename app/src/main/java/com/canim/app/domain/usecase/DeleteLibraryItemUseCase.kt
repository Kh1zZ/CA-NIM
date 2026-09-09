package com.canim.app.domain.usecase

import com.canim.app.domain.repository.LibraryRepository
import javax.inject.Inject

class DeleteLibraryItemUseCase @Inject constructor(
    private val repository: LibraryRepository
) {
    suspend fun deleteAnime(malId: Int): Result<Unit> =
        repository.deleteAnimeTracking(malId)

    suspend fun deleteManga(malId: Int): Result<Unit> =
        repository.deleteMangaTracking(malId)

    suspend operator fun invoke(malId: Int, isAnime: Boolean): Result<Unit> =
        if (isAnime) repository.deleteAnimeTracking(malId) else repository.deleteMangaTracking(malId)
}
