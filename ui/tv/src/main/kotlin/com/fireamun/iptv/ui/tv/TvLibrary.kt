package com.fireamun.iptv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.LibraryState
import com.fireamun.iptv.core.model.PlaybackStatus

@Composable
fun TvWelcome(state: LibraryState, onChangeLanguage: () -> Unit, onImportFile: () -> Unit, onImportUrl: (String) -> Unit, onSelectChannel: (Int) -> Unit, onCancelImport: () -> Unit, onApproveHttp: () -> Unit, onDismissHttp: () -> Unit) {
    var url by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val window = LocalWindowInfo.current
    val languageFocus = remember { FocusRequester() }
    val entryFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val targetId = state.channels.firstOrNull { it.id == state.selectedId }?.id ?: state.channels.firstOrNull()?.id
    fun submit() { val submitted = url; url = ""; keyboard?.hide(); focusManager.clearFocus(); onImportUrl(submitted) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.safeDrawingPadding().imePadding(), state = list, contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.tv_title), style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.tv_description), style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = onChangeLanguage, modifier = Modifier.focusRequester(languageFocus).testTag("language_switch")) { Text(stringResource(R.string.tv_language)) }
                            Button(onClick = { url = ""; keyboard?.hide(); onImportFile() }, enabled = !state.importing, modifier = Modifier.testTag("import_file")) { Text(stringResource(R.string.tv_import_file)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BasicTextField(value = url, onValueChange = { if (it.length <= 4096) url = it }, enabled = !state.importing, singleLine = true,
                                modifier = Modifier.weight(1f).testTag("source_url"), textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { submit() }),
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)).padding(14.dp)) {
                                        if (url.isEmpty()) Text(stringResource(R.string.tv_url_label), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                                        inner()
                                    }
                                })
                            Button(onClick = { submit() }, enabled = !state.importing && url.isNotBlank(), modifier = Modifier.testTag("import_url")) { Text(stringResource(R.string.tv_import_url)) }
                        }
                        if (state.importing) {
                            Text(stringResource(R.string.tv_loading))
                            Button(onClick = onCancelImport) { Text(stringResource(R.string.tv_cancel)) }
                        }
                        state.error?.let { Text(stringResource(importMessage(it)), modifier = Modifier.testTag("import_error")) }
                        Text(stringResource(if (state.channels.isEmpty()) R.string.tv_empty else R.string.tv_loaded, state.channels.size))
                    }
                }
                items(state.channels, key = { it.id }) { channel ->
                    Button(onClick = { onSelectChannel(channel.id) }, enabled = !state.importing,
                        modifier = Modifier.fillMaxWidth().then(if (channel.id == targetId) Modifier.focusRequester(entryFocus) else Modifier).testTag("channel_${channel.id}")) {
                        Column {
                            Text(channel.title ?: stringResource(R.string.tv_entry, channel.id + 1), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            channel.group?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
        if (state.needsHttpApproval) {
            val cancelFocus = remember { FocusRequester() }
            Dialog(onDismissRequest = onDismissHttp) {
                val dialogWindow = LocalWindowInfo.current
                Surface(Modifier.widthIn(max = 520.dp)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(stringResource(R.string.tv_http_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.tv_http_body))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = onDismissHttp, modifier = Modifier.focusRequester(cancelFocus).testTag("http_cancel")) { Text(stringResource(R.string.tv_cancel)) }
                            Button(onClick = onApproveHttp, modifier = Modifier.testTag("http_confirm")) { Text(stringResource(R.string.tv_continue)) }
                        }
                    }
                }
                LaunchedEffect(dialogWindow.isWindowFocused) {
                    if (dialogWindow.isWindowFocused) { withFrameNanos { }; cancelFocus.requestFocus() }
                }
            }
        }
    }
    LaunchedEffect(state.channels, state.selectedId, window.isWindowFocused) {
        if (!window.isWindowFocused) return@LaunchedEffect
        withFrameNanos { }
        if (targetId == null) languageFocus.requestFocus()
        else {
            list.scrollToItem(state.channels.indexOfFirst { it.id == targetId } + 1)
            withFrameNanos { }
            entryFocus.requestFocus()
        }
    }
}

@Composable
fun TvPlayback(title: String, status: PlaybackStatus, onBack: () -> Unit, onRetry: () -> Unit, player: @Composable (Modifier) -> Unit) {
    val retryFocus = remember { FocusRequester() }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Button(onClick = onBack) { Text(stringResource(R.string.tv_back)) }
                    Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                    if (status == PlaybackStatus.FAILED) Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus).testTag("playback_retry")) { Text(stringResource(R.string.tv_retry)) }
                }
                Text(stringResource(playbackMessage(status)), Modifier.testTag("playback_status"))
                player(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    LaunchedEffect(status) { if (status == PlaybackStatus.FAILED) retryFocus.requestFocus() }
}

private fun importMessage(error: ImportError): Int = when (error) {
    ImportError.NO_FILE_PICKER -> R.string.tv_no_picker
    ImportError.INVALID_ADDRESS -> R.string.tv_invalid_address
    ImportError.READ_FAILED -> R.string.tv_read_failed
    ImportError.NETWORK, ImportError.HTTP_ERROR -> R.string.tv_network_failed
    ImportError.TOO_LARGE, ImportError.LINE_TOO_LONG, ImportError.FIELD_TOO_LONG, ImportError.TOO_MANY_ENTRIES, ImportError.TIME_LIMIT -> R.string.tv_limit
    ImportError.HLS_MANIFEST -> R.string.tv_hls
    else -> R.string.tv_invalid_playlist
}
private fun playbackMessage(status: PlaybackStatus): Int = when (status) {
    PlaybackStatus.PREPARING -> R.string.tv_preparing
    PlaybackStatus.BUFFERING -> R.string.tv_buffering
    PlaybackStatus.PLAYING -> R.string.tv_playing
    PlaybackStatus.PAUSED -> R.string.tv_paused
    PlaybackStatus.ENDED -> R.string.tv_ended
    PlaybackStatus.FAILED -> R.string.tv_playback_failed
}
