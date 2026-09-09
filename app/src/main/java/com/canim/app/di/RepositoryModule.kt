package com.canim.app.di

import com.canim.app.data.repository.LibraryRepositoryImpl
import com.canim.app.domain.repository.LibraryRepository
import com.canim.app.data.repository.SearchRepositoryImpl
import com.canim.app.domain.repository.SearchRepository
import com.canim.app.data.repository.DiscoverRepositoryImpl
import com.canim.app.domain.repository.DiscoverRepository
import com.canim.app.data.repository.DetailRepositoryImpl
import com.canim.app.domain.repository.DetailRepository
import com.canim.app.data.repository.StudioRepositoryImpl
import com.canim.app.domain.repository.StudioRepository
import com.canim.app.data.repository.AuthRepositoryImpl
import com.canim.app.domain.repository.AuthRepository
import com.canim.app.data.repository.SystemRepositoryImpl
import com.canim.app.domain.repository.SystemRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {


    @Binds
    @Singleton
    abstract fun bindLibraryRepository(
        libraryRepositoryImpl: LibraryRepositoryImpl
    ): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        searchRepositoryImpl: SearchRepositoryImpl
    ): SearchRepository

    @Binds
    @Singleton
    abstract fun bindDiscoverRepository(
        discoverRepositoryImpl: DiscoverRepositoryImpl
    ): DiscoverRepository

    @Binds
    @Singleton
    abstract fun bindDetailRepository(
        detailRepositoryImpl: DetailRepositoryImpl
    ): DetailRepository

    @Binds
    @Singleton
    abstract fun bindStudioRepository(
        studioRepositoryImpl: StudioRepositoryImpl
    ): StudioRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSystemRepository(
        systemRepositoryImpl: SystemRepositoryImpl
    ): SystemRepository
}
