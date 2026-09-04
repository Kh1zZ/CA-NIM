package com.canim.app.data.remote

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.*
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
    val studios: AniListStudios?,
    val characters: AniListCharacters?,
    val staff: AniListStaff?
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
    val name: AniListName?,
    val image: AniListImage?
)

data class AniListVoiceActor(
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
    val name: AniListName?,
    val image: AniListImage?
)

data class AniListName(
    val full: String?
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
        CacheManager.putIdMapping(m.idMal, m.id)

        return MediaItem(
            malId = m.idMal ?: m.id,
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
        randomSort: String? = null
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

        val cacheKey = "discover_${category.key}_${filter.genre}_${filter.format}_${filter.year}_${filter.season}_${filter.minScore}_${randomSort}_p$page"
        val cached = CacheManager.getDiscover(cacheKey)
        if (cached != null) return@withContext cached

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
        type: MediaType
    ): ExtendedMediaDetail? = withContext(Dispatchers.IO) {
        val resolvedId = aniListId ?: (malId?.let { CacheManager.getAniListIdForMalId(it) })
        val cacheKey = "detail_${resolvedId ?: "mal_$malId"}"
        val cached = CacheManager.getDetail(cacheKey)
        if (cached != null) return@withContext cached

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
                        name
                      }
                    }
                    characters(sort: ROLE, perPage: 12) {
                      edges {
                        role
                        node {
                          name {
                            full
                          }
                          image {
                            medium
                            large
                          }
                        }
                        voiceActors(language: JAPANESE) {
                          name {
                            full
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
                          name {
                            full
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
                        name
                      }
                    }
                    characters(sort: ROLE, perPage: 12) {
                      edges {
                        role
                        node {
                          name {
                            full
                          }
                          image {
                            medium
                            large
                          }
                        }
                        voiceActors(language: JAPANESE) {
                          name {
                            full
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
                          name {
                            full
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
                characterName = charNode.name?.full ?: "Karakter",
                characterImage = charNode.image?.large ?: charNode.image?.medium,
                actorName = va?.name?.full,
                actorImage = va?.image?.large ?: va?.image?.medium,
                role = edge.role ?: "Supporting"
            )
        } ?: emptyList()

        val staffList = media.staff?.edges?.mapNotNull { edge ->
            val staffNode = edge.node ?: return@mapNotNull null
            StaffMemberItem(
                name = staffNode.name?.full ?: "Staff",
                role = edge.role ?: "Crew",
                image = staffNode.image?.large ?: staffNode.image?.medium
            )
        } ?: emptyList()

        val studioName = media.studios?.nodes?.firstOrNull()?.name

        val detail = ExtendedMediaDetail(
            anilistId = media.id,
            malId = media.idMal ?: malId,
            title = media.title?.romaji ?: media.title?.english ?: "",
            titleEnglish = media.title?.english,
            nativeTitle = media.title?.native,
            studio = studioName,
            source = media.source,
            airingStatus = media.status,
            startDate = media.startDate?.toFormattedString(),
            endDate = media.endDate?.toFormattedString(),
            genres = media.genres ?: emptyList(),
            durationMinutes = media.duration,
            cast = castList,
            crew = staffList,
            isFromFallback = false
        )

        CacheManager.putDetail(cacheKey, detail)
        detail
    }
}
