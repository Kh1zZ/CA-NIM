package com.canim.app.data

import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.PendingMutationDao
import com.canim.app.data.local.LibraryEntry
import com.canim.app.data.local.PendingMutation
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests that the SQLite schema is created correctly and that upsert/upgrade semantics work.
 * Uses Robolectric to provide an Android context without instrumentation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalLibrarySchemaTest {

    private lateinit var db: LocalDatabase
    private lateinit var libraryDao: LibraryDao
    private lateinit var mutationDao: PendingMutationDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = LocalDatabase(context)
        libraryDao = LibraryDao(db)
        mutationDao = PendingMutationDao(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `library_entries table is created and queryable`() {
        // If onCreate failed we'd get an exception here
        val entries = libraryDao.getAllEntries("ANIME")
        assertNotNull(entries)
        assertTrue("Expected empty DB on fresh schema", entries.isEmpty())
    }

    @Test
    fun `pending_mutations table is created and queryable`() {
        val mutations = mutationDao.getPendingMutations()
        assertNotNull(mutations)
        assertTrue(mutations.isEmpty())
    }

    @Test
    fun `upsert is idempotent - same PK twice results in single row`() {
        val entry = LibraryEntry(malId = 1001, mediaType = "ANIME", title = "Test Anime")
        libraryDao.upsertEntry(entry)
        libraryDao.upsertEntry(entry.copy(score = 9))

        val result = libraryDao.getAllEntries("ANIME")
        assertEquals("Upsert should coalesce to one row", 1, result.size)
        assertEquals("Second upsert value should win", 9, result[0].score)
    }

    @Test
    fun `upsert preserves ANIME and MANGA as separate rows with same malId`() {
        val animeEntry = LibraryEntry(malId = 1234, mediaType = "ANIME", title = "Dual Title Anime")
        val mangaEntry = LibraryEntry(malId = 1234, mediaType = "MANGA", title = "Dual Title Manga")
        libraryDao.upsertEntry(animeEntry)
        libraryDao.upsertEntry(mangaEntry)

        val animes = libraryDao.getAllEntries("ANIME")
        val mangas = libraryDao.getAllEntries("MANGA")
        assertEquals(1, animes.size)
        assertEquals(1, mangas.size)
        assertEquals("Dual Title Anime", animes[0].title)
        assertEquals("Dual Title Manga", mangas[0].title)
    }

    @Test
    fun `database version is 1`() {
        assertEquals(1, LocalDatabase.DATABASE_VERSION)
    }

    @Test
    fun `clearAll removes all library entries`() {
        libraryDao.upsertEntry(LibraryEntry(malId = 1, mediaType = "ANIME"))
        libraryDao.upsertEntry(LibraryEntry(malId = 2, mediaType = "MANGA"))
        libraryDao.clearAll()
        assertTrue(libraryDao.getAllEntries("ANIME").isEmpty())
        assertTrue(libraryDao.getAllEntries("MANGA").isEmpty())
    }

    @Test
    fun `clearAllForType removes only entries of given type`() {
        libraryDao.upsertEntry(LibraryEntry(malId = 1, mediaType = "ANIME"))
        libraryDao.upsertEntry(LibraryEntry(malId = 2, mediaType = "MANGA"))
        libraryDao.clearAllForType("ANIME")
        assertTrue(libraryDao.getAllEntries("ANIME").isEmpty())
        assertEquals(1, libraryDao.getAllEntries("MANGA").size)
    }

    @Test
    fun `onUpgrade preserves library entries and pending mutations`() {
        libraryDao.upsertEntry(LibraryEntry(malId = 9001, mediaType = "ANIME", title = "Upgrade Test"))
        mutationDao.enqueueMutation(
            PendingMutation(
                malId = 9001,
                mediaType = "ANIME",
                mutationType = PendingMutation.TYPE_UPDATE,
                payloadJson = "{}"
            )
        )

        db.onUpgrade(db.writableDatabase, 1, 2)

        val entries = libraryDao.getAllEntries("ANIME")
        assertEquals("Library entry must survive upgrade", 1, entries.size)
        assertEquals("Upgrade Test", entries[0].title)
        val mutations = mutationDao.getPendingMutations()
        assertEquals("Pending mutation must survive upgrade", 1, mutations.size)
        assertEquals(9001, mutations[0].malId)
    }

    @Test
    fun `onUpgrade is idempotent`() {
        db.onUpgrade(db.writableDatabase, 1, 2)
        db.onUpgrade(db.writableDatabase, 1, 2)
        assertTrue(libraryDao.getAllEntries("ANIME").isEmpty())
        assertTrue(mutationDao.getPendingMutations().isEmpty())
    }
}
