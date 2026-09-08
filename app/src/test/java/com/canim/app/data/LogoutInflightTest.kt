package com.canim.app.data

import com.canim.app.data.local.FakeNetworkChecker
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibraryEntry
import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.local.PendingMutation
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
 * Tests for the Logout and IN_FLIGHT lifecycle in [LibrarySyncEngine]:
 * - Calling onLogout() clears both pending_mutations and library_entries tables completely.
 * - Even mutations in IN_FLIGHT state are wiped, preventing false local success.
 * - [LibrarySyncEngine.isLoggedOut] prevents subsequent drains or immediate sends.
 * - onLogin() resets the logout guard.
 * - onLogout() is idempotent and safe against empty or repeated calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LogoutInflightTest {

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
        malId = malId,
        mediaType = "ANIME",
        mutationType = PendingMutation.TYPE_UPDATE,
        payloadJson = """{"status":"watching","score":8,"progress":5}"""
    )

    private fun animeEntry(malId: Int) = LibraryEntry(
        malId = malId,
        mediaType = "ANIME",
        title = "Anime $malId",
        status = "watching",
        score = 8,
        progress = 5,
        totalEpisodes = 12
    )

    @Test
    fun `onLogout clears pending mutations even when marked IN_FLIGHT`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 101))
        val mut = mutationDao.getActiveMutationForMalId(101, "ANIME")
        assertNotNull("Mutation should be enqueued", mut)
        val mutId = mut!!.id
        mutationDao.markInFlight(mutId)

        // Verify status is IN_FLIGHT
        val activeIdsBefore = mutationDao.getActiveMalIds("ANIME")
        assertTrue("malId 101 should be active while IN_FLIGHT", 101 in activeIdsBefore)

        // User logs out while mutation is IN_FLIGHT
        engine.onLogout()

        // Verify pending mutations table is completely empty
        val pendingAfter = mutationDao.getPendingMutations()
        assertTrue("pending_mutations should be empty after logout", pendingAfter.isEmpty())

        val activeIdsAfter = mutationDao.getActiveMalIds("ANIME")
        assertTrue("No active malIds should remain after logout", activeIdsAfter.isEmpty())
    }

    @Test
    fun `onLogout clears all library entries for both ANIME and MANGA`() = runTest {
        libraryDao.upsertEntry(animeEntry(malId = 201))
        libraryDao.upsertEntry(
            LibraryEntry(
                malId = 301,
                mediaType = "MANGA",
                title = "Manga 301",
                status = "reading",
                score = 9,
                progress = 10,
                totalEpisodes = 50
            )
        )

        assertEquals(1, libraryDao.getAllEntries("ANIME").size)
        assertEquals(1, libraryDao.getAllEntries("MANGA").size)

        engine.onLogout()

        assertTrue("ANIME entries should be cleared", libraryDao.getAllEntries("ANIME").isEmpty())
        assertTrue("MANGA entries should be cleared", libraryDao.getAllEntries("MANGA").isEmpty())
    }

    @Test
    fun `onLogout sets isLoggedOut to true and blocks subsequent drain`() = runTest {
        assertFalse("Initially not logged out", engine.isLoggedOut)

        engine.onLogout()
        assertTrue("Should be logged out", engine.isLoggedOut)

        // If something attempts to enqueue or drain after logout
        mutationDao.enqueueMutation(animeUpdate(malId = 401))
        engine.drainQueue()

        // fakeMal should not have been called because isLoggedOut = true guards drainQueue
        assertEquals("No network calls should occur after logout", 0, fakeMal.updateCallCount)
    }

    @Test
    fun `trySendImmediate returns false and does not send after onLogout`() = runTest {
        mutationDao.enqueueMutation(animeUpdate(malId = 501))
        engine.onLogout()

        val sent = engine.trySendImmediate(malId = 501, mediaType = "ANIME")
        assertFalse("trySendImmediate should return false after logout", sent)
        assertEquals(0, fakeMal.updateCallCount)
    }

    @Test
    fun `onLogin resets isLoggedOut flag`() = runTest {
        engine.onLogout()
        assertTrue(engine.isLoggedOut)

        engine.onLogin()
        assertFalse("isLoggedOut should be false after onLogin", engine.isLoggedOut)
    }

    @Test
    fun `onLogout is idempotent and safe when called multiple times`() = runTest {
        // First logout
        engine.onLogout()
        assertTrue(engine.isLoggedOut)

        // Repeated logouts should not throw
        engine.onLogout()
        engine.onLogout()
        assertTrue(engine.isLoggedOut)
    }
}
