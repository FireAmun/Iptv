package com.fireamun.iptv.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fireamun.iptv.core.designsystem.CinemaIcon
import com.fireamun.iptv.core.designsystem.CinemaSymbol
import com.fireamun.iptv.core.designsystem.cinemaPalette
import com.fireamun.iptv.core.remote.RemoteKey
import androidx.activity.compose.rememberLauncherForActivityResult
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.fireamun.iptv.core.designsystem.R as DR

/** TV settings card: turn the phone remote on or off, show a pairing code and manage paired phones. */
@Composable
internal fun TvRemoteSettings(host: RemoteHost) {
    val p = cinemaPalette()
    val ui by host.state.collectAsState()
    var pairing by rememberSaveable { mutableStateOf(false) }
    val action = Modifier.heightIn(min = 48.dp).focusProperties { canFocus = true }
    Column(Modifier.fillMaxWidth().testTag("remote_settings"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(DR.string.remote_tv_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(DR.string.remote_tv_description), color = p.muted, style = MaterialTheme.typography.bodyMedium)
        if (ui.unavailable) Text(stringResource(DR.string.remote_tv_unavailable), color = p.error)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(DR.string.remote_tv_enabled), Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Switch(ui.enabled, host::setEnabled, Modifier.focusProperties { canFocus = true }.testTag("remote_enabled"))
        }
        Button(onClick = { pairing = true; host.startPairing() }, enabled = ui.enabled, modifier = action.testTag("remote_pair")) {
            Text(stringResource(DR.string.remote_tv_pair))
        }
        Text(stringResource(DR.string.remote_tv_phones), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        val phones = ui.server.phones
        if (phones.isEmpty()) Text(stringResource(DR.string.remote_tv_no_phones), color = p.muted, modifier = Modifier.testTag("remote_no_phones"))
        phones.forEachIndexed { index, phone ->
            Row(Modifier.fillMaxWidth().testTag("remote_phone_$index"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(phone.name, fontWeight = FontWeight.Medium)
                    if (phone.id in ui.server.connected) Text(stringResource(DR.string.remote_tv_connected), color = p.focus, style = MaterialTheme.typography.bodySmall)
                }
                val label = stringResource(DR.string.remote_tv_remove_named, phone.name)
                OutlinedButton(onClick = { host.revoke(phone.id) }, modifier = action.testTag("remote_revoke_$index").semantics { contentDescription = label }) {
                    Text(stringResource(DR.string.remote_tv_remove), color = p.text)
                }
            }
        }
        if (phones.size > 1) OutlinedButton(onClick = host::revokeAll, modifier = action.testTag("remote_revoke_all")) {
            Text(stringResource(DR.string.remote_tv_remove_all), color = p.error)
        }
    }
    if (pairing) PairingDialog(host, ui) { pairing = false; host.stopPairing() }
}

@Composable
private fun PairingDialog(host: RemoteHost, ui: RemoteHostUi, onClose: () -> Unit) {
    val p = cinemaPalette()
    val server = ui.server
    val offer = server.offer
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(offer) { while (offer != null) { now = System.currentTimeMillis(); delay(1_000) } }
    val primary = remember { FocusRequester() }
    val mode = when {
        server.pending != null -> "ask"
        server.approved != null -> "done"
        ui.noNetwork || ui.unavailable -> "unavailable"
        offer != null && now < offer.expiresAtMs -> "code"
        else -> "expired"
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogWindow = LocalWindowInfo.current
        Box(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("remote_pairing"), shape = RoundedCornerShape(22.dp), color = p.panel, contentColor = p.text) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(DR.string.remote_pair_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    val button = Modifier.heightIn(min = 48.dp).focusProperties { canFocus = true }
                    when (mode) {
                        "ask" -> {
                            val pending = server.pending!!
                            Text(stringResource(DR.string.remote_pair_ask, pending.name), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("remote_pair_ask").semantics { liveRegion = LiveRegionMode.Assertive })
                            // Code pairing: the person at the TV is the check that no other device sits in between.
                            pending.code?.let { code ->
                                Text(stringResource(DR.string.remote_pair_compare), textAlign = TextAlign.Center)
                                PairingCode(code, Modifier.testTag("remote_pair_code"))
                            }
                            Text(stringResource(DR.string.remote_pair_ask_detail), color = p.muted, textAlign = TextAlign.Center)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = host::deny, modifier = button.focusRequester(primary).testTag("remote_deny")) { Text(stringResource(DR.string.remote_pair_deny), color = p.text) }
                                Button(onClick = host::approve, modifier = button.testTag("remote_allow")) { Text(stringResource(DR.string.remote_pair_allow)) }
                            }
                        }
                        "done" -> {
                            Text(stringResource(DR.string.remote_pair_done, server.approved.orEmpty()), textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("remote_pair_done").semantics { liveRegion = LiveRegionMode.Polite })
                            Button(onClick = onClose, modifier = button.focusRequester(primary).testTag("remote_pair_close")) { Text(stringResource(DR.string.remote_pair_close)) }
                        }
                        "unavailable" -> {
                            Text(stringResource(if (ui.unavailable) DR.string.remote_tv_unavailable else DR.string.remote_tv_no_network), textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("remote_pair_unavailable"))
                            OutlinedButton(onClick = onClose, modifier = button.focusRequester(primary).testTag("remote_pair_cancel")) { Text(stringResource(DR.string.remote_pair_cancel), color = p.text) }
                        }
                        "code" -> {
                            Text(stringResource(DR.string.remote_pair_steps), textAlign = TextAlign.Center, color = p.muted)
                            val qrLabel = stringResource(DR.string.remote_pair_qr)
                            QrImage(offer!!.link.encode(), 260.dp, Modifier.testTag("remote_qr").semantics { contentDescription = qrLabel })
                            val left = ((offer.expiresAtMs - now).coerceAtLeast(0) + 999) / 1_000
                            Text(stringResource(DR.string.remote_pair_expires, "%d:%02d".format(left / 60, left % 60)), color = p.muted,
                                modifier = Modifier.testTag("remote_pair_expiry"))
                            Text(stringResource(DR.string.remote_pair_no_camera), textAlign = TextAlign.Center, color = p.muted)
                            ui.address?.let { address ->
                                Text(address, fontSize = 24.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                                    modifier = Modifier.testTag("remote_pair_address"))
                            }
                            OutlinedButton(onClick = onClose, modifier = button.focusRequester(primary).testTag("remote_pair_cancel")) { Text(stringResource(DR.string.remote_pair_cancel), color = p.text) }
                        }
                        else -> {
                            Text(stringResource(DR.string.remote_pair_expired), modifier = Modifier.testTag("remote_pair_expired"))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onClose, modifier = button.testTag("remote_pair_cancel")) { Text(stringResource(DR.string.remote_pair_cancel), color = p.text) }
                                Button(onClick = { host.startPairing() }, modifier = button.focusRequester(primary).testTag("remote_pair_new")) { Text(stringResource(DR.string.remote_pair_new_code)) }
                            }
                        }
                    }
                }
            }
        }
        // Remote users need a focused control in each step; Deny comes first so a stray OK never approves.
        LaunchedEffect(mode, dialogWindow.isWindowFocused) {
            if (dialogWindow.isWindowFocused) { withFrameNanos { }; runCatching { primary.requestFocus() } }
        }
    }
}

