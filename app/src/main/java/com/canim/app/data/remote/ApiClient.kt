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

    /**
     * Per-host request policy for AniList (GraphQL).
     * Max 4 concurrent requests; applied inside [AniListApolloClient] via [RequestPolicy.withPolicy].
     */
    val aniListPolicy: RequestPolicy = RequestPolicy(maxConcurrent = 4)

    /**
     * Per-host request policy for MAL REST API.
     * Max 3 concurrent requests; applied via [MalRequestInterceptor] in [malOkHttpClient].
     */
    val malPolicy: RequestPolicy = RequestPolicy(maxConcurrent = 3)

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

    /**
     * Dedicated OkHttpClient for AniList GraphQL operations.
     * Tuned timeouts: 10s connect, 15s read, 10s write.
     * Isolated connection pool and no ineffective HTTP GET cache for GraphQL POST requests.
     */
    val aniListOkHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Dedicated OkHttpClient for MAL REST API requests.
     * Uses [malPolicy] via [MalRequestInterceptor] for 429 cooldown and idempotent retry.
     * Sensitive headers (Authorization, X-MAL-CLIENT-ID) are passed by callers and
     * never logged by [MalRequestInterceptor].
     */
    val malOkHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC logs request line + response code only — no headers, no body.
            // Authorization and X-MAL-CLIENT-ID headers are therefore NOT logged.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(false) // retry handled by MalRequestInterceptor
            .addInterceptor(MalRequestInterceptor(malPolicy))
            .addNetworkInterceptor(logging)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    val malApi: MalApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.myanimelist.net/v2/")
            .client(malOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MalApiService::class.java)
    }
}

