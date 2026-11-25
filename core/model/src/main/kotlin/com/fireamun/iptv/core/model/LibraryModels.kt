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
const val MAX_CATALOGUE_ENTRIES = 300_000
data class SavedSource(val id: String, val name: String?, val kind: SourceKind?, val ordinal: Int) {
    override fun toString() = "[saved playlist]"
}
data class SourceDirectory(val sources: List<SavedSource> = emptyList(), val activeSourceId: String? = null) {
    override fun toString() = "[saved playlist directory]"
}
fun normalizeSourceName(value: String): String? = value.trim().takeIf { it.isNotEmpty() && it.length <= 128 && value.none(Char::isISOControl) }
data class Channel(val id: Int, val title: String?, val group: String?, val kind: ContentKind = ContentKind.UNKNOWN, val season: Int? = null, val episode: Int? = null,
    val categoryKey: String? = null, val year: Int? = null, val rating: Double? = null, val hasArtwork: Boolean = false, val favourite: Boolean = false,
    /** Upcoming guide slots for live channels, attached when a page is read; null when the channel has no guide. */
    val guide: GuideNow? = null)

/** One programme as shown in lists and the player. Times are epoch milliseconds. */
data class GuideSlot(val title: String, val startMs: Long, val endMs: Long) {
    fun progress(nowMs: Long): Float = if (endMs <= startMs) 0f else ((nowMs - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
}
/** A few consecutive slots (oldest first); [at] picks what is on now and next for the current time. */
data class GuideNow(val slots: List<GuideSlot>) {
    fun at(nowMs: Long): Pair<GuideSlot?, GuideSlot?> {
        val index = slots.indexOfFirst { it.endMs > nowMs }
        if (index < 0) return null to null
        val first = slots[index]
        return if (first.startMs <= nowMs) first to slots.getOrNull(index + 1) else null to first
    }
}
enum class ImportError { INVALID_PLAYLIST, HLS_MANIFEST, INVALID_ADDRESS, INVALID_ENCODING, TOO_LARGE, LINE_TOO_LONG, FIELD_TOO_LONG, TOO_MANY_ENTRIES, TIME_LIMIT, EMPTY, READ_FAILED, NETWORK, HTTP_ERROR, NO_FILE_PICKER, INVALID_LOGIN, AUTH_FAILED, ACCOUNT_DISABLED, ACCOUNT_EXPIRED, SERVER_RESPONSE, NO_CONTENT, STORAGE, INVALID_NAME }
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
    val sources: List<SavedSource> = emptyList(),
    val activeSourceId: String? = null,
    val switchingSource: Boolean = false,
    val entryCount: Int = channels.size,
    val catalogueGeneration: String? = null,
    val tab: LibraryTab = LibraryTab.SETTINGS,
    val query: String = "",
    val category: String? = null,
    val categories: List<String> = emptyList(),
    val selectedTitle: String? = null,
)
enum class PlaybackStatus { PREPARING, BUFFERING, PLAYING, PAUSED, ENDED, FAILED }
