package com.fireamun.iptv.core.playback

/** Presentation only; no source, locator, track identifier or player crosses this boundary. */
data class PlaybackPresentation(
    val canEnter: Boolean = false,
    val wantsToPlay: Boolean = false,
    val isPlaying: Boolean = false,
    val aspectRatio: Float = 16f / 9f,
)

class PlaybackCommands(val play: () -> Unit, val pause: () -> Unit, val close: () -> Unit)
