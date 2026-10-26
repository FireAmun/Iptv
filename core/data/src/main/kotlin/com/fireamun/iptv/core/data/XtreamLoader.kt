package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.ImportError
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.CharacterCodingException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class XtreamLoader(private val limits: ParserLimits = ParserLimits(), client: OkHttpClient = OkHttpClient()) {
    private val parser = XtreamJson(limits)
    private val http = client.newBuilder().apply { interceptors().clear(); networkInterceptors().clear() }
        .eventListener(EventListener.NONE).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .cookieJar(CookieJar.NO_COOKIES).cache(null).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .callTimeout(120, TimeUnit.SECONDS).connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    suspend fun load(source: XtreamConnection): List<PlaylistEntry> = withTimeoutOrNull(300_000) {
        val auth = request(source.apiUrl(), parse = parser::auth)
        val entries = mutableListOf<PlaylistEntry>()
        for ((kind, categoryAction, streamAction) in listOf(
            Triple(ContentKind.LIVE, "get_live_categories", "get_live_streams"),
            Triple(ContentKind.MOVIE, "get_vod_categories", "get_vod_streams"),
            Triple(ContentKind.SERIES, "get_series_categories", "get_series"),
        )) {
            val categories = request(source.apiUrl(categoryAction), { emptyMap() }, parser::categories)
            val items = request(source.apiUrl(streamAction), if (kind == ContentKind.LIVE) null else ({ emptyList() })) { input, check -> parser.catalogue(input, check, kind, source, categories, auth.liveExtension, entries.size) }
            entries.addAll(items)
        }
        if (entries.isEmpty()) throw ImportFailure(ImportError.NO_CONTENT)
        entries.toList()
    } ?: throw ImportFailure(ImportError.TIME_LIMIT)

    suspend fun episodes(source: XtreamConnection, seriesId: Long): List<PlaylistEntry> = withTimeoutOrNull(300_000) {
        request(source.apiUrl("get_series_info", seriesId)) { input, check -> parser.episodes(input, check, source) }.also { if (it.isEmpty()) throw ImportFailure(ImportError.NO_CONTENT) }
    } ?: throw ImportFailure(ImportError.TIME_LIMIT)

    private suspend fun <T> request(url: HttpUrl, unsupported: (() -> T)? = null, parse: (InputStream, () -> Unit) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(ImportFailure(ImportError.NETWORK))) }
            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        when {
                            it.code in setOf(401, 403) -> throw ImportFailure(ImportError.AUTH_FAILED)
                            it.code in setOf(404, 405) && unsupported != null -> unsupported()
                            !it.isSuccessful -> throw ImportFailure(ImportError.HTTP_ERROR)
                            it.body.contentLength() > limits.maxBytes -> throw ImportFailure(ImportError.TOO_LARGE)
                            else -> parse(it.body.byteStream()) { if (!continuation.isActive) throw CancellationException() }
                        }
                    }.let { Result.success(it) }
                } catch (failure: ImportFailure) { Result.failure(failure) }
                catch (_: CancellationException) { return }
                catch (_: MalformedJsonException) { Result.failure(ImportFailure(ImportError.SERVER_RESPONSE)) }
                catch (_: CharacterCodingException) { Result.failure(ImportFailure(ImportError.SERVER_RESPONSE)) }
                catch (_: EOFException) { Result.failure(ImportFailure(ImportError.SERVER_RESPONSE)) }
                catch (_: IllegalStateException) { Result.failure(ImportFailure(ImportError.SERVER_RESPONSE)) }
                catch (_: IllegalArgumentException) { Result.failure(ImportFailure(ImportError.SERVER_RESPONSE)) }
                catch (_: IOException) { Result.failure(ImportFailure(ImportError.NETWORK)) }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
}
