package com.canim.app.domain.repository

import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.model.StudioBioInfo
import com.canim.app.data.model.StudioFilmographySort

interface StudioRepository {
    suspend fun getStudioFilmography(
        studioId: Int?,
        search: String? = null,
        page: Int = 1,
        forceRefresh: Boolean = false,
        sort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC,
        isMain: Boolean = true
    ): StudioFilmographyPage?

    suspend fun searchStudios(query: String, page: Int = 1, perPage: Int = 20): List<StudioBioInfo>

    fun getStudioInfo(studioId: Int, studioName: String): StudioBioInfo

    fun searchCuratedStudios(query: String): List<StudioBioInfo>
}
