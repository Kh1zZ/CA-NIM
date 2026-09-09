package com.canim.app.domain.usecase

import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.CacheRefreshEvent
import com.canim.app.domain.repository.DiscoverRepository
import com.canim.app.domain.repository.SearchRepository
import com.canim.app.domain.repository.SystemRepository
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

class ObserveCacheRefreshUseCase @Inject constructor(
    private val systemRepository: SystemRepository,
    private val searchRepository: SearchRepository,
    private val discoverRepository: DiscoverRepository
) {
    val events: SharedFlow<CacheRefreshEvent> = systemRepository.cacheRefreshEvents

    fun searchFilterKey(
        query: String,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null
    ): String = searchRepository.searchFilterKey(query, genres, year, format)

    fun discoverFilterKey(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        randomSort: String? = null,
        mediaType: MediaType? = null
    ): String = discoverRepository.discoverFilterKey(category, filter, page, randomSort, mediaType)
}
