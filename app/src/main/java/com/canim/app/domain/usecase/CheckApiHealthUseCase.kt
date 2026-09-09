package com.canim.app.domain.usecase

import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class CheckApiHealthUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    suspend fun isAniListUnavailable(): Boolean = repository.isAniListUnavailable()

    suspend fun isMalUnavailable(): Boolean = repository.isMalUnavailable()

    suspend operator fun invoke(): Pair<Boolean, Boolean> =
        Pair(repository.isAniListUnavailable(), repository.isMalUnavailable())
}
