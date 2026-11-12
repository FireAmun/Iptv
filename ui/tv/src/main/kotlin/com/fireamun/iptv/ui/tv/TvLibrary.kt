package com.fireamun.iptv.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
            focusedContainerColor = if (primary) Cinema.Saffron else p.raised, focusedContentColor = if (primary) Cinema.Night else p.text),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        border = ButtonDefaults.border(border = Border(BorderStroke(1.dp, p.outline.copy(alpha = .35f))), focusedBorder = Border(BorderStroke(3.dp, p.focus))),
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
    val targetId: Int? = null
    fun submit() { val submitted = url; url = ""; enteringUrl = false; onImportUrl(submitted) }
    fun dismissUrl() { enteringUrl = false; url = "" }
    CinemaTheme {
        val p = cinemaPalette()
        Surface(Modifier.fillMaxSize(), colors = SurfaceDefaults.colors(containerColor = p.background, contentColor = p.text)) {
            LazyColumn(modifier = Modifier.imePadding().testTag("library_list"), state = list, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "header") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(R.string.tv_settings_title), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.tv_appearance_section), style = MaterialTheme.typography.labelSmall, color = p.muted)
                            CinemaButton(onClick = onChangeAppearance, modifier = Modifier.testTag("appearance_switch")) {
                                CinemaIcon(CinemaSymbol.APPEARANCE, tint = LocalContentColor.current); Spacer(Modifier.width(8.dp))
                                Text(stringResource(if (isSystemInDarkTheme()) R.string.tv_light else R.string.tv_dark))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.tv_language_section), style = MaterialTheme.typography.labelSmall, color = p.muted)
                            CinemaButton(onClick = onChangeLanguage, modifier = Modifier.focusRequester(languageFocus).testTag("language_switch")) {
                                CinemaIcon(CinemaSymbol.LANGUAGE, tint = LocalContentColor.current); Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.tv_language))
                            }
                        }
                    }
                }
                if (state.importing || state.error != null) item(key = "status") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.importing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Text(stringResource(if (state.switchingSource) R.string.tv_switching_source else if (state.saving) R.string.tv_saving else if (state.restoring) R.string.tv_restoring else R.string.tv_loading), Modifier.weight(1f))
                            if (!state.saving && !state.restoring) CinemaButton(onClick = onCancelImport) { Text(stringResource(R.string.tv_cancel)) }
                        }
                        state.error?.let { Text(stringResource(importMessage(it)), color = p.error, modifier = Modifier.testTag("import_error")) }
                    }
                }
                item(key = "library") {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = p.panel, contentColor = p.text)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.tv_library), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (state.entryCount == 0) {
                                Text(stringResource(R.string.tv_title), fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp)
                                Text(stringResource(R.string.tv_description), style = MaterialTheme.typography.bodyMedium, color = p.muted)
                            } else {
                                if (state.canGoBack) CinemaButton(onClick = onLibraryBack, modifier = Modifier.testTag("library_back")) { Text(stringResource(R.string.tv_back)) }
                                val active = state.sources.firstOrNull { it.id == state.activeSourceId }
                                Text(state.collectionTitle ?: state.sourceName ?: active?.let { tvSourceName(it) } ?: stringResource(R.string.tv_library), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(stringResource(if (state.hasSavedSource) R.string.tv_loaded_saved else R.string.tv_loaded, state.entryCount), color = p.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.hasSavedSource) {
                                state.savedAtMillis?.let { Text(stringResource(R.string.tv_saved_at, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it))), color = p.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("saved_source")) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (state.sources.isNotEmpty()) CinemaButton(onClick = { managingSources = true }, enabled = !state.importing, modifier = Modifier.testTag("manage_sources")) { Text(stringResource(R.string.tv_manage_sources, state.sources.size)) }
                                    CinemaButton(onClick = onRefreshSource, enabled = !state.importing && !state.storageBlocked, modifier = Modifier.testTag("refresh_source")) { CinemaIcon(CinemaSymbol.REFRESH, tint = LocalContentColor.current); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tv_refresh_source)) }
                                    CinemaButton(onClick = { confirmingRemoval = true }, enabled = !state.importing, modifier = Modifier.testTag("remove_source")) { Text(stringResource(R.string.tv_remove_source), color = p.error) }
                                }
                            }
                        }
                    }
                }
                item(key = "add_source") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.tv_add_source_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SourceActions(state.importing || state.storageBlocked, { enteringLogin = true }, { enteringUrl = true }, { url = ""; keyboard?.hide(); onImportFile() })
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
                Surface(Modifier.widthIn(max = 520.dp).heightIn(max = 460.dp).imePadding(), shape = RoundedCornerShape(20.dp), colors = SurfaceDefaults.colors(containerColor = p.panel)) {
                    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val source = state.sources.firstOrNull { it.id == state.activeSourceId }
                            Text(if (source == null) stringResource(R.string.tv_remove_title) else stringResource(R.string.tv_remove_named_source, tvSourceName(source)), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.tv_remove_one_source_body), color = p.muted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CinemaButton(onClick = { confirmingRemoval = false }, modifier = Modifier.focusRequester(cancelFocus).testTag("remove_cancel")) { Text(stringResource(R.string.tv_cancel)) }
                            CinemaButton(onClick = { confirmingRemoval = false; onRemoveSource() }, enabled = !state.importing, modifier = Modifier.testTag("remove_confirm")) { Text(stringResource(R.string.tv_remove_source), color = p.error) }
                        }
                    }
                }
                LaunchedEffect(dialogWindow.isWindowFocused) { if (dialogWindow.isWindowFocused) { withFrameNanos { }; cancelFocus.requestFocus() } }
            }
        }
        if (enteringUrl) {
            var urlVisible by remember { mutableStateOf(false) }
            val visibilityDescription = stringResource(if (urlVisible) R.string.tv_hide_url else R.string.tv_show_url)
            val inputFocus = remember { FocusRequester() }
            var inputFocused by remember { mutableStateOf(false) }
            var initialFocusAssigned by remember { mutableStateOf(false) }
            Dialog(onDismissRequest = ::dismissUrl, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                val dialogWindow = LocalWindowInfo.current
                val dialogFocusManager = LocalFocusManager.current
                val dialogKeyboard = LocalSoftwareKeyboardController.current
                fun submitFromDialog() { dialogKeyboard?.hide(); dialogFocusManager.clearFocus(); submit() }
                fun dismissFromDialog() { dialogKeyboard?.hide(); dismissUrl() }
                val relocation = remember { BringIntoViewRequester() }
                val configuration = LocalConfiguration.current
                val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
                var panelBounds by remember { mutableStateOf(Rect.Zero) }
                val dismissPanel by rememberUpdatedState(::dismissFromDialog)
                LaunchedEffect(inputFocused, imeBottom, configuration.screenHeightDp, configuration.fontScale) {
                    if (inputFocused) { withFrameNanos { }; relocation.bringIntoView() }
                }
                // TV IMEs can remain visible while reporting a zero bottom inset; keep the field above them.
                BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { point -> if (!panelBounds.contains(point)) dismissPanel() }
                }.safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                    val shortWindow = maxHeight < 360.dp * configuration.fontScale
                    val scroll = rememberScrollState()
                    Surface(Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, 460.dp))
                        .onGloballyPositioned { panelBounds = it.boundsInRoot() }, shape = RoundedCornerShape(20.dp), colors = SurfaceDefaults.colors(containerColor = p.panel)) {
                        Column((if (shortWindow) Modifier.verticalScroll(scroll) else Modifier).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.tv_add_url), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Column((if (shortWindow) Modifier else Modifier.weight(1f, fill = false).verticalScroll(scroll)).bringIntoViewRequester(relocation), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.tv_url_label), Modifier.weight(1f), color = p.muted, style = MaterialTheme.typography.labelMedium)
                                    CinemaButton(onClick = { urlVisible = !urlVisible }, modifier = Modifier.heightIn(min = 48.dp).testTag("url_visibility")
                                        .semantics { contentDescription = visibilityDescription }) {
                                        Text(stringResource(if (urlVisible) R.string.tv_hide else R.string.tv_show))
                                    }
                                }
                                BasicTextField(value = url, onValueChange = { if (it.length <= 4096) url = it }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth().focusRequester(inputFocus).onFocusChanged { inputFocused = it.isFocused }.testTag("source_url")
                                        .onPreviewKeyEvent { event ->
                                            // Single-line editors consume vertical arrows; a remote uses them to leave the field.
                                            if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
                                                dialogFocusManager.moveFocus(if (event.key == Key.DirectionUp) FocusDirection.Up else FocusDirection.Down)
                                            } else false
                                        }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.text, textDirection = TextDirection.Ltr), cursorBrush = SolidColor(p.focus),
                                    visualTransformation = if (urlVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank() && !state.importing ) submitFromDialog() }),
                                    decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(p.background, RoundedCornerShape(10.dp)).border(if (inputFocused) 2.dp else 1.dp, if (inputFocused) p.focus else p.outline, RoundedCornerShape(10.dp)).padding(16.dp), contentAlignment = Alignment.CenterStart) { inner() } })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                CinemaButton(onClick = ::dismissFromDialog, modifier = Modifier.testTag("url_cancel")) { Text(stringResource(R.string.tv_cancel)) }
                                CinemaButton(onClick = { submitFromDialog() }, primary = true, enabled = url.isNotBlank() && !state.importing, modifier = Modifier.testTag("import_url")) { Text(stringResource(R.string.tv_import_url)) }
                            }
                        }
                    }
                }
                LaunchedEffect(dialogWindow.isWindowFocused) {
                    if (dialogWindow.isWindowFocused && !initialFocusAssigned) {
                        withFrameNanos { }; inputFocus.requestFocus(); initialFocusAssigned = true
                    }
                }
            }
        }
    }
    LaunchedEffect(state.channels, state.selectedId, window.isWindowFocused, enteringUrl, enteringLogin, confirmingRemoval, managingSources, state.needsHttpApproval) {
        if (!window.isWindowFocused || enteringUrl || enteringLogin || confirmingRemoval || managingSources || state.needsHttpApproval) return@LaunchedEffect
        withFrameNanos { }
        if (targetId == null) { list.scrollToItem(0); withFrameNanos { }; languageFocus.requestFocus() }
        else { list.scrollToItem(state.channels.indexOfFirst { it.id == targetId } + 1); withFrameNanos { }; entryFocus.requestFocus() }
    }
}

