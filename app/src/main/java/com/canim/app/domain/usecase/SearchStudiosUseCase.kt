package com.canim.app.domain.usecase

import com.canim.app.data.model.StudioBioInfo
import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class SearchStudiosUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    suspend operator fun invoke(
        query: String,
        page: Int = 1,
        perPage: Int = 20
    ): List<StudioBioInfo> = repository.searchStudios(query, page, perPage)
}
