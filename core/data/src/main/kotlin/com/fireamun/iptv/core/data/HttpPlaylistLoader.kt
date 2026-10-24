package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.MediaAddress
import com.fireamun.iptv.core.model.ImportError
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class HttpPlaylistLoader(private val parser: M3uParser = M3uParser(), client: OkHttpClient = OkHttpClient()) {
    private val http = client.newBuilder()
        .followRedirects(true).followSslRedirects(false)
        .callTimeout(20, TimeUnit.SECONDS).connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun load(address: MediaAddress): List<PlaylistEntry> = suspendCancellableCoroutine { continuation ->
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
                        parser.parse(it.body.byteStream()) {
                            if (!continuation.isActive) throw CancellationException()
                        }
                    }.let { Result.success(it) }
                } catch (failure: ImportFailure) {
                    Result.failure(failure)
                } catch (_: IOException) {
                    Result.failure(ImportFailure(ImportError.NETWORK))
                } catch (_: CancellationException) {
                    return
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
}
