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

    /** Which backend to run: "lurker" (WebSocket to a Lurker server) or "direct"
     *  (raw IRC / bouncer via KICL). null = first run → mode picker. */
    var clientMode: String?
        get() = sp.getString("clientMode", null)
        // commit synchronously — the mode picker kills the process right after
        // setting this, and an async apply() would be lost before it flushes.
        set(value) = sp.edit(commit = true) { putString("clientMode", value) }

    /** Selected app theme id ("light" | "dark" | "oled"); null = default. */
    var theme: String?
        get() = sp.getString("theme", null)
        set(value) = sp.edit { putString("theme", value) }

    /** Auto-embed image/video links as inline thumbnails in chat (default on). */
    var inlineMedia: Boolean
        get() = sp.getBoolean("inlineMedia", true)
        set(value) = sp.edit { putBoolean("inlineMedia", value) }

    /** Device-local sp added to the synced chat font size (0 = server default). */
    var chatTextScale: Int
        get() = sp.getInt("chatTextScale", 0)
        set(value) = sp.edit { putInt("chatTextScale", value) }

    /** Show message timestamps in 24-hour time (default off = 12-hour). */
    var clock24h: Boolean
        get() = sp.getBoolean("clock24h", false)
        set(value) = sp.edit { putBoolean("clock24h", value) }

    /** Highlight-message bubble colour (ARGB int). 0 = theme default (gold). */
    var highlightColor: Int
        get() = sp.getInt("highlightColor", 0)
        set(value) = sp.edit { putInt("highlightColor", value) }

    /** Buffer keys known to carry E2E — drives the sidebar lock before a buffer's
     *  history is loaded. (SharedPreferences returns an unmodifiable set; copy.) */
    var e2eBuffers: Set<String>
        get() = sp.getStringSet("e2eBuffers", emptySet())?.toSet() ?: emptySet()
        set(value) = sp.edit { putStringSet("e2eBuffers", value) }

    /** Require a biometric / device-credential unlock to open the app (default off). */
    var biometricLock: Boolean
        get() = sp.getBoolean("biometricLock", false)
        set(value) = sp.edit { putBoolean("biometricLock", value) }

    /** Require a biometric unlock to open an encrypted (E2E) channel; re-locks when
     *  the app is backgrounded (amiantos's idea). Independent of the app-open lock. */
    var e2eBiometricLock: Boolean
        get() = sp.getBoolean("e2eBiometricLock", false)
        set(value) = sp.edit { putBoolean("e2eBiometricLock", value) }

    /** Hold the connection open in the background via a foreground service so
     *  highlight/DM notifications keep arriving. Opt-in — off = no persistent
     *  notification, rely on ?since= resume on return (default off). */
    var backgroundConnect: Boolean
        get() = sp.getBoolean("backgroundConnect", false)
        set(value) = sp.edit { putBoolean("backgroundConnect", value) }

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
