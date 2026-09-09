package com.canim.app.domain.usecase

import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class LogoutMalUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    operator fun invoke() {
        repository.logoutMal()
    }
}
