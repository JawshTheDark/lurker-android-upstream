// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.content.Context
import androidx.core.content.edit

/**
 * Small persistent store for the session. The prototype held the token in
 * process memory, so every launch meant signing in again; keeping it here (in
 * app-private SharedPreferences) is what lets the app resume silently.
 *
 * The bearer *is* the session token — treat it like a password. It lives in
 * app-private storage, unreadable by other apps on a non-rooted device; moving
 * it into EncryptedSharedPreferences / the Keystore is the obvious next
 * hardening step and is noted in the README.
 */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("lurker", Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = sp.getString(KEY_SERVER, null)
        set(v) = sp.edit { putString(KEY_SERVER, v) }

    var username: String?
        get() = sp.getString(KEY_USER, null)
        set(v) = sp.edit { putString(KEY_USER, v) }

    var token: String?
        get() = sp.getString(KEY_TOKEN, null)
        set(v) = sp.edit { putString(KEY_TOKEN, v) }

    val hasSession: Boolean get() = !token.isNullOrEmpty() && !serverUrl.isNullOrEmpty()

    /** Selected app theme id ("light" | "dark" | "oled"); null = default. */
    var theme: String?
        get() = sp.getString("theme", null)
        set(value) = sp.edit { putString("theme", value) }

    /** Persist a fresh session after a successful token mint. */
    fun saveSession(server: String, username: String, token: String) {
        sp.edit {
            putString(KEY_SERVER, server)
            putString(KEY_USER, username)
            putString(KEY_TOKEN, token)
        }
    }

    /** Drop only the token on sign-out; server URL and username prefill re-login. */
    fun clearSession() {
        sp.edit { remove(KEY_TOKEN) }
    }

    private companion object {
        const val KEY_SERVER = "serverUrl"
        const val KEY_USER = "username"
        const val KEY_TOKEN = "token"
    }
}
