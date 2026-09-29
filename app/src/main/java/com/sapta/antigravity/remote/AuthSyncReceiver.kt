package com.sapta.antigravity.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast

/**
 * AuthSyncReceiver
 *
 * Hardened internal broadcast receiver for local session synchronization.
 * Unexported (exported=false) in AndroidManifest.xml to block unauthorized external invocation.
 */
class AuthSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SYNC_AUTH) {
            val cookiePayload = intent.getStringExtra("cookies")
            if (cookiePayload.isNullOrBlank()) {
                Log.w(TAG, "Received empty or null cookie payload")
                return
            }

            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                val cookieList = cookiePayload.split(";").map { it.trim() }
                for (cookie in cookieList) {
                    if (cookie.isNotEmpty() && cookie.contains("=")) {
                        cookieManager.setCookie(
                            "https://antigravity.google.com",
                            "$cookie; Domain=.google.com; Path=/; Secure; HttpOnly"
                        )
                        cookieManager.setCookie(
                            "https://accounts.google.com",
                            "$cookie; Domain=.google.com; Path=/; Secure; HttpOnly"
                        )
                    }
                }
                cookieManager.flush()
                Toast.makeText(context, "Session synced", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply cookie payload: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "AuthSyncReceiver"
        const val ACTION_SYNC_AUTH = "com.sapta.antigravity.SYNC_AUTH"
    }
}
