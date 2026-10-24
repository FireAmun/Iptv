package com.fireamun.iptv.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.fireamun.iptv.core.model.MediaAddress
import com.fireamun.iptv.core.model.PlaybackStatus

@OptIn(UnstableApi::class)
internal class LifecyclePlayerSession(
    private val context: Context,
    private val address: MediaAddress,
    private val onStatus: (PlaybackStatus) -> Unit,
) : DefaultLifecycleObserver {
    private var player: ExoPlayer? = null
    private var view: PlayerView? = null

    fun attach(target: PlayerView) {
        if (view === target) return
        view?.player = null
        view = target
        target.player = player
        target.showController()
        target.requestFocus()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (player != null) return
        // Nested network exceptions may contain credentials; never enable provider logs.
        Log.setLogLevel(Log.LOG_LEVEL_OFF)
        val sources = DefaultMediaSourceFactory(PlaybackDataSourceFactory(allowCleartext = address.isCleartext))
        val active = ExoPlayer.Builder(context).setMediaSourceFactory(sources).build()
        player = active
        active.setAudioAttributes(AudioAttributes.DEFAULT, true)
        active.setHandleAudioBecomingNoisy(true)
        active.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> onStatus(PlaybackStatus.BUFFERING)
                    Player.STATE_READY -> onStatus(if (active.isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED)
                    Player.STATE_ENDED -> onStatus(PlaybackStatus.ENDED)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (active.playbackState == Player.STATE_READY) onStatus(if (isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED)
            }
            override fun onPlayerError(error: PlaybackException) { onStatus(PlaybackStatus.FAILED) }
        })
        onStatus(PlaybackStatus.PREPARING)
        view?.apply { player = active; showController(); requestFocus() }
        active.setMediaItem(MediaItem.fromUri(address.reveal()))
        active.prepare()
        active.playWhenReady = true
    }

    override fun onStop(owner: LifecycleOwner) { release() }
    override fun onDestroy(owner: LifecycleOwner) { close() }
    private fun release() {
        view?.player = null
        val old = player
        player = null
        old?.release()
    }
    fun close() { release(); view = null }
}
