package com.kartik.pulse.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AF7C6),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF17382B),
    onPrimaryContainer = Color(0xFFB7F9D5),
    background = Color(0xFF101115),
    onBackground = Color(0xFFF0F1F3),
    surface = Color(0xFF191B20),
    onSurface = Color(0xFFF0F1F3),
    surfaceVariant = Color(0xFF25282E),
    onSurfaceVariant = Color(0xFFB8BBC3),
    outline = Color(0xFF474A52)
)

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = Typography(), content = content)
}
