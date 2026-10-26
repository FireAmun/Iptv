package com.fireamun.iptv.ui.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fireamun.iptv.core.designsystem.cinemaPalette
import com.fireamun.iptv.core.model.*

@Composable
internal fun mobileSourceName(source: SavedSource): String = source.name ?: stringResource(when (source.kind) {
    SourceKind.FILE -> R.string.mobile_file_playlist
    SourceKind.URL -> R.string.mobile_url_playlist
    SourceKind.XTREAM -> R.string.mobile_server_playlist
    null -> R.string.mobile_generic_playlist
}, source.ordinal)

@Composable
internal fun MobileSources(state: LibraryState, onDismiss: () -> Unit, onSelect: (String) -> Unit, onRename: (String, String) -> Unit, onRemove: (String) -> Unit) {
    val p = cinemaPalette()
    var editing by remember { mutableStateOf<SavedSource?>(null) }
    var removing by remember { mutableStateOf<SavedSource?>(null) }
    var draft by remember { mutableStateOf("") }
    val selected = editing
    val deleting = removing
    when {
        selected != null -> {
            val valid = normalizeSourceName(draft) != null
            fun save() { if (valid) { onRename(selected.id, draft); onDismiss() } }
            AlertDialog(onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.mobile_rename_source)) },
                text = { OutlinedTextField(draft, { if (it.length <= 128) draft = it }, singleLine = true, label = { Text(stringResource(R.string.mobile_source_name)) },
                    modifier = Modifier.fillMaxWidth().testTag("source_name"), isError = !valid, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() })) },
                confirmButton = { Button(onClick = ::save, enabled = valid && !state.importing, modifier = Modifier.testTag("rename_source_confirm")) { Text(stringResource(R.string.mobile_save_source)) } },
                dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.mobile_cancel), color = p.text) } })
        }
        deleting != null -> AlertDialog(onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.mobile_remove_named_source, mobileSourceName(deleting))) },
            text = { Text(stringResource(R.string.mobile_remove_one_source_body)) },
            confirmButton = { Button(onClick = { onRemove(deleting.id); onDismiss() }, enabled = !state.importing, modifier = Modifier.testTag("remove_confirm")) { Text(stringResource(R.string.mobile_remove_source)) } },
            dismissButton = { TextButton(onClick = { removing = null }, modifier = Modifier.testTag("remove_cancel")) { Text(stringResource(R.string.mobile_cancel), color = p.text) } })
        else -> AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.mobile_playlists_title)) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp).testTag("source_manager"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.sources, key = { _, source -> source.id }) { index, source ->
                        val name = mobileSourceName(source)
                        val active = source.id == state.activeSourceId
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(onClick = { onSelect(source.id); onDismiss() }, enabled = !state.importing,
                                color = if (active) p.raised else p.panel, border = BorderStroke(1.dp, if (active) p.focus else p.outline), shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("source_pick_$index")) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                    if (active) Text(stringResource(R.string.mobile_source_active), color = p.focus, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = { draft = name; editing = source }, enabled = !state.importing, modifier = Modifier.testTag("source_rename_$index")) { Text(stringResource(R.string.mobile_rename_source), color = p.text) }
                                TextButton(onClick = { removing = source }, enabled = !state.importing, modifier = Modifier.testTag("source_remove_$index")) { Text(stringResource(R.string.mobile_remove_source), color = p.text) }
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("sources_close")) { Text(stringResource(R.string.mobile_close_sources), color = p.text) } })
    }
}
