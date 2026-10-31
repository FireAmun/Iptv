package com.fireamun.iptv.ui.mobile

import android.view.Gravity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.fireamun.iptv.core.designsystem.*

/** Pinned actions with a scrollable body; very short windows can scroll the whole dialog. */
@Composable
internal fun MobileFormDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = cinemaPalette()
    val closeLabel = stringResource(R.string.mobile_close_sources)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val configuration = LocalConfiguration.current
        val compact = configuration.screenWidthDp < 600
        var sheetBounds by remember { mutableStateOf(Rect.Zero) }
        val dismiss by rememberUpdatedState(onDismiss)
        SideEffect { window?.setGravity(Gravity.CENTER) }
        BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) {
            // The wide native window includes transparent sheet margins; dismiss those too.
            detectTapGestures { point -> if (!sheetBounds.contains(point)) dismiss() }
        }.safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = if (compact) Alignment.BottomCenter else Alignment.Center) {
            val shortWindow = maxHeight < 360.dp * configuration.fontScale
            val scroll = rememberScrollState()
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight)
                    .onGloballyPositioned { sheetBounds = it.boundsInRoot() },
                color = p.panel, contentColor = p.text, shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, p.outline.copy(alpha = .3f)),
            ) {
                Column((if (shortWindow) Modifier.verticalScroll(scroll).testTag("mobile_form_scroll") else Modifier).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).semantics { contentDescription = closeLabel }) {
                            CinemaIcon(CinemaSymbol.CLOSE, tint = p.muted)
                        }
                    }
                    Column(if (shortWindow) Modifier else Modifier.weight(1f, fill = false).verticalScroll(scroll).testTag("mobile_form_scroll"),
                        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                    actions()
                }
            }
        }
    }
}

@Composable
internal fun MobileFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    uri: Boolean = false,
    isError: Boolean = false,
) {
    val p = cinemaPalette()
    val relocation = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom, configuration.screenHeightDp, configuration.fontScale) {
        if (focused) {
            withFrameNanos { }
            relocation.bringIntoView()
        }
    }
    Column(Modifier.bringIntoViewRequester(relocation), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        TextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).onFocusChanged { focused = it.isFocused }
                .testTag(tag).semantics { contentDescription = label },
            shape = RoundedCornerShape(12.dp), isError = isError,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = if (uri) TextDirection.Ltr else TextDirection.Content),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = p.background, unfocusedContainerColor = p.background,
                errorContainerColor = p.background, focusedTextColor = p.text, unfocusedTextColor = p.text,
                focusedIndicatorColor = p.focus, unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = p.error, cursorColor = p.focus,
            ),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
        )
    }
}

@Composable
internal fun MobileFormActions(
    primary: String,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    primaryTag: String,
    enabled: Boolean = true,
    cancelTag: String? = null,
    destructive: Boolean = false,
) {
    val p = cinemaPalette()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = onPrimary, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(primaryTag),
            shape = RoundedCornerShape(12.dp),
            colors = if (destructive) ButtonDefaults.buttonColors(containerColor = p.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
        ) { Text(primary, fontWeight = FontWeight.Bold) }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (cancelTag == null) Modifier else Modifier.testTag(cancelTag))) {
            Text(stringResource(R.string.mobile_cancel), color = p.muted)
        }
    }
}
