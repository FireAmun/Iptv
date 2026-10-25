package com.fireamun.iptv.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.*

object Cinema {
    val Night = Color(0xFF0B0B0B)
    val Charcoal = Color(0xFF2F2F2F)
    val Jet = Color(0xFF333533)
    val Cream = Color(0xFFF2ECDD)
    val Saffron = Color(0xFFF5CB5C)
    val Sand = Color(0xFFE3D5BB)
    val Gold = Color(0xFFA07F3A)
    val LightFocus = Color(0xFF765615)
    val DarkPanel = Color(0xFF181A18)
    val LightPanel = Color(0xFFFFFBF2)
    val Dark = CinemaPalette(Night, DarkPanel, Charcoal, Cream, Sand, Color(0xFF6B6C61), Saffron, Color(0xFFFFB4AB))
    val Light = CinemaPalette(Cream, LightPanel, Sand, Night, Color(0xFF59564C), Color(0xFF8C8471), LightFocus, Color(0xFFA12520))
}

data class CinemaPalette(val background: Color, val panel: Color, val raised: Color, val text: Color, val muted: Color, val outline: Color, val focus: Color, val error: Color)

@Composable
fun cinemaPalette(): CinemaPalette = if (isSystemInDarkTheme()) Cinema.Dark else Cinema.Light

@Composable
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun cinemaFont(): FontFamily {
    val resource = if (LocalConfiguration.current.locales[0].language == "ar") R.font.cairo else R.font.manrope
    return remember(resource) { FontFamily(listOf(400, 500, 600, 700, 800).map { weight ->
        Font(resource, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    }) }
}

/** Original vector artwork for source setup; no pretend channels or remote images. */
@Composable
fun CinemaIllustration(modifier: Modifier = Modifier) {
    val palette = cinemaPalette()
    Canvas(modifier) {
        val unit = size.minDimension
        val w = size.width * .76f
        val h = size.height * .64f
        val x = (size.width - w) / 2
        val y = (size.height - h) / 2
        drawRoundRect(palette.raised, Offset(x + unit * .055f, y + unit * .055f), Size(w, h), CornerRadius(unit * .05f))
        drawRoundRect(palette.panel, Offset(x, y), Size(w, h), CornerRadius(unit * .05f))
        drawRoundRect(Cinema.Gold, Offset(x, y), Size(w, h), CornerRadius(unit * .05f), style = Stroke(unit * .006f))
        val cx = size.width / 2
        val cy = size.height / 2
        drawCircle(Cinema.Saffron, unit * .13f, Offset(cx, cy))
        val play = Path().apply { moveTo(cx - unit * .032f, cy - unit * .055f); lineTo(cx + unit * .055f, cy); lineTo(cx - unit * .032f, cy + unit * .055f); close() }
        drawPath(play, Cinema.Night)
        drawLine(palette.outline, Offset(x + w * .12f, y + h * .84f), Offset(x + w * .88f, y + h * .84f), unit * .008f)
        drawLine(Cinema.Saffron, Offset(x + w * .12f, y + h * .84f), Offset(x + w * .40f, y + h * .84f), unit * .008f)
    }
}
