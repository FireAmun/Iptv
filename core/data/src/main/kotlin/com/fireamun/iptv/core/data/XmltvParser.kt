package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.normalizeSearch
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream

/** One guide programme; provider schedule text is not secret. Times are epoch milliseconds. */
data class GuideProgramme(val startMs: Long, val endMs: Long, val title: String, val description: String? = null)

/**
 * Parsed guide. [aliases] maps lookup keys (see [GuideKeys]) to XMLTV channel ids; [programmes] maps those ids to
 * their programmes inside the kept window, sorted by start.
 */
class ParsedGuide(val aliases: Map<String, String>, val programmes: Map<String, List<GuideProgramme>>) {
    val programmeCount get() = programmes.values.sumOf { it.size }
    override fun toString() = "[parsed guide]"
}

data class GuideLimits(val maxBytes: Long = 512L * 1024 * 1024, val maxChannels: Int = 50_000, val maxProgrammes: Int = 300_000,
    val maxMillis: Long = 300_000, val keepBeforeMs: Long = 2 * HOUR_MS, val keepAfterMs: Long = 48 * HOUR_MS) {
    companion object { const val HOUR_MS = 3_600_000L }
}

/** Guide lookup keys: an entry's provider channel id, or its normalized title when the provider gave no id. */
object GuideKeys {
    private val quality = setOf("hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h264", "h265", "hq", "backup", "raw")
    fun forId(id: String): String = "id:" + id.trim().lowercase()
    private val countryPrefix = Regex("^[A-Za-z]{2,3}\\s*[:|]\\s*")
    private val word = Regex("[\\p{L}\\p{N}]+")
    fun forTitle(title: String): String? {
        val withoutPrefix = title.trim().replace(countryPrefix, "")
        val words = word.findAll(normalizeSearch(withoutPrefix)).map { it.value }.filter { it !in quality }.toList()
        return words.takeIf { it.isNotEmpty() }?.joinToString(" ", prefix = "title:")?.take(256)
    }
    /** The key stored with a catalogue entry. */
    fun forEntry(guideId: String?, title: String?): String? = guideId?.let(::forId) ?: title?.let(::forTitle)
}

/**
 * Streaming, dependency-free XMLTV reader. It understands only what the guide needs (channel ids and display names,
 * programme channel/start/stop/title/desc), never resolves external entities and keeps only the requested window.
 * Gzip input is detected by its magic bytes.
 */
