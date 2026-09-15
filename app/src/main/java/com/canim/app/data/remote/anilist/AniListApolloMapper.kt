package com.canim.app.data.remote.anilist

import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.model.CastCrewProfile
import com.canim.app.data.model.CharacterCastItem
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.FilmographyItem
import com.canim.app.data.model.MediaItem
import com.canim.app.data.model.MediaRelationItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.StaffMemberItem
import com.canim.app.data.remote.anilist.graphql.GetCharacterProfileQuery
import com.canim.app.data.remote.anilist.graphql.GetDiscoverMediaQuery
import com.canim.app.data.remote.anilist.graphql.GetMediaBatchByMalIdsQuery
import com.canim.app.data.remote.anilist.graphql.GetStaffProfileQuery
import com.canim.app.data.remote.anilist.graphql.GetStudioFilmographyQuery
import com.canim.app.data.remote.anilist.graphql.SearchMediaQuery
import com.canim.app.data.remote.anilist.graphql.fragment.ExtendedMediaDetailFields
import com.canim.app.data.remote.anilist.graphql.type.MediaStatus
import com.canim.app.data.remote.anilist.graphql.type.MediaType as ApolloMediaType
import com.canim.app.util.TextSanitizer

/**
 * Pure data mapper transforming Apollo GraphQL generated response models into
 * CA'NIM domain models.
 *
 * Adheres strictly to Clean Architecture:
 * - Pure transformations with zero side effects.
 * - Free of caching, networking, or database side effects.
 * - Completely testable in isolation without mocking transport clients.
 */
object AniListApolloMapper {

