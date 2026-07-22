// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

/**
 * mIRC control-code parsing and URL detection.
 *
 * IRC carries inline formatting as C0 control bytes (bold , color ,
 * etc). The prototype rendered them literally, so text arrived peppered with
 * unprintable characters and raw color codes. This turns a raw line into styled
 * spans plus the plain visible text, which the Compose layer paints into an
 * AnnotatedString.
 *
 * The parser here is deliberately pure — no Android/Compose types — so the
 * span-splitting and palette logic can be unit-tested on the JVM. The Compose
 * bridge lives in MircText.kt.
 */

/**
 * Control-code strings for COMPOSING formatted text (the parser's constants are
 * chars and private; senders want strings to splice into drafts). Color indices
 * are written zero-padded ("04") so a following digit can't be swallowed.
 */
object Fmt {
    const val BOLD = "\u0002"
    const val ITALIC = "\u001D"
    const val UNDERLINE = "\u001F"
    const val STRIKE = "\u001E"
    const val MONO = "\u0011"
    const val COLOR = "\u0003"
    const val RESET = "\u000F"

    fun color(index: Int): String = COLOR + "%02d".format(index)

    /** Foreground + background pair: "FF,BB". mIRC requires a fg with bg. */
    fun colorPair(fg: Int, bg: Int): String = COLOR + "%02d,%02d".format(fg, bg)

    /**
     * Which toggle formats are "open" at [cursor] — i.e. text typed there would
     * carry them. Each toggle char flips its own state; RESET clears all. Drives
     * the composer's active-button highlight so you can SEE you're typing bold
     * (a #lurker-spooky ask — the control codes are invisible otherwise).
     */
    fun openTogglesAt(text: String, cursor: Int): Set<String> {
        var bold = false; var italic = false; var underline = false; var strike = false; var mono = false
        for (i in 0 until cursor.coerceIn(0, text.length)) {
            when (text[i]) {
                BOLD[0] -> bold = !bold
                ITALIC[0] -> italic = !italic
                UNDERLINE[0] -> underline = !underline
                STRIKE[0] -> strike = !strike
                MONO[0] -> mono = !mono
                RESET[0] -> { bold = false; italic = false; underline = false; strike = false; mono = false }
            }
        }
        return buildSet {
            if (bold) add(BOLD); if (italic) add(ITALIC); if (underline) add(UNDERLINE)
            if (strike) add(STRIKE); if (mono) add(MONO)
        }
    }
}

/** A run of text sharing one visual style. `fg`/`bg` are ARGB ints or null. */
data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val mono: Boolean = false,
    val fg: Int? = null,
    val bg: Int? = null,
)

object Mirc {
    private const val BOLD = '\u0002'
    private const val COLOR = '\u0003'
    private const val HEX = '\u0004'
    private const val RESET = '\u000F'
    private const val REVERSE = '\u0016'
    private const val ITALIC = '\u001D'
    private const val STRIKE = '\u001E'
    private const val UNDERLINE = '\u001F'
    private const val MONO = '\u0011'

