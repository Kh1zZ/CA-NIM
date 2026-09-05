package com.canim.app.data.repository

import android.net.Uri
import android.util.Log
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.*
import com.canim.app.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class MalAuthManager(
    private val secureStorage: MalSecureStorage
) {
    companion object {
        const val CLIENT_ID = "a4f3b20e6eb04e9daac4d2ea9fb2a45a"
        const val REDIRECT_URI = "canim://oauth/callback"
        const val AUTH_BASE_URL = "https://myanimelist.net/v1/oauth2/authorize"
        private const val PKCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        private const val ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val secureRandom = SecureRandom()

        fun generateRandomString(length: Int): String {
            val sb = StringBuilder(length)
            for (i in 0 until length) {
                sb.append(PKCE_CHARS[secureRandom.nextInt(PKCE_CHARS.length)])
            }
            return sb.toString()
        }

        fun generateAlphanumericString(length: Int): String {
            val sb = StringBuilder(length)
            for (i in 0 until length) {
                sb.append(ALPHANUMERIC[secureRandom.nextInt(ALPHANUMERIC.length)])
            }
            return sb.toString()
        }

        /**
         * Generates RFC 7636 PKCE S256 challenge.
         * Kept for standard RFC 7636 cryptographic compliance and testing.
         */
        fun generateCodeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
            return try {
                Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
            } catch (_: Throwable) {
                android.util.Base64.encodeToString(
                    hash,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                ).trim()
            }
        }
    }

    /**
     * Builds the MyAnimeList OAuth2 Authorization URL.
     * Note: MyAnimeList OAuth2 API explicitly mandates code_challenge_method=plain
     * and code_challenge=codeVerifier. Passing S256 causes MAL token exchange to fail with HTTP 400.
     */
    fun buildAuthorizeUrl(): String {
        val codeVerifier = generateRandomString(128)
        val state = generateAlphanumericString(32)

        secureStorage.savePkce(codeVerifier, state)

        val uri = Uri.parse(AUTH_BASE_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("code_challenge", codeVerifier)
            .appendQueryParameter("code_challenge_method", "plain")
            .appendQueryParameter("state", state)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .build()

        return uri.toString()
    }

    /**
     * Exchanges the authorization code for tokens and fetches user profile.
     * Enforces strict OAuth state verification (aborts on mismatch).
     */
    suspend fun handleOAuthCallback(code: String, state: String?): Result<MalUser> = withContext(Dispatchers.IO) {
        try {
            val savedState = secureStorage.getPkceState()
            if (!savedState.isNullOrEmpty() && !state.isNullOrEmpty() && savedState != state) {
                secureStorage.clearPkce()
                throw IllegalStateException("OAuth state mismatch (dikirim: $savedState, diterima: $state). Login dibatalkan.")
            }

            val verifier = secureStorage.getPkceVerifier()
                ?: throw IllegalStateException("PKCE verifier tidak ditemukan di secure storage. Silakan coba login kembali.")

            // 1. Exchange code for access & refresh tokens
            val tokenResponse = ApiClient.malApi.exchangeToken(
                clientId = CLIENT_ID,
                code = code.trim(),
                codeVerifier = verifier.trim(),
                grantType = "authorization_code",
                redirectUri = REDIRECT_URI
            )

            secureStorage.saveTokens(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresInSeconds = tokenResponse.expiresIn
            )
            secureStorage.clearPkce()

            // 2. Fetch User Profile
            val profile = ApiClient.malApi.getUserProfile(
                authHeader = "Bearer ${tokenResponse.accessToken}"
            )

            secureStorage.saveUserProfile(
                id = profile.id,
                username = profile.name,
                pictureUrl = profile.picture,
                location = profile.location,
                gender = profile.gender
            )

            val malUser = MalUser(
                id = profile.id,
                username = profile.name,
                pictureUrl = profile.picture,
                location = profile.location,
                gender = profile.gender,
                isLoggedIn = true
            )

            CacheManager.invalidateTracking()

            Result.success(malUser)
        } catch (e: Exception) {
            val errorMsg = if (e is retrofit2.HttpException) {
                val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                "HTTP ${e.code()}: ${errorBody ?: e.message()}"
            } else {
                e.message ?: e.toString()
            }
            Log.e("MalAuthManager", "Gagal menukar token MAL: $errorMsg", e)
            Result.failure(Exception(errorMsg, e))
        }
    }

    /**
     * Obtains a valid access token, auto-refreshing if expired.
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val currentToken = secureStorage.getAccessToken() ?: return@withContext null
        if (!secureStorage.isTokenExpired()) {
            return@withContext currentToken
        }

        val refreshToken = secureStorage.getRefreshToken() ?: return@withContext currentToken
        try {
            val refreshResponse = ApiClient.malApi.refreshToken(
                clientId = CLIENT_ID,
                refreshToken = refreshToken,
                grantType = "refresh_token"
            )
            secureStorage.saveTokens(
                accessToken = refreshResponse.accessToken,
                refreshToken = refreshResponse.refreshToken ?: refreshToken,
                expiresInSeconds = refreshResponse.expiresIn
            )
            refreshResponse.accessToken
        } catch (e: Exception) {
            Log.e("MalAuthManager", "Gagal refresh token MAL: ${e.message}")
            currentToken
        }
    }

    private suspend fun refreshTokenForce(): String? = withContext(Dispatchers.IO) {
        val refreshToken = secureStorage.getRefreshToken() ?: return@withContext null
        try {
            val refreshResponse = ApiClient.malApi.refreshToken(
                clientId = CLIENT_ID,
                refreshToken = refreshToken,
                grantType = "refresh_token"
            )
            secureStorage.saveTokens(
                accessToken = refreshResponse.accessToken,
                refreshToken = refreshResponse.refreshToken ?: refreshToken,
                expiresInSeconds = refreshResponse.expiresIn
            )
            refreshResponse.accessToken
        } catch (e: Exception) {
            Log.e("MalAuthManager", "Force refresh token MAL gagal: ${e.message}")
            null
        }
    }

    private suspend fun <T> executeWithTokenRefresh(block: suspend (authHeader: String) -> Response<T>): Response<T> {
        val token = getValidAccessToken() ?: throw IllegalStateException("Belum login ke MyAnimeList")
        var response = block("Bearer $token")
        if (response.code() == 401) {
            val refreshed = refreshTokenForce()
            if (refreshed != null) {
                response = block("Bearer $refreshed")
            }
        }
        return response
    }

    fun getCurrentUser(): MalUser = secureStorage.getUser()

    fun logout() {
        secureStorage.clearAuth()
        CacheManager.invalidateTracking()
    }

    /**
     * Fetches the user's anime list from MAL with full pagination (>500 items).
     * Reports partial failure if pages fail after initial success.
     */
    suspend fun fetchUserAnimeList(forceRefresh: Boolean = false): MalFetchResult<List<UserMediaItem>> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = CacheManager.getTracking("ANIME")
            if (cached != null) {
                return@withContext MalFetchResult.Success(cached, cached.size)
            }
        }

        val token = getValidAccessToken()
            ?: return@withContext MalFetchResult.Failure(IllegalStateException("Belum login ke MyAnimeList"))

        val items = mutableListOf<UserMediaItem>()
        var offset = 0
        val pageSize = 500
        var hasMore = true
        var partialError: Throwable? = null

        while (hasMore) {
            try {
                val response = ApiClient.malApi.getUserAnimeList(
                    authHeader = "Bearer $token",
                    limit = pageSize,
                    offset = offset
                )
                val data = response.data
                if (data.isEmpty()) {
                    hasMore = false
                } else {
                    for (entry in data) {
                        val node = entry.node
                        val ls = entry.listStatus
                        val tracking = MalTracking(
                            status = ls.status,
                            score = ls.score,
                            progress = ls.numEpisodesWatched,
                            isRepeating = ls.isRewatching,
                            numTimesRewatched = ls.numTimesRewatched,
                            priority = ls.priority,
                            comments = ls.comments,
                            startDate = ls.startDate,
                            finishDate = ls.finishDate,
                            updatedAt = System.currentTimeMillis()
                        )
                        val metadata = MediaMetadata(
                            title = node.title,
                            imageUrl = node.mainPicture?.large ?: node.mainPicture?.medium ?: "",
                            type = MediaType.ANIME,
                            synopsis = node.synopsis,
                            totalEpisodes = node.numEpisodes,
                            status = node.status,
                            genres = node.genres?.map { it.name } ?: emptyList(),
                            studio = node.studios?.firstOrNull()?.name
                        )
                        val userItem = UserMediaItem(
                            identity = MediaRef(malId = node.id),
                            metadata = metadata,
                            tracking = tracking
                        )
                        items.add(userItem)
                    }

                    if (data.size < pageSize || response.paging?.next == null) {
                        hasMore = false
                    } else {
                        offset += pageSize
                    }
                }
            } catch (e: Exception) {
                Log.e("MalAuthManager", "Error fetching anime list page at offset $offset: ${e.message}")
                partialError = e
                hasMore = false
            }
        }

        if (items.isNotEmpty()) {
            CacheManager.putTracking("ANIME", items)
        }

        when {
            partialError != null && items.isEmpty() -> MalFetchResult.Failure(partialError)
            partialError != null -> MalFetchResult.Partial(items, items.size, partialError)
            else -> MalFetchResult.Success(items, items.size)
        }
    }

    /**
     * Fetches the user's manga list from MAL with full pagination (>500 items).
     */
    suspend fun fetchUserMangaList(forceRefresh: Boolean = false): MalFetchResult<List<UserMediaItem>> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = CacheManager.getTracking("MANGA")
            if (cached != null) {
                return@withContext MalFetchResult.Success(cached, cached.size)
            }
        }

        val token = getValidAccessToken()
            ?: return@withContext MalFetchResult.Failure(IllegalStateException("Belum login ke MyAnimeList"))

        val items = mutableListOf<UserMediaItem>()
        var offset = 0
        val pageSize = 500
        var hasMore = true
        var partialError: Throwable? = null

        while (hasMore) {
            try {
                val response = ApiClient.malApi.getUserMangaList(
                    authHeader = "Bearer $token",
                    limit = pageSize,
                    offset = offset
                )
                val data = response.data
                if (data.isEmpty()) {
                    hasMore = false
                } else {
                    for (entry in data) {
                        val node = entry.node
                        val ls = entry.listStatus
                        val tracking = MalTracking(
                            status = ls.status,
                            score = ls.score,
                            progress = ls.numChaptersRead,
                            progressVolumes = ls.numVolumesRead,
                            isRepeating = ls.isRereading,
                            numTimesRewatched = ls.numTimesReread,
                            priority = ls.priority,
                            comments = ls.comments,
                            startDate = ls.startDate,
                            finishDate = ls.finishDate,
                            updatedAt = System.currentTimeMillis()
                        )
                        val metadata = MediaMetadata(
                            title = node.title,
                            imageUrl = node.mainPicture?.large ?: node.mainPicture?.medium ?: "",
                            type = MediaType.MANGA,
                            synopsis = node.synopsis,
                            totalChapters = node.numChapters,
                            totalVolumes = node.numVolumes,
                            status = node.status,
                            genres = node.genres?.map { it.name } ?: emptyList()
                        )
                        val userItem = UserMediaItem(
                            identity = MediaRef(malId = node.id),
                            metadata = metadata,
                            tracking = tracking
                        )
                        items.add(userItem)
                    }

                    if (data.size < pageSize || response.paging?.next == null) {
                        hasMore = false
                    } else {
                        offset += pageSize
                    }
                }
            } catch (e: Exception) {
                Log.e("MalAuthManager", "Error fetching manga list page at offset $offset: ${e.message}")
                partialError = e
                hasMore = false
            }
        }

        if (items.isNotEmpty()) {
            CacheManager.putTracking("MANGA", items)
        }

        when {
            partialError != null && items.isEmpty() -> MalFetchResult.Failure(partialError)
            partialError != null -> MalFetchResult.Partial(items, items.size, partialError)
            else -> MalFetchResult.Success(items, items.size)
        }
    }

    /**
     * Updates anime tracking data directly on MyAnimeList.
     */
    suspend fun updateAnimeTracking(malId: Int, tracking: MalTracking): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithTokenRefresh { authHeader ->
                ApiClient.malApi.updateAnimeStatus(
                    authHeader = authHeader,
                    animeId = malId,
                    status = tracking.status,
                    score = tracking.score,
                    numEpisodesWatched = tracking.progress,
                    isRewatching = tracking.isRepeating,
                    numTimesRewatched = tracking.numTimesRewatched,
                    priority = tracking.priority,
                    comments = tracking.comments,
                    startDate = tracking.startDate,
                    finishDate = tracking.finishDate
                )
            }
            if (response.isSuccessful) {
                CacheManager.invalidateMedia(null, malId)
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates manga tracking data directly on MyAnimeList.
     */
    suspend fun updateMangaTracking(malId: Int, tracking: MalTracking): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithTokenRefresh { authHeader ->
                ApiClient.malApi.updateMangaStatus(
                    authHeader = authHeader,
                    mangaId = malId,
                    status = tracking.status,
                    score = tracking.score,
                    numChaptersRead = tracking.progress,
                    numVolumesRead = tracking.progressVolumes,
                    isRereading = tracking.isRepeating,
                    numTimesReread = tracking.numTimesRewatched,
                    priority = tracking.priority,
                    comments = tracking.comments,
                    startDate = tracking.startDate,
                    finishDate = tracking.finishDate
                )
            }
            if (response.isSuccessful) {
                CacheManager.invalidateMedia(null, malId)
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an anime from the user's MyAnimeList library.
     */
    suspend fun deleteAnimeTracking(malId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithTokenRefresh { authHeader ->
                ApiClient.malApi.deleteAnimeFromList(authHeader, malId)
            }
            if (response.isSuccessful || response.code() == 404) {
                CacheManager.invalidateMedia(null, malId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Gagal menghapus anime dari MAL (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a manga from the user's MyAnimeList library.
     */
    suspend fun deleteMangaTracking(malId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithTokenRefresh { authHeader ->
                ApiClient.malApi.deleteMangaFromList(authHeader, malId)
            }
            if (response.isSuccessful || response.code() == 404) {
                CacheManager.invalidateMedia(null, malId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Gagal menghapus manga dari MAL (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Performs a full sync/refresh of user library from MyAnimeList.
     */
    suspend fun syncWithMal(): MalSyncResult = withContext(Dispatchers.IO) {
        try {
            // Also refresh profile for location and gender
            runCatching {
                val token = getValidAccessToken()
                if (token != null) {
                    val p = ApiClient.malApi.getUserProfile("Bearer $token")
                    secureStorage.saveUserProfile(p.id, p.name, p.picture, p.location, p.gender)
                }
            }

            val animeResult = fetchUserAnimeList(forceRefresh = true)
            val mangaResult = fetchUserMangaList(forceRefresh = true)

            val animeCount = when (animeResult) {
                is MalFetchResult.Success -> animeResult.totalItems
                is MalFetchResult.Partial -> animeResult.fetchedItems
                is MalFetchResult.Failure -> 0
            }
            val mangaCount = when (mangaResult) {
                is MalFetchResult.Success -> mangaResult.totalItems
                is MalFetchResult.Partial -> mangaResult.fetchedItems
                is MalFetchResult.Failure -> 0
            }

            val isPartial = animeResult is MalFetchResult.Partial || mangaResult is MalFetchResult.Partial
            val isFailure = animeResult is MalFetchResult.Failure && mangaResult is MalFetchResult.Failure
            val err = (animeResult as? MalFetchResult.Failure)?.error?.message
                ?: (mangaResult as? MalFetchResult.Failure)?.error?.message
                ?: (animeResult as? MalFetchResult.Partial)?.error?.message
                ?: (mangaResult as? MalFetchResult.Partial)?.error?.message

            secureStorage.setLastSynced()

            MalSyncResult(
                animeSynced = animeCount,
                mangaSynced = mangaCount,
                isSuccess = !isFailure,
                isPartial = isPartial,
                errorMessage = err
            )
        } catch (e: Exception) {
            Log.e("MalAuthManager", "Sinkronisasi MAL gagal: ${e.message}")
            MalSyncResult(
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Gagal terhubung ke MyAnimeList"
            )
        }
    }

    /**
     * Fallback public metadata retrieval from MyAnimeList if AniList lacks specific details.
     */
    suspend fun getMetadataFallback(malId: Int, type: MediaType): MediaItem? = withContext(Dispatchers.IO) {
        val cacheKey = CacheManager.malFallbackKey(malId, type.name)
        val cached = CacheManager.getMalFallback(cacheKey)
        if (cached != null) return@withContext cached

        try {
            if (type == MediaType.ANIME) {
                val response = ApiClient.malApi.getAnimeDetailFallback(CLIENT_ID, malId)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val item = MediaItem(
                        malId = body.id,
                        title = body.title,
                        imageUrl = body.mainPicture?.large ?: body.mainPicture?.medium ?: "",
                        type = MediaType.ANIME,
                        synopsis = body.synopsis,
                        episodes = body.numEpisodes,
                        status = body.status,
                        genres = body.genres?.map { it.name } ?: emptyList(),
                        studio = body.studios?.firstOrNull()?.name
                    )
                    CacheManager.putMalFallback(cacheKey, item)
                    return@withContext item
                }
            } else {
                val response = ApiClient.malApi.getMangaDetailFallback(CLIENT_ID, malId)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val item = MediaItem(
                        malId = body.id,
                        title = body.title,
                        imageUrl = body.mainPicture?.large ?: body.mainPicture?.medium ?: "",
                        type = MediaType.MANGA,
                        synopsis = body.synopsis,
                        chapters = body.numChapters,
                        volumes = body.numVolumes,
                        status = body.status,
                        genres = body.genres?.map { it.name } ?: emptyList()
                    )
                    CacheManager.putMalFallback(cacheKey, item)
                    return@withContext item
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Fallback full metadata retrieval from MyAnimeList when AniList returns null or incomplete info.
     */
    suspend fun getExtendedDetailFallback(malId: Int, type: MediaType): ExtendedMediaDetail? = withContext(Dispatchers.IO) {
        val cacheKey = CacheManager.detailKey(null, malId)
        val cached = CacheManager.getDetail(cacheKey)
        if (cached != null) return@withContext cached

        try {
            if (type == MediaType.ANIME) {
                val response = ApiClient.malApi.getAnimeDetailFallback(CLIENT_ID, malId)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val ext = ExtendedMediaDetail(
                        malId = body.id,
                        title = body.title,
                        studio = body.studios?.firstOrNull()?.name,
                        source = body.source,
                        airingStatus = body.status,
                        startDate = body.startDate,
                        endDate = body.endDate,
                        genres = body.genres?.map { it.name } ?: emptyList(),
                        malScore = body.mean,
                        malRank = body.rank,
                        malPopularity = body.popularity,
                        malMembers = body.numListUsers,
                        rank = body.rank,
                        popularity = body.popularity,
                        watchers = body.numListUsers,
                        isFromFallback = true
                    )
                    CacheManager.putDetail(cacheKey, ext)
                    return@withContext ext
                }
            } else {
                val response = ApiClient.malApi.getMangaDetailFallback(CLIENT_ID, malId)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val ext = ExtendedMediaDetail(
                        malId = body.id,
                        title = body.title,
                        publisher = body.authors?.firstOrNull()?.name,
                        airingStatus = body.status,
                        startDate = body.startDate,
                        endDate = body.endDate,
                        genres = body.genres?.map { it.name } ?: emptyList(),
                        malScore = body.mean,
                        malRank = body.rank,
                        malPopularity = body.popularity,
                        malMembers = body.numListUsers,
                        rank = body.rank,
                        popularity = body.popularity,
                        watchers = body.numListUsers,
                        isFromFallback = true
                    )
                    CacheManager.putDetail(cacheKey, ext)
                    return@withContext ext
                }
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun getMalUserTracking(malId: Int, type: MediaType): MalTracking? = withContext(Dispatchers.IO) {
        val token = getValidAccessToken() ?: return@withContext null
        try {
            if (type == MediaType.ANIME) {
                val response = ApiClient.malApi.getAnimeDetailAuth("Bearer $token", malId)
                if (response.isSuccessful && response.body() != null) {
                    val statusObj = response.body()!!.myListStatus ?: return@withContext null
                    return@withContext MalTracking(
                        status = statusObj.status,
                        score = statusObj.score,
                        progress = statusObj.numEpisodesWatched,
                        isRepeating = statusObj.isRewatching,
                        numTimesRewatched = statusObj.numTimesRewatched,
                        comments = statusObj.comments,
                        startDate = statusObj.startDate,
                        finishDate = statusObj.finishDate,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            } else {
                val response = ApiClient.malApi.getMangaDetailAuth("Bearer $token", malId)
                if (response.isSuccessful && response.body() != null) {
                    val statusObj = response.body()!!.myListStatus ?: return@withContext null
                    return@withContext MalTracking(
                        status = statusObj.status,
                        score = statusObj.score,
                        progress = statusObj.numChaptersRead,
                        isRepeating = statusObj.isRereading,
                        numTimesRewatched = statusObj.numTimesReread,
                        comments = statusObj.comments,
                        startDate = statusObj.startDate,
                        finishDate = statusObj.finishDate,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        } catch (_: Exception) {}
        null
    }
}
