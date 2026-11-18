package com.canim.app.data.repository

import android.content.Context
import com.canim.app.data.cache.CacheManager
import com.canim.app.data.local.LibrarySyncEngine
import com.canim.app.data.model.MalSyncResult
import com.canim.app.data.model.MalUser
import com.canim.app.domain.repository.AuthRepository
import com.canim.app.notification.NotificationScheduler
import com.canim.app.widget.WidgetUpdateHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val malAuthManager: MalAuthManager,
    private val syncEngine: LibrarySyncEngine? = null,
    @ApplicationContext private val context: Context? = null
) : AuthRepository {

    override fun buildMalAuthorizeUrl(): String = malAuthManager.buildAuthorizeUrl()

    override suspend fun handleMalOAuthCallback(code: String, state: String?): Result<MalUser> {
        val result = malAuthManager.handleOAuthCallback(code, state)
        if (result.isSuccess) {
            syncEngine?.onLogin()
            context?.let { NotificationScheduler.schedulePeriodicCheck(it) }
        }
        return result
    }

    override fun getMalUser(): MalUser = malAuthManager.getCurrentUser()

    override fun logoutMal() {
        malAuthManager.logout()
        syncEngine?.onLogout()
        context?.let { ctx ->
            CacheManager.clearTrackingDisk(ctx)
            NotificationScheduler.cancelPeriodicCheck(ctx)
            WidgetUpdateHelper.updateWatchingWidgets(ctx)
        }
    }

    override suspend fun syncWithMal(): MalSyncResult = malAuthManager.syncWithMal()
}
