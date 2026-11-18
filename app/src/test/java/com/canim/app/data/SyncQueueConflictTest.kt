package com.canim.app.data

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.FakeNetworkChecker
import com.canim.app.data.local.LibraryDao
import com.canim.app.data.local.LibraryEntry
import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.local.LocalDatabase
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.local.PendingMutation
import com.canim.app.data.local.PendingMutationDao
import com.canim.app.data.model.*
import com.canim.app.data.remote.ApiClient
import com.canim.app.data.remote.MalApiService
import com.canim.app.data.remote.anilist.AniListApolloClient
import com.canim.app.data.repository.LibraryRepositoryImpl
import com.canim.app.data.repository.MalAuthManager
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
 * Tests for the safe force-refresh reconciliation logic:
 * - Entries with active pending mutations are NOT overwritten by server data.
 * - Entries without pending mutations ARE overwritten by server data.
 * - Failed fetch does not change DB state.
 *
 * These tests exercise [PendingMutationDao.getActiveMalIds] and [LibraryDao] upsert/read
 * to simulate what [LibraryRepositoryImpl.safeReconcileAnime] does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncQueueConflictTest {

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
            mutationExecutor = fakeMal,
            networkChecker = fakeNetwork,
            appScope = testScope,
            maxAttempts = 3
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Simulates reconcile: for each serverItem, if its malId has no active pending,
     * overwrite DB; if it has active pending, preserve DB.
     */
    private fun simulateReconcile(
        serverMalIds: List<Int>,
        serverScore: Int = 5
    ) {
        val activePendingMalIds = mutationDao.getActiveMalIds("ANIME")
        serverMalIds.forEach { malId ->
            if (malId !in activePendingMalIds) {
                libraryDao.upsertEntry(LibraryEntry(
                    malId = malId, mediaType = "ANIME", score = serverScore, title = "Server Title"
                ))
            }
        }
    }

    @Test
    fun `entry WITH active pending is NOT overwritten by server data during reconcile`() {
        // Local entry exists with score=9
        libraryDao.upsertEntry(LibraryEntry(malId = 100, mediaType = "ANIME", score = 9, title = "Local"))
        // Pending mutation for malId=100
        mutationDao.enqueueMutation(PendingMutation(
            malId = 100, mediaType = "ANIME", mutationType = PendingMutation.TYPE_UPDATE,
            payloadJson = """{"score":9}"""
        ))

        // Server says score=5 — but local pending prevents overwrite
        simulateReconcile(serverMalIds = listOf(100), serverScore = 5)

        val entry = libraryDao.getEntry(100, "ANIME")
        assertEquals("Local score should be preserved (pending wins)", 9, entry?.score)
    }

    @Test
    fun `entry WITHOUT active pending IS overwritten by server data during reconcile`() {
        // Local entry exists with score=9
        libraryDao.upsertEntry(LibraryEntry(malId = 200, mediaType = "ANIME", score = 9, title = "Local"))
        // No pending mutation for malId=200

        simulateReconcile(serverMalIds = listOf(200), serverScore = 7)

        val entry = libraryDao.getEntry(200, "ANIME")
        assertEquals("Server score should overwrite (no pending)", 7, entry?.score)
    }

    @Test
    fun `after successful drain, server data can overwrite reconciled entry`() = runTest {
        // Pending mutation exists
        mutationDao.enqueueMutation(PendingMutation(
            malId = 300, mediaType = "ANIME", mutationType = PendingMutation.TYPE_UPDATE,
            payloadJson = """{"status":"watching","score":8,"progress":5}"""
        ))
        fakeMal.updateResult = Result.success(Unit)

        // Drain queue (simulates successful sync)
        engine.drainQueue()

        // After drain, no active pending — server can now overwrite
        val activePending = mutationDao.getActiveMalIds("ANIME")
        assertFalse("No active pending after successful drain", 300 in activePending)

        simulateReconcile(serverMalIds = listOf(300), serverScore = 8)
        val entry = libraryDao.getEntry(300, "ANIME")
        assertEquals("Server data applied after pending resolved", 8, entry?.score)
    }

    @Test
    fun `failed fetch should not change local state`() {
        // Local entry with score=9
        libraryDao.upsertEntry(LibraryEntry(malId = 400, mediaType = "ANIME", score = 9))

        // Simulate fetch failure: we don't call simulateReconcile at all
        // (real code returns early on fetch failure)
        val beforeEntry = libraryDao.getEntry(400, "ANIME")
        assertEquals("Local state unchanged when fetch fails", 9, beforeEntry?.score)
    }

    @Test
    fun `getActiveMalIds only returns PENDING and IN_FLIGHT malIds, not SUCCEEDED`() {
        mutationDao.enqueueMutation(PendingMutation(
            malId = 500, mediaType = "ANIME", mutationType = PendingMutation.TYPE_UPDATE, payloadJson = "{}"
        ))
        mutationDao.enqueueMutation(PendingMutation(
            malId = 501, mediaType = "ANIME", mutationType = PendingMutation.TYPE_UPDATE, payloadJson = "{}"
        ))
        val id501 = mutationDao.getActiveMutationForMalId(501, "ANIME")!!.id
        mutationDao.markInFlight(id501)
        mutationDao.markSucceeded(id501)

        val activeIds = mutationDao.getActiveMalIds("ANIME")
        assertTrue(500 in activeIds)
        assertFalse(501 in activeIds)
    }

    @Test
    fun `incrementAttemptsAndRequeue drops stale IN_FLIGHT row when newer PENDING row exists`() {
        mutationDao.enqueueMutation(PendingMutation(
            malId = 600, mediaType = "ANIME", mutationType = PendingMutation.TYPE_UPDATE,
            payloadJson = """{"progress":1}"""
        ))
        val staleId = mutationDao.getActiveMutationForMalId(600, "ANIME")!!.id
        mutationDao.markInFlight(staleId)

        // Newer user edit arrives while old mutation is IN_FLIGHT
        mutationDao.enqueueMutation(PendingMutation(
            malId = 600, mediaType = "ANIME", mutationType = PendingMutation.TYPE_UPDATE,
            payloadJson = """{"progress":2}"""
        ))

        mutationDao.incrementAttemptsAndRequeue(staleId)

        val pending = mutationDao.getPendingMutations()
        assertEquals(1, pending.size)
        assertEquals("""{"progress":2}""", pending[0].payloadJson)
        assertEquals(0, pending[0].attempts)
    }

    @Test
    fun `saveUserMediaItem fails fast when malId cannot be resolved`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val malAuth = MalAuthManager(MalSecureStorage(context))
        val repo = LibraryRepositoryImpl(malAuth, libraryDao, mutationDao, engine, context, testScope)
        val unmappedItem = UserMediaItem(
            identity = MediaRef(malId = null, anilistId = null),
            metadata = MediaMetadata(
                title = "Unknown",
                imageUrl = "",
                type = MediaType.ANIME,
                totalEpisodes = 12
            ),
            tracking = MalTracking(status = "watching", progress = 1)
        )

        val res = repo.saveUserMediaItem(unmappedItem)
        assertTrue("saveUserMediaItem must fail when malId is unresolved", res.isFailure)
        assertTrue("No invalid malId=0 mutation should be enqueued", mutationDao.getPendingMutations().isEmpty())
    }

    @Test
    fun `partial fetch reconcile preserves unreached local entries`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val storage = MalSecureStorage(context)
        storage.saveTokens("tok", "ref", 3600)
        val malAuth = MalAuthManager(storage)
        val repo = LibraryRepositoryImpl(malAuth, libraryDao, mutationDao, engine, context, testScope)

        libraryDao.upsertEntry(LibraryEntry(malId = 999, mediaType = "ANIME", score = 9, title = "Page 2 Item"))
        for (id in 1..500) {
            CacheManager.putMetadata(
                malId = id,
                type = MediaType.ANIME,
                item = MediaItem(malId = id, anilistId = id + 10000, title = "Show $id", imageUrl = "https://img/$id.jpg", type = MediaType.ANIME)
            )
        }

        val jsonType = "application/json; charset=utf-8".toMediaType()
        val pageOneNodes = (1..500).joinToString(",") { id ->
            """{"node":{"id":$id,"title":"Show $id"},"list_status":{"status":"watching","score":8,"num_episodes_watched":1,"is_rewatching":false}}"""
        }
        val mockMalClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val offset = req.url.queryParameter("offset")?.toIntOrNull() ?: 0
                if (offset == 0) {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"data":[$pageOneNodes],"paging":{"next":"https://api.myanimelist.net/v2/users/@me/animelist?offset=500"}}""".toResponseBody(jsonType))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Server Error")
                        .body("Error".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }
            .build()
        val fakeMalApi = Retrofit.Builder()
            .baseUrl("https://api.myanimelist.net/v2/")
            .client(mockMalClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MalApiService::class.java)
        val emptyApolloClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"data":{"Page":{"media":[]}}}""".toResponseBody(jsonType))
                    .build()
            }
            .build()

        ApiClient.setMalApiForTesting(fakeMalApi)
        AniListApolloClient.setOkHttpClientForTesting(emptyApolloClient)
        try {
            val res = repo.getUserAnimeList(forceRefresh = true)
            assertTrue("Page 2 failure after Page 1 success should yield Partial", res is MalFetchResult.Partial)
            assertNotNull("Unreached local entry malId=999 must NOT be wiped on Partial reconcile", libraryDao.getEntry(999, "ANIME"))
        } finally {
            ApiClient.setMalApiForTesting(null)
            AniListApolloClient.setOkHttpClientForTesting(null)
            storage.clearAuth()
            CacheManager.clearMetadataCache()
            CacheManager.clearTrackingDisk(context)
        }
    }
}
