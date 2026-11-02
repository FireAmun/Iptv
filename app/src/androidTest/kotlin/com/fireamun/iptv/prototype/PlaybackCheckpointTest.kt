package com.fireamun.iptv.prototype

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.MediaAddress
import com.fireamun.iptv.core.model.PlaybackCheckpoint
import com.fireamun.iptv.core.model.PlaybackRequest
import com.fireamun.iptv.core.model.PlaybackStart
import com.fireamun.iptv.core.model.PlaybackStatus
import com.fireamun.iptv.core.playback.PlaybackSurface
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

private object CheckpointBridge {
    val request = mutableStateOf<PlaybackRequest?>(null)
    var start: () -> PlaybackStart? = { null }
    var checkpoint: (String, PlaybackCheckpoint) -> Unit = { _, _ -> }
    var status: (PlaybackStatus) -> Unit = { }
    var disposed: () -> Unit = { }
}

@RunWith(AndroidJUnit4::class)
class PlaybackCheckpointTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun actualVodSeekBackwardPauseAndRestartUsesLatestOffset() = withClip { server ->
        val reports = CopyOnWriteArrayList<Pair<String, PlaybackCheckpoint>>()
        val starts = CopyOnWriteArrayList<Long>()
        var nextOffset = 12_000L
        CheckpointBridge.start = { starts += nextOffset; PlaybackStart("instance-${starts.size}", nextOffset) }
        CheckpointBridge.checkpoint = { id, value -> reports += id to value; if (value.eligible) nextOffset = value.positionMs }
        open(PlaybackRequest("session", address(server, "/clip.mp4"), ContentKind.MOVIE, 12_000)).use { scenario ->
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition in 10_000..17_000 }
            scenario.onActivity { playerView(it).player!!.seekTo(4_000) }
            awaitPlayer(scenario) { it.contentPosition in 3_000..8_000 }
            scenario.onActivity { playerView(it).player!!.pause() }
            await { reports.any { (_, value) -> value.eligible && value.positionMs in 3_000..8_000 } }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition in 3_000..9_000 }
            assertEquals(2, starts.size)
            assertTrue(starts[1] < 10_000)
        }
    }

    @Test fun finalCloseReportsCheckpointBeforeDisposalAndRetiresCallbacks() = withClip { server ->
        val events = CopyOnWriteArrayList<String>()
        CheckpointBridge.start = { PlaybackStart("instance", 1_000) }
        CheckpointBridge.checkpoint = { id, value -> if (value.eligible) events += "$id:${value.positionMs}" }
        CheckpointBridge.disposed = { events += "disposed" }
        open(PlaybackRequest("session", address(server, "/clip.mp4"), ContentKind.MOVIE, 1_000)).use { scenario ->
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            val count = events.size
            scenario.onActivity { CheckpointBridge.request.value = null }
            await { events.lastOrNull() == "disposed" }
            assertEquals(1, events.count { it == "disposed" })
            assertTrue(events.take(count).any { it.startsWith("instance:") })
            val closedCount = events.size
            SystemClock.sleep(300)
            assertEquals(closedCount, events.size)
        }
    }

    @Test fun failedPreparationDoesNotEmitEligibleZero() = withClip { server ->
        val reports = CopyOnWriteArrayList<PlaybackCheckpoint>()
        CheckpointBridge.start = { PlaybackStart("instance", 12_000) }
        CheckpointBridge.checkpoint = { _, value -> reports += value }
        val statuses = CopyOnWriteArrayList<PlaybackStatus>()
        CheckpointBridge.status = { statuses += it }
        open(PlaybackRequest("failed", address(server, "/missing.mp4"), ContentKind.MOVIE, 12_000)).use {
            await { PlaybackStatus.FAILED in statuses }
            assertFalse(reports.any { value -> value.eligible && value.positionMs == 0L })
        }
    }

    @Test fun liveAndUnknownNeverReportEligibleCheckpoint() = withClip { server ->
        for (kind in listOf(ContentKind.LIVE, ContentKind.UNKNOWN)) {
            val reports = CopyOnWriteArrayList<PlaybackCheckpoint>()
            CheckpointBridge.start = { PlaybackStart("instance", 12_000) }
            CheckpointBridge.checkpoint = { _, value -> reports += value }
            open(PlaybackRequest("session-$kind", address(server, "/clip.mp4"), kind, 12_000)).use { scenario ->
                awaitPlayer(scenario) { it.playbackState == Player.STATE_READY }
                scenario.onActivity { playerView(it).player!!.pause() }
                await { reports.isNotEmpty() }
                assertFalse(reports.any { it.eligible })
            }
        }
    }

    @Test fun periodicPlaybackAndActualEndReportProgress() = withClip { server ->
        val reports = CopyOnWriteArrayList<PlaybackCheckpoint>()
        CheckpointBridge.start = { PlaybackStart("instance", 1_000) }
        CheckpointBridge.checkpoint = { _, value -> reports += value }
        open(PlaybackRequest("periodic", address(server, "/clip.mp4"), ContentKind.MOVIE, 1_000)).use { scenario ->
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.isPlaying }
            await { reports.any { it.eligible && !it.ended && it.positionMs >= 5_000 } }
            scenario.onActivity { playerView(it).player!!.seekTo(29_500) }
            await { reports.any { it.eligible && it.ended } }
        }
    }

    @Test fun retiredRequestCreatesNoPlayerAndDisposesOnce() = withClip { server ->
        val events = CopyOnWriteArrayList<String>()
        CheckpointBridge.start = { null }
        CheckpointBridge.checkpoint = { _, _ -> events += "checkpoint" }
        CheckpointBridge.disposed = { events += "disposed" }
        open(PlaybackRequest("retired", address(server, "/clip.mp4"), ContentKind.MOVIE, 12_000)).use { scenario ->
            await {
                var attached = false
                scenario.onActivity { attached = playerViewOrNull(it) != null }
                attached
            }
            scenario.onActivity { assertNull(playerView(it).player) }
        }
        assertEquals(listOf("disposed"), events.toList())
    }

    @Test fun preparedErrorReportsLastGoodPositionBeforeFailedStatus() = withClip { server ->
        val events = CopyOnWriteArrayList<Pair<PlaybackStatus?, PlaybackCheckpoint?>>()
        CheckpointBridge.start = { PlaybackStart("instance", 12_000) }
        CheckpointBridge.checkpoint = { _, value -> events += null to value }
        CheckpointBridge.status = { value -> events += value to null }
        open(PlaybackRequest("prepared-error", address(server, "/clip.mp4"), ContentKind.MOVIE, 12_000)).use { scenario ->
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition >= 10_000 }
            scenario.onActivity { playerView(it).player!!.pause() }
            await { events.any { it.second?.eligible == true } }
            val prior = events.size
            scenario.onActivity { playerView(it).player!!.apply {
                setMediaItem(MediaItem.fromUri(server.url("/missing.mp4").toString()))
                prepare()
            } }
            await { events.any { it.first == PlaybackStatus.FAILED } }
            val failedIndex = events.indexOfFirst { it.first == PlaybackStatus.FAILED }
            assertTrue(events.subList(prior, failedIndex).any { it.second?.eligible == true && it.second!!.positionMs >= 10_000 })
        }
    }

    private fun open(request: PlaybackRequest): ActivityScenario<MobileActivity> {
        instrumentation.runOnMainSync { CheckpointBridge.request.value = request }
        return ActivityScenario.launch(MobileActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CheckpointBridge.request.value?.let { active ->
                        PlaybackSurface(active, CheckpointBridge.start, CheckpointBridge.checkpoint,
                            CheckpointBridge.status, CheckpointBridge.disposed, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
    private fun address(server: MockWebServer, path: String) = MediaAddress.parse(server.url(path).toString())!!
    private fun playerViewOrNull(activity: MobileActivity): PlayerView? {
        fun find(view: View): PlayerView? = when (view) {
            is PlayerView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
            else -> null
        }
        return find(activity.window.decorView)
    }
    private fun playerView(activity: MobileActivity): PlayerView = playerViewOrNull(activity) ?: error("PlayerView missing")
    private fun awaitPlayer(scenario: ActivityScenario<MobileActivity>, predicate: (Player) -> Boolean) {
        await {
            var okay = false
            scenario.onActivity { activity -> okay = playerViewOrNull(activity)?.player?.let(predicate) == true }
            okay
        }
    }
    private fun await(predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < end) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        fail("Timed out waiting for playback event")
    }
    private fun withClip(block: (MockWebServer) -> Unit) {
        val bytes = instrumentation.context.assets.open("synthetic-test.mp4").use { it.readBytes() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.url.encodedPath != "/clip.mp4") return MockResponse(code = 404)
                    val first = Regex("bytes=(\\d+)-").find(request.headers["Range"] ?: "")?.groupValues?.get(1)?.toIntOrNull()
                    if (first != null && first >= bytes.size) return MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${bytes.size}").build()
                    val offset = first ?: 0
                    return MockResponse.Builder().code(if (first == null) 200 else 206)
                        .addHeader("Content-Type", "video/mp4")
                        .addHeader("Accept-Ranges", "bytes")
                        .addHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
                        .body(Buffer().write(bytes, offset, bytes.size - offset)).build()
                }
            }
            server.start()
            try { block(server) } finally {
                instrumentation.runOnMainSync { CheckpointBridge.request.value = null }
                CheckpointBridge.start = { null }
                CheckpointBridge.checkpoint = { _, _ -> }
                CheckpointBridge.status = { }
                CheckpointBridge.disposed = { }
            }
        }
    }
}
