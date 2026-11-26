package com.fireamun.iptv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.fireamun.iptv.core.designsystem.*
import com.fireamun.iptv.core.model.*

@Composable
internal fun tvSourceName(source: SavedSource): String = source.name ?: stringResource(when (source.kind) {
    SourceKind.FILE -> R.string.tv_file_playlist
    SourceKind.URL -> R.string.tv_url_playlist
    SourceKind.XTREAM -> R.string.tv_server_playlist
    null -> R.string.tv_generic_playlist
}, source.ordinal)

@Composable
internal fun TvSources(state: LibraryState, onDismiss: () -> Unit, onSelect: (String) -> Unit, onRename: (String, String) -> Unit, onRemove: (String) -> Unit) {
    val p = cinemaPalette()
    var editing by remember { mutableStateOf<SavedSource?>(null) }
    var removing by remember { mutableStateOf<SavedSource?>(null) }
    var draft by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val list = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val activeIndex = state.sources.indexOfFirst { it.id == state.activeSourceId }.coerceAtLeast(0)
    val selected = editing
    val deleting = removing
    fun dismiss() { keyboard?.hide(); onDismiss() }
    Dialog(onDismissRequest = ::dismiss) {
        val window = LocalWindowInfo.current
        Surface(Modifier.widthIn(max = 640.dp).heightIn(max = 460.dp).imePadding(), shape = RoundedCornerShape(20.dp), colors = SurfaceDefaults.colors(containerColor = p.panel)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    selected != null -> {
                        val valid = normalizeSourceName(draft) != null
                        fun save() { if (valid) { onRename(selected.id, draft); dismiss() } }
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.tv_rename_source), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.tv_source_name), color = p.muted)
                            BasicTextField(draft, { if (it.length <= 128) draft = it }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(first).onFocusChanged { focused = it.isFocused }.remoteTyping(draft) { if (it.length <= 128) draft = it }.testTag("source_name"),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.text), cursorBrush = SolidColor(p.focus),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }),
                                decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(p.background, RoundedCornerShape(10.dp)).border(if (focused) 2.dp else 1.dp, if (focused) p.focus else p.outline, RoundedCornerShape(10.dp)).padding(14.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) { inner() } })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CinemaButton(onClick = { keyboard?.hide(); editing = null }) { Text(stringResource(R.string.tv_cancel)) }
                            CinemaButton(onClick = ::save, primary = true, enabled = valid && !state.importing, modifier = Modifier.testTag("rename_source_confirm")) { Text(stringResource(R.string.tv_save_source)) }
                        }
                    }
                    deleting != null -> {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.tv_remove_named_source, tvSourceName(deleting)), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.tv_remove_one_source_body), color = p.muted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CinemaButton(onClick = { removing = null }, modifier = Modifier.focusRequester(first).testTag("remove_cancel")) { Text(stringResource(R.string.tv_cancel)) }
                            CinemaButton(onClick = { onRemove(deleting.id); dismiss() }, enabled = !state.importing, modifier = Modifier.testTag("remove_confirm")) { Text(stringResource(R.string.tv_remove_source), color = p.error) }
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.tv_playlists_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth().testTag("source_manager"), state = list, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(state.sources, key = { _, source -> source.id }) { index, source ->
                                val name = tvSourceName(source)
                                Column(Modifier.fillMaxWidth().background(p.background, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CinemaButton(onClick = { onSelect(source.id); dismiss() }, enabled = !state.importing,
                                        modifier = Modifier.fillMaxWidth().then(if (index == activeIndex) Modifier.focusRequester(first) else Modifier).testTag("source_pick_$index")) {
                                        if (index == activeIndex) { CinemaIcon(CinemaSymbol.CHECK, tint = LocalContentColor.current); Spacer(Modifier.width(12.dp)) }
                                        Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                        if (index == activeIndex) { Spacer(Modifier.width(16.dp)); Text(stringResource(R.string.tv_source_active), style = MaterialTheme.typography.labelSmall) }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        CinemaButton(onClick = { draft = name; editing = source }, enabled = !state.importing, modifier = Modifier.testTag("source_rename_$index")) { Text(stringResource(R.string.tv_rename_source)) }
                                        CinemaButton(onClick = { removing = source }, enabled = !state.importing, modifier = Modifier.testTag("source_remove_$index")) { Text(stringResource(R.string.tv_remove_source), color = p.error) }
                                    }
                                }
                            }
                        }
                        CinemaButton(onClick = ::dismiss, modifier = Modifier.testTag("sources_close")) { Text(stringResource(R.string.tv_close_sources)) }
                    }
                }
            }
        }
        LaunchedEffect(window.isWindowFocused, selected?.id, deleting?.id, activeIndex) {
            if (window.isWindowFocused) {
                if (selected == null && deleting == null && state.sources.isNotEmpty()) list.scrollToItem(activeIndex)
                withFrameNanos { }
                first.requestFocus()
            }
        }
    }
}
