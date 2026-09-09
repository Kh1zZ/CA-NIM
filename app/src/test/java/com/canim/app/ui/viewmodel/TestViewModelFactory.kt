package com.canim.app.ui.viewmodel

import android.content.Context
import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.model.*
import com.canim.app.data.repository.CacheRefreshEvent
import com.canim.app.domain.repository.*
import com.canim.app.domain.usecase.*
import com.canim.app.ui.viewmodel.update.UpdateViewModel
import com.canim.app.ui.viewmodel.gacha.GachaViewModel
import com.canim.app.ui.viewmodel.detail.DetailViewModel
import com.canim.app.ui.viewmodel.studio.StudioViewModel
import com.canim.app.ui.viewmodel.search.SearchViewModel
import com.canim.app.ui.viewmodel.discover.DiscoverViewModel
import com.canim.app.ui.viewmodel.library.LibraryViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

interface CanimTestRepository :
    LibraryRepository,
    SearchRepository,
    DiscoverRepository,
    DetailRepository,
    StudioRepository,
    AuthRepository,
    SystemRepository

open class FakeCanimRepository : CanimTestRepository {
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
    override fun getCachedSearch(filterKey: String, type: String): List<MediaItem>? = null
    override fun matchesSearchKey(eventKey: String, filterKey: String, type: String): Boolean = false
    override fun discoverFilterKey(category: DiscoverCategory, filter: DiscoverFilter, page: Int, randomSort: String?, mediaType: MediaType?): String = ""
    override fun getCachedDiscover(categoryKey: String): List<MediaItem>? = null
    override fun matchesDiscoverKey(eventKey: String, categoryKey: String): Boolean = false
    override suspend fun searchAnime(query: String, genres: List<String>?, year: Int?, format: String?, forceRefresh: Boolean): List<MediaItem> = emptyList()
    override suspend fun searchManga(query: String, genres: List<String>?, year: Int?, format: String?, forceRefresh: Boolean): List<MediaItem> = emptyList()
    override suspend fun getDiscoverMedia(category: DiscoverCategory, filter: DiscoverFilter, page: Int, forceRefresh: Boolean, randomSort: String?, mediaType: MediaType?): List<MediaItem> = emptyList()
    override fun getCachedExtendedDetail(aniListId: Int?, malId: Int?, type: MediaType?): ExtendedMediaDetail? = null
    override suspend fun getMalExtendedDetailFallback(malId: Int, type: MediaType): ExtendedMediaDetail? = null
    override suspend fun getExtendedDetails(aniListId: Int?, malId: Int?, type: MediaType, forceRefresh: Boolean): ExtendedMediaDetail? = null
    override fun getAniListIdForMalId(malId: Int, type: MediaType?): Int? = null
    override fun getMalIdForAniListId(aniListId: Int, type: MediaType?): Int? = null
    override fun getCachedDetail(key: String): ExtendedMediaDetail? = null
    override fun matchesDetailKey(eventKey: String, aniId: Int?, malId: Int?): Boolean = false
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
    override fun getStudioInfo(studioId: Int, studioName: String): StudioBioInfo = StudioBioInfo(studioId = studioId, name = studioName)
    override fun searchCuratedStudios(query: String): List<StudioBioInfo> = emptyList()
}

fun createTestCanimViewModel(
    repository: CanimTestRepository = FakeCanimRepository(),
    gachaCreditManager: GachaCreditManager
): CanimViewModel {
    val consumeGachaCreditUseCase = ConsumeGachaCreditUseCase(gachaCreditManager)
    val getMalUserUseCase = GetMalUserUseCase(repository)
    return CanimViewModel(
        getLibraryUseCase = GetLibraryUseCase(repository, consumeGachaCreditUseCase, getMalUserUseCase),
        saveLibraryItemUseCase = SaveLibraryItemUseCase(repository, consumeGachaCreditUseCase, getMalUserUseCase),
        deleteLibraryItemUseCase = DeleteLibraryItemUseCase(repository, getMalUserUseCase),
        updateTrackingUseCase = UpdateTrackingUseCase(repository, consumeGachaCreditUseCase, getMalUserUseCase),
        searchMediaUseCase = SearchMediaUseCase(repository),
        getDiscoverCategoryUseCase = GetDiscoverCategoryUseCase(repository),
        getExtendedDetailUseCase = GetExtendedDetailUseCase(repository),
        getCastCrewProfileUseCase = GetCastCrewProfileUseCase(repository),
        getStudioFilmographyUseCase = GetStudioFilmographyUseCase(repository),
        searchStudiosUseCase = SearchStudiosUseCase(repository),
        consumeGachaCreditUseCase = consumeGachaCreditUseCase,
        loadFlashcardDeckUseCase = LoadFlashcardDeckUseCase(
            discoverRepository = repository,
            libraryRepository = repository
        ),
        getMalUserUseCase = getMalUserUseCase,
        loginMalUseCase = LoginMalUseCase(repository),
        handleMalOAuthCallbackUseCase = HandleMalOAuthCallbackUseCase(repository),
        logoutMalUseCase = LogoutMalUseCase(repository),
        syncMalUseCase = SyncMalUseCase(repository),
        checkForUpdatesUseCase = CheckForUpdatesUseCase(),
        startDownloadUpdateUseCase = StartDownloadUpdateUseCase(),
        installUpdateUseCase = InstallUpdateUseCase(),
        checkApiHealthUseCase = CheckApiHealthUseCase(repository),
        clearCacheUseCase = ClearCacheUseCase(repository),
        observeCacheRefreshUseCase = ObserveCacheRefreshUseCase(
            systemRepository = repository,
            searchRepository = repository,
            discoverRepository = repository
        )
    )
}

