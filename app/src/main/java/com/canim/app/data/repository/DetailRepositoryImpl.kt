package com.canim.app.data.repository

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.CastCrewProfile
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.AniListClient
import com.canim.app.data.remote.ApiClient
import com.canim.app.domain.repository.DetailRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetailRepositoryImpl @Inject constructor(
    private val malAuthManager: MalAuthManager,
    private val swrCoordinator: SwrCoordinator
) : DetailRepository {

    override fun getCachedExtendedDetail(
        aniListId: Int?,
        malId: Int?,
        type: MediaType?
    ): ExtendedMediaDetail? {
        val resolvedAniListId = aniListId ?: (malId?.let { CacheManager.getAniListIdForMalId(it, type) })
        val resolvedMalId = malId ?: (resolvedAniListId?.let { CacheManager.getMalIdForAniListId(it, type) })
        val primaryCacheKey = CacheManager.detailKey(resolvedAniListId, resolvedMalId)

        return CacheManager.getDetail(primaryCacheKey)
            ?: (resolvedAniListId?.let { CacheManager.getDetail(CacheManager.detailKey(it, null)) })
            ?: (resolvedMalId?.let { CacheManager.getDetail(CacheManager.detailKey(null, it)) })
    }

    override suspend fun getMalExtendedDetailFallback(malId: Int, type: MediaType): ExtendedMediaDetail? =
        malAuthManager.getExtendedDetailFallback(malId, type)

    override suspend fun getExtendedDetails(
        aniListId: Int?,
        malId: Int?,
        type: MediaType,
        forceRefresh: Boolean
    ): ExtendedMediaDetail? = withContext(Dispatchers.IO) {
        val resolvedAniListId = aniListId ?: (malId?.let { CacheManager.getAniListIdForMalId(it, type) })
        val resolvedMalId = malId ?: (resolvedAniListId?.let { CacheManager.getMalIdForAniListId(it, type) })
        val primaryCacheKey = CacheManager.detailKey(resolvedAniListId, resolvedMalId)

        if (!forceRefresh) {
            val swrHit = CacheManager.getDetailSwr(primaryCacheKey)
                ?: (resolvedAniListId?.let { CacheManager.getDetailSwr(CacheManager.detailKey(it, null)) })
                ?: (resolvedMalId?.let { CacheManager.getDetailSwr(CacheManager.detailKey(null, it)) })
            if (swrHit != null && (swrHit.data.malScore != null || resolvedMalId == null)) {
                if (swrHit.isStale) {
                    val capAniId = resolvedAniListId
                    val capMalId = resolvedMalId
                    val capType = type
                    val capPrimaryKey = primaryCacheKey
                    swrCoordinator.launchSwrJob(capPrimaryKey) {
                        runCatching {
                            val fresh = AniListClient.getExtendedDetails(capAniId, capMalId, capType, forceRefresh = true)
                            if (fresh != null) {
                                CacheManager.putDetail(capPrimaryKey, fresh)
                                capAniId?.let { CacheManager.putDetail(CacheManager.detailKey(it, null), fresh) }
                                capMalId?.let { CacheManager.putDetail(CacheManager.detailKey(null, it), fresh) }
                                if (fresh != swrHit.data) {
                                    swrCoordinator.emitRefreshEvent(capPrimaryKey, CacheRefreshType.DETAIL)
                                }
                            }
                        }
                    }
                }
                return@withContext swrHit.data
            }
        }

        AniListClient.deduplicateInFlight("detail_${resolvedAniListId}_${resolvedMalId}_${type.name}") {
            coroutineScope {
                // If AniList is currently in throttle cooldown, don't waste time on AniList call
                val isAniListThrottled = ApiClient.aniListLimiter.isCooldownActive()

                val malDeferred = async {
                    if (resolvedMalId != null) {
                        malAuthManager.getExtendedDetailFallback(resolvedMalId, type)
                    } else null
                }

                val aniDeferred = async {
                    if (!isAniListThrottled) {
                        AniListClient.getExtendedDetails(resolvedAniListId, resolvedMalId, type, forceRefresh)
                    } else null
                }

                var malExt = try { malDeferred.await() } catch (_: Exception) { null }
                val aniDetail = try { aniDeferred.await() } catch (_: Exception) { null }

                val effectiveMalId = aniDetail?.malId ?: resolvedMalId
                if (malExt == null && effectiveMalId != null && effectiveMalId != resolvedMalId) {
                    malExt = malAuthManager.getExtendedDetailFallback(effectiveMalId, type)
                }

                val merged = if (malExt != null && aniDetail != null) {
                    // MAL primary for metrics, anime info, poster, relations, recommendations
                    // AniList primary for cast and crew
                    ExtendedMediaDetail(
                        anilistId = aniDetail.anilistId ?: resolvedAniListId,
                        malId = malExt.malId ?: resolvedMalId,
                        title = malExt.title.takeIf { it.isNotBlank() } ?: aniDetail.title,
                        titleEnglish = malExt.titleEnglish ?: aniDetail.titleEnglish,
                        nativeTitle = malExt.nativeTitle ?: aniDetail.nativeTitle,
                        coverImage = malExt.coverImage?.takeIf { it.isNotBlank() } ?: aniDetail.coverImage,
                        bannerImage = malExt.bannerImage?.takeIf { it.isNotBlank() } ?: aniDetail.bannerImage,
                        synopsis = malExt.synopsis?.takeIf { it.isNotBlank() } ?: aniDetail.synopsis,
                        studio = malExt.studio ?: aniDetail.studio,
                        studioId = malExt.studioId ?: aniDetail.studioId,
                        publisher = malExt.publisher ?: aniDetail.publisher,
                        licensor = malExt.licensor ?: aniDetail.licensor,
                        durationMinutes = malExt.durationMinutes ?: aniDetail.durationMinutes,
                        source = malExt.source ?: aniDetail.source,
                        airingStatus = malExt.airingStatus ?: aniDetail.airingStatus,
                        startDate = malExt.startDate ?: aniDetail.startDate,
                        endDate = malExt.endDate ?: aniDetail.endDate,
                        genres = if (malExt.genres.isNotEmpty()) malExt.genres else aniDetail.genres,
                        openings = if (malExt.openings.isNotEmpty()) malExt.openings else aniDetail.openings,
                        endings = if (malExt.endings.isNotEmpty()) malExt.endings else aniDetail.endings,
                        cast = if (aniDetail.cast.isNotEmpty()) aniDetail.cast else malExt.cast,
                        crew = if (aniDetail.crew.isNotEmpty()) aniDetail.crew else malExt.crew,
                        relations = if (malExt.relations.isNotEmpty()) malExt.relations else aniDetail.relations,
                        averageScore = malExt.malScore ?: aniDetail.averageScore,
                        malScore = malExt.malScore ?: aniDetail.malScore,
                        malRank = malExt.malRank ?: aniDetail.malRank ?: aniDetail.rank,
                        malPopularity = malExt.malPopularity ?: aniDetail.malPopularity ?: aniDetail.popularity,
                        malMembers = malExt.malMembers ?: aniDetail.malMembers ?: aniDetail.watchers,
                        popularity = malExt.malPopularity ?: aniDetail.popularity,
                        rank = malExt.malRank ?: aniDetail.rank,
                        watchers = malExt.malMembers ?: aniDetail.watchers,
                        recommendations = if (malExt.recommendations.isNotEmpty()) malExt.recommendations else aniDetail.recommendations,
                        isFromFallback = malExt.isFromFallback
                    )
                } else if (malExt != null) {
                    malExt
                } else {
                    aniDetail
                }

                if (merged != null) {
                    val effectiveAni = merged.anilistId
                    val effectiveMal = merged.malId
                    if (effectiveAni != null && effectiveMal != null) {
                        CacheManager.putIdMapping(effectiveMal, effectiveAni, type)
                    }
                    CacheManager.putDetail(CacheManager.detailKey(effectiveAni, effectiveMal), merged)
                    if (effectiveAni != null) {
                        CacheManager.putDetail(CacheManager.detailKey(effectiveAni, null), merged)
                    }
                    if (effectiveMal != null) {
                        CacheManager.putDetail(CacheManager.detailKey(null, effectiveMal), merged)
                    }
                }

                merged
            }
        }
    }

    override suspend fun getMalTrackingStatus(malId: Int, type: MediaType): MalTracking? =
        malAuthManager.getMalUserTracking(malId, type)

    override suspend fun getCharacterProfile(characterId: Int, forceRefresh: Boolean): CastCrewProfile? = withContext(Dispatchers.IO) {
        AniListClient.getCharacterProfile(characterId, forceRefresh)
    }

    override suspend fun getStaffProfile(staffId: Int, forceRefresh: Boolean): CastCrewProfile? = withContext(Dispatchers.IO) {
        AniListClient.getStaffProfile(staffId, forceRefresh)
    }

    override fun getAniListIdForMalId(malId: Int, type: MediaType?): Int? =
        CacheManager.getAniListIdForMalId(malId, type)

    override fun getMalIdForAniListId(aniListId: Int, type: MediaType?): Int? =
        CacheManager.getMalIdForAniListId(aniListId, type)

    override fun getCachedDetail(key: String): ExtendedMediaDetail? =
        CacheManager.getDetail(key)

    override fun matchesDetailKey(eventKey: String, aniId: Int?, malId: Int?): Boolean =
        (aniId != null && eventKey == CacheManager.detailKey(aniId, malId))
            || (aniId != null && eventKey == CacheManager.detailKey(aniId, null))
            || (malId != null && eventKey == CacheManager.detailKey(null, malId))
            || (aniId != null && eventKey.contains("ani_$aniId"))
            || (malId != null && eventKey.contains("mal_$malId"))
}
