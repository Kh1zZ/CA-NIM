package com.canim.app.domain.usecase

import com.canim.app.data.model.MalSyncResult
import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class SyncMalUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    suspend operator fun invoke(): MalSyncResult = repository.syncWithMal()
}
