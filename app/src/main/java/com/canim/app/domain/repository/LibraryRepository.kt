package com.canim.app.domain.repository

import com.canim.app.data.model.MalFetchResult
import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.UserMediaItem

interface LibraryRepository {
    suspend fun getUserAnimeList(forceRefresh: Boolean = false): MalFetchResult<List<UserMediaItem>>
    suspend fun getUserMangaList(forceRefresh: Boolean = false): MalFetchResult<List<UserMediaItem>>
    fun getLastSyncedTime(): Long
    fun getCachedTracking(type: String): List<UserMediaItem>?
    fun getDemoAnime(): List<UserMediaItem>
    fun getDemoManga(): List<UserMediaItem>
    suspend fun updateAnimeTracking(malId: Int, tracking: MalTracking): Result<Unit>
    suspend fun updateMangaTracking(malId: Int, tracking: MalTracking): Result<Unit>
    suspend fun deleteAnimeTracking(malId: Int): Result<Unit>
    suspend fun deleteMangaTracking(malId: Int): Result<Unit>
    suspend fun retryFailedMutations(mediaType: String? = null): Int
}
