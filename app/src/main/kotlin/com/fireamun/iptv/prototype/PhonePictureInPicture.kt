package com.fireamun.iptv.prototype

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Process
import android.util.Rational
import androidx.lifecycle.Lifecycle
import com.fireamun.iptv.core.playback.PlaybackCommands
import com.fireamun.iptv.core.playback.PlaybackPresentation
import kotlin.math.roundToInt

/** Phone Activity integration only; the Surface remains the sole owner of the player. */
internal class PhonePictureInPicture(
    private val activity: MobileActivity,
    private val onVisibleChanged: (Boolean) -> Unit,
    private val onAvailableChanged: (Boolean) -> Unit,
    private val closePlayback: () -> Unit,
) {
    private var presentation = PlaybackPresentation()
    private var commands: PlaybackCommands? = null
    private var bounds: Rect? = null
    private var available = false
    private var inPictureInPicture = false
    private var entering = false
    private var pictureInPictureSinceResume = false
    private var stopped = false
    private var closedOnStop = false
    private var released = false
    private val mediaSession = MediaSession(activity, "Amar playback").apply {
        setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() { commands?.play?.invoke() }
            override fun onPause() { commands?.pause?.invoke() }
        })
    }

    fun update(value: PlaybackPresentation) {
        if (released) return
        presentation = value
        updateParams()
        updateMediaSession()
    }

    fun update(value: PlaybackCommands?) {
        if (released) return
        commands = value
        updateParams()
        updateMediaSession()
    }

    fun update(value: Rect) {
        if (released) return
        bounds = value.takeUnless { it.isEmpty }?.let(::Rect)
        updateParams()
    }

    fun onResume() {
        stopped = false
        closedOnStop = false
        inPictureInPicture = activity.isInPictureInPictureMode
        if (!inPictureInPicture) {
            entering = false
            pictureInPictureSinceResume = false
        } else pictureInPictureSinceResume = true
        refreshAvailability()
        publishVisibility()
        updateParams()
    }

    fun enter() {
        refreshAvailability()
        if (released || !available || !presentation.canEnter || commands == null || activity.isInPictureInPictureMode || activity.isFinishing || activity.isDestroyed ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        entering = true
        publishVisibility()
        val entered = try { activity.enterPictureInPictureMode(params()) }
        catch (_: IllegalStateException) { false }
        catch (_: IllegalArgumentException) { false }
        catch (_: SecurityException) { false }
        if (entered) pictureInPictureSinceResume = true
        else {
            entering = false
            publishVisibility()
        }
    }

    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < 31 && presentation.canEnter && presentation.wantsToPlay) enter()
    }

    fun onTransitionChanged(transitioning: Boolean) {
        entering = transitioning
        if (transitioning) pictureInPictureSinceResume = true
        publishVisibility()
    }

    fun onModeChanged(active: Boolean) {
        inPictureInPicture = active
        entering = false
        if (active) pictureInPictureSinceResume = true
        publishVisibility()
        // Expansion remains STARTED; dismissal can deliver this callback after STOP.
        if (!active && stopped) closeStoppedPlayback()
    }

    fun onStop() {
        stopped = true
        closeStoppedPlayback()
    }

    private fun closeStoppedPlayback() {
        if (!pictureInPictureSinceResume || closedOnStop || activity.isChangingConfigurations) return
        closedOnStop = true
        closePlayback()
        commands = null
        updateParams()
        updateMediaSession()
    }

    private fun refreshAvailability() {
        available = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && try {
            activity.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), activity.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: SecurityException) { false }
        onAvailableChanged(available)
    }

    private fun params(): PictureInPictureParams {
        val ratio = presentation.aspectRatio.takeIf { it.isFinite() && it > 0 } ?: (16f / 9f)
        // Round inward at the platform's inclusive 1:2.39 and 2.39:1 limits.
        val numerator = (ratio.coerceIn(1f / 2.39f, 2.39f) * 10_000).roundToInt().coerceIn(4_185, 23_900)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(numerator, 10_000))
        bounds?.let(builder::setSourceRectHint)
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(available && commands != null && presentation.canEnter && presentation.wantsToPlay)
        }
        return builder.build()
    }

    private fun updateParams() {
        if (released || activity.isDestroyed || !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        try { activity.setPictureInPictureParams(params()) }
        catch (_: IllegalStateException) { }
        catch (_: IllegalArgumentException) { }
        catch (_: SecurityException) { }
    }

    private fun publishVisibility() { onVisibleChanged(inPictureInPicture || entering) }

    private fun updateMediaSession() {
        if (released) return
        val active = commands != null && presentation.canEnter
        val state = when {
            !active -> PlaybackState.STATE_NONE
            presentation.isPlaying -> PlaybackState.STATE_PLAYING
            presentation.wantsToPlay -> PlaybackState.STATE_BUFFERING
            else -> PlaybackState.STATE_PAUSED
        }
        mediaSession.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (presentation.isPlaying) 1f else 0f)
            .build())
        mediaSession.isActive = active
    }

    fun close() {
        if (released) return
        released = true
        mediaSession.isActive = false
        mediaSession.release()
        commands = null
    }
}
