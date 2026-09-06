package com.canim.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val htmlUrl: String,
    val releaseNotes: String = "",
    val releaseName: String = ""
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
     * Queries GitHub Releases API for the latest published release.
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

            val isNewer = compareSemver(current = currentVersion, latest = tagName) > 0

            Result.success(
                UpdateInfo(
                    isUpdateAvailable = isNewer,
                    currentVersion = currentVersion,
                    latestVersion = tagName,
                    htmlUrl = htmlUrl,
                    releaseNotes = releaseNotes,
                    releaseName = releaseName
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
