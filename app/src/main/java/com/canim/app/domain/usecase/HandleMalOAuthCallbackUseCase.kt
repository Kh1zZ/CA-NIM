package com.canim.app.domain.usecase

import com.canim.app.data.model.MalUser
import com.canim.app.domain.repository.AuthRepository
import javax.inject.Inject

class HandleMalOAuthCallbackUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(code: String, state: String?): Result<MalUser> =
        repository.handleMalOAuthCallback(code, state)
}
