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
        @Query("fields") fields: String = "name,picture,location,gender"
    ): MalUserProfile

    @GET("users/@me/animelist")
    suspend fun getUserAnimeList(
        @Header("Authorization") authHeader: String,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "list_status{status,score,num_episodes_watched,is_rewatching,num_times_rewatched,priority,comments,start_date,finish_date,updated_at},num_episodes,status,genres,main_picture,synopsis,start_date,end_date,studios,source",
        @Query("nsfw") nsfw: Boolean = true
    ): MalAnimeListResponse

    @GET("users/@me/mangalist")
    suspend fun getUserMangaList(
        @Header("Authorization") authHeader: String,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = "list_status{status,score,num_chapters_read,num_volumes_read,is_rereading,num_times_reread,priority,comments,start_date,finish_date,updated_at},num_chapters,num_volumes,status,genres,main_picture,synopsis,start_date,end_date,authors",
        @Query("nsfw") nsfw: Boolean = true
    ): MalMangaListResponse

    @FormUrlEncoded
    @PUT("anime/{anime_id}/my_list_status")
    suspend fun updateAnimeStatus(
        @Header("Authorization") authHeader: String,
        @Path("anime_id") animeId: Int,
        @Field("status") status: String? = null,
        @Field("score") score: Int? = null,
        @Field("num_watched_episodes") numEpisodesWatched: Int? = null,
        @Field("is_rewatching") isRewatching: Boolean? = null,
        @Field("num_times_rewatched") numTimesRewatched: Int? = null,
        @Field("priority") priority: Int? = null,
        @Field("comments") comments: String? = null,
        @Field("tags") tags: String? = null,
        @Field("start_date") startDate: String? = null,
        @Field("finish_date") finishDate: String? = null
    ): Response<ResponseBody>

    @DELETE("anime/{anime_id}/my_list_status")
    suspend fun deleteAnimeFromList(
        @Header("Authorization") authHeader: String,
        @Path("anime_id") animeId: Int
    ): Response<ResponseBody>

    @FormUrlEncoded
    @PUT("manga/{manga_id}/my_list_status")
    suspend fun updateMangaStatus(
        @Header("Authorization") authHeader: String,
        @Path("manga_id") mangaId: Int,
        @Field("status") status: String? = null,
        @Field("score") score: Int? = null,
        @Field("num_chapters_read") numChaptersRead: Int? = null,
        @Field("num_volumes_read") numVolumesRead: Int? = null,
        @Field("is_rereading") isRereading: Boolean? = null,
        @Field("num_times_reread") numTimesReread: Int? = null,
        @Field("priority") priority: Int? = null,
        @Field("comments") comments: String? = null,
        @Field("tags") tags: String? = null,
        @Field("start_date") startDate: String? = null,
        @Field("finish_date") finishDate: String? = null
    ): Response<ResponseBody>

    @DELETE("manga/{manga_id}/my_list_status")
    suspend fun deleteMangaFromList(
        @Header("Authorization") authHeader: String,
        @Path("manga_id") mangaId: Int
    ): Response<ResponseBody>

    // Public Metadata Fallback Endpoints
    @GET("anime/{anime_id}")
    suspend fun getAnimeDetailFallback(
        @Header("X-MAL-CLIENT-ID") clientId: String,
        @Path("anime_id") animeId: Int,
        @Query("fields") fields: String = "id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank,popularity,num_list_users,num_episodes,status,genres,my_list_status,studios"
    ): Response<MalAnimeNode>

    @GET("manga/{manga_id}")
    suspend fun getMangaDetailFallback(
        @Header("X-MAL-CLIENT-ID") clientId: String,
        @Path("manga_id") mangaId: Int,
        @Query("fields") fields: String = "id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank,popularity,num_list_users,num_chapters,num_volumes,status,genres,my_list_status,authors"
    ): Response<MalMangaNode>

    @GET("anime/{anime_id}")
    suspend fun getAnimeDetailAuth(
        @Header("Authorization") authHeader: String,
        @Path("anime_id") animeId: Int,
        @Query("fields") fields: String = "id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank,popularity,num_list_users,num_episodes,status,genres,my_list_status,studios"
    ): Response<MalAnimeNode>

    @GET("manga/{manga_id}")
    suspend fun getMangaDetailAuth(
        @Header("Authorization") authHeader: String,
        @Path("manga_id") mangaId: Int,
        @Query("fields") fields: String = "id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank,popularity,num_list_users,num_chapters,num_volumes,status,genres,my_list_status,authors"
    ): Response<MalMangaNode>
}
