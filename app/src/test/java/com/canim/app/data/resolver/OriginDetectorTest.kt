package com.canim.app.data.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginDetectorTest {

    @Test
    fun testDetectOriginDirectCountryCode() {
        assertEquals("CN", OriginDetector.detectOrigin("Kyoto Animation", "CN"))
        assertEquals("KR", OriginDetector.detectOrigin("MAPPA", "KR"))
        assertEquals("JP", OriginDetector.detectOrigin("Studio Mir", "JP"))
    }

    @Test
    fun testDetectOriginHeuristicChineseStudio() {
        assertEquals("CN", OriginDetector.detectOrigin("Bilibili"))
        assertEquals("CN", OriginDetector.detectOrigin("Haoliners Animation League"))
        assertEquals("CN", OriginDetector.detectOrigin("B.CMAY PICTURES"))
        assertTrue(OriginDetector.isChineseStudio("Tencent Animation & Comics"))
    }

    @Test
    fun testDetectOriginHeuristicKoreanStudio() {
        assertEquals("KR", OriginDetector.detectOrigin("Studio Mir"))
        assertEquals("KR", OriginDetector.detectOrigin("DR Movie"))
        assertEquals("KR", OriginDetector.detectOrigin("SAMG Animation"))
        assertTrue(OriginDetector.isKoreanStudio("Studio Ppuri"))
    }

    @Test
    fun testDetectOriginDefaultJapanese() {
        assertEquals("JP", OriginDetector.detectOrigin("CloverWorks"))
        assertEquals("JP", OriginDetector.detectOrigin(null))
        assertFalse(OriginDetector.isChineseStudio("Kyoto Animation"))
        assertFalse(OriginDetector.isKoreanStudio("Kyoto Animation"))
    }
}
