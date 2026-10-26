package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ContentKind
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class LargeCatalogueTest {
    @Test fun defaultM3uImportAcceptsOneHundredThousandEntriesAboveOldByteLimit() {
        val text = buildString {
            append("#EXTM3U\n")
            repeat(100_000) { append("#EXTINF:-1 group-title=\"Synthetic catalogue\",Fixture $it\nhttps://example.invalid/live/$it?token=synthetic\n") }
        }
        assertTrue(text.toByteArray().size > 8 * 1024 * 1024)
        val entries = M3uParser().parse(text.byteInputStream())
        assertEquals(100_000, entries.size)
        assertEquals("Fixture 99999", entries.last().channel.title)
    }

    @Test fun defaultXtreamImportAcceptsLargeCombinedCatalogueAndLargeResponse() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse(body = when (request.url.queryParameter("action")) {
                    null -> """{"user_info":{"auth":1,"status":"Active"}}"""
                    "get_live_streams" -> catalogue(8_000, false)
                    "get_vod_streams" -> catalogue(72_000, false)
                    "get_series" -> catalogue(20_000, true)
                    else -> "[]"
                })
            }
            server.start()
            val result = XtreamLoader().load(XtreamConnection.create(server.url("/").toString(), "fixture-user", "fixture-password")!!)
            assertEquals(100_000, result.size)
            assertEquals(72_000, result.count { it.channel.kind == ContentKind.MOVIE })
            assertEquals(ContentKind.SERIES, result.last().channel.kind)
            assertEquals(99_999, result.last().channel.id)
        }
    }

    private fun catalogue(count: Int, series: Boolean) = buildString {
        append('[')
        repeat(count) {
            if (it > 0) append(',')
            append("{\"${if (series) "series_id" else "stream_id"}\":${it + 1},\"name\":\"Fixture $it\",\"ignored_metadata\":\"${"x".repeat(160)}\"}")
        }
        append(']')
    }
}
