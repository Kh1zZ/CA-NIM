package com.canim.app.domain.usecase

import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.domain.repository.SearchRepository
import javax.inject.Inject

class SearchMediaUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    suspend operator fun invoke(
        query: String,
        type: MediaType,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null,
        forceRefresh: Boolean = false
    ): List<MediaItem> {
        return if (type == MediaType.ANIME) {
            repository.searchAnime(query, genres, year, format, forceRefresh)
        } else {
            repository.searchManga(query, genres, year, format, forceRefresh)
        }
    }

    fun searchFilterKey(
        query: String,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null
    ): String = repository.searchFilterKey(query, genres, year, format)

    fun getCachedSearch(filterKey: String, type: String): List<MediaItem>? =
        repository.getCachedSearch(filterKey, type)

    fun matchesSearchKey(eventKey: String, filterKey: String, type: String): Boolean =
        repository.matchesSearchKey(eventKey, filterKey, type)
}
