package com.canim.app

import android.content.Context
import com.canim.app.data.local.GachaCandidateStore
import com.canim.app.data.local.GachaCandidateToken
import com.canim.app.data.local.GachaCooldownManager
import com.canim.app.data.model.*
import com.canim.app.domain.gacha.AdaptivePreferenceModel
import com.canim.app.domain.usecase.LoadFlashcardDeckUseCase
import com.canim.app.ui.viewmodel.FakeCanimRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GachaAdaptivePreferenceTest {

    private lateinit var context: Context
    private lateinit var cooldownManager: GachaCooldownManager
    private lateinit var candidateStore: GachaCandidateStore

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("canim_gacha_cooldown_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("canim_gacha_candidates_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        cooldownManager = GachaCooldownManager(context)
        candidateStore = GachaCandidateStore(context)
    }

    private fun createUserItem(id: Int, title: String, genres: List<String>, status: String = "completed", score: Int = 10): UserMediaItem {
        return UserMediaItem(
            identity = MediaRef(malId = id, anilistId = id + 1000),
            metadata = MediaMetadata(title = title, imageUrl = "https://example.com/$id.jpg", type = MediaType.ANIME, genres = genres),
            tracking = MalTracking(status = status, score = score, progress = 12)
        )
    }

    private fun createMediaItem(id: Int, title: String, genres: List<String>): MediaItem {
        return MediaItem(
            malId = id,
            anilistId = id + 1000,
            title = title,
            imageUrl = "https://example.com/$id.jpg",
            type = MediaType.ANIME,
            genres = genres,
            score = 8.5
        )
    }

    @Test
    fun testAdaptivePreferenceModelDerivesAffinitiesDynamically() {
        // User with heavy Cyberpunk / Sci-Fi preference
        val library = listOf(
            createUserItem(1, "Ghost in the Shell", listOf("Action", "Sci-Fi", "Cyberpunk"), status = "completed", score = 10),
            createUserItem(2, "Akira", listOf("Action", "Sci-Fi"), status = "completed", score = 9),
            createUserItem(3, "Romance Drama", listOf("Romance", "Drama"), status = "dropped", score = 2)
        )

        val tendencies = AdaptivePreferenceModel.deriveTendencies(library)
        assertTrue("Sci-Fi affinity must be present", tendencies.containsKey("Sci-Fi"))
        assertTrue("Action affinity must be present", tendencies.containsKey("Action"))

        val sciFiScore = tendencies["Sci-Fi"] ?: 0.0
        val romanceScore = tendencies["Romance"] ?: 0.0

        assertTrue("Sci-Fi weight ($sciFiScore) must exceed dropped Romance weight ($romanceScore)", sciFiScore > romanceScore)

        // Calculate match scores for candidate anime
        val candidateSciFi = listOf("Sci-Fi", "Mecha")
        val candidateRomance = listOf("Romance", "Slice of Life")

        val sciFiAffinity = AdaptivePreferenceModel.calculateAffinityScore(candidateSciFi, tendencies)
        val romanceAffinity = AdaptivePreferenceModel.calculateAffinityScore(candidateRomance, tendencies)

        assertTrue("Candidate matching user's completed genres must score higher", sciFiAffinity > romanceAffinity)
    }

    @Test
    fun testFourteenDayCooldownPersistenceAndExpiry() {
        val now = System.currentTimeMillis()
        val animeMalId = 55432

        assertFalse(cooldownManager.isUnderCooldown(animeMalId, now))

        // Record draw
        cooldownManager.recordGachaDrawn(animeMalId, now)
        assertTrue("Anime must be under cooldown right after draw", cooldownManager.isUnderCooldown(animeMalId, now))
        assertTrue(cooldownManager.getCooldownMalIds(now).contains(animeMalId))

        // After 7 days (still under cooldown)
        val sevenDaysLater = now + (7L * 24 * 60 * 60 * 1000L)
        assertTrue("Anime must remain under cooldown after 7 days", cooldownManager.isUnderCooldown(animeMalId, sevenDaysLater))

        // After 14 days + 1 second (cooldown expired)
        val fourteenDaysAndOneSecond = now + (14L * 24 * 60 * 60 * 1000L) + 1000L
        assertFalse("Anime must be eligible again after 14 days", cooldownManager.isUnderCooldown(animeMalId, fourteenDaysAndOneSecond))
        assertFalse(cooldownManager.getCooldownMalIds(fourteenDaysAndOneSecond).contains(animeMalId))
    }

    @Test
    fun testLibraryExclusionTakesPrecedenceOverCooldownExpiry() = runTest {
        val now = System.currentTimeMillis()
        val animeMalId = 11111

        // Record cooldown drawn 20 days ago (cooldown expired)
        val twentyDaysAgo = now - (20L * 24 * 60 * 60 * 1000L)
        cooldownManager.recordGachaDrawn(animeMalId, twentyDaysAgo)
        assertFalse(cooldownManager.isUnderCooldown(animeMalId, now))

        // But user has added it to their library
        val testRepo = object : FakeCanimRepository() {
            override fun getCachedTracking(type: String): List<UserMediaItem>? = listOf(
                createUserItem(animeMalId, "Watched Anime", listOf("Action"))
            )
            override suspend fun getDiscoverMedia(
                category: DiscoverCategory,
                filter: DiscoverFilter,
                page: Int,
                forceRefresh: Boolean,
                randomSort: String?,
                mediaType: MediaType?
            ): List<MediaItem> = listOf(
                createMediaItem(animeMalId, "Watched Anime", listOf("Action")),
                createMediaItem(22222, "Eligible Anime", listOf("Action"))
            )
        }

        val useCase = LoadFlashcardDeckUseCase(
            discoverRepository = testRepo,
            libraryRepository = testRepo,
            cooldownManager = cooldownManager,
            candidateStore = candidateStore
        )

        val deck = useCase()
        assertFalse("Watched/Library anime must NEVER be returned, even if cooldown expired", deck.any { it.malId == animeMalId })
        assertTrue("Eligible anime must be included", deck.any { it.malId == 22222 })
    }

    @Test
    fun testLightweightCandidateTokensStoreOnlyMalIdAndGenres() {
        val token = GachaCandidateToken(malId = 9999, genres = listOf("Psychological", "Thriller"))
        candidateStore.addCandidates(listOf(token))

        val retrieved = candidateStore.getAllCandidates()
        assertEquals(1, retrieved.size)
        assertEquals(9999, retrieved[0].malId)
        assertEquals(listOf("Psychological", "Thriller"), retrieved[0].genres)
    }
}
