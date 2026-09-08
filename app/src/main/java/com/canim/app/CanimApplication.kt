package com.canim.app

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.canim.app.data.local.ConnectivityNetworkChecker
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.local.PendingMutationDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

    // ── Phase 4: Local-First Library Database ─────────────────────────────────

    val localDatabase: LocalDatabase by lazy {
        try { LocalDatabase(this) } catch (e: Exception) {
            android.util.Log.e("CanimApplication", "LocalDatabase init failed: ${e.message}", e)
            throw e
        }
    }

    val libraryDao: LibraryDao by lazy { LibraryDao(localDatabase) }

    val pendingMutationDao: PendingMutationDao by lazy { PendingMutationDao(localDatabase) }

    val networkChecker: ConnectivityNetworkChecker by lazy { ConnectivityNetworkChecker(this) }

    // SyncEngine is wired lazily; MalAuthManager is provided at Repository construction time.
    // Access via CanimApplication.instance.syncEngine from MainActivity where Repository is built.
    // SyncEngine.start() is called from MainActivity after the Repository is constructed.
    var syncEngine: LibrarySyncEngine? = null
        private set

    /**
     * Called from MainActivity (or wherever the Repository is instantiated) to wire and start
     * the SyncEngine with the correct [com.canim.app.data.repository.MalAuthManager] reference.
     */
    fun initSyncEngine(malAuthManager: com.canim.app.data.repository.MalAuthManager) {
        if (syncEngine != null) return  // idempotent
        syncEngine = LibrarySyncEngine(
            pendingMutationDao = pendingMutationDao,
            libraryDao = libraryDao,
            malAuthManager = malAuthManager,
            networkChecker = networkChecker,
            appScope = appScope
        )
        syncEngine?.start()
    }

    // ── end Phase 4 ───────────────────────────────────────────────────────────

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
