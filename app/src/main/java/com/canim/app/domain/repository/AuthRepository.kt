package com.canim.app.domain.repository

import com.canim.app.data.model.MalSyncResult
import com.canim.app.data.model.MalUser

interface AuthRepository {
    fun buildMalAuthorizeUrl(): String

    suspend fun handleMalOAuthCallback(code: String, state: String?): Result<MalUser>

    fun getMalUser(): MalUser

    fun logoutMal()

    suspend fun syncWithMal(): MalSyncResult
}
