package com.canim.app.data.remote

import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.anilist.AniListApolloMapper
import com.canim.app.data.remote.anilist.graphql.GetCharacterProfileQuery
import com.canim.app.data.remote.anilist.graphql.GetDiscoverMediaQuery
import com.canim.app.data.remote.anilist.graphql.GetMediaBatchByMalIdsQuery
import com.canim.app.data.remote.anilist.graphql.GetStaffProfileQuery
import com.canim.app.data.remote.anilist.graphql.GetStudioFilmographyQuery
import com.canim.app.data.remote.anilist.graphql.SearchMediaQuery
import com.canim.app.data.remote.anilist.graphql.fragment.ExtendedMediaDetailFields
import com.canim.app.data.remote.anilist.graphql.type.CharacterRole
import com.canim.app.data.remote.anilist.graphql.type.MediaFormat
import com.canim.app.data.remote.anilist.graphql.type.MediaSeason
import com.canim.app.data.remote.anilist.graphql.type.MediaStatus
import com.canim.app.data.remote.anilist.graphql.type.MediaType as ApolloMediaType
import org.junit.Assert.*
import org.junit.Test

class AniListApolloMapperTest {

    @Test
    fun testFormatFuzzyDate() {
        assertNull(AniListApolloMapper.formatFuzzyDate(null, 1, 1))
        assertEquals("2023", AniListApolloMapper.formatFuzzyDate(2023, null, null))
        assertEquals("Okt 2023", AniListApolloMapper.formatFuzzyDate(2023, 10, null))
        assertEquals("12 Okt 2023", AniListApolloMapper.formatFuzzyDate(2023, 10, 12))
        assertEquals("2023", AniListApolloMapper.formatFuzzyDate(2023, 13, 12)) // month out of range
        assertEquals("1 Jan 2024", AniListApolloMapper.formatFuzzyDate(2024, 1, 1))
        assertEquals("28 Des 2025", AniListApolloMapper.formatFuzzyDate(2025, 12, 28))
    }

    @Test
    fun testToMediaItemFromSearchQuery() {
        val medium = SearchMediaQuery.Medium(
            id = 154587,
            idMal = 52991,
            title = SearchMediaQuery.Title(romaji = "Sousou no Frieren", english = "Frieren"),
            coverImage = SearchMediaQuery.CoverImage(large = "https://cover.jpg", extraLarge = "https://cover_hd.jpg", medium = "https://cover_m.jpg"),
            averageScore = 93,
            description = "A story about &quot;Frieren&quot; &amp; her party. <br><b>Enjoy!</b>",
            status = MediaStatus.FINISHED,
            seasonYear = 2023,
            season = MediaSeason.FALL,
            format = MediaFormat.TV,
            episodes = 28,
            chapters = null,
            volumes = null,
            genres = listOf("Adventure", "Drama", "Fantasy"),
            studios = SearchMediaQuery.Studios(listOf(SearchMediaQuery.Node("Madhouse")))
        )

        val item = AniListApolloMapper.toMediaItem(medium, MediaType.ANIME)

        assertEquals(154587, item.anilistId)
        assertEquals(52991, item.malId)
        assertEquals("Sousou no Frieren", item.title)
        assertEquals("Frieren", item.titleEnglish)
        assertEquals(9.3, item.score!!, 0.01)
        assertEquals("A story about \"Frieren\" & her party. Enjoy!", item.synopsis)
        assertEquals("AIRED", item.status)
        assertEquals("Madhouse", item.studio)
        assertEquals(28, item.episodes)
        assertEquals(MediaType.ANIME, item.type)
        assertEquals(listOf("Adventure", "Drama", "Fantasy"), item.genres)
    }

    @Test
    fun testToMediaItemStatusMappingForManga() {
        val releasingMedium = SearchMediaQuery.Medium(
            id = 30013,
            idMal = 13,
            title = SearchMediaQuery.Title(romaji = "One Piece", english = null),
            coverImage = null,
            averageScore = 0,
            description = null,
            status = MediaStatus.RELEASING,
            seasonYear = 1997,
            season = null,
            format = MediaFormat.MANGA,
            episodes = null,
            chapters = 1100,
            volumes = 108,
            genres = null,
            studios = null
        )

        val item = AniListApolloMapper.toMediaItem(releasingMedium, MediaType.MANGA)
        assertEquals("PUBLISHING", item.status)
        assertNull(item.score)
        assertEquals(MediaType.MANGA, item.type)
    }

    @Test
    fun testToExtendedMediaDetailNullSafety() {
        val fields = ExtendedMediaDetailFields(
            id = 1,
            idMal = null,
            title = null,
            duration = null,
            source = null,
            status = null,
            genres = null,
            averageScore = null,
            popularity = null,
            format = null,
            episodes = null,
            chapters = null,
            volumes = null,
            rankings = null,
            recommendations = null,
            startDate = null,
            endDate = null,
            studios = null,
            characters = null,
            staff = null,
            relations = null
        )

        val detail = AniListApolloMapper.toExtendedMediaDetail(fields, fallbackMalId = 999)
        assertEquals(1, detail.anilistId)
        assertEquals(999, detail.malId)
        assertEquals("", detail.title)
        assertNull(detail.format)
        assertNull(detail.episodes)
        assertTrue(detail.cast.isEmpty())
        assertTrue(detail.crew.isEmpty())
        assertTrue(detail.relations.isEmpty())
        assertTrue(detail.recommendations.isEmpty())
        assertNull(detail.averageScore)
    }

