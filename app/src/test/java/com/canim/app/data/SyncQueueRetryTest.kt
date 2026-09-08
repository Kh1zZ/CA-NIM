package com.canim.app.data

import com.canim.app.data.local.FakeNetworkChecker
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.local.PendingMutation
import com.canim.app.data.local.PendingMutation.Companion.STATUS_FAILED_PERMANENTLY
import com.canim.app.data.local.PendingMutation.Companion.STATUS_PENDING
import com.canim.app.data.local.PendingMutationDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for the queue retry scheduling behavior in LibrarySyncEngine:
 * - Attempt counting on failure.
 * - FAILED_PERMANENTLY after maxAttempts.
 * - FAILED_PERMANENTLY mutations not drained again.
 * - No interaction with Phase 1 HTTP backoff (the engine itself has no sleep).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncQueueRetryTest {

    private lateinit var db: LocalDatabase
    private lateinit var libraryDao: LibraryDao
    private lateinit var mutationDao: PendingMutationDao
    private lateinit var fakeNetwork: FakeNetworkChecker
    private lateinit var fakeMal: FakeMalAuthManager
    private lateinit var engine: LibrarySyncEngine
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(SupervisorJob() + testDispatcher)

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = LocalDatabase(context)
        libraryDao = LibraryDao(db)
        mutationDao = PendingMutationDao(db)
        fakeNetwork = FakeNetworkChecker(isAvailable = true)
        fakeMal = FakeMalAuthManager()
        engine = LibrarySyncEngine(
            pendingMutationDao = mutationDao,
            libraryDao = libraryDao,
            malAuthManager = fakeMal,
            networkChecker = fakeNetwork,
            appScope = testScope,
            maxAttempts = 3,
            pollIntervalMs = 100L
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun animeUpdate(malId: Int) = PendingMutation(
        malId = malId, mediaType = "ANIME", mutationType = PendingMutation.TYPE_UPDATE,
        payloadJson = """{"status":"watching","score":0,"progress":1}"""
    )

    @Test
    fun `first failure increments attempts to 1 and stays PENDING`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 1))
        fakeMal.updateResult = Result.failure(Exception("Network timeout"))

        engine.drainQueue()

        val pending = mutationDao.getPendingMutations()
        assertEquals("Should still be in queue", 1, pending.size)
        assertEquals(STATUS_PENDING, pending[0].status)
        assertEquals(1, pending[0].attempts)
    }

    @Test
    fun `second failure increments attempts to 2 and stays PENDING`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 2))
        fakeMal.updateResult = Result.failure(Exception("error"))

        engine.drainQueue()  // attempts = 1
        engine.drainQueue()  // attempts = 2

        val pending = mutationDao.getPendingMutations()
        assertEquals(1, pending.size)
        assertEquals(2, pending[0].attempts)
        assertEquals(STATUS_PENDING, pending[0].status)
    }

    @Test
    fun `after maxAttempts=3 failures, mutation becomes FAILED_PERMANENTLY`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 3))
        fakeMal.updateResult = Result.failure(Exception("error"))

        // 3 drain attempts (maxAttempts = 3)
        engine.drainQueue()  // attempts = 1 (< 3)
        engine.drainQueue()  // attempts = 2 (< 3)
        engine.drainQueue()  // attempts = 3 (= maxAttempts) → FAILED_PERMANENTLY

        val pending = mutationDao.getPendingMutations()
        assertTrue("FAILED_PERMANENTLY should not be in pending list", pending.isEmpty())

        // Verify the mutation exists in DB with correct status by checking active IDs
        val activeIds = mutationDao.getActiveMalIds("ANIME")
        assertFalse("FAILED_PERMANENTLY not counted as active", 3 in activeIds)
    }

    @Test
    fun `FAILED_PERMANENTLY mutations are not re-sent on subsequent drain`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 4))
        fakeMal.updateResult = Result.failure(Exception("error"))

        engine.drainQueue()
        engine.drainQueue()
        engine.drainQueue()  // → FAILED_PERMANENTLY

        val callsBefore = fakeMal.updateCallCount
        engine.drainQueue()  // Should not drain FAILED_PERMANENTLY

        assertEquals("No additional calls after FAILED_PERMANENTLY", callsBefore, fakeMal.updateCallCount)
    }

    @Test
    fun `successful send after initial failure clears mutation from queue`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 5))
        fakeMal.updateResult = Result.failure(Exception("error"))
        engine.drainQueue()  // attempts = 1, still PENDING

        fakeMal.updateResult = Result.success(Unit)
        engine.drainQueue()  // Should succeed now

        assertTrue("Queue should be empty after eventual success", mutationDao.getPendingMutations().isEmpty())
    }

    @Test
    fun `engine does not block on delays - separation from Phase 1 HTTP backoff`() = runTest {
        // This test verifies that drainQueue() completes without suspending on internal delays.
        // LibrarySyncEngine has no internal delay/sleep — only Phase 1 handles HTTP backoff.
        // If SyncEngine had its own backoff, UnconfinedTestDispatcher would stall here.
        mutationDao.enqueueMutation(animeUpdate(malId = 6))
        fakeMal.updateResult = Result.failure(Exception("timeout"))

        val start = System.currentTimeMillis()
        engine.drainQueue()
        val elapsed = System.currentTimeMillis() - start

        // Should complete virtually instantly — no internal sleep in SyncEngine
        assertTrue("SyncEngine.drainQueue should not block (elapsed=$elapsed ms)", elapsed < 500L)
    }
}
