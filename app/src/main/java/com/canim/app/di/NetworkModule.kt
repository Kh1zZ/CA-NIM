package com.canim.app.di

import com.apollographql.apollo.ApolloClient
import com.canim.app.data.remote.ApiClient
import com.canim.app.data.remote.MalApiService
import com.canim.app.data.remote.anilist.AniListApolloClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return ApiClient.okHttpClient
    }

    @Provides
    @Singleton
    @Named("aniListOkHttpClient")
    fun provideAniListOkHttpClient(): OkHttpClient {
        return ApiClient.aniListOkHttpClient
    }

    @Provides
    @Singleton
    @Named("malOkHttpClient")
    fun provideMalOkHttpClient(): OkHttpClient {
        return ApiClient.malOkHttpClient
    }

    @Provides
    @Singleton
    fun provideMalApiService(): MalApiService {
        return ApiClient.malApi
    }

    @Provides
    @Singleton
    fun provideApolloClient(): ApolloClient {
        return AniListApolloClient.client
    }
}
