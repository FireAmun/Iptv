package com.fireamun.iptv.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fireamun.iptv.core.data.ParentalPin
import com.fireamun.iptv.core.designsystem.cinemaPalette
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.fireamun.iptv.core.model.ContentKind
import kotlinx.coroutines.launch
import com.fireamun.iptv.core.designsystem.R as DR

/** Locked labels for the current tab while the parental lock is closed; browsing marks them. */
internal val LocalLockedCategories = compositionLocalOf { emptySet<String>() }

internal enum class PinMode { ENTER, CREATE }

/**
 * PIN entry on an on-screen keypad, so phones and TV remotes behave the same and no IME is needed. Hardware number
 * keys also work. [PinMode.ENTER] checks through [controls]; [PinMode.CREATE] asks twice and stores the new PIN.
 */
@Composable
internal fun PinDialog(mode: PinMode, controls: ParentalControls, television: Boolean, onDone: () -> Unit, onDismiss: () -> Unit,
    onForgot: (() -> Unit)? = null) {
    val p = cinemaPalette()
    val scope = rememberCoroutineScope()
    var digits by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmingForgot by remember { mutableStateOf(false) }
    val wrong = stringResource(DR.string.pin_wrong)
    val mismatch = stringResource(DR.string.pin_mismatch)
    val waitText = stringResource(DR.string.pin_wait)
    fun waitMessage(ms: Long) = String.format(waitText, ((ms + 999) / 1000).toInt())
    fun type(digit: Char) { if (!busy && digits.length < ParentalPin.MAX_LENGTH) { digits += digit; message = null } }
    fun delete() { if (!busy) digits = digits.dropLast(1) }
    fun submit() {
        if (busy || digits.length < ParentalPin.MIN_LENGTH) return
        val entered = digits
        digits = ""
        when (mode) {
            PinMode.ENTER -> {
                busy = true
                scope.launch {
                    try {
                        when (val result = controls.unlock(entered.toCharArray())) {
                            PinResult.Accepted -> onDone()
                            is PinResult.Rejected -> message = if (result.waitMs > 0) waitMessage(result.waitMs) else wrong
                            is PinResult.Wait -> message = waitMessage(result.waitMs)
                        }
                    } finally { busy = false }
                }
            }
            PinMode.CREATE -> {
                val previous = first
                if (previous == null) { first = entered; message = null }
                else if (previous != entered) { first = null; message = mismatch }
                else {
                    busy = true
                    scope.launch { try { controls.setPin(entered.toCharArray()); onDone() } finally { busy = false } }
                }
            }
        }
    }
    val title = stringResource(when {
        mode == PinMode.ENTER -> DR.string.pin_enter_title
        first == null -> DR.string.pin_new_title
        else -> DR.string.pin_confirm_title
    })
    val firstKey = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 420.dp).fillMaxWidth().testTag("pin_dialog").onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val digit = event.utf16CodePoint.toChar().takeIf { it in '0'..'9' }
                when {
                    digit != null -> { type(digit); true }
                    event.key == Key.Backspace || event.key == Key.Delete -> { delete(); true }
                    event.key == Key.NumPadEnter -> { submit(); true }
                    else -> false
                }
            }, color = p.panel, contentColor = p.text, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, p.outline.copy(alpha = .3f))) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp, modifier = Modifier.testTag("pin_title"))
                    Text(stringResource(DR.string.pin_hint), color = p.muted, style = MaterialTheme.typography.bodyMedium)
                    val dots = "●".repeat(digits.length).ifEmpty { " " }
                    Text(dots, fontSize = 28.sp, letterSpacing = 6.sp, modifier = Modifier.heightIn(min = 40.dp).testTag("pin_dots")
                        .semantics { contentDescription = "${digits.length}" })
                    message?.let { Text(it, color = p.error, modifier = Modifier.testTag("pin_message").semantics { liveRegion = LiveRegionMode.Polite }) }
                    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { digit ->
                                PinKey(digit.toString(), "pin_key_$digit", if (digit == '1') Modifier.focusRequester(firstKey) else Modifier) { type(digit) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PinKey(stringResource(DR.string.pin_delete), "pin_key_delete", Modifier, small = true) { delete() }
                        PinKey("0", "pin_key_0", Modifier) { type('0') }
                        PinKey(stringResource(DR.string.pin_ok), "pin_key_ok", Modifier, small = true, enabled = digits.length >= ParentalPin.MIN_LENGTH && !busy) { submit() }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (onForgot != null) TextButton(onClick = { confirmingForgot = true }, modifier = Modifier.focusProperties { canFocus = true }.testTag("pin_forgot")) {
                            Text(stringResource(DR.string.pin_forgot), color = p.muted)
                        } else Spacer(Modifier)
                        TextButton(onClick = onDismiss, modifier = Modifier.focusProperties { canFocus = true }.testTag("pin_cancel")) {
                            Text(stringResource(DR.string.pin_cancel), color = p.text)
                        }
                    }
                }
            }
        }
        val window = LocalWindowInfo.current
        LaunchedEffect(window.isWindowFocused) { if (television && window.isWindowFocused) { withFrameNanos { }; firstKey.requestFocus() } }
    }
    if (confirmingForgot && onForgot != null) AlertDialog(onDismissRequest = { confirmingForgot = false },
        title = { Text(stringResource(DR.string.pin_forgot)) }, text = { Text(stringResource(DR.string.pin_forgot_message)) },
        confirmButton = { TextButton(onClick = { confirmingForgot = false; onForgot() }, modifier = Modifier.testTag("pin_forgot_confirm")) {
            Text(stringResource(DR.string.pin_forgot_confirm), color = p.error) } },
        dismissButton = { TextButton(onClick = { confirmingForgot = false }) { Text(stringResource(DR.string.pin_cancel)) } })
}

