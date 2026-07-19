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
    /** Log severity for :system: and E2E lines (info/warn/error), null elsewhere. */
    val level: String? = null,
    /** True when this message arrived end-to-end encrypted (server-decrypted). */
    val e2e: Boolean = false,
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
    val isSend: Boolean get() = direction == "send"
    val isActive: Boolean
        get() = state in setOf(
            "connecting", "receiving", "stalled", "verifying", "requested",
            "offering", "sending", // outgoing-send states
        )
    val isTerminal: Boolean
        get() = state in setOf("completed", "failed", "rejected", "cancelled")
}

/**
 * A message worth notifying about (a highlight, DM, or notify-always channel),
 * flattened for the Notifier. [target] is the buffer to open on tap.
 */
data class NotifiableEvent(
    val networkId: Int?,
    val target: String,
    val nick: String,
    val text: String,
    val isDm: Boolean,
)

/** One reachable identity (network + nick) for a contact. */
data class ContactTarget(val networkId: Int, val nick: String, val isPrimary: Boolean = false)

/** A friend/contact the server tracks and reports presence for. */
data class Contact(
    val id: Int,
    val displayName: String,
    val notifyOnline: Boolean,
    val targets: List<ContactTarget>,
) {
    /** The identity to open when the contact row is tapped (primary, else first). */
    val primary: ContactTarget? get() = targets.firstOrNull { it.isPrimary } ?: targets.firstOrNull()
}

/** A server-synced custom slash alias: /name expands to expansion with $1..$9 params. */
data class AliasEntry(val id: Int, val name: String, val expansion: String)

/**
 * An irssi-style ignore rule (issue #301). AND-ed dimensions: [mask] (who),
 * [channels] (where), [pattern] (what text), [levels] (which event types + the
 * special modifiers NOHIGHLIGHT/NONOTIFY/NOUNREAD/ALL). [isExcept] inverts it
 * (an un-ignore). [networkId] null = a global rule (every network).
 */
data class IgnoreRule(
    val id: Long,
    val networkId: Int?,
    val mask: String?,
    val channels: List<String>?,
    val pattern: String?,
    val patternKind: String,
    val levels: List<String>,
    val isExcept: Boolean,
) {
    val who: String get() = mask?.takeIf { it.isNotBlank() } ?: "anyone"
    val levelsLabel: String get() = if (levels.isEmpty()) "all" else levels.joinToString(", ") { it.lowercase() }
}

/** A row in the /LIST channel browser. */
data class ChannelListing(val channel: String, val users: Int, val topic: String)

/** One search / highlights result row (decorated DB message: body + createdAt). */
data class SearchResult(
    val id: Long,
    val networkId: Int,
    val target: String,
    val nick: String,
    val body: String,
    val createdAt: String?,
)

/**
 * A channel member from `names` frames. `modes` are IRC mode letters
 * (q owner / a admin / o op / h halfop / v voice); the glyph mapping is
 * client-side, mirroring the web client (the server doesn't read ISUPPORT
 * PREFIX yet).
 */
data class Member(
    val nick: String,
    val modes: List<String> = emptyList(),
    val away: Boolean = false,
    val host: String? = null,
) {
    val prefix: String
        get() = when {
            "q" in modes -> "~"
            "a" in modes -> "&"
            "o" in modes -> "@"
            "h" in modes -> "%"
            "v" in modes -> "+"
            else -> ""
        }

    /** Sort rank: owner first, plain members last. */
    val rank: Int
        get() = when {
            "q" in modes -> 0
            "a" in modes -> 1
            "o" in modes -> 2
            "h" in modes -> 3
            "v" in modes -> 4
            else -> 5
        }

    /** Can this member moderate (op-gated menu actions)? Mirrors the web's MODERATE_MODES. */
    val canModerate: Boolean get() = modes.any { it == "q" || it == "a" || it == "o" || it == "h" }

    /** Ban mask, host-based when the host is known (mirrors the web client). */
    val banMask: String get() = host?.let { "*!*@$it" } ?: "$nick!*@*"
}

/**
 * A network row as `/api/networks` REST returns it (config, not live state —
 * live connected/nick state rides the WS snapshot into [LurkerClient.networks]).
 */
data class NetworkConfig(
    val id: Int,
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val nick: String,
    val username: String?,
    val realname: String?,
    val autoconnect: Boolean,
    val hasPassword: Boolean,
    val hasSaslPassword: Boolean,
    val saslAccount: String?,
    val blocked: Boolean,
)

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
    val category: String = "",
    val description: String = "",
    val default: Any? = null,
    val choices: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
)