    fun formatFuzzyDate(year: Int?, month: Int?, day: Int?): String? {
        if (year == null) return null
        val monthNames = listOf("", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agt", "Sep", "Okt", "Nov", "Des")
        val mStr = if (month != null && month in 1..12) monthNames[month] else null
        return if (day != null && mStr != null) {
            "$day $mStr $year"
        } else if (mStr != null) {
            "$mStr $year"
        } else {
            "$year"
        }
    }

    fun toMediaItem(
        m: SearchMediaQuery.Medium,
        fallbackType: MediaType
    ): MediaItem {
        val primaryTitle = m.title?.romaji ?: m.title?.english ?: "Unknown Title"
        val englishTitle = m.title?.english
        val img = m.coverImage?.large ?: m.coverImage?.extraLarge ?: m.coverImage?.medium ?: ""
        val imgHd = m.coverImage?.extraLarge ?: m.coverImage?.large ?: m.coverImage?.medium
        val score = if (m.averageScore != null && m.averageScore > 0) m.averageScore / 10.0 else null
        val cleanDescription = m.description
            ?.replace(Regex("<[^>]*>"), "")
            ?.replace("&quot;", "\"")
            ?.replace("&#039;", "'")
            ?.replace("&amp;", "&")

        val statusStr = when (m.status) {
            MediaStatus.RELEASING -> if (fallbackType == MediaType.ANIME) "AIRING" else "PUBLISHING"
            MediaStatus.FINISHED -> if (fallbackType == MediaType.ANIME) "AIRED" else "FINISHED"
            MediaStatus.NOT_YET_RELEASED -> "NOT YET AIRED"
            MediaStatus.CANCELLED -> "CANCELLED"
            else -> m.status?.rawValue ?: "AIRED"
        }

        val studioName = m.studios?.nodes?.firstOrNull()?.name

        return MediaItem(
            malId = m.idMal,
            anilistId = m.id,
            title = primaryTitle,
            titleEnglish = englishTitle,
            imageUrl = img,
            type = fallbackType,
            score = score,
            synopsis = cleanDescription,
            episodes = m.episodes,
            chapters = m.chapters,
            volumes = m.volumes,
            status = statusStr,
            year = m.seasonYear,
            season = m.season?.rawValue,
            genres = m.genres?.filterNotNull() ?: emptyList(),
            format = m.format?.rawValue,
            studio = studioName,
            imageUrlHd = imgHd
        )
    }

    fun toExtendedMediaDetail(
        fields: ExtendedMediaDetailFields,
        fallbackMalId: Int? = null
    ): ExtendedMediaDetail {
        val castList = fields.characters?.edges?.mapNotNull { edge ->
            val charNode = edge?.node ?: return@mapNotNull null
            val va = edge.voiceActors?.firstOrNull()
            CharacterCastItem(
                characterId = charNode.id,
                characterName = charNode.name?.full ?: "Karakter",
                characterImage = charNode.image?.large ?: charNode.image?.medium,
                actorId = va?.id,
                actorName = va?.name?.full,
                actorImage = va?.image?.large ?: va?.image?.medium,
                role = edge.role?.rawValue ?: "Supporting"
            )
        } ?: emptyList()

        val staffList = fields.staff?.edges?.mapNotNull { edge ->
            val staffNode = edge?.node ?: return@mapNotNull null
            StaffMemberItem(
                staffId = staffNode.id,
                name = staffNode.name?.full ?: "Staff",
                role = edge.role ?: "Crew",
                image = staffNode.image?.large ?: staffNode.image?.medium
            )
        } ?: emptyList()

        val studioNode = fields.studios?.nodes?.firstOrNull()
        val studioName = studioNode?.name
        val studioId = studioNode?.id

        val recList = fields.recommendations?.nodes?.mapNotNull { node ->
            val rec = node?.mediaRecommendation ?: return@mapNotNull null
            val recType = if (rec.type == ApolloMediaType.MANGA || rec.format?.rawValue == "MANGA") MediaType.MANGA else MediaType.ANIME
            MediaItem(
                malId = rec.idMal,
                anilistId = rec.id,
                title = rec.title?.romaji ?: rec.title?.english ?: "Unknown Title",
                titleEnglish = rec.title?.english,
                imageUrl = rec.coverImage?.large ?: rec.coverImage?.medium ?: "",
                score = if (rec.averageScore != null && rec.averageScore > 0) rec.averageScore / 10.0 else null,
                type = recType,
                synopsis = null,
                episodes = null,
                chapters = null,
                volumes = null,
                status = null,
                year = null,
                season = null,
                genres = emptyList(),
                format = rec.format?.rawValue
            )
        } ?: emptyList()

        val relationsList = fields.relations?.edges?.mapNotNull { edge ->
            val node = edge?.node ?: return@mapNotNull null
            val relType = edge.relationType?.rawValue ?: "RELATED"
            MediaRelationItem(
                id = node.id,
                malId = node.idMal,
                title = node.title?.romaji ?: node.title?.english ?: "Unknown",
                titleEnglish = node.title?.english,
                imageUrl = node.coverImage?.large ?: node.coverImage?.medium,
                relationType = relType,
                type = if (node.type == ApolloMediaType.MANGA) MediaType.MANGA else MediaType.ANIME,
                format = node.format?.rawValue,
                status = node.status?.rawValue
            )
        } ?: emptyList()

        val avgScore = if (fields.averageScore != null && fields.averageScore > 0) fields.averageScore / 10.0 else null
        val rankValue = fields.rankings?.firstOrNull { it?.allTime == true }?.rank ?: fields.rankings?.firstOrNull()?.rank

        return ExtendedMediaDetail(
            anilistId = fields.id,
            malId = fields.idMal ?: fallbackMalId,
            title = fields.title?.romaji ?: fields.title?.english ?: "",
            titleEnglish = fields.title?.english,
            nativeTitle = fields.title?.native,
            studio = studioName,
            studioId = studioId,
            source = fields.source?.rawValue,
            airingStatus = fields.status?.rawValue,
            startDate = formatFuzzyDate(fields.startDate?.year, fields.startDate?.month, fields.startDate?.day),
            endDate = formatFuzzyDate(fields.endDate?.year, fields.endDate?.month, fields.endDate?.day),
            genres = fields.genres?.filterNotNull() ?: emptyList(),
            durationMinutes = fields.duration,
            format = fields.format?.rawValue,
            episodes = fields.episodes,
            chapters = fields.chapters,
            volumes = fields.volumes,
            cast = castList,
            crew = staffList,
            relations = relationsList,
            averageScore = avgScore,
            popularity = fields.popularity,
            rank = rankValue,
            watchers = fields.popularity,
            recommendations = recList,
            isFromFallback = false
        )
    }

    fun toCharacterProfile(id: Int, char: GetCharacterProfileQuery.Character): CastCrewProfile {
        val fullName = char.name?.full?.takeIf { it.isNotBlank() } ?: "Karakter"
        val nativeName = char.name?.native?.takeIf { it.isNotBlank() }
        val firstName = char.name?.first?.takeIf { it.isNotBlank() }
        val lastName = char.name?.last?.takeIf { it.isNotBlank() }

        val imageUrl = char.image?.large?.takeIf { it.isNotBlank() }
            ?: char.image?.medium?.takeIf { it.isNotBlank() }

        val cleanDesc = char.description?.let { TextSanitizer.sanitize(it) }?.takeIf { it.isNotBlank() }

        val filmography = char.media?.edges?.mapNotNull { edge ->
            val role = edge?.characterRole?.rawValue ?: "Character"
            val node = edge?.node ?: return@mapNotNull null
            val mId = node.id
            val malId = node.idMal
            val tRomaji = node.title?.romaji
            val tEng = node.title?.english
            val cImg = node.coverImage?.large ?: node.coverImage?.medium
            val sYear = node.startDate?.year
            val fmt = node.format?.rawValue
            val mType = if (node.type == ApolloMediaType.MANGA) MediaType.MANGA else MediaType.ANIME

            FilmographyItem(
                id = mId,
                malId = malId,
                title = tRomaji ?: tEng ?: "Judul",
                titleEnglish = tEng,
                imageUrl = cImg,
                year = sYear,
                format = fmt,
                type = mType,
                role = role,
                characterName = fullName,
                characterImage = imageUrl
            )
        } ?: emptyList()

        return CastCrewProfile(
            id = id,
            isStaff = false,
            name = fullName,
            nativeName = nativeName,
            firstName = firstName,
            lastName = lastName,
            image = imageUrl,
            biography = cleanDesc,
            nationality = null,
            birthday = null,
            age = null,
            gender = null,
            filmography = filmography
        )
    }

    fun toStaffProfile(id: Int, staff: GetStaffProfileQuery.Staff): CastCrewProfile {
        val name = staff.name
        val fullName = name?.full?.takeIf { it.isNotBlank() } ?: "Staff"
        val nativeName = name?.native?.takeIf { it.isNotBlank() }
        val firstName = name?.first?.takeIf { it.isNotBlank() }
        val lastName = name?.last?.takeIf { it.isNotBlank() }

        val imageUrl = staff.image?.large?.takeIf { it.isNotBlank() }
            ?: staff.image?.medium?.takeIf { it.isNotBlank() }

        val cleanDesc = staff.description
            ?.let { TextSanitizer.sanitize(it) }
            ?.takeIf { it.isNotBlank() }

        // Build filmography from character voice roles (characters connection)
        val filmography = mutableListOf<FilmographyItem>()
        val charEdges = staff.characters?.edges
        if (charEdges != null) {
            for (edge in charEdges) {
                val charNode = edge?.node ?: continue
                val charName = charNode.name?.full
                val charImg = charNode.image?.large?.takeIf { it.isNotBlank() }
                    ?: charNode.image?.medium?.takeIf { it.isNotBlank() }
                val roleStr = edge.role?.rawValue ?: "MAIN"

                val mediaList = edge.media ?: emptyList()
                for (m in mediaList) {
                    if (m == null) continue
                    val mId = m.id
                    val malId = m.idMal
                    val tRomaji = m.title?.romaji?.takeIf { it.isNotBlank() }
                    val tEng = m.title?.english?.takeIf { it.isNotBlank() }
                    val cImg = m.coverImage?.large?.takeIf { it.isNotBlank() }
                        ?: m.coverImage?.medium?.takeIf { it.isNotBlank() }
                    val sYear = m.startDate?.year?.takeIf { it > 0 }
                    val fmt = m.format?.rawValue
                    val mType = if (m.type?.rawValue == "MANGA") MediaType.MANGA else MediaType.ANIME

                    filmography.add(
                        FilmographyItem(
                            id = mId,
                            malId = malId,
                            title = tRomaji ?: tEng ?: "Judul",
                            titleEnglish = tEng,
                            imageUrl = cImg,
                            year = sYear,
                            format = fmt,
                            type = mType,
                            role = roleStr,
                            characterName = charName,
                            characterImage = charImg
                        )
                    )
                }
            }
        }

        // Build filmography from production staff roles (staffMedia connection)
        val staffEdges = staff.staffMedia?.edges
        if (staffEdges != null) {
            for (edge in staffEdges) {
                val node = edge?.node ?: continue
                val mId = node.id
                val malId = node.idMal
                val tRomaji = node.title?.romaji?.takeIf { it.isNotBlank() }
                val tEng = node.title?.english?.takeIf { it.isNotBlank() }
                val cImg = node.coverImage?.large?.takeIf { it.isNotBlank() }
                    ?: node.coverImage?.medium?.takeIf { it.isNotBlank() }
                val sYear = node.startDate?.year?.takeIf { it > 0 }
                val fmt = node.format?.rawValue
                val mType = if (node.type?.rawValue == "MANGA") MediaType.MANGA else MediaType.ANIME
                val role = edge.staffRole?.takeIf { it.isNotBlank() } ?: "Staff"

                filmography.add(
                    FilmographyItem(
                        id = mId,
                        malId = malId,
                        title = tRomaji ?: tEng ?: "Judul",
                        titleEnglish = tEng,
                        imageUrl = cImg,
                        year = sYear,
                        format = fmt,
                        type = mType,
                        role = role,
                        characterName = null,
                        characterImage = null
                    )
                )
            }
        }

        // Extract and translate distinct occupations/roles
        val rawOccupations = mutableSetOf<String>()
        if (charEdges?.any { it?.node != null } == true) {
            rawOccupations.add("Voice Actor")
        }
        staffEdges?.forEach { edge ->
            val role = edge?.staffRole?.trim()
            if (!role.isNullOrBlank()) {
                // Roles may be comma-separated e.g. "Director, Script"
                role.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { rawOccupations.add(it) }
            }
        }

        val translatedOccupations = rawOccupations.map { role ->
            translateOccupationToIndonesian(role)
        }.distinct()

        return CastCrewProfile(
            id = id,
            isStaff = true,
            name = fullName,
            nativeName = nativeName,
            firstName = firstName,
            lastName = lastName,
            image = imageUrl,
            biography = cleanDesc,
            nationality = null,
            birthday = null,
            age = null,
            gender = null,
            occupations = translatedOccupations,
            filmography = filmography.distinctBy { it.id }
        )
    }

    private fun sanitizeRoleText(role: String): String {
        // Remove episode references like (eps 1-12), (ep 4), (eps 1, 3, 5), (ED 1), (OP), etc.
        var cleaned = role
            .replace(Regex("""\s*\((?:eps?|episodes?|ed|op|\d+)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(.*?\d+.*?\)"""), "")
            .trim()
        // Strip trailing commas, dashes, colons or semicolons
        cleaned = cleaned.trimEnd(',', '-', ':', ';', ' ')
        return cleaned.ifBlank { role.trim() }
    }

    private fun translateOccupationToIndonesian(role: String): String {
        val sanitized = sanitizeRoleText(role)
        val trimmed = sanitized.trim()
        val lower = trimmed.lowercase()
        return when {
            lower == "voice actor" || lower == "voice actress" || lower == "seiyuu" -> "Pengisi Suara (Seiyuu)"
            lower == "original creator" || lower == "original story" || lower == "original author" || lower == "author" -> "Author"
            lower == "director" -> "Sutradara"
            lower == "episode director" -> "Sutradara Episode"
            lower == "series composition" -> "Komposisi Seri"
            lower == "character design" || lower == "original character design" -> "Desain Karakter"
            lower == "chief animation director" -> "Kepala Sutradara Animasi"
            lower == "animation director" -> "Sutradara Animasi"
            lower == "music" || lower == "composer" -> "Komposer / Musik"
            lower == "sound director" -> "Pengarah Suara"
            lower == "producer" -> "Produser"
            lower == "chief producer" -> "Kepala Produser"
            lower == "animation producer" -> "Produser Animasi"
            lower == "art director" -> "Penata Artistik"
            lower == "color design" -> "Penata Warna"
            lower == "editing" || lower == "editor" -> "Penyunting (Editor)"
            lower == "planning" -> "Perencana"
            lower == "script" || lower == "screenplay" -> "Penulis Naskah"
            lower == "theme song performance" -> "Penyanyi Lagu Tema"
            lower == "insert song performance" -> "Penyanyi Lagu Sisipan"
            lower == "sound effects" -> "Efek Suara"
            lower == "director of photography" -> "Pengarah Sinematografi"
            lower == "storyboard" -> "Papan Cerita (Storyboard)"
            lower == "key animation" -> "Animator Utama"
            lower == "assistant director" -> "Asisten Sutradara"
            else -> trimmed
        }
    }

    fun toStudioFilmographyItem(
        node: GetStudioFilmographyQuery.Node,
        studioName: String
    ): MediaItem? {
        val mId = node.id.takeIf { it > 0 } ?: return null
        val malId = node.idMal
        val tRomaji = node.title?.romaji?.takeIf { it.isNotBlank() }
        val tEng = node.title?.english?.takeIf { it.isNotBlank() }
        val cover = node.coverImage?.large?.takeIf { it.isNotBlank() }
            ?: node.coverImage?.medium?.takeIf { it.isNotBlank() }
            ?: node.coverImage?.extraLarge?.takeIf { it.isNotBlank() } ?: ""
        val coverHd = node.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
            ?: node.coverImage?.large?.takeIf { it.isNotBlank() }
        val fmt = node.format?.rawValue
        val mType = if (node.type?.rawValue == "MANGA") MediaType.MANGA else MediaType.ANIME
        val status = node.status?.rawValue
        val episodes = node.episodes?.takeIf { it > 0 }
        val chapters = node.chapters?.takeIf { it > 0 }
        val avgScore = node.averageScore ?: 0
        val score = if (avgScore > 0) avgScore / 10.0 else null
        val popularity = node.popularity?.takeIf { it > 0 }
        val genresList = node.genres?.filterNotNull() ?: emptyList()
        val year = node.startDate?.year?.takeIf { it > 0 }

        return MediaItem(
            malId = malId,
            anilistId = mId,
            title = tRomaji ?: tEng ?: "Judul",
            titleEnglish = tEng,
            imageUrl = cover,
            type = mType,
            score = score,
            format = fmt,
            status = status,
            episodes = episodes,
            chapters = chapters,
            genres = genresList,
            year = year,
            studio = studioName,
            popularity = popularity,
            imageUrlHd = coverHd
        )
    }

    fun toStudioFilmographyPage(
        studioObj: GetStudioFilmographyQuery.Studio,
        page: Int
    ): StudioFilmographyPage {
        val resolvedId = studioObj.id
        val resolvedName = studioObj.name
        val isAnimationStudio = studioObj.isAnimationStudio
        val siteUrl = studioObj.siteUrl?.takeIf { it.isNotBlank() }
        val favourites = studioObj.favourites?.takeIf { it > 0 }

        val pageInfo = studioObj.media?.pageInfo
        val hasNextPage = pageInfo?.hasNextPage ?: false
        val currentPage = pageInfo?.currentPage ?: page
        val total = pageInfo?.total ?: 0

        val seenIds = mutableSetOf<Int>()
        val items = (studioObj.media?.nodes ?: emptyList())
            .filterNotNull()
            .mapNotNull { node ->
                if (!seenIds.add(node.id)) null
                else toStudioFilmographyItem(node, resolvedName)
            }

        return StudioFilmographyPage(
            studioId = resolvedId,
            studioName = resolvedName,
            items = items,
            hasNextPage = hasNextPage,
            currentPage = currentPage,
            total = total,
            siteUrl = siteUrl,
            favourites = favourites,
            isAnimationStudio = isAnimationStudio
        )
    }

    fun toDiscoverMediaItem(
        m: GetDiscoverMediaQuery.Medium,
        fallbackType: MediaType
    ): MediaItem {
        val primaryTitle = m.title?.romaji?.takeIf { it.isNotBlank() }
            ?: m.title?.english?.takeIf { it.isNotBlank() }
            ?: "Unknown Title"
        val englishTitle = m.title?.english
        val img = m.coverImage?.large?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.medium?.takeIf { it.isNotBlank() }
            ?: ""
        val imgHd = m.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.large?.takeIf { it.isNotBlank() }
        val score = if ((m.averageScore ?: 0) > 0) m.averageScore!! / 10.0 else null
        val cleanDesc = m.description?.let { TextSanitizer.sanitize(it) }

        val statusStr = when (m.status?.rawValue) {
            "RELEASING" -> if (fallbackType == MediaType.ANIME) "AIRING" else "PUBLISHING"
            "FINISHED"  -> if (fallbackType == MediaType.ANIME) "AIRED" else "FINISHED"
            "NOT_YET_RELEASED" -> "NOT YET AIRED"
            "CANCELLED" -> "CANCELLED"
            "HIATUS"    -> "ON HIATUS"
            else        -> m.status?.rawValue?.uppercase() ?: "AIRED"
        }

        val studioName = m.studios?.nodes?.firstOrNull()?.name

        return MediaItem(
            malId = m.idMal,
            anilistId = m.id,
            title = primaryTitle,
            titleEnglish = englishTitle,
            imageUrl = img,
            type = fallbackType,
            score = score,
            synopsis = cleanDesc,
            episodes = m.episodes?.takeIf { it > 0 },
            chapters = m.chapters?.takeIf { it > 0 },
            volumes = m.volumes?.takeIf { it > 0 },
            status = statusStr,
            year = m.seasonYear,
            season = m.season?.rawValue,
            genres = m.genres?.filterNotNull() ?: emptyList(),
            format = m.format?.rawValue,
            studio = studioName,
            imageUrlHd = imgHd
        )
    }

    fun toBatchMediaItem(
        m: GetMediaBatchByMalIdsQuery.Medium,
        fallbackType: MediaType
    ): MediaItem {
        val primaryTitle = m.title?.romaji?.takeIf { it.isNotBlank() }
            ?: m.title?.english?.takeIf { it.isNotBlank() }
            ?: "Unknown Title"
        val englishTitle = m.title?.english
        val img = m.coverImage?.large?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.medium?.takeIf { it.isNotBlank() }
            ?: ""
        val imgHd = m.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
            ?: m.coverImage?.large?.takeIf { it.isNotBlank() }
        val score = if ((m.averageScore ?: 0) > 0) m.averageScore!! / 10.0 else null
        val cleanDesc = m.description?.let { TextSanitizer.sanitize(it) }

        val statusStr = when (m.status?.rawValue) {
            "RELEASING" -> if (fallbackType == MediaType.ANIME) "AIRING" else "PUBLISHING"
            "FINISHED"  -> if (fallbackType == MediaType.ANIME) "AIRED" else "FINISHED"
            "NOT_YET_RELEASED" -> "NOT YET AIRED"
            "CANCELLED" -> "CANCELLED"
            "HIATUS"    -> "ON HIATUS"
            else        -> m.status?.rawValue?.uppercase() ?: "AIRED"
        }

        val studioName = m.studios?.nodes?.firstOrNull()?.name

        return MediaItem(
            malId = m.idMal,
            anilistId = m.id,
            title = primaryTitle,
            titleEnglish = englishTitle,
            imageUrl = img,
            type = fallbackType,
            score = score,
            synopsis = cleanDesc,
            episodes = m.episodes?.takeIf { it > 0 },
            chapters = m.chapters?.takeIf { it > 0 },
            volumes = m.volumes?.takeIf { it > 0 },
            status = statusStr,
            year = m.seasonYear,
            season = m.season?.rawValue,
            genres = m.genres?.filterNotNull() ?: emptyList(),
            format = m.format?.rawValue,
            studio = studioName,
            imageUrlHd = imgHd
        )
    }
}
