package com.canim.app.data

import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibraryEntry
import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.local.PendingMutation
import com.canim.app.data.local.PendingMutationDao
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Focused tests proving the transaction boundary and atomicity invariant:
 * - local mutation succeeds AND pending mutation is persisted
 * - OR neither is committed (atomic rollback on error).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AtomicMutationTransactionTest {

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
    fun `upsertWithMutation persists both library entry and pending mutation on success`() {
        val entry = LibraryEntry(
            malId = 501,
            mediaType = "ANIME",
            title = "Test Anime",
            status = "watching",
            score = 9,
            progress = 4
        )
        val mutation = PendingMutation(
            malId = 501,
            mediaType = "ANIME",
            mutationType = PendingMutation.TYPE_UPDATE,
            payloadJson = """{"score":9,"progress":4}"""
        )

        libraryDao.upsertWithMutation(entry, mutation, mutationDao)

        // Verify both are persisted
        val savedEntry = libraryDao.getEntry(501, "ANIME")
        assertNotNull("Library entry must be saved", savedEntry)
        assertEquals(9, savedEntry!!.score)
        assertEquals(4, savedEntry.progress)

        val pending = mutationDao.getPendingMutations()
        assertEquals(1, pending.size)
        assertEquals(501, pending[0].malId)
    }

    @Test
    fun `deleteWithMutation deletes library entry and persists pending delete mutation on success`() {
        // Pre-insert an entry
        val entry = LibraryEntry(
            malId = 601,
            mediaType = "ANIME",
            title = "To Delete",
            status = "completed"
        )
        libraryDao.upsertEntry(entry)
        assertNotNull(libraryDao.getEntry(601, "ANIME"))

        val deleteMutation = PendingMutation(
            malId = 601,
            mediaType = "ANIME",
            mutationType = PendingMutation.TYPE_DELETE,
            payloadJson = ""
        )

        libraryDao.deleteWithMutation(601, "ANIME", deleteMutation, mutationDao)

        // Verify entry is deleted from library_entries
        assertNull("Entry must be deleted", libraryDao.getEntry(601, "ANIME"))

        // Verify delete mutation is persisted
        val pending = mutationDao.getPendingMutations()
        assertEquals(1, pending.size)
        assertEquals(PendingMutation.TYPE_DELETE, pending[0].mutationType)
        assertEquals(601, pending[0].malId)
    }

    @Test
    fun `atomic rollback when enqueue fails - neither library entry nor pending mutation is committed`() {
        val entry = LibraryEntry(
            malId = 701,
            mediaType = "ANIME",
            title = "Rollback Test",
            status = "plan_to_watch"
        )
        val mutation = PendingMutation(
            malId = 701,
            mediaType = "ANIME",
            mutationType = PendingMutation.TYPE_UPDATE,
            payloadJson = "{}"
        )

        // Failing mutation DAO simulating failure within transaction
        val failingDao = object : PendingMutationDao(db) {
            override fun enqueueMutation(mutation: PendingMutation, database: android.database.sqlite.SQLiteDatabase?) {
                throw IllegalStateException("Simulated SQLite failure during enqueue")
            }
        }

        try {
            libraryDao.upsertWithMutation(entry, mutation, failingDao)
            fail("Expected exception was not thrown")
        } catch (e: IllegalStateException) {
            assertEquals("Simulated SQLite failure during enqueue", e.message)
        }

        // INVARIANT VERIFICATION: Neither is committed
        assertNull("Library entry must NOT be committed on transaction failure", libraryDao.getEntry(701, "ANIME"))
        assertTrue("Pending mutations must be empty after rollback", mutationDao.getPendingMutations().isEmpty())
    }

    @Test
    fun `atomic rollback when delete enqueue fails - library entry is preserved`() {
        val entry = LibraryEntry(
            malId = 801,
            mediaType = "ANIME",
            title = "Preserved on Delete Failure",
            status = "watching"
        )
        libraryDao.upsertEntry(entry)
        assertNotNull(libraryDao.getEntry(801, "ANIME"))

        val deleteMutation = PendingMutation(
            malId = 801,
            mediaType = "ANIME",
            mutationType = PendingMutation.TYPE_DELETE,
            payloadJson = ""
        )

        val failingDao = object : PendingMutationDao(db) {
            override fun enqueueMutation(mutation: PendingMutation, database: android.database.sqlite.SQLiteDatabase?) {
                throw RuntimeException("Simulated enqueue failure on delete")
            }
        }

        try {
            libraryDao.deleteWithMutation(801, "ANIME", deleteMutation, failingDao)
            fail("Expected exception was not thrown")
        } catch (e: RuntimeException) {
            assertEquals("Simulated enqueue failure on delete", e.message)
        }

        // INVARIANT VERIFICATION: Entry was NOT deleted because transaction rolled back
        assertNotNull("Library entry must still exist after rollback", libraryDao.getEntry(801, "ANIME"))
        assertTrue("No pending delete should exist", mutationDao.getPendingMutations().isEmpty())
    }
}
