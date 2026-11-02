package com.fireamun.iptv.core.model

class PlaybackRequest(
    val sessionId: String,
    val address: MediaAddress,
    val kind: ContentKind,
    val initialOffsetMs: Long,
) {
    override fun toString(): String = "[protected playback request]"
}

data class PlaybackStart(val instanceId: String, val positionMs: Long)

data class PlaybackCheckpoint(
    val positionMs: Long,
    val durationMs: Long?,
    val ended: Boolean,
    val eligible: Boolean,
)

fun isBookmarkEligible(kind: ContentKind, prepared: Boolean, seekable: Boolean, dynamic: Boolean, live: Boolean): Boolean =
    kind in setOf(ContentKind.MOVIE, ContentKind.EPISODE) && prepared && seekable && !dynamic && !live

fun normalizedCheckpoint(value: PlaybackCheckpoint): PlaybackCheckpoint? {
    if (!value.eligible || value.positionMs < 0) return null
    val duration = value.durationMs?.takeIf { it > 0 }
    return value.copy(positionMs = duration?.let { value.positionMs.coerceAtMost(it) } ?: value.positionMs, durationMs = duration)
}

fun resumeOffset(positionMs: Long, durationMs: Long?): Long =
    positionMs.coerceAtLeast(0).let { position ->
        durationMs?.takeIf { it > 0 }?.let { position.coerceAtMost(it - 1) } ?: position
    }