class XmltvParser(private val limits: GuideLimits = GuideLimits(), private val nanoTime: () -> Long = System::nanoTime) {
    fun parse(input: InputStream, nowMs: Long, wanted: Set<String>? = null, checkCancelled: () -> Unit = {}): ParsedGuide {
        val started = nanoTime()
        fun checkpoint() {
            checkCancelled()
            if ((nanoTime() - started) / 1_000_000 > limits.maxMillis) throw ImportFailure(ImportError.TIME_LIMIT)
        }
        val reader = reader(input)
        val aliases = HashMap<String, String>()
        val keptChannels = HashSet<String>()
        val programmes = HashMap<String, ArrayList<GuideProgramme>>()
        var programmeCount = 0
        var channelCount = 0
        val from = nowMs - limits.keepBeforeMs
        val until = nowMs + limits.keepAfterMs

        // Element state
        var channelId: String? = null
        val names = ArrayList<String>()
        var programmeChannel: String? = null
        var programmeStart: Long? = null
        var programmeStop: Long? = null
        var title: String? = null
        var description: String? = null
        var capture: StringBuilder? = null
        var captureTarget = 0 // 1 display-name, 2 title, 3 desc

        fun wantedChannel(id: String, displayNames: List<String>): Boolean {
            if (wanted == null) return true
            if (GuideKeys.forId(id) in wanted) return true
            return displayNames.any { name -> GuideKeys.forTitle(name)?.let { it in wanted } == true }
        }

        val scanner = Scanner(reader, ::checkpoint)
        while (true) {
            val token = scanner.next() ?: break
            when (token) {
                is Token.Start -> when (token.name) {
                    "channel" -> if (channelId == null) { channelId = token.attributes["id"]?.trim()?.takeIf { it.isNotEmpty() && it.length <= 256 }; names.clear() }
                    "display-name" -> if (channelId != null && names.size < 8) { capture = StringBuilder(); captureTarget = 1 }
                    "programme" -> {
                        programmeChannel = token.attributes["channel"]?.trim()?.takeIf { it.isNotEmpty() && it.length <= 256 }
                        programmeStart = token.attributes["start"]?.let(::parseTime)
                        programmeStop = token.attributes["stop"]?.let(::parseTime)
                        title = null; description = null
                    }
                    "title" -> if (programmeChannel != null && title == null) { capture = StringBuilder(); captureTarget = 2 }
                    "desc" -> if (programmeChannel != null && description == null) { capture = StringBuilder(); captureTarget = 3 }
                }
                is Token.Text -> capture?.let { if (it.length < MAX_TEXT) it.append(token.text, 0, minOf(token.text.length, MAX_TEXT - it.length)) }
                is Token.End -> when (token.name) {
                    "display-name" -> if (captureTarget == 1) { capture?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(names::add); capture = null; captureTarget = 0 }
                    "title" -> if (captureTarget == 2) { title = capture?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.take(256); capture = null; captureTarget = 0 }
                    "desc" -> if (captureTarget == 3) { description = capture?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.take(1024); capture = null; captureTarget = 0 }
                    "channel" -> {
                        val id = channelId
                        if (id != null && wantedChannel(id, names)) {
                            if (++channelCount > limits.maxChannels) throw ImportFailure(ImportError.TOO_MANY_ENTRIES)
                            keptChannels += id
                            aliases.putIfAbsent(GuideKeys.forId(id), id)
                            names.forEach { name -> GuideKeys.forTitle(name)?.let { aliases.putIfAbsent(it, id) } }
                        }
                        channelId = null; names.clear()
                    }
                    "programme" -> {
                        val id = programmeChannel
                        val start = programmeStart
                        val text = title
                        // A guide may omit <channel> elements; its programme ids still match entries by id.
                        val known = id != null && (id in keptChannels || (wanted == null || GuideKeys.forId(id) in wanted))
                        if (known && start != null && text != null && start < until && (programmeStop ?: Long.MAX_VALUE) > from) {
                            if (id !in keptChannels) {
                                if (++channelCount > limits.maxChannels) throw ImportFailure(ImportError.TOO_MANY_ENTRIES)
                                keptChannels += id!!; aliases.putIfAbsent(GuideKeys.forId(id), id)
                            }
                            if (++programmeCount > limits.maxProgrammes) throw ImportFailure(ImportError.TOO_MANY_ENTRIES)
                            programmes.getOrPut(id!!) { ArrayList() }.add(GuideProgramme(start, programmeStop ?: UNKNOWN_END, text, description))
                        }
                        programmeChannel = null; programmeStart = null; programmeStop = null; title = null; description = null
                    }
                }
            }
        }
        checkpoint()
        if (!scanner.sawRoot) throw ImportFailure(ImportError.INVALID_PLAYLIST)
        val result = HashMap<String, List<GuideProgramme>>(programmes.size)
        for ((id, list) in programmes) {
            list.sortBy { it.startMs }
            val fixed = ArrayList<GuideProgramme>(list.size)
            for (index in list.indices) {
                val item = list[index]
                if (fixed.isNotEmpty() && fixed.last().startMs == item.startMs) continue
                // A missing or bad stop ends at the next start, or after one hour for the last programme.
                val next = list.getOrNull(index + 1)?.startMs
                val end = when {
                    item.endMs != UNKNOWN_END && item.endMs > item.startMs -> item.endMs
                    next != null && next > item.startMs -> next
                    else -> item.startMs + GuideLimits.HOUR_MS
                }
                if (end > from) fixed += item.copy(endMs = minOf(end, item.startMs + 24 * GuideLimits.HOUR_MS))
            }
            if (fixed.isNotEmpty()) result[id] = fixed
        }
        return ParsedGuide(aliases.filterValues { it in result }, result)
    }

