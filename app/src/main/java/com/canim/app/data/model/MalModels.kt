package com.canim.app.data.model

import com.google.gson.annotations.SerializedName

data class MalTokenResponse(
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String?
)

data class MalUserProfile(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("picture") val picture: String?
)

data class MalUser(
    val id: Long = 0,
    val username: String = "",
    val pictureUrl: String? = null,
    val isLoggedIn: Boolean = false
)

data class MalAnimeListResponse(
    @SerializedName("data") val data: List<MalAnimeNodeItem>,
    @SerializedName("paging") val paging: MalPaging?
)

data class MalMangaListResponse(
    @SerializedName("data") val data: List<MalMangaNodeItem>,
    @SerializedName("paging") val paging: MalPaging?
)

data class MalPaging(
    @SerializedName("next") val next: String?,
    @SerializedName("previous") val previous: String?
)

data class MalAnimeNodeItem(
    @SerializedName("node") val node: MalAnimeNode,
    @SerializedName("list_status") val listStatus: MalAnimeListStatus
)

data class MalAnimeNode(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("main_picture") val mainPicture: MalPicture?,
    @SerializedName("num_episodes") val numEpisodes: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("genres") val genres: List<MalGenre>?,
    @SerializedName("synopsis") val synopsis: String?,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("studios") val studios: List<MalGenre>? = null,
    @SerializedName("source") val source: String? = null
)

data class MalMangaNodeItem(
    @SerializedName("node") val node: MalMangaNode,
    @SerializedName("list_status") val listStatus: MalMangaListStatus
)

data class MalMangaNode(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("main_picture") val mainPicture: MalPicture?,
    @SerializedName("num_chapters") val numChapters: Int?,
    @SerializedName("num_volumes") val numVolumes: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("genres") val genres: List<MalGenre>?,
    @SerializedName("synopsis") val synopsis: String?,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("authors") val authors: List<MalGenre>? = null
)

data class MalPicture(
    @SerializedName("medium") val medium: String?,
    @SerializedName("large") val large: String?
)

data class MalGenre(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)

data class MalAnimeListStatus(
    @SerializedName("status") val status: String,
    @SerializedName("score") val score: Int,
    @SerializedName("num_episodes_watched") val numEpisodesWatched: Int,
    @SerializedName("is_rewatching") val isRewatching: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class MalMangaListStatus(
    @SerializedName("status") val status: String,
    @SerializedName("score") val score: Int,
    @SerializedName("num_chapters_read") val numChaptersRead: Int,
    @SerializedName("num_volumes_read") val numVolumesRead: Int = 0,
    @SerializedName("is_rereading") val isRereading: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class MalSyncResult(
    val animeSynced: Int = 0,
    val mangaSynced: Int = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)
