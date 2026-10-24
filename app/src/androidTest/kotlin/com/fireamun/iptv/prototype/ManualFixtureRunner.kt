package com.fireamun.iptv.prototype

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import java.io.File
import java.net.InetAddress
import androidx.test.runner.AndroidJUnitRunner

/** Separate developer fixture host in the test APK; never included in the application. */
class ManualFixtureRunner : AndroidJUnitRunner() {
    private var manual = false
    override fun onCreate(arguments: Bundle?) {
        manual = arguments?.getString("manualFixture") == "true"
        super.onCreate(arguments)
    }
    override fun onStart() { if (manual) serveFixture() else super.onStart() }
    private fun serveFixture() {
        val stop = File(targetContext.cacheDir, "manual-qa-stop")
        stop.delete()
        val clip = context.assets.open("synthetic-test.mp4").use { it.readBytes() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/playlist.m3u" -> MockResponse(body = "#EXTM3U\n#EXTINF:-1 group-title=\"Synthetic\",Synthetic test clip\nhttp://127.0.0.1:8765/clip.mp4\n")
                    "/clip.mp4" -> MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip)).build()
                    else -> MockResponse(code = 404)
                }
            }
            server.start(InetAddress.getByName("127.0.0.1"), 8765)
            sendStatus(0, Bundle().apply { putString("stream", "Synthetic fixture ready on emulator loopback port 8765") })
            val deadline = SystemClock.elapsedRealtime() + 10 * 60_000
            while (!stop.exists() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(250)
        }
        stop.delete()
        finish(Activity.RESULT_OK, Bundle())
    }
}
