package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DuskLoreDarkColorScheme = darkColorScheme(
    primary = CrimsonEmber,
    onPrimary = ParchmentInk,
    primaryContainer = CrimsonContainer,
    onPrimaryContainer = OnCrimsonContainer,
    secondary = LanternAmber,
    onSecondary = ObsidianCanvas,
    secondaryContainer = LanternContainer,
    onSecondaryContainer = OnLanternContainer,
    tertiary = SpectralTeal,
    onTertiary = ObsidianCanvas,
    tertiaryContainer = SpectralContainer,
    onTertiaryContainer = OnSpectralContainer,
    background = ObsidianCanvas,
    onBackground = ParchmentInk,
    surface = CharcoalSurface,
    onSurface = ParchmentInk,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = AshSecondary,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = CharcoalElevated,
    surfaceContainerHigh = CharcoalHighest,
    outline = HairlineOutline,
    outlineVariant = SubtleOutline,
    error = ErrorCrimson,
    onError = Color(0xFF280507),
    errorContainer = Color(0xFF421216),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val DuskLoreShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DuskLoreTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DuskLoreDarkColorScheme,
        typography = Typography,
        shapes = DuskLoreShapes,
        content = content
    )
}
