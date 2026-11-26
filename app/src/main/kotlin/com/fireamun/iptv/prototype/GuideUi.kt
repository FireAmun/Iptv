package com.fireamun.iptv.prototype

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fireamun.iptv.core.designsystem.cinemaPalette
import com.fireamun.iptv.core.designsystem.remoteTyping
import com.fireamun.iptv.core.designsystem.R as DR

/** Settings card shared by phone and TV: guide status, Refresh guide and the optional manual XMLTV link. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuideSettings(refresher: GuideRefresher, sourceId: String?) {
    val p = cinemaPalette()
    val context = LocalContext.current
    val ui by refresher.state.collectAsState()
    var link by remember(sourceId) { mutableStateOf("") }
    var invalid by remember(sourceId) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("guide_settings"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(DR.string.guide_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(DR.string.guide_description), color = p.muted, style = MaterialTheme.typography.bodyMedium)
        if (sourceId == null || ui.sourceId != sourceId) {
            Text(stringResource(DR.string.guide_no_source), color = p.muted)
            return@Column
        }
        val saved = ui.saved
        val fetched = saved?.fetchedAtMs
        val status = when {
            ui.refreshing -> stringResource(DR.string.guide_refreshing)
            ui.noLink -> stringResource(DR.string.guide_status_no_link)
            saved?.error != null -> stringResource(DR.string.guide_status_failed)
            fetched != null && saved.programmeCount == 0 -> stringResource(DR.string.guide_status_empty)
            fetched != null -> String.format(stringResource(DR.string.guide_status_ready),
                DateUtils.formatDateTime(context, fetched, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH), saved.programmeCount)
            else -> stringResource(DR.string.guide_status_none)
        }
        Text(status, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("guide_status").semantics { liveRegion = LiveRegionMode.Polite })
        val action = Modifier.heightIn(min = 48.dp).focusProperties { canFocus = true }
        Button(onClick = refresher::refresh, enabled = !ui.refreshing, modifier = action.testTag("guide_refresh")) { Text(stringResource(DR.string.guide_refresh)) }
        Text(stringResource(if (saved?.hasManualLink == true) DR.string.guide_link_manual else DR.string.guide_link_automatic), color = p.muted,
            modifier = Modifier.testTag("guide_link_mode"))
        OutlinedTextField(link, { link = it; invalid = false }, singleLine = true, label = { Text(stringResource(DR.string.guide_link_label)) },
            isError = invalid, supportingText = if (invalid) ({ Text(stringResource(DR.string.guide_link_invalid)) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { if (refresher.setManualLink(link)) link = "" else invalid = true }),
            modifier = Modifier.fillMaxWidth().remoteTyping(link) { link = it; invalid = false }.testTag("guide_link_input"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { if (refresher.setManualLink(link)) link = "" else invalid = true }, enabled = link.isNotBlank(),
                modifier = action.testTag("guide_link_save")) { Text(stringResource(DR.string.guide_link_save), color = p.text) }
            if (saved?.hasManualLink == true) OutlinedButton(onClick = { refresher.setManualLink(null) }, modifier = action.testTag("guide_link_clear")) {
                Text(stringResource(DR.string.guide_link_clear), color = p.text)
            }
        }
    }
}
