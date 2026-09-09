package com.canim.app.data.repository

import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.model.StudioBioInfo
import com.canim.app.data.model.StudioFilmographySort
import com.canim.app.data.remote.AniListClient
import com.canim.app.domain.repository.StudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudioRepositoryImpl @Inject constructor() : StudioRepository {

    override suspend fun getStudioFilmography(
        studioId: Int?,
        search: String?,
        page: Int,
        forceRefresh: Boolean,
        sort: StudioFilmographySort,
        isMain: Boolean
    ): StudioFilmographyPage? = withContext(Dispatchers.IO) {
        AniListClient.getStudioFilmography(
            studioId = studioId,
            search = search,
            page = page,
            forceRefresh = forceRefresh,
            sort = sort,
            isMain = isMain
        )
    }

    override suspend fun searchStudios(
        query: String,
        page: Int,
        perPage: Int
    ): List<StudioBioInfo> = withContext(Dispatchers.IO) {
        val rawList = AniListClient.searchStudios(query, page, perPage)
        rawList.map { raw ->
            val info = StudioBioRegistry.getStudioInfo(raw.studioId, raw.name).let { base ->
                base.copy(
                    studioId = raw.studioId,
                    name = raw.name,
                    coverUrl = base.coverUrl ?: raw.coverUrl,
                    favourites = base.favourites ?: raw.favourites,
                    officialSite = base.officialSite ?: raw.officialSite
                )
            }
            StudioBioRegistry.saveToPersistentCache(info)
            info
        }
    }

    override fun getStudioInfo(studioId: Int, studioName: String): StudioBioInfo =
        StudioBioRegistry.getStudioInfo(studioId, studioName)

    override fun searchCuratedStudios(query: String): List<StudioBioInfo> =
        StudioBioRegistry.searchCuratedStudios(query)
}
