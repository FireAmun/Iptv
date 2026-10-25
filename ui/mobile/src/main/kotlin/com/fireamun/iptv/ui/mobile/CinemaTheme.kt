package com.fireamun.iptv.ui.mobile

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.fireamun.iptv.core.designsystem.*

@Composable
internal fun CinemaTheme(content: @Composable () -> Unit) {
    val p = cinemaPalette()
    val font = cinemaFont()
    val base = Typography()
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cinema.Saffron, onPrimary = Cinema.Night, primaryContainer = p.raised, onPrimaryContainer = p.text,
        secondary = p.focus, onSecondary = p.background, secondaryContainer = p.raised, onSecondaryContainer = p.text,
        background = p.background, onBackground = p.text, surface = p.background, onSurface = p.text,
        surfaceVariant = p.panel, onSurfaceVariant = p.muted, surfaceContainer = p.panel, surfaceContainerHigh = p.raised,
        outline = p.outline, outlineVariant = p.outline.copy(alpha = .45f), surfaceTint = p.focus, error = p.error, onError = p.background,
    ), typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = font), displayMedium = base.displayMedium.copy(fontFamily = font),
        displaySmall = base.displaySmall.copy(fontFamily = font), headlineLarge = base.headlineLarge.copy(fontFamily = font),
        headlineMedium = base.headlineMedium.copy(fontFamily = font), headlineSmall = base.headlineSmall.copy(fontFamily = font),
        titleLarge = base.titleLarge.copy(fontFamily = font), titleMedium = base.titleMedium.copy(fontFamily = font), titleSmall = base.titleSmall.copy(fontFamily = font),
        bodyLarge = base.bodyLarge.copy(fontFamily = font), bodyMedium = base.bodyMedium.copy(fontFamily = font), bodySmall = base.bodySmall.copy(fontFamily = font),
        labelLarge = base.labelLarge.copy(fontFamily = font), labelMedium = base.labelMedium.copy(fontFamily = font), labelSmall = base.labelSmall.copy(fontFamily = font),
    ), content = content)
}
