package com.canim.app.data.repository

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MediaType
import com.canim.app.domain.repository.DetailRepository
import com.canim.app.domain.repository.SystemRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CanimRepositoryBoundaryTest {

    private lateinit var systemRepository: SystemRepository
    private lateinit var detailRepository: DetailRepository

    @Before
    fun setUp() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()

        val app = RuntimeEnvironment.getApplication()
        val storage = MalSecureStorage(app)
        val malAuth = MalAuthManager(storage)
        val swrCoordinator = SwrCoordinator()
        systemRepository = SystemRepositoryImpl(swrCoordinator)
        detailRepository = DetailRepositoryImpl(malAuth, swrCoordinator)
    }

    @After
    fun tearDown() {
        CacheManager.clearMetadataCache()
        CacheManager.clearIdMappings()
        CacheManager.clearNegativeCache()
    }

    @Test
    fun testRepositoryPruneCacheExecutesWithoutError() = kotlinx.coroutines.runBlocking {
        systemRepository.pruneCache()
    }

    @Test
    fun testGetCachedExtendedDetailWithStrictMediaTypeIsolation() {
        val sharedMalId = 100
        val animeAniListId = 1001
        val mangaAniListId = 2002

        // Register separate mappings for Anime vs Manga with the identical numeric MAL ID
        CacheManager.putIdMapping(malId = sharedMalId, aniListId = animeAniListId, type = MediaType.ANIME)
        CacheManager.putIdMapping(malId = sharedMalId, aniListId = mangaAniListId, type = MediaType.MANGA)

        val animeDetail = ExtendedMediaDetail(
            anilistId = animeAniListId,
            malId = sharedMalId,
            title = "Anime Shared ID Title",
            titleEnglish = null,
            coverImage = null,
            synopsis = "Anime detail"
        )
        val mangaDetail = ExtendedMediaDetail(
            anilistId = mangaAniListId,
            malId = sharedMalId,
            title = "Manga Shared ID Title",
            titleEnglish = null,
            coverImage = null,
            synopsis = "Manga detail"
        )

        CacheManager.putDetail(CacheManager.detailKey(animeAniListId, sharedMalId), animeDetail)
        CacheManager.putDetail(CacheManager.detailKey(mangaAniListId, sharedMalId), mangaDetail)

        // Query repository boundary passing ANIME
        val cachedAnime = detailRepository.getCachedExtendedDetail(
            aniListId = null,
            malId = sharedMalId,
            type = MediaType.ANIME
        )
        assertNotNull(cachedAnime)
        assertEquals(animeAniListId, cachedAnime?.anilistId)
        assertEquals("Anime Shared ID Title", cachedAnime?.title)

        // Query repository boundary passing MANGA
        val cachedManga = detailRepository.getCachedExtendedDetail(
            aniListId = null,
            malId = sharedMalId,
            type = MediaType.MANGA
        )
        assertNotNull(cachedManga)
        assertEquals(mangaAniListId, cachedManga?.anilistId)
        assertEquals("Manga Shared ID Title", cachedManga?.title)
    }

    @Test
    fun testGetCachedExtendedDetailDirectAniListIdLookup() {
        val detail = ExtendedMediaDetail(
            anilistId = 154587,
            malId = 52991,
            title = "Frieren",
            titleEnglish = null,
            coverImage = null,
            synopsis = null
        )
        CacheManager.putDetail(CacheManager.detailKey(154587, 52991), detail)

        val retrieved = detailRepository.getCachedExtendedDetail(aniListId = 154587, malId = 52991, type = MediaType.ANIME)
        assertNotNull(retrieved)
        assertEquals("Frieren", retrieved?.title)
    }
}
