package com.canim.app.data.remote

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.cache.StudioFilmographyPage
import com.canim.app.data.model.*
import com.canim.app.util.TextSanitizer
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class AniListFuzzyDate(
    val year: Int?,
    val month: Int?,
    val day: Int?
) {
    fun toFormattedString(): String? {
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
}

data class AniListMedia(
    val id: Int,
    val idMal: Int?,
    val title: AniListTitle?,
    val coverImage: AniListCoverImage?,
    val averageScore: Int?,
    val description: String?,
    val episodes: Int?,
    val chapters: Int?,
    val volumes: Int?,
    val status: String?,
    val seasonYear: Int?,
    val season: String?,
    val format: String?,
    val genres: List<String>?,
    val startDate: AniListFuzzyDate?,
    val endDate: AniListFuzzyDate?,
    val duration: Int?,
    val source: String?,
    val popularity: Int?,
    val rankings: List<AniListRanking>?,
    val recommendations: AniListRecommendations?,
    val studios: AniListStudios?,
    val characters: AniListCharacters?,
    val staff: AniListStaff?
)

data class AniListRanking(
    val rank: Int?,
    val type: String?,
    val allTime: Boolean?
)

data class AniListRecommendations(
    val nodes: List<AniListRecommendationNode>?
)

data class AniListRecommendationNode(
    val mediaRecommendation: AniListMedia?
)

data class AniListTitle(
    val romaji: String?,
    val english: String?,
    val native: String?
)

data class AniListCoverImage(
    val medium: String?,
    val large: String?,
    val extraLarge: String?
)

data class AniListStudios(
    val nodes: List<AniListStudioNode>?
)

data class AniListStudioNode(
    val id: Int? = null,
    val name: String
)

data class AniListCharacters(
    val edges: List<AniListCharacterEdge>?
)

data class AniListCharacterEdge(
    val role: String?,
    val node: AniListCharacterNode?,
    val voiceActors: List<AniListVoiceActor>?
)

data class AniListCharacterNode(
    val id: Int?,
    val name: AniListName?,
    val image: AniListImage?
)

data class AniListVoiceActor(
    val id: Int?,
    val name: AniListName?,
    val image: AniListImage?
)

data class AniListStaff(
    val edges: List<AniListStaffEdge>?
)

data class AniListStaffEdge(
    val role: String?,
    val node: AniListStaffNode?
)

data class AniListStaffNode(
    val id: Int?,
    val name: AniListName?,
    val image: AniListImage?
)

data class AniListName(
    val full: String?,
    val native: String? = null
)

data class AniListImage(
    val medium: String?,
    val large: String?
)

data class AniListPage(
    val media: List<AniListMedia>?
)

data class AniListResponseData(
    val Page: AniListPage?,
    val Media: AniListMedia?
)

data class AniListResponse(
    val data: AniListResponseData?
)

object AniListClient {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private const val GRAPHQL_ENDPOINT = "https://graphql.anilist.co"

    private suspend fun executeQuery(graphqlQuery: String, variables: JSONObject): String? = withContext(Dispatchers.IO) {
        try {
            val requestBodyJson = JSONObject().apply {
                put("query", graphqlQuery)
                put("variables", variables)
            }

            val request = Request.Builder()
                .url(GRAPHQL_ENDPOINT)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "CanimApp/2.0")
                .header("Accept", "application/json")
                .build()

            val response = ApiClient.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            response.body?.string()
        } catch (_: Exception) {
            null
        }
    }

    private fun mapAniListMediaToItem(m: AniListMedia, fallbackType: MediaType): MediaItem {
        val primaryTitle = m.title?.romaji ?: m.title?.english ?: "Unknown Title"
        val englishTitle = m.title?.english
        val img = m.coverImage?.large ?: m.coverImage?.extraLarge ?: m.coverImage?.medium ?: ""
        val score = if (m.averageScore != null && m.averageScore > 0) m.averageScore / 10.0 else null
        val cleanDescription = m.description
            ?.replace(Regex("<[^>]*>"), "")
            ?.replace("&quot;", "\"")
            ?.replace("&#039;", "'")
            ?.replace("&amp;", "&")

        val statusStr = when (m.status) {
            "RELEASING" -> if (fallbackType == MediaType.ANIME) "Currently Airing" else "Publishing"
            "FINISHED" -> if (fallbackType == MediaType.ANIME) "Finished Airing" else "Finished"
            "NOT_YET_RELEASED" -> "Not yet aired"
            "CANCELLED" -> "Cancelled"
            "HIATUS" -> "On Hiatus"
            else -> m.status ?: "Finished"
        }

        val studioName = m.studios?.nodes?.firstOrNull()?.name

        // Cache ID mapping
        if (m.idMal != null) {
            CacheManager.putIdMapping(m.idMal, m.id)
        }

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
            season = m.season,
            genres = m.genres ?: emptyList(),
            format = m.format,
            studio = studioName
        )
    }

    suspend fun resolveIdMal(malId: Int, type: MediaType): Int? = withContext(Dispatchers.IO) {
        val graphqlQuery = """
            query (${'$'}idMal: Int, ${'$'}type: MediaType) {
              Media(idMal: ${'$'}idMal, type: ${'$'}type) {
                id
                idMal
              }
            }
        """.trimIndent()
        val variables = JSONObject().apply {
            put("idMal", malId)
            put("type", if (type == MediaType.ANIME) "ANIME" else "MANGA")
        }
        val responseString = executeQuery(graphqlQuery, variables) ?: return@withContext null
        val parsed = gson.fromJson(responseString, AniListResponse::class.java)
        val m = parsed.data?.Media
        if (m != null) {
            CacheManager.putIdMapping(malId, m.id)
            m.id
        } else {
            null
        }
    }

    suspend fun resolveAniListId(aniListId: Int): Int? = withContext(Dispatchers.IO) {
        val graphqlQuery = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id) {
                id
                idMal
              }
            }
        """.trimIndent()
        val variables = JSONObject().apply {
            put("id", aniListId)
        }
        val responseString = executeQuery(graphqlQuery, variables) ?: return@withContext null
        val parsed = gson.fromJson(responseString, AniListResponse::class.java)
        val m = parsed.data?.Media
        if (m?.idMal != null) {
            CacheManager.putIdMapping(m.idMal, m.id)
            m.idMal
        } else {
            null
        }
    }

    suspend fun getMediaBatchByMalIds(malIds: List<Int>, type: MediaType): Map<Int, MediaItem> = withContext(Dispatchers.IO) {
        if (malIds.isEmpty()) return@withContext emptyMap()
        val resultMap = mutableMapOf<Int, MediaItem>()
        val distinctIds = malIds.distinct().filter { it > 0 }

        // Chunk by 50 to respect AniList max perPage
        for (chunk in distinctIds.chunked(50)) {
            val graphqlQuery = """
                query (${'$'}idMal_in: [Int], ${'$'}type: MediaType) {
                  Page(page: 1, perPage: 50) {
                    media(idMal_in: ${'$'}idMal_in, type: ${'$'}type) {
                      id
                      idMal
                      title {
                        romaji
                        english
                      }
                      coverImage {
                        medium
                        large
                        extraLarge
                      }
                      averageScore
                      description(asHtml: false)
                      episodes
                      chapters
                      volumes
                      status
                      seasonYear
                      season
                      format
                      genres
                      studios(isMain: true) {
                        nodes {
                          name
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val variables = JSONObject().apply {
                put("idMal_in", JSONArray(chunk))
                put("type", if (type == MediaType.ANIME) "ANIME" else "MANGA")
            }

            val responseString = executeQuery(graphqlQuery, variables)
            if (responseString != null) {
                val parsed = gson.fromJson(responseString, AniListResponse::class.java)
                val mediaList = parsed.data?.Page?.media ?: emptyList()
                for (m in mediaList) {
                    val item = mapAniListMediaToItem(m, type)
                    m.idMal?.let { malId ->
                        resultMap[malId] = item
                    }
                }
            }
        }
        resultMap
    }

    /**
     * Search Anime or Manga using AniList GraphQL.
     */
    suspend fun searchMedia(query: String, type: MediaType): List<MediaItem> = withContext(Dispatchers.IO) {
        val cached = CacheManager.getSearch(query, type.name)
        if (cached != null) return@withContext cached

        val graphqlQuery = """
            query (${'$'}search: String, ${'$'}type: MediaType) {
              Page(page: 1, perPage: 25) {
                media(search: ${'$'}search, type: ${'$'}type, sort: POPULARITY_DESC) {
                  id
                  idMal
                  title {
                    romaji
                    english
                  }
                  coverImage {
                    medium
                    large
                    extraLarge
                  }
                  averageScore
                  description(asHtml: false)
                  episodes
                  chapters
                  volumes
                  status
                  seasonYear
                  season
                  format
                  genres
                  studios(isMain: true) {
                    nodes {
                      name
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val variables = JSONObject().apply {
            put("search", query)
            put("type", if (type == MediaType.ANIME) "ANIME" else "MANGA")
        }

        val responseString = executeQuery(graphqlQuery, variables) ?: return@withContext emptyList()
        val parsed = gson.fromJson(responseString, AniListResponse::class.java)
        val mediaList = parsed.data?.Page?.media ?: return@withContext emptyList()

        val items = mediaList.map { mapAniListMediaToItem(it, type) }
        if (items.isNotEmpty()) {
            CacheManager.putSearch(query, type.name, items)
        }
        items
    }

    /**
     * On-demand Discover fetching by dynamic category & filters using server-side AniList parameters.
     */
    suspend fun getDiscoverMedia(
        category: DiscoverCategory,
        filter: DiscoverFilter = DiscoverFilter(),
        page: Int = 1,
        perPage: Int = 25,
        randomSort: String? = null,
        forceRefresh: Boolean = false
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) // 0-11

        // Calculate dynamic seasons
        val (currentSeason, nextSeason, nextSeasonYear) = when (currentMonth) {
            in 0..2 -> Triple("WINTER", "SPRING", currentYear)
            in 3..5 -> Triple("SPRING", "SUMMER", currentYear)
            in 6..8 -> Triple("SUMMER", "FALL", currentYear)
            else -> Triple("FALL", "WINTER", currentYear + 1) // Fall -> Winter next year
        }

        val cacheKey = "${category.key}_${filter.genre}_${filter.format}_${filter.year}_${filter.season}_${filter.minScore}_${randomSort}_p$page"
        if (!forceRefresh) {
            val cached = CacheManager.getDiscover(cacheKey)
            if (cached != null) return@withContext cached
        }

        val variables = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
            put("type", if (filter.format == "MANGA") "MANGA" else "ANIME")
        }

        when (category) {
            DiscoverCategory.CURRENT_SEASON -> {
                variables.put("season", currentSeason)
                variables.put("seasonYear", currentYear)
                variables.put("sort", JSONArray().apply { put("POPULARITY_DESC") })
            }
            DiscoverCategory.NEXT_SEASON -> {
                variables.put("season", nextSeason)
                variables.put("seasonYear", nextSeasonYear)
                variables.put("sort", JSONArray().apply { put("POPULARITY_DESC") })
            }
            DiscoverCategory.UPCOMING -> {
                variables.put("status", "NOT_YET_RELEASED")
                variables.put("sort", JSONArray().apply { put("POPULARITY_DESC") })
            }
            DiscoverCategory.TBA -> {
                variables.put("status", "NOT_YET_RELEASED")
                variables.put("sort", JSONArray().apply { put("ID_DESC") })
            }
            DiscoverCategory.STUDIO -> {
                variables.put("sort", JSONArray().apply { put("POPULARITY_DESC") })
            }
            DiscoverCategory.RANDOM_FILTER -> {
                filter.genre?.takeIf { it.isNotBlank() }?.let { variables.put("genre", it) }
                filter.format?.takeIf { it.isNotBlank() }?.let { variables.put("format", it) }
                filter.year?.let { variables.put("seasonYear", it) }
                filter.season?.takeIf { it.isNotBlank() }?.let { variables.put("season", it.uppercase()) }
                filter.status?.takeIf { it.isNotBlank() }?.let { variables.put("status", it.uppercase()) }
                filter.minScore?.let { variables.put("averageScore_greater", it * 10) }

                val sortToUse = randomSort ?: "POPULARITY_DESC"
                variables.put("sort", JSONArray().apply { put(sortToUse) })
            }
        }

        val graphqlQuery = """
            query (
              ${'$'}page: Int,
              ${'$'}perPage: Int,
              ${'$'}type: MediaType,
              ${'$'}status: MediaStatus,
              ${'$'}seasonYear: Int,
              ${'$'}season: MediaSeason,
              ${'$'}genre: String,
              ${'$'}format: MediaFormat,
              ${'$'}averageScore_greater: Int,
              ${'$'}sort: [MediaSort]
            ) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                media(
                  type: ${'$'}type,
                  status: ${'$'}status,
                  seasonYear: ${'$'}seasonYear,
                  season: ${'$'}season,
                  genre: ${'$'}genre,
                  format: ${'$'}format,
                  averageScore_greater: ${'$'}averageScore_greater,
                  sort: ${'$'}sort,
                  isAdult: false
                ) {
                  id
                  idMal
                  title {
                    romaji
                    english
                  }
                  coverImage {
                    medium
                    large
                    extraLarge
                  }
                  averageScore
                  description(asHtml: false)
                  episodes
                  chapters
                  volumes
                  status
                  seasonYear
                  season
                  format
                  genres
                  studios(isMain: true) {
                    nodes {
                      name
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val responseString = executeQuery(graphqlQuery, variables) ?: return@withContext emptyList()
        val parsed = gson.fromJson(responseString, AniListResponse::class.java)
        val mediaList = parsed.data?.Page?.media ?: return@withContext emptyList()

        val items = mediaList.map { mapAniListMediaToItem(it, if (filter.format == "MANGA") MediaType.MANGA else MediaType.ANIME) }
        if (items.isNotEmpty()) {
            CacheManager.putDiscover(cacheKey, items)
        }
        items
    }

    /**
     * Fetch extended details (Cast, Staff/Crew, Studio, Duration) on demand.
     */
    suspend fun getExtendedDetails(
        aniListId: Int?,
        malId: Int?,
        type: MediaType,
        forceRefresh: Boolean = false
    ): ExtendedMediaDetail? = withContext(Dispatchers.IO) {
        val resolvedId = aniListId ?: (malId?.let { CacheManager.getAniListIdForMalId(it) })
        val cacheKey = CacheManager.detailKey(resolvedId, malId)
        if (!forceRefresh) {
            val cached = CacheManager.getDetail(cacheKey)
            if (cached != null) return@withContext cached
        }

        val graphqlQuery = if (resolvedId != null) {
            """
                query (${'$'}id: Int) {
                  Media(id: ${'$'}id) {
                    id
                    idMal
                    title {
                      romaji
                      english
                      native
                    }
                    duration
                    source
                    status
                    genres
                    averageScore
                    popularity
                    rankings {
                      rank
                      allTime
                    }
                    recommendations(page: 1, perPage: 8, sort: RATING_DESC) {
                      nodes {
                        mediaRecommendation {
                          id
                          idMal
                          title {
                            romaji
                            english
                          }
                          coverImage {
                            large
                            medium
                          }
                          type
                          averageScore
                          format
                        }
                      }
                    }
                    startDate {
                      year
                      month
                      day
                    }
                    endDate {
                      year
                      month
                      day
                    }
                    studios(isMain: true) {
                      nodes {
                        id
                        name
                      }
                    }
                    characters(sort: ROLE, perPage: 12) {
                      edges {
                        role
                        node {
                          id
                          name {
                            full
                            native
                          }
                          image {
                            medium
                            large
                          }
                        }
                        voiceActors(language: JAPANESE) {
                          id
                          name {
                            full
                            native
                          }
                          image {
                            medium
                            large
                          }
                        }
                      }
                    }
                    staff(sort: RELEVANCE, perPage: 10) {
                      edges {
                        role
                        node {
                          id
                          name {
                            full
                            native
                          }
                          image {
                            medium
                            large
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
        } else if (malId != null) {
            """
                query (${'$'}idMal: Int, ${'$'}type: MediaType) {
                  Media(idMal: ${'$'}idMal, type: ${'$'}type) {
                    id
                    idMal
                    title {
                      romaji
                      english
                      native
                    }
                    duration
                    source
                    status
                    genres
                    averageScore
                    popularity
                    rankings {
                      rank
                      allTime
                    }
                    recommendations(page: 1, perPage: 8, sort: RATING_DESC) {
                      nodes {
                        mediaRecommendation {
                          id
                          idMal
                          title {
                            romaji
                            english
                          }
                          coverImage {
                            large
                            medium
                          }
                          type
                          averageScore
                          format
                        }
                      }
                    }
                    startDate {
                      year
                      month
                      day
                    }
                    endDate {
                      year
                      month
                      day
                    }
                    studios(isMain: true) {
                      nodes {
                        id
                        name
                      }
                    }
                    characters(sort: ROLE, perPage: 12) {
                      edges {
                        role
                        node {
                          id
                          name {
                            full
                            native
                          }
                          image {
                            medium
                            large
                          }
                        }
                        voiceActors(language: JAPANESE) {
                          id
                          name {
                            full
                            native
                          }
                          image {
                            medium
                            large
                          }
                        }
                      }
                    }
                    staff(sort: RELEVANCE, perPage: 10) {
                      edges {
                        role
                        node {
                          id
                          name {
                            full
                            native
                          }
                          image {
                            medium
                            large
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
        } else {
            return@withContext null
        }

        val variables = JSONObject().apply {
            if (resolvedId != null) {
                put("id", resolvedId)
            } else if (malId != null) {
                put("idMal", malId)
                put("type", if (type == MediaType.ANIME) "ANIME" else "MANGA")
            }
        }

        val responseString = executeQuery(graphqlQuery, variables)
        if (responseString == null) {
            CacheManager.putNegativeCache(cacheKey)
            return@withContext null
        }

        val parsed = gson.fromJson(responseString, AniListResponse::class.java)
        val media = parsed.data?.Media ?: return@withContext null

        CacheManager.putIdMapping(media.idMal, media.id)

        val castList = media.characters?.edges?.mapNotNull { edge ->
            val charNode = edge.node ?: return@mapNotNull null
            val va = edge.voiceActors?.firstOrNull()
            CharacterCastItem(
                characterId = charNode.id,
                characterName = charNode.name?.full ?: "Karakter",
                characterImage = charNode.image?.large ?: charNode.image?.medium,
                actorId = va?.id,
                actorName = va?.name?.full,
                actorImage = va?.image?.large ?: va?.image?.medium,
                role = edge.role ?: "Supporting"
            )
        } ?: emptyList()

        val staffList = media.staff?.edges?.mapNotNull { edge ->
            val staffNode = edge.node ?: return@mapNotNull null
            StaffMemberItem(
                staffId = staffNode.id,
                name = staffNode.name?.full ?: "Staff",
                role = edge.role ?: "Crew",
                image = staffNode.image?.large ?: staffNode.image?.medium
            )
        } ?: emptyList()

        val studioNode = media.studios?.nodes?.firstOrNull()
        val studioName = studioNode?.name
        val studioId = studioNode?.id

        val recList = media.recommendations?.nodes?.mapNotNull { node ->
            val rec = node.mediaRecommendation ?: return@mapNotNull null
            mapAniListMediaToItem(rec, if (rec.format == "MANGA") MediaType.MANGA else MediaType.ANIME)
        } ?: emptyList()

        val avgScore = if (media.averageScore != null && media.averageScore > 0) media.averageScore / 10.0 else null
        val rankValue = media.rankings?.firstOrNull { it.allTime == true }?.rank ?: media.rankings?.firstOrNull()?.rank

        val detail = ExtendedMediaDetail(
            anilistId = media.id,
            malId = media.idMal ?: malId,
            title = media.title?.romaji ?: media.title?.english ?: "",
            titleEnglish = media.title?.english,
            nativeTitle = media.title?.native,
            studio = studioName,
            studioId = studioId,
            source = media.source,
            airingStatus = media.status,
            startDate = media.startDate?.toFormattedString(),
            endDate = media.endDate?.toFormattedString(),
            genres = media.genres ?: emptyList(),
            durationMinutes = media.duration,
            cast = castList,
            crew = staffList,
            averageScore = avgScore,
            popularity = media.popularity,
            rank = rankValue,
            watchers = media.popularity,
            recommendations = recList,
            isFromFallback = false
        )

        CacheManager.putDetail(cacheKey, detail)
        detail
    }

    suspend fun getCharacterProfile(id: Int, forceRefresh: Boolean = false): CastCrewProfile? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = CacheManager.getCastCrewProfile(id, isStaff = false)
            if (cached != null) return@withContext cached
        }

        val query = """
            query (${'$'}id: Int) {
              Character(id: ${'$'}id) {
                id
                name {
                  first
                  middle
                  last
                  full
                  native
                }
                image {
                  large
                  medium
                }
                description(asHtml: false)
                gender
                dateOfBirth {
                  year
                  month
                  day
                }
                age
                media(page: 1, perPage: 25, sort: POPULARITY_DESC) {
                  edges {
                    characterRole
                    node {
                      id
                      idMal
                      title {
                        romaji
                        english
                      }
                      coverImage {
                        large
                        medium
                      }
                      startDate {
                        year
                      }
                      format
                      type
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val variables = JSONObject().apply { put("id", id) }
        val responseString = executeQuery(query, variables) ?: return@withContext null
        try {
            val root = JSONObject(responseString)
            val data = root.optJSONObject("data") ?: return@withContext null
            val charObj = data.optJSONObject("Character") ?: return@withContext null

            val nameObj = charObj.optJSONObject("name")
            val fullName = nameObj?.optString("full")?.takeIf { it.isNotBlank() } ?: "Karakter"
            val nativeName = nameObj?.optString("native")?.takeIf { it.isNotBlank() }
            val firstName = nameObj?.optString("first")?.takeIf { it.isNotBlank() }
            val lastName = nameObj?.optString("last")?.takeIf { it.isNotBlank() }

            val imgObj = charObj.optJSONObject("image")
            val imageUrl = imgObj?.optString("large")?.takeIf { it.isNotBlank() }
                ?: imgObj?.optString("medium")?.takeIf { it.isNotBlank() }

            val cleanDesc = TextSanitizer.sanitize(charObj.optString("description")).takeIf { it.isNotBlank() }

            val dob = charObj.optJSONObject("dateOfBirth")
            val dobStr = if (dob != null && !dob.isNull("year")) {
                val y = dob.optInt("year")
                val m = dob.optInt("month", 0)
                val d = dob.optInt("day", 0)
                if (m > 0 && d > 0) "$d/$m/$y" else "$y"
            } else null

            val ageStr = charObj.optString("age")?.takeIf { it.isNotBlank() && it != "null" }

            val mediaArray = charObj.optJSONObject("media")?.optJSONArray("edges")
            val filmography = mutableListOf<FilmographyItem>()
            if (mediaArray != null) {
                for (i in 0 until mediaArray.length()) {
                    val edge = mediaArray.optJSONObject(i) ?: continue
                    val role = edge.optString("characterRole")
                    val node = edge.optJSONObject("node") ?: continue
                    val mId = node.optInt("id")
                    val malId = if (node.has("idMal") && !node.isNull("idMal")) node.optInt("idMal") else null
                    val titleObj = node.optJSONObject("title")
                    val tRomaji = titleObj?.optString("romaji")
                    val tEng = titleObj?.optString("english")
                    val cImg = node.optJSONObject("coverImage")?.optString("large")
                        ?: node.optJSONObject("coverImage")?.optString("medium")
                    val sYear = node.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
                    val fmt = node.optString("format")
                    val mType = if (node.optString("type") == "MANGA") MediaType.MANGA else MediaType.ANIME

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
                            role = if (role.isNotBlank()) role else "Character",
                            characterName = fullName,
                            characterImage = imageUrl
                        )
                    )
                }
            }

            val profile = CastCrewProfile(
                id = id,
                isStaff = false,
                name = fullName,
                nativeName = nativeName,
                firstName = firstName,
                lastName = lastName,
                image = imageUrl,
                biography = cleanDesc,
                nationality = null,
                birthday = dobStr,
                age = ageStr,
                filmography = filmography
            )
            CacheManager.putCastCrewProfile(id, isStaff = false, profile)
            profile
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getStaffProfile(id: Int, forceRefresh: Boolean = false): CastCrewProfile? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = CacheManager.getCastCrewProfile(id, isStaff = true)
            if (cached != null) return@withContext cached
        }

        val query = """
            query (${'$'}id: Int) {
              Staff(id: ${'$'}id) {
                id
                name {
                  first
                  middle
                  last
                  full
                  native
                }
                image {
                  large
                  medium
                }
                description(asHtml: false)
                gender
                dateOfBirth {
                  year
                  month
                  day
                }
                age
                homeTown
                characterMedia(page: 1, perPage: 25, sort: POPULARITY_DESC) {
                  edges {
                    characterRole
                    characters {
                      id
                      name {
                        full
                      }
                      image {
                        medium
                        large
                      }
                    }
                    node {
                      id
                      idMal
                      title {
                        romaji
                        english
                      }
                      coverImage {
                        large
                        medium
                      }
                      startDate {
                        year
                      }
                      format
                      type
                    }
                  }
                }
                staffMedia(page: 1, perPage: 25, sort: POPULARITY_DESC) {
                  edges {
                    staffRole
                    node {
                      id
                      idMal
                      title {
                        romaji
                        english
                      }
                      coverImage {
                        large
                        medium
                      }
                      startDate {
                        year
                      }
                      format
                      type
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val variables = JSONObject().apply { put("id", id) }
        val responseString = executeQuery(query, variables) ?: return@withContext null
        try {
            val root = JSONObject(responseString)
            val data = root.optJSONObject("data") ?: return@withContext null
            val staffObj = data.optJSONObject("Staff") ?: return@withContext null

            val nameObj = staffObj.optJSONObject("name")
            val fullName = nameObj?.optString("full")?.takeIf { it.isNotBlank() } ?: "Staff"
            val nativeName = nameObj?.optString("native")?.takeIf { it.isNotBlank() }
            val firstName = nameObj?.optString("first")?.takeIf { it.isNotBlank() }
            val lastName = nameObj?.optString("last")?.takeIf { it.isNotBlank() }

            val imgObj = staffObj.optJSONObject("image")
            val imageUrl = imgObj?.optString("large")?.takeIf { it.isNotBlank() }
                ?: imgObj?.optString("medium")?.takeIf { it.isNotBlank() }

            val cleanDesc = TextSanitizer.sanitize(staffObj.optString("description")).takeIf { it.isNotBlank() }

            val dob = staffObj.optJSONObject("dateOfBirth")
            val dobStr = if (dob != null && !dob.isNull("year")) {
                val y = dob.optInt("year")
                val m = dob.optInt("month", 0)
                val d = dob.optInt("day", 0)
                if (m > 0 && d > 0) "$d/$m/$y" else "$y"
            } else null

            val ageStr = if (staffObj.has("age") && !staffObj.isNull("age")) staffObj.optString("age") else null
            val homeTown = staffObj.optString("homeTown")?.takeIf { it.isNotBlank() && it != "null" }

            val filmography = mutableListOf<FilmographyItem>()
            val charMediaArray = staffObj.optJSONObject("characterMedia")?.optJSONArray("edges")
            if (charMediaArray != null) {
                for (i in 0 until charMediaArray.length()) {
                    val edge = charMediaArray.optJSONObject(i) ?: continue
                    val role = edge.optString("characterRole")
                    val chars = edge.optJSONArray("characters")
                    val firstChar = chars?.optJSONObject(0)
                    val charName = firstChar?.optJSONObject("name")?.optString("full")?.takeIf { it.isNotBlank() }
                    val charImg = firstChar?.optJSONObject("image")?.optString("large")
                        ?: firstChar?.optJSONObject("image")?.optString("medium")
                    val node = edge.optJSONObject("node") ?: continue
                    val mId = node.optInt("id")
                    val malId = if (node.has("idMal") && !node.isNull("idMal")) node.optInt("idMal") else null
                    val titleObj = node.optJSONObject("title")
                    val tRomaji = titleObj?.optString("romaji")
                    val tEng = titleObj?.optString("english")
                    val cImg = node.optJSONObject("coverImage")?.optString("large")
                        ?: node.optJSONObject("coverImage")?.optString("medium")
                    val sYear = node.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
                    val fmt = node.optString("format")
                    val mType = if (node.optString("type") == "MANGA") MediaType.MANGA else MediaType.ANIME

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
                            role = if (role.isNotBlank()) role else "Voice Actor",
                            characterName = charName,
                            characterImage = charImg
                        )
                    )
                }
            }

            val staffMediaArray = staffObj.optJSONObject("staffMedia")?.optJSONArray("edges")
            if (staffMediaArray != null) {
                for (i in 0 until staffMediaArray.length()) {
                    val edge = staffMediaArray.optJSONObject(i) ?: continue
                    val role = edge.optString("staffRole")
                    val node = edge.optJSONObject("node") ?: continue
                    val mId = node.optInt("id")
                    val malId = if (node.has("idMal") && !node.isNull("idMal")) node.optInt("idMal") else null
                    val titleObj = node.optJSONObject("title")
                    val tRomaji = titleObj?.optString("romaji")
                    val tEng = titleObj?.optString("english")
                    val cImg = node.optJSONObject("coverImage")?.optString("large")
                        ?: node.optJSONObject("coverImage")?.optString("medium")
                    val sYear = node.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
                    val fmt = node.optString("format")
                    val mType = if (node.optString("type") == "MANGA") MediaType.MANGA else MediaType.ANIME

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

            val profile = CastCrewProfile(
                id = id,
                isStaff = true,
                name = fullName,
                nativeName = nativeName,
                firstName = firstName,
                lastName = lastName,
                image = imageUrl,
                biography = cleanDesc,
                nationality = homeTown,
                birthday = dobStr,
                age = ageStr,
                filmography = filmography.distinctBy { it.id }
            )
            CacheManager.putCastCrewProfile(id, isStaff = true, profile)
            profile
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getStudioFilmography(
        studioId: Int?,
        search: String? = null,
        page: Int = 1,
        perPage: Int = 24,
        forceRefresh: Boolean = false
    ): StudioFilmographyPage? = withContext(Dispatchers.IO) {
        if (studioId == null && search.isNullOrBlank()) return@withContext null
        if (!forceRefresh && studioId != null) {
            val cached = CacheManager.getStudioFilmography(studioId, page)
            if (cached != null) return@withContext cached
        }

        val query = """
            query (${'$'}id: Int, ${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int) {
              Studio(id: ${'$'}id, search: ${'$'}search) {
                id
                name
                media(page: ${'$'}page, perPage: ${'$'}perPage, sort: POPULARITY_DESC, isMain: true) {
                  pageInfo {
                    total
                    perPage
                    currentPage
                    lastPage
                    hasNextPage
                  }
                  nodes {
                    id
                    idMal
                    title {
                      romaji
                      english
                      native
                    }
                    coverImage {
                      large
                      medium
                    }
                    format
                    type
                    status
                    episodes
                    chapters
                    averageScore
                    genres
                    startDate {
                      year
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val variables = JSONObject().apply {
            if (studioId != null) put("id", studioId)
            if (!search.isNullOrBlank()) put("search", search)
            put("page", page)
            put("perPage", perPage)
        }

        val responseString = executeQuery(query, variables) ?: return@withContext null
        try {
            val root = JSONObject(responseString)
            val data = root.optJSONObject("data") ?: return@withContext null
            val studioObj = data.optJSONObject("Studio") ?: return@withContext null
            val resolvedStudioId = studioObj.optInt("id", studioId ?: 0)
            val resolvedStudioName = studioObj.optString("name", search ?: "Studio")

            val mediaObj = studioObj.optJSONObject("media")
            val pageInfo = mediaObj?.optJSONObject("pageInfo")
            val hasNextPage = pageInfo?.optBoolean("hasNextPage", false) ?: false
            val currentPage = pageInfo?.optInt("currentPage", page) ?: page
            val total = pageInfo?.optInt("total", 0) ?: 0

            val nodesArray = mediaObj?.optJSONArray("nodes")
            val items = mutableListOf<MediaItem>()
            val seenIds = mutableSetOf<Int>()
            if (nodesArray != null) {
                for (i in 0 until nodesArray.length()) {
                    val node = nodesArray.optJSONObject(i) ?: continue
                    val mId = node.optInt("id")
                    if (mId <= 0 || !seenIds.add(mId)) {
                        continue
                    }
                    val malId = if (node.has("idMal") && !node.isNull("idMal")) node.optInt("idMal") else null
                    val titleObj = node.optJSONObject("title")
                    val tRomaji = titleObj?.optString("romaji")
                    val tEng = titleObj?.optString("english")
                    val coverObj = node.optJSONObject("coverImage")
                    val cover = coverObj?.optString("large") ?: coverObj?.optString("medium") ?: ""
                    val fmt = node.optString("format")
                    val mType = if (node.optString("type") == "MANGA") MediaType.MANGA else MediaType.ANIME
                    val status = node.optString("status")
                    val episodes = if (node.has("episodes") && !node.isNull("episodes")) node.optInt("episodes") else null
                    val chapters = if (node.has("chapters") && !node.isNull("chapters")) node.optInt("chapters") else null
                    val score = if (node.has("averageScore") && !node.isNull("averageScore")) node.optDouble("averageScore") / 10.0 else null
                    val genresList = mutableListOf<String>()
                    val genresArr = node.optJSONArray("genres")
                    if (genresArr != null) {
                        for (g in 0 until genresArr.length()) {
                            genresList.add(genresArr.optString(g))
                        }
                    }
                    val year = node.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }

                    if (malId != null) {
                        CacheManager.putIdMapping(malId, mId)
                    }

                    items.add(
                        MediaItem(
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
                            studio = resolvedStudioName
                        )
                    )
                }
            }

            val result = StudioFilmographyPage(
                studioId = resolvedStudioId,
                studioName = resolvedStudioName,
                items = items,
                hasNextPage = hasNextPage,
                currentPage = currentPage,
                total = total
            )
            CacheManager.putStudioFilmography(resolvedStudioId, page, result)
            result
        } catch (_: Exception) {
            null
        }
    }
}
