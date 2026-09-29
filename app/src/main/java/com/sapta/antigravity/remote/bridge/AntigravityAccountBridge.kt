package com.sapta.antigravity.remote.bridge

import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface

/**
 * AntigravityAccountBridge
 *
 * Secure native-to-web bridge handling Google Account profile extraction
 * and hardware telemetry. Enforces origin checks and strict SSRF domain validation.
 */
class AntigravityAccountBridge(
    private val originProvider: () -> String?,
    private val listener: BridgeEventListener
) {

    interface BridgeEventListener {
        fun onAccountUpdated(name: String, email: String, avatarUrl: String, isPro: Boolean)
        fun onTelemetryUpdated(ram: String, ssd: String, battery: String)
    }

    @JavascriptInterface
    fun onAccountInfoExtracted(name: String?, email: String?, avatarUrl: String?, isPro: Boolean) {
        val currentUrl = originProvider() ?: ""
        if (!isGoogleOrigin(currentUrl)) {
            Log.w(TAG, "Rejected account extraction from unverified origin: $currentUrl")
            return
        }

        val cleanName = name?.trim()?.take(80) ?: ""
        val cleanEmail = if (!email.isNullOrBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            email.trim()
        } else {
            ""
        }

        val safeAvatarUrl = if (!avatarUrl.isNullOrBlank() && isValidGoogleAvatarUrl(avatarUrl)) {
            avatarUrl.trim()
        } else {
            ""
        }

        listener.onAccountUpdated(
            name = cleanName,
            email = cleanEmail,
            avatarUrl = safeAvatarUrl,
            isPro = isPro
        )
    }

    @JavascriptInterface
    fun onTelemetryExtracted(ram: String?, ssd: String?, battery: String?) {
        val currentUrl = originProvider() ?: ""
        if (!isGoogleOrigin(currentUrl)) {
            Log.w(TAG, "Rejected telemetry from unverified origin: $currentUrl")
            return
        }

        listener.onTelemetryUpdated(
            ram = ram?.take(40) ?: "",
            ssd = ssd?.take(40) ?: "",
            battery = battery?.take(40) ?: ""
        )
    }

    private fun isGoogleOrigin(url: String): Boolean {
        return url.startsWith("https://antigravity.google.com/") ||
                url.startsWith("https://accounts.google.com/")
    }

    private fun isValidGoogleAvatarUrl(urlStr: String): Boolean {
        return try {
            val uri = Uri.parse(urlStr)
            val host = uri.host?.lowercase() ?: ""
            uri.scheme == "https" && (
                host.endsWith(".googleusercontent.com") ||
                host.endsWith(".ggpht.com")
            )
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "AccountBridge"
        const val JAVASCRIPT_NAME = "AntigravityNativeBridge"
    }
}
