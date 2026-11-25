package com.fireamun.iptv.core.storage

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.LibraryArtwork
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Called only under RoomLibraryStore's mutex and database transaction. */
internal class WatchProgressStore(private val dao: LibraryDao) {
    fun digest(target: ProgressTarget, key: ByteArray): String {
        val reference = "watch-progress\u0000${target.sourceId}\u0000${target.kind.name}\u0000${target.rootItemId}\u0000${target.episodeProviderKey.orEmpty()}".toByteArray(Charsets.UTF_8)
        return try {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
            val result = mac.doFinal(reference)
            try { result.joinToString("") { "%02x".format(it.toInt() and 255) } }
            finally { result.fill(0) }
        } finally { reference.fill(0) }
    }

    private fun rootMatches(target: ProgressTarget, root: EntryRow): Boolean = root.sourceId == target.sourceId && root.id == target.rootItemId &&
        root.kind == when {
            target.kind == ContentKind.MOVIE -> ContentKind.MOVIE.name
            target.episodeProviderKey != null -> ContentKind.SERIES.name
            else -> ContentKind.EPISODE.name
        }

    suspend fun save(target: ProgressTarget, progress: WatchProgress, snapshot: EpisodeSnapshot?, root: EntryRow, key: ByteArray) {
        require(rootMatches(target, root))
        require((target.episodeProviderKey == null && snapshot == null) ||
            (target.episodeProviderKey != null && snapshot != null && snapshot.providerKey == target.episodeProviderKey))
        val digest = digest(target, key)
        val plain = snapshot?.let(EpisodeSnapshotCodec::encode)
        val encrypted = try { plain?.let { AesGcm { SecretKeySpec(key, "AES") }.seal(it, context(target.sourceId, digest)) } }
        finally { plain?.fill(0) }
        dao.progress(WatchProgressRow(target.sourceId, digest, target.rootItemId, target.kind.name, progress.positionMs,
            progress.durationMs, progress.completed, progress.watchedAtMs, encrypted))
    }

    suspend fun read(target: ProgressTarget, root: EntryRow, key: ByteArray): ProgressRecord? {
        if (!rootMatches(target, root)) return null
        val row = dao.progress(target.sourceId, digest(target, key)) ?: return null
        return read(ProgressReference(row.sourceId, row.refDigest), row, root, key)?.takeIf { it.target == target }
    }

    fun read(reference: ProgressReference, row: WatchProgressRow, root: EntryRow, key: ByteArray): ProgressRecord? {
        require(reference.sourceId == row.sourceId && reference.refDigest == row.refDigest)
        val snapshot = openSnapshot(row, key)
        val target = ProgressTarget(row.sourceId, row.rootItemId, ContentKind.valueOf(row.kind), snapshot?.providerKey)
        require(rootMatches(target, root) && (snapshot != null) == (target.episodeProviderKey != null))
        val expected = digest(target, key)
        require(MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), reference.refDigest.toByteArray(Charsets.US_ASCII)))
        return ProgressRecord(target, row.progress(), snapshot)
    }

    suspend fun recent(sourceId: String, kind: ContentKind, limit: Int, key: ByteArray): List<RecentWatch> =
        dao.recentProgress(sourceId, kind.name, limit).map { joined ->
            val row = joined.watch
            val snapshot = openSnapshot(row, key)
            val target = ProgressTarget(row.sourceId, row.rootItemId, ContentKind.valueOf(row.kind), snapshot?.providerKey)
            require(digest(target, key) == row.refDigest)
            val reference = ProgressReference(row.sourceId, row.refDigest)
            RecentWatch(reference, target.kind, snapshot?.title ?: joined.rootTitle,
                if (snapshot != null) joined.rootTitle else null, row.rootItemId, snapshot?.season, snapshot?.episode,
                row.progress(), if (joined.rootArtwork != null) LibraryArtwork(sourceId, row.rootItemId, joined.generation) else null, joined.rootGroup)
        }

    private fun openSnapshot(row: WatchProgressRow, key: ByteArray): EpisodeSnapshot? {
        val plain = row.snapshot?.let { AesGcm { SecretKeySpec(key, "AES") }.open(it, context(row.sourceId, row.refDigest)) }
        return try { plain?.let(EpisodeSnapshotCodec::decode) } finally { plain?.fill(0) }
    }

    private fun WatchProgressRow.progress() = WatchProgress(positionMs, durationMs, completed, watchedAtMs)
    private fun context(sourceId: String, digest: String) = "watch-progress:$sourceId:$digest"
}
