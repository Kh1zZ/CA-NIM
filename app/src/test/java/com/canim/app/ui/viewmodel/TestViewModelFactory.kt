package com.canim.app.ui.viewmodel

import android.content.Context
import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.model.*
import com.canim.app.data.repository.CacheRefreshEvent
import com.canim.app.domain.repository.CanimRepositoryContract
import com.canim.app.domain.usecase.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

open class FakeCanimRepository : CanimRepositoryContract {
    override val cacheRefreshEvents: SharedFlow<CacheRefreshEvent> = MutableSharedFlow()
    override fun buildMalAuthorizeUrl(): String = ""
    override suspend fun handleMalOAuthCallback(code: String, state: String?): Result<MalUser> = Result.success(MalUser())
    override fun getMalUser(): MalUser = MalUser()
    override fun logoutMal() {}
    override suspend fun syncWithMal(): MalSyncResult = MalSyncResult(isSuccess = true)
    override fun getLastSyncedTime(): Long = 0L
    override fun getCachedTracking(type: String): List<UserMediaItem>? = null
    override suspend fun getUserAnimeList(forceRefresh: Boolean): MalFetchResult<List<UserMediaItem>> = MalFetchResult.Success(emptyList(), 0)
    override suspend fun getUserMangaList(forceRefresh: Boolean): MalFetchResult<List<UserMediaItem>> = MalFetchResult.Success(emptyList(), 0)
    override suspend fun retryFailedMutations(mediaType: String?): Int = 0
    override suspend fun getCharacterProfile(characterId: Int, forceRefresh: Boolean): CastCrewProfile? = null
    override suspend fun getStaffProfile(staffId: Int, forceRefresh: Boolean): CastCrewProfile? = null
    override suspend fun updateAnimeTracking(malId: Int, tracking: MalTracking): Result<Unit> = Result.success(Unit)
    override suspend fun updateMangaTracking(malId: Int, tracking: MalTracking): Result<Unit> = Result.success(Unit)
    override suspend fun deleteAnimeTracking(malId: Int): Result<Unit> = Result.success(Unit)
    override suspend fun deleteMangaTracking(malId: Int): Result<Unit> = Result.success(Unit)
    override fun searchFilterKey(query: String, genres: List<String>?, year: Int?, format: String?): String = ""
    override fun discoverFilterKey(category: DiscoverCategory, filter: DiscoverFilter, page: Int, randomSort: String?, mediaType: MediaType?): String = ""
    override suspend fun searchAnime(query: String, genres: List<String>?, year: Int?, format: String?, forceRefresh: Boolean): List<MediaItem> = emptyList()
    override suspend fun searchManga(query: String, genres: List<String>?, year: Int?, format: String?, forceRefresh: Boolean): List<MediaItem> = emptyList()
    override suspend fun getDiscoverMedia(category: DiscoverCategory, filter: DiscoverFilter, page: Int, forceRefresh: Boolean, randomSort: String?, mediaType: MediaType?): List<MediaItem> = emptyList()
    override fun getCachedExtendedDetail(aniListId: Int?, malId: Int?, type: MediaType?): ExtendedMediaDetail? = null
    override suspend fun getMalExtendedDetailFallback(malId: Int, type: MediaType): ExtendedMediaDetail? = null
    override suspend fun isAniListUnavailable(): Boolean = false
    override suspend fun isMalUnavailable(): Boolean = false
    override suspend fun pruneCache() {}
    override fun clearMetadataCache() {}
    override suspend fun clearImageCache(context: Context) {}
    override suspend fun clearAllCache(context: Context) {}
    override fun getDemoAnime(): List<UserMediaItem> = emptyList()
    override fun getDemoManga(): List<UserMediaItem> = emptyList()
    override suspend fun getMalTrackingStatus(malId: Int, type: MediaType): MalTracking? = null
    override suspend fun getStudioFilmography(studioId: Int?, search: String?, page: Int, forceRefresh: Boolean, sort: StudioFilmographySort, isMain: Boolean): StudioFilmographyPage? = null
    override suspend fun searchStudios(query: String, page: Int, perPage: Int): List<StudioBioInfo> = emptyList()
}

fun createTestCanimViewModel(
    repository: CanimRepositoryContract = FakeCanimRepository(),
    gachaCreditManager: GachaCreditManager
): CanimViewModel {
    return CanimViewModel(
        getLibraryUseCase = GetLibraryUseCase(repository),
        saveLibraryItemUseCase = SaveLibraryItemUseCase(repository),
        deleteLibraryItemUseCase = DeleteLibraryItemUseCase(repository),
        updateTrackingUseCase = UpdateTrackingUseCase(repository),
        searchMediaUseCase = SearchMediaUseCase(repository),
        getDiscoverCategoryUseCase = GetDiscoverCategoryUseCase(repository),
        getExtendedDetailUseCase = GetExtendedDetailUseCase(repository),
        getCastCrewProfileUseCase = GetCastCrewProfileUseCase(repository),
        getStudioFilmographyUseCase = GetStudioFilmographyUseCase(repository),
        searchStudiosUseCase = SearchStudiosUseCase(repository),
        consumeGachaCreditUseCase = ConsumeGachaCreditUseCase(gachaCreditManager),
        loadFlashcardDeckUseCase = LoadFlashcardDeckUseCase(repository),
        getMalUserUseCase = GetMalUserUseCase(repository),
        loginMalUseCase = LoginMalUseCase(repository),
        handleMalOAuthCallbackUseCase = HandleMalOAuthCallbackUseCase(repository),
        logoutMalUseCase = LogoutMalUseCase(repository),
        syncMalUseCase = SyncMalUseCase(repository),
        checkForUpdatesUseCase = CheckForUpdatesUseCase(),
        startDownloadUpdateUseCase = StartDownloadUpdateUseCase(),
        installUpdateUseCase = InstallUpdateUseCase(),
        checkApiHealthUseCase = CheckApiHealthUseCase(repository),
        clearCacheUseCase = ClearCacheUseCase(repository),
        observeCacheRefreshUseCase = ObserveCacheRefreshUseCase(repository)
    )
}
