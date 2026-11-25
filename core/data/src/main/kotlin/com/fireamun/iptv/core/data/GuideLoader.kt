package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.MediaAddress
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** Guide refresh record for one saved source; never carries the guide link itself. */
class GuideState(val sourceId: String, val hasManualLink: Boolean, val hasPlaylistLink: Boolean, val fetchedAtMs: Long?, val attemptedAtMs: Long?,
    val programmeCount: Int, val error: ImportError?) {
    override fun toString() = "[guide state]"
}

/** Stored programme guide. Links are sealed like source logins; schedules are provider text and stored plainly. */
interface GuidePersistence {
    suspend fun guideState(sourceId: String): GuideState?
    /** Manual link first, then the playlist header link, then the Xtream account guide. */
    suspend fun guideLink(sourceId: String): MediaAddress?
    suspend fun setManualGuideLink(sourceId: String, link: MediaAddress?)
    suspend fun setPlaylistGuideLink(sourceId: String, link: MediaAddress?)
    /** Lookup keys of every entry in the source, so a guide keeps only channels the playlist can show. */
    suspend fun guideKeys(sourceId: String): Set<String>
    suspend fun replaceGuide(sourceId: String, guide: ParsedGuide, fetchedAtMs: Long)
    suspend fun guideFailed(sourceId: String, error: ImportError, attemptedAtMs: Long)
    /** Programmes for one catalogue item overlapping [fromMs, untilMs), oldest first. */
    suspend fun schedule(sourceId: String, itemId: Int, fromMs: Long, untilMs: Long, limit: Int = 200): List<GuideProgramme>
}

class GuideLoader(client: OkHttpClient = OkHttpClient(), private val parser: XmltvParser = XmltvParser()) {
    private val http = client.newBuilder()
        .followRedirects(true).followSslRedirects(false)
        .callTimeout(5, TimeUnit.MINUTES).connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun load(address: MediaAddress, nowMs: Long, wanted: Set<String>?): ParsedGuide = suspendCancellableCoroutine { continuation ->
        val request = try { Request.Builder().url(address.reveal()).build() }
        catch (_: IllegalArgumentException) {
            continuation.resumeWith(Result.failure(ImportFailure(ImportError.INVALID_ADDRESS)))
            return@suspendCancellableCoroutine
        }
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(ImportFailure(ImportError.NETWORK)))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        if (!it.isSuccessful) throw ImportFailure(ImportError.HTTP_ERROR)
                        parser.parse(it.body.byteStream(), nowMs, wanted) { if (!continuation.isActive) throw CancellationException() }
                    }.let { Result.success(it) }
                } catch (failure: ImportFailure) { Result.failure(failure) }
                catch (_: IOException) { Result.failure(ImportFailure(ImportError.NETWORK)) }
                catch (_: CancellationException) { return }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
}
