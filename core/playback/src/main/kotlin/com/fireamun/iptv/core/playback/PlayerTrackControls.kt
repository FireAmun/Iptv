package com.fireamun.iptv.core.playback

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTrackNameProvider
import com.fireamun.iptv.core.designsystem.cinemaFont
import com.fireamun.iptv.core.designsystem.cinemaPalette

@OptIn(UnstableApi::class)
private data class TrackChoice(val group: TrackGroup, val index: Int, val format: Format)
@OptIn(UnstableApi::class)
private data class TrackSnapshot(val tracks: Tracks, val parameters: TrackSelectionParameters, val canSelect: Boolean)

@OptIn(UnstableApi::class)
private fun Player.trackSnapshot() = TrackSnapshot(
    if (isCommandAvailable(Player.COMMAND_GET_TRACKS)) currentTracks else Tracks.EMPTY,
    trackSelectionParameters,
    isCommandAvailable(Player.COMMAND_GET_TRACKS) &&
        isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS) && playerError == null,
)

@OptIn(UnstableApi::class)
private fun supportedChoices(tracks: Tracks, type: Int): List<TrackChoice> =
    tracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
            TrackChoice(group.mediaTrackGroup, index, group.getTrackFormat(index))
        }
    }

/** Changes only the active player's selected track type; no media replacement or seek. */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerTrackControls(
    player: Player?,
    television: Boolean,
    modifier: Modifier = Modifier,
) {
    val latestPlayer by rememberUpdatedState(player)
    key(player) {
        PlayerTrackControlsContent(player, { latestPlayer === player }, television, modifier)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerTrackControlsContent(
    player: Player?,
    isCurrent: () -> Boolean,
    television: Boolean,
    modifier: Modifier,
) {
    var snapshot by remember(player) { mutableStateOf(player?.trackSnapshot()) }
    var attached by remember { mutableStateOf(false) }
    var requestedType by remember { mutableStateOf<Int?>(null) }
    var restoreType by remember { mutableStateOf<Int?>(null) }
    val parentWindow = LocalWindowInfo.current
    val audioFocus = remember { FocusRequester() }
    val textFocus = remember { FocusRequester() }
    fun origin(type: Int) = if (type == C.TRACK_TYPE_AUDIO) audioFocus else textFocus
    fun dismiss() {
        restoreType = requestedType
        requestedType = null
    }
    DisposableEffect(player) {
        attached = true
        fun refresh() {
            if (attached && isCurrent()) snapshot = player?.trackSnapshot()
        }
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                if (!attached || !isCurrent()) return
                val oldGroups = snapshot?.tracks?.groups?.map { it.mediaTrackGroup }
                if (oldGroups != tracks.groups.map { it.mediaTrackGroup }) dismiss()
                refresh()
            }
            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) = refresh()
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = refresh()
            override fun onPlayerError(error: PlaybackException) { dismiss(); refresh() }
        }
        player?.addListener(listener)
        refresh()
        onDispose { attached = false; player?.removeListener(listener) }
    }
    LaunchedEffect(restoreType, parentWindow.isWindowFocused) {
        val type = restoreType
        if (television && type != null && parentWindow.isWindowFocused && isCurrent()) {
            withFrameNanos { }
            origin(type).requestFocus()
            restoreType = null
        }
    }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrackButton(stringResource(R.string.player_audio), Modifier.focusRequester(audioFocus).testTag("player_audio"), snapshot?.canSelect == true) {
            requestedType = C.TRACK_TYPE_AUDIO
        }
        TrackButton(stringResource(R.string.player_subtitles), Modifier.focusRequester(textFocus).testTag("player_subtitles"), snapshot?.canSelect == true) {
            requestedType = C.TRACK_TYPE_TEXT
        }
    }
    val type = requestedType
    val current = snapshot
    if (type != null && player != null && current != null) {
        TrackDialog(type, current, television, onDismiss = ::dismiss) { choice, disabled ->
            // A dialog action can outlive a source update or the concrete lifecycle player.
            if (attached && isCurrent() && player.isCommandAvailable(Player.COMMAND_GET_TRACKS) &&
                player.currentTracks.groups.map { it.mediaTrackGroup } == current.tracks.groups.map { it.mediaTrackGroup } &&
                player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS) && player.playerError == null) {
                val validChoice = choice == null || supportedChoices(player.currentTracks, type).any {
                    it.group == choice.group && it.index == choice.index
                }
                if (validChoice) {
                    val builder = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(type).setTrackTypeDisabled(type, disabled)
                    if (choice != null) builder.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
                    player.trackSelectionParameters = builder.build()
                }
            }
            dismiss()
        }
    }
}

