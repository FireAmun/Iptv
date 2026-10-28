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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fireamun.iptv.core.designsystem.*
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
            fun save() { if (valid) { val submitted = draft; draft = ""; onRename(selected.id, submitted); onDismiss() } }
            MobileFormDialog(stringResource(R.string.mobile_rename_source), { draft = ""; onDismiss() }, actions = {
                MobileFormActions(stringResource(R.string.mobile_save_source), ::save, { draft = ""; editing = null }, "rename_source_confirm", valid && !state.importing)
            }) {
                MobileFormField(draft, { if (it.length <= 128) draft = it }, stringResource(R.string.mobile_source_name), "source_name",
                    isError = !valid, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }))
            }
        }
        deleting != null -> MobileFormDialog(stringResource(R.string.mobile_remove_named_source, mobileSourceName(deleting)), onDismiss, actions = {
            MobileFormActions(stringResource(R.string.mobile_remove_source), { onRemove(deleting.id); onDismiss() }, { removing = null },
                "remove_confirm", !state.importing, "remove_cancel", destructive = true)
        }) {
            Text(stringResource(R.string.mobile_remove_one_source_body), color = p.muted)
        }
        else -> MobileFormDialog(stringResource(R.string.mobile_playlists_title), onDismiss, actions = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sources_close")) { Text(stringResource(R.string.mobile_close_sources), color = p.muted) }
        }) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp).testTag("source_manager"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(state.sources, key = { _, source -> source.id }) { index, source ->
                    val name = mobileSourceName(source)
                    val active = source.id == state.activeSourceId
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(onClick = { onSelect(source.id); onDismiss() }, enabled = !state.importing,
                            color = if (active) p.focus.copy(alpha = .10f) else p.panel, border = BorderStroke(1.dp, if (active) p.focus else p.outline.copy(alpha = .4f)), shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("source_pick_$index")) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                    if (active) Text(stringResource(R.string.mobile_source_active), color = p.focus, style = MaterialTheme.typography.labelMedium)
                                }
                                if (active) CinemaIcon(CinemaSymbol.CHECK, tint = p.focus)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { draft = name; editing = source }, enabled = !state.importing, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("source_rename_$index")) { Text(stringResource(R.string.mobile_rename_source), color = if (state.importing) p.muted.copy(alpha = .38f) else p.text) }
                            TextButton(onClick = { removing = source }, enabled = !state.importing, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("source_remove_$index")) { Text(stringResource(R.string.mobile_remove_source), color = if (state.importing) p.muted.copy(alpha = .38f) else p.error) }
                        }
                    }
                }
            }
        }
    }
}
