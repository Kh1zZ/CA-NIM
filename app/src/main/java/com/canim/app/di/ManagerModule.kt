package com.canim.app.di

import android.content.Context
import com.canim.app.data.local.ConnectivityNetworkChecker
import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.local.PendingMutationDao
import com.canim.app.data.repository.MalAuthManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Provides
    @Singleton
    fun provideMalSecureStorage(@ApplicationContext context: Context): MalSecureStorage {
        return MalSecureStorage(context)
    }

    @Provides
    @Singleton
    fun provideMalAuthManager(secureStorage: MalSecureStorage): MalAuthManager {
        return MalAuthManager(secureStorage = secureStorage)
    }

    @Provides
    @Singleton
    fun provideGachaCreditManager(@ApplicationContext context: Context): GachaCreditManager {
        return GachaCreditManager(context)
    }

    @Provides
    @Singleton
    fun provideConnectivityNetworkChecker(@ApplicationContext context: Context): ConnectivityNetworkChecker {
        return ConnectivityNetworkChecker(context)
    }

    @Provides
    @Singleton
    fun provideLibrarySyncEngine(
        pendingMutationDao: PendingMutationDao,
        libraryDao: LibraryDao,
        malAuthManager: MalAuthManager,
        networkChecker: ConnectivityNetworkChecker,
        appScope: CoroutineScope
    ): LibrarySyncEngine {
        val syncEngine = LibrarySyncEngine(
            pendingMutationDao = pendingMutationDao,
            libraryDao = libraryDao,
            malAuthManager = malAuthManager,
            networkChecker = networkChecker,
            appScope = appScope
        )
        syncEngine.start()
        return syncEngine
    }
}