/** Six digits in two groups, kept left-to-right so both screens read the same in Arabic too. */
@Composable
private fun PairingCode(code: String, modifier: Modifier = Modifier) {
    Text("${code.take(3)} ${code.drop(3)}", modifier.semantics { contentDescription = code.toList().joinToString(" ") },
        fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp,
        style = MaterialTheme.typography.displaySmall.copy(textDirection = TextDirection.Ltr), color = cinemaPalette().focus)
}

/** Black modules on white with a four-module quiet zone, in both themes, so every scanner reads it. */
@Composable
private fun QrImage(text: String, size: Dp, modifier: Modifier = Modifier) {
    // Width 0 asks for the bare matrix (one pixel per module, no margin); the quiet zone is drawn here.
    val code = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
    }
    Canvas(modifier.size(size).background(Color.White)) {
        val modules = code.width + 8
        val cell = kotlin.math.floor(this.size.minDimension / modules)
        val origin = (this.size.minDimension - cell * code.width) / 2f
        for (y in 0 until code.height) for (x in 0 until code.width) if (code.get(x, y))
            drawRect(Color.Black, Offset(origin + x * cell, origin + y * cell), Size(cell, cell))
    }
}

/** Phone settings card: scan a TV's code, then open the remote or forget the TV. */
@Composable
internal fun PhoneRemoteSettings(controller: RemoteController) {
    val p = cinemaPalette()
    val ui by controller.state.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    var withCode by rememberSaveable { mutableStateOf(false) }
    val tv = ui.tvName
    val scanPrompt = stringResource(DR.string.remote_phone_scan_prompt)
    // The scanner screen asks for the camera itself, only when the user starts a scan; a cancelled scan does nothing.
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(controller::pair) }
    Column(Modifier.fillMaxWidth().testTag("remote_client"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(DR.string.remote_phone_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(DR.string.remote_phone_description), color = p.muted, style = MaterialTheme.typography.bodyMedium)
        if (tv == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                        try {
                            scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(scanPrompt)
                                .setBeepEnabled(false).setOrientationLocked(false))
                        } catch (_: android.content.ActivityNotFoundException) { controller.scannerUnavailable() }
                    }, enabled = ui.pairing == PairingPhase.IDLE, modifier = Modifier.heightIn(min = 48.dp).testTag("remote_scan")) {
                    Text(stringResource(DR.string.remote_phone_scan))
                }
                OutlinedButton(onClick = { controller.clearProblem(); withCode = true }, enabled = ui.pairing == PairingPhase.IDLE,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("remote_pair_with_code")) {
                    Text(stringResource(DR.string.remote_phone_pair_code), color = p.text)
                }
            }
            if (ui.pairing != PairingPhase.IDLE) Text(stringResource(if (ui.pairing == PairingPhase.WAITING_APPROVAL) DR.string.remote_phone_waiting else DR.string.remote_phone_connecting),
                fontWeight = FontWeight.Medium, modifier = Modifier.testTag("remote_status").semantics { liveRegion = LiveRegionMode.Polite })
        } else {
            Text(stringResource(DR.string.remote_phone_paired, tv), fontWeight = FontWeight.Medium, modifier = Modifier.testTag("remote_paired"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("remote_open")) { Text(stringResource(DR.string.remote_phone_open)) }
                OutlinedButton(onClick = { open = false; controller.forget() }, modifier = Modifier.heightIn(min = 48.dp).testTag("remote_forget")) {
                    Text(stringResource(DR.string.remote_phone_forget), color = p.text)
                }
            }
        }
        if (!open && !withCode) ui.problem?.let { RemoteProblemText(it) }
    }
    if (open && tv != null) RemotePanel(controller) { open = false }
    if (withCode && tv == null) CodePairingDialog(controller) { withCode = false }
}

