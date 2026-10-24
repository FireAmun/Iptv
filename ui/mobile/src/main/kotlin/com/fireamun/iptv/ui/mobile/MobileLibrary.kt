package com.fireamun.iptv.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.LibraryState
import com.fireamun.iptv.core.model.PlaybackStatus

@Composable
fun MobileWelcome(state: LibraryState, onChangeLanguage: () -> Unit, onImportFile: () -> Unit, onImportUrl: (String) -> Unit, onSelectChannel: (Int) -> Unit, onCancelImport: () -> Unit, onApproveHttp: () -> Unit, onDismissHttp: () -> Unit) {
    var url by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    fun submit() { val submitted = url; url = ""; keyboard?.hide(); onImportUrl(submitted) }
    LaunchedEffect(state.channels, state.selectedId) {
        if (state.channels.isNotEmpty()) list.scrollToItem(state.channels.indexOfFirst { it.id == state.selectedId }.coerceAtLeast(0) + 1)
    }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.safeDrawingPadding().imePadding(), state = list, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.mobile_title), style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.mobile_description), style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onChangeLanguage, modifier = Modifier.testTag("language_switch")) { Text(stringResource(R.string.mobile_language)) }
                            OutlinedButton(onClick = { url = ""; keyboard?.hide(); onImportFile() }, enabled = !state.importing, modifier = Modifier.testTag("import_file")) { Text(stringResource(R.string.mobile_import_file)) }
                        }
                        OutlinedTextField(value = url, onValueChange = { if (it.length <= 4096) url = it }, modifier = Modifier.fillMaxWidth().testTag("source_url"), enabled = !state.importing, singleLine = true,
                            label = { Text(stringResource(R.string.mobile_url_label)) }, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { submit() }))
                        Button(onClick = { submit() }, enabled = !state.importing && url.isNotBlank(), modifier = Modifier.testTag("import_url")) { Text(stringResource(R.string.mobile_import_url)) }
                        if (state.importing) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(stringResource(R.string.mobile_loading))
                            TextButton(onClick = onCancelImport) { Text(stringResource(R.string.mobile_cancel)) }
                        }
                        state.error?.let { Text(stringResource(importMessage(it)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import_error")) }
                        Text(stringResource(if (state.channels.isEmpty()) R.string.mobile_empty else R.string.mobile_loaded, state.channels.size))
                    }
                }
                items(state.channels, key = { it.id }) { channel ->
                    ListItem(headlineContent = { Text(channel.title ?: stringResource(R.string.mobile_entry, channel.id + 1), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = channel.group?.let { group -> { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        modifier = Modifier.fillMaxWidth().testTag("channel_${channel.id}").clickable(enabled = !state.importing) { onSelectChannel(channel.id) })
                }
            }
        }
        if (state.needsHttpApproval) AlertDialog(onDismissRequest = onDismissHttp,
            title = { Text(stringResource(R.string.mobile_http_title)) }, text = { Text(stringResource(R.string.mobile_http_body)) },
            confirmButton = { Button(onClick = onApproveHttp, modifier = Modifier.testTag("http_confirm")) { Text(stringResource(R.string.mobile_continue)) } },
            dismissButton = { TextButton(onClick = onDismissHttp) { Text(stringResource(R.string.mobile_cancel)) } })
    }
}

@Composable
fun MobilePlayback(title: String, status: PlaybackStatus, onBack: () -> Unit, onRetry: () -> Unit, player: @Composable (Modifier) -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.mobile_back)) }
                    Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                    if (status == PlaybackStatus.FAILED) Button(onClick = onRetry, modifier = Modifier.testTag("playback_retry")) { Text(stringResource(R.string.mobile_retry)) }
                }
                Text(stringResource(playbackMessage(status)), Modifier.testTag("playback_status"))
                player(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

private fun importMessage(error: ImportError): Int = when (error) {
    ImportError.NO_FILE_PICKER -> R.string.mobile_no_picker
    ImportError.INVALID_ADDRESS -> R.string.mobile_invalid_address
    ImportError.READ_FAILED -> R.string.mobile_read_failed
    ImportError.NETWORK, ImportError.HTTP_ERROR -> R.string.mobile_network_failed
    ImportError.TOO_LARGE, ImportError.LINE_TOO_LONG, ImportError.FIELD_TOO_LONG, ImportError.TOO_MANY_ENTRIES, ImportError.TIME_LIMIT -> R.string.mobile_limit
    ImportError.HLS_MANIFEST -> R.string.mobile_hls
    else -> R.string.mobile_invalid_playlist
}
private fun playbackMessage(status: PlaybackStatus): Int = when (status) {
    PlaybackStatus.PREPARING -> R.string.mobile_preparing
    PlaybackStatus.BUFFERING -> R.string.mobile_buffering
    PlaybackStatus.PLAYING -> R.string.mobile_playing
    PlaybackStatus.PAUSED -> R.string.mobile_paused
    PlaybackStatus.ENDED -> R.string.mobile_ended
    PlaybackStatus.FAILED -> R.string.mobile_playback_failed
}
