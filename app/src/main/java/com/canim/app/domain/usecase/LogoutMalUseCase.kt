package com.canim.app.domain.usecase

import com.canim.app.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutMalUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke() {
        repository.logoutMal()
    }
}
