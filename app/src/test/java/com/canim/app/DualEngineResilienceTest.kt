package com.canim.app

import com.canim.app.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualEngineResilienceTest {

    @Test
    fun testMalRelationsMappingToExtendedMediaDetail() {
        val relatedAnime = listOf(
            MalRelatedEdge(
                node = MalAnimeNode(
                    id = 16498,
                    title = "Shingeki no Kyojin",
                    mainPicture = MalPicture(medium = "https://example.com/aot_m.jpg", large = "https://example.com/aot_l.jpg"),
                    numEpisodes = 25,
                    status = "finished_airing",
                    genres = listOf(MalGenre(1, "Action")),
                    synopsis = "Titan attack..."
                ),
                relationType = "prequel",
                relationTypeFormatted = "Prequel"
            )
        )

        val recommendations = listOf(
            MalRecommendationEdge(
                node = MalAnimeNode(
                    id = 5114,
                    title = "Fullmetal Alchemist: Brotherhood",
                    mainPicture = MalPicture(medium = "https://example.com/fma.jpg", large = "https://example.com/fma.jpg"),
                    numEpisodes = 64,
                    status = "finished_airing",
                    genres = listOf(MalGenre(2, "Adventure")),
                    synopsis = "Alchemy..."
                ),
                numRecommendations = 42
            )
        )

        val altTitles = MalAlternativeTitles(
            synonyms = listOf("AOT"),
            en = "Attack on Titan Season 2",
            ja = "進撃の巨人 Season 2"
        )

        val node = MalAnimeNode(
            id = 25777,
            title = "Shingeki no Kyojin Season 2",
            mainPicture = MalPicture(medium = "https://example.com/s2.jpg", large = "https://example.com/s2.jpg"),
            numEpisodes = 12,
            status = "finished_airing",
            genres = listOf(MalGenre(1, "Action")),
            synopsis = "Season 2 continues...",
            mean = 8.42,
            rank = 120,
            popularity = 30,
            numListUsers = 1500000,
            relatedAnime = relatedAnime,
            recommendations = recommendations,
            alternativeTitles = altTitles
        )

        val relations = node.relatedAnime?.map { rel ->
            MediaRelationItem(
                id = rel.node.id,
                malId = rel.node.id,
                title = rel.node.title,
                imageUrl = rel.node.mainPicture?.large ?: rel.node.mainPicture?.medium,
                relationType = (rel.relationTypeFormatted ?: rel.relationType ?: "RELATION").uppercase(),
                type = MediaType.ANIME
            )
        } ?: emptyList()

        val recs = node.recommendations?.map { rec ->
            MediaItem(
                malId = rec.node.id,
                anilistId = rec.node.id,
                title = rec.node.title,
                imageUrl = rec.node.mainPicture?.large ?: rec.node.mainPicture?.medium ?: "",
                type = MediaType.ANIME
            )
        } ?: emptyList()

        val ext = ExtendedMediaDetail(
            malId = node.id,
            title = node.title,
            titleEnglish = node.alternativeTitles?.en,
            nativeTitle = node.alternativeTitles?.ja,
            coverImage = node.mainPicture?.large ?: node.mainPicture?.medium,
            synopsis = node.synopsis,
            malScore = node.mean,
            relations = relations,
            recommendations = recs,
            isFromFallback = true
        )

        assertEquals("Attack on Titan Season 2", ext.titleEnglish)
        assertEquals("進撃の巨人 Season 2", ext.nativeTitle)
        assertEquals(1, ext.relations.size)
        assertEquals("Shingeki no Kyojin", ext.relations[0].title)
        assertEquals("PREQUEL", ext.relations[0].relationType)
        assertEquals(1, ext.recommendations.size)
        assertEquals("Fullmetal Alchemist: Brotherhood", ext.recommendations[0].title)
        assertTrue(ext.isFromFallback)
    }

    @Test
    fun testStatusStringMappingResilience() {
        fun mapStatus(status: String?): String {
            return when (status?.lowercase()) {
                "currently_airing" -> "AIRING"
                "finished_airing" -> "AIRED"
                "not_yet_aired" -> "NOT YET AIRED"
                else -> status?.uppercase() ?: "AIRED"
            }
        }

        assertEquals("AIRING", mapStatus("currently_airing"))
        assertEquals("AIRED", mapStatus("finished_airing"))
        assertEquals("NOT YET AIRED", mapStatus("not_yet_aired"))
        assertEquals("AIRED", mapStatus(null))
    }

    @Test
    fun testVersionBumpV632() {
        assertEquals("v6.3.2", BuildConfig.VERSION_NAME)
        assertEquals(40, BuildConfig.VERSION_CODE)
    }

    @Test
    fun testSemverComparison() {
        assertEquals(1, com.canim.app.data.remote.UpdateChecker.compareSemver("v6.1.1", "v6.1.2"))
        assertEquals(-1, com.canim.app.data.remote.UpdateChecker.compareSemver("v6.1.2", "v6.1.1"))
        assertEquals(0, com.canim.app.data.remote.UpdateChecker.compareSemver("v6.1.2", "v6.1.2"))
    }
}
