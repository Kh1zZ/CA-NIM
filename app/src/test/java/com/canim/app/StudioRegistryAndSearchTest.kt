package com.canim.app

import com.canim.app.data.repository.StudioBioRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioRegistryAndSearchTest {

    @Test
    fun testA1PicturesIdAndMetadata() {
        val studio = StudioBioRegistry.getStudioInfo(561, "A-1 Pictures")
        assertEquals(561, studio.studioId)
        assertEquals("A-1 Pictures", studio.name)
        assertEquals(2005, studio.foundedYear)
        assertEquals("Jepang", studio.country)
        assertNotNull(studio.bio)
        assertTrue(studio.bio!!.contains("Aniplex") || studio.bio!!.contains("Solo Leveling"))
    }

    @Test
    fun testCloverWorksIdAndMetadata() {
        val studio = StudioBioRegistry.getStudioInfo(6222, "CloverWorks")
        assertEquals(6222, studio.studioId)
        assertEquals("CloverWorks", studio.name)
        assertEquals(2018, studio.foundedYear)
        assertEquals("Jepang", studio.country)
        assertNotNull(studio.bio)
        assertTrue(studio.bio!!.contains("Bocchi") || studio.bio!!.contains("Spy x Family"))
    }

    @Test
    fun testCoMixWaveFilmsIdAndMetadata() {
        val studio = StudioBioRegistry.getStudioInfo(291, "CoMix Wave Films")
        assertEquals(291, studio.studioId)
        assertEquals("CoMix Wave Films", studio.name)
        assertEquals(2007, studio.foundedYear)
        assertNotNull(studio.bio)
        assertTrue(studio.bio!!.contains("Makoto Shinkai") || studio.bio!!.contains("Your Name"))
    }

    @Test
    fun testKinemaCitrusIdAndMetadata() {
        val studio = StudioBioRegistry.getStudioInfo(290, "Kinema Citrus")
        assertEquals(290, studio.studioId)
        assertEquals("Kinema Citrus", studio.name)
        assertEquals(2008, studio.foundedYear)
        assertNotNull(studio.bio)
        assertTrue(studio.bio!!.contains("Made in Abyss"))
    }

    @Test
    fun testSearchCuratedStudiosByName() {
        val resultsA1 = StudioBioRegistry.searchCuratedStudios("a-1")
        assertTrue(resultsA1.isNotEmpty())
        assertTrue(resultsA1.any { it.studioId == 561 && it.name == "A-1 Pictures" })

        val resultsClover = StudioBioRegistry.searchCuratedStudios("clover")
        assertTrue(resultsClover.isNotEmpty())
        assertTrue(resultsClover.any { it.studioId == 6222 && it.name == "CloverWorks" })
    }

    @Test
    fun testSearchCuratedStudiosEmptyReturnsAll() {
        val all = StudioBioRegistry.searchCuratedStudios("")
        assertTrue(all.size >= 20)
        // Ensure no duplicate IDs in curated registry
        val uniqueIds = all.map { it.studioId }.toSet()
        assertEquals(all.size, uniqueIds.size)
    }
}