fun createTestUpdateViewModel(
    context: Context,
    checkForUpdatesUseCase: CheckForUpdatesUseCase = CheckForUpdatesUseCase(),
    startDownloadUpdateUseCase: StartDownloadUpdateUseCase = StartDownloadUpdateUseCase(),
    installUpdateUseCase: InstallUpdateUseCase = InstallUpdateUseCase()
): UpdateViewModel {
    return UpdateViewModel(
        appContext = context,
        checkForUpdatesUseCase = checkForUpdatesUseCase,
        startDownloadUpdateUseCase = startDownloadUpdateUseCase,
        installUpdateUseCase = installUpdateUseCase
    )
}

fun createTestGachaViewModel(
    repository: CanimTestRepository = FakeCanimRepository(),
    gachaCreditManager: GachaCreditManager
): GachaViewModel {
    return GachaViewModel(
        consumeGachaCreditUseCase = ConsumeGachaCreditUseCase(gachaCreditManager),
        loadFlashcardDeckUseCase = LoadFlashcardDeckUseCase(
            discoverRepository = repository,
            libraryRepository = repository
        )
    )
}

fun createTestDetailViewModel(
    repository: CanimTestRepository = FakeCanimRepository()
): DetailViewModel {
    return DetailViewModel(
        getExtendedDetailUseCase = GetExtendedDetailUseCase(repository),
        getCastCrewProfileUseCase = GetCastCrewProfileUseCase(repository),
        observeCacheRefreshUseCase = ObserveCacheRefreshUseCase(
            systemRepository = repository,
            searchRepository = repository,
            discoverRepository = repository
        )
    )
}

fun createTestStudioViewModel(
    repository: CanimTestRepository = FakeCanimRepository()
): StudioViewModel {
    return StudioViewModel(
        getStudioFilmographyUseCase = GetStudioFilmographyUseCase(repository),
        searchStudiosUseCase = SearchStudiosUseCase(repository)
    )
}

fun createTestSearchViewModel(
    repository: CanimTestRepository = FakeCanimRepository()
): SearchViewModel {
    return SearchViewModel(
        searchMediaUseCase = SearchMediaUseCase(repository),
        observeCacheRefreshUseCase = ObserveCacheRefreshUseCase(
            systemRepository = repository,
            searchRepository = repository,
            discoverRepository = repository
        )
    )
}

fun createTestDiscoverViewModel(
    repository: CanimTestRepository = FakeCanimRepository()
): DiscoverViewModel {
    return DiscoverViewModel(
        getDiscoverCategoryUseCase = GetDiscoverCategoryUseCase(repository),
        observeCacheRefreshUseCase = ObserveCacheRefreshUseCase(
            systemRepository = repository,
            searchRepository = repository,
            discoverRepository = repository
        )
    )
}

fun createTestLibraryViewModel(
    repository: CanimTestRepository = FakeCanimRepository(),
    gachaCreditManager: GachaCreditManager
): LibraryViewModel {
    val consumeGachaCreditUseCase = ConsumeGachaCreditUseCase(gachaCreditManager)
    val getMalUserUseCase = GetMalUserUseCase(repository)
    return LibraryViewModel(
        getLibraryUseCase = GetLibraryUseCase(repository, consumeGachaCreditUseCase, getMalUserUseCase),
        saveLibraryItemUseCase = SaveLibraryItemUseCase(repository, consumeGachaCreditUseCase, getMalUserUseCase),
        deleteLibraryItemUseCase = DeleteLibraryItemUseCase(repository, getMalUserUseCase),
        updateTrackingUseCase = UpdateTrackingUseCase(repository, consumeGachaCreditUseCase, getMalUserUseCase)
    )
}

