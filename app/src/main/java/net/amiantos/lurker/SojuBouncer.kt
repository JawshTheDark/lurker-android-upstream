// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0
package net.amiantos.lurker

/** One upstream network advertised by a soju bouncer via soju.im/bouncer-networks. */
data class SojuNetwork(
    val id: String,
    val name: String,
    val host: String?,
    val state: String?, // "connected" | "disconnected" | "connecting"
    val removed: Boolean = false, // BOUNCER NETWORK <id> * means the network was deleted
)

/**
 * Parser for soju's `soju.im/bouncer-networks` control messages. Pure/testable —
 * the DirectIrcBackend wires this to a KICL raw-command listener. The wire shape:
 *   BOUNCER NETWORK <id> <attributes>
 * where <attributes> is `key=value;key=value;…` (soju-escaped), or a bare `*`
 * meaning the network was removed. NOTE: implemented from the spec; the live
 * multiplexing still needs verification against a real soju instance.
 */
object SojuBouncer {

    /** Parse the params of a `BOUNCER NETWORK …` command (params = after "BOUNCER"). */
    fun parseNetworkLine(params: List<String>): SojuNetwork? {
        // params[0] == "NETWORK", params[1] == id, params[2] == attributes
        if (params.size < 3 || !params[0].equals("NETWORK", ignoreCase = true)) return null
        val id = params[1]
        val attrsRaw = params[2]
        if (attrsRaw == "*") return SojuNetwork(id, id, null, null, removed = true)
        val attrs = parseAttrs(attrsRaw)
        return SojuNetwork(
            id = id,
            name = attrs["name"] ?: attrs["host"] ?: id,
            host = attrs["host"],
            state = attrs["state"],
        )
    }

    /** `key=value;key=value` with soju's backslash escaping (\\s space, \\: ; \\\\). */
    fun parseAttrs(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (pair in splitUnescaped(raw, ';')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            if (eq < 0) { out[unescape(pair)] = ""; continue }
            out[unescape(pair.substring(0, eq))] = unescape(pair.substring(eq + 1))
        }
        return out
    }

    private fun splitUnescaped(s: String, delim: Char): List<String> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { cur.append(c).append(s[i + 1]); i += 2; continue }
            if (c == delim) { parts.add(cur.toString()); cur.clear(); i++; continue }
            cur.append(c); i++
        }
        parts.add(cur.toString())
        return parts
    }

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    's' -> out.append(' ')
                    ':' -> out.append(';')
                    '\\' -> out.append('\\')
                    else -> out.append(s[i + 1])
                }
                i += 2
            } else { out.append(c); i++ }
        }
        return out.toString()
    }
}
