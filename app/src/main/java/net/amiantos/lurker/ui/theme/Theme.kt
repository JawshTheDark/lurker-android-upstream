// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** The app's three looks. OLED is the original pure-black design. */
enum class AppTheme(val id: String, val label: String) {
    Light("light", "Light"),
    Dark("dark", "Dark"),
    Oled("oled", "OLED black"),
    ;

    companion object {
        fun from(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: Oled
    }
}

/** Global theme selector; the Activity seeds it from Prefs at launch. */
object Ui {
    var theme by mutableStateOf(AppTheme.Oled)
}

private data class Palette(
    val canvas: Color,
    val surface: Color, // glass card fill (translucent — canvas ghosts through)
    val raised: Color, // bubbles and raised chrome
    val pill: Color,
    val border: Color, // hairline on glass edges
    val accent: Color,
    val red: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val amber: Color,
    val green: Color,
    val dark: Boolean,
)

// OLED: the original look — pure black, near-solid surfaces (translucency has
// nothing to show through on #000 anyway).
private val OledPalette = Palette(
    canvas = Color(0xFF000000),
    surface = Color(0xF01C1C1E),
    raised = Color(0xFF2C2C2E),
    pill = Color(0xFF3A3A3C),
    border = Color(0x16FFFFFF),
    accent = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF98989F),
    amber = Color(0xFFFFD60A),
    green = Color(0xFF30D158),
    dark = true,
)

// Dark: softened near-black with blue undertones and truly translucent glass.
private val DarkPalette = Palette(
    canvas = Color(0xFF12121C),
    surface = Color(0xB32A2A3A),
    raised = Color(0xD93A3A4C),
    pill = Color(0xFF4A4A5E),
    border = Color(0x22FFFFFF),
    accent = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFA0A0AE),
    amber = Color(0xFFFFD60A),
    green = Color(0xFF30D158),
    dark = true,
)

// Light: iOS-light flavored, frosted white glass on a grey canvas.
private val LightPalette = Palette(
    canvas = Color(0xFFF2F2F7),
    surface = Color(0xC7FFFFFF),
    raised = Color(0xFFE5E5EC),
    pill = Color(0xFFCFCFD8),
    border = Color(0x14000000),
    accent = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF6C6C72),
    amber = Color(0xFF9A6B00), // amber text needs contrast on white glass
    green = Color(0xFF34C759),
    dark = false,
)

private val palette: Palette
    get() = when (Ui.theme) {
        AppTheme.Light -> LightPalette
        AppTheme.Dark -> DarkPalette
        AppTheme.Oled -> OledPalette
    }

// The pre-theming constant names, now theme-backed so every existing usage
// follows the switch. (The names date from the OLED-only era — "CanvasBlack"
// is white in the light theme; renaming them all is churn for another day.)
val CanvasBlack: Color get() = palette.canvas
val SurfaceDark: Color get() = palette.surface
val SurfaceRaised: Color get() = palette.raised
val PillGray: Color get() = palette.pill
val GlassBorder: Color get() = palette.border
val AccentBlue: Color get() = palette.accent
val AlertRed: Color get() = palette.red
val TextPrimary: Color get() = palette.textPrimary
val TextSecondary: Color get() = palette.textSecondary
val NoticeAmber: Color get() = palette.amber
val OnlineGreen: Color get() = palette.green

@Composable
fun LurkerTheme(content: @Composable () -> Unit) {
    val p = palette
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = Color.White,
            secondary = p.textSecondary,
            tertiary = p.amber,
            background = p.canvas,
            onBackground = p.textPrimary,
            surface = p.canvas,
            onSurface = p.textPrimary,
            surfaceVariant = p.raised,
            onSurfaceVariant = p.textPrimary,
            surfaceContainer = p.raised,
            surfaceContainerHigh = p.raised,
            outline = p.textSecondary,
            error = p.red,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = Color.White,
            secondary = p.textSecondary,
            tertiary = p.amber,
            background = p.canvas,
            onBackground = p.textPrimary,
            surface = p.canvas,
            onSurface = p.textPrimary,
            surfaceVariant = p.raised,
            onSurfaceVariant = p.textPrimary,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            outline = p.textSecondary,
            error = p.red,
            onError = Color.White,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
