package com.canim.app

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class CanimApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var instance: CanimApplication
            private set
    }

    /**
     * Application-scoped CoroutineScope for long-lived background operations.
     * SupervisorJob ensures one child failure does not cancel siblings.
     * Cancelled only when the process is killed — intentionally never cancelled manually.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // CacheManager.init(this) sengaja dipertahankan pada bootstrap Application karena CacheManager
        // merupakan global in-memory & disk cache registry yang harus siap sebelum komponen repositori,
        // remote, atau UI mengakses entri cache selama lifecycle proses aplikasi berlangsung.
        com.canim.app.data.cache.CacheManager.init(this)
        com.canim.app.notification.CanimNotificationManager(this).initChannels()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // Allocate 25% of memory for smooth cover art scrolling without cache thrashing
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("canim_image_cache"))
                    .maxSizeBytes(120L * 1024 * 1024) // 120 MB disk cache
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
            }
            .allowHardware(true)
            .allowRgb565(true) // Reduce bitmap memory by 50% for butter-smooth fling scrolling
            .crossfade(false) // Disable global crossfade to eliminate animation overhead during fast scrolling
            .respectCacheHeaders(false) // Prefer cached cover art
            .build()
    }
}
