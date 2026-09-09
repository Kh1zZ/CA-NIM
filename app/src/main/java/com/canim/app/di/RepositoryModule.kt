package com.canim.app.di

import com.canim.app.data.repository.CanimRepository
import com.canim.app.domain.repository.CanimRepositoryContract
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.canim.app.data.repository.LibraryRepositoryImpl
import com.canim.app.domain.repository.LibraryRepository
import com.canim.app.data.repository.SearchRepositoryImpl
import com.canim.app.domain.repository.SearchRepository
import com.canim.app.data.repository.DiscoverRepositoryImpl
import com.canim.app.domain.repository.DiscoverRepository

import com.canim.app.data.repository.DetailRepositoryImpl
import com.canim.app.domain.repository.DetailRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCanimRepository(
        canimRepository: CanimRepository
    ): CanimRepositoryContract

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
}
