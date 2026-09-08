package com.canim.app.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Abstraction for network availability checks.
 *
 * Decoupled from [ConnectivityManager] to allow deterministic mocking in unit tests
 * without requiring an Android context or instrumentation runner.
 */
interface NetworkAvailabilityChecker {
    /**
     * Returns true if the device currently has an active network connection
     * with Internet capability. Returns false if offline or if the check fails.
     */
    fun isNetworkAvailable(): Boolean
}

/**
 * Production implementation backed by [ConnectivityManager].
 * Requires ACCESS_NETWORK_STATE permission (already declared in AndroidManifest
 * for existing network operations).
 */
class ConnectivityNetworkChecker(private val context: Context) : NetworkAvailabilityChecker {

    override fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Test-only implementation that always reports the given fixed [isAvailable] state.
 */
class FakeNetworkChecker(private var isAvailable: Boolean = true) : NetworkAvailabilityChecker {
    override fun isNetworkAvailable(): Boolean = isAvailable
    fun setAvailable(available: Boolean) { isAvailable = available }
}
