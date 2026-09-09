package com.canim.app.domain.usecase

import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.MediaType
import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class GetExtendedDetailUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
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

    suspend operator fun invoke(malId: Int, type: MediaType): ExtendedMediaDetail? =
        repository.getMalExtendedDetailFallback(malId, type)
}
