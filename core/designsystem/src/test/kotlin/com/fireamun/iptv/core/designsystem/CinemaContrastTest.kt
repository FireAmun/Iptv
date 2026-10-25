package com.fireamun.iptv.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaContrastTest {
    @Test fun bothAppearancesKeepTextAndFocusReadable() {
        for (palette in listOf(Cinema.Dark, Cinema.Light)) {
            for (surface in listOf(palette.background, palette.panel, palette.raised)) {
                assertTrue("Primary text must meet 4.5:1", contrast(palette.text, surface) >= 4.5)
                assertTrue("Secondary text must meet 4.5:1", contrast(palette.muted, surface) >= 4.5)
            }
            assertTrue("Focus must meet 3:1 against background", contrast(palette.focus, palette.background) >= 3)
            assertTrue("Input outline must meet 3:1 against panel", contrast(palette.outline, palette.panel) >= 3)
        }
        assertTrue("Primary action text must meet 4.5:1", contrast(Cinema.Night, Cinema.Saffron) >= 4.5)
    }
    private fun contrast(a: Color, b: Color): Double {
        val first = a.luminance().toDouble()
        val second = b.luminance().toDouble()
        return (maxOf(first, second) + .05) / (minOf(first, second) + .05)
    }
}
