package com.canim.app.domain.usecase

import android.util.Log
import com.canim.app.data.local.GachaCandidateStore
import com.canim.app.data.local.GachaCandidateToken
import com.canim.app.data.model.DiscoverCategory
import com.canim.app.data.model.DiscoverFilter
import com.canim.app.data.remote.ApiClient
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.domain.repository.DiscoverRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Opportunistic background expansion for Gacha candidate pool.
 *
 * Rules per Phase 3 PRD:
 * - Allowed only opportunistically while the user is actively using CA'NIM.
 * - Primary source: MAL. Fallback: AniList.
 * - Stores ONLY MAL ID + genre data (no full metadata crawl).
 * - Keep bursts low, obey rate limiting, yield to user actions.
 * - Pauses when resources are constrained / rate limiter is on cooldown.
 */
class ExpandGachaCandidatesUseCase @Inject constructor(
    private val candidateStore: GachaCandidateStore,
    private val discoverRepository: DiscoverRepository
) {
    companion object {
        private const val TARGET_CANDIDATE_COUNT = 60
        private const val BATCH_SIZE = 25
    }

    suspend operator fun invoke(forceExpand: Boolean = false): Int = withContext(Dispatchers.IO) {
        val currentCount = candidateStore.getCandidateCount()
        if (!forceExpand && currentCount >= TARGET_CANDIDATE_COUNT) {
            return@withContext 0
        }

        // Check if rate limiter is currently on cooldown; if so, immediately abort to yield resources
        if (ApiClient.malLimiter.isCooldownActive()) {
            return@withContext 0
        }

        var addedCount = 0

        // 1. Primary Source: MAL Rankings (Airing & All)
        try {
            val malRankingType = if (currentCount < 25) "airing" else "bypopularity"
            val offset = (currentCount / BATCH_SIZE) * BATCH_SIZE
            val resp = ApiClient.malApi.getAnimeRanking(
                clientId = MalAuthManager.CLIENT_ID,
                rankingType = malRankingType,
                limit = BATCH_SIZE,
                offset = offset
            )

            if (resp.isSuccessful && resp.body()?.data?.isNotEmpty() == true) {
                val tokens = resp.body()!!.data.mapNotNull { item ->
                    val id = item.node.id
                    val genres = item.node.genres?.map { it.name } ?: emptyList()
                    if (id > 0) GachaCandidateToken(malId = id, genres = genres) else null
                }
                candidateStore.addCandidates(tokens)
                addedCount += tokens.size
                return@withContext addedCount
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ExpandCandidates", "MAL candidate expansion failed, falling back: ${e.message}")
        }

        // 2. Fallback: AniList via DiscoverRepository if MAL call was empty/failed
        if (addedCount == 0 && !ApiClient.aniListLimiter.isCooldownActive()) {
            try {
                val discoverItems = discoverRepository.getDiscoverMedia(
                    category = DiscoverCategory.CURRENT_SEASON,
                    filter = DiscoverFilter(),
                    page = 1
                )
                val tokens = discoverItems.mapNotNull { item ->
                    val malId = item.malId
                    if (malId != null && malId > 0) {
                        GachaCandidateToken(malId = malId, genres = item.genres)
                    } else null
                }
                if (tokens.isNotEmpty()) {
                    candidateStore.addCandidates(tokens)
                    addedCount += tokens.size
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ExpandCandidates", "AniList fallback candidate expansion failed: ${e.message}")
            }
        }

        return@withContext addedCount
    }
}
