package com.canim.app.data.repository

import com.canim.app.data.cache.CacheManager
import com.canim.app.data.model.CastCrewProfile
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.MediaType
import com.canim.app.data.remote.AniListClient
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
                val aniDeferred = async {
                    AniListClient.getExtendedDetails(resolvedAniListId, resolvedMalId, type, forceRefresh)
                }
                val malDeferred = async {
                    if (resolvedMalId != null) {
                        malAuthManager.getExtendedDetailFallback(resolvedMalId, type)
                    } else null
                }

                val aniDetail = try { aniDeferred.await() } catch (_: Exception) { null }
                var malExt = try { malDeferred.await() } catch (_: Exception) { null }

                val effectiveMalId = aniDetail?.malId ?: resolvedMalId
                if (malExt == null && effectiveMalId != null && effectiveMalId != resolvedMalId) {
                    malExt = malAuthManager.getExtendedDetailFallback(effectiveMalId, type)
                }

                val merged = if (aniDetail != null && malExt != null) {
                    aniDetail.copy(
                        coverImage = malExt.coverImage?.takeIf { it.isNotBlank() } ?: aniDetail.coverImage,
                        malScore = malExt.malScore ?: aniDetail.malScore,
                        malRank = malExt.malRank ?: aniDetail.rank,
                        malPopularity = malExt.malPopularity ?: aniDetail.popularity,
                        malMembers = malExt.malMembers ?: aniDetail.watchers,
                        synopsis = malExt.synopsis?.takeIf { it.isNotBlank() } ?: aniDetail.synopsis,
                        airingStatus = malExt.airingStatus ?: aniDetail.airingStatus,
                        startDate = malExt.startDate ?: aniDetail.startDate,
                        endDate = malExt.endDate ?: aniDetail.endDate,
                        genres = if (malExt.genres.isNotEmpty()) malExt.genres else aniDetail.genres,
                        source = malExt.source ?: aniDetail.source,
                        bannerImage = aniDetail.bannerImage ?: malExt.bannerImage,
                        studio = aniDetail.studio ?: malExt.studio,
                        studioId = aniDetail.studioId ?: malExt.studioId,
                        publisher = aniDetail.publisher ?: malExt.publisher
                    )
                } else {
                    aniDetail ?: malExt
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
