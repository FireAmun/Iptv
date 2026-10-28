package com.fireamun.iptv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.fireamun.iptv.core.designsystem.cinemaPalette

@Composable
internal fun TvServerLogin(onSubmit: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val p = cinemaPalette()
    val valid = name.isNotBlank() && server.isNotBlank() && username.isNotEmpty() && password.isNotEmpty()
    fun clear() { name = ""; server = ""; username = ""; password = ""; keyboard?.hide(); focus.clearFocus() }
    fun submit() {
        if (!valid) return
        val values = listOf(name, server, username, password)
        clear()
        onSubmit(values[0], values[1], values[2], values[3])
    }
    fun dismiss() { clear(); onDismiss() }
    Dialog(onDismissRequest = ::dismiss) {
        val window = LocalWindowInfo.current
        Surface(Modifier.widthIn(max = 600.dp).heightIn(max = 460.dp).imePadding(), shape = RoundedCornerShape(20.dp), colors = SurfaceDefaults.colors(containerColor = p.panel)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.tv_server_login), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.tv_login_description), color = p.muted, style = MaterialTheme.typography.bodySmall)
                    LoginField(name, { if (it.length <= 128) name = it }, R.string.tv_playlist_name, "playlist_name", modifier = Modifier.focusRequester(first))
                    LoginField(server, { if (it.length <= 2048) server = it }, R.string.tv_server_url, "server_url", KeyboardType.Uri)
                    LoginField(username, { if (it.length <= 512) username = it }, R.string.tv_username, "server_username", KeyboardType.Ascii)
                    LoginField(password, { if (it.length <= 512) password = it }, R.string.tv_password, "server_password", KeyboardType.Password, true, onGo = ::submit)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CinemaButton(onClick = ::dismiss, modifier = Modifier.testTag("login_cancel")) { Text(stringResource(R.string.tv_cancel)) }
                    CinemaButton(onClick = ::submit, primary = true, enabled = valid, modifier = Modifier.testTag("submit_login")) { Text(stringResource(R.string.tv_connect)) }
                }
            }
        }
        LaunchedEffect(window.isWindowFocused) { if (window.isWindowFocused) { withFrameNanos { }; first.requestFocus() } }
    }
}

@Composable
private fun LoginField(value: String, onChange: (String) -> Unit, label: Int, tag: String, type: KeyboardType = KeyboardType.Text, secret: Boolean = false, modifier: Modifier = Modifier, onGo: () -> Unit = {}) {
    val p = cinemaPalette()
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(label), color = p.muted, style = MaterialTheme.typography.labelMedium)
        BasicTextField(value = value, onValueChange = onChange, singleLine = true,
            modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.testTag(tag),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.text, textDirection = if (type == KeyboardType.Text) TextDirection.Content else TextDirection.Ltr), cursorBrush = SolidColor(p.focus),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false, imeAction = if (secret) ImeAction.Go else ImeAction.Next),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(p.background, RoundedCornerShape(10.dp)).border(if (focused) 2.dp else 1.dp, if (focused) p.focus else p.outline, RoundedCornerShape(10.dp)).padding(14.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) { inner() } })
    }
}
