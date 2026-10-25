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
                val row = dao.source() ?: return@withTransaction null
                val envelope = envelope(row)
                try {
                    val rows = dao.entries()
                    require(rows.isNotEmpty() && rows.size <= 10_000)
                    val cipher = AesGcm { SecretKeySpec(envelope.key, "AES") }
                    val entries = rows.map { entry ->
                        currentCoroutineContext().ensureActive()
                        val raw = entry.address?.let { cipher.open(it, addressContext(row, entry.id)) }
                        val address = try { raw?.let { MediaAddress.parse(it.toString(Charsets.UTF_8)) ?: error("Invalid protected address") } } finally { raw?.fill(0) }
                        val kind = ContentKind.valueOf(entry.kind)
                        require((kind == ContentKind.SERIES && entry.seriesId != null) || address != null)
                        PlaylistEntry(Channel(entry.id, entry.title, entry.groupLabel, kind), address, entry.seriesId, entry.providerKey)
                    }
                    SavedLibrary(envelope.source, entries, row.savedAt)
                } finally { envelope.key.fill(0) }
            }
        }
    }

    override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean): SavedLibrary = guarded {
        mutex.withLock {
            require(entries.isNotEmpty() && entries.size <= 10_000)
            val previous = dao.source()
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
                val row = SourceRow(sourceId = id, generation = UUID.randomUUID().toString(), secret = secret, savedAt = System.currentTimeMillis())
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
                    EntryRow(stable.id, index, channel.title, channel.group, channel.kind.name, entry.seriesId, entry.providerKey, encrypted)
                }
                currentCoroutineContext().ensureActive()
                database.withTransaction {
                    dao.clearEntries()
                    if (!refreshing) dao.clearIdentities()
                    added.chunked(250).forEach { dao.insertIdentities(it) }
                    rows.chunked(250).forEach { dao.insertEntries(it) }
                    dao.source(row)
                    currentCoroutineContext().ensureActive()
                }
                SavedLibrary(source, remapped, row.savedAt)
            } finally { key.fill(0); old?.key?.fill(0) }
        }
    }

    override suspend fun clear(): Unit = guarded {
        mutex.withLock {
            database.withTransaction {
                dao.clearEntries(); dao.clearIdentities(); dao.clearSource()
                protector.clear()
            }
        }
    }
    fun close() = database.close()

    private fun sealEnvelope(source: SourceSpec, key: ByteArray, id: String): ByteArray {
        val sourceBytes = source.encode()
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).use { out -> out.write(key); out.write(sourceBytes) } }.toByteArray()
        return try { protector.seal(bytes, "source:$id") } finally { sourceBytes.fill(0); bytes.fill(0) }
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
            RoomLibraryStore(Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, name).build(), protector)
    }
}
