package com.fireamun.iptv.core.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.PlaybackCheckpoint
import com.fireamun.iptv.core.model.PlaybackRequest
import com.fireamun.iptv.core.model.PlaybackStart
import com.fireamun.iptv.core.model.PlaybackStatus
import com.fireamun.iptv.core.model.isBookmarkEligible
import com.fireamun.iptv.core.model.resumeOffset

@OptIn(UnstableApi::class)
internal class LifecyclePlayerSession(
    private val context: Context,
    private val request: PlaybackRequest,
    private val startInstance: () -> PlaybackStart?,
    private val onCheckpoint: (String, PlaybackCheckpoint) -> Unit,
    private val onStatus: (PlaybackStatus) -> Unit,
    private val onDisposed: () -> Unit,
    private val onPlayerChanged: (ExoPlayer?) -> Unit = {},
    private val onPresentation: (PlaybackPresentation) -> Unit = {},
    private val onCommands: (PlaybackCommands?) -> Unit = {},
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var listener: Player.Listener? = null
    private var view: PlayerView? = null
    private var instanceId: String? = null
    private var prepared = false
    private var checkedTimeline = false
    private var lastEligibleCheckpoint: PlaybackCheckpoint? = null
    private var closed = false
    private var controlsVisible = true
    private var playbackIntent = true
    private var selectionParameters: TrackSelectionParameters? = null
    private val poll = object : Runnable {
        override fun run() {
            val active = player ?: return
            if (closed || !active.isPlaying) return
            checkpoint(active)
            handler.postDelayed(this, 5_000)
        }
    }

    fun attach(target: PlayerView) {
        if (closed || view === target) return
        view?.player = null
        view = target
        target.player = player
        target.useController = controlsVisible
        if (controlsVisible) target.showController() else target.hideController()
        if (controlsVisible) target.requestFocus()
    }

    fun setControlsVisible(visible: Boolean) {
        if (controlsVisible == visible) return
        controlsVisible = visible
        view?.apply {
            useController = visible
            if (visible) showController() else hideController()
        }
    }

    private fun present(active: ExoPlayer) {
        if (closed || player !== active) return
        val size = active.videoSize
        val ratio = if (size.width > 0 && size.height > 0)
            (size.width * size.pixelWidthHeightRatio / size.height).takeIf { it.isFinite() && it > 0 } ?: (16f / 9f)
        else 16f / 9f
        val available = active.playerError == null && active.currentTracks.isTypeSupported(C.TRACK_TYPE_VIDEO) &&
            active.playbackState in setOf(Player.STATE_READY, Player.STATE_BUFFERING)
        onPresentation(PlaybackPresentation(available,
            active.playWhenReady && active.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            active.isPlaying, ratio))
    }

    override fun onStart(owner: LifecycleOwner) {
        if (closed || player != null) return
        val start = startInstance() ?: return
        instanceId = start.instanceId
        prepared = false
        checkedTimeline = false
        lastEligibleCheckpoint = null
        // Nested network exceptions may contain credentials; never enable provider logs.
        Log.setLogLevel(Log.LOG_LEVEL_OFF)
        val sources = DefaultMediaSourceFactory(PlaybackDataSourceFactory(allowCleartext = request.address.isCleartext))
        val active = ExoPlayer.Builder(context).setMediaSourceFactory(sources)
            .setSeekBackIncrementMs(30_000).setSeekForwardIncrementMs(30_000).build()
        player = active
        selectionParameters?.let { active.trackSelectionParameters = it }
        onPlayerChanged(active)
        onCommands(PlaybackCommands(
            { if (!closed && player === active) active.play() },
            { if (!closed && player === active) active.pause() },
            { close() },
        ))
        active.setAudioAttributes(AudioAttributes.DEFAULT, true)
        active.setHandleAudioBecomingNoisy(true)
        val observer = object : Player.Listener {
            private fun current() = !closed && player === active
            override fun onEvents(player: Player, events: Player.Events) { if (current()) present(active) }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!current()) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> onStatus(PlaybackStatus.BUFFERING)
                    Player.STATE_READY -> {
                        prepared = true
                        if (!checkedTimeline && request.kind in setOf(ContentKind.MOVIE, ContentKind.EPISODE)) {
                            checkedTimeline = true
                            if (!isBookmarkEligible(request.kind, true, active.isCurrentMediaItemSeekable,
                                    active.isCurrentMediaItemDynamic, active.isCurrentMediaItemLive)) {
                                active.seekToDefaultPosition()
                            } else if (active.duration > 0 && active.contentPosition >= active.duration) {
                                active.seekTo(resumeOffset(active.contentPosition, active.duration))
                            }
                        }
                        onStatus(if (active.isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED)
                    }
                    Player.STATE_ENDED -> {
                        checkpoint(active, ended = true)
                        onStatus(PlaybackStatus.ENDED)
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!current()) return
                handler.removeCallbacks(poll)
                if (isPlaying) handler.postDelayed(poll, 5_000) else checkpoint(active)
                if (active.playbackState == Player.STATE_READY) onStatus(if (isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED)
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (current() && reason == Player.DISCONTINUITY_REASON_SEEK) checkpoint(active, ended = false)
            }
            override fun onPlayerError(error: PlaybackException) {
                if (!current()) return
                val latest = checkpoint(active)
                if (latest?.eligible != true) {
                    lastEligibleCheckpoint?.let { saved -> instanceId?.let { onCheckpoint(it, saved) } }
                }
                onStatus(PlaybackStatus.FAILED)
            }
        }
        listener = observer
        active.addListener(observer)
        onStatus(PlaybackStatus.PREPARING)
        view?.apply {
            player = active
            useController = controlsVisible
            if (controlsVisible) showController() else hideController()
            if (controlsVisible) requestFocus()
        }
        val item = MediaItem.fromUri(request.address.reveal())
        if (request.kind in setOf(ContentKind.MOVIE, ContentKind.EPISODE))
            active.setMediaItem(item, resumeOffset(start.positionMs, null))
        else active.setMediaItem(item)
        active.prepare()
        active.playWhenReady = playbackIntent
    }

    override fun onStop(owner: LifecycleOwner) { release() }
    override fun onDestroy(owner: LifecycleOwner) { close() }

    private fun checkpoint(active: ExoPlayer, ended: Boolean = active.playbackState == Player.STATE_ENDED): PlaybackCheckpoint? {
        if (closed || player !== active) return null
        val id = instanceId ?: return null
        val position = active.contentPosition
        val duration = active.duration.takeIf { it > 0 }
        val value = PlaybackCheckpoint(position, duration, ended,
            isBookmarkEligible(request.kind, prepared, active.isCurrentMediaItemSeekable,
                active.isCurrentMediaItemDynamic, active.isCurrentMediaItemLive))
        if (value.eligible && value.positionMs >= 0) lastEligibleCheckpoint = value
        onCheckpoint(id, value)
        return value
    }

    private fun release() {
        handler.removeCallbacks(poll)
        val old = player ?: return
        playbackIntent = old.playWhenReady
        selectionParameters = old.trackSelectionParameters
        checkpoint(old, old.playbackState == Player.STATE_ENDED)
        player = null
        onPlayerChanged(null)
        onPresentation(PlaybackPresentation())
        onCommands(null)
        view?.player = null
        listener?.let(old::removeListener)
        listener = null
        instanceId = null
        prepared = false
        lastEligibleCheckpoint = null
        old.release()
    }

    fun close() {
        if (closed) return
        release()
        closed = true
        view = null
        onDisposed()
    }
}