/** Pairing without the camera: pick a TV found on the Wi-Fi or type its address, then compare codes. */
@Composable
private fun CodePairingDialog(controller: RemoteController, onClose: () -> Unit) {
    val p = cinemaPalette()
    val ui by controller.state.collectAsState()
    // Searching runs only while this dialog is open.
    val found by remember { controller.searchTvs() }.collectAsState(initial = emptyList())
    var address by rememberSaveable { mutableStateOf("") }
    fun close() { controller.cancelPairing(); onClose() }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("remote_code_pairing"), shape = RoundedCornerShape(22.dp), color = p.panel, contentColor = p.text) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(DR.string.remote_phone_pair_code), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    val code = ui.code
                    when {
                        code != null -> {
                            Text(stringResource(DR.string.remote_phone_compare), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            PairingCode(code, Modifier.align(Alignment.CenterHorizontally).testTag("remote_phone_code"))
                        }
                        ui.pairing != PairingPhase.IDLE -> Text(stringResource(DR.string.remote_phone_connecting), modifier = Modifier.testTag("remote_status"))
                        else -> {
                            Text(stringResource(DR.string.remote_phone_code_steps), color = p.muted)
                            if (found.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = p.focus)
                                Text(stringResource(DR.string.remote_phone_searching), color = p.muted, modifier = Modifier.testTag("remote_searching"))
                            }
                            found.forEachIndexed { index, tv ->
                                OutlinedButton(onClick = { controller.pairWith(tv) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("remote_found_$index")) {
                                    Text(tv.name, color = p.text, fontWeight = FontWeight.Medium)
                                }
                            }
                            Text(stringResource(DR.string.remote_phone_address_hint), color = p.muted, modifier = Modifier.padding(top = 6.dp))
                            OutlinedTextField(address, { address = it.take(21) }, singleLine = true, label = { Text(stringResource(DR.string.remote_phone_address_label)) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                                keyboardActions = KeyboardActions(onGo = { controller.pairWithAddress(address) }),
                                modifier = Modifier.fillMaxWidth().testTag("remote_address"))
                            Button(onClick = { controller.pairWithAddress(address) }, enabled = address.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag("remote_address_connect")) {
                                Text(stringResource(DR.string.remote_phone_address_connect))
                            }
                            ui.problem?.let { RemoteProblemText(it) }
                        }
                    }
                    OutlinedButton(onClick = ::close, modifier = Modifier.heightIn(min = 48.dp).testTag("remote_code_close")) {
                        Text(stringResource(if (ui.pairing == PairingPhase.IDLE) DR.string.remote_phone_close else DR.string.remote_pair_cancel), color = p.text)
                    }
                }
            }
        }
    }
    LaunchedEffect(ui.tvName) { if (ui.tvName != null) onClose() }
}

