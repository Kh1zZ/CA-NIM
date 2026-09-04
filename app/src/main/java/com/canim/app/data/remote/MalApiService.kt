package com.canim.app.data.remote

import com.canim.app.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface MalApiService {

    @FormUrlEncoded
    @POST("https://myanimelist.net/v1/oauth2/token")
    suspend fun exchangeToken(
        @Field("client_id") clientId: String,
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("redirect_uri") redirectUri: String
    ): MalTokenResponse

    @FormUrlEncoded
    @POST("https://myanimelist.net/v1/oauth2/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): MalTokenResponse

    @GET("users/@me")
    suspend fun getUserProfile(
        @Header("Authorization") authHeader: String,
        @Query("fields") fields: String = "name,picture"
    ): MalUserProfile

    @GET("users/@me/animelist")
    suspend fun getUserAnimeList(
        @Header("Authorization") authHeader: String,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "list_status,num_episodes,status,genres,main_picture,synopsis,start_date,end_date",
        @Query("nsfw") nsfw: Boolean = true
    ): MalAnimeListResponse

    @GET("users/@me/mangalist")
    suspend fun getUserMangaList(
        @Header("Authorization") authHeader: String,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "list_status,num_chapters,num_volumes,status,genres,main_picture,synopsis,start_date,end_date",
        @Query("nsfw") nsfw: Boolean = true
    ): MalMangaListResponse

    @FormUrlEncoded
    @PUT("anime/{anime_id}/my_list_status")
    suspend fun updateAnimeStatus(
        @Header("Authorization") authHeader: String,
        @Path("anime_id") animeId: Int,
        @Field("status") status: String?,
        @Field("score") score: Int?,
        @Field("num_watched_episodes") numEpisodesWatched: Int?
    ): Response<ResponseBody>

    @FormUrlEncoded
    @PUT("manga/{manga_id}/my_list_status")
    suspend fun updateMangaStatus(
        @Header("Authorization") authHeader: String,
        @Path("manga_id") mangaId: Int,
        @Field("status") status: String?,
        @Field("score") score: Int?,
        @Field("num_chapters_read") numChaptersRead: Int?,
        @Field("num_volumes_read") numVolumesRead: Int?
    ): Response<ResponseBody>

    // Public Metadata Fallback Endpoints
    @GET("anime/{anime_id}")
    suspend fun getAnimeDetailFallback(
        @Header("X-MAL-CLIENT-ID") clientId: String,
        @Path("anime_id") animeId: Int,
        @Query("fields") fields: String = "id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank,popularity,num_episodes,status,genres,my_list_status,studios"
    ): Response<MalAnimeNode>

    @GET("manga/{manga_id}")
    suspend fun getMangaDetailFallback(
        @Header("X-MAL-CLIENT-ID") clientId: String,
        @Path("manga_id") mangaId: Int,
        @Query("fields") fields: String = "id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank,popularity,num_chapters,num_volumes,status,genres,my_list_status"
    ): Response<MalMangaNode>
}
