// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0
package net.amiantos.lurker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.amiantos.lurker.ui.theme.TextSecondary

/**
 * One consistent family of hand-drawn icons, replacing the stock emoji (🔍 📷 📁
 * 📝 🔒 ➤ ☰) which render as gaudy multicolour glyphs against the monochrome UI
 * and vary per device font. All are thin-stroked Canvas draws that inherit a
 * [color] and scale to [size] — same visual language as the search magnifier.
 */

private fun DrawScope.stroke(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun SearchGlyph(color: Color = TextSecondary, size: Dp = 19.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val w = s * 0.09f
    val r = s * 0.30f
    val c = Offset(s * 0.40f, s * 0.40f)
    drawCircle(color = color, radius = r, center = c, style = Stroke(width = w))
    val edge = r + w * 0.5f
    drawLine(color, Offset(c.x + edge * 0.72f, c.y + edge * 0.72f), Offset(s * 0.88f, s * 0.88f), w, StrokeCap.Round)
}

@Composable
fun LockGlyph(color: Color = TextSecondary, size: Dp = 16.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val w = s * 0.085f
    // Body: rounded rect.
    drawRoundRect(
        color = color,
        topLeft = Offset(s * 0.24f, s * 0.44f),
        size = Size(s * 0.52f, s * 0.40f),
        cornerRadius = CornerRadius(s * 0.09f),
        style = stroke(w),
    )
    // Shackle: a "U" over the body.
    val shackle = Path().apply {
        moveTo(s * 0.34f, s * 0.46f)
        lineTo(s * 0.34f, s * 0.37f)
        arcTo(Rect(s * 0.34f, s * 0.20f, s * 0.66f, s * 0.52f), 180f, 180f, false)
        lineTo(s * 0.66f, s * 0.46f)
    }
    drawPath(shackle, color, style = stroke(w))
    // Keyhole dot.
    drawCircle(color, radius = s * 0.045f, center = Offset(s * 0.5f, s * 0.62f))
}

@Composable
fun CameraGlyph(color: Color = TextSecondary, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val w = s * 0.08f
    // Viewfinder bump on top.
    drawRoundRect(
        color = color,
        topLeft = Offset(s * 0.34f, s * 0.22f),
        size = Size(s * 0.22f, s * 0.12f),
        cornerRadius = CornerRadius(s * 0.03f),
        style = stroke(w),
    )
    // Body.
    drawRoundRect(
        color = color,
        topLeft = Offset(s * 0.12f, s * 0.32f),
        size = Size(s * 0.76f, s * 0.48f),
        cornerRadius = CornerRadius(s * 0.09f),
        style = stroke(w),
    )
    // Lens.
    drawCircle(color, radius = s * 0.15f, center = Offset(s * 0.5f, s * 0.57f), style = stroke(w))
}

@Composable
fun FolderGlyph(color: Color = TextSecondary, size: Dp = 18.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val w = s * 0.08f
    val p = Path().apply {
        moveTo(s * 0.14f, s * 0.78f)
        lineTo(s * 0.14f, s * 0.34f)
        lineTo(s * 0.40f, s * 0.34f)
        lineTo(s * 0.47f, s * 0.44f)
        lineTo(s * 0.86f, s * 0.44f)
        lineTo(s * 0.86f, s * 0.78f)
        close()
    }
    drawPath(p, color, style = stroke(w))
}

@Composable
fun NoteGlyph(color: Color = TextSecondary, size: Dp = 15.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val w = s * 0.08f
    drawRoundRect(
        color = color,
        topLeft = Offset(s * 0.26f, s * 0.16f),
        size = Size(s * 0.48f, s * 0.68f),
        cornerRadius = CornerRadius(s * 0.06f),
        style = stroke(w),
    )
    listOf(0.36f, 0.50f, 0.64f).forEach { y ->
        drawLine(color, Offset(s * 0.36f, s * y), Offset(s * 0.64f, s * y), w * 0.9f, StrokeCap.Round)
    }
}

/** Three-line list glyph — the member-count button (was ☰). */
@Composable
fun MembersGlyph(color: Color = TextSecondary, size: Dp = 16.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val w = s * 0.11f
    listOf(0.30f, 0.50f, 0.70f).forEach { y ->
        drawLine(color, Offset(s * 0.20f, s * y), Offset(s * 0.80f, s * y), w, StrokeCap.Round)
    }
}

/** A gear — quick Settings access from the chat top bar. */
@Composable
fun SettingsGlyph(color: Color = TextSecondary, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val c = Offset(s / 2f, s / 2f)
    val w = s * 0.085f
    drawCircle(color, radius = s * 0.24f, center = c, style = stroke(w))       // ring
    drawCircle(color, radius = s * 0.085f, center = c, style = stroke(w))      // hub
    for (i in 0 until 8) {                                                     // stubby teeth on the rim
        rotate(i * 45f, c) {
            drawLine(color, Offset(s / 2f, s * 0.12f), Offset(s / 2f, s * 0.24f), s * 0.12f, StrokeCap.Round)
        }
    }
}

/** Filled paper-plane send button (was ➤). */
@Composable
fun SendGlyph(color: Color = TextSecondary, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val plane = Path().apply {
        moveTo(s * 0.14f, s * 0.50f)
        lineTo(s * 0.86f, s * 0.16f)
        lineTo(s * 0.50f, s * 0.86f)
        lineTo(s * 0.43f, s * 0.57f)
        close()
    }
    drawPath(plane, color)
}