    @Test
    fun testToExtendedMediaDetailMovieFormat() {
        val fields = ExtendedMediaDetailFields(
            id = 100,
            idMal = 500,
            title = null,
            duration = 120,
            source = null,
            status = null,
            genres = null,
            averageScore = null,
            popularity = null,
            format = com.canim.app.data.remote.anilist.graphql.type.MediaFormat.MOVIE,
            episodes = 1,
            chapters = null,
            volumes = null,
            rankings = null,
            recommendations = null,
            startDate = null,
            endDate = null,
            studios = null,
            characters = null,
            staff = null,
            relations = null
        )

        val detail = AniListApolloMapper.toExtendedMediaDetail(fields)
        assertEquals("MOVIE", detail.format)
        assertEquals(1, detail.episodes)
        assertEquals(120, detail.durationMinutes)
    }

    @Test
    fun testToCharacterProfile() {
        val char = GetCharacterProfileQuery.Character(
            id = 40882,
            name = GetCharacterProfileQuery.Name(
                full = "Frieren",
                native = "フリーレン",
                first = "Frieren",
                last = null
            ),
            image = GetCharacterProfileQuery.Image(large = "https://frieren.jpg", medium = null),
            description = "An ancient elven mage who was a member of the Hero's party.",
            media = GetCharacterProfileQuery.Media(
                listOf(
                    GetCharacterProfileQuery.Edge(
                        characterRole = CharacterRole.MAIN,
                        node = GetCharacterProfileQuery.Node(
                            id = 154587,
                            idMal = 52991,
                            title = GetCharacterProfileQuery.Title(romaji = "Sousou no Frieren", english = "Frieren"),
                            coverImage = GetCharacterProfileQuery.CoverImage(large = "https://cover.jpg", medium = null),
                            startDate = GetCharacterProfileQuery.StartDate(year = 2023),
                            format = MediaFormat.TV,
                            type = ApolloMediaType.ANIME
                        )
                    )
                )
            )
        )

        val profile = AniListApolloMapper.toCharacterProfile(40882, char)
        assertEquals(40882, profile.id)
        assertFalse(profile.isStaff)
        assertEquals("Frieren", profile.name)
        assertEquals("フリーレン", profile.nativeName)
        assertEquals("https://frieren.jpg", profile.image)
        assertEquals(1, profile.filmography.size)
        assertEquals("Sousou no Frieren", profile.filmography[0].title)
        assertEquals("MAIN", profile.filmography[0].role)
    }

    @Test
    fun testToStaffProfileWithDeduplicatedFilmography() {
        val staff = GetStaffProfileQuery.Staff(
            id = 10688,
            name = GetStaffProfileQuery.Name(
                full = "Atsumi Tanezaki",
                native = "種﨑敦美",
                first = "Atsumi",
                last = "Tanezaki"
            ),
            image = GetStaffProfileQuery.Image(large = "https://atsumi.jpg", medium = null),
            description = "Japanese voice actress.",
            characters = GetStaffProfileQuery.Characters(
                listOf(
                    GetStaffProfileQuery.Edge(
                        node = GetStaffProfileQuery.Node(
                            name = GetStaffProfileQuery.Name1(full = "Frieren"),
                            image = GetStaffProfileQuery.Image1(large = "https://frieren.jpg", medium = null)
                        ),
                        role = CharacterRole.MAIN,
                        media = listOf(
                            GetStaffProfileQuery.Medium(
                                id = 154587,
                                idMal = 52991,
                                title = GetStaffProfileQuery.Title(romaji = "Sousou no Frieren", english = null),
                                coverImage = GetStaffProfileQuery.CoverImage(large = "https://cover.jpg", medium = null),
                                startDate = GetStaffProfileQuery.StartDate(year = 2023),
                                format = MediaFormat.TV,
                                type = ApolloMediaType.ANIME
                            )
                        )
                    )
                )
            ),
            staffMedia = GetStaffProfileQuery.StaffMedia(
                listOf(
                    GetStaffProfileQuery.Edge1(
                        node = GetStaffProfileQuery.Node1(
                            id = 154587, // duplicate ID
                            idMal = 52991,
                            title = GetStaffProfileQuery.Title1(romaji = "Sousou no Frieren", english = null),
                            coverImage = GetStaffProfileQuery.CoverImage1(large = "https://cover.jpg", medium = null),
                            startDate = GetStaffProfileQuery.StartDate1(year = 2023),
                            format = MediaFormat.TV,
                            type = ApolloMediaType.ANIME
                        ),
                        staffRole = "Theme Song Performance"
                    )
                )
            )
        )

        val profile = AniListApolloMapper.toStaffProfile(10688, staff)
        assertEquals(10688, profile.id)
        assertTrue(profile.isStaff)
        assertEquals("Atsumi Tanezaki", profile.name)
        // Verify distinctBy ensures filmography is not duplicated
        assertEquals(1, profile.filmography.size)
    }

