package com.fireamun.iptv.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fireamun.iptv.core.designsystem.*
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.LibraryState
import com.fireamun.iptv.core.model.PlaybackStatus
import com.fireamun.iptv.core.model.ContentKind

@Composable
fun MobileWelcome(state: LibraryState, onChangeLanguage: () -> Unit, onImportFile: () -> Unit, onImportUrl: (String) -> Unit, onSelectChannel: (Int) -> Unit, onCancelImport: () -> Unit, onApproveHttp: () -> Unit, onDismissHttp: () -> Unit, onChangeAppearance: () -> Unit, onServerLogin: (String, String, String, String) -> Unit, onLibraryBack: () -> Unit, onRefreshSource: () -> Unit, onRemoveSource: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var enteringUrl by remember { mutableStateOf(false) }
    var enteringLogin by remember { mutableStateOf(false) }
    var confirmingRemoval by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    fun submit() { val submitted = url; url = ""; enteringUrl = false; keyboard?.hide(); onImportUrl(submitted) }
    LaunchedEffect(state.channels, state.selectedId) {
        if (state.channels.isNotEmpty()) list.scrollToItem(state.channels.indexOfFirst { it.id == state.selectedId }.coerceAtLeast(0) + 1)
    }
    LaunchedEffect(state.importing, state.error) {
        if (state.importing || state.error != null) list.scrollToItem(0)
    }
    CinemaTheme {
        val p = cinemaPalette()
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.safeDrawingPadding().imePadding().widthIn(max = 800.dp), state = list, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { Text("iptv.", style = MaterialTheme.typography.headlineLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp) }
                            TextButton(onClick = onChangeAppearance, modifier = Modifier.testTag("appearance_switch")) { Text(stringResource(if (isSystemInDarkTheme()) R.string.mobile_light else R.string.mobile_dark), color = p.text, fontSize = 12.sp) }
                            OutlinedButton(onClick = onChangeLanguage, modifier = Modifier.testTag("language_switch"), contentPadding = PaddingValues(horizontal = 12.dp)) { Text(stringResource(R.string.mobile_language), color = p.text, fontSize = 12.sp) }
                        }
                        if (state.importing) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(if (state.saving) R.string.mobile_saving else if (state.restoring) R.string.mobile_restoring else R.string.mobile_loading), Modifier.weight(1f))
                                    if (!state.saving && !state.restoring) TextButton(onClick = onCancelImport) { Text(stringResource(R.string.mobile_cancel), color = p.text) }
                                }
                            }
                        }
                        state.error?.let { Text(stringResource(importMessage(it)), color = p.error, modifier = Modifier.testTag("import_error")) }
                        if (state.hasSavedSource) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.savedAtMillis?.let { Text(stringResource(R.string.mobile_saved_at, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it))), color = p.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("saved_source")) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onRefreshSource, enabled = !state.importing && !state.storageBlocked, modifier = Modifier.testTag("refresh_source")) { Text(stringResource(R.string.mobile_refresh_source), color = p.text) }
                                    TextButton(onClick = { confirmingRemoval = true }, enabled = !state.importing, modifier = Modifier.testTag("remove_source")) { Text(stringResource(R.string.mobile_remove_source), color = p.text) }
                                }
                            }
                        }
                        if (state.channels.isEmpty()) {
                            CinemaIllustration(Modifier.fillMaxWidth().height(160.dp))
                            Text(stringResource(R.string.mobile_title), fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-1).sp)
                            Text(stringResource(R.string.mobile_description), style = MaterialTheme.typography.bodyMedium, color = p.muted)
                        } else {
                            if (state.canGoBack) TextButton(onClick = onLibraryBack, modifier = Modifier.testTag("library_back")) { Text(stringResource(R.string.mobile_back), color = p.text) }
                            Text(state.collectionTitle ?: state.sourceName ?: stringResource(R.string.mobile_library), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineLarge)
                            Text(stringResource(if (state.hasSavedSource) R.string.mobile_loaded_saved else R.string.mobile_loaded, state.channels.size), style = MaterialTheme.typography.bodySmall, color = p.muted)
                        }
                        Button(onClick = { enteringLogin = true }, enabled = !state.importing && !state.storageBlocked, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("add_login"), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.mobile_server_login), fontWeight = FontWeight.Bold) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { enteringUrl = true }, enabled = !state.importing && !state.storageBlocked, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("add_url"), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.mobile_add_url), color = p.text) }
                            OutlinedButton(onClick = { url = ""; keyboard?.hide(); onImportFile() }, enabled = !state.importing && !state.storageBlocked, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("import_file"), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.mobile_import_file), color = p.text) }
                        }
                        if (state.channels.isNotEmpty()) HorizontalDivider(color = p.outline.copy(alpha = .35f))
                    }
                }
                items(state.channels, key = { it.id }) { channel ->
                    val title = channel.title ?: stringResource(R.string.mobile_entry, channel.id + 1)
                    Surface(color = p.panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("channel_${channel.id}").clickable(enabled = !state.importing && !state.storageBlocked) { onSelectChannel(channel.id) }) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(50.dp).background(p.raised, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(title.take(1), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.text) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                channel.group?.let { Text(it, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                contentLabel(channel.kind)?.let { Text(stringResource(it), color = p.focus, style = MaterialTheme.typography.labelSmall) }
                                if (channel.kind == ContentKind.EPISODE && channel.season != null && channel.episode != null) Text(stringResource(R.string.mobile_episode_number, channel.season!!, channel.episode!!), color = p.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (channel.kind == ContentKind.SERIES) "↳" else "▶", color = p.focus, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
        if (enteringLogin) MobileServerLogin(onDismiss = { enteringLogin = false }, onSubmit = { name, server, username, password -> enteringLogin = false; onServerLogin(name, server, username, password) })
        if (confirmingRemoval) AlertDialog(onDismissRequest = { confirmingRemoval = false },
            title = { Text(stringResource(R.string.mobile_remove_title)) }, text = { Text(stringResource(R.string.mobile_remove_body)) },
            confirmButton = { Button(onClick = { confirmingRemoval = false; onRemoveSource() }, modifier = Modifier.testTag("remove_confirm")) { Text(stringResource(R.string.mobile_remove_source)) } },
            dismissButton = { TextButton(onClick = { confirmingRemoval = false }, modifier = Modifier.testTag("remove_cancel")) { Text(stringResource(R.string.mobile_cancel), color = p.text) } })
        if (enteringUrl) AlertDialog(onDismissRequest = { enteringUrl = false; url = "" },
            title = { Text(stringResource(R.string.mobile_add_url), fontWeight = FontWeight.Bold) },
            text = { OutlinedTextField(value = url, onValueChange = { if (it.length <= 4096) url = it }, modifier = Modifier.fillMaxWidth().testTag("source_url"), singleLine = true,
                label = { Text(stringResource(R.string.mobile_url_label)) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.focus, cursorColor = p.focus, focusedLabelColor = p.focus), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) submit() })) },
            confirmButton = { Button(onClick = { submit() }, enabled = url.isNotBlank(), modifier = Modifier.testTag("import_url")) { Text(stringResource(R.string.mobile_import_url)) } },
            dismissButton = { TextButton(onClick = { enteringUrl = false; url = "" }) { Text(stringResource(R.string.mobile_cancel), color = p.text) } })
        if (state.needsHttpApproval) AlertDialog(onDismissRequest = onDismissHttp,
            title = { Text(stringResource(R.string.mobile_http_title)) }, text = { Text(stringResource(R.string.mobile_http_body)) },
            confirmButton = { Button(onClick = onApproveHttp, modifier = Modifier.testTag("http_confirm")) { Text(stringResource(R.string.mobile_continue)) } },
            dismissButton = { TextButton(onClick = onDismissHttp) { Text(stringResource(R.string.mobile_cancel), color = p.text) } })
    }
}

