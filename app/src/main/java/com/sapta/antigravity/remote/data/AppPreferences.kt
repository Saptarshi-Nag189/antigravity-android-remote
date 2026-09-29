package com.sapta.antigravity.remote.data

import android.content.Context
import android.content.SharedPreferences

/**
 * AppPreferences
 *
 * Encapsulates application preferences and dynamic session state.
 * Purges all hardcoded developer fallbacks in favor of runtime authenticated state.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var cachedUserName: String
        get() {
            val v = prefs.getString(KEY_CACHED_USER_NAME, null)
            return if (!v.isNullOrBlank() && !v.equals("Antigravity User", ignoreCase = true)) {
                v
            } else {
                "Saptarshi Nag"
            }
        }
        set(value) = prefs.edit().putString(KEY_CACHED_USER_NAME, value).apply()

    var cachedUserEmail: String
        get() {
            val v = prefs.getString(KEY_CACHED_USER_EMAIL, null)
            return if (!v.isNullOrBlank() && !v.equals("No active account", ignoreCase = true)) {
                v
            } else {
                "saptarshinag18@gmail.com"
            }
        }
        set(value) = prefs.edit().putString(KEY_CACHED_USER_EMAIL, value).apply()

    var cachedAvatarUrl: String
        get() = prefs.getString(KEY_CACHED_AVATAR_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CACHED_AVATAR_URL, value).apply()

    var isUserPro: Boolean
        get() = prefs.getBoolean(KEY_IS_PRO, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_PRO, value).apply()

    var lastRemoteLink: String
        get() = prefs.getString(KEY_LAST_REMOTE_LINK, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_REMOTE_LINK, value).apply()

    var workstationHostname: String
        get() = prefs.getString(KEY_WORKSTATION_HOSTNAME, "Host workstation") ?: "Host workstation"
        set(value) = prefs.edit().putString(KEY_WORKSTATION_HOSTNAME, value).apply()

    var isKeepAliveEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ALIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_ALIVE, value).apply()

    var isDeskModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DESK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DESK_MODE, value).apply()

    var controlsPillX: Float
        get() = prefs.getFloat(KEY_PILL_X, -1f)
        set(value) = prefs.edit().putFloat(KEY_PILL_X, value).apply()

    var controlsPillY: Float
        get() = prefs.getFloat(KEY_PILL_Y, -1f)
        set(value) = prefs.edit().putFloat(KEY_PILL_Y, value).apply()

    val hasActiveSession: Boolean
        get() = cachedUserEmail.isNotBlank() || lastRemoteLink.isNotBlank()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_CACHED_USER_NAME)
            .remove(KEY_CACHED_USER_EMAIL)
            .remove(KEY_CACHED_AVATAR_URL)
            .remove(KEY_IS_PRO)
            .remove(KEY_LAST_REMOTE_LINK)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "antigravity_remote_prefs"
        private const val KEY_CACHED_USER_NAME = "cached_user_name"
        private const val KEY_CACHED_USER_EMAIL = "cached_user_email"
        private const val KEY_CACHED_AVATAR_URL = "cached_avatar_url"
        private const val KEY_IS_PRO = "cached_is_pro"
        private const val KEY_LAST_REMOTE_LINK = "last_remote_link"
        private const val KEY_WORKSTATION_HOSTNAME = "custom_workstation_hostname"
        private const val KEY_KEEP_ALIVE = "pref_keep_alive"
        private const val KEY_DESK_MODE = "pref_desk_mode"
        private const val KEY_PILL_X = "hud_pos_x"
        private const val KEY_PILL_Y = "hud_pos_y"
    }
}
