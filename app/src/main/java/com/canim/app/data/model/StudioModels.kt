package com.canim.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class StudioBioInfo(
    val studioId: Int,
    val name: String,
    val foundedYear: Int? = null,
    val country: String = "Jepang",
    val officialSite: String? = null,
    val bio: String? = null,
    val totalAnime: Int? = null,
    val favourites: Int? = null,
    val logoUrl: String? = null,
    val coverUrl: String? = null
)

enum class StudioFilmographySort(val label: String) {
    YEAR_DESC("Tahun: Terbaru → Terlama"),
    YEAR_ASC("Tahun: Terlama → Terbaru"),
    POPULARITY_DESC("Popularitas"),
    SCORE_DESC("Rating Tertinggi")
}

@Immutable
data class StudioYearGroup(
    val header: String,
    val year: Int? = null,
    val items: List<MediaItem> = emptyList()
)

fun groupAndSortFilmography(
    items: List<MediaItem>,
    sort: StudioFilmographySort
): List<StudioYearGroup> {
    if (items.isEmpty()) return emptyList()

    return when (sort) {
        StudioFilmographySort.YEAR_DESC -> {
            val (tba, withYear) = items.partition { it.year == null || it.year <= 0 }
            val yearGroups = withYear.groupBy { it.year!! }
                .toList()
                .sortedByDescending { it.first }
                .map { (year, list) ->
                    StudioYearGroup(
                        header = year.toString(),
                        year = year,
                        items = list.sortedByDescending { it.score ?: 0.0 }
                    )
                }
            val result = mutableListOf<StudioYearGroup>()
            if (tba.isNotEmpty()) {
                result.add(
                    StudioYearGroup(
                        header = "Akan Datang / TBA",
                        year = null,
                        items = tba
                    )
                )
            }
            result.addAll(yearGroups)
            result
        }
        StudioFilmographySort.YEAR_ASC -> {
            val (tba, withYear) = items.partition { it.year == null || it.year <= 0 }
            val yearGroups = withYear.groupBy { it.year!! }
                .toList()
                .sortedBy { it.first }
                .map { (year, list) ->
                    StudioYearGroup(
                        header = year.toString(),
                        year = year,
                        items = list.sortedByDescending { it.score ?: 0.0 }
                    )
                }
            val result = mutableListOf<StudioYearGroup>()
            if (tba.isNotEmpty()) {
                result.add(
                    StudioYearGroup(
                        header = "Akan Datang / TBA",
                        year = null,
                        items = tba
                    )
                )
            }
            result.addAll(yearGroups)
            result
        }
        StudioFilmographySort.POPULARITY_DESC -> {
            val sorted = items.sortedWith(
                compareByDescending<MediaItem> { it.popularity ?: 0 }
                    .thenByDescending { it.score ?: 0.0 }
            )
            listOf(
                StudioYearGroup(
                    header = "Semua Rilisan (Popularitas)",
                    year = null,
                    items = sorted
                )
            )
        }
        StudioFilmographySort.SCORE_DESC -> {
            val sorted = items.sortedWith(
                compareByDescending<MediaItem> { it.score ?: 0.0 }
                    .thenByDescending { it.popularity ?: 0 }
            )
            listOf(
                StudioYearGroup(
                    header = "Semua Rilisan (Rating Tertinggi)",
                    year = null,
                    items = sorted
                )
            )
        }
    }
}

