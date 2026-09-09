package com.canim.app.domain.usecase

import com.canim.app.data.model.CastCrewProfile
import com.canim.app.domain.repository.DetailRepository
import javax.inject.Inject

class GetCastCrewProfileUseCase @Inject constructor(
    private val repository: DetailRepository
) {
    suspend fun getStaffProfile(staffId: Int, forceRefresh: Boolean = false): CastCrewProfile? =
        repository.getStaffProfile(staffId, forceRefresh)

    suspend fun getCharacterProfile(characterId: Int, forceRefresh: Boolean = false): CastCrewProfile? =
        repository.getCharacterProfile(characterId, forceRefresh)

    suspend operator fun invoke(id: Int, isStaff: Boolean, forceRefresh: Boolean = false): CastCrewProfile? =
        if (isStaff) repository.getStaffProfile(id, forceRefresh) else repository.getCharacterProfile(id, forceRefresh)
}
