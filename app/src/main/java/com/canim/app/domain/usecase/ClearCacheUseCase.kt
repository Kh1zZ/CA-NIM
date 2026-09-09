package com.canim.app.domain.usecase

import android.content.Context
import com.canim.app.domain.repository.SystemRepository
import javax.inject.Inject

class ClearCacheUseCase @Inject constructor(
    private val repository: SystemRepository
) {
    suspend fun prune() {
        repository.pruneCache()
    }

    suspend fun clearImageCache(context: Context) {
        repository.clearImageCache(context)
    }

    fun clearMetadataCache() {
        repository.clearMetadataCache()
    }

    suspend fun clearAllCache(context: Context) {
        repository.clearAllCache(context)
    }
}
