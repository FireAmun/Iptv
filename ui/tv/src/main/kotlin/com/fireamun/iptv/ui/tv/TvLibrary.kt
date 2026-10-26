package com.fireamun.iptv.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.fireamun.iptv.core.designsystem.*
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.LibraryState
import com.fireamun.iptv.core.model.PlaybackStatus
import com.fireamun.iptv.core.model.ContentKind

@Composable
internal fun CinemaButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val p = cinemaPalette()
    Button(onClick = onClick, modifier = modifier, enabled = enabled,
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.colors(containerColor = if (primary) Cinema.Saffron else p.panel, contentColor = if (primary) Cinema.Night else p.text,
            focusedContainerColor = Cinema.Saffron, focusedContentColor = Cinema.Night),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, p.focus))),
        content = content)
}

@Composable
fun TvWelcome(state: LibraryState, onChangeLanguage: () -> Unit, onImportFile: () -> Unit, onImportUrl: (String) -> Unit, onSelectChannel: (Int) -> Unit, onCancelImport: () -> Unit, onApproveHttp: () -> Unit, onDismissHttp: () -> Unit, onChangeAppearance: () -> Unit, onServerLogin: (String, String, String, String) -> Unit, onLibraryBack: () -> Unit, onRefreshSource: () -> Unit, onRemoveSource: () -> Unit, onSelectSource: (String) -> Unit, onRenameSource: (String, String) -> Unit, onRemoveSourceById: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var enteringUrl by remember { mutableStateOf(false) }
    var enteringLogin by remember { mutableStateOf(false) }
    var confirmingRemoval by remember { mutableStateOf(false) }
    var managingSources by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val window = LocalWindowInfo.current
    val languageFocus = remember { FocusRequester() }
    val entryFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val targetId = state.channels.firstOrNull { it.id == state.selectedId }?.id ?: state.channels.firstOrNull()?.id
    fun submit() { val submitted = url; url = ""; enteringUrl = false; keyboard?.hide(); focusManager.clearFocus(); onImportUrl(submitted) }
    CinemaTheme {
        val p = cinemaPalette()
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.safeDrawingPadding().imePadding().testTag("library_list"), state = list, contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { AmarWordmark(p.text, Modifier.testTag("app_brand"), 30.sp) }
                            CinemaButton(onClick = onChangeAppearance, modifier = Modifier.testTag("appearance_switch")) { Text(stringResource(if (isSystemInDarkTheme()) R.string.tv_light else R.string.tv_dark)) }
                            CinemaButton(onClick = onChangeLanguage, modifier = Modifier.focusRequester(languageFocus).testTag("language_switch")) { Text(stringResource(R.string.tv_language)) }
                        }
                        if (state.channels.isEmpty()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Text(stringResource(R.string.tv_title), fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-1).sp)
                                    Text(stringResource(R.string.tv_description), style = MaterialTheme.typography.bodyMedium, color = p.muted)
                                    SourceActions(state.importing || state.storageBlocked, { enteringLogin = true }, { enteringUrl = true }, { url = ""; keyboard?.hide(); onImportFile() })
                                }
                                CinemaIllustration(Modifier.weight(1f).height(240.dp))
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (state.canGoBack) CinemaButton(onClick = onLibraryBack, modifier = Modifier.testTag("library_back")) { Text(stringResource(R.string.tv_back)) }
                                    val active = state.sources.firstOrNull { it.id == state.activeSourceId }
                                    Text(state.collectionTitle ?: state.sourceName ?: active?.let { tvSourceName(it) } ?: stringResource(R.string.tv_library), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                                    Text(stringResource(if (state.hasSavedSource) R.string.tv_loaded_saved else R.string.tv_loaded, state.channels.size), color = p.muted, style = MaterialTheme.typography.bodySmall)
                                }
                                SourceActions(state.importing || state.storageBlocked, { enteringLogin = true }, { enteringUrl = true }, { url = ""; keyboard?.hide(); onImportFile() })
                            }
                        }
                        if (state.importing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text(stringResource(if (state.switchingSource) R.string.tv_switching_source else if (state.saving) R.string.tv_saving else if (state.restoring) R.string.tv_restoring else R.string.tv_loading))
                            if (!state.saving && !state.restoring) CinemaButton(onClick = onCancelImport) { Text(stringResource(R.string.tv_cancel)) }
                        }
                        state.error?.let { Text(stringResource(importMessage(it)), color = p.error, modifier = Modifier.testTag("import_error")) }
                        if (state.hasSavedSource) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (state.sources.isNotEmpty()) CinemaButton(onClick = { managingSources = true }, enabled = !state.importing, modifier = Modifier.testTag("manage_sources")) { Text(stringResource(R.string.tv_manage_sources, state.sources.size)) }
                                state.savedAtMillis?.let { Text(stringResource(R.string.tv_saved_at, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it))), color = p.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("saved_source")) }
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CinemaButton(onClick = onRefreshSource, enabled = !state.importing && !state.storageBlocked, modifier = Modifier.testTag("refresh_source")) { Text(stringResource(R.string.tv_refresh_source)) }
                                    CinemaButton(onClick = { confirmingRemoval = true }, enabled = !state.importing, modifier = Modifier.testTag("remove_source")) { Text(stringResource(R.string.tv_remove_source)) }
                                }
                            }
                        }
                    }
                }
                items(state.channels, key = { it.id }) { channel ->
                    val title = channel.title ?: stringResource(R.string.tv_entry, channel.id + 1)
                    CinemaButton(onClick = { onSelectChannel(channel.id) }, enabled = !state.importing && !state.storageBlocked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).then(if (channel.id == targetId) Modifier.focusRequester(entryFocus) else Modifier).testTag("channel_${channel.id}")) {
                        Box(Modifier.size(42.dp).background(p.raised, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(title.take(1), color = p.text, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            channel.group?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            contentLabel(channel.kind)?.let { Text(stringResource(it), style = MaterialTheme.typography.labelSmall) }
                            if (channel.kind == ContentKind.EPISODE && channel.season != null && channel.episode != null) Text(stringResource(R.string.tv_episode_number, channel.season!!, channel.episode!!), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (channel.kind == ContentKind.SERIES) "↳" else "▶", fontSize = 20.sp)
                    }
                }
            }
        }
        if (managingSources) TvSources(state, { managingSources = false }, onSelectSource, onRenameSource, onRemoveSourceById)
        if (enteringLogin) TvServerLogin(onDismiss = { enteringLogin = false }, onSubmit = { name, server, username, password -> enteringLogin = false; onServerLogin(name, server, username, password) })
        if (confirmingRemoval) {
            val cancelFocus = remember { FocusRequester() }
            Dialog(onDismissRequest = { confirmingRemoval = false }) {
                val dialogWindow = LocalWindowInfo.current
                Surface(Modifier.widthIn(max = 520.dp), shape = RoundedCornerShape(20.dp), colors = SurfaceDefaults.colors(containerColor = p.panel)) {
                    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        val source = state.sources.firstOrNull { it.id == state.activeSourceId }
                        Text(if (source == null) stringResource(R.string.tv_remove_title) else stringResource(R.string.tv_remove_named_source, tvSourceName(source)), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.tv_remove_one_source_body))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CinemaButton(onClick = { confirmingRemoval = false }, modifier = Modifier.focusRequester(cancelFocus).testTag("remove_cancel")) { Text(stringResource(R.string.tv_cancel)) }
                            CinemaButton(onClick = { confirmingRemoval = false; onRemoveSource() }, modifier = Modifier.testTag("remove_confirm")) { Text(stringResource(R.string.tv_remove_source)) }
                        }
                    }
                }
                LaunchedEffect(dialogWindow.isWindowFocused) { if (dialogWindow.isWindowFocused) { withFrameNanos { }; cancelFocus.requestFocus() } }
            }
        }
        if (enteringUrl) {
            val inputFocus = remember { FocusRequester() }
            var inputFocused by remember { mutableStateOf(false) }
            Dialog(onDismissRequest = { enteringUrl = false; url = "" }) {
                val dialogWindow = LocalWindowInfo.current
                Surface(Modifier.widthIn(max = 600.dp), shape = RoundedCornerShape(20.dp), colors = SurfaceDefaults.colors(containerColor = p.panel)) {
                    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(stringResource(R.string.tv_add_url), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        BasicTextField(value = url, onValueChange = { if (it.length <= 4096) url = it }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(inputFocus).onFocusChanged { inputFocused = it.isFocused }.testTag("source_url"), textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.text), cursorBrush = SolidColor(p.focus),
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) submit() }),
                            decorationBox = { inner -> Box(Modifier.fillMaxWidth().border(if (inputFocused) 2.dp else 1.dp, if (inputFocused) p.focus else p.outline, RoundedCornerShape(8.dp)).padding(16.dp)) { if (url.isEmpty()) Text(stringResource(R.string.tv_url_label), color = p.muted); inner() } })
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CinemaButton(onClick = { enteringUrl = false; url = "" }) { Text(stringResource(R.string.tv_cancel)) }
                            CinemaButton(onClick = { submit() }, primary = true, enabled = url.isNotBlank(), modifier = Modifier.testTag("import_url")) { Text(stringResource(R.string.tv_import_url)) }
                        }
                    }
                }
                LaunchedEffect(dialogWindow.isWindowFocused) { if (dialogWindow.isWindowFocused) { withFrameNanos { }; inputFocus.requestFocus() } }
            }
        }
        if (state.needsHttpApproval) {
            val cancelFocus = remember { FocusRequester() }
            Dialog(onDismissRequest = onDismissHttp) {
                val dialogWindow = LocalWindowInfo.current
                Surface(Modifier.widthIn(max = 520.dp), shape = RoundedCornerShape(20.dp), colors = SurfaceDefaults.colors(containerColor = p.panel)) {
                    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(stringResource(R.string.tv_http_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.tv_http_body))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CinemaButton(onClick = onDismissHttp, modifier = Modifier.focusRequester(cancelFocus).testTag("http_cancel")) { Text(stringResource(R.string.tv_cancel)) }
                            CinemaButton(onClick = onApproveHttp, primary = true, modifier = Modifier.testTag("http_confirm")) { Text(stringResource(R.string.tv_continue)) }
                        }
                    }
                }
                LaunchedEffect(dialogWindow.isWindowFocused) { if (dialogWindow.isWindowFocused) { withFrameNanos { }; cancelFocus.requestFocus() } }
            }
        }
    }
    LaunchedEffect(state.channels, state.selectedId, window.isWindowFocused, enteringUrl, enteringLogin, confirmingRemoval, state.needsHttpApproval) {
        if (!window.isWindowFocused || enteringUrl || enteringLogin || confirmingRemoval || state.needsHttpApproval) return@LaunchedEffect
        withFrameNanos { }
        if (targetId == null) languageFocus.requestFocus()
        else { list.scrollToItem(state.channels.indexOfFirst { it.id == targetId } + 1); withFrameNanos { }; entryFocus.requestFocus() }
    }
}

