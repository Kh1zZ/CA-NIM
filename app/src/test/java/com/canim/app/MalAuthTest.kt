package com.canim.app

import android.net.Uri
import com.canim.app.data.repository.MalAuthManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MalAuthTest {

    @Test
    fun testPkceStringGeneration() {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val secureRandom = java.security.SecureRandom()
        val sb = StringBuilder(128)
        for (i in 0 until 128) {
            sb.append(chars[secureRandom.nextInt(chars.length)])
        }
        val verifier = sb.toString()
        assertEquals(128, verifier.length)
        assertTrue(verifier.all { it in chars })
    }

    @Test
    fun testPkceS256ChallengeCalculation() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val challenge = MalAuthManager.generateCodeChallenge(verifier)
        val expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expected, challenge)
        assertEquals(43, challenge.length)
        assertFalse(challenge.contains("="))
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))
    }

    @Test
    fun testAuthorizeUrlFormatForMyAnimeList() {
        val verifier = "test_code_verifier_128_chars_long_enough_for_pkce_specification_testing_purpose_random_characters_abcd_12345_xyz_sample_test"
        val state = "random_state_value_32_chars_long"

        val uri = Uri.parse(MalAuthManager.AUTH_BASE_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", MalAuthManager.CLIENT_ID)
            .appendQueryParameter("code_challenge", verifier)
            .appendQueryParameter("code_challenge_method", "plain")
            .appendQueryParameter("state", state)
            .appendQueryParameter("redirect_uri", MalAuthManager.REDIRECT_URI)
            .build()

        assertEquals("https", uri.scheme)
        assertEquals("myanimelist.net", uri.host)
        assertEquals("code", uri.getQueryParameter("response_type"))
        assertEquals("a4f3b20e6eb04e9daac4d2ea9fb2a45a", uri.getQueryParameter("client_id"))
        assertEquals("canim://oauth/callback", uri.getQueryParameter("redirect_uri"))
        assertEquals("plain", uri.getQueryParameter("code_challenge_method"))
        assertEquals(verifier, uri.getQueryParameter("code_challenge"))
        assertEquals(state, uri.getQueryParameter("state"))
    }
}
