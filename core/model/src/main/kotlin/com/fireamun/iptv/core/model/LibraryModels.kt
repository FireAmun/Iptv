package com.fireamun.iptv.core.model

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

class MediaAddress private constructor(private val raw: String) {
    val isCleartext: Boolean get() = raw.startsWith("http:", ignoreCase = true)
    fun reveal(): String = raw
    override fun toString(): String = "[protected media address]"
    companion object {
        fun parse(value: String): MediaAddress? {
            if (value.length > 4096) return null
            val text = value.trim()
            return try {
                val uri = URI(text)
                if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.host.isNullOrEmpty() || uri.port !in -1..65535) null
                else MediaAddress(text)
            } catch (_: URISyntaxException) {
                null
            }
        }
    }
}

data class Channel(val id: Int, val title: String?, val group: String?)
enum class ImportError { INVALID_PLAYLIST, HLS_MANIFEST, INVALID_ADDRESS, INVALID_ENCODING, TOO_LARGE, LINE_TOO_LONG, FIELD_TOO_LONG, TOO_MANY_ENTRIES, TIME_LIMIT, EMPTY, READ_FAILED, NETWORK, HTTP_ERROR, NO_FILE_PICKER }
data class LibraryState(
    val channels: List<Channel> = emptyList(),
    val importing: Boolean = false,
    val error: ImportError? = null,
    val needsHttpApproval: Boolean = false,
    val selectedId: Int? = null,
)
enum class PlaybackStatus { PREPARING, BUFFERING, PLAYING, PAUSED, ENDED, FAILED }
