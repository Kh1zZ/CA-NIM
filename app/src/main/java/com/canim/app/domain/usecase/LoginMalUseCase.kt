package com.canim.app.domain.usecase

import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class LoginMalUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    operator fun invoke(): String = repository.buildMalAuthorizeUrl()
}
