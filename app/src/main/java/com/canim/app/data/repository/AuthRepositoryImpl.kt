package com.canim.app.data.repository

import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.model.MalSyncResult
import com.canim.app.data.model.MalUser
import com.canim.app.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val malAuthManager: MalAuthManager,
    private val syncEngine: LibrarySyncEngine? = null
) : AuthRepository {

    override fun buildMalAuthorizeUrl(): String = malAuthManager.buildAuthorizeUrl()

    override suspend fun handleMalOAuthCallback(code: String, state: String?): Result<MalUser> =
        malAuthManager.handleOAuthCallback(code, state)

    override fun getMalUser(): MalUser = malAuthManager.getCurrentUser()

    override fun logoutMal() {
        syncEngine?.onLogout()
        malAuthManager.logout()
    }

    override suspend fun syncWithMal(): MalSyncResult = malAuthManager.syncWithMal()
}