@Composable
private fun SourceActions(importing: Boolean, onLogin: () -> Unit, onUrl: () -> Unit, onFile: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CinemaButton(onClick = onLogin, primary = true, enabled = !importing, modifier = Modifier.testTag("add_login")) { CinemaIcon(CinemaSymbol.SERVER, tint = LocalContentColor.current); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tv_server_login), fontWeight = FontWeight.Bold) }
        CinemaButton(onClick = onUrl, enabled = !importing, modifier = Modifier.testTag("add_url")) { CinemaIcon(CinemaSymbol.LINK, tint = LocalContentColor.current); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tv_url_short)) }
        CinemaButton(onClick = onFile, enabled = !importing, modifier = Modifier.testTag("import_file")) { CinemaIcon(CinemaSymbol.FILE, tint = LocalContentColor.current); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tv_file_short)) }
    }
}

@Composable
fun TvPlayback(title: String, status: PlaybackStatus, onBack: () -> Unit, onRetry: () -> Unit, player: @Composable (Modifier) -> Unit) {
    val retryFocus = remember { FocusRequester() }
    CinemaTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            player(Modifier.fillMaxSize())
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .88f), Color.Transparent))).safeDrawingPadding().padding(horizontal = 32.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    CinemaButton(onClick = onBack) { CinemaIcon(CinemaSymbol.BACK, tint = LocalContentColor.current); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tv_back)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AmarWordmark(Cinema.Sand, Modifier.testTag("app_brand"), 12.sp)
                        Text(title, color = Cinema.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (status == PlaybackStatus.FAILED) CinemaButton(onClick = onRetry, primary = true, modifier = Modifier.focusRequester(retryFocus).testTag("playback_retry")) { Text(stringResource(R.string.tv_retry)) }
                }
                Text(stringResource(playbackMessage(status)), Modifier.testTag("playback_status"), color = if (status == PlaybackStatus.FAILED) Cinema.Dark.error else Cinema.Sand, style = MaterialTheme.typography.labelMedium)
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
    ImportError.TIME_LIMIT -> R.string.tv_import_timeout
    ImportError.LINE_TOO_LONG, ImportError.FIELD_TOO_LONG -> R.string.tv_import_field
    ImportError.TOO_LARGE, ImportError.TOO_MANY_ENTRIES -> R.string.tv_limit
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
