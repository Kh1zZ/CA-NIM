package com.canim.app.domain.usecase

import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.UserMediaItem
import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class SaveLibraryItemUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    suspend fun saveAnime(malId: Int, tracking: MalTracking): Result<Unit> =
        repository.updateAnimeTracking(malId, tracking)

    suspend fun saveManga(malId: Int, tracking: MalTracking): Result<Unit> =
        repository.updateMangaTracking(malId, tracking)

    suspend operator fun invoke(item: UserMediaItem): Result<Unit> {
        val malId = item.malId ?: return Result.failure(IllegalArgumentException("Item has no malId"))
        return if (item.isAnime) {
            repository.updateAnimeTracking(malId, item.tracking)
        } else {
            repository.updateMangaTracking(malId, item.tracking)
        }
    }
}
