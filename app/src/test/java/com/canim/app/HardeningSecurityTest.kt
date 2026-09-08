package com.canim.app

import android.content.Context
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.remote.UpdateChecker
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.util.LogRedactor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class HardeningSecurityTest {

    private lateinit var context: Context
    private lateinit var secureStorage: MalSecureStorage
    private lateinit var authManager: MalAuthManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        secureStorage = MalSecureStorage(context)
        authManager = MalAuthManager(secureStorage)
        secureStorage.clearAuth()
        secureStorage.clearPkce()
    }

    // ========================================================================
    // 1. Redaction Tests
    // ========================================================================

    @Test
    fun testRedactsBearerToken() {
        val input = "Authorization: Bearer my_secret_token_12345"
        val output = LogRedactor.redact(input)
        assertEquals("Authorization: Bearer [REDACTED]", output)
        assertFalse(output.contains("my_secret_token_12345"))
    }

    @Test
    fun testRedactsSensitiveParameters() {
        val input = "https://api.myanimelist.net/token?client_id=12345&client_secret=secret999&code=auth_code_xyz&code_verifier=pkce_secret_abc&state=oauth_state_val"
        val output = LogRedactor.redact(input)
        assertTrue(output.contains("client_id=[REDACTED]"))
        assertTrue(output.contains("client_secret=[REDACTED]"))
        assertTrue(output.contains("code=[REDACTED]"))
        assertTrue(output.contains("code_verifier=[REDACTED]"))
        assertTrue(output.contains("state=[REDACTED]"))
        assertFalse(output.contains("secret999"))
        assertFalse(output.contains("auth_code_xyz"))
        assertFalse(output.contains("pkce_secret_abc"))
        assertFalse(output.contains("oauth_state_val"))
    }

    @Test
    fun testRedactsSearchQuery() {
        val input = "GET /v2/anime?q=Attack+On+Titan&limit=10"
        val output = LogRedactor.redact(input)
        assertTrue(output.contains("q=[REDACTED_QUERY]"))
        assertFalse(output.contains("Attack+On+Titan"))
    }

    @Test
    fun testRedactException() {
        val ex = IllegalStateException("Failed with token secret_token_value_abc")
        val output = LogRedactor.redactException(ex)
        assertTrue(output.startsWith("IllegalStateException:"))
    }

    // ========================================================================
    // 2. APK Update Hardening Tests
    // ========================================================================

    @Test
    fun testSanitizeFileNamePathTraversal() {
        assertEquals("update.apk", UpdateChecker.sanitizeFileName("../../update.apk"))
        assertEquals("app.apk", UpdateChecker.sanitizeFileName("../../../root/app.apk"))
        assertEquals("update.apk", UpdateChecker.sanitizeFileName("C:\\Windows\\System32\\update.apk"))
        assertEquals("canim-update.apk", UpdateChecker.sanitizeFileName("malicious.exe"))
        assertEquals("canim-update.apk", UpdateChecker.sanitizeFileName(""))
    }

    @Test
    fun testIsSafeDownloadUrlAllowlist() {
        assertTrue(UpdateChecker.isSafeDownloadUrl("https://github.com/Kh1zZ/CA-NIM/releases/download/v1.0.0/app.apk"))
        assertTrue(UpdateChecker.isSafeDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset-2e65be/app.apk"))
        // Reject HTTP (insecure)
        assertFalse(UpdateChecker.isSafeDownloadUrl("http://github.com/download/app.apk"))
        // Reject non-GitHub hosts
        assertFalse(UpdateChecker.isSafeDownloadUrl("https://evil.com/app.apk"))
        assertFalse(UpdateChecker.isSafeDownloadUrl("https://malicious-github.com/app.apk"))
        assertFalse(UpdateChecker.isSafeDownloadUrl("javascript:alert(1)"))
    }

    @Test
    fun testInstallApkValidation() {
        val emptyFile = File(context.cacheDir, "empty.apk").apply {
            createNewFile()
        }
        val resultEmpty = UpdateChecker.installApk(context, emptyFile)
        assertTrue(resultEmpty.isFailure)

        val nonExistent = File(context.cacheDir, "non_existent.apk")
        val resultNonExistent = UpdateChecker.installApk(context, nonExistent)
        assertTrue(resultNonExistent.isFailure)
    }

    // ========================================================================
    // 3. OAuth Deep Link Hardening Tests
    // ========================================================================

    @Test
    fun testOAuthCallbackFailsWhenNoSavedState() = runBlocking {
        // No saved state in storage
        val result = authManager.handleOAuthCallback("sample_code", "some_state")
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("tidak valid atau telah kedaluwarsa"))
    }

    @Test
    fun testOAuthCallbackFailsWhenStateIsNull() = runBlocking {
        secureStorage.savePkce("test_verifier", "saved_state_secret_123")

        // Passing null state should fail immediately
        val result = authManager.handleOAuthCallback("sample_code", null)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("OAuth state mismatch atau parameter state kosong"))
        assertFalse(msg.contains("saved_state_secret_123")) // Secrets must NOT be leaked
        assertNull(secureStorage.getPkceState()) // PKCE must be cleared
    }

    @Test
    fun testOAuthCallbackFailsOnStateMismatch() = runBlocking {
        secureStorage.savePkce("test_verifier", "saved_state_secret_123")

        // Passing mismatched state should fail immediately
        val result = authManager.handleOAuthCallback("sample_code", "forged_state_999")
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("OAuth state mismatch atau parameter state kosong"))
        assertFalse(msg.contains("saved_state_secret_123")) // Secrets must NOT be leaked
        assertFalse(msg.contains("forged_state_999"))
        assertNull(secureStorage.getPkceState()) // PKCE must be cleared
    }

    // ========================================================================
    // 4. Backup Rules Verification
    // ========================================================================

    @Test
    fun testBackupRulesExcludeSecurePrefs() {
        val backupRulesFile = listOf(
            File("src/main/res/xml/backup_rules.xml"),
            File("app/src/main/res/xml/backup_rules.xml")
        ).firstOrNull { it.exists() }
        assertNotNull("backup_rules.xml must exist", backupRulesFile)
        val content = backupRulesFile!!.readText()
        assertTrue(content.contains("canim_mal_secure_prefs.xml"))
        assertTrue(content.contains("canim_mal_prefs.xml"))
    }

    @Test
    fun testDataExtractionRulesExcludeSecurePrefs() {
        val extractionRulesFile = listOf(
            File("src/main/res/xml/data_extraction_rules.xml"),
            File("app/src/main/res/xml/data_extraction_rules.xml")
        ).firstOrNull { it.exists() }
        assertNotNull("data_extraction_rules.xml must exist", extractionRulesFile)
        val content = extractionRulesFile!!.readText()
        assertTrue(content.contains("canim_mal_secure_prefs.xml"))
        assertTrue(content.contains("canim_mal_prefs.xml"))
    }
}
