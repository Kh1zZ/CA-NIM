package com.canim.app.util

import java.text.NumberFormat
import java.util.Locale

object MediaDisplayFormatter {

    /**
     * Formats raw API status into natural, polite Indonesian.
     * Removes underscores and avoids all-caps or raw slugs.
     */
    fun formatStatus(rawStatus: String?): String {
        if (rawStatus.isNullOrBlank()) return "Tidak Diketahui"
        val normalized = rawStatus.lowercase().trim().replace(" ", "_")
        return when (normalized) {
            "currently_airing", "airing", "releasing" -> "Sedang Tayang"
            "finished_airing", "finished", "completed", "aired" -> "Tamat"
            "not_yet_aired", "not_yet_released", "unreleased" -> "Belum Tayang"
            "currently_publishing", "publishing" -> "Sedang Terbit"
            "on_hiatus", "hiatus" -> "Ditunda (Hiatus)"
            "discontinued", "cancelled", "canceled" -> "Dibatalkan"
            else -> cleanRawSlug(rawStatus)
        }
    }

    /**
     * Formats raw media format into friendly Indonesian terms without underscores.
     */
    fun formatFormat(rawFormat: String?): String {
        if (rawFormat.isNullOrBlank()) return "Lainnya"
        val normalized = rawFormat.lowercase().trim().replace(" ", "_")
        return when (normalized) {
            "tv" -> "Serial TV"
            "tv_short" -> "TV Pendek"
            "movie" -> "Film Layar Lebar"
            "special", "tv_special" -> "Spesial TV"
            "ova" -> "OVA"
            "ona" -> "ONA (Web Anime)"
            "music" -> "Video Musik"
            "manga" -> "Manga"
            "novel" -> "Novel"
            "one_shot" -> "One-Shot"
            "light_novel" -> "Light Novel"
            "visual_novel" -> "Visual Novel"
            "manhwa" -> "Manhwa"
            "manhua" -> "Manhua"
            else -> cleanRawSlug(rawFormat)
        }
    }

    /**
     * Formats media source into Indonesian terminology without underscores.
     */
    fun formatSource(rawSource: String?): String {
        if (rawSource.isNullOrBlank()) return "Tidak Diketahui"
        val normalized = rawSource.lowercase().trim().replace(" ", "_")
        return when (normalized) {
            "original" -> "Karya Orisinal"
            "manga" -> "Adaptasi Manga"
            "light_novel" -> "Light Novel"
            "visual_novel" -> "Visual Novel"
            "video_game", "game" -> "Video Game"
            "novel" -> "Novel"
            "doujinshi" -> "Doujinshi"
            "anime" -> "Anime"
            "web_novel", "web_manga" -> "Web Novel / Manga"
            "comic" -> "Komik Barat"
            "multimedia_project" -> "Proyek Multimedia"
            "picture_book" -> "Buku Bergambar"
            "other" -> "Lainnya"
            else -> cleanRawSlug(rawSource)
        }
    }

    /**
     * Parses dates like "2023-10-05", "2023-10", "2023" or fuzzy date strings
     * and returns natural Indonesian date text (e.g. "5 Oktober 2023").
     * Eliminates raw hyphen/dash characters ("2023-10-05" -> "5 Oktober 2023").
     */
    fun formatDateIndonesian(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()

        // Match ISO YYYY-MM-DD
        val ymdRegex = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
        val ymdMatch = ymdRegex.matchEntire(trimmed)
        if (ymdMatch != null) {
            val (year, month, day) = ymdMatch.destructured
            val monthName = getIndonesianMonth(month.toIntOrNull() ?: 0)
            val d = day.toIntOrNull() ?: day
            return "$d $monthName $year"
        }

        // Match ISO YYYY-MM
        val ymRegex = Regex("""^(\d{4})-(\d{1,2})$""")
        val ymMatch = ymRegex.matchEntire(trimmed)
        if (ymMatch != null) {
            val (year, month) = ymMatch.destructured
            val monthName = getIndonesianMonth(month.toIntOrNull() ?: 0)
            return "$monthName $year"
        }

        // Just Year (e.g. "2023")
        if (trimmed.matches(Regex("""^\d{4}$"""))) {
            return trimmed
        }

        // Handle fuzzy date already partially formatted with short month, e.g. "5 Okt 2023" or "Okt 2023"
        return trimmed
            .replace("-", " ")
            .replace("_", " ")
            .replace("Jan", "Januari")
            .replace("Feb", "Februari")
            .replace("Mar", "Maret")
            .replace("Apr", "April")
            .replace("Mei", "Mei")
            .replace("Jun", "Juni")
            .replace("Jul", "Juli")
            .replace("Agt", "Agustus")
            .replace("Sep", "September")
            .replace("Okt", "Oktober")
            .replace("Nov", "November")
            .replace("Des", "Desember")
    }

    /**
     * Formats duration in minutes into grammatically correct Indonesian.
     */
    fun formatDuration(durationMinutes: Int?, format: String? = null): String? {
        if (durationMinutes == null || durationMinutes <= 0) return null
        val isMovie = format?.equals("movie", ignoreCase = true) == true
        return if (isMovie) "$durationMinutes menit (Durasi Penuh)" else "$durationMinutes menit per episode"
    }

    /**
     * Formats Season and Year into natural Indonesian (e.g., "Musim Gugur 2023").
     */
    fun formatSeasonYear(season: String?, year: Int?): String? {
        if (season.isNullOrBlank() && (year == null || year <= 0)) return null
        val indonesianSeason = when (season?.uppercase()?.trim()) {
            "WINTER" -> "Musim Dingin"
            "SPRING" -> "Musim Semi"
            "SUMMER" -> "Musim Panas"
            "FALL" -> "Musim Gugur"
            else -> season?.let { cleanRawSlug(it) }
        }
        return when {
            indonesianSeason != null && year != null && year > 0 -> "$indonesianSeason $year"
            indonesianSeason != null -> indonesianSeason
            year != null && year > 0 -> "$year"
            else -> null
        }
    }

    /**
     * Replaces raw dashes ("—") in rank metric with proper informative Indonesian text.
     */
    fun formatMetricRank(rank: Int?): String {
        return if (rank != null && rank > 0) "#${formatNumber(rank)}" else "Belum Terdaftar"
    }

    /**
     * Replaces raw dashes ("—") in popularity metric with proper informative Indonesian text.
     */
    fun formatMetricPopularity(popularity: Int?): String {
        return if (popularity != null && popularity > 0) "#${formatNumber(popularity)}" else "Belum Terdaftar"
    }

    /**
     * Formats member/watchers count without raw dashes.
     */
    fun formatMetricMembers(members: Int?): String {
        return if (members != null && members > 0) formatCompactNumber(members) else "0"
    }

    private fun getIndonesianMonth(month: Int): String {
        return when (month) {
            1 -> "Januari"
            2 -> "Februari"
            3 -> "Maret"
            4 -> "April"
            5 -> "Mei"
            6 -> "Juni"
            7 -> "Juli"
            8 -> "Agustus"
            9 -> "September"
            10 -> "Oktober"
            11 -> "November"
            12 -> "Desember"
            else -> ""
        }
    }

    private fun cleanRawSlug(text: String): String {
        return text
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }

    private fun formatNumber(number: Int): String {
        return NumberFormat.getIntegerInstance(Locale.GERMAN).format(number)
    }

    private fun formatCompactNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
            number >= 1_000 -> String.format(Locale.US, "%.1fK", number / 1_000.0)
            else -> number.toString()
        }
    }
}
