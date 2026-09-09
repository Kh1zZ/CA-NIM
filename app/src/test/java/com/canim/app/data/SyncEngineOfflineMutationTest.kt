package com.canim.app.data

import com.canim.app.data.local.FakeNetworkChecker
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.local.PendingMutation
import com.canim.app.data.local.PendingMutationDao
import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.data.local.MalSecureStorage
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
 * Tests that the SyncEngine correctly drains the queue when online and
 * preserves mutations when offline.
 *
 * Uses [FakeNetworkChecker] to control network state deterministically
 * without requiring Android ConnectivityManager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncEngineOfflineMutationTest {

    private lateinit var db: LocalDatabase
    private lateinit var libraryDao: LibraryDao
    private lateinit var mutationDao: PendingMutationDao
    private lateinit var fakeNetwork: FakeNetworkChecker
    private lateinit var fakeMalManager: FakeMalAuthManager
    private lateinit var engine: LibrarySyncEngine
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(SupervisorJob() + testDispatcher)

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = LocalDatabase(context)
        libraryDao = LibraryDao(db)
        mutationDao = PendingMutationDao(db)
        fakeNetwork = FakeNetworkChecker(isAvailable = false)
        fakeMalManager = FakeMalAuthManager()
        engine = LibrarySyncEngine(
            pendingMutationDao = mutationDao,
            libraryDao = libraryDao,
            mutationExecutor = fakeMalManager,
            networkChecker = fakeNetwork,
            appScope = testScope,
            maxAttempts = 3,
            pollIntervalMs = 1_000L
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `mutation stays PENDING when offline`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 1))
        fakeNetwork.setAvailable(false)

        engine.drainQueue()

        val pending = mutationDao.getPendingMutations()
        assertEquals("Mutation should remain PENDING when offline", 1, pending.size)
        assertEquals(0, fakeMalManager.updateCallCount)
    }

    @Test
    fun `mutation is sent and marked SUCCEEDED when online`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 2))
        fakeMalManager.updateResult = Result.success(Unit)
        fakeNetwork.setAvailable(true)

        engine.drainQueue()

        val pending = mutationDao.getPendingMutations()
        assertTrue("Queue should be empty after successful drain", pending.isEmpty())
        assertEquals(1, fakeMalManager.updateCallCount)
    }

    @Test
    fun `multiple mutations are drained FIFO when online`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 10, time = 1000L))
        mutationDao.enqueueMutation(animeUpdate(malId = 11, time = 2000L))
        mutationDao.enqueueMutation(animeUpdate(malId = 12, time = 3000L))
        fakeMalManager.updateResult = Result.success(Unit)
        fakeNetwork.setAvailable(true)

        engine.drainQueue()

        assertTrue(mutationDao.getPendingMutations().isEmpty())
        assertEquals(3, fakeMalManager.updateCallCount)
        // Verify FIFO order
        assertEquals(listOf(10, 11, 12), fakeMalManager.updatedMalIds)
    }

    @Test
    fun `mutation stays PENDING when send fails with network error`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 20))
        fakeMalManager.updateResult = Result.failure(Exception("Network error"))
        fakeNetwork.setAvailable(true)

        engine.drainQueue()

        val pending = mutationDao.getPendingMutations()
        assertEquals("Mutation should remain queued after failure", 1, pending.size)
        assertEquals(1, pending[0].attempts)
    }

    @Test
    fun `trySendImmediate returns false when offline`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 30))
        fakeNetwork.setAvailable(false)

        val result = engine.trySendImmediate(30, "ANIME")

        assertFalse(result)
        assertEquals(0, fakeMalManager.updateCallCount)
    }

    @Test
    fun `trySendImmediate returns true when online and send succeeds`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 31))
        fakeMalManager.updateResult = Result.success(Unit)
        fakeNetwork.setAvailable(true)

        val result = engine.trySendImmediate(31, "ANIME")

        assertTrue(result)
        assertTrue(mutationDao.getPendingMutations().isEmpty())
    }

    @Test
    fun `DELETE IN_FLIGHT then UPDATE - both mutations are eventually processed correctly in FIFO order`() = runTest {
        // Step 1: Enqueue DELETE
        val deleteMut = PendingMutation(
            malId = 88,
            mediaType = "ANIME",
            mutationType = PendingMutation.TYPE_DELETE,
            payloadJson = "",
            localUpdatedAt = 1000L,
            createdAt = 1000L
        )
        mutationDao.enqueueMutation(deleteMut)
        val deleteEntry = mutationDao.getActiveMutationForMalId(88, "ANIME")
        assertNotNull(deleteEntry)

        // Step 2: Mark DELETE as IN_FLIGHT (simulating network send underway)
        mutationDao.markInFlight(deleteEntry!!.id)

        // Step 3: While DELETE is IN_FLIGHT, user executes UPDATE
        val updateMut = PendingMutation(
            malId = 88,
            mediaType = "ANIME",
            mutationType = PendingMutation.TYPE_UPDATE,
            payloadJson = """{"status":"watching","score":9,"progress":3}""",
            localUpdatedAt = 2000L,
            createdAt = 2000L
        )
        mutationDao.enqueueMutation(updateMut)

        // Verify: UPDATE was NOT ignored! Both exist (one IN_FLIGHT, one PENDING).
        val pendingList = mutationDao.getPendingMutations()
        assertEquals("Newer UPDATE must be preserved as PENDING", 1, pendingList.size)
        assertEquals(PendingMutation.TYPE_UPDATE, pendingList[0].mutationType)

        // Step 4: Complete the IN_FLIGHT DELETE
        mutationDao.markSucceeded(deleteEntry.id)
        fakeMalManager.deleteCallCount = 1 // simulate DELETE confirmed on server

        // Step 5: Drain queue with network available
        fakeNetwork.setAvailable(true)
        fakeMalManager.updateResult = Result.success(Unit)
        engine.drainQueue()

        // Step 6: Verify UPDATE was dispatched to MAL after DELETE
        assertEquals("UPDATE must be sent after DELETE completes", 1, fakeMalManager.updateCallCount)
        assertEquals(88, fakeMalManager.updatedMalIds.first())

        // Step 7: Queue should now be empty
        assertTrue("Queue should be completely clean", mutationDao.getPendingMutations().isEmpty())
    }

    private fun animeUpdate(malId: Int, time: Long = System.currentTimeMillis()) = PendingMutation(
        malId = malId,
        mediaType = "ANIME",
        mutationType = PendingMutation.TYPE_UPDATE,
        payloadJson = """{"status":"watching","score":0,"progress":1}""",
        localUpdatedAt = time,
        createdAt = time
    )
}

/**
 * Minimal fake for [MalAuthManager] that records calls and returns configurable results.
 * Avoids needing Mockito — keeps test dependencies minimal.
 */
class FakeMalAuthManager : MalAuthManager(
    secureStorage = MalSecureStorage(RuntimeEnvironment.getApplication())
) {
    var updateResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    val updatedMalIds = mutableListOf<Int>()
    var updateCallCount = 0
    var deleteCallCount = 0

    override fun getCurrentUser(): com.canim.app.data.model.MalUser =
        com.canim.app.data.model.MalUser(id = 1L, username = "fake_user", isLoggedIn = true)

    override suspend fun updateAnimeTracking(malId: Int, tracking: MalTracking): Result<Unit> {
        updateCallCount++
        updatedMalIds.add(malId)
        return updateResult
    }

    override suspend fun updateMangaTracking(malId: Int, tracking: MalTracking): Result<Unit> {
        updateCallCount++
        updatedMalIds.add(malId)
        return updateResult
    }

    override suspend fun deleteAnimeTracking(malId: Int): Result<Unit> {
        deleteCallCount++
        return deleteResult
    }

    override suspend fun deleteMangaTracking(malId: Int): Result<Unit> {
        deleteCallCount++
        return deleteResult
    }
}
