package dev.zed.assistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Monochrome identity: true black canvas, white accents, frosted surfaces.
val ZedBlack = Color(0xFF000000)
val ZedWhite = Color(0xFFFFFFFF)
val ZedMuted = Color(0xFF9C9C9C)
val ZedDim = Color(0xFF6B6B6B)
val ZedBackground = ZedBlack

val ZedGlassFill = Color(0x0EFFFFFF)
val ZedGlassFillStrong = Color(0x14FFFFFF)
val ZedGlassBorder = Color(0x1FFFFFFF)
val ZedGlassBorderStrong = Color(0x33FFFFFF)

private val ZedColorScheme = darkColorScheme(
    primary = ZedWhite,
    onPrimary = ZedBlack,
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = ZedWhite,
    secondary = Color(0xFFE4E4E4),
    onSecondary = ZedBlack,
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = ZedWhite,
    tertiary = Color(0xFFC4C4C4),
    onTertiary = ZedBlack,
    tertiaryContainer = Color(0xFF2E2E2E),
    onTertiaryContainer = ZedWhite,
    background = ZedBlack,
    onBackground = ZedWhite,
    surface = Color(0xFF0A0A0A),
    onSurface = ZedWhite,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = ZedMuted,
    surfaceContainerLowest = ZedBlack,
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1B1B1B),
    surfaceContainerHighest = Color(0xFF232323),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF242424),
    error = Color(0xFFFF7A7A),
    onError = ZedBlack,
    errorContainer = Color(0xFF3B1414),
    onErrorContainer = Color(0xFFFFD9D9),
    scrim = ZedBlack
)

private val ZedShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

private val base = Typography()

private val ZedTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = (-0.6).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = base.bodyLarge.copy(letterSpacing = 0.1.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelMedium = base.labelMedium.copy(letterSpacing = 0.3.sp),
    labelSmall = base.labelSmall.copy(letterSpacing = 0.9.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun ZedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZedColorScheme,
        typography = ZedTypography,
        shapes = ZedShapes,
        content = content
    )
}