@Composable
fun MobilePlayback(title: String, status: PlaybackStatus, onBack: () -> Unit, onRetry: () -> Unit, player: @Composable (Modifier) -> Unit) {
    CinemaTheme {
        val p = cinemaPalette()
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.mobile_back), color = p.text) }
                    Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (status == PlaybackStatus.FAILED) Button(onClick = onRetry, modifier = Modifier.testTag("playback_retry")) { Text(stringResource(R.string.mobile_retry)) }
                }
                Surface(color = p.panel, shape = RoundedCornerShape(8.dp)) { Text(stringResource(playbackMessage(status)), Modifier.testTag("playback_status").padding(horizontal = 12.dp, vertical = 6.dp), color = if (status == PlaybackStatus.FAILED) p.error else p.muted, style = MaterialTheme.typography.labelMedium) }
                player(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

private fun importMessage(error: ImportError): Int = when (error) {
    ImportError.STORAGE -> R.string.mobile_storage_failed
    ImportError.INVALID_LOGIN -> R.string.mobile_invalid_login
    ImportError.AUTH_FAILED -> R.string.mobile_auth_failed
    ImportError.ACCOUNT_DISABLED -> R.string.mobile_account_disabled
    ImportError.ACCOUNT_EXPIRED -> R.string.mobile_account_expired
    ImportError.SERVER_RESPONSE -> R.string.mobile_server_response
    ImportError.NO_CONTENT -> R.string.mobile_no_content
    ImportError.NO_FILE_PICKER -> R.string.mobile_no_picker
    ImportError.INVALID_ADDRESS -> R.string.mobile_invalid_address
    ImportError.READ_FAILED -> R.string.mobile_read_failed
    ImportError.NETWORK, ImportError.HTTP_ERROR -> R.string.mobile_network_failed
    ImportError.TOO_LARGE, ImportError.LINE_TOO_LONG, ImportError.FIELD_TOO_LONG, ImportError.TOO_MANY_ENTRIES, ImportError.TIME_LIMIT -> R.string.mobile_limit
    ImportError.HLS_MANIFEST -> R.string.mobile_hls
    else -> R.string.mobile_invalid_playlist
}
private fun contentLabel(kind: ContentKind): Int? = when (kind) {
    ContentKind.LIVE -> R.string.mobile_live
    ContentKind.MOVIE -> R.string.mobile_movie
    ContentKind.SERIES -> R.string.mobile_series
    ContentKind.EPISODE -> R.string.mobile_episode
    ContentKind.UNKNOWN -> null
}
private fun playbackMessage(status: PlaybackStatus): Int = when (status) {
    PlaybackStatus.PREPARING -> R.string.mobile_preparing
    PlaybackStatus.BUFFERING -> R.string.mobile_buffering
    PlaybackStatus.PLAYING -> R.string.mobile_playing
    PlaybackStatus.PAUSED -> R.string.mobile_paused
    PlaybackStatus.ENDED -> R.string.mobile_ended
    PlaybackStatus.FAILED -> R.string.mobile_playback_failed
}
