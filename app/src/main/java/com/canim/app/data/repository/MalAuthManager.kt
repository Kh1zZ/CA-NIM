package com.canim.app.data.repository

import android.net.Uri
import android.util.Log
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.AnimeDao
import com.canim.app.data.local.AnimeEntity
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.local.MangaDao
import com.canim.app.data.local.MangaEntity
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MalSyncResult
import com.canim.app.data.model.MalUser
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

class MalAuthManager(
    private val secureStorage: MalSecureStorage,
    private val animeDao: AnimeDao,
    private val mangaDao: MangaDao
) {
    companion object {
        const val CLIENT_ID = "a4f3b20e6eb04e9daac4d2ea9fb2a45a"
        const val REDIRECT_URI = "canim://oauth/callback"
        const val AUTH_BASE_URL = "https://myanimelist.net/v1/oauth2/authorize"
        private const val PKCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    }

    private val secureRandom = SecureRandom()

    fun generateRandomString(length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(PKCE_CHARS[secureRandom.nextInt(PKCE_CHARS.length)])
        }
        return sb.toString()
    }

    /**
     * Builds the MyAnimeList OAuth2 Authorization URL with PKCE (plain method).
     */
    fun buildAuthorizeUrl(): String {
        val codeVerifier = generateRandomString(128)
        val state = generateRandomString(32)

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
     * Runs strictly in the background.
     */
    suspend fun handleOAuthCallback(code: String, state: String?): Result<MalUser> = withContext(Dispatchers.IO) {
        try {
            val savedState = secureStorage.getPkceState()
            if (savedState != null && state != null && savedState != state) {
                Log.w("MalAuthManager", "OAuth state mismatch (possible CSRF), proceeding with caution")
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
                pictureUrl = profile.picture
            )

            val malUser = MalUser(
                id = profile.id,
                username = profile.name,
                pictureUrl = profile.picture,
                isLoggedIn = true
            )

            Result.success(malUser)
        } catch (e: Exception) {
            Log.e("MalAuthManager", "Gagal menukar token MAL", e)
            Result.failure(e)
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
            Log.e("MalAuthManager", "Gagal refresh token MAL, menggunakan token saat ini", e)
            currentToken
        }
    }

    fun getCurrentUser(): MalUser = secureStorage.getUser()

    fun logout() {
        secureStorage.clearAuth()
    }

    /**
     * Synchronizes Anime and Manga list from MyAnimeList into local Room Database with FULL PAGINATION.
     * No hard limit of 500 items! Supports 1000+, 3000+ items seamlessly.
     */
    suspend fun syncWithMal(): MalSyncResult = withContext(Dispatchers.IO) {
        try {
            val token = getValidAccessToken()
                ?: return@withContext MalSyncResult(
                    isSuccess = false,
                    errorMessage = "Belum login ke MyAnimeList"
                )

            val authHeader = "Bearer $token"
            val pageSize = 500

            // 1. Fetch Anime List with Full Pagination
            var animeCount = 0
            var animeOffset = 0
            var hasMoreAnime = true

            while (hasMoreAnime) {
                try {
                    val animeResponse = ApiClient.malApi.getUserAnimeList(
                        authHeader = authHeader,
                        limit = pageSize,
                        offset = animeOffset
                    )
                    val data = animeResponse.data
                    if (data.isEmpty()) {
                        hasMoreAnime = false
                    } else {
                        val animeBatch = ArrayList<AnimeEntity>(data.size)
                        for (item in data) {
                            val node = item.node
                            val listStatus = item.listStatus

                            val mappedStatus = when (listStatus.status) {
                                "watching" -> "watching"
                                "completed" -> "completed"
                                "on_hold" -> "on_hold"
                                "dropped" -> "dropped"
                                "plan_to_watch" -> "plan_to_watch"
                                else -> "watching"
                            }

                            val resolvedAniListId = CacheManager.getAniListIdForMalId(node.id)

                            val entity = AnimeEntity(
                                id = "anime_${node.id}",
                                malId = node.id,
                                anilistId = resolvedAniListId,
                                title = node.title,
                                titleEnglish = null,
                                imageUrl = node.mainPicture?.large ?: node.mainPicture?.medium ?: "",
                                status = mappedStatus,
                                score = listStatus.score,
                                progress = listStatus.numEpisodesWatched,
                                totalEpisodes = node.numEpisodes ?: 0,
                                airingStatus = node.status ?: "Finished Airing",
                                genres = node.genres?.joinToString(", ") { it.name } ?: "",
                                synopsis = node.synopsis ?: "",
                                year = null,
                                season = null,
                                notes = "",
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = "synced"
                            )
                            animeBatch.add(entity)
                        }
                        animeDao.insertAll(animeBatch)
                        animeCount += animeBatch.size

                        if (data.size < pageSize || animeResponse.paging?.next == null) {
                            hasMoreAnime = false
                        } else {
                            animeOffset += pageSize
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MalAuthManager", "Error syncing anime list page from MAL at offset $animeOffset", e)
                    hasMoreAnime = false
                }
            }

            // 2. Fetch Manga List with Full Pagination
            var mangaCount = 0
            var mangaOffset = 0
            var hasMoreManga = true

            while (hasMoreManga) {
                try {
                    val mangaResponse = ApiClient.malApi.getUserMangaList(
                        authHeader = authHeader,
                        limit = pageSize,
                        offset = mangaOffset
                    )
                    val data = mangaResponse.data
                    if (data.isEmpty()) {
                        hasMoreManga = false
                    } else {
                        val mangaBatch = ArrayList<MangaEntity>(data.size)
                        for (item in data) {
                            val node = item.node
                            val listStatus = item.listStatus

                            val mappedStatus = when (listStatus.status) {
                                "reading" -> "reading"
                                "completed" -> "completed"
                                "on_hold" -> "on_hold"
                                "dropped" -> "dropped"
                                "plan_to_read" -> "plan_to_read"
                                else -> "reading"
                            }

                            val resolvedAniListId = CacheManager.getAniListIdForMalId(node.id)

                            val entity = MangaEntity(
                                id = "manga_${node.id}",
                                malId = node.id,
                                anilistId = resolvedAniListId,
                                title = node.title,
                                titleEnglish = null,
                                imageUrl = node.mainPicture?.large ?: node.mainPicture?.medium ?: "",
                                status = mappedStatus,
                                score = listStatus.score,
                                progressChapters = listStatus.numChaptersRead,
                                progressVolumes = listStatus.numVolumesRead,
                                totalChapters = node.numChapters ?: 0,
                                totalVolumes = node.numVolumes ?: 0,
                                publishingStatus = node.status ?: "Finished",
                                genres = node.genres?.joinToString(", ") { it.name } ?: "",
                                synopsis = node.synopsis ?: "",
                                year = null,
                                notes = "",
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = "synced"
                            )
                            mangaBatch.add(entity)
                        }
                        mangaDao.insertAll(mangaBatch)
                        mangaCount += mangaBatch.size

                        if (data.size < pageSize || mangaResponse.paging?.next == null) {
                            hasMoreManga = false
                        } else {
                            mangaOffset += pageSize
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MalAuthManager", "Error syncing manga list page from MAL at offset $mangaOffset", e)
                    hasMoreManga = false
                }
            }

            secureStorage.setLastSynced()

            MalSyncResult(
                animeSynced = animeCount,
                mangaSynced = mangaCount,
                isSuccess = true
            )
        } catch (e: Exception) {
            Log.e("MalAuthManager", "Sinkronisasi MAL gagal", e)
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
        val cacheKey = "mal_fallback_${malId}_${type.name}"
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
        val cacheKey = "mal_ext_fallback_${malId}_${type.name}"
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
                        isFromFallback = true
                    )
                    CacheManager.putDetail(cacheKey, ext)
                    return@withContext ext
                }
            }
        } catch (_: Exception) {}
        null
    }
}
