package com.fireamun.iptv.prototype

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.posthog.PostHog
import com.posthog.PostHogCompression
import com.posthog.android.PostHogAndroid
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PostHogDeliveryTest {
    @get:Rule val diagnostics = TestDiagnostics()

    @Test fun sdkSerializesOnlySanitizedEventsErrorsAndLogs() {
        // Intercept every SDK request: no test data or project key leaves the device.
        PostHog.close()
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val forwarded = CopyOnWriteArrayList<Throwable>()
        val priorHandler = Thread.UncaughtExceptionHandler { _, error -> forwarded.add(error) }
        val payloads = CopyOnWriteArrayList<String>()
        val delivered = CountDownLatch(1)
        val expected = listOf("library_imported", "library_refreshed", "content_playback_started", "favourite_changed", "\$exception", "library import failed")
        val secret = "synthetic-private-token"
        val config = postHogConfiguration("phc_fixture", "https://us.i.posthog.com")!!
        config.compression = PostHogCompression.NONE
        config.flushAt = 1
        config.flushIntervalSeconds = 1
        config.logs.flushAt = 1
        config.logs.flushIntervalSeconds = 1
        config.httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            val bytes = if (request.header("Content-Encoding") == "gzip") GzipSource(buffer).buffer().use { it.readByteArray() } else buffer.readByteArray()
            payloads.add(bytes.toString(Charsets.UTF_8))
            val combined = payloads.joinToString()
            if (expected.all { combined.contains(it) }) delivered.countDown()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"errorTracking":{"autocaptureExceptions":true}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        Thread.setDefaultUncaughtExceptionHandler(priorHandler)
        val client = try {
            PostHogAndroid.with(InstrumentationRegistry.getInstrumentation().targetContext, config)
        } catch (failure: Throwable) {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            throw failure
        }
        try {
            for (event in listOf("library_imported", "library_refreshed")) client.capture(event = event,
                properties = mapOf("source_kind" to "xtream", "entry_count" to 12345, "url" to secret))
            client.capture(event = "content_playback_started", properties = mapOf("content_kind" to "movie", "title" to secret))
            client.capture(event = "favourite_changed", properties = mapOf("content_kind" to "movie", "is_favourite" to true))
            client.logger.warn("library import failed", mapOf("operation" to "refresh", "source_kind" to "url", "error_code" to "read_failed", "url" to secret))
            client.capture(event = "unapproved_fixture_event", properties = mapOf("url" to secret))
            val error = IllegalStateException(secret)
            val handlerDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (Thread.getDefaultUncaughtExceptionHandler() === priorHandler && System.nanoTime() < handlerDeadline) Thread.sleep(10)
            val installed = Thread.getDefaultUncaughtExceptionHandler()
            assertNotSame(priorHandler, installed)
            // Exercise the actual installed uncaught handler with a controlled prior
            // handler, so this synthetic error cannot kill the instrumentation process.
            installed!!.uncaughtException(Thread.currentThread(), error)
            client.flush()
            assertTrue("Expected sanitized SDK requests", delivered.await(20, TimeUnit.SECONDS))
            assertEquals(listOf(error), forwarded.toList())
            val sent = payloads.joinToString()
            assertFalse(sent.contains(secret))
            assertFalse(sent.contains("unapproved_fixture_event"))
            assertFalse(sent.contains("\"entry_count\""))
            assertTrue(sent.contains("10001_100000"))
            assertTrue(sent.contains("Details withheld"))
            assertTrue(sent.contains("IllegalStateException"))
        } finally {
            client.close()
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }
}