    private fun reader(input: InputStream): Reader {
        val limited = LimitedStream(input, limits.maxBytes)
        var stream: InputStream = BufferedInputStream(limited, 64 * 1024)
        stream.mark(2)
        val first = stream.read(); val second = stream.read()
        stream.reset()
        if (first == 0x1f && second == 0x8b) stream = BufferedInputStream(LimitedStream(GZIPInputStream(stream, 64 * 1024), limits.maxBytes), 64 * 1024)
        stream.mark(512)
        val head = ByteArray(512)
        var read = 0
        while (read < head.size) { val n = stream.read(head, read, head.size - read); if (n <= 0) break; read += n }
        stream.reset()
        val prolog = String(head, 0, read, Charsets.ISO_8859_1)
        val declared = Regex("""<\?xml[^>]*encoding\s*=\s*["']([A-Za-z0-9._-]{1,40})["']""").find(prolog)?.groupValues?.get(1)
        val charset = declared?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        // One bad byte in a provider guide should not drop the whole schedule.
        val decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
        return InputStreamReader(stream, decoder)
    }

    private sealed interface Token {
        class Start(val name: String, val attributes: Map<String, String>) : Token
        class End(val name: String) : Token
        class Text(val text: String) : Token
    }

    /** Minimal XML tokenizer; markup declarations, comments and processing instructions are skipped, never expanded. */
    private class Scanner(private val reader: Reader, private val tick: () -> Unit) {
        private var pending: Int = -2
        private var queuedEnd: String? = null
        var sawRoot = false
        private val buffer = CharArray(16 * 1024)
        private var position = 0
        private var limit = 0
        private fun read(): Int {
            if (pending != -2) { val c = pending; pending = -2; return c }
            if (position == limit) {
                // Cancellation and the time limit are checked once per 16K characters.
                tick()
                limit = reader.read(buffer, 0, buffer.size)
                position = 0
                if (limit <= 0) { limit = 0; return -1 }
            }
            return buffer[position++].code
        }
        private fun unread(c: Int) { pending = c }

        fun next(): Token? {
            queuedEnd?.let { queuedEnd = null; return Token.End(it) }
            while (true) {
                val c = read()
                if (c == -1) return null
                if (c != '<'.code) {
                    val text = StringBuilder()
                    var ch = c
                    while (ch != -1 && ch != '<'.code) {
                        if (ch == '&'.code) entity(text) else if (text.length < MAX_TEXT * 2) text.append(ch.toChar())
                        ch = read()
                    }
                    if (ch != -1) unread(ch)
                    return Token.Text(text.toString())
                }
                val kind = read()
                when (kind) {
                    '?'.code -> skipUntil("?>")
                    '!'.code -> {
                        val a = read(); val b = read()
                        if (a == '-'.code && b == '-'.code) skipUntil("-->")
                        else if (a == '['.code && b == 'C'.code) {
                            // <![CDATA[ ... ]]>
                            repeat(5) { read() }
                            val text = StringBuilder()
                            var tail = 0
                            while (true) {
                                val ch = read(); if (ch == -1) return null
                                if (text.length < MAX_TEXT * 2) text.append(ch.toChar())
                                tail = if (ch == ']'.code) minOf(tail + 1, 2) else if (ch == '>'.code && tail == 2) 3 else 0
                                if (tail == 3) { text.setLength(maxOf(0, text.length - 3)); return Token.Text(text.toString()) }
                            }
                        } else skipDeclaration()
                    }
                    '/'.code -> {
                        val name = name(read())
                        var ch = read(); while (ch != -1 && ch != '>'.code) ch = read()
                        return Token.End(name)
                    }
                    -1 -> return null
                    else -> {
                        val name = name(kind)
                        sawRoot = sawRoot || name == "tv"
                        val attributes = HashMap<String, String>(4)
                        var ch = read()
                        while (true) {
                            while (ch != -1 && ch.toChar().isWhitespace()) ch = read()
                            if (ch == -1) return null
                            if (ch == '>'.code) return Token.Start(name, attributes)
                            if (ch == '/'.code) { read(); queuedEnd = name; return Token.Start(name, attributes) }
                            val key = StringBuilder()
                            while (ch != -1 && ch != '='.code && ch != '>'.code && ch != '/'.code && !ch.toChar().isWhitespace()) { if (key.length < 64) key.append(ch.toChar()); ch = read() }
                            while (ch != -1 && ch.toChar().isWhitespace()) ch = read()
                            if (ch != '='.code) continue
                            ch = read()
                            while (ch != -1 && ch.toChar().isWhitespace()) ch = read()
                            if (ch != '"'.code && ch != '\''.code) continue
                            val quote = ch
                            val value = StringBuilder()
                            ch = read()
                            while (ch != -1 && ch != quote) {
                                if (ch == '&'.code) entity(value) else if (value.length < 1024) value.append(ch.toChar())
                                ch = read()
                            }
                            if (attributes.size < 16) attributes[key.toString()] = value.toString()
                            ch = read()
                        }
                    }
                }
            }
        }

