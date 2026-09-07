package com.canim.app.data.remote

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val htmlUrl: String,
    val releaseNotes: String = "",
    val releaseName: String = "",
    val apkDownloadUrl: String? = null,
    val apkName: String? = null,
    val apkSize: Long = 0L
)

object UpdateChecker {

    private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/Kh1zZ/CA-NIM/releases/latest"

    /**
     * Parses a semver string (e.g., "v5.1.0", "5.0.0", "v5.1.0-alpha") into (major, minor, patch).
     */
    fun parseVersion(versionStr: String): Triple<Int, Int, Int> {
        val clean = versionStr.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        val parts = clean.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    /**
     * Compares two semver strings.
     * Returns 1 if [latest] is strictly newer than [current],
     * 0 if equal, -1 if [current] is newer.
     */
    fun compareSemver(current: String, latest: String): Int {
        val (cMajor, cMinor, cPatch) = parseVersion(current)
        val (lMajor, lMinor, lPatch) = parseVersion(latest)

        return when {
            lMajor > cMajor -> 1
            lMajor < cMajor -> -1
            lMinor > cMinor -> 1
            lMinor < cMinor -> -1
            lPatch > cPatch -> 1
            lPatch < cPatch -> -1
            else -> 0
        }
    }

    /**
     * Queries GitHub Releases API for the latest published release and finds any attached .apk asset.
     */
    suspend fun checkLatestRelease(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "CA-NIM-App")
                connectTimeout = 8000
                readTimeout = 8000
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP $responseCode dari GitHub API"))
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "").trim()
            val releaseName = json.optString("name", tagName)
            val htmlUrl = json.optString("html_url", "https://github.com/Kh1zZ/CA-NIM/releases/latest")
            val releaseNotes = json.optString("body", "")

            if (tagName.isBlank()) {
                return@withContext Result.failure(Exception("Tag rilis tidak ditemukan"))
            }

            // Find .apk asset if attached in this release
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkFileName: String? = null
            var apkFileSize: Long = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkFileName = name
                        apkFileSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val isNewer = compareSemver(current = currentVersion, latest = tagName) > 0

            Result.success(
                UpdateInfo(
                    isUpdateAvailable = isNewer,
                    currentVersion = currentVersion,
                    latestVersion = tagName,
                    htmlUrl = htmlUrl,
                    releaseNotes = releaseNotes,
                    releaseName = releaseName,
                    apkDownloadUrl = apkUrl,
                    apkName = apkFileName,
                    apkSize = apkFileSize
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Downloads the APK directly from GitHub with percentage progress callback.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        fileName: String = "canim-update.apk",
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val updateDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
            val apkFile = File(updateDir, fileName)
            if (apkFile.exists()) apkFile.delete()

            var targetUrl = downloadUrl
            var redirectCount = 0
            var connected = false

            while (!connected && redirectCount < 5) {
                val url = URL(targetUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "CA-NIM-App")
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                }

                val responseCode = connection.responseCode
                if (responseCode in 301..308) {
                    val newLocation = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newLocation != null) {
                        targetUrl = newLocation
                        redirectCount++
                    } else {
                        return@withContext Result.failure(Exception("Redirect tanpa lokasi"))
                    }
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    connected = true
                } else {
                    return@withContext Result.failure(Exception("HTTP $responseCode saat mengunduh APK"))
                }
            }

            if (!connected) {
                return@withContext Result.failure(Exception("Terlalu banyak pengalihan (redirect)"))
            }

            val fileLength = connection!!.contentLengthLong
            connection!!.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (fileLength > 0) {
                            val progress = totalBytesRead.toFloat() / fileLength.toFloat()
                            onProgress(progress.coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Launches the Android system package installer for the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
