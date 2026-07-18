// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.AppTheme
import net.amiantos.lurker.ui.theme.CanvasBlack
import net.amiantos.lurker.ui.theme.Ui

/**
 * The soft, slowly drifting gradient wash behind every screen. Its whole reason
 * to exist is glassmorphism: a blur over a FLAT canvas is invisible (worst on
 * OLED black), so this gives the frosted bars/cards something to diffuse. It
 * registers nothing itself — a caller wraps it in `hazeSource` so panes above
 * blur it. Motion is pure `graphicsLayer`-free Canvas redraw on animated offsets
 * (cheap; a handful of radial gradients), and OLED keeps it near-invisible.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val theme = Ui.theme
    // Per-theme intensity: OLED stays essentially black; Dark/Light get real
    // color so the blur has contrast to work with.
    val alpha = when (theme) {
        AppTheme.Oled -> 0.05f
        AppTheme.Dark -> 0.16f
        AppTheme.Light -> 0.05f // subtle on white — the green/warm wash read "wonky"
    }
    val blobs = when (theme) {
        // Light: cool tones only (blue/lavender/sky) so white stays clean, not greenish.
        AppTheme.Light -> listOf(AccentBlue, Color(0xFFBF5AF2), Color(0xFF64D2FF))
        else -> listOf(AccentBlue, Color(0xFFBF5AF2), Color(0xFF32ADE6))
    }

    val t = rememberInfiniteTransition(label = "ambient")
    // Three independent slow drifts (different periods so they never sync up).
    val d1 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Reverse), label = "d1")
    val d2 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(85_000, easing = LinearEasing), RepeatMode.Reverse), label = "d2")
    val d3 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(110_000, easing = LinearEasing), RepeatMode.Reverse), label = "d3")

    Canvas(modifier.fillMaxSize().background(CanvasBlack)) {
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.7f
        fun blob(color: Color, cx: Float, cy: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        blob(blobs[0], w * (0.15f + 0.20f * d1), h * (0.12f + 0.10f * d2))
        blob(blobs[1], w * (0.85f - 0.20f * d2), h * (0.40f + 0.15f * d3))
        blob(blobs[2], w * (0.30f + 0.25f * d3), h * (0.88f - 0.12f * d1))
    }
}