    @Test
    fun testToStudioFilmographyPageDeduplicatesItems() {
        val studio = GetStudioFilmographyQuery.Studio(
            id = 569,
            name = "MAPPA",
            isAnimationStudio = true,
            siteUrl = "http://www.mappa.co.jp/",
            favourites = 12000,
            media = GetStudioFilmographyQuery.Media(
                pageInfo = GetStudioFilmographyQuery.PageInfo(
                    hasNextPage = false,
                    currentPage = 1,
                    lastPage = 1,
                    total = 2
                ),
                nodes = listOf(
                    GetStudioFilmographyQuery.Node(
                        id = 113415,
                        idMal = 40748,
                        title = GetStudioFilmographyQuery.Title(romaji = "Jujutsu Kaisen", english = "Jujutsu Kaisen"),
                        coverImage = GetStudioFilmographyQuery.CoverImage(large = "https://jjk.jpg", medium = null, extraLarge = null),
                        format = MediaFormat.TV,
                        type = ApolloMediaType.ANIME,
                        status = MediaStatus.FINISHED,
                        episodes = 24,
                        chapters = null,
                        averageScore = 86,
                        popularity = 300000,
                        genres = listOf("Action", "Fantasy"),
                        startDate = GetStudioFilmographyQuery.StartDate(year = 2020)
                    ),
                    GetStudioFilmographyQuery.Node(
                        id = 113415, // duplicate node
                        idMal = 40748,
                        title = GetStudioFilmographyQuery.Title(romaji = "Jujutsu Kaisen", english = "Jujutsu Kaisen"),
                        coverImage = null,
                        format = MediaFormat.TV,
                        type = ApolloMediaType.ANIME,
                        status = MediaStatus.FINISHED,
                        episodes = 24,
                        chapters = null,
                        averageScore = 86,
                        popularity = 300000,
                        genres = null,
                        startDate = null
                    )
                )
            )
        )

        val page = AniListApolloMapper.toStudioFilmographyPage(studio, page = 1)
        assertEquals(569, page.studioId)
        assertEquals("MAPPA", page.studioName)
        assertEquals(1, page.items.size) // deduplicated
        assertEquals("Jujutsu Kaisen", page.items[0].title)
        assertEquals(8.6, page.items[0].score!!, 0.01)
    }

    @Test
    fun testToDiscoverMediaItem() {
        val medium = GetDiscoverMediaQuery.Medium(
            id = 21,
            idMal = 21,
            title = GetDiscoverMediaQuery.Title(romaji = "One Piece", english = null),
            coverImage = GetDiscoverMediaQuery.CoverImage(large = "https://op.jpg", extraLarge = null, medium = null),
            averageScore = 88,
            description = "Pirate adventure",
            status = MediaStatus.RELEASING,
            seasonYear = 1999,
            season = MediaSeason.FALL,
            format = MediaFormat.TV,
            episodes = 1000,
            chapters = null,
            volumes = null,
            genres = listOf("Action", "Adventure"),
            studios = GetDiscoverMediaQuery.Studios(listOf(GetDiscoverMediaQuery.Node("Toei Animation")))
        )

        val item = AniListApolloMapper.toDiscoverMediaItem(medium, MediaType.ANIME)
        assertEquals(21, item.anilistId)
        assertEquals(21, item.malId)
        assertEquals("AIRING", item.status)
        assertEquals("Toei Animation", item.studio)
        assertEquals(8.8, item.score!!, 0.01)
    }

    @Test
    fun testToBatchMediaItem() {
        val medium = GetMediaBatchByMalIdsQuery.Medium(
            id = 5114,
            idMal = 5114,
            title = GetMediaBatchByMalIdsQuery.Title(romaji = "Fullmetal Alchemist: Brotherhood", english = "FMAB"),
            coverImage = GetMediaBatchByMalIdsQuery.CoverImage(large = "https://fmab.jpg", extraLarge = null, medium = null),
            averageScore = 91,
            description = "Two brothers...",
            status = MediaStatus.FINISHED,
            seasonYear = 2009,
            season = MediaSeason.SPRING,
            format = MediaFormat.TV,
            episodes = 64,
            chapters = null,
            volumes = null,
            genres = listOf("Action", "Adventure"),
            studios = GetMediaBatchByMalIdsQuery.Studios(listOf(GetMediaBatchByMalIdsQuery.Node("Bones")))
        )

        val item = AniListApolloMapper.toBatchMediaItem(medium, MediaType.ANIME)
        assertEquals(5114, item.anilistId)
        assertEquals(5114, item.malId)
        assertEquals("FMAB", item.titleEnglish)
        assertEquals("AIRED", item.status)
        assertEquals(9.1, item.score!!, 0.01)
    }
}
