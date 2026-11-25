package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.LibraryArtwork
import com.fireamun.iptv.core.model.MediaAddress

private val episodeKeyPattern = Regex("EPISODE:[1-9][0-9]*")

internal fun validEpisodeKey(value: String): Boolean = episodeKeyPattern.matches(value) && value.substringAfter(':').toLongOrNull() != null
internal fun validSourceId(value: String): Boolean = value.isNotBlank() && value.length <= 128 && value.none(Char::isISOControl)

data class ProgressTarget(val sourceId: String, val rootItemId: Int, val kind: ContentKind, val episodeProviderKey: String? = null) {
    init {
        require(validSourceId(sourceId) && rootItemId >= 0)
        require(kind == ContentKind.MOVIE || kind == ContentKind.EPISODE)
        require(episodeProviderKey == null || kind == ContentKind.EPISODE && validEpisodeKey(episodeProviderKey))
    }
    override fun toString() = "[protected progress target]"
}

class EpisodeSnapshot(val providerKey: String, val title: String?, val season: Int?, val episode: Int?, val address: MediaAddress) {
    init {
        require(validEpisodeKey(providerKey))
        require(title == null || title.length <= 512 && title.none(Char::isISOControl))
        require(season == null || season >= 0)
        require(episode == null || episode >= 0)
    }
    override fun toString() = "[protected episode snapshot]"
}

data class WatchProgress(val positionMs: Long, val durationMs: Long?, val completed: Boolean, val watchedAtMs: Long) {
    init { require(positionMs >= 0 && (durationMs == null || durationMs > 0) && watchedAtMs >= 0) }
}

class ProgressRecord(val target: ProgressTarget, val progress: WatchProgress, val snapshot: EpisodeSnapshot?) {
    override fun toString() = "[protected progress record]"
}

data class ProgressReference(val sourceId: String, val refDigest: String) {
    init { require(validSourceId(sourceId) && refDigest.length == 64 && refDigest.all { it in '0'..'9' || it in 'a'..'f' }) }
    override fun toString() = "[protected progress reference]"
}

data class RecentWatch(val reference: ProgressReference, val kind: ContentKind, val title: String?, val parentTitle: String?, val rootItemId: Int,
    val season: Int?, val episode: Int?, val progress: WatchProgress, val artwork: LibraryArtwork?, val rootGroup: String? = null) {
    override fun toString() = "[protected recent watch]"
}

interface PlaybackProgressPersistence {
    suspend fun read(target: ProgressTarget): ProgressRecord?
    suspend fun read(reference: ProgressReference): ProgressRecord?
    suspend fun save(target: ProgressTarget, progress: WatchProgress, snapshot: EpisodeSnapshot? = null)
    suspend fun reset(target: ProgressTarget)
    suspend fun recent(sourceId: String, kind: ContentKind, limit: Int = 20): List<RecentWatch>
}
