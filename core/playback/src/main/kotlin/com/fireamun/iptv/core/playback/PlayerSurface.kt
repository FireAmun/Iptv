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
import com.fireamun.iptv.core.model.MediaAddress
import com.fireamun.iptv.core.model.PlaybackStatus

@OptIn(UnstableApi::class)
@Composable
fun PlaybackSurface(address: MediaAddress, retry: Int, onStatus: (PlaybackStatus) -> Unit, modifier: Modifier = Modifier) {
    key(address, retry) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val statusCallback by rememberUpdatedState(onStatus)
        val session = remember { LifecyclePlayerSession(context.applicationContext, address) { statusCallback(it) } }
        AndroidView(modifier = modifier.testTag("player_surface"), factory = { viewContext ->
            PlayerView(viewContext).apply {
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
