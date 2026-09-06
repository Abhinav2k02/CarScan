package com.carscan.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---- Palette (matches the Figma dark design) ----
val Bg = Color(0xFF0D0D0D)
val Surface = Color(0xFF1A1A1C)
val SurfaceAlt = Color(0xFF242427)
val Accent = Color(0xFFF5C518)
val TextPrimary = Color(0xFFFFFFFF)
val TextMuted = Color(0xFF9A9A9E)
val Good = Color(0xFF4CAF50)
val Warn = Color(0xFFFF9800)
val Bad = Color(0xFFE53935)

private val CarScanColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    secondary = Accent,
    onSecondary = Color.Black,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceAlt,
    onSurfaceVariant = TextMuted,
    error = Bad,
    outline = SurfaceAlt
)

@Composable
fun CarScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CarScanColors,
        typography = Typography(),
        content = content
    )
}
