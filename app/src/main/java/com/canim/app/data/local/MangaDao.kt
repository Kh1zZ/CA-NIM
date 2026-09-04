package com.canim.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga_items ORDER BY updatedAt DESC")
    fun getAllManga(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga_items WHERE id = :id LIMIT 1")
    fun getMangaById(id: String): Flow<MangaEntity?>

    @Query("SELECT * FROM manga_items WHERE malId = :malId LIMIT 1")
    suspend fun getMangaByMalId(malId: Int): MangaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manga: MangaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<MangaEntity>)

    @Update
    suspend fun update(manga: MangaEntity)

    @Delete
    suspend fun delete(manga: MangaEntity)

    @Query("DELETE FROM manga_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM manga_items")
    suspend fun clearAll()

    @Query("""
        UPDATE manga_items 
        SET progressChapters = MAX(0, MIN(progressChapters + :amount, CASE WHEN totalChapters > 0 THEN totalChapters ELSE progressChapters + :amount END)),
            updatedAt = :timestamp 
        WHERE id = :id
    """)
    suspend fun incrementProgress(id: String, amount: Int, timestamp: Long)
}
