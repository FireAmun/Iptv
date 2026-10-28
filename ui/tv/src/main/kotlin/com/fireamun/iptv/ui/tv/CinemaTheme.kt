package com.fireamun.iptv.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.*
import com.fireamun.iptv.core.designsystem.*

@Composable
internal fun CinemaTheme(content: @Composable () -> Unit) {
    val p = cinemaPalette()
    val font = cinemaFont()
    val base = Typography()
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme.copy(
        primary = p.focus, onPrimary = if (dark) Cinema.Night else Cinema.LightPanel,
        primaryContainer = p.panel, onPrimaryContainer = p.text, inversePrimary = if (dark) Cinema.LightFocus else Cinema.Saffron,
        secondary = p.muted, onSecondary = p.background, secondaryContainer = p.panel, onSecondaryContainer = p.text,
        tertiary = p.focus, onTertiary = if (dark) Cinema.Night else Cinema.LightPanel, tertiaryContainer = p.panel, onTertiaryContainer = p.text,
        background = p.background, onBackground = p.text, surface = p.panel, onSurface = p.text,
        surfaceVariant = p.panel, onSurfaceVariant = p.muted, surfaceTint = Color.Transparent,
        inverseSurface = if (dark) Cinema.Cream else Cinema.DarkPanel, inverseOnSurface = if (dark) Cinema.Night else Cinema.Cream,
        border = p.outline, borderVariant = p.outline.copy(alpha = .35f),
        error = p.error, onError = p.background, errorContainer = p.panel, onErrorContainer = p.error, scrim = Color.Black,
    ), typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = font), displayMedium = base.displayMedium.copy(fontFamily = font), displaySmall = base.displaySmall.copy(fontFamily = font),
        headlineLarge = base.headlineLarge.copy(fontFamily = font), headlineMedium = base.headlineMedium.copy(fontFamily = font), headlineSmall = base.headlineSmall.copy(fontFamily = font),
        titleLarge = base.titleLarge.copy(fontFamily = font), titleMedium = base.titleMedium.copy(fontFamily = font), titleSmall = base.titleSmall.copy(fontFamily = font),
        bodyLarge = base.bodyLarge.copy(fontFamily = font), bodyMedium = base.bodyMedium.copy(fontFamily = font), bodySmall = base.bodySmall.copy(fontFamily = font),
        labelLarge = base.labelLarge.copy(fontFamily = font), labelMedium = base.labelMedium.copy(fontFamily = font), labelSmall = base.labelSmall.copy(fontFamily = font),
    ), content = content)
}
