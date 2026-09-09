package com.canim.app.domain.usecase

import com.canim.app.data.model.MalFetchResult
import com.canim.app.data.model.UserMediaItem
import com.canim.app.domain.repository.LibraryRepository
import javax.inject.Inject

class GetLibraryUseCase @Inject constructor(
    private val repository: LibraryRepository
) {
    suspend fun getUserAnimeList(forceRefresh: Boolean = false): MalFetchResult<List<UserMediaItem>> =
        repository.getUserAnimeList(forceRefresh)

    suspend fun getUserMangaList(forceRefresh: Boolean = false): MalFetchResult<List<UserMediaItem>> =
        repository.getUserMangaList(forceRefresh)

    fun getLastSyncedTime(): Long = repository.getLastSyncedTime()

    fun getCachedTracking(type: String): List<UserMediaItem>? = repository.getCachedTracking(type)

    fun getDemoAnime(): List<UserMediaItem> = repository.getDemoAnime()

    fun getDemoManga(): List<UserMediaItem> = repository.getDemoManga()

    suspend operator fun invoke(forceRefresh: Boolean = false): Pair<MalFetchResult<List<UserMediaItem>>, MalFetchResult<List<UserMediaItem>>> =
        Pair(repository.getUserAnimeList(forceRefresh), repository.getUserMangaList(forceRefresh))
}
