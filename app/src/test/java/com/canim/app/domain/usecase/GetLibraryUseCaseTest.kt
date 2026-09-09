package com.canim.app.domain.usecase

import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.model.*
import com.canim.app.ui.viewmodel.FakeCanimRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GetLibraryUseCaseTest {

    private class TestLibraryRepo : FakeCanimRepository() {
        var testMalUser: MalUser = MalUser(username = "testUser", isLoggedIn = true)
        var userAnimeListToReturn: List<UserMediaItem> = listOf(
            UserMediaItem(
                identity = MediaRef(malId = 10, anilistId = 110),
                metadata = MediaMetadata(title = "Anime 10", imageUrl = "", type = MediaType.ANIME),
                tracking = MalTracking(status = "watching", progress = 3)
            )
        )
        var userMangaListToReturn: List<UserMediaItem> = listOf(
            UserMediaItem(
                identity = MediaRef(malId = 20, anilistId = 120),
                metadata = MediaMetadata(title = "Manga 20", imageUrl = "", type = MediaType.MANGA),
                tracking = MalTracking(status = "reading", progress = 12)
            )
        )
        var demoAnimeToReturn: List<UserMediaItem> = listOf(
            UserMediaItem(
                identity = MediaRef(malId = 99, anilistId = 199),
                metadata = MediaMetadata(title = "Demo Anime", imageUrl = "", type = MediaType.ANIME),
                tracking = MalTracking(status = "watching", progress = 1)
            )
        )
        var demoMangaToReturn: List<UserMediaItem> = listOf(
            UserMediaItem(
                identity = MediaRef(malId = 88, anilistId = 188),
                metadata = MediaMetadata(title = "Demo Manga", imageUrl = "", type = MediaType.MANGA),
                tracking = MalTracking(status = "reading", progress = 2)
            )
        )
        var getUserAnimeListCalled = false
        var getUserMangaListCalled = false

        override fun getMalUser(): MalUser = testMalUser

        override suspend fun getUserAnimeList(forceRefresh: Boolean): MalFetchResult<List<UserMediaItem>> {
            getUserAnimeListCalled = true
            return MalFetchResult.Success(userAnimeListToReturn, userAnimeListToReturn.size)
        }

        override suspend fun getUserMangaList(forceRefresh: Boolean): MalFetchResult<List<UserMediaItem>> {
            getUserMangaListCalled = true
            return MalFetchResult.Success(userMangaListToReturn, userMangaListToReturn.size)
        }

        override fun getDemoAnime(): List<UserMediaItem> = demoAnimeToReturn
        override fun getDemoManga(): List<UserMediaItem> = demoMangaToReturn
        override fun getLastSyncedTime(): Long = 123456L
        override fun getCachedTracking(type: String): List<UserMediaItem>? =
            if (type == "ANIME") userAnimeListToReturn else userMangaListToReturn
    }

    private lateinit var fakeRepo: TestLibraryRepo
    private lateinit var gachaCreditManager: GachaCreditManager
    private lateinit var consumeGachaCreditUseCase: ConsumeGachaCreditUseCase
    private lateinit var getMalUserUseCase: GetMalUserUseCase
    private lateinit var useCase: GetLibraryUseCase

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        gachaCreditManager = GachaCreditManager.getInstance(app)
        consumeGachaCreditUseCase = ConsumeGachaCreditUseCase(gachaCreditManager)
        fakeRepo = TestLibraryRepo()
        getMalUserUseCase = GetMalUserUseCase(fakeRepo)
        useCase = GetLibraryUseCase(fakeRepo, consumeGachaCreditUseCase, getMalUserUseCase)
    }

    @Test
    fun testGetUserAnimeListLoggedIn() = runTest {
        val result = useCase.getUserAnimeList()
        assertTrue(result is MalFetchResult.Success)
        val data = (result as MalFetchResult.Success).data
        assertEquals(1, data.size)
        assertEquals("Anime 10", data[0].title)
        assertTrue(fakeRepo.getUserAnimeListCalled)
    }

    @Test
    fun testGetUserAnimeListLoggedOutReturnsDemo() = runTest {
        fakeRepo.testMalUser = MalUser(isLoggedIn = false)
        val result = useCase.getUserAnimeList()
        assertTrue(result is MalFetchResult.Success)
        val data = (result as MalFetchResult.Success).data
        assertEquals(1, data.size)
        assertEquals("Demo Anime", data[0].title)
        assertFalse(fakeRepo.getUserAnimeListCalled)
    }

    @Test
    fun testGetUserMangaListLoggedIn() = runTest {
        val result = useCase.getUserMangaList()
        assertTrue(result is MalFetchResult.Success)
        val data = (result as MalFetchResult.Success).data
        assertEquals(1, data.size)
        assertEquals("Manga 20", data[0].title)
        assertTrue(fakeRepo.getUserMangaListCalled)
    }

    @Test
    fun testGetUserMangaListLoggedOutReturnsDemo() = runTest {
        fakeRepo.testMalUser = MalUser(isLoggedIn = false)
        val result = useCase.getUserMangaList()
        assertTrue(result is MalFetchResult.Success)
        val data = (result as MalFetchResult.Success).data
        assertEquals(1, data.size)
        assertEquals("Demo Manga", data[0].title)
        assertFalse(fakeRepo.getUserMangaListCalled)
    }

    @Test
    fun testGetLastSyncedTimeAndCachedTracking() {
        assertEquals(123456L, useCase.getLastSyncedTime())
        assertNotNull(useCase.getCachedTracking("ANIME"))
        assertEquals(1, useCase.getCachedTracking("ANIME")?.size)
    }
}
