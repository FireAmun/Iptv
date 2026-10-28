package com.fireamun.iptv.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class CinemaSymbol {
    SEARCH, LIVE, MOVIES, SERIES, FAVOURITE, FAVOURITE_FILLED, SETTINGS, CATEGORIES,
    CHECK, CLOSE, BACK, ADD, REFRESH, FILE, LINK, SERVER, LANGUAGE, APPEARANCE,
}

/** Decorative vector; the containing action supplies its localized accessible label. */
@Composable
fun CinemaIcon(symbol: CinemaSymbol, modifier: Modifier = Modifier, tint: Color = cinemaPalette().text) {
    val mirror = symbol == CinemaSymbol.BACK && LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        translate((size.width - unit * 24f) / 2f, (size.height - unit * 24f) / 2f) {
            scale(unit, unit, pivot = Offset.Zero) {
                scale(if (mirror) -1f else 1f, 1f, pivot = Offset(12f, 12f)) {
                    val stroke = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x, y), Offset(x2, y2), 1.7f, StrokeCap.Round)
                    fun path(vararg points: Float) {
                        drawPath(Path().apply {
                            moveTo(points[0], points[1])
                            for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                        }, tint, style = stroke)
                    }
                    fun box(x: Float, y: Float, w: Float, h: Float, radius: Float = 2f) =
                        drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(radius), style = stroke)
                    when (symbol) {
                        CinemaSymbol.SEARCH -> { drawCircle(tint, 6.5f, Offset(10.5f, 10.5f), style = stroke); line(15.5f, 15.5f, 21f, 21f) }
                        CinemaSymbol.LIVE -> { box(3f, 6f, 18f, 13f); path(8f, 2f, 12f, 6f, 16f, 2f); line(9f, 22f, 15f, 22f) }
                        CinemaSymbol.MOVIES -> {
                            box(3f, 4f, 18f, 16f); line(3f, 9f, 21f, 9f)
                            line(7f, 4f, 10f, 9f); line(14f, 4f, 17f, 9f)
                        }
                        CinemaSymbol.SERIES -> { box(4f, 8f, 16f, 13f); line(6f, 4.5f, 18f, 4.5f); line(8f, 1.5f, 16f, 1.5f) }
                        CinemaSymbol.FAVOURITE, CinemaSymbol.FAVOURITE_FILLED -> {
                            val heart = Path().apply {
                                moveTo(12f, 21f)
                                cubicTo(9f, 18.5f, 2.5f, 13.5f, 2.5f, 8.5f)
                                cubicTo(2.5f, 2.8f, 9f, 1.7f, 12f, 6f)
                                cubicTo(15f, 1.7f, 21.5f, 2.8f, 21.5f, 8.5f)
                                cubicTo(21.5f, 13.5f, 15f, 18.5f, 12f, 21f); close()
                            }
                            if (symbol == CinemaSymbol.FAVOURITE_FILLED) drawPath(heart, tint) else drawPath(heart, tint, style = stroke)
                        }
                        CinemaSymbol.SETTINGS, CinemaSymbol.APPEARANCE -> {
                            drawCircle(tint, 6f, Offset(12f, 12f), style = stroke)
                            if (symbol == CinemaSymbol.SETTINGS) drawCircle(tint, 2.2f, Offset(12f, 12f), style = stroke)
                            else drawArc(tint, -90f, 180f, useCenter = true, topLeft = Offset(6f, 6f), size = Size(12f, 12f))
                            for (i in 0..7) {
                                val angle = i * kotlin.math.PI / 4
                                val x = kotlin.math.cos(angle).toFloat(); val y = kotlin.math.sin(angle).toFloat()
                                line(12f + x * 8.5f, 12f + y * 8.5f, 12f + x * 10.5f, 12f + y * 10.5f)
                            }
                        }
                        CinemaSymbol.CATEGORIES -> { for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f)) box(x, y, 7f, 7f, 1f) }
                        CinemaSymbol.CHECK -> path(4f, 12f, 9f, 17f, 20f, 6f)
                        CinemaSymbol.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                        CinemaSymbol.BACK -> { path(13f, 4f, 5f, 12f, 13f, 20f); line(5f, 12f, 21f, 12f) }
                        CinemaSymbol.ADD -> { line(12f, 4f, 12f, 20f); line(4f, 12f, 20f, 12f) }
                        CinemaSymbol.REFRESH -> {
                            drawArc(tint, 35f, 285f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                            path(20f, 3f, 20f, 9f, 14f, 9f)
                        }
                        CinemaSymbol.FILE -> { path(14f, 2f, 5f, 2f, 5f, 22f, 19f, 22f, 19f, 7f, 14f, 2f, 14f, 7f, 19f, 7f); line(8f, 12f, 16f, 12f); line(8f, 16f, 14f, 16f) }
                        CinemaSymbol.LINK -> {
                            drawArc(tint, 45f, 270f, false, Offset(2f, 9f), Size(11f, 11f), style = stroke)
                            drawArc(tint, 225f, 270f, false, Offset(11f, 4f), Size(11f, 11f), style = stroke)
                            line(8f, 16f, 16f, 8f)
                        }
                        CinemaSymbol.SERVER -> { box(3f, 3f, 18f, 7f); box(3f, 14f, 18f, 7f); drawCircle(tint, 1f, Offset(7f, 6.5f)); drawCircle(tint, 1f, Offset(7f, 17.5f)); line(12f, 6.5f, 17f, 6.5f); line(12f, 17.5f, 17f, 17.5f) }
                        CinemaSymbol.LANGUAGE -> {
                            drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                            drawOval(tint, Offset(8f, 3f), Size(8f, 18f), style = stroke)
                            line(3f, 12f, 21f, 12f); line(5f, 7f, 19f, 7f); line(5f, 17f, 19f, 17f)
                        }
                    }
                }
            }
        }
    }
}
