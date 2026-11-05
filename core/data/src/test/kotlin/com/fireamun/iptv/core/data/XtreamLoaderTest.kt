package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.ImportError
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.buffer

class XtreamLoaderTest {
    @Test fun closedReusedConnectionIsRecoveredBeforeNextCatalogueResponse() = runBlocking {
        MockWebServer().use { server ->
            val normal = fixtures()
            val closed = java.util.concurrent.atomic.AtomicBoolean()
            val attempts = java.util.concurrent.CopyOnWriteArrayList<RecordedRequest>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.url.queryParameter("action") == "get_series_categories") {
                        attempts.add(request)
                        if (closed.compareAndSet(false, true)) {
                            return MockResponse.Builder().onResponseStart(mockwebserver3.SocketEffect.CloseSocket()).build()
                        }
                    }
                    return normal.dispatch(request)
                }
            }
            server.start()
            val result = XtreamLoader().load(XtreamConnection.create(server.url("/").toString(), "fixture-user", "fixture-password")!!)
            assertEquals(3, result.size)
            assertEquals(2, attempts.size)
            assertTrue("Failure must occur on a previously working pooled connection", attempts[0].exchangeIndex > 0)
            assertNotEquals(attempts[0].connectionIndex, attempts[1].connectionIndex)
            assertTrue(attempts.all { it.url.encodedPath == "/player_api.php" && it.url.port == server.port })
        }
    }

    @Test fun credentialsAreEncodedAndRedactedAndServerInputIsStrict() {
        val source = XtreamConnection.create("https://example.invalid/prefix/", "u+/", "p?&")!!
        val api = source.apiUrl("get_live_streams")
        assertEquals("/prefix/player_api.php", api.encodedPath)
        assertEquals("u+/", api.queryParameter("username"))
        assertEquals("p?&", api.queryParameter("password"))
        assertFalse(source.toString().contains("example.invalid"))
        assertFalse(source.toString().contains("p?&"))
        assertEquals("/prefix/movie/u+%2F/p%3F&/7.mp4", source.playbackAddress(ContentKind.MOVIE, 7, "mp4")!!.reveal().toHttpUrl().encodedPath)
        for (url in listOf("file:///data/source", "https://user:pass@example.invalid", "https://example.invalid?password=secret", "https://example.invalid/#x")) assertNull(XtreamConnection.create(url, "u", "p"))
        assertNull(XtreamConnection.create("https://example.invalid", "", "p"))
        for (dot in listOf(".", "..")) {
            assertNull(XtreamConnection.create("https://example.invalid", dot, "p"))
            assertNull(XtreamConnection.create("https://example.invalid", "u", dot))
        }
    }

    @Test fun authenticatesAndLoadsAllKindsAndSeriesEpisodesWithoutM3u() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = fixtures()
            server.start()
            val connection = XtreamConnection.create(server.url("/").toString(), "synthetic-user", "synthetic-password")!!
            val loader = XtreamLoader()
            val entries = loader.load(connection)
            assertEquals(listOf(ContentKind.LIVE, ContentKind.MOVIE, ContentKind.SERIES), entries.map { it.channel.kind })
            assertEquals(listOf("Live group", "Film group", "Series group"), entries.map { it.channel.group })
            assertTrue(entries[0].address!!.reveal().endsWith("/live/synthetic-user/synthetic-password/10.m3u8"))
            assertNull(entries[2].address)
            val episodes = loader.episodes(connection, entries[2].seriesId!!)
            assertEquals(ContentKind.EPISODE, episodes.single().channel.kind)
            assertEquals(1, episodes.single().channel.season)
            assertEquals(2, episodes.single().channel.episode)
            assertEquals("EPISODE:31", episodes.single().providerKey)
            assertTrue(episodes.single().address!!.reveal().endsWith("/series/synthetic-user/synthetic-password/31.mp4"))
            repeat(8) { assertEquals("/player_api.php", server.takeRequest().url.encodedPath) }
        }
    }

    @Test fun failedAuthAndExpiredOrDisabledAccountsNeverFetchCatalogue() = runBlocking {
        for ((body, expected) in listOf(
            """{"user_info":{"auth":0}}""" to ImportError.AUTH_FAILED,
            """{"user_info":{"auth":"1","status":"Expired"}}""" to ImportError.ACCOUNT_EXPIRED,
            """{"user_info":{"auth":1,"status":"Banned"}}""" to ImportError.ACCOUNT_DISABLED,
            """{"user_info":{"auth":1,"status":"Active","exp_date":"1"}}""" to ImportError.ACCOUNT_EXPIRED,
        )) MockWebServer().use { server ->
            server.enqueue(MockResponse(body = body)); server.start()
            try { XtreamLoader().load(XtreamConnection.create(server.url("/").toString(), "u", "p")!!); fail("Expected fixed failure") }
            catch (failure: ImportFailure) { assertEquals(expected, failure.code); assertNull(failure.cause); assertFalse(failure.toString().contains(server.hostName)) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun responseAndCombinedCatalogueLimitsFailSafely() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = fixtures(); server.start()
            val connection = XtreamConnection.create(server.url("/").toString(), "u", "p")!!
            try { XtreamLoader(ParserLimits(maxEntries = 1)).load(connection); fail("Expected catalogue limit") }
            catch (failure: ImportFailure) { assertEquals(ImportError.TOO_MANY_ENTRIES, failure.code) }
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse(body = "x".repeat(128))); server.start()
            try { XtreamLoader(ParserLimits(maxBytes = 32)).load(XtreamConnection.create(server.url("/").toString(), "u", "p")!!); fail("Expected byte limit") }
            catch (failure: ImportFailure) { assertEquals(ImportError.TOO_LARGE, failure.code) }
        }
    }

    @Test fun explicitEmptyEpisodeCollectionsAreNoContentRatherThanMalformed() = runBlocking {
        for (body in listOf("""{"episodes":[]}""", """{"episodes":{}}""", """{"episodes":null}""", "{}", "[]", "null")) MockWebServer().use { server ->
            server.enqueue(MockResponse(body = body)); server.start()
            try { XtreamLoader().episodes(XtreamConnection.create(server.url("/").toString(), "u", "p")!!, 1); fail("Expected no episodes") }
            catch (failure: ImportFailure) { assertEquals(ImportError.NO_CONTENT, failure.code) }
        }
    }

    @Test fun redirectNeverForwardsCredentialsAndMalformedResponsesHaveFixedErrors() = runBlocking {
        MockWebServer().use { target ->
            target.start()
            MockWebServer().use { server ->
                server.enqueue(MockResponse.Builder().code(302).addHeader("Location", target.url("/private")).build()); server.start()
                try { XtreamLoader().load(XtreamConnection.create(server.url("/").toString(), "u", "p")!!); fail("Expected redirect rejection") }
                catch (failure: ImportFailure) { assertEquals(ImportError.HTTP_ERROR, failure.code); assertNull(failure.cause) }
                assertEquals(0, target.requestCount)
            }
        }
        for (body in listOf("<html>private provider response</html>", "{", """{"user_info":{"auth":1}} trailing""", "[1]")) MockWebServer().use { server ->
            server.enqueue(MockResponse(body = body)); server.start()
            try { XtreamLoader().load(XtreamConnection.create(server.url("/").toString(), "u", "p")!!); fail("Expected response failure") }
            catch (failure: ImportFailure) { assertEquals(ImportError.SERVER_RESPONSE, failure.code); assertNull(failure.cause); assertFalse(failure.toString().contains("private")) }
        }
    }

    @Test fun cancellationStopsImportBeforeAnyCatalogueRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().body("""{"user_info":{"auth":1}}""").bodyDelay(2, java.util.concurrent.TimeUnit.SECONDS).build())
            server.start()
            val deferred = async { XtreamLoader().load(XtreamConnection.create(server.url("/").toString(), "u", "p")!!) }
            val request = withContext(Dispatchers.IO) { server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS) }
            assertNotNull(request)
            deferred.cancelAndJoin()
            assertTrue(deferred.isCancelled)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun optionalUnsupportedCategoriesAndVodStillAllowLiveCatalogue() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(body = """{"user_info":{"auth":1}}"""))
            server.enqueue(MockResponse(code = 404))
            server.enqueue(MockResponse(body = """[{"stream_id":1,"name":"Live"}]"""))
            repeat(4) { server.enqueue(MockResponse(code = 404)) }
            server.start()
            val entries = XtreamLoader().load(XtreamConnection.create(server.url("/").toString(), "u", "p")!!)
            assertEquals(ContentKind.LIVE, entries.single().channel.kind)
            assertNull(entries.single().channel.group)
        }
    }

    @Test fun fieldDepthUtf8AndDecompressedByteLimitsAreEnforced() = runBlocking {
        val malformed = okio.Buffer().write(byteArrayOf(0x7b, 0x22, 0xc3.toByte(), 0x28, 0x22, 0x3a, 0x31, 0x7d))
        val deep = """{"ignored":""" + "[".repeat(40) + "0" + "]".repeat(40) + "}"
        val oversized = """{"user_info":{"auth":1,"status":"""" + "x".repeat(513) + "\"}}"
        val compressed = okio.Buffer()
        okio.GzipSink(compressed).buffer().use { it.writeUtf8("""{"ignored":"""" + "x".repeat(2048) + "\"}") }
        val cases = listOf(
            MockResponse.Builder().body(malformed).build() to ImportError.SERVER_RESPONSE,
            MockResponse(body = deep) to ImportError.SERVER_RESPONSE,
            MockResponse(body = oversized) to ImportError.FIELD_TOO_LONG,
            MockResponse.Builder().addHeader("Content-Encoding", "gzip").body(compressed).build() to ImportError.TOO_LARGE,
        )
        for ((response, expected) in cases) MockWebServer().use { server ->
            server.enqueue(response); server.start()
            val limits = if (expected == ImportError.TOO_LARGE) ParserLimits(maxBytes = 128) else ParserLimits()
            try { XtreamLoader(limits).load(XtreamConnection.create(server.url("/").toString(), "u", "p")!!); fail("Expected bounded failure") }
            catch (failure: ImportFailure) { assertEquals(expected, failure.code); assertNull(failure.cause) }
        }
        var clock = 0L
        try {
            XtreamJson(ParserLimits(maxMillis = 1), { clock.also { clock += 2_000_000 } }).auth("{}".byteInputStream()) {}
            fail("Expected parse budget")
        } catch (failure: ImportFailure) { assertEquals(ImportError.TIME_LIMIT, failure.code) }
    }

    companion object {
        fun fixtures(): Dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse(body = when (request.url.queryParameter("action")) {
                null -> """{"user_info":{"auth":"1","status":"Active","exp_date":null,"allowed_output_formats":["m3u8","ts"]}}"""
                "get_live_categories" -> """[{"category_id":"1","category_name":"Live group"}]"""
                "get_live_streams" -> """[{"stream_id":10,"name":"Live fixture","category_id":"1","direct_source":"https://ignored.invalid/private"}]"""
                "get_vod_categories" -> """[{"category_id":"2","category_name":"Film group"}]"""
                "get_vod_streams" -> """[{"stream_id":"20","name":"Film fixture","category_id":"2","container_extension":"mp4"}]"""
                "get_series_categories" -> """[{"category_id":"3","category_name":"Series group"}]"""
                "get_series" -> """[{"series_id":30,"name":"Series fixture","category_id":"3"}]"""
                "get_series_info" -> """{"episodes":{"1":[{"id":"31","title":"Episode fixture","season":1,"episode_num":2,"container_extension":"mp4"}]}}"""
                else -> "[]"
            })
        }
    }
}
