package com.canim.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.canim.app.data.model.MalUser

class MalSecureStorage(private val context: Context) {

    private val prefs: SharedPreferences = try {
        createEncryptedPrefs(context)
    } catch (e: Exception) {
        Log.e("MalSecureStorage", "Failed to initialize EncryptedSharedPreferences, resetting corrupted store: ${e.message}")
        try {
            val sharedPrefsFile = java.io.File(context.filesDir.parent, "shared_prefs/$PREFS_FILENAME.xml")
            if (sharedPrefsFile.exists()) {
                sharedPrefsFile.delete()
            }
            createEncryptedPrefs(context)
        } catch (e2: Exception) {
            Log.e("MalSecureStorage", "Recovery failed, falling back to private SharedPreferences", e2)
            context.getSharedPreferences(PREFS_FILENAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    private fun createEncryptedPrefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            ctx,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val PREFS_FILENAME = "canim_mal_secure_prefs"
        private const val PREFS_FILENAME_FALLBACK = "canim_mal_prefs"

        private const val KEY_ACCESS_TOKEN = "mal_access_token"
        private const val KEY_REFRESH_TOKEN = "mal_refresh_token"
        private const val KEY_EXPIRES_AT = "mal_expires_at"
        private const val KEY_PKCE_VERIFIER = "mal_pkce_verifier"
        private const val KEY_PKCE_STATE = "mal_pkce_state"
        private const val KEY_USER_ID = "mal_user_id"
        private const val KEY_USERNAME = "mal_username"
        private const val KEY_USER_PICTURE = "mal_user_picture"
        private const val KEY_USER_LOCATION = "mal_user_location"
        private const val KEY_USER_GENDER = "mal_user_gender"
        private const val KEY_LAST_SYNCED = "mal_last_synced"
    }

    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
        try {
            prefs.edit().apply {
                putString(KEY_ACCESS_TOKEN, accessToken)
                if (refreshToken != null) {
                    putString(KEY_REFRESH_TOKEN, refreshToken)
                }
                putLong(KEY_EXPIRES_AT, expiresAt)
            }.apply()
        } catch (e: Exception) {
            Log.e("MalSecureStorage", "Failed to save tokens: ${e.message}", e)
        }
    }

    fun getAccessToken(): String? = try {
        prefs.getString(KEY_ACCESS_TOKEN, null)
    } catch (e: Exception) {
        Log.e("MalSecureStorage", "Failed to read access token: ${e.message}", e)
        null
    }

    fun getRefreshToken(): String? = try {
        prefs.getString(KEY_REFRESH_TOKEN, null)
    } catch (e: Exception) {
        Log.e("MalSecureStorage", "Failed to read refresh token: ${e.message}", e)
        null
    }

    fun isTokenExpired(): Boolean = try {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        // Refresh 60 seconds before actual expiration
        expiresAt <= 0 || System.currentTimeMillis() >= (expiresAt - 60_000)
    } catch (e: Exception) {
        true
    }

    fun savePkce(verifier: String, state: String) {
        try {
            prefs.edit()
                .putString(KEY_PKCE_VERIFIER, verifier)
                .putString(KEY_PKCE_STATE, state)
                .apply()
        } catch (e: Exception) {
            Log.e("MalSecureStorage", "Failed to save PKCE: ${e.message}", e)
        }
    }

    fun getPkceVerifier(): String? = try {
        prefs.getString(KEY_PKCE_VERIFIER, null)
    } catch (e: Exception) {
        Log.e("MalSecureStorage", "Failed to read PKCE verifier: ${e.message}", e)
        null
    }

    fun getPkceState(): String? = try {
        prefs.getString(KEY_PKCE_STATE, null)
    } catch (e: Exception) {
        Log.e("MalSecureStorage", "Failed to read PKCE state: ${e.message}", e)
        null
    }

    fun clearPkce() {
        try {
            prefs.edit()
                .remove(KEY_PKCE_VERIFIER)
                .remove(KEY_PKCE_STATE)
                .apply()
        } catch (e: Exception) {
            Log.e("MalSecureStorage", "Failed to clear PKCE: ${e.message}", e)
        }
    }

    fun saveUserProfile(id: Long, username: String, pictureUrl: String?, location: String? = null, gender: String? = null) {
        try {
            prefs.edit()
                .putLong(KEY_USER_ID, id)
                .putString(KEY_USERNAME, username)
                .putString(KEY_USER_PICTURE, pictureUrl)
                .putString(KEY_USER_LOCATION, location)
                .putString(KEY_USER_GENDER, gender)
                .apply()
        } catch (e: Exception) {
            Log.e("MalSecureStorage", "Failed to save user profile: ${e.message}", e)
        }
    }

    fun getUser(): MalUser = try {
        val token = getAccessToken()
        val username = prefs.getString(KEY_USERNAME, null)
        val picture = prefs.getString(KEY_USER_PICTURE, null)
        val location = prefs.getString(KEY_USER_LOCATION, null)
        val gender = prefs.getString(KEY_USER_GENDER, null)
        val id = prefs.getLong(KEY_USER_ID, 0L)

        MalUser(
            id = id,
            username = username ?: "",
            pictureUrl = picture,
            location = location,
            gender = gender,
            isLoggedIn = !token.isNullOrBlank() && !username.isNullOrBlank()
        )
    } catch (e: Exception) {
        MalUser()
    }

    fun setLastSynced(timestamp: Long = System.currentTimeMillis()) {
        try {
            prefs.edit().putLong(KEY_LAST_SYNCED, timestamp).apply()
        } catch (e: Exception) {
            Log.e("MalSecureStorage", "Failed to set last synced: ${e.message}", e)
        }
    }

    fun getLastSynced(): Long = try {
        prefs.getLong(KEY_LAST_SYNCED, 0L)
    } catch (e: Exception) {
        0L
    }

    fun clearAuth() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e("MalSecureStorage", "Failed to clear auth: ${e.message}", e)
        }
    }
}
