package com.canim.app.data

import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.local.PendingMutation
import com.canim.app.data.local.PendingMutation.Companion.STATUS_FAILED_PERMANENTLY
import com.canim.app.data.local.PendingMutation.Companion.STATUS_IN_FLIGHT
import com.canim.app.data.local.PendingMutation.Companion.STATUS_PENDING
import com.canim.app.data.local.PendingMutation.Companion.STATUS_SUCCEEDED
import com.canim.app.data.local.PendingMutation.Companion.TYPE_DELETE
import com.canim.app.data.local.PendingMutation.Companion.TYPE_UPDATE
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
 * Tests for PendingMutationDao: coalescing rules, FIFO ordering, status transitions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PendingMutationDaoTest {

    private lateinit var db: LocalDatabase
    private lateinit var dao: PendingMutationDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = LocalDatabase(context)
        dao = PendingMutationDao(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun update(malId: Int, time: Long = System.currentTimeMillis()) = PendingMutation(
        malId = malId, mediaType = "ANIME", mutationType = TYPE_UPDATE,
        payloadJson = "{\"status\":\"watching\"}", localUpdatedAt = time, createdAt = time
    )

    private fun delete(malId: Int, time: Long = System.currentTimeMillis()) = PendingMutation(
        malId = malId, mediaType = "ANIME", mutationType = TYPE_DELETE,
        payloadJson = "", localUpdatedAt = time, createdAt = time
    )

    // ── Coalesce: UPDATE + UPDATE ─────────────────────────────────────────────

    @Test
    fun `two UPDATE mutations for same malId coalesce to single entry`() {
        dao.enqueueMutation(update(malId = 1, time = 1000L))
        dao.enqueueMutation(update(malId = 1, time = 2000L))

        val pending = dao.getPendingMutations()
        assertEquals("Should coalesce to 1 mutation", 1, pending.size)
        assertEquals(TYPE_UPDATE, pending[0].mutationType)
    }

    @Test
    fun `newer UPDATE payload replaces older UPDATE for same malId`() {
        val older = PendingMutation(
            malId = 2, mediaType = "ANIME", mutationType = TYPE_UPDATE,
            payloadJson = "{\"score\":7}", localUpdatedAt = 100L, createdAt = 100L
        )
        val newer = PendingMutation(
            malId = 2, mediaType = "ANIME", mutationType = TYPE_UPDATE,
            payloadJson = "{\"score\":9}", localUpdatedAt = 200L, createdAt = 200L
        )
        dao.enqueueMutation(older)
        dao.enqueueMutation(newer)

        val pending = dao.getPendingMutations()
        assertEquals(1, pending.size)
        assertEquals("{\"score\":9}", pending[0].payloadJson)
    }

    // ── Coalesce: DELETE beats UPDATE ─────────────────────────────────────────

    @Test
    fun `DELETE enqueued after UPDATE for same malId - DELETE wins`() {
        dao.enqueueMutation(update(malId = 3))
        dao.enqueueMutation(delete(malId = 3))

        val pending = dao.getPendingMutations()
        assertEquals(1, pending.size)
        assertEquals(TYPE_DELETE, pending[0].mutationType)
    }

    @Test
    fun `UPDATE enqueued after DELETE for same malId - DELETE wins, UPDATE ignored`() {
        dao.enqueueMutation(delete(malId = 4))
        dao.enqueueMutation(update(malId = 4))  // should be ignored

        val pending = dao.getPendingMutations()
        assertEquals(1, pending.size)
        assertEquals(TYPE_DELETE, pending[0].mutationType)
    }

    // ── Separate malIds are independent ──────────────────────────────────────

    @Test
    fun `mutations for different malIds are independent`() {
        dao.enqueueMutation(update(malId = 10))
        dao.enqueueMutation(update(malId = 11))
        dao.enqueueMutation(delete(malId = 12))

        val pending = dao.getPendingMutations()
        assertEquals(3, pending.size)
    }

    // ── FIFO ordering ────────────────────────────────────────────────────────

    @Test
    fun `getPendingMutations returns FIFO order by createdAt`() {
        dao.enqueueMutation(update(malId = 100, time = 3000L))
        dao.enqueueMutation(update(malId = 101, time = 1000L))
        dao.enqueueMutation(update(malId = 102, time = 2000L))

        val pending = dao.getPendingMutations()
        assertEquals(3, pending.size)
        assertEquals(101, pending[0].malId)  // earliest
        assertEquals(102, pending[1].malId)
        assertEquals(100, pending[2].malId)  // latest
    }

    // ── Status transitions ────────────────────────────────────────────────────

    @Test
    fun `markSucceeded - mutation no longer returned by getPendingMutations`() {
        dao.enqueueMutation(update(malId = 20))
        val inserted = dao.getPendingMutations().first()

        dao.markInFlight(inserted.id)
        dao.markSucceeded(inserted.id)

        val pending = dao.getPendingMutations()
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `clearSucceeded removes SUCCEEDED mutations`() {
        dao.enqueueMutation(update(malId = 21))
        val id = dao.getPendingMutations().first().id
        dao.markInFlight(id)
        dao.markSucceeded(id)

        dao.clearSucceeded()

        // Nothing should remain at all
        val active = dao.getActiveMutationForMalId(21, "ANIME")
        assertNull(active)
    }

    @Test
    fun `incrementAttemptsAndRequeue increases attempts and resets to PENDING`() {
        dao.enqueueMutation(update(malId = 30))
        val id = dao.getPendingMutations().first().id
        dao.markInFlight(id)
        dao.incrementAttemptsAndRequeue(id)

        val pending = dao.getPendingMutations()
        assertEquals(1, pending.size)
        assertEquals(STATUS_PENDING, pending[0].status)
        assertEquals(1, pending[0].attempts)
    }

    @Test
    fun `markFailedPermanently - mutation not returned by getPendingMutations`() {
        dao.enqueueMutation(update(malId = 40))
        val id = dao.getPendingMutations().first().id
        dao.markInFlight(id)
        dao.markFailedPermanently(id)

        assertTrue(dao.getPendingMutations().isEmpty())
    }

    // ── getActiveMalIds ───────────────────────────────────────────────────────

    @Test
    fun `getActiveMalIds includes PENDING and IN_FLIGHT but not SUCCEEDED`() {
        dao.enqueueMutation(update(malId = 50))
        dao.enqueueMutation(update(malId = 51))
        val id51 = dao.getActiveMutationForMalId(51, "ANIME")!!.id
        dao.markInFlight(id51)

        dao.enqueueMutation(update(malId = 52))
        val id52 = dao.getActiveMutationForMalId(52, "ANIME")!!.id
        dao.markInFlight(id52)
        dao.markSucceeded(id52)

        val activeIds = dao.getActiveMalIds("ANIME")
        assertTrue(50 in activeIds)
        assertTrue(51 in activeIds)  // IN_FLIGHT counts
        assertFalse(52 in activeIds) // SUCCEEDED does not count
    }

    // ── resetInFlightToPending ────────────────────────────────────────────────

    @Test
    fun `resetInFlightToPending recovers IN_FLIGHT to PENDING on process restart`() {
        dao.enqueueMutation(update(malId = 60))
        val id = dao.getPendingMutations().first().id
        dao.markInFlight(id)

        // Simulate process restart
        dao.resetInFlightToPending()

        val pending = dao.getPendingMutations()
        assertEquals(1, pending.size)
        assertEquals(STATUS_PENDING, pending[0].status)
    }

    @Test
    fun `clearAll removes all mutations`() {
        dao.enqueueMutation(update(malId = 70))
        dao.enqueueMutation(delete(malId = 71))
        dao.clearAll()

        assertTrue(dao.getPendingMutations().isEmpty())
    }
}
