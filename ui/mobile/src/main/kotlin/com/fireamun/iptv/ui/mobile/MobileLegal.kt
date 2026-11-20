package com.fireamun.iptv.ui.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fireamun.iptv.core.designsystem.*
import com.fireamun.iptv.core.designsystem.R as DR

/** First-launch agreement. Usage statistics are a separate choice from accepting the Terms. */
@Composable
fun MobileLegalGate(onAccept: (analytics: Boolean) -> Unit, onDecline: () -> Unit, onChangeLanguage: () -> Unit) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    var analytics by rememberSaveable { mutableStateOf(true) }
    var reading by rememberSaveable { mutableStateOf<LegalDocument?>(null) }
    CinemaTheme {
        val p = cinemaPalette()
        Surface(Modifier.fillMaxSize().testTag("legal_gate"), color = p.background, contentColor = p.text) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AmarWordmark(p.focus)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onChangeLanguage, modifier = Modifier.heightIn(min = 48.dp).testTag("legal_language")) {
                            CinemaIcon(CinemaSymbol.LANGUAGE, Modifier.size(18.dp), p.muted); Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.mobile_language), color = p.text)
                        }
                    }
                    Text(stringResource(DR.string.legal_gate_title), fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() })
                    listOf(DR.string.legal_gate_player_only, DR.string.legal_gate_authorized, DR.string.legal_gate_private).forEach {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CinemaIcon(CinemaSymbol.CHECK, Modifier.padding(top = 2.dp).size(20.dp), p.focus)
                            Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { reading = LegalDocument.TERMS }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("legal_read_terms"),
                            shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text)) { Text(stringResource(DR.string.legal_terms)) }
                        OutlinedButton(onClick = { reading = LegalDocument.PRIVACY }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("legal_read_privacy"),
                            shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text)) { Text(stringResource(DR.string.legal_privacy)) }
                    }
                    Surface(color = p.panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, p.outline.copy(alpha = .25f))) {
                        Row(Modifier.fillMaxWidth().toggleable(analytics, role = Role.Switch, onValueChange = { analytics = it })
                            .padding(16.dp).testTag("legal_analytics"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(DR.string.legal_analytics), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(DR.string.legal_analytics_detail), style = MaterialTheme.typography.bodySmall, color = p.muted)
                            }
                            Switch(checked = analytics, onCheckedChange = null)
                        }
                    }
                }
                HorizontalDivider(color = p.outline.copy(alpha = .25f))
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(agreed, role = Role.Checkbox, onValueChange = { agreed = it }).testTag("legal_agree"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = agreed, onCheckedChange = null)
                        Text(stringResource(DR.string.legal_gate_agree), style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { onAccept(analytics) }, enabled = agreed, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("legal_continue"),
                        shape = RoundedCornerShape(12.dp)) { Text(stringResource(DR.string.legal_gate_continue), fontWeight = FontWeight.Bold) }
                    TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("legal_decline")) {
                        Text(stringResource(DR.string.legal_gate_decline), color = p.muted)
                    }
                }
            }
        }
        reading?.let { MobileLegalDocument(it) { reading = null } }
    }
}

/** Full-screen offline reader for a bundled legal document. */
@Composable
fun MobileLegalDocument(document: LegalDocument, onClose: () -> Unit) {
    val context = LocalContext.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    // The header already names the document, so its own title line is not repeated.
    val text = remember(document, arabic) { loadLegalDocument(context, document, arabic) }
    val blocks = remember(text) { text.blocks.filterNot { it is LegalBlock.Title } }
    // English fallback text (the open-source notices) keeps left-to-right punctuation inside the Arabic interface.
    val direction = if (arabic && !text.arabic) LayoutDirection.Ltr else LocalLayoutDirection.current
    val closeLabel = stringResource(DR.string.legal_close)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CinemaTheme {
            val p = cinemaPalette()
            Surface(Modifier.fillMaxSize().testTag("legal_document"), color = p.background, contentColor = p.text) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(when (document) {
                            LegalDocument.TERMS -> DR.string.legal_terms
                            LegalDocument.PRIVACY -> DR.string.legal_privacy
                            LegalDocument.NOTICES -> DR.string.legal_notices
                        }), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("legal_document_close").semantics { contentDescription = closeLabel }) {
                            CinemaIcon(CinemaSymbol.CLOSE, tint = p.muted)
                        }
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        LazyColumn(Modifier.fillMaxSize().testTag("legal_document_list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(blocks) { _, block -> MobileLegalBlock(block) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileLegalBlock(block: LegalBlock) {
    val p = cinemaPalette()
    when (block) {
        is LegalBlock.Title -> Text(block.text, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        is LegalBlock.Heading -> Text(block.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        is LegalBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
        is LegalBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("•", style = MaterialTheme.typography.bodyMedium, color = p.focus)
            Text(block.text, style = MaterialTheme.typography.bodyMedium)
        }
        is LegalBlock.License -> Text(block.text, style = MaterialTheme.typography.bodySmall, color = p.muted)
    }
}

/** Settings card: documents, the usage-statistics switch, contact and version. */
@Composable
internal fun MobileAboutCard(analyticsEnabled: Boolean, onAnalyticsChange: (Boolean) -> Unit, appVersion: String, onRead: (LegalDocument) -> Unit) {
    val p = cinemaPalette()
    Surface(color = p.panel, contentColor = p.text, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, p.outline.copy(alpha = .25f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(DR.string.legal_about_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            listOf(LegalDocument.TERMS to DR.string.legal_terms, LegalDocument.PRIVACY to DR.string.legal_privacy, LegalDocument.NOTICES to DR.string.legal_notices)
                .forEach { (document, label) ->
                    TextButton(onClick = { onRead(document) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("about_${document.name.lowercase()}")) {
                        Text(stringResource(label), Modifier.weight(1f), color = p.text)
                    }
                }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(analyticsEnabled, role = Role.Switch, onValueChange = onAnalyticsChange)
                .padding(horizontal = 12.dp).testTag("about_analytics"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(DR.string.legal_analytics), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = analyticsEnabled, onCheckedChange = null)
            }
            Text(stringResource(DR.string.legal_contact, AmarLegal.SUPPORT_EMAIL), style = MaterialTheme.typography.bodySmall, color = p.muted,
                modifier = Modifier.padding(horizontal = 12.dp))
            if (appVersion.isNotEmpty()) Text(stringResource(DR.string.legal_version, appVersion), style = MaterialTheme.typography.bodySmall, color = p.muted,
                modifier = Modifier.padding(horizontal = 12.dp).testTag("about_version"))
        }
    }
}
