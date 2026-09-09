package com.canim.app.data.repository

import android.content.Context
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.remote.ApiClient
import com.canim.app.data.remote.AniListClient
import com.canim.app.domain.repository.SystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemRepositoryImpl @Inject constructor(
    private val swrCoordinator: SwrCoordinator
) : SystemRepository {

    override val cacheRefreshEvents: SharedFlow<CacheRefreshEvent>
        get() = swrCoordinator.events

    override suspend fun isAniListUnavailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (AniListClient.pingHealth()) {
                return@withContext false
            }
            // Tolerant retry: wait 1.5s before concluding outage
            delay(1500L)
            !AniListClient.pingHealth()
        } catch (_: Exception) {
            true
        }
    }

    override suspend fun isMalUnavailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.malApi.getAnimeRanking(MalAuthManager.CLIENT_ID, "all", limit = 1)
            !resp.isSuccessful
        } catch (_: Exception) {
            true
        }
    }

    // --- Cache Management Actions ---
    override suspend fun pruneCache() {
        CacheManager.pruneExpired()
    }

    override fun clearMetadataCache() {
        CacheManager.clearMetadataCache()
    }

    override suspend fun clearImageCache(context: Context) {
        CacheManager.clearImageCache(context)
    }

    override suspend fun clearAllCache(context: Context) {
        CacheManager.clearAllCache(context)
    }
}
