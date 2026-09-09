package com.canim.app.domain.repository

import com.canim.app.data.model.MediaItem

interface SearchRepository {
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

    fun searchFilterKey(
        query: String,
        genres: List<String>? = null,
        year: Int? = null,
        format: String? = null
    ): String

    fun getCachedSearch(filterKey: String, type: String): List<MediaItem>?

    fun matchesSearchKey(eventKey: String, filterKey: String, type: String): Boolean
}
