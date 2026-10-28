package com.fireamun.iptv.ui.mobile

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.fireamun.iptv.core.designsystem.cinemaPalette

/** Credential text lives only in this form; never save it in instance state. */
@Composable
internal fun MobileServerLogin(onSubmit: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
    MobileFormDialog(stringResource(R.string.mobile_server_login), ::dismiss, actions = {
        MobileFormActions(stringResource(R.string.mobile_connect), ::submit, ::dismiss, "submit_login", valid, "login_cancel")
    }) {
        Text(stringResource(R.string.mobile_login_description), style = MaterialTheme.typography.bodyMedium, color = p.muted)
        LoginField(name, { if (it.length <= 128) name = it }, R.string.mobile_playlist_name, "playlist_name")
        LoginField(server, { if (it.length <= 2048) server = it }, R.string.mobile_server_url, "server_url", KeyboardType.Uri)
        LoginField(username, { if (it.length <= 512) username = it }, R.string.mobile_username, "server_username", KeyboardType.Ascii)
        LoginField(password, { if (it.length <= 512) password = it }, R.string.mobile_password, "server_password", KeyboardType.Password, true, ::submit)
    }
}

@Composable
private fun LoginField(value: String, onChange: (String) -> Unit, label: Int, tag: String, type: KeyboardType = KeyboardType.Text, secret: Boolean = false, onGo: () -> Unit = {}) {
    MobileFormField(value = value, onValueChange = onChange, label = stringResource(label), tag = tag,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false, imeAction = if (secret) ImeAction.Go else ImeAction.Next),
        uri = type == KeyboardType.Uri,
        keyboardActions = KeyboardActions(onGo = { onGo() }))
}
