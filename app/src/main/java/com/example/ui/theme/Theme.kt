package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ActakeLightColorScheme = lightColorScheme(
    primary = AccentCoral,
    onPrimary = BgCard,
    primaryContainer = AccentCoralSoft,
    onPrimaryContainer = AccentCoralDeep,
    secondary = BgObsidian,
    onSecondary = BgCard,
    secondaryContainer = BgSurfaceSoft,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentEmerald,
    onTertiary = BgCard,
    tertiaryContainer = AccentEmeraldSoft,
    onTertiaryContainer = BgCelebration,
    background = BgCanvas,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgSurfaceSoft,
    onSurfaceVariant = TextMuted,
    outline = DividerSubtle,
    error = AccentDanger,
    onError = BgCard
)

val ActakeShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ActakeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ActakeLightColorScheme,
        typography = AppTypography,
        shapes = ActakeShapes,
        content = content
    )
}
