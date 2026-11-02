package com.fireamun.iptv.core.playback

import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.DefaultTimeBar
import androidx.compose.ui.graphics.toArgb
import com.fireamun.iptv.core.designsystem.Cinema
import com.fireamun.iptv.core.model.PlaybackCheckpoint
import com.fireamun.iptv.core.model.PlaybackRequest
import com.fireamun.iptv.core.model.PlaybackStart
import com.fireamun.iptv.core.model.PlaybackStatus

@OptIn(UnstableApi::class)
@Composable
fun PlaybackSurface(
    request: PlaybackRequest,
    startInstance: () -> PlaybackStart?,
    onCheckpoint: (String, PlaybackCheckpoint) -> Unit,
    onStatus: (PlaybackStatus) -> Unit,
    onDisposed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    key(request.sessionId) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val latestStart by rememberUpdatedState(startInstance)
        val latestCheckpoint by rememberUpdatedState(onCheckpoint)
        val latestStatus by rememberUpdatedState(onStatus)
        val latestDisposed by rememberUpdatedState(onDisposed)
        val session = remember {
            LifecyclePlayerSession(context.applicationContext, request,
                { latestStart() }, { id, value -> latestCheckpoint(id, value) },
                { latestStatus(it) }, { latestDisposed() })
        }
        AndroidView(modifier = modifier.testTag("player_surface"), factory = { viewContext ->
            PlayerView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
                    setPlayedColor(Cinema.Saffron.toArgb())
                    setScrubberColor(Cinema.Saffron.toArgb())
                    setBufferedColor(Cinema.Sand.toArgb())
                }
                keepScreenOn = true
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setErrorMessageProvider { android.util.Pair.create(0, viewContext.getString(R.string.playback_error)) }
                session.attach(this)
            }
        }, update = { session.attach(it) }, onRelease = { session.close() })
        DisposableEffect(session, lifecycle) {
            lifecycle.addObserver(session)
            onDispose { lifecycle.removeObserver(session); session.close() }
        }
    }
}
