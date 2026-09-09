package com.canim.app.data.repository

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.MalAnimeNode
import com.canim.app.data.model.MalMangaNode
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaType

internal object MediaMappingUtils {

    fun mapMalAnimeNodeToMediaItem(node: MalAnimeNode): MediaItem {
        return MediaItem(
            malId = node.id,
            anilistId = CacheManager.getAniListIdForMalId(node.id),
            title = node.title,
            titleEnglish = node.alternativeTitles?.en ?: node.title,
            imageUrl = node.mainPicture?.large ?: node.mainPicture?.medium ?: "",
            type = MediaType.ANIME,
            score = node.mean,
            synopsis = node.synopsis ?: "",
            episodes = node.numEpisodes,
            chapters = null,
            volumes = null,
            status = when (node.status?.lowercase()) {
                "currently_airing" -> "AIRING"
                "finished_airing" -> "AIRED"
                "not_yet_aired" -> "NOT YET AIRED"
                else -> node.status?.uppercase() ?: "AIRED"
            },
            year = node.startDate?.take(4)?.toIntOrNull(),
            season = null,
            genres = node.genres?.map { it.name } ?: emptyList(),
            format = "TV",
            studio = node.studios?.firstOrNull()?.name
        )
    }

    fun mapMalMangaNodeToMediaItem(node: MalMangaNode): MediaItem {
        return MediaItem(
            malId = node.id,
            anilistId = CacheManager.getAniListIdForMalId(node.id),
            title = node.title,
            titleEnglish = node.alternativeTitles?.en ?: node.title,
            imageUrl = node.mainPicture?.large ?: node.mainPicture?.medium ?: "",
            type = MediaType.MANGA,
            score = node.mean,
            synopsis = node.synopsis ?: "",
            episodes = null,
            chapters = node.numChapters,
            volumes = node.numVolumes,
            status = when (node.status?.lowercase()) {
                "currently_publishing" -> "PUBLISHING"
                "finished" -> "FINISHED"
                "on_hiatus" -> "ON HIATUS"
                "discontinued" -> "CANCELLED"
                else -> node.status?.uppercase() ?: "FINISHED"
            },
            year = node.startDate?.take(4)?.toIntOrNull(),
            season = null,
            genres = node.genres?.map { it.name } ?: emptyList(),
            format = "MANGA",
            studio = node.authors?.firstOrNull()?.name
        )
    }

    fun fallbackAnime(): List<MediaItem> = listOf(
        MediaItem(52991, 154587, "Sousou no Frieren", "Frieren: Beyond Journey's End", "https://cdn.myanimelist.net/images/anime/1015/138075l.jpg", MediaType.ANIME, 9.35, "During their decade-long quest to defeat the Demon King...", 28, null, null, "Finished Airing", 2023, "Fall", listOf("Adventure", "Fantasy"), "TV", "Madhouse"),
        MediaItem(16498, 16498, "Shingeki no Kyojin", "Attack on Titan", "https://cdn.myanimelist.net/images/anime/10/47347l.jpg", MediaType.ANIME, 8.55, "Centuries ago, mankind was slaughtered...", 25, null, null, "Finished Airing", 2013, "Spring", listOf("Action", "Drama"), "TV", "Wit Studio"),
        MediaItem(5114, 5114, "Fullmetal Alchemist: Brotherhood", "Fullmetal Alchemist: Brotherhood", "https://cdn.myanimelist.net/images/anime/1223/96541l.jpg", MediaType.ANIME, 9.10, "After a horrific alchemy experiment goes wrong...", 64, null, null, "Finished Airing", 2009, "Spring", listOf("Action", "Adventure"), "TV", "Bones"),
        MediaItem(40748, 113415, "Jujutsu Kaisen", "Jujutsu Kaisen", "https://cdn.myanimelist.net/images/anime/1171/109222l.jpg", MediaType.ANIME, 8.61, "Idly indulging in paranormal activities with the Occult Club...", 24, null, null, "Finished Airing", 2020, "Fall", listOf("Action", "Fantasy"), "TV", "MAPPA"),
        MediaItem(38000, 101922, "Kimetsu no Yaiba", "Demon Slayer", "https://cdn.myanimelist.net/images/anime/1286/99889l.jpg", MediaType.ANIME, 8.48, "Ever since the death of his father...", 26, null, null, "Finished Airing", 2019, "Spring", listOf("Action", "Fantasy"), "TV", "ufotable")
    )

    fun fallbackManga(): List<MediaItem> = listOf(
        MediaItem(2, 30002, "Berserk", "Berserk", "https://cdn.myanimelist.net/images/manga/1/157897l.jpg", MediaType.MANGA, 9.47, "Guts, a former mercenary now known as the 'Black Swordsman'...", null, null, null, "Publishing", null, null, listOf("Action", "Dark Fantasy"), "MANGA", null),
        MediaItem(13, 30013, "One Piece", "One Piece", "https://cdn.myanimelist.net/images/manga/2/253146l.jpg", MediaType.MANGA, 9.22, "Gol D. Roger was known as the 'Pirate King'...", null, null, null, "Publishing", null, null, listOf("Action", "Adventure"), "MANGA", null),
        MediaItem(656, 30656, "Vagabond", "Vagabond", "https://cdn.myanimelist.net/images/manga/1/259070l.jpg", MediaType.MANGA, 9.25, "Growing up in 16th century Sengoku era Japan...", null, 327, 37, "On Hiatus", null, null, listOf("Action", "Historical"), "MANGA", null),
        MediaItem(121496, 105398, "Solo Leveling", "Solo Leveling", "https://cdn.myanimelist.net/images/manga/3/222295l.jpg", MediaType.MANGA, 8.68, "Ten years ago, 'the Gate' appeared...", null, 179, null, "Finished", null, null, listOf("Action", "Fantasy"), "MANGA", null)
    )
}
