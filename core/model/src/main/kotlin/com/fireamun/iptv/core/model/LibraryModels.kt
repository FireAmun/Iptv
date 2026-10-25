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

enum class ContentKind { UNKNOWN, LIVE, MOVIE, SERIES, EPISODE }
enum class SourceKind { FILE, URL, XTREAM }
data class Channel(val id: Int, val title: String?, val group: String?, val kind: ContentKind = ContentKind.UNKNOWN, val season: Int? = null, val episode: Int? = null)
enum class ImportError { INVALID_PLAYLIST, HLS_MANIFEST, INVALID_ADDRESS, INVALID_ENCODING, TOO_LARGE, LINE_TOO_LONG, FIELD_TOO_LONG, TOO_MANY_ENTRIES, TIME_LIMIT, EMPTY, READ_FAILED, NETWORK, HTTP_ERROR, NO_FILE_PICKER, INVALID_LOGIN, AUTH_FAILED, ACCOUNT_DISABLED, ACCOUNT_EXPIRED, SERVER_RESPONSE, NO_CONTENT, STORAGE }
data class LibraryState(
    val channels: List<Channel> = emptyList(),
    val importing: Boolean = false,
    val error: ImportError? = null,
    val needsHttpApproval: Boolean = false,
    val selectedId: Int? = null,
    val sourceName: String? = null,
    val collectionTitle: String? = null,
    val canGoBack: Boolean = false,
    val savedAtMillis: Long? = null,
    val saving: Boolean = false,
    val storageBlocked: Boolean = false,
    val hasSavedSource: Boolean = false,
    val restoring: Boolean = false,
)
enum class PlaybackStatus { PREPARING, BUFFERING, PLAYING, PAUSED, ENDED, FAILED }
