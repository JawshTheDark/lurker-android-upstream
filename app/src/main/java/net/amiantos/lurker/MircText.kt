// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Compose bridge over [Mirc]. Parses a raw IRC line into styled runs, paints
 * them into an AnnotatedString, and overlays clickable link annotations on any
 * URLs. Kept out of Mirc.kt so the parser stays a pure, unit-testable module.
 */
fun mircAnnotated(
    raw: String,
    linkColor: Color,
    onLink: ((String) -> Unit)? = null,
    // Makes #channel mentions tappable (join prompt) when supplied (freakyy85).
    onChannel: ((String) -> Unit)? = null,
): AnnotatedString {
    val spans = Mirc.parse(raw)
    val plain = buildString { spans.forEach { append(it.text) } }
    val urls = Mirc.findUrls(plain)

    return buildAnnotatedString {
        for (span in spans) {
            withStyle(
                SpanStyle(
                    color = span.fg?.let { Color(it) } ?: Color.Unspecified,
                    background = span.bg?.let { Color(it) } ?: Color.Unspecified,
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontFamily = if (span.mono) FontFamily.Monospace else null,
                    textDecoration = decorationOf(span.underline, span.strike),
                ),
            ) { append(span.text) }
        }
        val linkStyle = TextLinkStyles(
            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
        )
        for (r in urls) {
            val url = plain.substring(r)
            val link = if (onLink != null) {
                // Custom routing (in-app viewers); default handling otherwise.
                LinkAnnotation.Url(url, linkStyle) { onLink(url) }
            } else {
                LinkAnnotation.Url(url, linkStyle)
            }
            addLink(link, r.first, r.last + 1)
        }
        if (onChannel != null) {
            val chanStyle = TextLinkStyles(SpanStyle(color = linkColor))
            for (r in findChannelMentions(plain, urls)) {
                val name = plain.substring(r)
                addLink(LinkAnnotation.Clickable(name, chanStyle) { onChannel(name) }, r.first, r.last + 1)
            }
        }
    }
}

private val CHANNEL_TOKEN = Regex("""[#&][^\s,]+""")

/** Trailing punctuation that reads as sentence, not channel: "join #foo." */
private const val CHANNEL_TRAILING = ".,:;!?)\"'>"

/**
 * Ranges in [plain] that look like channel mentions worth offering to join.
 *
 * Skips anything overlapping a [urls] range, so the fragment in
 * `https://host/page#section` doesn't become a channel (and so two link
 * annotations never fight over the same characters), trims trailing sentence
 * punctuation, and requires a letter in the name so an issue reference like
 * "fixed #92" isn't offered as a channel.
 */
internal fun findChannelMentions(plain: String, urls: List<IntRange>): List<IntRange> =
    CHANNEL_TOKEN.findAll(plain).mapNotNull { m ->
        var end = m.range.last + 1
        while (end > m.range.first + 1 && plain[end - 1] in CHANNEL_TRAILING) end--
        val range = m.range.first until end
        val name = plain.substring(range)
        when {
            name.length < 2 -> null
            !name.any { it.isLetter() } -> null
            urls.any { range.first <= it.last && it.first < end } -> null
            else -> range
        }
    }.toList()

private fun decorationOf(underline: Boolean, strike: Boolean): TextDecoration? = when {
    underline && strike ->
        TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
    underline -> TextDecoration.Underline
    strike -> TextDecoration.LineThrough
    else -> null
}
