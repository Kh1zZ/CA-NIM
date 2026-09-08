package com.canim.app.data.local

import android.content.ContentValues
import android.database.Cursor
import android.util.Log
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_ANILIST_ID
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_AIRING_STATUS
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_COMMENTS
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_FINISH_DATE
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_FORMAT
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_GENRES_JSON
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_IMAGE_URL
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_IS_REPEATING
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_LOCAL_UPDATED_AT
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_MAL_ID
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_MEDIA_TYPE
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_NUM_TIMES_REWATCHED
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_PRIORITY
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_PROGRESS
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_PROGRESS_VOLUMES
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_REWATCH_VALUE
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_SCORE
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_SEASON
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_START_DATE
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_STATUS
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_STUDIO
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_SYNCED_AT
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_TAGS_JSON
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_TITLE
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_TITLE_ENGLISH
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_TOTAL_CHAPTERS
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_TOTAL_EPISODES
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_TOTAL_VOLUMES
import com.canim.app.data.local.LocalDatabase.Companion.COL_LIB_YEAR
import com.canim.app.data.local.LocalDatabase.Companion.TABLE_LIBRARY_ENTRIES

/**
 * Data Access Object for [TABLE_LIBRARY_ENTRIES].
 *
 * All operations are synchronous SQLite calls — callers are responsible for
 * dispatching to an appropriate background dispatcher (Dispatchers.IO).
 *
 * Uses INSERT OR REPLACE for upsert semantics (SQLite CONFLICT_REPLACE).
 */
class LibraryDao(private val db: LocalDatabase) {