@Composable
private fun RemoteProblemText(problem: RemoteProblem) {
    Text(stringResource(when (problem) {
        RemoteProblem.NOT_A_CODE -> DR.string.remote_problem_not_a_code
        RemoteProblem.NOT_AN_ADDRESS -> DR.string.remote_problem_not_an_address
        RemoteProblem.NO_FIELD -> DR.string.remote_problem_no_field
        RemoteProblem.UNREACHABLE -> DR.string.remote_problem_unreachable
        RemoteProblem.SCANNER_UNAVAILABLE -> DR.string.remote_problem_scanner
        RemoteProblem.EXPIRED -> DR.string.remote_problem_expired
        RemoteProblem.DENIED -> DR.string.remote_problem_denied
        RemoteProblem.INVALID -> DR.string.remote_problem_invalid
        RemoteProblem.IDENTITY -> DR.string.remote_problem_identity
        RemoteProblem.BUSY -> DR.string.remote_problem_busy
        RemoteProblem.VERSION -> DR.string.remote_problem_version
        RemoteProblem.REVOKED -> DR.string.remote_problem_revoked
        RemoteProblem.FAILED -> DR.string.remote_problem_failed
        RemoteProblem.NO_PLAYBACK -> DR.string.remote_problem_no_playback
        RemoteProblem.NOT_FOCUSED -> DR.string.remote_problem_not_focused
    }), color = cinemaPalette().error, modifier = Modifier.testTag("remote_problem").semantics { liveRegion = LiveRegionMode.Polite })
}

