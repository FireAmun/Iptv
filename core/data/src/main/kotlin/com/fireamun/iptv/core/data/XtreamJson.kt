package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.Channel
import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.MediaAddress
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.util.Locale

internal class XtreamJson(private val limits: ParserLimits, private val nanoTime: () -> Long = System::nanoTime) {
    data class Auth(val liveExtension: String)
    private data class Row(var id: Long? = null, var name: String? = null, var category: String? = null, var extension: String? = null, var season: Int? = null, var episode: Int? = null,
        var artwork: MediaAddress? = null, var year: Int? = null, var rating: Double? = null, var rating5: Double? = null, var guideId: String? = null)

    fun auth(input: InputStream, check: () -> Unit): Auth = read(input, check) { reader, checkpoint ->
        var auth: String? = null; var status: String? = null; var expiry: String? = null
        val formats = mutableSetOf<String>()
        var found = false
        reader.beginObject()
        while (reader.hasNext()) {
            checkpoint()
            if (reader.nextName() == "user_info" && reader.peek() == JsonToken.BEGIN_OBJECT) {
                found = true; reader.beginObject()
                while (reader.hasNext()) {
                    checkpoint()
                    when (reader.nextName()) {
                        "auth" -> auth = scalar(reader)
                        "status" -> status = scalar(reader)
                        "exp_date" -> expiry = scalar(reader)
                        "allowed_output_formats" -> if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                            reader.beginArray(); while (reader.hasNext()) { if (formats.size >= 32) throw ImportFailure(ImportError.SERVER_RESPONSE); scalar(reader)?.let { formats.add(it.lowercase(Locale.ROOT)) } }; reader.endArray()
                        } else reader.skipValue()
                        else -> reader.skipValue()
                    }
                }; reader.endObject()
            } else reader.skipValue()
        }; reader.endObject()
        if (!found || auth !in setOf("1", "true")) throw ImportFailure(ImportError.AUTH_FAILED)
        val state = status?.trim()?.lowercase(Locale.ROOT)
        if (state == "expired") throw ImportFailure(ImportError.ACCOUNT_EXPIRED)
        if (!state.isNullOrEmpty() && state != "active") throw ImportFailure(ImportError.ACCOUNT_DISABLED)
        if (!expiry.isNullOrBlank() && expiry != "null") {
            val seconds = expiry?.toLongOrNull() ?: throw ImportFailure(ImportError.SERVER_RESPONSE)
            if (seconds > 0 && seconds <= System.currentTimeMillis() / 1000) throw ImportFailure(ImportError.ACCOUNT_EXPIRED)
        }
        Auth(if ("m3u8" in formats) "m3u8" else "ts")
    }

    fun categories(input: InputStream, check: () -> Unit): Map<String, String> = read(input, check) { reader, checkpoint ->
        val result = linkedMapOf<String, String>()
        var count = 0
        array(reader) {
            checkpoint(); if (++count > limits.maxEntries) throw ImportFailure(ImportError.TOO_MANY_ENTRIES)
            var id: String? = null; var name: String? = null
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw ImportFailure(ImportError.SERVER_RESPONSE)
            reader.beginObject()
            while (reader.hasNext()) when (reader.nextName()) { "category_id" -> id = scalar(reader); "category_name" -> name = scalar(reader); else -> reader.skipValue() }
            reader.endObject()
            if (!id.isNullOrBlank() && !name.isNullOrBlank()) result[id!!] = name!!
        }
        result
    }

    fun catalogue(input: InputStream, check: () -> Unit, kind: ContentKind, source: XtreamConnection, categories: Map<String, String>, liveExtension: String, offset: Int): List<PlaylistEntry> = read(input, check) { reader, checkpoint ->
        val entries = mutableListOf<PlaylistEntry>()
        var rows = 0
        array(reader) {
            checkpoint(); if (++rows + offset > limits.maxEntries) throw ImportFailure(ImportError.TOO_MANY_ENTRIES)
            val row = row(reader)
            val id = row.id
            if (id != null && id > 0) {
                val channel = Channel(offset + entries.size, row.name, categories[row.category], kind, categoryKey = row.category,
                    year = row.year, rating = row.rating ?: row.rating5?.times(2), hasArtwork = row.artwork != null)
                if (kind == ContentKind.SERIES) entries.add(PlaylistEntry(channel, null, id, "${kind.name}:$id", row.artwork))
                else {
                    val ext = if (kind == ContentKind.LIVE) liveExtension else row.extension?.takeIf { it.isNotBlank() } ?: "mp4"
                    val address = source.playbackAddress(kind, id, ext) ?: throw ImportFailure(ImportError.SERVER_RESPONSE)
                    entries.add(PlaylistEntry(channel, address, providerKey = "${kind.name}:$id", artwork = row.artwork, guideId = if (kind == ContentKind.LIVE) row.guideId else null))
                }
            }
        }
        entries
    }

    fun episodes(input: InputStream, check: () -> Unit, source: XtreamConnection): List<PlaylistEntry> = read(input, check) { reader, checkpoint ->
        val entries = mutableListOf<PlaylistEntry>()
        var count = 0
        if (reader.peek() == JsonToken.NULL || reader.peek() == JsonToken.BEGIN_ARRAY) {
            array(reader) { throw ImportFailure(ImportError.SERVER_RESPONSE) }
            return@read entries
        }
        reader.beginObject()
        while (reader.hasNext()) {
            checkpoint()
            if (reader.nextName() != "episodes" || reader.peek() == JsonToken.NULL) { reader.skipValue(); continue }
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                array(reader) { throw ImportFailure(ImportError.SERVER_RESPONSE) }
                continue
            }
            reader.beginObject()
            while (reader.hasNext()) {
                val season = reader.nextName().takeIf { it.length <= 12 }?.toIntOrNull()?.takeIf { it >= 0 }
                array(reader) {
                    checkpoint(); if (++count > limits.maxEntries) throw ImportFailure(ImportError.TOO_MANY_ENTRIES)
                    val row = row(reader)
                    row.id?.takeIf { it > 0 }?.let { id ->
                        val address = source.playbackAddress(ContentKind.EPISODE, id, row.extension?.takeIf { it.isNotBlank() } ?: "mp4") ?: throw ImportFailure(ImportError.SERVER_RESPONSE)
                        entries.add(PlaylistEntry(Channel(entries.size, row.name, null, ContentKind.EPISODE, row.season ?: season, row.episode), address, providerKey = "EPISODE:$id"))
                    }
                }
            }; reader.endObject()
        }; reader.endObject()
        entries.sortedWith(compareBy<PlaylistEntry> { it.channel.season ?: Int.MAX_VALUE }.thenBy { it.channel.episode ?: Int.MAX_VALUE }.thenBy { it.channel.id })
    }

    private fun row(reader: JsonReader): Row {
        val row = Row()
        reader.beginObject()
        while (reader.hasNext()) when (reader.nextName()) {
            "stream_id", "series_id", "id" -> row.id = scalar(reader)?.toLongOrNull()
            "name", "title" -> scalar(reader)?.let { if (row.name == null) row.name = it }
            "category_id" -> row.category = scalar(reader)
            "container_extension" -> row.extension = scalar(reader)
            "season" -> row.season = scalar(reader)?.toIntOrNull()?.takeIf { it >= 0 }
            "episode_num" -> row.episode = scalar(reader)?.toIntOrNull()?.takeIf { it >= 0 }
            "stream_icon", "cover", "movie_image" -> optionalScalar(reader, 4096)?.let { MediaAddress.parse(it) }?.let { if (row.artwork == null) row.artwork = it }
            "year" -> row.year = optionalScalar(reader)?.toIntOrNull()?.takeIf { it in 1800..2200 }
            "rating" -> row.rating = optionalScalar(reader)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..10.0 }
            "epg_channel_id" -> row.guideId = guideId(optionalScalar(reader, 256))
            "rating_5based" -> row.rating5 = optionalScalar(reader)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..5.0 }
            else -> reader.skipValue()
        }
        reader.endObject()
        return row
    }
    private fun optionalScalar(reader: JsonReader, max: Int = 64): String? {
        if (reader.peek() !in setOf(JsonToken.STRING, JsonToken.NUMBER)) { reader.skipValue(); return null }
        return reader.nextString().takeIf { it.length <= max && it.none(Char::isISOControl) && it.isNotBlank() }
    }
    private fun scalar(reader: JsonReader): String? {
        val value = when (reader.peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            else -> { reader.skipValue(); return null }
        }
        if (value.length > limits.maxFieldChars) throw ImportFailure(ImportError.FIELD_TOO_LONG)
        return value.filterNot { it.isISOControl() }.takeIf { it.isNotBlank() }
    }
    private fun array(reader: JsonReader, item: () -> Unit) {
        when (reader.peek()) {
            JsonToken.NULL -> reader.nextNull()
            JsonToken.BEGIN_OBJECT -> { reader.beginObject(); if (reader.hasNext()) throw ImportFailure(ImportError.SERVER_RESPONSE); reader.endObject() }
            else -> { reader.beginArray(); while (reader.hasNext()) item(); reader.endArray() }
        }
    }
    private fun <T> read(input: InputStream, check: () -> Unit, parse: (JsonReader, () -> Unit) -> T): T {
        val start = nanoTime()
        fun checkpoint() { check(); if ((nanoTime() - start) / 1_000_000 > limits.maxMillis) throw ImportFailure(ImportError.TIME_LIMIT) }
        var bytes = 0L
        val bounded = object : InputStream() {
            override fun read(): Int { checkpoint(); return input.read().also { if (it >= 0 && ++bytes > limits.maxBytes) throw ImportFailure(ImportError.TOO_LARGE) } }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int { checkpoint(); return input.read(buffer, offset, length).also { if (it > 0) { bytes += it; if (bytes > limits.maxBytes) throw ImportFailure(ImportError.TOO_LARGE) } } }
            override fun close() = input.close()
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return JsonReader(InputStreamReader(bounded, decoder)).use { reader ->
            reader.strictness = Strictness.STRICT
            reader.nestingLimit = 32
            checkpoint()
            val result = parse(reader, ::checkpoint)
            if (reader.peek() != JsonToken.END_DOCUMENT) throw ImportFailure(ImportError.SERVER_RESPONSE)
            checkpoint()
            result
        }
    }
}
