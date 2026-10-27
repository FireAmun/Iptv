package com.fireamun.iptv.prototype

import android.content.Context
import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import coil3.toUri
import com.fireamun.iptv.core.data.LibraryPersistence
import com.fireamun.iptv.core.model.LibraryArtwork
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/** URL resolution stays behind the persistence boundary, including Coil request/cache identities. */
internal fun protectedImageLoader(context: Context, store: LibraryPersistence): ImageLoader {
    val client = OkHttpClient.Builder().followSslRedirects(false).cache(null)
        .callTimeout(20, TimeUnit.SECONDS).connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    val network = OkHttpNetworkFetcherFactory(client)
    return ImageLoader.Builder(context).logger(null).diskCache(null).components {
        add(Keyer<LibraryArtwork> { data, _ -> "art:${data.sourceId}:${data.generation}:${data.itemId}" })
        add(object : Fetcher.Factory<LibraryArtwork> {
            override fun create(data: LibraryArtwork, options: Options, imageLoader: ImageLoader): Fetcher = Fetcher {
                try {
                    val address = store.artwork(data) ?: return@Fetcher null
                    network.create(address.reveal().toUri(), options, imageLoader)?.fetch()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { throw IOException("Artwork unavailable") }
            }
        })
    }.build()
}
