package com.fireamun.iptv.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.fireamun.iptv.core.designsystem.*
import kotlinx.coroutines.launch
import com.fireamun.iptv.core.designsystem.R as DR

/** First-launch agreement for the remote. Usage statistics are a separate choice from accepting the Terms. */
@Composable
fun TvLegalGate(onAccept: (analytics: Boolean) -> Unit, onDecline: () -> Unit, onChangeLanguage: () -> Unit) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    var analytics by rememberSaveable { mutableStateOf(true) }
    var reading by rememberSaveable { mutableStateOf<LegalDocument?>(null) }
    val window = LocalWindowInfo.current
    val first = remember { FocusRequester() }
    LaunchedEffect(window.isWindowFocused) { if (window.isWindowFocused && reading == null) runCatching { first.requestFocus() } }
    CinemaTheme {
        val p = cinemaPalette()
        Surface(Modifier.fillMaxSize().testTag("legal_gate"), colors = SurfaceDefaults.colors(containerColor = p.background, contentColor = p.text)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AmarWordmark(p.focus, fontSize = 28.sp)
                    Text(stringResource(DR.string.legal_gate_title), fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() })
                    listOf(DR.string.legal_gate_player_only, DR.string.legal_gate_authorized, DR.string.legal_gate_private).forEach {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            CinemaIcon(CinemaSymbol.CHECK, Modifier.padding(top = 3.dp).size(22.dp), p.focus)
                            Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CinemaButton(onClick = { reading = LegalDocument.TERMS }, modifier = Modifier.fillMaxWidth().focusRequester(first).testTag("legal_read_terms")) {
                        Text(stringResource(DR.string.legal_read_terms))
                    }
                    CinemaButton(onClick = { reading = LegalDocument.PRIVACY }, modifier = Modifier.fillMaxWidth().testTag("legal_read_privacy")) {
                        Text(stringResource(DR.string.legal_read_privacy))
                    }
                    TvToggle(analytics, { analytics = !analytics }, Role.Switch, Modifier.testTag("legal_analytics")) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(DR.string.legal_analytics))
                            Text(stringResource(DR.string.legal_analytics_detail), style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = .75f))
                        }
                    }
                    TvToggle(agreed, { agreed = !agreed }, Role.Checkbox, Modifier.testTag("legal_agree")) {
                        Text(stringResource(DR.string.legal_gate_agree))
                    }
                    CinemaButton(onClick = { onAccept(analytics) }, enabled = agreed, primary = true, modifier = Modifier.fillMaxWidth().testTag("legal_continue")) {
                        Text(stringResource(DR.string.legal_gate_continue), fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CinemaButton(onClick = onDecline, modifier = Modifier.testTag("legal_decline")) { Text(stringResource(DR.string.legal_gate_decline)) }
                        CinemaButton(onClick = onChangeLanguage, modifier = Modifier.testTag("legal_language")) {
                            CinemaIcon(CinemaSymbol.LANGUAGE, tint = LocalContentColor.current); Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.tv_language))
                        }
                    }
                    Text(stringResource(DR.string.legal_gate_decline_hint), style = MaterialTheme.typography.bodySmall, color = p.muted)
                }
            }
        }
        reading?.let { TvLegalDocument(it) { reading = null } }
    }
}

