package com.canim.app.domain.usecase

import com.canim.app.data.model.MalUser
import com.canim.app.domain.repository.CanimRepositoryContract
import javax.inject.Inject

class GetMalUserUseCase @Inject constructor(
    private val repository: CanimRepositoryContract
) {
    operator fun invoke(): MalUser = repository.getMalUser()
}
