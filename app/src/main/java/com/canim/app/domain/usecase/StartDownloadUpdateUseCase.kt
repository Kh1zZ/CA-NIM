package com.canim.app.domain.usecase

import android.content.Context
import com.canim.app.data.remote.UpdateChecker
import java.io.File
import javax.inject.Inject

class StartDownloadUpdateUseCase @Inject constructor() {
    suspend operator fun invoke(
        context: Context,
        downloadUrl: String,
        fileName: String = "canim-update.apk",
        onProgress: (Float) -> Unit
    ): Result<File> = UpdateChecker.downloadApk(
        context = context,
        downloadUrl = downloadUrl,
        fileName = fileName,
        onProgress = onProgress
    )
}
