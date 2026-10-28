package com.fireamun.iptv.ui.mobile

import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.fireamun.iptv.core.designsystem.*

@Composable
fun CinemaTheme(content: @Composable () -> Unit) {
    val p = cinemaPalette()
    val font = cinemaFont()
    val base = Typography()
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Cinema.Saffron, onPrimary = Cinema.Night, primaryContainer = p.raised, onPrimaryContainer = p.text,
        inversePrimary = Cinema.LightFocus,
        secondary = p.focus, onSecondary = Cinema.Night, secondaryContainer = p.panel, onSecondaryContainer = p.text,
        tertiary = p.muted, onTertiary = Cinema.Night, tertiaryContainer = p.panel, onTertiaryContainer = p.text,
        background = p.background, onBackground = p.text, surface = p.background, onSurface = p.text,
        surfaceVariant = p.panel, onSurfaceVariant = p.muted, surfaceTint = Color.Transparent,
        inverseSurface = Cinema.LightPanel, inverseOnSurface = Cinema.Night,
        error = p.error, onError = Color(0xFF601410), errorContainer = Color(0xFF431917), onErrorContainer = Color(0xFFFFDAD6),
        outline = p.outline, outlineVariant = p.outline.copy(alpha = .45f), scrim = Color.Black,
        surfaceBright = p.panel, surfaceDim = p.background, surfaceContainerLowest = p.background,
        surfaceContainerLow = p.panel, surfaceContainer = p.panel, surfaceContainerHigh = p.panel, surfaceContainerHighest = p.panel,
        primaryFixed = Cinema.Saffron, primaryFixedDim = Cinema.Sand, onPrimaryFixed = Cinema.Night, onPrimaryFixedVariant = Cinema.LightFocus,
        secondaryFixed = Cinema.Cream, secondaryFixedDim = Cinema.Sand, onSecondaryFixed = Cinema.Night, onSecondaryFixedVariant = Cinema.Charcoal,
        tertiaryFixed = Cinema.Cream, tertiaryFixedDim = Cinema.Sand, onTertiaryFixed = Cinema.Night, onTertiaryFixedVariant = Cinema.Charcoal,
    ) else lightColorScheme(
        primary = Cinema.Saffron, onPrimary = Cinema.Night, primaryContainer = p.raised, onPrimaryContainer = p.text,
        inversePrimary = Cinema.Saffron,
        secondary = p.focus, onSecondary = Cinema.LightPanel, secondaryContainer = p.panel, onSecondaryContainer = p.text,
        tertiary = p.muted, onTertiary = Cinema.LightPanel, tertiaryContainer = p.panel, onTertiaryContainer = p.text,
        background = p.background, onBackground = p.text, surface = p.background, onSurface = p.text,
        surfaceVariant = p.panel, onSurfaceVariant = p.muted, surfaceTint = Color.Transparent,
        inverseSurface = Cinema.DarkPanel, inverseOnSurface = Cinema.Cream,
        error = p.error, onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        outline = p.outline, outlineVariant = p.outline.copy(alpha = .45f), scrim = Color.Black,
        surfaceBright = p.panel, surfaceDim = p.background, surfaceContainerLowest = p.panel,
        surfaceContainerLow = p.panel, surfaceContainer = p.panel, surfaceContainerHigh = p.panel, surfaceContainerHighest = p.panel,
        primaryFixed = Cinema.Saffron, primaryFixedDim = Cinema.Sand, onPrimaryFixed = Cinema.Night, onPrimaryFixedVariant = Cinema.LightFocus,
        secondaryFixed = Cinema.Cream, secondaryFixedDim = Cinema.Sand, onSecondaryFixed = Cinema.Night, onSecondaryFixedVariant = Cinema.Charcoal,
        tertiaryFixed = Cinema.Cream, tertiaryFixedDim = Cinema.Sand, onTertiaryFixed = Cinema.Night, onTertiaryFixedVariant = Cinema.Charcoal,
    )
    MaterialTheme(colorScheme = colors, typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = font), displayMedium = base.displayMedium.copy(fontFamily = font),
        displaySmall = base.displaySmall.copy(fontFamily = font), headlineLarge = base.headlineLarge.copy(fontFamily = font),
        headlineMedium = base.headlineMedium.copy(fontFamily = font), headlineSmall = base.headlineSmall.copy(fontFamily = font),
        titleLarge = base.titleLarge.copy(fontFamily = font), titleMedium = base.titleMedium.copy(fontFamily = font), titleSmall = base.titleSmall.copy(fontFamily = font),
        bodyLarge = base.bodyLarge.copy(fontFamily = font), bodyMedium = base.bodyMedium.copy(fontFamily = font), bodySmall = base.bodySmall.copy(fontFamily = font),
        labelLarge = base.labelLarge.copy(fontFamily = font), labelMedium = base.labelMedium.copy(fontFamily = font), labelSmall = base.labelSmall.copy(fontFamily = font),
    ), content = content)
}
