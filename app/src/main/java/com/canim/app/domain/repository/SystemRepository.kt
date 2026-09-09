package com.canim.app.domain.repository

import android.content.Context
import com.canim.app.data.repository.CacheRefreshEvent
import kotlinx.coroutines.flow.SharedFlow

interface SystemRepository {
    val cacheRefreshEvents: SharedFlow<CacheRefreshEvent>

    suspend fun isAniListUnavailable(): Boolean

    suspend fun isMalUnavailable(): Boolean

    suspend fun pruneCache()

    fun clearMetadataCache()

    suspend fun clearImageCache(context: Context)

    suspend fun clearAllCache(context: Context)
}
