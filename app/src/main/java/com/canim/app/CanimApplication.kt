package com.canim.app

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CanimApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var instance: CanimApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.canim.app.data.cache.CacheManager.init(this)
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
