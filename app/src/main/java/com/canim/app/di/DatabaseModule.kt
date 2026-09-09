package com.canim.app.di

import android.content.Context
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.local.PendingMutationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLocalDatabase(@ApplicationContext context: Context): LocalDatabase {
        return try {
            LocalDatabase(context)
        } catch (e: Exception) {
            android.util.Log.e("DatabaseModule", "LocalDatabase init failed: ${e.message}", e)
            throw e
        }
    }

    @Provides
    @Singleton
    fun provideLibraryDao(localDatabase: LocalDatabase): LibraryDao {
        return LibraryDao(localDatabase)
    }

    @Provides
    @Singleton
    fun providePendingMutationDao(localDatabase: LocalDatabase): PendingMutationDao {
        return PendingMutationDao(localDatabase)
    }
}
