// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker.ui.theme

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Per-nick label colors, iOS-system-palette flavored. A nick hashes to a stable
// slot so the same person is the same color everywhere, including across runs.
private val NICK_COLORS = listOf(
    Color(0xFFFF9F0A), // orange
    Color(0xFF32ADE6), // cyan
    Color(0xFF30D158), // green
    Color(0xFFFF375F), // pink
    Color(0xFFBF5AF2), // purple
    Color(0xFFFFD60A), // yellow
    Color(0xFF64D2FF), // sky
    Color(0xFFFF6482), // rose
    Color(0xFFAC8E68), // tan
    Color(0xFF66D4CF), // teal
)

fun nickColor(nick: String): Color {
    if (nick.isEmpty()) return NICK_COLORS[0]
    // Case-folded stable hash so Nick and nick collide on purpose.
    var h = 0
    for (c in nick.lowercase()) h = (h * 31 + c.code) and 0x7FFFFFFF
    return NICK_COLORS[h % NICK_COLORS.size]
}

private val TIME_FORMAT_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val TIME_FORMAT_24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** ISO timestamp -> "12:45 PM" (or "12:45" when [Ui.clock24h]) in the device
 *  zone; null if unparseable. Reads Ui.clock24h so timestamps recompose live
 *  when the toggle flips. */
fun formatTime(iso: String?): String? = try {
    iso?.let {
        val fmt = if (Ui.clock24h) TIME_FORMAT_24 else TIME_FORMAT_12
        fmt.format(Instant.parse(it).atZone(ZoneId.systemDefault()))
    }
} catch (_: Exception) {
    null
}
