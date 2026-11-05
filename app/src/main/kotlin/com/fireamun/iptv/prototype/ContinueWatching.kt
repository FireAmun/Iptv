package com.fireamun.iptv.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.fireamun.iptv.core.data.RecentWatch
import com.fireamun.iptv.core.designsystem.*
import com.fireamun.iptv.ui.mobile.CinemaTheme
import java.util.Locale

internal fun mediaTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

@Composable
internal fun ContinueWatchingRow(items: List<RecentWatch>, loading: Boolean, imageLoader: ImageLoader,
    list: LazyListState, targetIndex: Int, focus: FocusRequester, onPlay: (RecentWatch) -> Unit, onFocused: (RecentWatch, Int) -> Unit) {
    val p = cinemaPalette()
    val compactTv = LocalBrowserTv.current && LocalConfiguration.current.screenHeightDp < 600
    Column(Modifier.fillMaxWidth().testTag("continue_watching"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.browse_continue_watching), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        LazyRow(state = list, modifier = Modifier.fillMaxWidth().testTag("continue_watching_row"), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(3.dp)) {
            itemsIndexed(items, key = { _, item -> item.reference.refDigest }) { index, item ->
                BrowserTarget({ onPlay(item) }, Modifier.width(if (compactTv) 280.dp else 164.dp).heightIn(min = 48.dp)
                    .then(if (index == targetIndex) Modifier.focusRequester(focus) else Modifier)
                    .testTag("recent_${item.reference.refDigest}"), enabled = !loading, onFocused = { onFocused(item, index) }) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (compactTv) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RecentArtwork(item, imageLoader, Modifier.size(64.dp))
                                RecentLabels(item, compact = true, modifier = Modifier.weight(1f))
                            }
                        } else {
                            RecentArtwork(item, imageLoader, Modifier.fillMaxWidth().height(88.dp))
                            RecentLabels(item, compact = false)
                        }
                        val duration = item.progress.durationMs?.takeIf { it > 0 }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            if (duration != null) LinearProgressIndicator(progress = { (item.progress.positionMs.toDouble() / duration).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth(), color = p.focus, trackColor = p.outline)
                            Text(mediaTime(item.progress.positionMs), color = p.muted, style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr), modifier = Modifier.testTag("recent_time_${item.reference.refDigest}"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentArtwork(item: RecentWatch, imageLoader: ImageLoader, modifier: Modifier) {
    val p = cinemaPalette()
    Box(modifier.background(p.raised, RoundedCornerShape(10.dp)).testTag("recent_artwork_${item.reference.refDigest}"), contentAlignment = Alignment.Center) {
        CinemaIcon(if (item.parentTitle != null) CinemaSymbol.SERIES else CinemaSymbol.MOVIES, tint = p.focus)
        item.artwork?.let { artwork -> AsyncImage(model = artwork, imageLoader = imageLoader, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun RecentLabels(item: RecentWatch, compact: Boolean, modifier: Modifier = Modifier) {
    val p = cinemaPalette()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item.title ?: stringResource(R.string.browse_progress_untitled), fontWeight = FontWeight.Bold, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        item.parentTitle?.let { Text(it, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
        if (item.season != null && item.episode != null) Text(stringResource(R.string.browse_episode, item.season!!, item.episode!!), color = p.muted, style = MaterialTheme.typography.labelSmall, maxLines = if (compact) 1 else 2)
    }
}

@Composable
internal fun ProgressOverlays(state: ContinueWatchingState, television: Boolean, onContinue: () -> Unit,
    onStartOver: () -> Unit, onCancel: () -> Unit, onDismissError: () -> Unit) {
    CompositionLocalProvider(LocalBrowserTv provides television) { CinemaTheme {
        val p = cinemaPalette()
        val prompt = state.prompt
        if (prompt != null) {
            val focus = remember(prompt) { FocusRequester() }
            val startOverFocus = remember(prompt) { FocusRequester() }
            val cancelFocus = remember(prompt) { FocusRequester() }
            var focusedOnce by remember(prompt) { mutableStateOf(false) }
            Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                val window = LocalWindowInfo.current
                BoxWithConstraints(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
                    Surface(Modifier.widthIn(max = 540.dp).fillMaxWidth().heightIn(max = maxHeight).testTag("resume_dialog"), shape = RoundedCornerShape(22.dp), color = p.panel, contentColor = p.text) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(prompt.title ?: stringResource(R.string.browse_progress_untitled), fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (state.error == ProgressProblem.RESET) Text(stringResource(R.string.browse_progress_reset_failed), color = p.error, modifier = Modifier.testTag("progress_error"))
                            // Isolate the media timestamp inside the translated label, retaining the surrounding RTL text.
                            BrowserTarget(onContinue, Modifier.fillMaxWidth().heightIn(min = 52.dp).focusRequester(focus)
                                .then(if (television) Modifier.focusProperties { up = FocusRequester.Cancel; down = if (state.loading) cancelFocus else startOverFocus } else Modifier)
                                .testTag("resume_continue"), enabled = !state.loading) {
                                Text(stringResource(R.string.browse_continue_at, "\u2066${mediaTime(prompt.positionMs)}\u2069"), Modifier.padding(14.dp), color = p.focus, fontWeight = FontWeight.Bold)
                            }
                            BrowserTarget(onStartOver, Modifier.fillMaxWidth().heightIn(min = 52.dp).focusRequester(startOverFocus)
                                .then(if (television) Modifier.focusProperties { up = focus; down = cancelFocus } else Modifier)
                                .testTag("resume_start_over"), enabled = !state.loading) { Text(stringResource(R.string.browse_start_over), Modifier.padding(14.dp)) }
                            BrowserTarget(onCancel, Modifier.fillMaxWidth().heightIn(min = 52.dp).focusRequester(cancelFocus)
                                .then(if (television) Modifier.focusProperties { up = if (state.loading) FocusRequester.Cancel else startOverFocus; down = FocusRequester.Cancel } else Modifier)
                                .testTag("resume_cancel"), quiet = true) { Text(stringResource(R.string.browse_progress_cancel), Modifier.padding(14.dp), color = p.muted) }
                        }
                    }
                }
                LaunchedEffect(window.isWindowFocused, state.loading) {
                    if (television && window.isWindowFocused && !state.loading && !focusedOnce) {
                        withFrameNanos { }
                        focusedOnce = true
                        focus.requestFocus()
                    }
                }
            }
        } else if (state.error != null) {
            Dialog(onDismissRequest = onDismissError, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                BoxWithConstraints(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
                    Surface(Modifier.widthIn(max = 540.dp).fillMaxWidth().heightIn(max = maxHeight), color = p.panel, contentColor = p.text, shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(if (state.error == ProgressProblem.SAVE) R.string.browse_progress_save_failed else R.string.browse_progress_read_failed), color = p.error, modifier = Modifier.testTag("progress_error"))
                            TextButton(onClick = onDismissError, modifier = Modifier.heightIn(min = 48.dp).focusProperties { canFocus = true }.testTag("progress_dismiss")) { Text(stringResource(R.string.browse_close), color = p.focus) }
                        }
                    }
                }
            }
        }
    } }
}
