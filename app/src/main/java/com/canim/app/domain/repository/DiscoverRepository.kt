package com.canim.app.domain.repository

import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType

interface DiscoverRepository {
    suspend fun getDiscoverMedia(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        forceRefresh: Boolean = false,
        randomSort: String? = null,
        mediaType: MediaType? = null
    ): List<MediaItem>

    fun discoverFilterKey(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        randomSort: String? = null,
        mediaType: MediaType? = null
    ): String

    fun getCachedDiscover(categoryKey: String): List<MediaItem>?

    fun matchesDiscoverKey(eventKey: String, categoryKey: String): Boolean
}
