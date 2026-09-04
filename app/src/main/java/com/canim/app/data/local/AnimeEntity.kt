package com.canim.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "anime_items",
    indices = [
        Index(value = ["malId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["updatedAt"])
    ]
)
data class AnimeEntity(
    @PrimaryKey val id: String, // e.g. "anime_16498"
    val malId: Int,
    val anilistId: Int? = null,
    val title: String,
    val titleEnglish: String? = null,
    val imageUrl: String,
    val status: String = "watching", // "watching", "completed", "on_hold", "dropped", "plan_to_watch"
    val score: Int = 0, // 0-10
    val progress: Int = 0, // watched episodes
    val totalEpisodes: Int = 0,
    val airingStatus: String = "Finished Airing",
    val genres: String = "",
    val synopsis: String = "",
    val year: Int? = null,
    val season: String? = null,
    val notes: String = "",
    val rewatches: Int = 0,
    val studio: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "local_only"
)
