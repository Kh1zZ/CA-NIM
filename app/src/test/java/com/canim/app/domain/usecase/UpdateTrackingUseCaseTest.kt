package com.canim.app.domain.usecase

import com.canim.app.data.model.*
import com.canim.app.ui.viewmodel.FakeCanimRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateTrackingUseCaseTest {

    private class TestTrackingRepository : FakeCanimRepository() {
        var lastUpdatedAnimeId: Int? = null
        var lastUpdatedAnimeTracking: MalTracking? = null
        var lastUpdatedMangaId: Int? = null
        var lastUpdatedMangaTracking: MalTracking? = null

        override suspend fun updateAnimeTracking(malId: Int, tracking: MalTracking): Result<Unit> {
            lastUpdatedAnimeId = malId
            lastUpdatedAnimeTracking = tracking
            return Result.success(Unit)
        }

        override suspend fun updateMangaTracking(malId: Int, tracking: MalTracking): Result<Unit> {
            lastUpdatedMangaId = malId
            lastUpdatedMangaTracking = tracking
            return Result.success(Unit)
        }
    }

    private lateinit var fakeRepository: TestTrackingRepository
    private lateinit var useCase: UpdateTrackingUseCase

    @Before
    fun setUp() {
        fakeRepository = TestTrackingRepository()
        useCase = UpdateTrackingUseCase(fakeRepository)
    }

    private fun createAnimeItem(
        status: String = "watching",
        progress: Int = 10,
        totalEpisodes: Int = 24
    ): UserMediaItem {
        return UserMediaItem(
            identity = MediaRef(malId = 1, anilistId = 101),
            metadata = MediaMetadata(
                title = "Test Anime",
                imageUrl = "https://example.com/anime.jpg",
                type = MediaType.ANIME,
                totalEpisodes = totalEpisodes
            ),
            tracking = MalTracking(
                status = status,
                progress = progress
            )
        )
    }

    private fun createMangaItem(
        status: String = "reading",
        progress: Int = 15,
        totalChapters: Int = 100
    ): UserMediaItem {
        return UserMediaItem(
            identity = MediaRef(malId = 2, anilistId = 102),
            metadata = MediaMetadata(
                title = "Test Manga",
                imageUrl = "https://example.com/manga.jpg",
                type = MediaType.MANGA,
                totalChapters = totalChapters
            ),
            tracking = MalTracking(
                status = status,
                progress = progress
            )
        )
    }

    @Test
    fun testStatusChangedToCompletedSetsProgressToTotalEpisodesForAnime() {
        val anime = createAnimeItem(status = "watching", progress = 10, totalEpisodes = 24)

        val result = useCase.applyStatusChange(anime, "completed")

        assertEquals("completed", result.tracking.status)
        assertEquals(24, result.tracking.progress)
    }

    @Test
    fun testStatusChangedToCompletedSetsProgressToTotalChaptersForManga() {
        val manga = createMangaItem(status = "reading", progress = 15, totalChapters = 100)

        val result = useCase.applyStatusChange(manga, "completed")

        assertEquals("completed", result.tracking.status)
        assertEquals(100, result.tracking.progress)
    }

    @Test
    fun testStatusChangedToNonCompletedDoesNotAutoFillProgress() {
        val anime = createAnimeItem(status = "plan_to_watch", progress = 0, totalEpisodes = 12)

        val resultWatching = useCase.applyStatusChange(anime, "watching")
        assertEquals("watching", resultWatching.tracking.status)
        assertEquals(0, resultWatching.tracking.progress)

        val resultOnHold = useCase.applyStatusChange(resultWatching.copy(tracking = resultWatching.tracking.copy(progress = 5)), "on_hold")
        assertEquals("on_hold", resultOnHold.tracking.status)
        assertEquals(5, resultOnHold.tracking.progress)

        val resultDropped = useCase.applyStatusChange(resultOnHold, "dropped")
        assertEquals("dropped", resultDropped.tracking.status)
        assertEquals(5, resultDropped.tracking.progress)
    }

    @Test
    fun testStatusChangedToCompletedWithZeroTotalKeepsOriginalProgress() {
        val animeZeroTotal = createAnimeItem(status = "watching", progress = 7, totalEpisodes = 0)

        val result = useCase.applyStatusChange(animeZeroTotal, "completed")

        assertEquals("completed", result.tracking.status)
        assertEquals(7, result.tracking.progress)
    }

    @Test
    fun testInvokeCallsRepositoryUpdateAnimeTracking() = runTest {
        val tracking = MalTracking(status = "watching", progress = 5)

        val result = useCase.updateAnime(1, tracking)

        assertTrue(result.isSuccess)
        assertEquals(1, fakeRepository.lastUpdatedAnimeId)
        assertEquals(tracking, fakeRepository.lastUpdatedAnimeTracking)
    }

    @Test
    fun testInvokeCallsRepositoryUpdateMangaTracking() = runTest {
        val tracking = MalTracking(status = "reading", progress = 20)

        val result = useCase.updateManga(2, tracking)

        assertTrue(result.isSuccess)
        assertEquals(2, fakeRepository.lastUpdatedMangaId)
        assertEquals(tracking, fakeRepository.lastUpdatedMangaTracking)
    }
}
