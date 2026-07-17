// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The app deliberately ships one look — the iOS-dark design lurker-ios uses —
// rather than following system light/dark or Material You dynamic color. Pure
// black canvas, #1C1C1E cards, iMessage blue for self/actions.
val CanvasBlack = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceRaised = Color(0xFF2C2C2E)
val PillGray = Color(0xFF3A3A3C)
val AccentBlue = Color(0xFF0A84FF)
val AlertRed = Color(0xFFFF453A)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF98989F)
val NoticeAmber = Color(0xFFFFD60A)
val OnlineGreen = Color(0xFF30D158)

private val IosDarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = TextPrimary,
    secondary = TextSecondary,
    tertiary = NoticeAmber,
    background = CanvasBlack,
    onBackground = TextPrimary,
    surface = CanvasBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextPrimary,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceRaised,
    outline = TextSecondary,
    error = AlertRed,
    onError = TextPrimary,
)

@Composable
fun LurkerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IosDarkScheme,
        typography = Typography,
        content = content,
    )
}
