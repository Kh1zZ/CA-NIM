package com.canim.app.data.remote

import com.canim.app.BuildConfig
import com.canim.app.CanimApplication
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object ApiClient {
    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val cache = runCatching {
            val app = CanimApplication.instance
            val cacheDir = File(app.cacheDir, "http_cache")
            Cache(cacheDir, 30L * 1024 * 1024)
        }.getOrNull()

        OkHttpClient.Builder()
            .apply { if (cache != null) cache(cache) }
            .connectionPool(ConnectionPool(8, 10, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    val malApi: MalApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.myanimelist.net/v2/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MalApiService::class.java)
    }
}
