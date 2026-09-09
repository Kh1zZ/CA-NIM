package com.canim.app.data.local

import com.canim.app.data.model.MalTracking

/**
 * Abstraction for dispatching tracking mutations to MyAnimeList.
 * Decouples [LibrarySyncEngine] from concrete [com.canim.app.data.repository.MalAuthManager].
 */
interface MalMutationExecutor {
    suspend fun updateAnimeTracking(malId: Int, tracking: MalTracking): Result<Unit>
    suspend fun updateMangaTracking(malId: Int, tracking: MalTracking): Result<Unit>
    suspend fun deleteAnimeTracking(malId: Int): Result<Unit>
    suspend fun deleteMangaTracking(malId: Int): Result<Unit>
}
