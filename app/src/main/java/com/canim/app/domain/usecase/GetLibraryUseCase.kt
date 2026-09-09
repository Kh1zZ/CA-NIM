package com.canim.app.domain.usecase

import com.canim.app.data.model.MalFetchResult
import com.canim.app.data.model.UserMediaItem
import com.canim.app.domain.repository.LibraryRepository
import javax.inject.Inject

class GetLibraryUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val consumeGachaCreditUseCase: ConsumeGachaCreditUseCase? = null,
    private val getMalUserUseCase: GetMalUserUseCase? = null
) {
    suspend fun getUserAnimeList(forceRefresh: Boolean = false): MalFetchResult<List<UserMediaItem>> {
        val isLoggedIn = getMalUserUseCase?.invoke()?.isLoggedIn != false
        val result = if (isLoggedIn) {
            repository.getUserAnimeList(forceRefresh)
        } else {
            val demo = repository.getDemoAnime()
            MalFetchResult.Success(demo, demo.size)
        }
        when (result) {
            is MalFetchResult.Success -> result.data.forEach { consumeGachaCreditUseCase?.initBaselineProgress(it.id, it.progress) }
            is MalFetchResult.Partial -> result.data.forEach { consumeGachaCreditUseCase?.initBaselineProgress(it.id, it.progress) }
            is MalFetchResult.Failure -> { /* network failure, no new items */ }
        }
        return result
    }

    suspend fun getUserMangaList(forceRefresh: Boolean = false): MalFetchResult<List<UserMediaItem>> {
        val isLoggedIn = getMalUserUseCase?.invoke()?.isLoggedIn != false
        val result = if (isLoggedIn) {
            repository.getUserMangaList(forceRefresh)
        } else {
            val demo = repository.getDemoManga()
            MalFetchResult.Success(demo, demo.size)
        }
        when (result) {
            is MalFetchResult.Success -> result.data.forEach { consumeGachaCreditUseCase?.initBaselineProgress(it.id, it.progress) }
            is MalFetchResult.Partial -> result.data.forEach { consumeGachaCreditUseCase?.initBaselineProgress(it.id, it.progress) }
            is MalFetchResult.Failure -> { /* network failure, no new items */ }
        }
        return result
    }

    fun getLastSyncedTime(): Long = repository.getLastSyncedTime()

    fun getCachedTracking(type: String): List<UserMediaItem>? = repository.getCachedTracking(type)

    fun getDemoAnime(): List<UserMediaItem> = repository.getDemoAnime()

    fun getDemoManga(): List<UserMediaItem> = repository.getDemoManga()

    fun isUserLoggedIn(): Boolean = getMalUserUseCase?.invoke()?.isLoggedIn ?: false

    suspend operator fun invoke(forceRefresh: Boolean = false): Pair<MalFetchResult<List<UserMediaItem>>, MalFetchResult<List<UserMediaItem>>> =
        Pair(getUserAnimeList(forceRefresh), getUserMangaList(forceRefresh))
}
