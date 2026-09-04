package com.canim.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "manga_items",
    indices = [
        Index(value = ["malId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["updatedAt"])
    ]
)
data class MangaEntity(
    @PrimaryKey val id: String, // e.g. "manga_13"
    val malId: Int,
    val anilistId: Int? = null,
    val title: String,
    val titleEnglish: String? = null,
    val imageUrl: String,
    val status: String = "reading", // "reading", "completed", "on_hold", "dropped", "plan_to_read"
    val score: Int = 0, // 0-10
    val progressChapters: Int = 0,
    val progressVolumes: Int = 0,
    val totalChapters: Int = 0,
    val totalVolumes: Int = 0,
    val publishingStatus: String = "Publishing",
    val genres: String = "",
    val synopsis: String = "",
    val year: Int? = null,
    val notes: String = "",
    val rereads: Int = 0,
    val author: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "local_only"
)