@Composable
private fun TrackButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val p = cinemaPalette()
    var focused by remember { mutableStateOf(false) }
    TextButton(onClick, modifier.focusProperties { canFocus = enabled }.onFocusChanged { focused = it.isFocused }, enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) p.focus else p.outline),
        colors = ButtonDefaults.textButtonColors(contentColor = p.text, containerColor = p.panel,
            disabledContentColor = p.muted, disabledContainerColor = p.panel)) {
        Text(label, fontFamily = cinemaFont(), maxLines = 1)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TrackDialog(
    type: Int,
    snapshot: TrackSnapshot,
    television: Boolean,
    onDismiss: () -> Unit,
    onSelect: (TrackChoice?, Boolean) -> Unit,
) {
    val p = cinemaPalette()
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val names = remember(resources, configuration) { DefaultTrackNameProvider(resources) }
    val options = supportedChoices(snapshot.tracks, type)
    val labels = options.mapIndexed { index, choice ->
        val name = names.getTrackName(choice.format).safeTrackLabel()
        if (name.isBlank()) stringResource(R.string.player_track_number, index + 1) else name
    }
    val disabled = type in snapshot.parameters.disabledTrackTypes
    val overrides = snapshot.parameters.overrides.values.filter { it.type == type }
    val auto = !disabled && overrides.isEmpty()
    val selectedIndex = options.indexOfFirst { choice ->
        !disabled && overrides.any { it.mediaTrackGroup == choice.group && choice.index in it.trackIndices }
    }
    val initialFocus = remember { FocusRequester() }
    val title = stringResource(when (type) {
        C.TRACK_TYPE_AUDIO -> R.string.player_audio
        else -> R.string.player_subtitles
    })
    Dialog(onDismissRequest = onDismiss) {
        val window = LocalWindowInfo.current
        var initialFocusAssigned by remember { mutableStateOf(false) }
        Surface(Modifier.widthIn(max = 560.dp).heightIn(max = 480.dp).testTag("player_track_dialog"),
            shape = RoundedCornerShape(20.dp), color = p.panel, contentColor = p.text,
            border = BorderStroke(1.dp, p.outline)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = cinemaFont(), fontWeight = FontWeight.Bold)
                if (options.isEmpty()) {
                    Text(stringResource(R.string.player_tracks_unavailable), color = p.muted, fontFamily = cinemaFont())
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrackRow(stringResource(R.string.player_track_auto), auto,
                        Modifier.testTag("track_auto").then(if (auto || (selectedIndex < 0 && (!disabled || type != C.TRACK_TYPE_TEXT))) Modifier.focusRequester(initialFocus) else Modifier)) {
                        onSelect(null, false)
                    }
                    if (type == C.TRACK_TYPE_TEXT) {
                        TrackRow(stringResource(R.string.player_track_off), disabled,
                            Modifier.testTag("track_off").then(if (disabled) Modifier.focusRequester(initialFocus) else Modifier)) {
                            onSelect(null, true)
                        }
                    }
                    options.forEachIndexed { index, choice ->
                        val name = labels[index]
                        val label = if (labels.count { it == name } > 1)
                            stringResource(R.string.player_track_distinct, name, index + 1) else name
                        TrackRow(label, index == selectedIndex,
                            Modifier.testTag("track_option_$index").then(if (index == selectedIndex) Modifier.focusRequester(initialFocus) else Modifier)) {
                            onSelect(choice, false)
                        }
                    }
                }
                TrackButton(stringResource(R.string.player_track_close), Modifier.align(Alignment.End), true, onDismiss)
            }
        }
        LaunchedEffect(window.isWindowFocused) {
            if (television && window.isWindowFocused && !initialFocusAssigned) {
                withFrameNanos { }
                initialFocus.requestFocus()
                initialFocusAssigned = true
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = cinemaPalette()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.fillMaxWidth().focusProperties { canFocus = true }.onFocusChanged { focused = it.isFocused }
        .background(if (focused) p.raised else p.panel, shape)
        .border(if (focused) 2.dp else 1.dp, if (focused) p.focus else p.outline.copy(alpha = .35f), shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = p.focus, unselectedColor = p.muted))
        Text(label, Modifier.weight(1f), fontFamily = cinemaFont(), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun String.safeTrackLabel() = filterNot { it.isISOControl() }.take(120).trim()
