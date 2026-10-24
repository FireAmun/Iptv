package com.fireamun.iptv.prototype

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fireamun.iptv.core.playback.PlaybackDataSourceFactory
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackTransportTest {
    @Test fun unapprovedHttpSubresourcesAreBlocked() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(body = "abc"))
            server.start()
            val source = PlaybackDataSourceFactory(allowCleartext = false).createDataSource()
            try {
                assertThrows(IOException::class.java) { source.open(DataSpec.Builder().setUri(server.url("/segment").toString()).build()) }
            } finally { source.close() }
        }
    }
    @Test fun approvedHttpOperationCanReadItsMedia() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(body = "abc"))
            server.start()
            val source = PlaybackDataSourceFactory(allowCleartext = true).createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(server.url("/segment").toString()).build())
                val buffer = ByteArray(3)
                assertEquals(3, source.read(buffer, 0, 3))
                assertEquals("abc", buffer.toString(Charsets.UTF_8))
            } finally { source.close() }
        }
    }
}
