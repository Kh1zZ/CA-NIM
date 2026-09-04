package com.canim.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    @Query("SELECT * FROM anime_items ORDER BY updatedAt DESC")
    fun getAllAnime(): Flow<List<AnimeEntity>>

    @Query("SELECT * FROM anime_items WHERE id = :id LIMIT 1")
    fun getAnimeById(id: String): Flow<AnimeEntity?>

    @Query("SELECT * FROM anime_items WHERE malId = :malId LIMIT 1")
    suspend fun getAnimeByMalId(malId: Int): AnimeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anime: AnimeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<AnimeEntity>)

    @Update
    suspend fun update(anime: AnimeEntity)

    @Delete
    suspend fun delete(anime: AnimeEntity)

    @Query("DELETE FROM anime_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM anime_items")
    suspend fun clearAll()

    @Query("""
        UPDATE anime_items 
        SET progress = MAX(0, MIN(progress + :amount, CASE WHEN totalEpisodes > 0 THEN totalEpisodes ELSE progress + :amount END)),
            updatedAt = :timestamp 
        WHERE id = :id
    """)
    suspend fun incrementProgress(id: String, amount: Int, timestamp: Long)
}
