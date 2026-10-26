package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.MediaAddress
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Never put this object or its generated URLs in logs, saved state, storage or telemetry. */
class XtreamConnection private constructor(private val server: HttpUrl, private val username: String, private val password: String) {
    val address: MediaAddress get() = MediaAddress.parse(server.toString())!!
    override fun toString(): String = "[protected server login]"
    internal fun writeTo(output: java.io.DataOutput) { output.writeUTF(server.toString()); output.writeUTF(username); output.writeUTF(password) }

    internal fun apiUrl(action: String? = null, seriesId: Long? = null): HttpUrl = server.newBuilder()
        .addPathSegment("player_api.php").addQueryParameter("username", username).addQueryParameter("password", password)
        .apply { if (action != null) addQueryParameter("action", action); if (seriesId != null) addQueryParameter("series_id", seriesId.toString()) }.build()

    internal fun playbackAddress(kind: ContentKind, id: Long, extension: String): MediaAddress? {
        if (id <= 0 || !extension.matches(Regex("[A-Za-z0-9]{1,12}"))) return null
        val path = when (kind) { ContentKind.LIVE -> "live"; ContentKind.MOVIE -> "movie"; ContentKind.EPISODE -> "series"; else -> return null }
        return MediaAddress.parse(server.newBuilder().addPathSegment(path).addPathSegment(username).addPathSegment(password).addPathSegment("$id.$extension").build().toString())
    }
    companion object {
        /** Recognize only unambiguous, unfiltered account exports. Never guess from arbitrary URLs. */
        fun fromPlaylistUrl(address: MediaAddress): XtreamConnection? {
            val url = address.reveal().toHttpUrlOrNull() ?: return null
            if (url.pathSegments.lastOrNull() != "get.php" || url.fragment != null || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
            val allowed = setOf("username", "password", "type", "output")
            if (url.queryParameterNames.any { it !in allowed || url.queryParameterValues(it).size != 1 }) return null
            if (url.queryParameter("type") !in setOf("m3u", "m3u_plus") || url.queryParameter("output") !in setOf(null, "ts", "m3u8")) return null
            val username = url.queryParameter("username") ?: return null
            val password = url.queryParameter("password") ?: return null
            val base = url.newBuilder().removePathSegment(url.pathSegments.lastIndex).query(null).build()
            return create(base.toString(), username, password)
        }

        fun create(serverUrl: String, username: String, password: String): XtreamConnection? {
            fun validCredential(value: String) = value.isNotEmpty() && value !in setOf(".", "..") && value.length <= 512 && value.codePoints().noneMatch { Character.isISOControl(it) || it in 0xD800..0xDFFF }
            if (serverUrl.length > 2048 || !validCredential(username) || !validCredential(password)) return null
            val parsed = serverUrl.trim().toHttpUrlOrNull() ?: return null
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || parsed.query != null || parsed.fragment != null) return null
            val base = parsed.newBuilder().encodedPath(parsed.encodedPath.trimEnd('/') + "/").build()
            val connection = XtreamConnection(base, username, password)
            return connection.takeIf { it.apiUrl().toString().length <= 4096 && MediaAddress.parse(base.toString()) != null }
        }
    }
}
