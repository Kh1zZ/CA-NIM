package com.canim.app.domain.usecase

import com.canim.app.data.remote.UpdateChecker
import com.canim.app.data.remote.UpdateInfo
import javax.inject.Inject

class CheckForUpdatesUseCase @Inject constructor() {
    suspend operator fun invoke(currentVersion: String): Result<UpdateInfo> =
        UpdateChecker.checkLatestRelease(currentVersion)
}
