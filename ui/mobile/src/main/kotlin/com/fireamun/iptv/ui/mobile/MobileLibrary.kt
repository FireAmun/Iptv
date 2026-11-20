package com.fireamun.iptv.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fireamun.iptv.core.designsystem.*
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.LibraryState
import com.fireamun.iptv.core.model.PlaybackStatus
import com.fireamun.iptv.core.model.ContentKind
import kotlinx.coroutines.launch

@Composable
fun MobileWelcome(state: LibraryState, onChangeLanguage: () -> Unit, onImportFile: () -> Unit, onImportUrl: (String) -> Unit, onSelectChannel: (Int) -> Unit, onCancelImport: () -> Unit, onApproveHttp: () -> Unit, onDismissHttp: () -> Unit, onChangeAppearance: () -> Unit, onServerLogin: (String, String, String, String) -> Unit, onLibraryBack: () -> Unit, onRefreshSource: () -> Unit, onRemoveSource: () -> Unit, onSelectSource: (String) -> Unit, onRenameSource: (String, String) -> Unit, onRemoveSourceById: (String) -> Unit,
    analyticsEnabled: Boolean = false, onAnalyticsChange: (Boolean) -> Unit = {}, appVersion: String = "") {
    var url by remember { mutableStateOf("") }
    var enteringUrl by remember { mutableStateOf(false) }
    var enteringLogin by remember { mutableStateOf(false) }
    var confirmingRemoval by remember { mutableStateOf(false) }
    var managingSources by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The rendered order also resolves sidebar anchors, so inserting a section cannot stale an index.
    val settingsSections = remember { listOf("header", "status", "library", "add_sources", "appearance", "language", "about") }
    var reading by remember { mutableStateOf<com.fireamun.iptv.core.designsystem.LegalDocument?>(null) }
    var selectedSettingsSection by remember { mutableStateOf("library") }
    fun showSettingsSection(key: String) {
        selectedSettingsSection = key
        scope.launch { list.animateScrollToItem(settingsSections.indexOf(key)) }
    }
    fun submit() { val submitted = url; url = ""; enteringUrl = false; keyboard?.hide(); onImportUrl(submitted) }
    LaunchedEffect(state.importing, state.error) {
        if (state.importing || state.error != null) list.scrollToItem(0)
    }
    fun dismissUrl() { enteringUrl = false; url = ""; keyboard?.hide() }
    CinemaTheme {
        val p = cinemaPalette()
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide) {
                        Column(Modifier.width(220.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.mobile_settings_title), fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp)
                            listOf(
                                Triple("library", CinemaSymbol.FILE, R.string.mobile_library),
                                Triple("appearance", CinemaSymbol.APPEARANCE, R.string.mobile_appearance_title),
                                Triple("language", CinemaSymbol.LANGUAGE, R.string.mobile_language_title),
                                Triple("about", CinemaSymbol.SETTINGS, com.fireamun.iptv.core.designsystem.R.string.legal_about_title),
                            ).forEach { (key, icon, label) ->
                                val active = selectedSettingsSection == key
                                var focused by remember(key) { mutableStateOf(false) }
                                Surface(onClick = { showSettingsSection(key) },
                                    color = if (active) p.focus.copy(alpha = .10f) else p.background,
                                    contentColor = if (active) p.focus else p.text,
                                    shape = RoundedCornerShape(12.dp),
                                    border = if (focused) androidx.compose.foundation.BorderStroke(2.dp, p.focus) else null,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings_anchor_$key").onFocusChanged { focused = it.hasFocus }.semantics { selected = active }) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        CinemaIcon(icon, Modifier.size(20.dp), if (active) p.focus else p.muted)
                                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                        VerticalDivider(color = p.outline.copy(alpha = .25f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(modifier = Modifier.imePadding().widthIn(max = 800.dp).fillMaxWidth().testTag("library_list"), state = list,
                            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(settingsSections, key = { it }) { section ->
                                when (section) {
                                    "header" -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(stringResource(if (wide) R.string.mobile_library else R.string.mobile_settings_title), fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp)
                                            Text(stringResource(R.string.mobile_settings_description), color = p.muted, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    "status" -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (state.importing) {
                                                LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.focus)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(stringResource(if (state.switchingSource) R.string.mobile_switching_source else if (state.saving) R.string.mobile_saving else if (state.restoring) R.string.mobile_restoring else R.string.mobile_loading), Modifier.weight(1f))
                                                    if (!state.saving && !state.restoring) TextButton(onClick = onCancelImport) { Text(stringResource(R.string.mobile_cancel), color = p.text) }
                                                }
                                            }
                                            state.error?.let { Text(stringResource(importMessage(it)), color = p.error, modifier = Modifier.testTag("import_error")) }
                                        }
                                    }
                                    "library" -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (!wide) Text(stringResource(R.string.mobile_library), color = p.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                            SettingsCard {
                                                if (state.entryCount == 0 && !state.hasSavedSource) {
                                                    Text(stringResource(R.string.mobile_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                                    Text(stringResource(R.string.mobile_description), style = MaterialTheme.typography.bodyMedium, color = p.muted)
                                                } else {
                                                    val active = state.sources.firstOrNull { it.id == state.activeSourceId }
                                                    if (state.hasSavedSource) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            CinemaIcon(CinemaSymbol.CHECK, Modifier.size(16.dp), p.focus)
                                                            Text(stringResource(R.string.mobile_source_active), color = p.focus, style = MaterialTheme.typography.labelMedium)
                                                        }
                                                    }
                                                    Text(active?.let { mobileSourceName(it) } ?: state.sourceName ?: state.collectionTitle ?: stringResource(R.string.mobile_library),
                                                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                    Text(stringResource(if (state.hasSavedSource) R.string.mobile_loaded_saved else R.string.mobile_loaded, state.entryCount), style = MaterialTheme.typography.bodyMedium, color = p.muted)
                                                    state.savedAtMillis?.let {
                                                        Text(stringResource(R.string.mobile_saved_at, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it))),
                                                            color = p.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("saved_source"))
                                                    }
                                                    if (state.canGoBack) TextButton(onClick = onLibraryBack, modifier = Modifier.heightIn(min = 48.dp).testTag("library_back")) {
                                                        CinemaIcon(CinemaSymbol.BACK, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.mobile_back), color = p.text)
                                                    }
                                                }
                                                if (state.hasSavedSource) {
                                                    if (state.sources.isNotEmpty()) OutlinedButton(onClick = { managingSources = true }, enabled = !state.importing,
                                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manage_sources"), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text)) {
                                                        CinemaIcon(CinemaSymbol.CATEGORIES, Modifier.size(20.dp), if (state.importing) p.muted.copy(alpha = .38f) else p.text)
                                                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.mobile_manage_sources, state.sources.size))
                                                    }
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        TextButton(onClick = onRefreshSource, enabled = !state.importing && !state.storageBlocked,
                                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("refresh_source")) {
                                                            Text(stringResource(R.string.mobile_refresh_source), color = if (state.importing || state.storageBlocked) p.muted.copy(alpha = .38f) else p.text)
                                                        }
                                                        TextButton(onClick = { confirmingRemoval = true }, enabled = !state.importing,
                                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("remove_source")) {
                                                            Text(stringResource(R.string.mobile_remove_source), color = if (state.importing) p.muted.copy(alpha = .38f) else p.error)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "add_sources" -> {
                                        SettingsCard {
                                            Text(stringResource(R.string.mobile_add_source_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Button(onClick = { enteringLogin = true }, enabled = !state.importing && !state.storageBlocked,
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("add_login"), shape = RoundedCornerShape(12.dp)) {
                                                CinemaIcon(CinemaSymbol.SERVER, Modifier.size(20.dp), if (state.importing || state.storageBlocked) p.muted.copy(alpha = .38f) else Cinema.Night)
                                                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.mobile_server_login), fontWeight = FontWeight.Bold)
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                OutlinedButton(onClick = { enteringUrl = true }, enabled = !state.importing && !state.storageBlocked,
                                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("add_url"), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text)) { Text(stringResource(R.string.mobile_add_url)) }
                                                OutlinedButton(onClick = { url = ""; keyboard?.hide(); onImportFile() }, enabled = !state.importing && !state.storageBlocked,
                                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("import_file"), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text)) { Text(stringResource(R.string.mobile_import_file)) }
                                            }
                                        }
                                    }
                                    "appearance" -> {
                                        SettingsCard {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                CinemaIcon(CinemaSymbol.APPEARANCE, tint = p.muted)
                                                Text(stringResource(R.string.mobile_appearance_title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                                TextButton(onClick = onChangeAppearance, modifier = Modifier.heightIn(min = 48.dp).testTag("appearance_switch")) {
                                                    Text(stringResource(if (isSystemInDarkTheme()) R.string.mobile_light else R.string.mobile_dark), color = p.text)
                                                }
                                            }
                                        }
                                    }
                                    "language" -> {
                                        SettingsCard {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                CinemaIcon(CinemaSymbol.LANGUAGE, tint = p.muted)
                                                Text(stringResource(R.string.mobile_language_title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                                TextButton(onClick = onChangeLanguage, modifier = Modifier.heightIn(min = 48.dp).testTag("language_switch")) {
                                                    Text(stringResource(R.string.mobile_language), color = p.text)
                                                }
                                            }
                                        }
                                    }
                                    "about" -> MobileAboutCard(analyticsEnabled, onAnalyticsChange, appVersion) { reading = it }
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
        reading?.let { MobileLegalDocument(it) { reading = null } }
        if (managingSources) MobileSources(state, { managingSources = false }, onSelectSource, onRenameSource, onRemoveSourceById)
        if (enteringLogin) MobileServerLogin(onDismiss = { enteringLogin = false }, onSubmit = { name, server, username, password -> enteringLogin = false; onServerLogin(name, server, username, password) })
        if (confirmingRemoval) {
            val source = state.sources.firstOrNull { it.id == state.activeSourceId }
            MobileFormDialog(if (source == null) stringResource(R.string.mobile_remove_title) else stringResource(R.string.mobile_remove_named_source, mobileSourceName(source)), { confirmingRemoval = false }, actions = {
                MobileFormActions(stringResource(R.string.mobile_remove_source), { confirmingRemoval = false; onRemoveSource() }, { confirmingRemoval = false },
                    "remove_confirm", cancelTag = "remove_cancel", destructive = true)
            }) {
                Text(stringResource(R.string.mobile_remove_one_source_body), color = p.muted)
            }
        }
        if (enteringUrl) {
            var urlVisible by remember { mutableStateOf(false) }
            val visibilityDescription = stringResource(if (urlVisible) R.string.mobile_hide_url else R.string.mobile_show_url)
            MobileFormDialog(stringResource(R.string.mobile_add_url), ::dismissUrl, actions = {
                MobileFormActions(stringResource(R.string.mobile_import_url), ::submit, ::dismissUrl, "import_url", url.isNotBlank(), cancelTag = "url_cancel")
            }) {
                MobileFormField(value = url, onValueChange = { if (it.length <= 4096) url = it }, label = stringResource(R.string.mobile_url_label), tag = "source_url",
                    visualTransformation = if (urlVisible) VisualTransformation.None else PasswordVisualTransformation(), uri = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) submit() }),
                    labelAction = {
                        TextButton(onClick = { urlVisible = !urlVisible }, modifier = Modifier.heightIn(min = 48.dp).testTag("url_visibility")
                            .semantics { contentDescription = visibilityDescription }) {
                            Text(stringResource(if (urlVisible) R.string.mobile_hide else R.string.mobile_show), color = p.focus)
                        }
                    })
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val p = cinemaPalette()
    Surface(color = p.panel, contentColor = p.text, shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, p.outline.copy(alpha = .25f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun MobilePlayback(title: String, status: PlaybackStatus, onBack: () -> Unit, onRetry: () -> Unit, player: @Composable (Modifier) -> Unit) {
    CinemaTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            player(Modifier.fillMaxSize())
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .88f), Color.Transparent))).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = Cinema.Cream)) {
                        CinemaIcon(CinemaSymbol.BACK, tint = Cinema.Cream); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.mobile_back))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AmarWordmark(Cinema.Sand, Modifier.testTag("app_brand"), 11.sp)
                        Text(title, color = Cinema.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (status == PlaybackStatus.FAILED) Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Cinema.Saffron, contentColor = Cinema.Night), modifier = Modifier.testTag("playback_retry")) { Text(stringResource(R.string.mobile_retry)) }
                }
                Text(stringResource(playbackMessage(status)), Modifier.testTag("playback_status"), color = if (status == PlaybackStatus.FAILED) Cinema.Dark.error else Cinema.Sand, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun importMessage(error: ImportError): Int = when (error) {
    ImportError.INVALID_NAME -> R.string.mobile_invalid_name
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
    ImportError.TIME_LIMIT -> R.string.mobile_import_timeout
    ImportError.LINE_TOO_LONG, ImportError.FIELD_TOO_LONG -> R.string.mobile_import_field
    ImportError.TOO_LARGE, ImportError.TOO_MANY_ENTRIES -> R.string.mobile_limit
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
