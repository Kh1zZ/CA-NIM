package com.canim.app.domain.gacha

import com.canim.app.data.model.UserMediaItem

/**
 * Derives dynamic user genre tendencies from the user's library without any hardcoded tables.
 *
 * Signals used:
 * - Genre frequency across tracked entries
 * - Status multipliers (completed and watching receive higher weighting than dropped or plan_to_watch)
 * - User rating scores (higher ratings increase the genre weight significantly)
 *
 * The model adapts dynamically as the library changes.
 */
object AdaptivePreferenceModel {

    data class GenreTendency(
        val genre: String,
        val weight: Double,
        val normalizedProbability: Double
    )

    /**
     * Derives genre affinities from the given list of user anime items.
     * Returns a map of genre names to their normalized affinity scores (0.0 .. 1.0).
     */
    fun deriveTendencies(items: List<UserMediaItem>): Map<String, Double> {
        if (items.isEmpty()) return emptyMap()

        val rawWeights = mutableMapOf<String, Double>()

        for (item in items) {
            val genres = item.metadata.genres
            if (genres.isEmpty()) continue

            // Status weight
            val statusMultiplier = when (item.tracking.status.lowercase()) {
                "completed" -> 1.5
                "watching" -> 1.3
                "on_hold", "paused" -> 0.8
                "plan_to_watch" -> 0.5
                "dropped" -> 0.2
                else -> 1.0
            }

            // Score weight: scores > 0 provide additional boost
            val userScore = item.tracking.score
            val scoreMultiplier = when {
                userScore >= 9 -> 2.0
                userScore >= 7 -> 1.5
                userScore >= 5 -> 1.0
                userScore > 0 -> 0.5
                else -> 1.0 // Unrated defaults to baseline
            }

            val itemTotalWeight = statusMultiplier * scoreMultiplier

            for (genre in genres) {
                val normalizedGenre = genre.trim()
                if (normalizedGenre.isNotBlank()) {
                    rawWeights[normalizedGenre] = (rawWeights[normalizedGenre] ?: 0.0) + itemTotalWeight
                }
            }
        }

        val totalSum = rawWeights.values.sum()
        if (totalSum <= 0.0) return emptyMap()

        // Normalize weights so the maximum affinity is 1.0
        val maxWeight = rawWeights.values.maxOrNull() ?: 1.0
        return rawWeights.mapValues { (_, weight) -> weight / maxWeight }
    }

    /**
     * Extracts top preferred genres from the tendencies map, ordered by affinity weight descending.
     */
    fun getTopGenres(tendencies: Map<String, Double>, limit: Int = 3): List<String> {
        if (tendencies.isEmpty()) return emptyList()
        return tendencies.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    /**
     * Scores a candidate anime against the user's genre tendencies.
     * Higher score = stronger match with user preferences.
     * Incorporates breadth bonus for candidates matching multiple preferred genres.
     */
    fun calculateAffinityScore(candidateGenres: List<String>, tendencies: Map<String, Double>): Double {
        if (candidateGenres.isEmpty() || tendencies.isEmpty()) return 0.1
        var scoreSum = 0.0
        var matchCount = 0

        for (genre in candidateGenres) {
            val tendency = tendencies[genre.trim()]
            if (tendency != null) {
                scoreSum += tendency
                matchCount++
            }
        }

        return if (matchCount > 0) {
            val avgScore = scoreSum / matchCount
            val breadthBonus = (matchCount - 1) * 0.15
            (avgScore + breadthBonus).coerceAtMost(2.0)
        } else {
            0.05 // Baseline for genres not yet in library to allow gentle discovery
        }
    }
}
