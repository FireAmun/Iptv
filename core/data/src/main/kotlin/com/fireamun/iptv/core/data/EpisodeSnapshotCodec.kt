package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.MediaAddress
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream

object EpisodeSnapshotCodec {
    const val MAX_BYTES = 16 * 1024

    private class BoundedBuffer : OutputStream() {
        private val bytes = ByteArray(MAX_BYTES)
        private var size = 0

        override fun write(value: Int) {
            require(size < MAX_BYTES)
            bytes[size++] = value.toByte()
        }

        override fun write(values: ByteArray, offset: Int, length: Int) {
            require(offset >= 0 && length >= 0 && offset <= values.size - length)
            require(length <= MAX_BYTES - size)
            values.copyInto(bytes, size, offset, offset + length)
            size += length
        }

        fun copy() = bytes.copyOf(size)
        fun clear() = bytes.fill(0)
    }

    fun encode(snapshot: EpisodeSnapshot): ByteArray {
        val bytes = BoundedBuffer()
        return try {
            DataOutputStream(bytes).use { output ->
                output.writeByte(1)
                output.writeUTF(snapshot.providerKey)
                output.writeBoolean(snapshot.title != null)
                snapshot.title?.let(output::writeUTF)
                output.writeInt(snapshot.season ?: -1)
                output.writeInt(snapshot.episode ?: -1)
                output.writeUTF(snapshot.address.reveal())
            }
            bytes.copy()
        } finally {
            bytes.clear()
        }
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
