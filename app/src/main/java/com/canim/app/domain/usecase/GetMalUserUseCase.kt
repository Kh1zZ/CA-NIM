package com.canim.app.domain.usecase

import com.canim.app.data.model.MalUser
import com.canim.app.domain.repository.AuthRepository
import javax.inject.Inject

class GetMalUserUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): MalUser = repository.getMalUser()
}
