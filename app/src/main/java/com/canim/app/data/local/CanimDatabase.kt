package com.canim.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import com.canim.app.BuildConfig

@Database(
    entities = [AnimeEntity::class, MangaEntity::class],
    version = 3,
    exportSchema = false
)
abstract class CanimDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun mangaDao(): MangaDao

    companion object {
        @Volatile
        private var INSTANCE: CanimDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime_items ADD COLUMN anilistId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE manga_items ADD COLUMN anilistId INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_anime_items_malId ON anime_items(malId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_anime_items_status ON anime_items(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_anime_items_updatedAt ON anime_items(updatedAt)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_manga_items_malId ON manga_items(malId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_items_status ON manga_items(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manga_items_updatedAt ON manga_items(updatedAt)")
            }
        }

        fun getDatabase(context: Context): CanimDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    CanimDatabase::class.java,
                    "canim_local_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)

                // CRITICAL SAFEGUARD: Never wipe user library data in production release.
                // Always write explicit Migration objects when updating schema version.
                if (BuildConfig.DEBUG) {
                    builder.fallbackToDestructiveMigration()
                }

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
