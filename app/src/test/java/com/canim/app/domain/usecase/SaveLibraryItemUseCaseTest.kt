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
class SaveLibraryItemUseCaseTest {

    private class TestSaveRepo : FakeCanimRepository() {
        var testMalUser: MalUser = MalUser(username = "testUser", isLoggedIn = true)
        var lastSavedAnimeId: Int? = null
        var lastSavedAnimeTracking: MalTracking? = null
        var lastSavedMangaId: Int? = null
        var lastSavedMangaTracking: MalTracking? = null

        override fun getMalUser(): MalUser = testMalUser

        override suspend fun updateAnimeTracking(malId: Int, tracking: MalTracking): Result<Unit> {
            lastSavedAnimeId = malId
            lastSavedAnimeTracking = tracking
            return Result.success(Unit)
        }

        override suspend fun updateMangaTracking(malId: Int, tracking: MalTracking): Result<Unit> {
            lastSavedMangaId = malId
            lastSavedMangaTracking = tracking
            return Result.success(Unit)
        }

        override suspend fun saveUserMediaItem(item: UserMediaItem): Result<Unit> {
            if (item.isAnime) {
                lastSavedAnimeId = item.malId
                lastSavedAnimeTracking = item.tracking
            } else {
                lastSavedMangaId = item.malId
                lastSavedMangaTracking = item.tracking
            }
            return Result.success(Unit)
        }
    }

    private lateinit var fakeRepo: TestSaveRepo
    private lateinit var gachaCreditManager: GachaCreditManager
    private lateinit var consumeGachaCreditUseCase: ConsumeGachaCreditUseCase
    private lateinit var getMalUserUseCase: GetMalUserUseCase
    private lateinit var useCase: SaveLibraryItemUseCase

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        gachaCreditManager = GachaCreditManager.getInstance(app)
        consumeGachaCreditUseCase = ConsumeGachaCreditUseCase(gachaCreditManager)
        fakeRepo = TestSaveRepo()
        getMalUserUseCase = GetMalUserUseCase(fakeRepo)
        useCase = SaveLibraryItemUseCase(fakeRepo, consumeGachaCreditUseCase, getMalUserUseCase)
    }

    @Test
    fun testSaveAnimeLoggedIn() = runTest {
        val tracking = MalTracking(status = "watching", progress = 4)
        val result = useCase.saveAnime(malId = 1, tracking = tracking, mediaId = "anime_1")

        assertTrue(result.isSuccess)
        assertEquals(1, fakeRepo.lastSavedAnimeId)
        assertEquals(tracking, fakeRepo.lastSavedAnimeTracking)
    }

    @Test
    fun testSaveAnimeLoggedOutDoesNotCallRepo() = runTest {
        fakeRepo.testMalUser = MalUser(isLoggedIn = false)
        val tracking = MalTracking(status = "watching", progress = 4)
        val result = useCase.saveAnime(malId = 1, tracking = tracking)

        assertTrue(result.isSuccess)
        assertNull(fakeRepo.lastSavedAnimeId)
    }

    @Test
    fun testSaveAnimeNullMalIdDoesNotCallRepo() = runTest {
        val tracking = MalTracking(status = "watching", progress = 4)
        val result = useCase.saveAnime(malId = null, tracking = tracking)

        assertTrue(result.isSuccess)
        assertNull(fakeRepo.lastSavedAnimeId)
    }

    @Test
    fun testSaveMangaLoggedIn() = runTest {
        val tracking = MalTracking(status = "reading", progress = 10)
        val result = useCase.saveManga(malId = 2, tracking = tracking, mediaId = "manga_2")

        assertTrue(result.isSuccess)
        assertEquals(2, fakeRepo.lastSavedMangaId)
        assertEquals(tracking, fakeRepo.lastSavedMangaTracking)
    }

    @Test
    fun testSaveMangaLoggedOutDoesNotCallRepo() = runTest {
        fakeRepo.testMalUser = MalUser(isLoggedIn = false)
        val tracking = MalTracking(status = "reading", progress = 10)
        val result = useCase.saveManga(malId = 2, tracking = tracking)

        assertTrue(result.isSuccess)
        assertNull(fakeRepo.lastSavedMangaId)
    }

    @Test
    fun testInvokeWithUserMediaItem() = runTest {
        val item = UserMediaItem(
            identity = MediaRef(malId = 3, anilistId = 303),
            metadata = MediaMetadata(title = "Item 3", imageUrl = "", type = MediaType.ANIME),
            tracking = MalTracking(status = "completed", progress = 12)
        )
        val result = useCase(item)

        assertTrue(result.isSuccess)
        assertEquals(3, fakeRepo.lastSavedAnimeId)
    }

    @Test
    fun testRecordProgressAndAwardCredits() {
        consumeGachaCreditUseCase.initBaselineProgress("save_media_1", 0)
        val awarded = useCase.recordProgressAndAwardCredits("save_media_1", 5)
        assertEquals(5, awarded)
    }
}