@Composable
private fun PinKey(label: String, tag: String, modifier: Modifier, small: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val p = cinemaPalette()
    OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, p.outline.copy(alpha = .4f)), contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(width = 84.dp, height = 56.dp).focusProperties { canFocus = true }.testTag(tag)) {
        Text(label, color = if (enabled) p.text else p.muted, fontSize = if (small) 15.sp else 22.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Settings card shared by phone and TV. Changing anything needs the PIN first (or no PIN yet). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParentalSettings(controls: ParentalControls, television: Boolean, sourceId: String?,
    loadCategories: suspend (ContentKind) -> List<String>, onForgot: () -> Unit) {
    val p = cinemaPalette()
    val state by controls.state.collectAsState()
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<PinMode?>(null) }
    var confirmingOff by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<Pair<ContentKind, List<String>>?>(null) }
    val locks = remember(state.revision, sourceId) { controls.locks(sourceId) }
    val restricted = state.pinSet && !state.unlocked
    Column(Modifier.fillMaxWidth().testTag("parental_settings"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(DR.string.parental_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(DR.string.parental_description), color = p.muted, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(when { !state.pinSet -> DR.string.parental_status_off; restricted -> DR.string.parental_status_locked; else -> DR.string.parental_status_unlocked }),
            fontWeight = FontWeight.Medium, modifier = Modifier.testTag("parental_status"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val action = Modifier.heightIn(min = 48.dp).focusProperties { canFocus = true }
            when {
                !state.pinSet -> Button(onClick = { dialog = PinMode.CREATE }, modifier = action.testTag("parental_set_pin")) { Text(stringResource(DR.string.parental_set_pin)) }
                restricted -> Button(onClick = { dialog = PinMode.ENTER }, modifier = action.testTag("parental_unlock")) { Text(stringResource(DR.string.parental_unlock)) }
                else -> {
                    OutlinedButton(onClick = { dialog = PinMode.CREATE }, modifier = action.testTag("parental_change_pin")) { Text(stringResource(DR.string.parental_change_pin), color = p.text) }
                    OutlinedButton(onClick = controls::relock, modifier = action.testTag("parental_lock_now")) { Text(stringResource(DR.string.parental_lock_now), color = p.text) }
                    OutlinedButton(onClick = { confirmingOff = true }, modifier = action.testTag("parental_turn_off")) { Text(stringResource(DR.string.parental_turn_off), color = p.error) }
                }
            }
        }
        if (state.pinSet && !restricted) {
            Text(stringResource(DR.string.parental_locked_categories), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (sourceId == null) Text(stringResource(DR.string.parental_no_source), color = p.muted)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(ContentKind.LIVE to R.string.nav_live, ContentKind.MOVIE to R.string.nav_movies, ContentKind.SERIES to R.string.nav_series).forEach { (kind, label) ->
                    OutlinedButton(onClick = { scope.launch { choosing = kind to loadCategories(kind) } },
                        modifier = Modifier.heightIn(min = 48.dp).focusProperties { canFocus = true }.testTag("parental_lock_${kind.name.lowercase()}")) {
                        Text("${stringResource(label)} · ${String.format(stringResource(DR.string.parental_locked_count), locks.labels(kind).size)}", color = p.text)
                    }
                }
            }
        }
    }
    dialog?.let { mode ->
        PinDialog(mode, controls, television, onDone = { dialog = null }, onDismiss = { dialog = null },
            onForgot = if (mode == PinMode.ENTER) ({ dialog = null; onForgot() }) else null)
    }
    if (confirmingOff) AlertDialog(onDismissRequest = { confirmingOff = false },
        title = { Text(stringResource(DR.string.parental_turn_off)) }, text = { Text(stringResource(DR.string.parental_turn_off_message)) },
        confirmButton = { TextButton(onClick = { confirmingOff = false; if (!controls.restricted) controls.disable() }, modifier = Modifier.testTag("parental_turn_off_confirm")) {
            Text(stringResource(DR.string.parental_turn_off), color = p.error) } },
        dismissButton = { TextButton(onClick = { confirmingOff = false }) { Text(stringResource(DR.string.pin_cancel)) } })
    choosing?.let { (kind, categories) ->
        val id = sourceId
        if (id == null || restricted) { choosing = null; return@let }
        if (categories.isEmpty()) AlertDialog(onDismissRequest = { choosing = null }, text = { Text(stringResource(DR.string.parental_no_categories)) },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text(stringResource(DR.string.parental_done)) } })
        else CategoryPicker(categories, null, television, title = stringResource(DR.string.parental_choose_categories),
            checked = locks.labels(kind), onDismiss = { choosing = null },
            onSelect = { label -> if (label != null && !controls.restricted) controls.setLock(id, kind, label, label !in controls.locks(id).labels(kind)) })
    }
}