@Composable
private fun SourceActions(importing: Boolean, onLogin: () -> Unit, onUrl: () -> Unit, onFile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CinemaButton(onClick = onLogin, primary = true, enabled = !importing, modifier = Modifier.testTag("add_login")) { Text(stringResource(R.string.tv_server_login), fontWeight = FontWeight.Bold) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CinemaButton(onClick = onUrl, enabled = !importing, modifier = Modifier.testTag("add_url")) { Text(stringResource(R.string.tv_url_short)) }
            CinemaButton(onClick = onFile, enabled = !importing, modifier = Modifier.testTag("import_file")) { Text(stringResource(R.string.tv_file_short)) }
        }
    }
}

@Composable
fun TvPlayback(title: String, status: PlaybackStatus, onBack: () -> Unit, onRetry: () -> Unit, player: @Composable (Modifier) -> Unit) {
    val retryFocus = remember { FocusRequester() }
    CinemaTheme {
        val p = cinemaPalette()
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    CinemaButton(onClick = onBack) { Text(stringResource(R.string.tv_back)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AmarWordmark(p.muted, Modifier.testTag("app_brand"), 12.sp)
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    if (status == PlaybackStatus.FAILED) CinemaButton(onClick = onRetry, primary = true, modifier = Modifier.focusRequester(retryFocus).testTag("playback_retry")) { Text(stringResource(R.string.tv_retry)) }
                }
                Text(stringResource(playbackMessage(status)), Modifier.testTag("playback_status"), color = if (status == PlaybackStatus.FAILED) p.error else p.muted, style = MaterialTheme.typography.labelMedium)
                player(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    LaunchedEffect(status) { if (status == PlaybackStatus.FAILED) retryFocus.requestFocus() }
}

private fun importMessage(error: ImportError): Int = when (error) {
    ImportError.INVALID_NAME -> R.string.tv_invalid_name
    ImportError.STORAGE -> R.string.tv_storage_failed
    ImportError.INVALID_LOGIN -> R.string.tv_invalid_login
    ImportError.AUTH_FAILED -> R.string.tv_auth_failed
    ImportError.ACCOUNT_DISABLED -> R.string.tv_account_disabled
    ImportError.ACCOUNT_EXPIRED -> R.string.tv_account_expired
    ImportError.SERVER_RESPONSE -> R.string.tv_server_response
    ImportError.NO_CONTENT -> R.string.tv_no_content
    ImportError.NO_FILE_PICKER -> R.string.tv_no_picker
    ImportError.INVALID_ADDRESS -> R.string.tv_invalid_address
    ImportError.READ_FAILED -> R.string.tv_read_failed
    ImportError.NETWORK, ImportError.HTTP_ERROR -> R.string.tv_network_failed
    ImportError.TOO_LARGE, ImportError.LINE_TOO_LONG, ImportError.FIELD_TOO_LONG, ImportError.TOO_MANY_ENTRIES, ImportError.TIME_LIMIT -> R.string.tv_limit
    ImportError.HLS_MANIFEST -> R.string.tv_hls
    else -> R.string.tv_invalid_playlist
}
private fun contentLabel(kind: ContentKind): Int? = when (kind) {
    ContentKind.LIVE -> R.string.tv_live
    ContentKind.MOVIE -> R.string.tv_movie
    ContentKind.SERIES -> R.string.tv_series
    ContentKind.EPISODE -> R.string.tv_episode
    ContentKind.UNKNOWN -> null
}
private fun playbackMessage(status: PlaybackStatus): Int = when (status) {
    PlaybackStatus.PREPARING -> R.string.tv_preparing
    PlaybackStatus.BUFFERING -> R.string.tv_buffering
    PlaybackStatus.PLAYING -> R.string.tv_playing
    PlaybackStatus.PAUSED -> R.string.tv_paused
    PlaybackStatus.ENDED -> R.string.tv_ended
    PlaybackStatus.FAILED -> R.string.tv_playback_failed
}
