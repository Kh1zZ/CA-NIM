package com.canim.app.domain.repository

import com.canim.app.data.model.CastCrewProfile
import com.canim.app.data.model.ExtendedMediaDetail
import com.canim.app.data.model.MalTracking
import com.canim.app.data.model.MediaType

interface DetailRepository {
    fun getCachedExtendedDetail(
        aniListId: Int?,
        malId: Int?,
        type: MediaType? = null
    ): ExtendedMediaDetail?

    suspend fun getMalExtendedDetailFallback(malId: Int, type: MediaType): ExtendedMediaDetail?

    suspend fun getExtendedDetails(
        aniListId: Int?,
        malId: Int?,
        type: MediaType,
        forceRefresh: Boolean = false
    ): ExtendedMediaDetail?

    suspend fun getMalTrackingStatus(malId: Int, type: MediaType): MalTracking?

    suspend fun getCharacterProfile(characterId: Int, forceRefresh: Boolean = false): CastCrewProfile?

    suspend fun getStaffProfile(staffId: Int, forceRefresh: Boolean = false): CastCrewProfile?

    fun getAniListIdForMalId(malId: Int, type: MediaType? = null): Int?

    fun getMalIdForAniListId(aniListId: Int, type: MediaType? = null): Int?

    fun getCachedDetail(key: String): ExtendedMediaDetail?

    fun matchesDetailKey(eventKey: String, aniId: Int?, malId: Int?): Boolean
}
