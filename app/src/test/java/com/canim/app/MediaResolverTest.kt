package com.canim.app

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.MediaRef
import com.canim.app.data.resolver.MediaResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MediaResolverTest {

    @Before
    fun setUp() {
        CacheManager.clearIdMappings()
    }

    @Test
    fun testMediaRefSeparation() {
        val ref = MediaRef(anilistId = 16498, malId = 52991) // Frieren
        assertNotEquals(ref.anilistId, ref.malId)
        assertTrue(ref.hasBoth)
        assertTrue(ref.isResolved)
    }

    @Test
    fun testMediaRefPartialResolution() {
        val aniOnly = MediaRef(anilistId = 12345, malId = null)
        assertTrue(aniOnly.isResolved)
        assertFalse(aniOnly.hasBoth)
        assertNull(aniOnly.malId)

        val malOnly = MediaRef(anilistId = null, malId = 54321)
        assertTrue(malOnly.isResolved)
        assertFalse(malOnly.hasBoth)
        assertNull(malOnly.anilistId)
    }

    @Test
    fun testMediaResolverPrepopulateAndLookup() = runBlocking {
        // Pre-populate mappings
        MediaResolver.registerMapping(anilistId = 21, malId = 21) // One Piece happens to share ID
        MediaResolver.registerMapping(anilistId = 16498, malId = 52991) // Frieren differs

        val resolvedMalId = MediaResolver.resolveMalIdForAniListId(16498)
        assertEquals(52991, resolvedMalId)

        val resolvedAniId = MediaResolver.resolveAniListIdForMalId(52991, com.canim.app.data.model.MediaType.ANIME)
        assertEquals(16498, resolvedAniId)
    }

    @Test
    fun testMediaResolverDoesNotFabricateId() = runBlocking {
        // Looking up an unknown AniList ID should not fabricate malId = anilistId
        val malId = CacheManager.getMalIdForAniListId(99999999)
        assertNull(malId)
        assertNotEquals(99999999, malId)
    }
}
