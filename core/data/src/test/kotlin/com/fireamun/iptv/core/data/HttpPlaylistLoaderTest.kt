package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.MediaAddress
import kotlinx.coroutines.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpPlaylistLoaderTest {
    private val playlist = "#EXTM3U\n#EXTINF:-1,Example\nhttps://example.invalid/live\n"
    @Test fun unavailableAccountDownloadFallsBackToSameServerApi() = runBlocking {
        MockWebServer().use { server ->
            val fixtures = XtreamLoaderTest.fixtures()
            server.dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse =
                    if (request.url.encodedPath == "/get.php") MockResponse(code = 404) else fixtures.dispatch(request)
            }
            server.start()
            val address = MediaAddress.parse(server.url("/get.php?username=fixture-user&password=fixture-password&type=m3u&output=ts").toString())!!
            val result = HttpPlaylistLoader().load(address)
            assertEquals(3, result.size)
            assertNotNull(result.last().seriesId)
            val saved = SourceSpec.decode(SourceSpec.url(address).encode())
            assertNotNull("URL imports must retain API episode support after restart", saved.xtream)
            assertEquals(server.url("/player_api.php").encodedPath, saved.xtream!!.apiUrl().encodedPath)
            repeat(8) { assertEquals(server.port, server.takeRequest().url.port) }
        }
    }

    @Test fun ordinaryAndRestrictedDownloadLinksNeverTriggerApiFallback() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for (path in listOf("/playlist.m3u", "/get.php?username=u&password=p&type=m3u&category_id=4", "/get.php?username=u&username=v&password=p&type=m3u")) {
                server.enqueue(MockResponse(code = 404))
                try { HttpPlaylistLoader().load(MediaAddress.parse(server.url(path).toString())!!); fail("Expected HTTP error") }
                catch (failure: ImportFailure) { assertEquals(ImportError.HTTP_ERROR, failure.code); assertNull(failure.cause) }
            }
            assertEquals(3, server.requestCount)
        }
    }
    @Test fun redirectedOrRejectedAccountExportsNeverTriggerApiFallback() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val address = MediaAddress.parse(server.url("/get.php?username=u&password=p&type=m3u").toString())!!
            server.enqueue(MockResponse(code = 403))
            try { HttpPlaylistLoader().load(address); fail("Expected rejection") }
            catch (failure: ImportFailure) { assertEquals(ImportError.HTTP_ERROR, failure.code) }
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/missing").build())
            server.enqueue(MockResponse(code = 404))
            try { HttpPlaylistLoader().load(address); fail("Expected redirect destination error") }
            catch (failure: ImportFailure) { assertEquals(ImportError.HTTP_ERROR, failure.code) }
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun accountExportRecognitionPreservesPrefixAndEncodedCredentials() {
        val address = MediaAddress.parse("https://example.invalid/prefix/get.php?username=u%2B%2F&password=p%3F%26&type=m3u_plus&output=m3u8")!!
        val source = XtreamConnection.fromPlaylistUrl(address)!!
        assertEquals("/prefix/player_api.php", source.apiUrl().encodedPath)
        assertEquals("u+/", source.apiUrl().queryParameter("username"))
        assertEquals("p?&", source.apiUrl().queryParameter("password"))
        for (url in listOf(
            "https://example.invalid/get.php?username=u&password=p&type=hls",
            "https://example.invalid/get.php?username=&password=p&type=m3u",
            "https://example.invalid/get.php?username=u&password=p&type=m3u#fragment",
            "https://embedded:password@example.invalid/get.php?username=u&password=p&type=m3u",
        )) assertNull(XtreamConnection.fromPlaylistUrl(MediaAddress.parse(url)!!))
    }
    @Test fun clientIncompatibleAddressIsASafeValidationFailure() = runBlocking {
        val address = requireNotNull(MediaAddress.parse("https://[fe80::1%25en0]/live"))
        try { HttpPlaylistLoader().load(address); fail("Expected invalid address") }
        catch (failure: ImportFailure) { assertEquals(ImportError.INVALID_ADDRESS, failure.code) }
    }
    @Test fun followsSameProtocolRedirectAndParsesResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/next").build())
            server.enqueue(MockResponse(body = playlist))
            server.start()
            val result = HttpPlaylistLoader().load(requireNotNull(MediaAddress.parse(server.url("/start").toString())))
            assertEquals("Example", result.single().channel.title)
        }
    }
    @Test fun mapsHttpAndInvalidResponsesToSafeFailures() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val address = requireNotNull(MediaAddress.parse(server.url("/private-token").toString()))
            server.enqueue(MockResponse(code = 403, body = "private-token"))
            try { HttpPlaylistLoader().load(address); fail("Expected rejection") }
            catch (failure: ImportFailure) {
                assertEquals(ImportError.HTTP_ERROR, failure.code)
                assertFalse(failure.toString().contains("private-token"))
            }
            server.enqueue(MockResponse(body = "<html>private-token</html>"))
            try { HttpPlaylistLoader().load(address); fail("Expected invalid playlist") }
            catch (failure: ImportFailure) { assertEquals(ImportError.INVALID_PLAYLIST, failure.code) }
        }
    }
    @Test fun refusesHttpsDowngradeWithoutRequestingUnencryptedDestination() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { secure ->
            secure.useHttps(serverCertificates.sslSocketFactory())
            secure.start()
            secure.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://127.0.0.1:1/private-token").build())
            val client = OkHttpClient.Builder().sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager).build()
            try {
                HttpPlaylistLoader(client = client).load(requireNotNull(MediaAddress.parse(secure.url("/source").toString())))
                fail("Downgrade must not succeed")
            } catch (failure: ImportFailure) { assertEquals(ImportError.HTTP_ERROR, failure.code) }
        }
    }
    @Test fun enforcesLimitsOnNetworkInput() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse(body = "#EXTM3U\n" + "# comment\n".repeat(50)))
            try {
                HttpPlaylistLoader(M3uParser(ParserLimits(maxBytes = 64))).load(requireNotNull(MediaAddress.parse(server.url("/").toString())))
                fail("Oversized input must be rejected")
            } catch (failure: ImportFailure) { assertEquals(ImportError.TOO_LARGE, failure.code) }
        }
    }
    @Test fun cancellationStopsAnInFlightRead() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body(playlist).bodyDelay(1, TimeUnit.SECONDS).build())
            val job = async { HttpPlaylistLoader().load(requireNotNull(MediaAddress.parse(server.url("/").toString()))) }
            withContext(Dispatchers.IO) { requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            withTimeout(3000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }
}
