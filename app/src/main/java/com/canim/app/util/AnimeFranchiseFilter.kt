package com.canim.app.util

import com.canim.app.data.model.UserMediaItem

/**
 * Utility for detecting anime franchise roots and sequel relationships.
 * Prevents multiple sequels/seasons of the same franchise from monopolizing
 * the Top 5 Anime rankings in Stats and Exports (e.g., Attack on Titan Season 1, 2, 3).
 */
object AnimeFranchiseFilter {

    private val SEPARATORS = listOf(":", " - ", " – ", " — ")

    private val SEQUEL_PATTERNS = listOf(
        Regex("""(?i)\bthe\s+final\s+season\b"""),
        Regex("""(?i)\bfinal\s+season\b"""),
        Regex("""(?i)\bseason\s*\d+\b"""),
        Regex("""(?i)\b\d+(st|nd|rd|th)\s*season\b"""),
        Regex("""(?i)\bpart\s*\d+\b"""),
        Regex("""(?i)\b(ii|iii|iv|v|vi|vii|viii|ix|x)\b"""),
        Regex("""(?i)\b(movie|the\s+movie|ova|ona|special|specials)\b"""),
        Regex("""(?i)\b(zoku|kanketsu\w*|zenpen|kouhen|hen)\b"""),
        Regex("""\b\d+\b""") // trailing standalone numbers (e.g. "KonoSuba 2")
    )

    /**
     * Extracts the canonical base/root franchise name from an anime title.
     * E.g.:
     * - "Shingeki no Kyojin: The Final Season" -> "shingeki no kyojin"
     * - "Jujutsu Kaisen 2nd Season" -> "jujutsu kaisen"
     * - "Mob Psycho 100 II" -> "mob psycho"
     */
    fun getFranchiseRoot(title: String?): String {
        if (title.isNullOrBlank()) return ""
        var t = title.trim()

        // 1. Take prefix before subtitle separators if separator is present and prefix is meaningful
        for (sep in SEPARATORS) {
            if (t.contains(sep)) {
                val prefix = t.substringBefore(sep).trim()
                if (prefix.length >= 3) {
                    t = prefix
                    break
                }
            }
        }

        // 2. Remove punctuation
        t = t.replace(Regex("""[^\w\s]"""), " ")

        // 3. Remove common sequel/season indicators
        for (pattern in SEQUEL_PATTERNS) {
            t = t.replace(pattern, " ")
        }

        // 4. Normalize whitespace and lowercase
        return t.replace(Regex("""\s+"""), " ").trim().lowercase()
    }

    /**
     * Determines whether two anime titles belong to the same franchise or represent a sequel relationship.
     */
    fun isSequelOrSameFranchise(title1: String?, title2: String?): Boolean {
        if (title1.isNullOrBlank() || title2.isNullOrBlank()) return false
        val r1 = getFranchiseRoot(title1)
        val r2 = getFranchiseRoot(title2)
        if (r1.isEmpty() || r2.isEmpty()) return false

        // Exact franchise root match
        if (r1 == r2) return true

        // Substring / Prefix match if root is sufficiently descriptive (>= 4 chars)
        val minLen = minOf(r1.length, r2.length)
        if (minLen >= 4) {
            if (r1.startsWith(r2) || r2.startsWith(r1)) return true
            if (r1.contains(r2) || r2.contains(r1)) return true
        }

        // Word prefix match: share first two descriptive words (e.g., "attack on titan", "jujutsu kaisen", "mob psycho")
        val words1 = r1.split(" ").filter { it.isNotBlank() }
        val words2 = r2.split(" ").filter { it.isNotBlank() }
        if (words1.size >= 2 && words2.size >= 2) {
            if (words1[0] == words2[0] && words1[1] == words2[1]) {
                return true
            }
        }

        return false
    }

    /**
     * Filters a list of user anime items to select the Top [limit] items by score,
     * strictly excluding any title that is a sequel or belongs to the same franchise
     * as an already selected higher-ranked anime.
     */
    fun selectTopAnimeNonSequel(items: List<UserMediaItem>, limit: Int = 5): List<UserMediaItem> {
        val sorted = items.filter { it.score > 0 }.sortedByDescending { it.score }
        val result = mutableListOf<UserMediaItem>()

        for (candidate in sorted) {
            val isDuplicate = result.any { selected ->
                isSequelOrSameFranchise(candidate.title, selected.title) ||
                (!candidate.titleEnglish.isNullOrBlank() && !selected.titleEnglish.isNullOrBlank() &&
                 isSequelOrSameFranchise(candidate.titleEnglish, selected.titleEnglish))
            }

            if (!isDuplicate) {
                result.add(candidate)
                if (result.size >= limit) break
            }
        }

        return result
    }
}
