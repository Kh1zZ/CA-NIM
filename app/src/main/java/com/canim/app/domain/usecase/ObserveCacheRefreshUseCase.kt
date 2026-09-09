package com.canim.app.domain.usecase

import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.CacheRefreshEvent
import com.canim.app.domain.repository.CanimRepositoryContract
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

class ObserveCacheRefreshUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    val events: SharedFlow<CacheRefreshEvent> = repository.cacheRefreshEvents

    fun searchFilterKey(
        query: String,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null
    ): String = repository.searchFilterKey(query, genres, year, format)

    fun discoverFilterKey(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        randomSort: String? = null,
        mediaType: MediaType? = null
    ): String = repository.discoverFilterKey(category, filter, page, randomSort, mediaType)
}
