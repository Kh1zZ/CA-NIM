package com.canim.app.domain.usecase

import com.canim.app.data.model.*
import com.canim.app.domain.repository.DiscoverRepository
import com.canim.app.domain.repository.LibraryRepository
import com.canim.app.ui.viewmodel.FakeCanimRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoadFlashcardDeckUseCaseTest {

    private class TestFlashcardRepository : FakeCanimRepository() {
        var cachedAnime: List<UserMediaItem>? = null
        var discoverItems: List<MediaItem> = emptyList()
        var demoAnimeList: List<UserMediaItem> = listOf(
            UserMediaItem(
                identity = MediaRef(malId = 999, anilistId = 1999),
                metadata = MediaMetadata(title = "Demo Anime", imageUrl = "https://example.com/demo.jpg", type = MediaType.ANIME),
                tracking = MalTracking()
            )
        )

        override fun getCachedTracking(type: String): List<UserMediaItem>? = cachedAnime
        override fun getDemoAnime(): List<UserMediaItem> = demoAnimeList

        override suspend fun getDiscoverMedia(
            category: DiscoverCategory,
            filter: DiscoverFilter,
            page: Int,
            forceRefresh: Boolean,
            randomSort: String?,
            mediaType: MediaType?
        ): List<MediaItem> = discoverItems
    }

    private lateinit var fakeRepo: TestFlashcardRepository
    private lateinit var useCase: LoadFlashcardDeckUseCase

    @Before
    fun setUp() {
        fakeRepo = TestFlashcardRepository()
        useCase = LoadFlashcardDeckUseCase(
            discoverRepository = fakeRepo,
            libraryRepository = fakeRepo
        )
    }

    private fun createMediaItem(id: Int, title: String): MediaItem {
        return MediaItem(
            malId = id,
            anilistId = id + 1000,
            title = title,
            titleEnglish = title,
            imageUrl = "https://example.com/$id.jpg",
            type = MediaType.ANIME,
            score = 8.0,
            synopsis = "Synopsis $id",
            episodes = 12,
            genres = listOf("Action"),
            status = "FINISHED"
        )
    }

    private fun createUserItem(id: Int, title: String): UserMediaItem {
        return UserMediaItem(
            identity = MediaRef(malId = id, anilistId = id + 1000),
            metadata = MediaMetadata(title = title, imageUrl = "https://example.com/$id.jpg", type = MediaType.ANIME),
            tracking = MalTracking(status = "completed", progress = 12)
        )
    }

    @Test
    fun testExcludesItemsPresentInLibraryRepository() = runTest {
        // Given anime 100 is in the user's library
        fakeRepo.cachedAnime = listOf(createUserItem(100, "In Library Anime"))

        // Given discover returns items including anime 100 and anime 200
        fakeRepo.discoverItems = listOf(
            createMediaItem(100, "In Library Anime"),
            createMediaItem(200, "New Anime 200")
        )

        // When invoking without parameters
        val deck = useCase()

        // Then anime 100 must be excluded, only anime 200 included
        assertFalse(deck.any { it.malId == 100 })
        assertTrue(deck.any { it.malId == 200 })
    }

    @Test
    fun testFallbackToDemoAnimeWhenDiscoverIsEmpty() = runTest {
        fakeRepo.cachedAnime = emptyList()
        fakeRepo.discoverItems = emptyList()

        val deck = useCase()

        // Fallback uses demo anime items
        assertTrue(deck.isNotEmpty())
    }

    @Test
    fun testExcludesItemsMatchingAniListIdEvenIfMalIdIsNull() = runTest {
        // Given anime with anilistId 5000 is in the user's library
        fakeRepo.cachedAnime = listOf(
            UserMediaItem(
                identity = MediaRef(malId = null, anilistId = 5000),
                metadata = MediaMetadata(title = "AniList Only Library", imageUrl = "https://example.com/5000.jpg", type = MediaType.ANIME),
                tracking = MalTracking(status = "completed")
            )
        )

        // Given discover returns an item with anilistId 5000 (malId null) and another with 6000
        fakeRepo.discoverItems = listOf(
            MediaItem(malId = null, anilistId = 5000, title = "AniList Only Library", imageUrl = "https://example.com/5000.jpg", type = MediaType.ANIME),
            MediaItem(malId = null, anilistId = 6000, title = "AniList Only Eligible", imageUrl = "https://example.com/6000.jpg", type = MediaType.ANIME)
        )

        val deck = useCase()

        assertFalse("AniList-only item in library must be excluded", deck.any { it.anilistId == 5000 })
        assertTrue("Eligible item must be included", deck.any { it.anilistId == 6000 })
    }

    @Test
    fun testTargetedPoolPrioritizesUserPreferredGenres() = runTest {
        // User library with Sci-Fi completed
        fakeRepo.cachedAnime = listOf(
            UserMediaItem(
                identity = MediaRef(malId = 1, anilistId = 1001),
                metadata = MediaMetadata(title = "SciFi Hit", imageUrl = "https://example.com/1.jpg", type = MediaType.ANIME, genres = listOf("Sci-Fi")),
                tracking = MalTracking(status = "completed", score = 10)
            )
        )

        fakeRepo.discoverItems = listOf(
            createMediaItem(2, "Romance Anime").copy(genres = listOf("Romance")),
            createMediaItem(3, "Sci-Fi Anime").copy(genres = listOf("Sci-Fi"))
        )

        val deck = useCase()

        assertTrue(deck.isNotEmpty())
        assertEquals("Top card must match user's top genre", 3, deck.first().malId)
    }
}
