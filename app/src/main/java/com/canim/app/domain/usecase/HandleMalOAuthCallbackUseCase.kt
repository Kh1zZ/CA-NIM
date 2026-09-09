package com.canim.app.domain.usecase

import com.canim.app.data.model.MalUser
import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class HandleMalOAuthCallbackUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    suspend operator fun invoke(code: String, state: String?): Result<MalUser> =
        repository.handleMalOAuthCallback(code, state)
}