    // The 99-entry mIRC palette (indices 0..98). 0..15 are the classic colors
    // every client agrees on; 16..98 are the extended table added later. 99 is
    // "default" (rendered as no override). ARGB, fully opaque.
    private val PALETTE: IntArray = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF00007F.toInt(), 0xFF009300.toInt(),
        0xFFFF0000.toInt(), 0xFF7F0000.toInt(), 0xFF9C009C.toInt(), 0xFFFC7F00.toInt(),
        0xFFFFFF00.toInt(), 0xFF00FC00.toInt(), 0xFF009393.toInt(), 0xFF00FFFF.toInt(),
        0xFF0000FC.toInt(), 0xFFFF00FF.toInt(), 0xFF7F7F7F.toInt(), 0xFFD2D2D2.toInt(),
        // 16..27
        0xFF470000.toInt(), 0xFF472100.toInt(), 0xFF474700.toInt(), 0xFF324700.toInt(),
        0xFF004700.toInt(), 0xFF00472C.toInt(), 0xFF004747.toInt(), 0xFF002747.toInt(),
        0xFF000047.toInt(), 0xFF2E0047.toInt(), 0xFF470047.toInt(), 0xFF47002A.toInt(),
        // 28..39
        0xFF740000.toInt(), 0xFF743A00.toInt(), 0xFF747400.toInt(), 0xFF517400.toInt(),
        0xFF007400.toInt(), 0xFF007449.toInt(), 0xFF007474.toInt(), 0xFF004074.toInt(),
        0xFF000074.toInt(), 0xFF4B0074.toInt(), 0xFF740074.toInt(), 0xFF740045.toInt(),
        // 40..51
        0xFFB50000.toInt(), 0xFFB56300.toInt(), 0xFFB5B500.toInt(), 0xFF7DB500.toInt(),
        0xFF00B500.toInt(), 0xFF00B571.toInt(), 0xFF00B5B5.toInt(), 0xFF0063B5.toInt(),
        0xFF0000B5.toInt(), 0xFF7500B5.toInt(), 0xFFB500B5.toInt(), 0xFFB5006B.toInt(),
        // 52..63
        0xFFFF0000.toInt(), 0xFFFF8C00.toInt(), 0xFFFFFF00.toInt(), 0xFFB2FF00.toInt(),
        0xFF00FF00.toInt(), 0xFF00FFA0.toInt(), 0xFF00FFFF.toInt(), 0xFF008CFF.toInt(),
        0xFF0000FF.toInt(), 0xFFA500FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0098.toInt(),
        // 64..75
        0xFFFF5959.toInt(), 0xFFFFB459.toInt(), 0xFFFFFF71.toInt(), 0xFFCFFF60.toInt(),
        0xFF6FFF6F.toInt(), 0xFF65FFC9.toInt(), 0xFF6DFFFF.toInt(), 0xFF59B4FF.toInt(),
        0xFF5959FF.toInt(), 0xFFC459FF.toInt(), 0xFFFF66FF.toInt(), 0xFFFF59BC.toInt(),
        // 76..87
        0xFFFF9C9C.toInt(), 0xFFFFD39C.toInt(), 0xFFFFFF9C.toInt(), 0xFFE2FF9C.toInt(),
        0xFF9CFF9C.toInt(), 0xFF9CFFDB.toInt(), 0xFF9CFFFF.toInt(), 0xFF9CD3FF.toInt(),
        0xFF9C9CFF.toInt(), 0xFFDB9CFF.toInt(), 0xFFFF9CFF.toInt(), 0xFFFF94D3.toInt(),
        // 88..98
        0xFF000000.toInt(), 0xFF131313.toInt(), 0xFF282828.toInt(), 0xFF363636.toInt(),
        0xFF4D4D4D.toInt(), 0xFF656565.toInt(), 0xFF818181.toInt(), 0xFF9F9F9F.toInt(),
        0xFFBCBCBC.toInt(), 0xFFE2E2E2.toInt(), 0xFFFFFFFF.toInt(),
    )

    /** Palette lookup; null for 99 ("default") or out-of-range. */
    fun color(index: Int): Int? = if (index in PALETTE.indices) PALETTE[index] else null

    /**
     * A readable default TEXT color (palette index 0=white or 1=black) for use
     * on top of background color [bg] — used when the user picks a fill without
     * having picked a text color first.
     */
    fun contrastIndex(bg: Int): Int {
        val c = color(bg) ?: return 0
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return if ((r * 299 + g * 587 + b * 114) / 1000 > 140) 1 else 0
    }

    /**
     * Split a raw IRC line into styled spans. Control codes are consumed and
     * never appear in any span's `text`. Toggling codes flip their attribute;
     *  resets everything;  with no digits clears color.
     */
    fun parse(input: String): List<Span> {
        val spans = mutableListOf<Span>()
        val cur = StringBuilder()
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        var mono = false
        var fg: Int? = null
        var bg: Int? = null

        fun flush() {
            if (cur.isEmpty()) return
            spans.add(Span(cur.toString(), bold, italic, underline, strike, mono, fg, bg))
            cur.setLength(0)
        }

        var i = 0
        while (i < input.length) {
            when (val c = input[i]) {
                BOLD -> { flush(); bold = !bold; i++ }
                ITALIC -> { flush(); italic = !italic; i++ }
                UNDERLINE -> { flush(); underline = !underline; i++ }
                STRIKE -> { flush(); strike = !strike; i++ }
                MONO -> { flush(); mono = !mono; i++ }
                REVERSE -> { flush(); val t = fg; fg = bg; bg = t; i++ }
                RESET -> {
                    flush()
                    bold = false; italic = false; underline = false; strike = false; mono = false
                    fg = null; bg = null; i++
                }
                COLOR -> {
                    flush()
                    i++
                    // Up to two digits for fg, optional ",bg" with up to two digits.
                    val fgStr = StringBuilder()
                    while (i < input.length && input[i].isDigit() && fgStr.length < 2) {
                        fgStr.append(input[i]); i++
                    }
                    if (fgStr.isEmpty()) {
                        // Bare color code clears colors.
                        fg = null; bg = null
                    } else {
                        fg = color(fgStr.toString().toInt())
                        if (i < input.length && input[i] == ',' &&
                            i + 1 < input.length && input[i + 1].isDigit()
                        ) {
                            i++ // consume comma
                            val bgStr = StringBuilder()
                            while (i < input.length && input[i].isDigit() && bgStr.length < 2) {
                                bgStr.append(input[i]); i++
                            }
                            bg = color(bgStr.toString().toInt())
                        }
                    }
                }
                HEX -> {
                    flush()
                    i++
                    // RRGGBB (6 hex digits). Bare code clears fg.
                    val hex = StringBuilder()
                    while (i < input.length && hex.length < 6 && input[i].isHex()) {
                        hex.append(input[i]); i++
                    }
                    fg = if (hex.length == 6) (0xFF000000.toInt() or hex.toString().toInt(16)) else null
                }
                else -> { cur.append(c); i++ }
            }
        }
        flush()
        return spans
    }

    private fun Char.isHex(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    // URL detection over already-stripped plain text. Kept conservative: http(s)
    // schemes only, stopping at whitespace and angle/quote delimiters.
    private val URL_REGEX =
        Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** The URL strings within [text] (single regex source, shared with rendering). */
    fun urls(text: String): List<String> = findUrls(text).map { text.substring(it) }

    /** Character ranges of URLs within [text], trailing sentence punctuation trimmed. */
    fun findUrls(text: String): List<IntRange> =
        URL_REGEX.findAll(text).map { m ->
            var end = m.range.last
            while (end > m.range.first && text[end] in ".,!?:;\"'") {
                end--
            }
            // Drop an unbalanced trailing ')' or ']' (common after "(see http://x)").
            while (end > m.range.first && (text[end] == ')' || text[end] == ']')) {
                val sub = text.substring(m.range.first..end)
                val open = if (text[end] == ')') '(' else '['
                if (sub.count { it == open } >= sub.count { it == text[end] }) break
                end--
            }
            m.range.first..end
        }.toList()

    /**
     * If one mIRC background color paints (nearly) the whole message, return it
     * so the bubble itself can take that color — a fully-highlighted line reads
     * as a colored bubble, not as colored stripes floating in a gray one.
     * Partial highlights (< 85% coverage) return null and stay inline.
     */
    fun wholeMessageBg(input: String): Int? {
        val spans = parse(input)
        var total = 0
        val coverage = HashMap<Int, Int>()
        for (span in spans) {
            val visible = span.text.count { !it.isWhitespace() }
            if (visible == 0) continue
            total += visible
            span.bg?.let { coverage[it] = (coverage[it] ?: 0) + visible }
        }
        if (total == 0) return null
        val (bg, covered) = coverage.maxByOrNull { it.value } ?: return null
        return if (covered * 100 >= total * 85) bg else null
    }

    /** Strip all control codes without styling — used for notifications/previews. */
    fun strip(input: String): String = buildString {
        parse(input).forEach { append(it.text) }
    }
}
