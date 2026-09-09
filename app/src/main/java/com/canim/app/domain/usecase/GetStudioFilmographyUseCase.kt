package com.canim.app.domain.usecase

import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.model.StudioFilmographySort
import com.canim.app.domain.repository.StudioRepository
import javax.inject.Inject

class GetStudioFilmographyUseCase @Inject constructor(
    private val repository: StudioRepository
) {
    suspend operator fun invoke(
        studioId: Int?,
        search: String? = null,
        page: Int = 1,
        forceRefresh: Boolean = false,
        sort: StudioFilmographySort = StudioFilmographySort.YEAR_DESC,
        isMain: Boolean = true
    ): StudioFilmographyPage? = repository.getStudioFilmography(
        studioId = studioId,
        search = search,
        page = page,
        forceRefresh = forceRefresh,
        sort = sort,
        isMain = isMain
    )
}
