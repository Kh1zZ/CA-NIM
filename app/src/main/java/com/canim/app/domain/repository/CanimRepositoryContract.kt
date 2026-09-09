package com.canim.app.domain.repository

import android.content.Context
import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.model.CastCrewProfile
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MalFetchResult
import com.canim.app.data.model.MalSyncResult
import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.MalUser
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.StudioBioInfo
import com.canim.app.data.model.StudioFilmographySort
import com.canim.app.data.model.UserMediaItem
import com.canim.app.data.repository.CacheRefreshEvent
import kotlinx.coroutines.flow.SharedFlow

interface CanimRepositoryContract : LibraryRepository {
    val cacheRefreshEvents: SharedFlow<CacheRefreshEvent>

    fun buildMalAuthorizeUrl(): String

    suspend fun handleMalOAuthCallback(code: String, state: String?): Result<MalUser>

    fun getMalUser(): MalUser

    fun logoutMal()

    suspend fun syncWithMal(): MalSyncResult

    suspend fun getCharacterProfile(characterId: Int, forceRefresh: Boolean = false): CastCrewProfile?

    suspend fun getStaffProfile(staffId: Int, forceRefresh: Boolean = false): CastCrewProfile?

    fun searchFilterKey(
        query: String,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null
    ): String

    fun discoverFilterKey(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        randomSort: String? = null,
        mediaType: MediaType? = null
    ): String

    suspend fun searchAnime(
        query: String,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null,
        forceRefresh: Boolean = false
    ): List<MediaItem>

    suspend fun searchManga(
        query: String,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null,
        forceRefresh: Boolean = false
    ): List<MediaItem>

    suspend fun getDiscoverMedia(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        forceRefresh: Boolean = false,
        randomSort: String? = null,
        mediaType: MediaType? = null
    ): List<MediaItem>

    fun getCachedExtendedDetail(
        aniListId: Int?,
        malId: Int?,
        type: MediaType? = null
    ): ExtendedMediaDetail?

    suspend fun getMalExtendedDetailFallback(malId: Int, type: MediaType): ExtendedMediaDetail?

    suspend fun isAniListUnavailable(): Boolean

    suspend fun isMalUnavailable(): Boolean

    suspend fun pruneCache()

    fun clearMetadataCache()

    suspend fun clearImageCache(context: Context)

    suspend fun clearAllCache(context: Context)

    suspend fun getMalTrackingStatus(malId: Int, type: MediaType): MalTracking?

    suspend fun getStudioFilmography(
        studioId: Int?,
        search: String? = null,
        page: Int = 1,
        forceRefresh: Boolean = false,
        sort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC,
        isMain: Boolean = true
    ): StudioFilmographyPage?

    suspend fun searchStudios(query: String, page: Int = 1, perPage: Int = 20): List<StudioBioInfo>
}
