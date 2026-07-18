// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

/**
 * Parse the inline message-search syntax `from:nick in:#channel on:network
 * freetext…` — pure so it's JVM-testable. `from:` may repeat (OR over nicks);
 * a bare `from:` or unknown `word:` token stays in the free text (the server's
 * FTS handles it harmlessly). Mirrors the web's parseSearchQuery.
 */
object Search {
    data class Query(
        val text: String,
        val from: List<String>,
        val inTarget: String,
        val onNetwork: String,
    )

    private val TOKEN = Regex("^(from|in|on):(.+)$", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Query {
        val from = ArrayList<String>()
        var inTarget = ""
        var onNetwork = ""
        val free = ArrayList<String>()
        for (token in raw.trim().split(Regex("\\s+"))) {
            if (token.isEmpty()) continue
            val m = TOKEN.find(token)
            if (m != null) {
                val key = m.groupValues[1].lowercase()
                val value = m.groupValues[2]
                when (key) {
                    "from" -> from.add(value)
                    "in" -> inTarget = value
                    "on" -> onNetwork = value
                }
            } else {
                free.add(token)
            }
        }
        return Query(free.joinToString(" "), from, inTarget, onNetwork)
    }
}
