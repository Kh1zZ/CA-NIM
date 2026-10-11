package com.canim.app.data.resolver

import java.util.Locale

/**
 * OriginDetector provides authoritative and heuristic detection of media country of origin.
 * Primary source is AniList GraphQL scalar countryOfOrigin ("JP" vs "CN").
 * When resolving data from MyAnimeList (which lacks an explicit country field),
 * known Chinese animation production studios and producers are matched heuristically.
 */
object OriginDetector {

    private val CHINESE_STUDIOS = setOf(
        "bilibili",
        "tencent animation & comics",
        "tencent penguin pictures",
        "tencent video",
        "haoliners animation league",
        "sparkly key animation",
        "foch film",
        "coloroom pictures",
        "colored-pencil animation design",
        "lan studio",
        "studio lan",
        "pb animation co. ltd.",
        "pb animation",
        "samsara animation",
        "paper plane animation",
        "nice boat animation",
        "b.cmay pictures",
        "g.cmay animation & film",
        "sharefun studio",
        "wulifang culture",
        "l²studio",
        "shanghai foch film and tv culture investment",
        "beijing rocen digital",
        "chosen animation",
        "big firebird animation",
        "b&t",
        "sparkly key animation studio",
        "byment",
        "thunde ray",
        "fenz animation"
    )

    /**
     * Checks if a studio name matches known Chinese Donghua animation studios.
     */
    fun isChineseStudio(studioName: String?): Boolean {
        if (studioName.isNullOrBlank()) return false
        val normalized = studioName.lowercase(Locale.ROOT).trim()
        return CHINESE_STUDIOS.any { normalized.contains(it) }
    }

    /**
     * Detects country of origin code ("CN", "JP", etc.).
     */
    fun detectOrigin(
        studioName: String?,
        explicitOrigin: String? = null,
        defaultOrigin: String = "JP"
    ): String {
        if (!explicitOrigin.isNullOrBlank()) {
            return explicitOrigin.uppercase(Locale.ROOT).trim()
        }
        if (isChineseStudio(studioName)) {
            return "CN"
        }
        return defaultOrigin
    }
}
