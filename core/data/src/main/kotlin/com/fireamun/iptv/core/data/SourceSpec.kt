package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.MediaAddress
import com.fireamun.iptv.core.model.SourceKind
import java.io.*

/** Protected connection material. Encoding is plaintext for immediate encryption only. */
class SourceSpec private constructor(val kind: SourceKind, val name: String?, val url: MediaAddress?, val xtream: XtreamConnection?) {
    override fun toString() = "[protected source]"
    fun encode(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeByte(1); output.writeUTF(kind.name)
            when (kind) {
                SourceKind.FILE -> Unit
                SourceKind.URL -> output.writeUTF(url!!.reveal())
                SourceKind.XTREAM -> { output.writeUTF(name!!); xtream!!.writeTo(output) }
            }
        }
    }.toByteArray()
    companion object {
        fun file() = SourceSpec(SourceKind.FILE, null, null, null)
        fun url(address: MediaAddress) = SourceSpec(SourceKind.URL, null, address, null)
        fun xtream(name: String, connection: XtreamConnection): SourceSpec {
            if (name.isBlank() || name.length > 128 || name.any { it.isISOControl() }) throw ImportFailure(ImportError.INVALID_LOGIN)
            return SourceSpec(SourceKind.XTREAM, name, null, connection)
        }
        fun decode(bytes: ByteArray): SourceSpec {
            try {
                require(bytes.size <= 16_384)
                return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                    require(input.readUnsignedByte() == 1)
                    val value = when (SourceKind.valueOf(input.readUTF())) {
                        SourceKind.FILE -> file()
                        SourceKind.URL -> url(MediaAddress.parse(input.readUTF()) ?: error("Invalid protected address"))
                        SourceKind.XTREAM -> xtream(input.readUTF(), XtreamConnection.create(input.readUTF(), input.readUTF(), input.readUTF()) ?: error("Invalid protected connection"))
                    }
                    require(input.available() == 0)
                    value
                }
            } catch (_: IOException) { throw ImportFailure(ImportError.STORAGE) }
            catch (_: IllegalArgumentException) { throw ImportFailure(ImportError.STORAGE) }
            catch (_: IllegalStateException) { throw ImportFailure(ImportError.STORAGE) }
        }
    }
}