/** A focusable row that shows its on/off state; activated with the remote's centre button. */
@Composable
private fun TvToggle(checked: Boolean, onToggle: () -> Unit, role: Role, modifier: Modifier = Modifier, label: @Composable () -> Unit) {
    val p = cinemaPalette()
    CinemaButton(onClick = onToggle, modifier = modifier.fillMaxWidth().semantics {
        this.role = role
        toggleableState = ToggleableState(checked)
    }) {
        Box(Modifier.size(24.dp).border(BorderStroke(2.dp, if (checked) p.focus else LocalContentColor.current.copy(alpha = .6f)), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center) {
            if (checked) CinemaIcon(CinemaSymbol.CHECK, Modifier.size(18.dp), p.focus)
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) { label() }
    }
}

/** Full-screen offline reader; up and down scroll the text, Back closes it. */
@Composable
fun TvLegalDocument(document: LegalDocument, onClose: () -> Unit) {
    val context = LocalContext.current
    val arabic = legalArabic()
    val text = remember(document, arabic) { loadLegalDocument(context, document, arabic) }
    // English fallback text (the open-source notices) keeps left-to-right punctuation inside the Arabic interface.
    val textDirection = if (arabic && !text.arabic) LayoutDirection.Ltr else LocalLayoutDirection.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogWindow = LocalWindowInfo.current
        val focus = remember { FocusRequester() }
        val list = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val step = with(LocalDensity.current) { 240.dp.toPx() }
        LaunchedEffect(dialogWindow.isWindowFocused) { if (dialogWindow.isWindowFocused) runCatching { focus.requestFocus() } }
        CinemaTheme {
            val p = cinemaPalette()
            Surface(Modifier.fillMaxSize().testTag("legal_document"), colors = SurfaceDefaults.colors(containerColor = p.background, contentColor = p.text)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(DR.string.legal_scroll_hint), style = MaterialTheme.typography.bodySmall, color = p.muted)
                    CompositionLocalProvider(LocalLayoutDirection provides textDirection) { LazyColumn(Modifier.fillMaxSize().focusRequester(focus).onPreviewKeyEvent { event ->
                        val direction = when (event.key) { Key.DirectionDown -> 1f; Key.DirectionUp -> -1f; else -> 0f }
                        if (direction == 0f) false
                        else { if (event.type == KeyEventType.KeyDown) scope.launch { list.animateScrollBy(direction * step) }; true }
                    }.focusable().testTag("legal_document_list"), state = list, verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 48.dp)) {
                        itemsIndexed(text.blocks) { _, block -> TvLegalBlock(block) }
                    } }
                }
            }
        }
    }
}

@Composable
private fun TvLegalBlock(block: LegalBlock) {
    val p = cinemaPalette()
    when (block) {
        is LegalBlock.Title -> Text(block.text, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        is LegalBlock.Heading -> Text(block.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        is LegalBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyLarge)
        is LegalBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("•", style = MaterialTheme.typography.bodyLarge, color = p.focus)
            Text(block.text, style = MaterialTheme.typography.bodyLarge)
        }
        is LegalBlock.License -> Text(block.text, style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
}

/** Settings section: documents, the usage-statistics switch, contact and version. */
@Composable
internal fun TvAboutSection(analyticsEnabled: Boolean, onAnalyticsChange: (Boolean) -> Unit, appVersion: String, onRead: (LegalDocument) -> Unit) {
    val p = cinemaPalette()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(DR.string.legal_about_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CinemaButton(onClick = { onRead(LegalDocument.TERMS) }, modifier = Modifier.testTag("about_terms")) { Text(stringResource(DR.string.legal_terms)) }
            CinemaButton(onClick = { onRead(LegalDocument.PRIVACY) }, modifier = Modifier.testTag("about_privacy")) { Text(stringResource(DR.string.legal_privacy)) }
            CinemaButton(onClick = { onRead(LegalDocument.NOTICES) }, modifier = Modifier.testTag("about_notices")) { Text(stringResource(DR.string.legal_notices)) }
        }
        Box(Modifier.widthIn(max = 640.dp)) {
            TvToggle(analyticsEnabled, { onAnalyticsChange(!analyticsEnabled) }, Role.Switch, Modifier.testTag("about_analytics")) {
                Text(stringResource(DR.string.legal_analytics))
            }
        }
        Text(stringResource(DR.string.legal_contact, AmarLegal.SUPPORT_EMAIL), style = MaterialTheme.typography.bodySmall, color = p.muted)
        Text(stringResource(DR.string.legal_privacy_online, AmarLegal.privacyPolicyUrl(legalArabic()).removePrefix("https://")),
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.testTag("about_privacy_online"))
        if (appVersion.isNotEmpty()) Text(stringResource(DR.string.legal_version, appVersion), style = MaterialTheme.typography.bodySmall, color = p.muted,
            modifier = Modifier.testTag("about_version"))
    }
}