        private fun name(first: Int): String {
            val builder = StringBuilder()
            var ch = first
            while (ch != -1 && ch != '>'.code && ch != '/'.code && !ch.toChar().isWhitespace()) { if (builder.length < 64) builder.append(ch.toChar()); ch = read() }
            if (ch != -1) unread(ch)
            return builder.toString()
        }

        private fun entity(out: StringBuilder) {
            val name = StringBuilder()
            var ch = read()
            while (ch != -1 && ch != ';'.code && name.length < 10 && (ch.toChar().isLetterOrDigit() || ch == '#'.code)) { name.append(ch.toChar()); ch = read() }
            if (ch != ';'.code) { out.append('&').append(name); if (ch != -1) unread(ch); return }
            val value = when (val text = name.toString()) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
                else -> when {
                    text.startsWith("#x") || text.startsWith("#X") -> text.substring(2).toIntOrNull(16)
                    text.startsWith("#") -> text.substring(1).toIntOrNull()
                    else -> null
                }?.takeIf { Character.isValidCodePoint(it) && !Character.isISOControl(it) }?.let { String(Character.toChars(it)) } ?: "&$text;"
            }
            out.append(value)
        }

        private fun skipUntil(end: String) {
            var matched = 0
            while (matched < end.length) {
                val ch = read(); if (ch == -1) return
                matched = if (ch == end[matched].code) matched + 1 else if (ch == end[0].code) 1 else 0
            }
        }

        /** Skip <!DOCTYPE ...> including an internal subset; nothing in it is ever expanded. */
        private fun skipDeclaration() {
            var depth = 0
            while (true) {
                val ch = read(); if (ch == -1) return
                if (ch == '['.code) depth++ else if (ch == ']'.code) depth-- else if (ch == '>'.code && depth <= 0) return
            }
        }
    }

    private class LimitedStream(private val source: InputStream, private val maximum: Long) : InputStream() {
        private var consumed = 0L
        private fun count(amount: Int): Int {
            if (amount > 0) consumed += amount
            if (consumed > maximum) throw ImportFailure(ImportError.TOO_LARGE)
            return amount
        }
        override fun read(): Int = source.read().also { if (it != -1) count(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = count(source.read(buffer, offset, length))
        override fun close() = source.close()
    }

    companion object {
        private const val MAX_TEXT = 4096
        private const val UNKNOWN_END = Long.MIN_VALUE

        /** XMLTV time: YYYYMMDDhh[mm[ss]] with an optional "+hhmm"/"-hhmm" offset; no offset means UTC. */
        fun parseTime(value: String): Long? {
            val text = value.trim()
            val digits = text.takeWhile(Char::isDigit)
            if (digits.length !in setOf(10, 12, 14)) return null
            val year = digits.substring(0, 4).toInt(); val month = digits.substring(4, 6).toInt(); val day = digits.substring(6, 8).toInt()
            val hour = digits.substring(8, 10).toInt()
            val minute = if (digits.length >= 12) digits.substring(10, 12).toInt() else 0
            val second = if (digits.length >= 14) digits.substring(12, 14).toInt() else 0
            if (year !in 1970..2200 || month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null
            val zone = text.substring(digits.length).trim()
            val offsetMinutes = when {
                zone.isEmpty() || zone.equals("UTC", true) || zone.equals("GMT", true) || zone == "Z" -> 0
                zone.length >= 5 && (zone[0] == '+' || zone[0] == '-') && zone.substring(1, 5).all(Char::isDigit) -> {
                    val minutes = zone.substring(1, 3).toInt() * 60 + zone.substring(3, 5).toInt()
                    if (minutes > 18 * 60) return null
                    if (zone[0] == '-') -minutes else minutes
                }
                else -> return null
            }
            return try {
                val date = java.time.LocalDate.of(year, month, day)
                val epochSecond = date.toEpochDay() * 86_400 + hour * 3_600L + minute * 60L + minOf(second, 59) - offsetMinutes * 60L
                epochSecond * 1000
            } catch (_: java.time.DateTimeException) { null }
        }
    }
}
