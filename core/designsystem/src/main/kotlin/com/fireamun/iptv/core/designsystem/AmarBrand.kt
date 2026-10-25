package com.fireamun.iptv.core.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun amarBrandFont(): FontFamily = remember {
    FontFamily(Font(R.font.manrope, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))))
}

@Composable
fun AmarWordmark(color: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 24.sp) {
    BasicText(stringResource(R.string.amar_app_name), modifier, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontFamily = amarBrandFont(), fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize, letterSpacing = (-.7).sp, textDirection = TextDirection.Ltr))
}
