package dev.zed.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ZedCyan = Color(0xFF35E6FF)
val ZedGold = Color(0xFFFFC24B)
val ZedRed = Color(0xFFFF5470)
val ZedBackground = Color(0xFF04070D)
val ZedSurface = Color(0xFF0B1120)
val ZedSurfaceHigh = Color(0xFF121B2D)
val ZedText = Color(0xFFE6F7FF)
val ZedMuted = Color(0xFF7C8DA6)

private val ZedColorScheme = darkColorScheme(
    primary = ZedCyan,
    onPrimary = Color(0xFF00131A),
    primaryContainer = Color(0xFF0B3B47),
    onPrimaryContainer = ZedText,
    secondary = ZedGold,
    onSecondary = Color(0xFF231700),
    background = ZedBackground,
    onBackground = ZedText,
    surface = ZedSurface,
    onSurface = ZedText,
    surfaceVariant = ZedSurfaceHigh,
    onSurfaceVariant = ZedMuted,
    error = ZedRed,
    onError = Color(0xFF1F0006),
    outline = Color(0xFF243450)
)

@Composable
fun ZedTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ZedColorScheme, content = content)
}
