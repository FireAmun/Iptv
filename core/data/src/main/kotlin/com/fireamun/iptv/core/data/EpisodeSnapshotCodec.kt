package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.MediaAddress
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object EpisodeSnapshotCodec {
    const val MAX_BYTES = 16 * 1024

    fun encode(snapshot: EpisodeSnapshot): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeByte(1)
            output.writeUTF(snapshot.providerKey)
            output.writeBoolean(snapshot.title != null)
            snapshot.title?.let(output::writeUTF)
            output.writeInt(snapshot.season ?: -1)
            output.writeInt(snapshot.episode ?: -1)
            output.writeUTF(snapshot.address.reveal())
        }
        return bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): EpisodeSnapshot {
        require(bytes.size in 1..MAX_BYTES)
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readUnsignedByte() == 1)
                val key = input.readUTF()
                val title = if (input.readBoolean()) input.readUTF() else null
                val season = input.readInt().let { if (it == -1) null else it }
                val episode = input.readInt().let { if (it == -1) null else it }
                val address = MediaAddress.parse(input.readUTF()) ?: throw IllegalArgumentException("Invalid snapshot address")
                require(input.available() == 0)
                EpisodeSnapshot(key, title, season, episode, address)
            }
        } catch (_: java.io.IOException) { throw IllegalArgumentException("Invalid snapshot") }
    }
}
