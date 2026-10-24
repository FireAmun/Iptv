package com.fireamun.iptv.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.util.Locale

@OptIn(UnstableApi::class)
class PlaybackDataSourceFactory(private val allowCleartext: Boolean) : DataSource.Factory {
    private val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false).setConnectTimeoutMs(8000).setReadTimeoutMs(8000)
    override fun createDataSource(): DataSource {
        val delegate = http.createDataSource()
        return object : DataSource by delegate {
            override fun open(dataSpec: DataSpec): Long {
                val scheme = dataSpec.uri.scheme?.lowercase(Locale.ROOT)
                if (scheme != "https" && (scheme != "http" || !allowCleartext)) throw IOException("Playback transport is not approved")
                return delegate.open(dataSpec)
            }
        }
    }
}
