package com.canim.app.domain.usecase

import com.canim.app.domain.repository.AuthRepository
import javax.inject.Inject

class LoginMalUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): String = repository.buildMalAuthorizeUrl()
}
