// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0
package net.amiantos.lurker

/** What to do with an incoming message after checking the ignore rules. */
data class IgnoreOutcome(
    val drop: Boolean = false,
    val suppressHighlight: Boolean = false,
    val suppressNotify: Boolean = false,
    val suppressUnread: Boolean = false,
)

/**
 * Client-side ignore matcher for direct-IRC mode (the Lurker server does this
 * server-side and stamps `fromIgnored`; here we mirror it). irssi-style:
 *  - a rule matches when its `mask` glob-matches the sender hostmask AND its
 *    network scope (null = all) AND channel scope (null/empty = all) AND, if set,
 *    its text `pattern` all match.
 *  - hard levels (PUBLIC/MSGS/NOTICES/ACTIONS, or ALL) → drop; modifier levels
 *    NOHIGHLIGHT / NONOTIFY / NOUNREAD → soften instead of drop.
 *  - an `isExcept` rule that matches cancels the ignore (a whitelist).
 */
object IgnoreMatch {

    /** The "hard" level token this message qualifies for. */
    private fun levelFor(type: String, isDm: Boolean): String? = when (type) {
        "message" -> if (isDm) "MSGS" else "PUBLIC"
        "notice" -> "NOTICES"
        "action" -> "ACTIONS"
        else -> null
    }

    fun evaluate(
        rules: List<IgnoreRule>,
        networkId: Int?,
        channel: String?, // null for a DM
        isDm: Boolean,
        hostmask: String,
        type: String,
        text: String,
    ): IgnoreOutcome {
        val hard = levelFor(type, isDm)
        var drop = false
        var noHighlight = false
        var noNotify = false
        var noUnread = false
        var excepted = false

        for (r in rules) {
            if (!scopeMatches(r, networkId, channel)) continue
            if (r.mask != null && r.mask.isNotBlank() && !maskMatches(r.mask, hostmask)) continue
            if (!patternMatches(r, text)) continue
            val levels = r.levels.map { it.uppercase() }
            val all = levels.isEmpty() || "ALL" in levels
            val hitsHard = all || (hard != null && hard in levels)
            if (r.isExcept) {
                if (hitsHard) excepted = true
                continue
            }
            if (hitsHard) drop = true
            if (all || "NOHIGHLIGHT" in levels) noHighlight = true
            if (all || "NONOTIFY" in levels) noNotify = true
            if (all || "NOUNREAD" in levels) noUnread = true
        }
        if (excepted) return IgnoreOutcome() // whitelist wins
        return IgnoreOutcome(drop, noHighlight, noNotify, noUnread)
    }

    private fun scopeMatches(r: IgnoreRule, networkId: Int?, channel: String?): Boolean {
        if (r.networkId != null && r.networkId != networkId) return false
        val chans = r.channels
        if (chans.isNullOrEmpty()) return true
        if (channel == null) return false // channel-scoped rule can't match a DM
        return chans.any { it.equals(channel, ignoreCase = true) }
    }

    private fun patternMatches(r: IgnoreRule, text: String): Boolean {
        val p = r.pattern?.takeIf { it.isNotBlank() } ?: return true
        return when (r.patternKind.lowercase()) {
            "full" -> text.equals(p, ignoreCase = true)
            "regex" -> runCatching { Regex(p, RegexOption.IGNORE_CASE).containsMatchIn(text) }.getOrDefault(false)
            else -> text.contains(p, ignoreCase = true) // substr
        }
    }

    /** IRC glob match (`*` any run, `?` one char), case-insensitive, whole-string.
     *  A bare mask with no !/@ is treated as `mask!*@*` (nick-only ignore). */
    fun maskMatches(mask: String, hostmask: String): Boolean {
        val full = if ('!' in mask || '@' in mask) mask else "$mask!*@*"
        val re = buildString {
            append("(?i)^")
            for (c in full) when (c) {
                '*' -> append(".*")
                '?' -> append('.')
                else -> append(Regex.escape(c.toString()))
            }
            append('$')
        }
        return runCatching { Regex(re).matches(hostmask) }.getOrDefault(false)
    }
}
