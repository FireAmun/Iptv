package com.fireamun.iptv.core.playback

import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.media3.exoplayer.ExoPlayer
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
    television: Boolean = false,
    inPictureInPicture: Boolean = false,
    onMinimize: (() -> Unit)? = null,
    onPresentation: (PlaybackPresentation) -> Unit = {},
    onCommands: (PlaybackCommands?) -> Unit = {},
    onVideoBounds: (android.graphics.Rect) -> Unit = {},
) {
    key(request.sessionId) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val latestStart by rememberUpdatedState(startInstance)
        val latestCheckpoint by rememberUpdatedState(onCheckpoint)
        val latestStatus by rememberUpdatedState(onStatus)
        val latestDisposed by rememberUpdatedState(onDisposed)
        val latestPresentation by rememberUpdatedState(onPresentation)
        val latestCommands by rememberUpdatedState(onCommands)
        val latestBounds by rememberUpdatedState(onVideoBounds)
        var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }
        var playerView by remember { mutableStateOf<PlayerView?>(null) }
        val session = remember {
            LifecyclePlayerSession(context.applicationContext, request,
                { latestStart() }, { id, value -> latestCheckpoint(id, value) },
                { latestStatus(it) }, { latestDisposed() }, { activePlayer = it },
                { latestPresentation(it) }, { latestCommands(it) })
        }
        Column(modifier) {
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth().testTag("player_surface").onGloballyPositioned {
            playerView?.let { view ->
                val bounds = android.graphics.Rect()
                if (view.getGlobalVisibleRect(bounds)) latestBounds(bounds)
            }
        }, factory = { viewContext ->
            PlayerView(viewContext).apply {
                playerView = this
                setBackgroundColor(android.graphics.Color.BLACK)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
                    setPlayedColor(Cinema.Saffron.toArgb())
                    setScrubberColor(Cinema.Saffron.toArgb())
                    setBufferedColor(Cinema.Sand.toArgb())
                }
                keepScreenOn = true
                useController = !inPictureInPicture
                // The Cinema menus below own track selection consistently on phone and TV.
                setShowSubtitleButton(false)
                findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)?.visibility = android.view.View.GONE
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setErrorMessageProvider { android.util.Pair.create(0, viewContext.getString(R.string.playback_error)) }
                session.setControlsVisible(!inPictureInPicture)
                session.attach(this)
            }
        }, update = { session.setControlsVisible(!inPictureInPicture); session.attach(it) }, onRelease = { session.close() })
        if (!inPictureInPicture) PlayerTrackControls(activePlayer, television,
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 4.dp), onMinimize = onMinimize)
        }
        DisposableEffect(session, lifecycle) {
            lifecycle.addObserver(session)
            onDispose { lifecycle.removeObserver(session); session.close() }
        }
    }
}
