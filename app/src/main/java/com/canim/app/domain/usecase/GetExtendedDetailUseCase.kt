package com.canim.app.domain.usecase

import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.MediaType
import com.canim.app.domain.repository.DetailRepository
import javax.inject.Inject

class GetExtendedDetailUseCase @Inject constructor(
    private val repository: DetailRepository
) {
    fun getCachedExtendedDetail(
        aniListId: Int?,
        malId: Int?,
        type: MediaType? = null
    ): ExtendedMediaDetail? = repository.getCachedExtendedDetail(aniListId, malId, type)

    suspend fun getMalExtendedDetailFallback(malId: Int, type: MediaType): ExtendedMediaDetail? =
        repository.getMalExtendedDetailFallback(malId, type)

    suspend fun getMalTrackingStatus(malId: Int, type: MediaType): MalTracking? =
        repository.getMalTrackingStatus(malId, type)

    fun getAniListIdForMalId(malId: Int, type: MediaType? = null): Int? =
        repository.getAniListIdForMalId(malId, type)

    fun getMalIdForAniListId(aniListId: Int, type: MediaType? = null): Int? =
        repository.getMalIdForAniListId(aniListId, type)

    fun getCachedDetail(key: String): ExtendedMediaDetail? =
        repository.getCachedDetail(key)

    fun matchesDetailKey(eventKey: String, aniId: Int?, malId: Int?): Boolean =
        repository.matchesDetailKey(eventKey, aniId, malId)

    suspend fun getExtendedDetails(
        aniListId: Int?,
        malId: Int?,
        type: MediaType,
        forceRefresh: Boolean = false
    ): ExtendedMediaDetail? = repository.getExtendedDetails(aniListId, malId, type, forceRefresh)

    suspend operator fun invoke(malId: Int, type: MediaType): ExtendedMediaDetail? =
        repository.getMalExtendedDetailFallback(malId, type)
}
