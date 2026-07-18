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
    }
}

private fun decorationOf(underline: Boolean, strike: Boolean): TextDecoration? = when {
    underline && strike ->
        TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
    underline -> TextDecoration.Underline
    strike -> TextDecoration.LineThrough
    else -> null
}
