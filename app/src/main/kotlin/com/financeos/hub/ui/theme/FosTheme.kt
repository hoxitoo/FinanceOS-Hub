package com.financeos.hub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

private val FosDarkColorScheme = darkColorScheme(
    background       = FosColors.Background,
    surface          = FosColors.Surface,
    surfaceVariant   = FosColors.Surface2,
    outline          = FosColors.Border,
    primary          = FosColors.Positive,
    onPrimary        = FosColors.Background,
    secondary        = FosColors.Info,
    onSecondary      = FosColors.Background,
    tertiary         = FosColors.Warning,
    onTertiary       = FosColors.Background,
    error            = FosColors.Negative,
    onError          = FosColors.Background,
    onBackground     = FosColors.TextPrimary,
    onSurface        = FosColors.TextPrimary,
    onSurfaceVariant = FosColors.TextSecondary,
)

private val FosTypography = Typography(
    bodyLarge   = FosType.Body,
    bodyMedium  = FosType.Body,
    bodySmall   = FosType.Micro,
    labelLarge  = FosType.Label,
    labelMedium = FosType.Label,
    labelSmall  = FosType.Micro,
    titleLarge  = FosType.ScreenTitle,
    titleMedium = FosType.SubHeader,
    titleSmall  = FosType.BodySemi,
    headlineLarge  = FosType.HeroAmount,
    headlineMedium = FosType.CardAmount,
    headlineSmall  = TextStyle(),
)

@Composable
fun FosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FosDarkColorScheme,
        typography  = FosTypography,
        content     = content,
    )
}
