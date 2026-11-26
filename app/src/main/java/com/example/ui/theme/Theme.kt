package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PlanetDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = TextPrimary,
    primaryContainer = ElectricVioletDeep,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricVioletLight,
    onSecondary = SpaceNavy950,
    secondaryContainer = ElectricVioletSoft,
    onSecondaryContainer = ElectricVioletLight,
    tertiary = SolarAmber,
    onTertiary = SpaceNavy950,
    tertiaryContainer = SolarAmberSoft,
    onTertiaryContainer = SolarAmber,
    background = SpaceNavy950,
    onBackground = TextPrimary,
    surface = SpaceNavy900,
    onSurface = TextPrimary,
    surfaceVariant = SpaceNavy850,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = SpaceNavy950,
    surfaceContainerLow = SpaceNavy900,
    surfaceContainer = SpaceNavy850,
    surfaceContainerHigh = SpaceNavy800,
    outline = SpaceBorder,
    outlineVariant = Color(0xFF211A3D),
    error = CoralError,
    onError = TextPrimary,
    errorContainer = CoralErrorSoft,
    onErrorContainer = CoralError
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PlanetStreamingTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PlanetDarkColorScheme,
        typography = AppTypography,
        shapes = StudioShapes,
        content = content
    )
}
