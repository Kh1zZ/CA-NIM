package com.canim.app.domain.usecase

import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class GetDiscoverCategoryUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    suspend operator fun invoke(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        forceRefresh: Boolean = false,
        randomSort: String? = null,
        mediaType: MediaType? = null
    ): List<MediaItem> = repository.getDiscoverMedia(
        category = category,
        filter = filter,
        page = page,
        forceRefresh = forceRefresh,
        randomSort = randomSort,
        mediaType = mediaType
    )

    fun discoverFilterKey(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        randomSort: String? = null,
        mediaType: MediaType? = null
    ): String = repository.discoverFilterKey(
        category = category,
        filter = filter,
        page = page,
        randomSort = randomSort,
        mediaType = mediaType
    )
}
