package com.canim.app.domain.usecase

import com.canim.app.data.model.StudioBioInfo
import com.canim.app.domain.repository.StudioRepository
import javax.inject.Inject

class SearchStudiosUseCase @Inject constructor(
    private val repository: StudioRepository
) {
    suspend operator fun invoke(
        query: String,
        page: Int = 1,
        perPage: Int = 20
    ): List<StudioBioInfo> = repository.searchStudios(query, page, perPage)

    fun getStudioInfo(studioId: Int, studioName: String): StudioBioInfo =
        repository.getStudioInfo(studioId, studioName)

    fun searchCuratedStudios(query: String): List<StudioBioInfo> =
        repository.searchCuratedStudios(query)
}
