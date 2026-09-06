package com.canim.app

import com.canim.app.data.remote.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionCheckTest {

    @Test
    fun testSemverParsing() {
        assertEquals(Triple(5, 1, 0), UpdateChecker.parseVersion("v5.1.0"))
        assertEquals(Triple(5, 1, 0), UpdateChecker.parseVersion("5.1.0"))
        assertEquals(Triple(5, 0, 0), UpdateChecker.parseVersion("v5.0.0"))
        assertEquals(Triple(4, 4, 3), UpdateChecker.parseVersion("v4.4.3"))
        assertEquals(Triple(5, 2, 1), UpdateChecker.parseVersion("v5.2.1-beta01"))
    }

    @Test
    fun testSemverComparisonNewer() {
        // Latest is newer than current
        assertEquals(1, UpdateChecker.compareSemver(current = "v5.0.0", latest = "v5.1.0"))
        assertEquals(1, UpdateChecker.compareSemver(current = "5.0.0", latest = "5.1.0"))
        assertEquals(1, UpdateChecker.compareSemver(current = "v5.1.0", latest = "v5.1.1"))
        assertEquals(1, UpdateChecker.compareSemver(current = "v4.9.9", latest = "v5.0.0"))
        assertEquals(1, UpdateChecker.compareSemver(current = "v5.0.9", latest = "v5.1.0"))
    }

    @Test
    fun testSemverComparisonEqual() {
        // Identical versions
        assertEquals(0, UpdateChecker.compareSemver(current = "v5.1.0", latest = "v5.1.0"))
        assertEquals(0, UpdateChecker.compareSemver(current = "v5.1.0", latest = "5.1.0"))
        assertEquals(0, UpdateChecker.compareSemver(current = "5.1.0", latest = "v5.1.0"))
        assertEquals(0, UpdateChecker.compareSemver(current = "v5.1.0-rc1", latest = "v5.1.0"))
    }

    @Test
    fun testSemverComparisonOlder() {
        // Current is newer than latest (e.g. dev build ahead of release)
        assertEquals(-1, UpdateChecker.compareSemver(current = "v5.2.0", latest = "v5.1.0"))
        assertEquals(-1, UpdateChecker.compareSemver(current = "v5.1.1", latest = "v5.1.0"))
        assertEquals(-1, UpdateChecker.compareSemver(current = "6.0.0", latest = "v5.1.0"))
    }
}
