package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.Channel
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.MediaAddress
import com.fireamun.iptv.core.model.MAX_CATALOGUE_ENTRIES
import java.io.InputStream
import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException

class PlaylistEntry(val channel: Channel, val address: MediaAddress?, val seriesId: Long? = null, val providerKey: String? = null) {
    override fun toString(): String = "[protected catalogue entry]"
}
class ImportFailure(val code: ImportError) : IOException(code.name)
data class ParserLimits(val maxBytes: Long = 64L * 1024 * 1024, val maxLineChars: Int = 16 * 1024, val maxFieldChars: Int = 512, val maxEntries: Int = MAX_CATALOGUE_ENTRIES, val maxMillis: Long = 120_000)

class M3uParser(private val limits: ParserLimits = ParserLimits(), private val nanoTime: () -> Long = System::nanoTime) {
    fun parse(input: InputStream, checkCancelled: () -> Unit = {}): List<PlaylistEntry> {
        val started = nanoTime()
        fun checkpoint() {
            checkCancelled()
            if ((nanoTime() - started) / 1_000_000 > limits.maxMillis) throw ImportFailure(ImportError.TIME_LIMIT)
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val reader = BufferedReader(InputStreamReader(LimitedInput(input, limits.maxBytes), decoder))
        fun line(): String? {
            checkpoint()
            val result = StringBuilder()
            while (true) {
                val character = reader.read()
                if (character == -1) { checkpoint(); return if (result.isEmpty()) null else result.toString().removeSuffix("\r") }
                if (character == '\n'.code) { checkpoint(); return result.toString().removeSuffix("\r") }
                if (result.length >= limits.maxLineChars) throw ImportFailure(ImportError.LINE_TOO_LONG)
                result.append(character.toChar())
                if (result.length % 1024 == 0) checkpoint()
            }
        }
        val entries = ArrayList<PlaylistEntry>()
        var header = false
        var metadata: Pair<String?, String?>? = null
        try {
            while (true) {
                val raw = line() ?: break
                val text = raw.removePrefix("\uFEFF").trim()
                if (text.isEmpty()) continue
                if (!header) {
                    if (text != "#EXTM3U" && !text.startsWith("#EXTM3U ")) throw ImportFailure(ImportError.INVALID_PLAYLIST)
                    header = true
                    continue
                }
                when {
                    text.startsWith("#EXT-X-") -> throw ImportFailure(ImportError.HLS_MANIFEST)
                    text.startsWith("#EXTINF:") -> {
                        if (metadata != null) throw ImportFailure(ImportError.INVALID_PLAYLIST)
                        metadata = metadata(text)
                    }
                    text.startsWith("#") -> Unit
                    else -> {
                        val address = MediaAddress.parse(text) ?: throw ImportFailure(ImportError.INVALID_ADDRESS)
                        if (entries.size >= limits.maxEntries) throw ImportFailure(ImportError.TOO_MANY_ENTRIES)
                        entries.add(PlaylistEntry(Channel(entries.size, metadata?.first, metadata?.second), address))
                        metadata = null
                    }
                }
            }
        } catch (_: CharacterCodingException) {
            throw ImportFailure(ImportError.INVALID_ENCODING)
        }
        checkpoint()
        if (!header || metadata != null) throw ImportFailure(ImportError.INVALID_PLAYLIST)
        if (entries.isEmpty()) throw ImportFailure(ImportError.EMPTY)
        return entries
    }

    private fun metadata(line: String): Pair<String?, String?> {
        var quote: Char? = null
        var comma = -1
        for (index in 8 until line.length) {
            val character = line[index]
            if (quote != null) {
                if (character == quote) quote = null
            } else if (character == '"' || character == '\'') {
                quote = character
            } else if (character == ',') {
                comma = index
                break
            }
        }
        if (comma == -1) throw ImportFailure(ImportError.INVALID_PLAYLIST)
        val attributes = line.substring(8, comma)
        var index = 0
        while (index < attributes.length && !attributes[index].isWhitespace()) index++
        if (attributes.substring(0, index).toDoubleOrNull()?.isFinite() != true) throw ImportFailure(ImportError.INVALID_PLAYLIST)
        val title = line.substring(comma + 1).trim().ifEmpty { null }
        var group: String? = null
        while (index < attributes.length) {
            while (index < attributes.length && attributes[index].isWhitespace()) index++
            if (index == attributes.length) break
            val keyStart = index
            while (index < attributes.length && !attributes[index].isWhitespace() && attributes[index] != '=') index++
            val key = attributes.substring(keyStart, index)
            while (index < attributes.length && attributes[index].isWhitespace()) index++
            if (index == attributes.length || attributes[index] != '=') throw ImportFailure(ImportError.INVALID_PLAYLIST)
            index++
            while (index < attributes.length && attributes[index].isWhitespace()) index++
            if (index == attributes.length) throw ImportFailure(ImportError.INVALID_PLAYLIST)
            val delimiter = attributes[index].takeIf { it == '"' || it == '\'' }
            if (delimiter != null) index++
            val start = index
            while (index < attributes.length && if (delimiter != null) attributes[index] != delimiter else !attributes[index].isWhitespace()) index++
            val value = attributes.substring(start, index)
            if (delimiter != null) {
                if (index == attributes.length) throw ImportFailure(ImportError.INVALID_PLAYLIST)
                index++
                if (index < attributes.length && !attributes[index].isWhitespace()) throw ImportFailure(ImportError.INVALID_PLAYLIST)
            }
            if (key == "group-title") group = value.trim().ifEmpty { null }
        }
        if ((title?.length ?: 0) > limits.maxFieldChars || (group?.length ?: 0) > limits.maxFieldChars) throw ImportFailure(ImportError.FIELD_TOO_LONG)
        return title to group
    }

    private class LimitedInput(private val source: InputStream, private val maximum: Long) : InputStream() {
        private var consumed = 0L
        private fun count(amount: Int): Int {
            if (amount > 0) consumed += amount
            if (consumed > maximum) throw ImportFailure(ImportError.TOO_LARGE)
            return amount
        }
        override fun read(): Int {
            val result = source.read()
            if (result != -1) count(1)
            return result
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            count(source.read(buffer, offset, minOf(length.toLong(), maximum - consumed + 1).toInt()))
    }
}
