package com.fireamun.iptv.core.designsystem

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/** A brief first-open welcome, retained only while local restore is still working. */
@Composable
fun AmarStartupScreen(onIntroFinished: () -> Unit, animationsEnabled: Boolean = ValueAnimator.areAnimatorsEnabled()) {
    val phase = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onIntroFinished)
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) { finished(); return@LaunchedEffect }
        var reported = false
        while (isActive) {
            phase.snapTo(0f)
            phase.animateTo(1f, tween(900, easing = LinearEasing))
            if (!reported) { reported = true; finished() }
        }
    }
    AmarStartupArt(if (animationsEnabled) phase.value else 0f)
}

@Composable
private fun AmarStartupArt(phase: Float) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("startup_screen")) {
        val compact = maxHeight < 400.dp || LocalDensity.current.fontScale > 1.4f
        val logoSize = if (compact) 88.dp else 124.dp
        val density = LocalDensity.current
        val jump = if (phase < .65f) -sin(phase / .65f * PI).toFloat() * 18f else -sin((phase - .65f) / .35f * PI).toFloat() * 6f
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(0f to Cinema.Saffron, .12f to Color(0xFFCD9C20), .34f to Cinema.LightFocus, .64f to Cinema.Night, 1f to Color.Black))
            drawCircle(Brush.radialGradient(listOf(Cinema.Saffron.copy(alpha = .18f), Color.Transparent), center = Offset(size.width * .5f, size.height * .42f), radius = size.minDimension * .55f), size.minDimension * .55f, Offset(size.width * .5f, size.height * .42f))
            for ((index, point) in listOf(.14f to .35f, .82f to .29f, .9f to .72f, .2f to .82f).withIndex()) {
                val drift = sin(phase * 2 * PI + index).toFloat() * 7.dp.toPx()
                drawCircle(Cinema.Saffron.copy(alpha = .16f + index * .025f), (1.5f + index * .4f).dp.toPx(), Offset(size.width * point.first + drift, size.height * point.second - drift))
            }
        }
        Column(Modifier.align(Alignment.Center).fillMaxWidth().safeDrawingPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)) {
            Box(Modifier.size(logoSize + 24.dp, logoSize + 24.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val width = size.width * (.56f + jump / 100f)
                    drawOval(Color.Black.copy(alpha = .3f), Offset((size.width - width) / 2, size.height * .9f), Size(width, 6.dp.toPx()))
                }
                Canvas(Modifier.align(Alignment.TopCenter).size(logoSize).graphicsLayer {
                    translationY = with(density) { jump.dp.toPx() }
                    rotationZ = sin(phase * PI * 2).toFloat() * 5f
                    scaleX = 1f - jump / 550f; scaleY = 1f + jump / 650f
                }.testTag("startup_logo")) {
                    drawRoundRect(Color(0xFF15140F), cornerRadius = CornerRadius(size.width * .23f))
                    drawRoundRect(Brush.linearGradient(listOf(Cinema.Saffron, Cinema.Gold)), topLeft = Offset(1.dp.toPx(), 1.dp.toPx()), size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), cornerRadius = CornerRadius(size.width * .22f), style = Stroke(1.5.dp.toPx()))
                    drawPath(Path().apply { moveTo(size.width * .39f, size.height * .27f); lineTo(size.width * .73f, size.height * .5f); lineTo(size.width * .39f, size.height * .73f); close() }, Cinema.Saffron)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(stringResource(R.string.amar_welcome), style = TextStyle(color = Cinema.Cream, fontFamily = cinemaFont(), fontWeight = FontWeight.Medium, fontSize = if (compact) 16.sp else 20.sp, textAlign = TextAlign.Center))
                BasicText(stringResource(R.string.amar_app_name), style = TextStyle(color = Cinema.Saffron, fontFamily = amarBrandFont(), fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 32.sp else 44.sp, lineHeight = if (compact) 40.sp else 54.sp, letterSpacing = (-1.2).sp, textDirection = TextDirection.Ltr, textAlign = TextAlign.Center))
            }
            Canvas(Modifier.width(64.dp).height(20.dp)) {
                repeat(3) { index ->
                    val lift = sin(phase * PI * 2 - index * .65).toFloat() * 3.dp.toPx()
                    drawCircle(Cinema.Saffron.copy(alpha = .55f + index * .15f), 2.5.dp.toPx(), Offset(size.width / 2 + (index - 1) * 16.dp.toPx(), size.height / 2 - lift))
                }
            }
        }
    }
}