    /**
     * Inserts or replaces a [LibraryEntry] atomically.
     * If an entry with the same (malId, mediaType) PK exists, it is replaced entirely.
     */
    fun upsertEntry(entry: LibraryEntry) {
        try {
            val cv = entry.toContentValues()
            db.writableDatabase.insertWithOnConflict(
                TABLE_LIBRARY_ENTRIES,
                null,
                cv,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            Log.e("LibraryDao", "upsertEntry failed for malId=${entry.malId}: ${e.message}", e)
            throw e
        }
    }

    /**
     * Returns all library entries for the given [mediaType] ("ANIME" or "MANGA"),
     * ordered by [COL_LIB_LOCAL_UPDATED_AT] descending (most recently modified first).
     */
    fun getAllEntries(mediaType: String): List<LibraryEntry> {
        return try {
            val cursor = db.readableDatabase.query(
                TABLE_LIBRARY_ENTRIES,
                null,
                "$COL_LIB_MEDIA_TYPE = ?",
                arrayOf(mediaType),
                null, null,
                "$COL_LIB_LOCAL_UPDATED_AT DESC"
            )
            cursor.use { it.toLibraryEntryList() }
        } catch (e: Exception) {
            Log.e("LibraryDao", "getAllEntries failed for type=$mediaType: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Returns a single [LibraryEntry] by (malId, mediaType), or null if not found.
     */
    fun getEntry(malId: Int, mediaType: String): LibraryEntry? {
        return try {
            val cursor = db.readableDatabase.query(
                TABLE_LIBRARY_ENTRIES,
                null,
                "$COL_LIB_MAL_ID = ? AND $COL_LIB_MEDIA_TYPE = ?",
                arrayOf(malId.toString(), mediaType),
                null, null, null
            )
            cursor.use { if (it.moveToFirst()) it.toLibraryEntry() else null }
        } catch (e: Exception) {
            Log.e("LibraryDao", "getEntry failed for malId=$malId type=$mediaType: ${e.message}", e)
            null
        }
    }

    /**
     * Deletes the entry with the given (malId, mediaType) composite key.
     */
    fun deleteEntry(malId: Int, mediaType: String) {
        try {
            db.writableDatabase.delete(
                TABLE_LIBRARY_ENTRIES,
                "$COL_LIB_MAL_ID = ? AND $COL_LIB_MEDIA_TYPE = ?",
                arrayOf(malId.toString(), mediaType)
            )
        } catch (e: Exception) {
            Log.e("LibraryDao", "deleteEntry failed for malId=$malId type=$mediaType: ${e.message}", e)
        }
    }

    /**
     * Marks an entry as synced by updating [COL_LIB_SYNCED_AT] to the given timestamp.
     */
    fun markSynced(malId: Int, mediaType: String, syncedAt: Long = System.currentTimeMillis()) {
        try {
            val cv = ContentValues().apply {
                put(COL_LIB_SYNCED_AT, syncedAt)
            }
            db.writableDatabase.update(
                TABLE_LIBRARY_ENTRIES,
                cv,
                "$COL_LIB_MAL_ID = ? AND $COL_LIB_MEDIA_TYPE = ?",
                arrayOf(malId.toString(), mediaType)
            )
        } catch (e: Exception) {
            Log.e("LibraryDao", "markSynced failed for malId=$malId: ${e.message}", e)
        }
    }

    /**
     * Returns the set of malIds that have entries in the DB for the given [mediaType].
     * Used during force-refresh reconciliation to determine which entries exist locally.
     */
    fun getAllMalIds(mediaType: String): Set<Int> {
        return try {
            val cursor = db.readableDatabase.query(
                TABLE_LIBRARY_ENTRIES,
                arrayOf(COL_LIB_MAL_ID),
                "$COL_LIB_MEDIA_TYPE = ?",
                arrayOf(mediaType),
                null, null, null
            )
            cursor.use {
                val ids = mutableSetOf<Int>()
                while (it.moveToNext()) {
                    ids.add(it.getInt(0))
                }
                ids
            }
        } catch (e: Exception) {
            Log.e("LibraryDao", "getAllMalIds failed for type=$mediaType: ${e.message}", e)
            emptySet()
        }
    }

    /** Deletes all entries from [TABLE_LIBRARY_ENTRIES]. */
    fun clearAll() {
        try {
            db.writableDatabase.delete(TABLE_LIBRARY_ENTRIES, null, null)
        } catch (e: Exception) {
            Log.e("LibraryDao", "clearAll failed: ${e.message}", e)
        }
    }

    /** Deletes all entries of a specific [mediaType] from [TABLE_LIBRARY_ENTRIES]. */
    fun clearAllForType(mediaType: String) {
        try {
            db.writableDatabase.delete(
                TABLE_LIBRARY_ENTRIES,
                "$COL_LIB_MEDIA_TYPE = ?",
                arrayOf(mediaType)
            )
        } catch (e: Exception) {
            Log.e("LibraryDao", "clearAllForType failed for type=$mediaType: ${e.message}", e)
        }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun LibraryEntry.toContentValues(): ContentValues = ContentValues().apply {
        put(COL_LIB_MAL_ID, malId)
        put(COL_LIB_MEDIA_TYPE, mediaType)
        put(COL_LIB_STATUS, status)
        put(COL_LIB_SCORE, score)
        put(COL_LIB_PROGRESS, progress)
        put(COL_LIB_PROGRESS_VOLUMES, progressVolumes)
        put(COL_LIB_IS_REPEATING, isRepeating)
        put(COL_LIB_NUM_TIMES_REWATCHED, numTimesRewatched)
        put(COL_LIB_REWATCH_VALUE, rewatchValue)
        put(COL_LIB_PRIORITY, priority)
        put(COL_LIB_TAGS_JSON, tagsJson)
        put(COL_LIB_COMMENTS, comments)
        put(COL_LIB_START_DATE, startDate)
        put(COL_LIB_FINISH_DATE, finishDate)
        put(COL_LIB_TITLE, title)
        put(COL_LIB_TITLE_ENGLISH, titleEnglish)
        put(COL_LIB_IMAGE_URL, imageUrl)
        put(COL_LIB_TOTAL_EPISODES, totalEpisodes)
        put(COL_LIB_TOTAL_CHAPTERS, totalChapters)
        put(COL_LIB_TOTAL_VOLUMES, totalVolumes)
        put(COL_LIB_AIRING_STATUS, airingStatus)
        put(COL_LIB_YEAR, year)
        put(COL_LIB_SEASON, season)
        put(COL_LIB_GENRES_JSON, genresJson)
        put(COL_LIB_FORMAT, format)
        put(COL_LIB_STUDIO, studio)
        put(COL_LIB_ANILIST_ID, anilistId)
        put(COL_LIB_LOCAL_UPDATED_AT, localUpdatedAt)
        put(COL_LIB_SYNCED_AT, syncedAt)
    }

    private fun Cursor.toLibraryEntryList(): List<LibraryEntry> {
        val list = mutableListOf<LibraryEntry>()
        while (moveToNext()) {
            list.add(toLibraryEntry())
        }
        return list
    }

    private fun Cursor.toLibraryEntry(): LibraryEntry = LibraryEntry(
        malId = getInt(getColumnIndexOrThrow(COL_LIB_MAL_ID)),
        mediaType = getString(getColumnIndexOrThrow(COL_LIB_MEDIA_TYPE)),
        status = getString(getColumnIndexOrThrow(COL_LIB_STATUS)) ?: "watching",
        score = getInt(getColumnIndexOrThrow(COL_LIB_SCORE)),
        progress = getInt(getColumnIndexOrThrow(COL_LIB_PROGRESS)),
        progressVolumes = getInt(getColumnIndexOrThrow(COL_LIB_PROGRESS_VOLUMES)),
        isRepeating = getInt(getColumnIndexOrThrow(COL_LIB_IS_REPEATING)),
        numTimesRewatched = getInt(getColumnIndexOrThrow(COL_LIB_NUM_TIMES_REWATCHED)),
        rewatchValue = getInt(getColumnIndexOrThrow(COL_LIB_REWATCH_VALUE)),
        priority = getInt(getColumnIndexOrThrow(COL_LIB_PRIORITY)),
        tagsJson = getString(getColumnIndexOrThrow(COL_LIB_TAGS_JSON)) ?: "[]",
        comments = getString(getColumnIndexOrThrow(COL_LIB_COMMENTS)),
        startDate = getString(getColumnIndexOrThrow(COL_LIB_START_DATE)),
        finishDate = getString(getColumnIndexOrThrow(COL_LIB_FINISH_DATE)),
        title = getString(getColumnIndexOrThrow(COL_LIB_TITLE)) ?: "",
        titleEnglish = getString(getColumnIndexOrThrow(COL_LIB_TITLE_ENGLISH)),
        imageUrl = getString(getColumnIndexOrThrow(COL_LIB_IMAGE_URL)) ?: "",
        totalEpisodes = getInt(getColumnIndexOrThrow(COL_LIB_TOTAL_EPISODES)),
        totalChapters = getInt(getColumnIndexOrThrow(COL_LIB_TOTAL_CHAPTERS)),
        totalVolumes = getInt(getColumnIndexOrThrow(COL_LIB_TOTAL_VOLUMES)),
        airingStatus = getString(getColumnIndexOrThrow(COL_LIB_AIRING_STATUS)),
        year = getInt(getColumnIndexOrThrow(COL_LIB_YEAR)),
        season = getString(getColumnIndexOrThrow(COL_LIB_SEASON)),
        genresJson = getString(getColumnIndexOrThrow(COL_LIB_GENRES_JSON)) ?: "[]",
        format = getString(getColumnIndexOrThrow(COL_LIB_FORMAT)),
        studio = getString(getColumnIndexOrThrow(COL_LIB_STUDIO)),
        anilistId = if (isNull(getColumnIndexOrThrow(COL_LIB_ANILIST_ID))) null
                    else getInt(getColumnIndexOrThrow(COL_LIB_ANILIST_ID)),
        localUpdatedAt = getLong(getColumnIndexOrThrow(COL_LIB_LOCAL_UPDATED_AT)),
        syncedAt = getLong(getColumnIndexOrThrow(COL_LIB_SYNCED_AT))
    )
}