/** Full-screen remote: connection status, a D-pad that stays left-to-right in Arabic, and playback buttons. */
@Composable
private fun RemotePanel(controller: RemoteController, onClose: () -> Unit) {
    val p = cinemaPalette()
    val ui by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    DisposableEffect(Unit) { controller.connect(); onDispose { controller.finishTyping(); controller.disconnect() } }
    var keyboard by rememberSaveable { mutableStateOf(false) }
    val connected = ui.connection == RemoteConnection.CONNECTED
    fun press(key: RemoteKey) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { controller.press(key) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("remote_panel"), color = p.background, contentColor = p.text) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.tvName.orEmpty(), Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    val closeLabel = stringResource(DR.string.remote_panel_close)
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("remote_close").semantics { contentDescription = closeLabel }) {
                        CinemaIcon(CinemaSymbol.CLOSE, tint = p.muted)
                    }
                }
                Text(when (ui.connection) {
                    RemoteConnection.CONNECTED -> stringResource(DR.string.remote_panel_connected, ui.tvName.orEmpty())
                    RemoteConnection.CONNECTING -> stringResource(DR.string.remote_panel_connecting, ui.tvName.orEmpty())
                    RemoteConnection.OFFLINE -> stringResource(DR.string.remote_panel_offline)
                }, color = if (connected) p.focus else p.muted, textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("remote_connection").semantics { liveRegion = LiveRegionMode.Polite })
                ui.problem?.let { RemoteProblemText(it) }
                if (ui.connection == RemoteConnection.OFFLINE && ui.tvName != null) OutlinedButton(onClick = controller::connect,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("remote_retry")) { Text(stringResource(DR.string.remote_panel_retry), color = p.text) }
                // Near the top, so the box, its problem message and Close stay above the phone keyboard.
                if (keyboard && connected) TypingBox(controller)
                // Physical directions: the TV's own D-pad keys do not flip in right-to-left languages.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.size(272.dp).background(p.panel, CircleShape)) {
                        RemoteButton(RemoteKey.UP, DR.string.remote_key_up, connected, ::press, Modifier.align(Alignment.TopCenter).padding(top = 8.dp), onPanel = true) { CinemaIcon(CinemaSymbol.CHEVRON_UP, tint = it) }
                        RemoteButton(RemoteKey.LEFT, DR.string.remote_key_left, connected, ::press, Modifier.align(Alignment.CenterStart).padding(start = 8.dp), onPanel = true) { CinemaIcon(CinemaSymbol.CHEVRON_UP, Modifier.rotate(-90f), it) }
                        RemoteButton(RemoteKey.RIGHT, DR.string.remote_key_right, connected, ::press, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), onPanel = true) { CinemaIcon(CinemaSymbol.CHEVRON_UP, Modifier.rotate(90f), it) }
                        RemoteButton(RemoteKey.DOWN, DR.string.remote_key_down, connected, ::press, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), onPanel = true) { CinemaIcon(CinemaSymbol.CHEVRON_UP, Modifier.rotate(180f), it) }
                        RemoteButton(RemoteKey.OK, DR.string.remote_key_ok, connected, ::press, Modifier.align(Alignment.Center), size = 96.dp, filled = true) {
                            Text(stringResource(DR.string.remote_key_ok), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = it)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RemoteButton(RemoteKey.BACK, DR.string.remote_key_back, connected, ::press) { CinemaIcon(CinemaSymbol.BACK, tint = it) }
                    RemoteButton(RemoteKey.PLAY_PAUSE, DR.string.remote_key_play_pause, connected, ::press) { CinemaIcon(CinemaSymbol.PLAY_PAUSE, tint = it) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RemoteButton(RemoteKey.CHANNEL_UP, DR.string.remote_key_channel_up, connected, ::press) { CinemaIcon(CinemaSymbol.CHEVRON_UP, tint = it) }
                        Text(stringResource(DR.string.remote_key_channel), color = p.muted, style = MaterialTheme.typography.labelMedium)
                        RemoteButton(RemoteKey.CHANNEL_DOWN, DR.string.remote_key_channel_down, connected, ::press) { CinemaIcon(CinemaSymbol.CHEVRON_UP, Modifier.rotate(180f), it) }
                    }
                }
                OutlinedButton(onClick = { keyboard = !keyboard; if (!keyboard) controller.finishTyping() }, enabled = connected,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("remote_keyboard")) {
                    Text(stringResource(DR.string.remote_keyboard), color = if (connected) p.text else p.muted)
                }
            }
        }
    }
}

/**
 * Text typed here is added to the TV's selected text box as it changes; deleting here deletes there. Done starts a
 * fresh line and leaves the TV text as it is.
 */
@Composable
private fun TypingBox(controller: RemoteController) {
    val p = cinemaPalette()
    val text by controller.typing.collectAsState()
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(DR.string.remote_keyboard_hint), color = p.muted, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(text, controller::type, singleLine = true, label = { Text(stringResource(DR.string.remote_keyboard_label)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { controller.finishTyping() }),
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("remote_typing"))
            TextButton(onClick = controller::finishTyping, modifier = Modifier.heightIn(min = 48.dp).testTag("remote_typing_done")) {
                Text(stringResource(DR.string.remote_keyboard_done))
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun RemoteButton(key: RemoteKey, label: Int, enabled: Boolean, onPress: (RemoteKey) -> Unit, modifier: Modifier = Modifier,
    size: Dp = 64.dp, filled: Boolean = false, onPanel: Boolean = false, content: @Composable (Color) -> Unit) {
    val p = cinemaPalette()
    val description = stringResource(label)
    val tint = when { !enabled -> p.muted.copy(alpha = .5f); filled -> p.background; else -> p.text }
    Surface(onClick = { onPress(key) }, enabled = enabled, shape = CircleShape,
        // D-pad keys sit on the panel disc; the playback row sits on the page, so each takes the other surface.
        color = when { filled && enabled -> p.focus; onPanel -> p.background; else -> p.panel },
        modifier = modifier.size(size).testTag("remote_key_${key.name}").semantics { contentDescription = description; role = Role.Button }) {
        Box(contentAlignment = Alignment.Center) { content(tint) }
    }
}
