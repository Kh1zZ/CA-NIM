package com.canim.app

import com.canim.app.util.MediaDisplayFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MediaDisplayFormatterTest {

    @Test
    fun testFormatStatusIndonesian() {
        assertEquals("Sedang Tayang", MediaDisplayFormatter.formatStatus("currently_airing"))
        assertEquals("Tamat", MediaDisplayFormatter.formatStatus("finished_airing"))
        assertEquals("Belum Tayang", MediaDisplayFormatter.formatStatus("not_yet_aired"))
        assertEquals("Sedang Tayang", MediaDisplayFormatter.formatStatus("RELEASING"))
        assertEquals("Tamat", MediaDisplayFormatter.formatStatus("FINISHED"))
        assertEquals("Belum Tayang", MediaDisplayFormatter.formatStatus("NOT_YET_RELEASED"))
        assertEquals("Dibatalkan", MediaDisplayFormatter.formatStatus("CANCELLED"))
        assertEquals("Ditunda (Hiatus)", MediaDisplayFormatter.formatStatus("HIATUS"))
        assertEquals("Tidak Diketahui", MediaDisplayFormatter.formatStatus(null))
        assertEquals("Tidak Diketahui", MediaDisplayFormatter.formatStatus(""))
    }

    @Test
    fun testFormatFormatIndonesian() {
        assertEquals("Serial TV", MediaDisplayFormatter.formatFormat("tv"))
        assertEquals("Film Layar Lebar", MediaDisplayFormatter.formatFormat("movie"))
        assertEquals("Spesial TV", MediaDisplayFormatter.formatFormat("tv_special"))
        assertEquals("OVA", MediaDisplayFormatter.formatFormat("ova"))
        assertEquals("ONA (Web Anime)", MediaDisplayFormatter.formatFormat("ona"))
        assertEquals("Video Musik", MediaDisplayFormatter.formatFormat("music"))
        assertEquals("Lainnya", MediaDisplayFormatter.formatFormat(null))
    }

    @Test
    fun testFormatSourceIndonesian() {
        assertEquals("Adaptasi Manga", MediaDisplayFormatter.formatSource("manga"))
        assertEquals("Light Novel", MediaDisplayFormatter.formatSource("light_novel"))
        assertEquals("Visual Novel", MediaDisplayFormatter.formatSource("visual_novel"))
        assertEquals("Karya Orisinal", MediaDisplayFormatter.formatSource("original"))
        assertEquals("Video Game", MediaDisplayFormatter.formatSource("video_game"))
        assertEquals("Web Novel / Manga", MediaDisplayFormatter.formatSource("web_manga"))
        assertEquals("Tidak Diketahui", MediaDisplayFormatter.formatSource(null))
    }

    @Test
    fun testFormatDateIndonesian() {
        assertEquals("5 Oktober 2023", MediaDisplayFormatter.formatDateIndonesian("2023-10-05"))
        assertEquals("1 Januari 2024", MediaDisplayFormatter.formatDateIndonesian("2024-01-01"))
        assertEquals("Desember 2022", MediaDisplayFormatter.formatDateIndonesian("2022-12"))
        assertEquals("2021", MediaDisplayFormatter.formatDateIndonesian("2021"))
        assertNull(MediaDisplayFormatter.formatDateIndonesian(null))
        assertNull(MediaDisplayFormatter.formatDateIndonesian(""))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("24 menit per episode", MediaDisplayFormatter.formatDuration(24))
        assertEquals("120 menit per episode", MediaDisplayFormatter.formatDuration(120))
        assertNull(MediaDisplayFormatter.formatDuration(null))
        assertNull(MediaDisplayFormatter.formatDuration(0))
    }

    @Test
    fun testNoRawUnderscoresOrDashesInFormattedValues() {
        val testInputs = listOf(
            "tv_special",
            "light_novel",
            "not_yet_aired",
            "video_game",
            "web_manga",
            "super_natural_action"
        )
        for (input in testInputs) {
            val statusRes = MediaDisplayFormatter.formatStatus(input)
            val formatRes = MediaDisplayFormatter.formatFormat(input)
            val sourceRes = MediaDisplayFormatter.formatSource(input)
            assertFalse(statusRes.contains("_"))
            assertFalse(formatRes.contains("_"))
            assertFalse(sourceRes.contains("_"))
        }
    }
}
