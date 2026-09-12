package com.canim.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * SQLiteOpenHelper for the CA'NIM local-first library database.
 *
 * Schema version 1 — Phase 4 Local-First Sync.
 *
 * Tables:
 * - [TABLE_LIBRARY_ENTRIES]: one row per tracked media item (MAL authoritative).
 * - [TABLE_PENDING_MUTATIONS]: queue of write operations waiting to be synced to MAL.
 *
 * Migration strategy:
 * - Non-destructive incremental upgrades: existing rows are preserved, including
 *   unsynced offline mutations in [TABLE_PENDING_MUTATIONS].
 * - Missing tables are created; missing columns are appended via ALTER TABLE.
 * - If [onCreate] fails: caught by caller; CacheManager disk JSON fallback remains available.
 */
class LocalDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "canim_library.db"
        const val DATABASE_VERSION = 1

        // ── Table: library_entries ────────────────────────────────────────────
        const val TABLE_LIBRARY_ENTRIES = "library_entries"
        const val COL_LIB_MAL_ID = "mal_id"
        const val COL_LIB_MEDIA_TYPE = "media_type"
        const val COL_LIB_STATUS = "status"
        const val COL_LIB_SCORE = "score"
        const val COL_LIB_PROGRESS = "progress"
        const val COL_LIB_PROGRESS_VOLUMES = "progress_volumes"
        const val COL_LIB_IS_REPEATING = "is_repeating"
        const val COL_LIB_NUM_TIMES_REWATCHED = "num_times_rewatched"
        const val COL_LIB_REWATCH_VALUE = "rewatch_value"
        const val COL_LIB_PRIORITY = "priority"
        const val COL_LIB_TAGS_JSON = "tags_json"
        const val COL_LIB_COMMENTS = "comments"
        const val COL_LIB_START_DATE = "start_date"
        const val COL_LIB_FINISH_DATE = "finish_date"
        const val COL_LIB_TITLE = "title"
        const val COL_LIB_TITLE_ENGLISH = "title_english"
        const val COL_LIB_IMAGE_URL = "image_url"
        const val COL_LIB_TOTAL_EPISODES = "total_episodes"
        const val COL_LIB_TOTAL_CHAPTERS = "total_chapters"
        const val COL_LIB_TOTAL_VOLUMES = "total_volumes"
        const val COL_LIB_AIRING_STATUS = "airing_status"
        const val COL_LIB_YEAR = "year"
        const val COL_LIB_SEASON = "season"
        const val COL_LIB_GENRES_JSON = "genres_json"
        const val COL_LIB_FORMAT = "format"
        const val COL_LIB_STUDIO = "studio"
        const val COL_LIB_ANILIST_ID = "anilist_id"
        const val COL_LIB_LOCAL_UPDATED_AT = "local_updated_at"
        const val COL_LIB_SYNCED_AT = "synced_at"

        private const val SQL_CREATE_LIBRARY = """
            CREATE TABLE IF NOT EXISTS $TABLE_LIBRARY_ENTRIES (
                $COL_LIB_MAL_ID INTEGER NOT NULL,
                $COL_LIB_MEDIA_TYPE TEXT NOT NULL,
                $COL_LIB_STATUS TEXT NOT NULL DEFAULT 'watching',
                $COL_LIB_SCORE INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_PROGRESS INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_PROGRESS_VOLUMES INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_IS_REPEATING INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_NUM_TIMES_REWATCHED INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_REWATCH_VALUE INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_PRIORITY INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_TAGS_JSON TEXT NOT NULL DEFAULT '[]',
                $COL_LIB_COMMENTS TEXT,
                $COL_LIB_START_DATE TEXT,
                $COL_LIB_FINISH_DATE TEXT,
                $COL_LIB_TITLE TEXT NOT NULL DEFAULT '',
                $COL_LIB_TITLE_ENGLISH TEXT,
                $COL_LIB_IMAGE_URL TEXT NOT NULL DEFAULT '',
                $COL_LIB_TOTAL_EPISODES INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_TOTAL_CHAPTERS INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_TOTAL_VOLUMES INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_AIRING_STATUS TEXT,
                $COL_LIB_YEAR INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_SEASON TEXT,
                $COL_LIB_GENRES_JSON TEXT NOT NULL DEFAULT '[]',
                $COL_LIB_FORMAT TEXT,
                $COL_LIB_STUDIO TEXT,
                $COL_LIB_ANILIST_ID INTEGER,
                $COL_LIB_LOCAL_UPDATED_AT INTEGER NOT NULL DEFAULT 0,
                $COL_LIB_SYNCED_AT INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY ($COL_LIB_MAL_ID, $COL_LIB_MEDIA_TYPE)
            )
        """

        // ── Table: pending_mutations ──────────────────────────────────────────
        const val TABLE_PENDING_MUTATIONS = "pending_mutations"
        const val COL_MUT_ID = "id"
        const val COL_MUT_MAL_ID = "mal_id"
        const val COL_MUT_MEDIA_TYPE = "media_type"
        const val COL_MUT_TYPE = "mutation_type"
        const val COL_MUT_PAYLOAD_JSON = "payload_json"
        const val COL_MUT_LOCAL_UPDATED_AT = "local_updated_at"
        const val COL_MUT_CREATED_AT = "created_at"
        const val COL_MUT_ATTEMPTS = "attempts"
        const val COL_MUT_STATUS = "status"

        private const val SQL_CREATE_MUTATIONS = """
            CREATE TABLE IF NOT EXISTS $TABLE_PENDING_MUTATIONS (
                $COL_MUT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MUT_MAL_ID INTEGER NOT NULL,
                $COL_MUT_MEDIA_TYPE TEXT NOT NULL,
                $COL_MUT_TYPE TEXT NOT NULL,
                $COL_MUT_PAYLOAD_JSON TEXT NOT NULL DEFAULT '',
                $COL_MUT_LOCAL_UPDATED_AT INTEGER NOT NULL DEFAULT 0,
                $COL_MUT_CREATED_AT INTEGER NOT NULL DEFAULT 0,
                $COL_MUT_ATTEMPTS INTEGER NOT NULL DEFAULT 0,
                $COL_MUT_STATUS TEXT NOT NULL DEFAULT 'PENDING'
            )
        """

        // Column definitions used by non-destructive upgrades to append missing columns.
        // Every NOT NULL column carries a DEFAULT so ALTER TABLE ADD COLUMN is always safe.
        private val LIBRARY_COLUMNS: Map<String, String> = mapOf(
            COL_LIB_STATUS to "TEXT NOT NULL DEFAULT 'watching'",
            COL_LIB_SCORE to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_PROGRESS to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_PROGRESS_VOLUMES to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_IS_REPEATING to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_NUM_TIMES_REWATCHED to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_REWATCH_VALUE to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_PRIORITY to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_TAGS_JSON to "TEXT NOT NULL DEFAULT '[]'",
            COL_LIB_COMMENTS to "TEXT",
            COL_LIB_START_DATE to "TEXT",
            COL_LIB_FINISH_DATE to "TEXT",
            COL_LIB_TITLE to "TEXT NOT NULL DEFAULT ''",
            COL_LIB_TITLE_ENGLISH to "TEXT",
            COL_LIB_IMAGE_URL to "TEXT NOT NULL DEFAULT ''",
            COL_LIB_TOTAL_EPISODES to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_TOTAL_CHAPTERS to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_TOTAL_VOLUMES to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_AIRING_STATUS to "TEXT",
            COL_LIB_YEAR to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_SEASON to "TEXT",
            COL_LIB_GENRES_JSON to "TEXT NOT NULL DEFAULT '[]'",
            COL_LIB_FORMAT to "TEXT",
            COL_LIB_STUDIO to "TEXT",
            COL_LIB_ANILIST_ID to "INTEGER",
            COL_LIB_LOCAL_UPDATED_AT to "INTEGER NOT NULL DEFAULT 0",
            COL_LIB_SYNCED_AT to "INTEGER NOT NULL DEFAULT 0"
        )

        private val MUTATION_COLUMNS: Map<String, String> = mapOf(
            COL_MUT_MAL_ID to "INTEGER NOT NULL DEFAULT 0",
            COL_MUT_MEDIA_TYPE to "TEXT NOT NULL DEFAULT ''",
            COL_MUT_TYPE to "TEXT NOT NULL DEFAULT ''",
            COL_MUT_PAYLOAD_JSON to "TEXT NOT NULL DEFAULT ''",
            COL_MUT_LOCAL_UPDATED_AT to "INTEGER NOT NULL DEFAULT 0",
            COL_MUT_CREATED_AT to "INTEGER NOT NULL DEFAULT 0",
            COL_MUT_ATTEMPTS to "INTEGER NOT NULL DEFAULT 0",
            COL_MUT_STATUS to "TEXT NOT NULL DEFAULT 'PENDING'"
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        try {
            createTablesIfMissing(db)
        } catch (e: Exception) {
            Log.e("LocalDatabase", "Failed to create tables: ${e.message}", e)
            throw e
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Non-destructive incremental migration: preserve existing rows, including
        // unsynced offline mutations in pending_mutations.
        Log.i("LocalDatabase", "Migrating DB from v$oldVersion to v$newVersion (non-destructive)")
        try {
            createTablesIfMissing(db)
            ensureColumns(db, TABLE_LIBRARY_ENTRIES, LIBRARY_COLUMNS)
            ensureColumns(db, TABLE_PENDING_MUTATIONS, MUTATION_COLUMNS)
        } catch (e: Exception) {
            Log.e("LocalDatabase", "Failed to migrate DB: ${e.message}", e)
            throw e
        }
    }

    private fun createTablesIfMissing(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_LIBRARY)
        db.execSQL(SQL_CREATE_MUTATIONS)
    }

    /**
     * Appends any column declared in [columns] that does not yet exist in [table].
     * Column definitions must be safe to append (a NOT NULL column requires a DEFAULT).
     * Existing rows and columns are left untouched.
     */
    private fun ensureColumns(
        db: SQLiteDatabase,
        table: String,
        columns: Map<String, String>
    ) {
        val existing = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0) existing.add(cursor.getString(nameIndex))
            }
        }
        for ((name, definition) in columns) {
            if (name !in existing) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $name $definition")
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
}
