package com.canim.app.domain.usecase

import android.content.Context
import com.canim.app.data.remote.UpdateChecker
import java.io.File
import javax.inject.Inject

class InstallUpdateUseCase @Inject constructor() {
    operator fun invoke(context: Context, apkFile: File): Result<Unit> =
        UpdateChecker.installApk(context, apkFile)
}
