package com.fireamun.iptv.prototype

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fireamun.iptv.core.data.GuideProgramme
import com.fireamun.iptv.core.designsystem.CinemaIcon
import com.fireamun.iptv.core.designsystem.CinemaSymbol
import com.fireamun.iptv.core.designsystem.cinemaPalette
import com.fireamun.iptv.core.model.Channel
import com.fireamun.iptv.core.model.GuideNow
import com.fireamun.iptv.core.model.GuideSlot
import kotlinx.coroutines.delay
import com.fireamun.iptv.core.designsystem.R as DR

/** Loads a channel's schedule; absent when the app has no stored guide (for example in previews). */
internal val LocalGuideSchedule = staticCompositionLocalOf<(suspend (Int) -> List<GuideProgramme>)?> { null }
/** Wall clock for guide rows, ticking every 30 seconds; one shared state so rows do not each run a timer. */
internal val LocalGuideClock = staticCompositionLocalOf<State<Long>> { mutableLongStateOf(System.currentTimeMillis()) }

@Composable
internal fun rememberGuideClock(): State<Long> {
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000 - System.currentTimeMillis() % 30_000)
            clock.longValue = System.currentTimeMillis()
        }
    }
    return clock
}

@Composable
internal fun rememberTimeText(): (Long) -> String {
    val context = LocalContext.current
    return remember(context) { { millis -> DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME) } }
}

/** "Now" with its progress and "Next" for a live row; nothing when the guide has no current or upcoming slot. */
@Composable
internal fun GuideLines(guide: GuideNow, channelId: Int) {
    val now = LocalGuideClock.current.value
    val (current, next) = guide.at(now)
    if (current == null && next == null) return
    val p = cinemaPalette()
    val time = rememberTimeText()
    Column(Modifier.testTag("guide_lines_$channelId"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        current?.let { slot ->
            Text(stringResource(DR.string.guide_now, slot.title), color = p.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            GuideProgress(slot, now, Modifier.fillMaxWidth(.7f))
        }
        next?.let { slot ->
            Text(stringResource(DR.string.guide_next, time(slot.startMs), slot.title), color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun GuideProgress(slot: GuideSlot, now: Long, modifier: Modifier = Modifier) {
    val p = cinemaPalette()
    val progress = slot.progress(now)
    Box(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.outline.copy(alpha = .25f))
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(p.focus))
    }
}

/** Player subtitle such as "14:00–15:00 · News"; null when nothing is on. */
@Composable
internal fun playerProgrammeText(slot: GuideSlot?): String? {
    slot ?: return null
    val time = rememberTimeText()
    return stringResource(DR.string.guide_slot, time(slot.startMs), time(slot.endMs), slot.title)
}

@Composable
internal fun GuideButton(channel: Channel, onClick: () -> Unit, onFocused: () -> Unit = {}) {
    val p = cinemaPalette()
    val label = stringResource(DR.string.guide_schedule_for, channel.title.orEmpty())
    val tv = LocalBrowserTv.current
    TextButton(onClick = onClick, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .then(if (tv) Modifier.focusProperties { canFocus = true } else Modifier)
        .onFocusChanged { if (it.isFocused) onFocused() }.testTag("guide_${channel.id}").semantics { contentDescription = label },
        contentPadding = PaddingValues(8.dp)) {
        CinemaIcon(CinemaSymbol.GUIDE, tint = p.muted)
    }
}

/** 48-hour schedule of one channel, grouped by day, with the programme on now highlighted. */
@Composable
internal fun ScheduleDialog(channel: Channel, television: Boolean, onPlay: () -> Unit, onDismiss: () -> Unit) {
    val p = cinemaPalette()
    val context = LocalContext.current
    val load = LocalGuideSchedule.current
    val now = LocalGuideClock.current.value
    val time = rememberTimeText()
    var programmes by remember(channel.id) { mutableStateOf<List<GuideProgramme>?>(null) }
    LaunchedEffect(channel.id) { programmes = load?.invoke(channel.id).orEmpty() }
    val list = rememberLazyListState()
    val playFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogWindow = LocalWindowInfo.current
        BoxWithConstraints(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, 720.dp)).testTag("guide_schedule"),
                shape = RoundedCornerShape(22.dp), color = p.panel, contentColor = p.text) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(DR.string.guide_schedule_for, channel.title.orEmpty()), Modifier.weight(1f), fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        val closeLabel = stringResource(R.string.browse_close)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).focusProperties { canFocus = true }.testTag("guide_schedule_close").semantics { contentDescription = closeLabel }) {
                            CinemaIcon(CinemaSymbol.CLOSE, tint = p.muted)
                        }
                    }
                    val items = programmes
                    when {
                        items == null -> Text(stringResource(DR.string.guide_loading), color = p.muted)
                        items.isEmpty() -> Text(stringResource(DR.string.guide_schedule_empty), color = p.muted, modifier = Modifier.testTag("guide_schedule_empty"))
                        else -> {
                            LaunchedEffect(items) {
                                val index = items.indexOfFirst { it.endMs > now }.coerceAtLeast(0)
                                list.scrollToItem(index + items.take(index + 1).map { day(context, it.startMs) }.distinct().size)
                            }
                            LazyColumn(Modifier.weight(1f, fill = false).testTag("guide_schedule_list"), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                var previousDay: String? = null
                                itemsIndexed(items.flatMap { programme ->
                                    val label = day(context, programme.startMs)
                                    (if (label != previousDay) listOf<Any>(label) else emptyList<Any>()).also { previousDay = label } + programme
                                }) { _, item ->
                                    if (item is String) Text(item, color = p.focus, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                                    else ProgrammeRow(item as GuideProgramme, now, time, television)
                                }
                            }
                        }
                    }
                    Button(onClick = onPlay, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .focusProperties { canFocus = true }.focusRequester(playFocus).testTag("guide_schedule_play")) {
                        Text(stringResource(DR.string.guide_watch), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        LaunchedEffect(dialogWindow.isWindowFocused) { if (television && dialogWindow.isWindowFocused) { withFrameNanos { }; playFocus.requestFocus() } }
    }
}

private fun day(context: android.content.Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)

@Composable
private fun ProgrammeRow(programme: GuideProgramme, now: Long, time: (Long) -> String, television: Boolean) {
    val p = cinemaPalette()
    val onNow = programme.startMs <= now && now < programme.endMs
    var focused by remember { mutableStateOf(false) }
    // On TV each row takes focus so the remote can scroll the schedule.
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (onNow) p.focus.copy(alpha = .10f) else androidx.compose.ui.graphics.Color.Transparent)
        .border(if (focused) 2.dp else 0.dp, if (focused) p.focus else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
        .onFocusChanged { focused = it.isFocused }
        .then(if (television) Modifier.focusable() else Modifier)
        .padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${time(programme.startMs)}–${time(programme.endMs)}", color = if (onNow) p.focus else p.muted, style = MaterialTheme.typography.labelMedium)
            if (onNow) Text(stringResource(DR.string.guide_on_now), color = p.focus, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(programme.title, fontWeight = if (onNow) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (onNow) GuideProgress(GuideSlot(programme.title, programme.startMs, programme.endMs), now, Modifier.fillMaxWidth(.5f))
        programme.description?.let { Text(it, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
