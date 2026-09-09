package com.canim.app.domain.usecase

import com.canim.app.data.model.MalSyncResult
import com.canim.app.domain.repository.AuthRepository
import javax.inject.Inject

class SyncMalUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): MalSyncResult = repository.syncWithMal()
}
