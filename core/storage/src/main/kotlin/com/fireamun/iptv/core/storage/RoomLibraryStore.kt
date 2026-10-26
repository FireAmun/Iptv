package com.fireamun.iptv.core.storage

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class RoomLibraryStore private constructor(private val database: LibraryDatabase, private val protector: EnvelopeProtector) : LibraryPersistence {
    private val dao = database.library()
    private val mutex = Mutex()
    private class Envelope(val source: SourceSpec, val key: ByteArray)

    override suspend fun restore(): SavedLibrary? = guarded {
        mutex.withLock {
            database.withTransaction {
                val row = dao.source()
                if (row == null) { require(dao.sources().isEmpty()); null } else restoreRow(row)
            }
        }
    }

    private suspend fun restoreRow(row: SourceRow): SavedLibrary {
        val envelope = envelope(row)
        return try {
            val rows = dao.entries(row.sourceId)
            require(rows.isNotEmpty() && rows.size <= MAX_CATALOGUE_ENTRIES)
            val cipher = AesGcm { SecretKeySpec(envelope.key, "AES") }
            val entries = rows.map { entry ->
                currentCoroutineContext().ensureActive()
                val raw = entry.address?.let { cipher.open(it, addressContext(row, entry.id)) }
                val address = try { raw?.let { MediaAddress.parse(it.toString(Charsets.UTF_8)) ?: error("Invalid protected address") } } finally { raw?.fill(0) }
                val kind = ContentKind.valueOf(entry.kind)
                require((kind == ContentKind.SERIES && entry.seriesId != null) || address != null)
                PlaylistEntry(Channel(entry.id, entry.title, entry.groupLabel, kind), address, entry.seriesId, entry.providerKey)
            }
            SavedLibrary(envelope.source, entries, row.savedAt, row.sourceId)
        } finally { envelope.key.fill(0) }
    }

    override suspend fun directory(): SourceDirectory = guarded {
        mutex.withLock {
            database.withTransaction { SourceDirectory(dao.sources().map { summary(it) }, dao.active()) }
        }
    }

    private fun summary(row: SourceRow): SavedSource {
        // An unreadable label must not hide the opaque ID needed for targeted recovery.
        return try {
            var kind = row.kind?.let(SourceKind::valueOf)
            var name: String? = null
            if (row.label != null) {
                val bytes = protector.open(row.label, "label:${row.sourceId}")
                try { name = normalizeSourceName(bytes.toString(Charsets.UTF_8)) ?: error("Invalid protected label") }
                finally { bytes.fill(0) }
            } else if (kind == null) {
                val old = envelope(row)
                try { kind = old.source.kind; name = old.source.name } finally { old.key.fill(0) }
            }
            SavedSource(row.sourceId, name, kind, row.slot)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { SavedSource(row.sourceId, null, null, row.slot) }
    }

    override suspend fun activate(sourceId: String): SavedLibrary = guarded {
        mutex.withLock {
            database.withTransaction {
                val row = dao.source(sourceId) ?: error("Missing saved source")
                // Authenticate the entire target before committing the selected source pointer.
                restoreRow(row).also { dao.active(ActiveRow(sourceId = sourceId)) }
            }
        }
    }

    override suspend fun rename(sourceId: String, name: String): Unit = guarded {
        val normalized = normalizeSourceName(name) ?: throw ImportFailure(ImportError.INVALID_NAME)
        mutex.withLock {
            database.withTransaction {
                val row = dao.source(sourceId) ?: error("Missing saved source")
                val authenticated = envelope(row)
                val kind = authenticated.source.kind.name
                authenticated.key.fill(0)
                dao.source(row.copy(kind = kind, label = sealLabel(normalized, sourceId)))
            }
        }
    }

    override suspend fun remove(sourceId: String): Unit = guarded {
        mutex.withLock {
            val empty = database.withTransaction {
                require(dao.source(sourceId) != null)
                dao.clearEntries(sourceId); dao.clearIdentities(sourceId); dao.clearSource(sourceId)
                val remaining = dao.sources()
                if (remaining.isEmpty()) dao.clearActive()
                else {
                    val active = dao.active()
                    if (remaining.none { it.sourceId == active }) dao.active(ActiveRow(sourceId = remaining.first().sourceId))
                }
                remaining.isEmpty()
            }
            // Never destroy another saved source's wrapping key, or a rolled-back snapshot's key.
            if (empty) clearUnusedKey()
        }
    }

    override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?): SavedLibrary = guarded {
        mutex.withLock {
            require(entries.isNotEmpty() && entries.size <= MAX_CATALOGUE_ENTRIES)
            val previous = if (refreshing && targetSourceId != null) dao.source(targetSourceId) else dao.source()
            require(previous != null || dao.sources().isEmpty())
            // Retry orphan-key cleanup strictly before a fresh import; no saved source depends on it.
            if (previous == null) protector.clear()
            // Even replacement must fail closed if an existing key/envelope is unreadable.
            val old = previous?.let { envelope(it) }
            val id = if (refreshing) previous?.sourceId ?: error("Missing saved source") else UUID.randomUUID().toString()
            val key = if (refreshing) old!!.key.copyOf() else ByteArray(32).also { SecureRandom().nextBytes(it) }
            try {
                if (refreshing) {
                    val before = old!!.source.encode(); val after = source.encode()
                    try { require(MessageDigest.isEqual(before, after)) } finally { before.fill(0); after.fill(0) }
                }
                val secret = if (refreshing) previous!!.secret else sealEnvelope(source, key, id)
                val slot = if (refreshing) previous!!.slot else (dao.maxSlot().toLong() + 1).also { require(it <= Int.MAX_VALUE) }.toInt()
                val row = SourceRow(slot = slot, sourceId = id,
                    generation = UUID.randomUUID().toString(), secret = secret, savedAt = System.currentTimeMillis(), kind = source.kind.name,
                    label = if (refreshing) previous!!.label ?: old!!.source.name?.let { sealLabel(it, id) } else source.name?.let { sealLabel(it, id) })
                val identities = (if (refreshing) dao.identities(id) else emptyList()).associateBy { it.identityKey }.toMutableMap()
                var nextId = dao.maxId().toLong() + 1
                val added = mutableListOf<IdentityRow>()
                val occurrences = mutableMapOf<String, Int>()
                val cipher = AesGcm { SecretKeySpec(key, "AES") }
                val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
                val remapped = mutableListOf<PlaylistEntry>()
                val rows = entries.mapIndexed { index, entry ->
                    currentCoroutineContext().ensureActive()
                    require((entry.channel.title?.length ?: 0) <= 512 && (entry.channel.group?.length ?: 0) <= 512)
                    val identity = entry.providerKey?.let { "provider:$it" } ?: "locator:${entry.address?.reveal() ?: error("Missing locator")}"
                    val digest = buildString {
                        for (byte in mac.doFinal(identity.toByteArray(Charsets.UTF_8))) {
                            val value = byte.toInt() and 255
                            append("0123456789abcdef"[value ushr 4]); append("0123456789abcdef"[value and 15])
                        }
                    }
                    val occurrence = occurrences.getOrDefault(digest, 0); occurrences[digest] = occurrence + 1
                    val identityKey = "$digest:$occurrence"
                    val stable = identities[identityKey] ?: run {
                        require(nextId <= Int.MAX_VALUE)
                        IdentityRow(nextId++.toInt(), id, identityKey).also { identities[identityKey] = it; added.add(it) }
                    }
                    val channel = entry.channel.copy(id = stable.id)
                    remapped.add(PlaylistEntry(channel, entry.address, entry.seriesId, entry.providerKey))
                    val plaintext = entry.address?.reveal()?.toByteArray(Charsets.UTF_8)
                    val encrypted = try { plaintext?.let { cipher.seal(it, addressContext(row, stable.id)) } } finally { plaintext?.fill(0) }
                    EntryRow(stable.id, index, channel.title, channel.group, channel.kind.name, entry.seriesId, entry.providerKey, encrypted, id)
                }
                currentCoroutineContext().ensureActive()
                database.withTransaction {
                    dao.clearEntries(id)
                    added.chunked(250).forEach { dao.insertIdentities(it) }
                    rows.chunked(250).forEach { dao.insertEntries(it) }
                    dao.source(row)
                    dao.active(ActiveRow(sourceId = id))
                    currentCoroutineContext().ensureActive()
                }
                SavedLibrary(source, remapped, row.savedAt, id)
            } finally { key.fill(0); old?.key?.fill(0) }
        }
    }

    override suspend fun clear(): Unit = guarded {
        mutex.withLock {
            database.withTransaction {
                dao.clearEntries(); dao.clearIdentities(); dao.clearSource(); dao.clearActive()
            }
            clearUnusedKey()
        }
    }
    fun close() = database.close()

    private fun clearUnusedKey() {
        // The DB deletion is committed. Platform cleanup failure must not revive removed data in UI.
        try { protector.clear() }
        catch (_: GeneralSecurityException) { }
        catch (_: java.security.ProviderException) { }
        catch (_: IOException) { }
        catch (_: SecurityException) { }
    }

    private fun sealEnvelope(source: SourceSpec, key: ByteArray, id: String): ByteArray {
        val sourceBytes = source.encode()
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).use { out -> out.write(key); out.write(sourceBytes) } }.toByteArray()
        return try { protector.seal(bytes, "source:$id") } finally { sourceBytes.fill(0); bytes.fill(0) }
    }
    private fun sealLabel(name: String, id: String): ByteArray {
        val bytes = name.toByteArray(Charsets.UTF_8)
        return try { protector.seal(bytes, "label:$id") } finally { bytes.fill(0) }
    }
    private fun envelope(row: SourceRow): Envelope {
        val bytes = protector.open(row.secret, "source:${row.sourceId}")
        return try {
            require(bytes.size in 33..16_416)
            val sourceBytes = bytes.copyOfRange(32, bytes.size)
            try { Envelope(SourceSpec.decode(sourceBytes), bytes.copyOfRange(0, 32)) } finally { sourceBytes.fill(0) }
        } finally { bytes.fill(0) }
    }
    private fun addressContext(row: SourceRow, id: Int) = "entry:${row.sourceId}:${row.generation}:$id"
    private suspend fun <T> guarded(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ImportFailure) { throw failure }
        catch (_: GeneralSecurityException) { throw ImportFailure(ImportError.STORAGE) }
        catch (_: java.security.ProviderException) { throw ImportFailure(ImportError.STORAGE) }
        catch (_: IOException) { throw ImportFailure(ImportError.STORAGE) }
        catch (_: android.database.SQLException) { throw ImportFailure(ImportError.STORAGE) }
        catch (_: SecurityException) { throw ImportFailure(ImportError.STORAGE) }
        catch (_: IllegalStateException) { throw ImportFailure(ImportError.STORAGE) }
        catch (_: IllegalArgumentException) { throw ImportFailure(ImportError.STORAGE) }
    }
    companion object {
        fun open(context: Context, name: String = "library.db", protector: EnvelopeProtector = KeystoreEnvelope()) =
            RoomLibraryStore(Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, name).addMigrations(MIGRATION_1_2).build(), protector)
    }
}
