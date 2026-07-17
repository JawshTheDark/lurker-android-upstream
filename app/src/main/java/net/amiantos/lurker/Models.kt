// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

/** A configured IRC network as the server describes it. */
data class Network(
    val id: Int,
    val name: String,
    val nick: String?,
    val connected: Boolean,
)

/** A conversation: a channel, a DM, or the app-scoped system buffer. */
data class Buffer(
    /** null for the system buffer, which is read-only. */
    val networkId: Int?,
    val target: String,
    val networkName: String,
) {
    val key: String get() = "${networkId ?: "sys"}::$target"
    val isChannel: Boolean get() = Commands.isChannel(target)
    val isSystem: Boolean get() = networkId == null
    val isServerBuffer: Boolean get() = target.startsWith(":server:")

    /** What the UI calls this buffer; raw pseudo-targets get friendly names. */
    val displayName: String get() = if (isServerBuffer) "Server" else target
}

/** A rendered line. System events (join/part/…) carry [system] = true. */
data class Msg(
    val id: Long,
    val type: String,
    val nick: String,
    val text: String,
    val self: Boolean,
    val time: String? = null,
    val system: Boolean = false,
)

/** An inbound DCC file transfer, mirrored from the server's transfer rows. */
data class DccTransfer(
    val id: Int,
    val peerNick: String,
    val filename: String,
    val state: String,
    val received: Long,
    val total: Long,
    val direction: String,
    val error: String?,
) {
    val progress: Float
        get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
    val isPending: Boolean get() = state == "pending_approval"
    val isActive: Boolean
        get() = state in setOf("connecting", "receiving", "stalled", "verifying", "requested")
    val isTerminal: Boolean
        get() = state in setOf("completed", "failed", "rejected", "cancelled")
}

/**
 * One entry from the settings registry (`/api/settings/bootstrap`). The screen
 * renders generically off [type], so the whole registry is editable without the
 * client hard-coding any particular key.
 */
data class SettingOption(
    val key: String,
    val label: String,
    val type: String, // bool | int | string | secret | color | enum | string-list
    val group: String,
    val choices: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
)
