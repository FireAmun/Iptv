package com.fireamun.iptv.prototype

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import com.fireamun.iptv.core.playback.PlaybackPresentation
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native Activity/PiP/MediaSession tests; no production-only bridge or fake player. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerPictureInPictureTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as IptvApplication

    @Test fun manualNativePipKeepsOneAdvancingPlayerAndFullscreenRestoresChrome() = movie { fixture ->
        if (!supportsPip()) { assertUnsupported(fixture); return@movie }
        val player = read(fixture) { attached(it) }
        val before = read(fixture) { attached(it).contentPosition }
        activate("player_pip")
        awaitPip(fixture, true)
        awaitPlayer(fixture) { it === player && it.isPlaying && it.contentPosition >= before + 500 }
        assertPipChromeHidden(fixture)
        captureSyntheticPreview(instrumentation, "pip-native-playing-owned-video")
        fullscreen(fixture)
        awaitPlayer(fixture) { it === player && it.isPlaying && it.playWhenReady }
        awaitTag("playback_status")
        awaitTag("player_pip")
        read(fixture) { assertTrue(playerView(it)!!.useController) }
    }

    @Test fun pausedManualPipAndNativeSessionCommandsKeepIntentOnFullscreenReturn() = movie { fixture ->
        if (!supportsPip()) { assertUnsupported(fixture); return@movie }
        val player = read(fixture) { attached(it) }
        try {
            awaitTag("player_pip")
            logPausedMinimizeState(fixture, player, "before_pause")
            read(fixture) { attached(it).pause() }
            awaitPlayer(fixture) { !it.playWhenReady && !it.isPlaying }
            logPausedMinimizeState(fixture, player, "after_pause")
            activate("player_pip")
        } catch (failure: ComposeTimeoutException) {
            diagnosePausedMinimize(fixture, player)
            throw failure
        }
        awaitPip(fixture, true)
        awaitPlayer(fixture) { it === player && !it.playWhenReady && !it.isPlaying }
        assertPipChromeHidden(fixture)
        // Dispatch through Android's native active MediaSession, rather than app callbacks.
        dispatchNativeSessionCommand("play")
        awaitPlayer(fixture) { it === player && it.isPlaying }
        dispatchNativeSessionCommand("pause")
        awaitPlayer(fixture) { it === player && !it.playWhenReady && !it.isPlaying }
        awaitPlaybackThread(fixture)
        val pausedAt = read(fixture) { attached(it).contentPosition }
        fullscreen(fixture)
        awaitPlayer(fixture) { it === player && !it.playWhenReady && !it.isPlaying }
        read(fixture) { assertEquals(pausedAt, attached(it).contentPosition) }
        // Expansion must reset dismissal tracking: a later ordinary paused Home keeps the request.
        home(fixture)
        await { read(fixture) { !it.isInPictureInPictureMode && playerView(it)?.player == null } }
        fullscreen(fixture)
        awaitPlayer(fixture) { it !== player && it.playbackState == Player.STATE_READY && !it.playWhenReady && !it.isPlaying }
    }

    @Test fun playingHomeEntersNativePipAndSystemCloseFinalizesProgressWithoutReopeningPlayback() = movie { fixture ->
        if (!supportsPip()) { assertUnsupported(fixture); return@movie }
        val player = read(fixture) { attached(it) }
        home(fixture)
        awaitPip(fixture, true)
        awaitPlayer(fixture) { it === player && it.isPlaying }
        assertPipChromeHidden(fixture)
        val beforeClose = read(fixture) { attached(it).contentPosition }
        closeThroughNativePipMenu()
        await {
            read(fixture) { playerView(it)?.player == null && !(it as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        }
        val final = runBlocking { app.progressRecorder.flush().await(); app.libraryStore.read(fixture.target)!!.progress }
        assertTrue("Dismissal must persist the final current position", final.positionMs >= beforeClose - 250)
        assertTrue(final.positionMs < 30_000)
        assertFalse(final.completed)
        // API28 SystemUI preserves the stopped task after Close. Reopen as a user would;
        // ActivityScenario.launch adds CLEAR_TASK and destroys that preserved task instead.
        withReopenedActivityAfterNativeClose(fixture) { reopenedFixture ->
            awaitTag("nav_movies")
            compose.onNodeWithTag("player_surface").assertDoesNotExist()
            compose.onNodeWithTag("player_pip").assertDoesNotExist()
            read(fixture) { assertNull("The original player must remain detached", playerView(it)?.player) }
            read(reopenedFixture) { assertNull(playerView(it)?.player) }
            // A newly created Activity starts on Live; select Movies before awaiting its saved card.
            compose.openDestination("nav_movies", tv = false)
            awaitTag("channel_${fixture.movieId}")
            val reopened = runBlocking { app.progressRecorder.flush().await(); app.libraryStore.read(fixture.target)!!.progress }
            assertEquals("Retired playback must not overwrite the final checkpoint", final, reopened)
        }
    }

    @Test fun pausedHomeDoesNotEnterPipAndActualStopStartKeepsPauseAndPosition() = movie { fixture ->
        val old = read(fixture) { attached(it).apply { pause() } }
        awaitPlayer(fixture) { !it.playWhenReady }
        val pausedAt = read(fixture) { attached(it).contentPosition }
        home(fixture)
        await { read(fixture) { !it.isInPictureInPictureMode && playerView(it)?.player == null && !(it as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) } }
        fullscreen(fixture)
        awaitPlayer(fixture) { it !== old && it.playbackState == Player.STATE_READY && !it.playWhenReady && !it.isPlaying }
        read(fixture) { assertTrue(kotlin.math.abs(attached(it).contentPosition - pausedAt) <= 500) }
        awaitTag("playback_status")
    }

    @Test fun deniedNativeAppOpHidesMinimizeAndHomeDoesNotEnterPip() = withDeniedNativePipAppOp {
        movie { fixture ->
            compose.onNodeWithTag("player_pip").assertDoesNotExist()
            home(fixture)
            await { read(fixture) { !it.isInPictureInPictureMode && playerView(it)?.player == null } }
            fullscreen(fixture)
            awaitPlayer(fixture) { it.playbackState == Player.STATE_READY }
            compose.onNodeWithTag("player_pip").assertDoesNotExist()
        }
    }

    @Test fun televisionActivityNeverOffersOrEntersPictureInPicture() = movie(TvActivity::class.java, tv = true) { fixture ->
        compose.onNodeWithTag("player_pip").assertDoesNotExist()
        home(fixture)
        await { read(fixture) { !it.isInPictureInPictureMode && playerView(it)?.player == null } }
        fullscreen(fixture)
        awaitPlayer(fixture) { it.playbackState == Player.STATE_READY }
        compose.onNodeWithTag("player_pip").assertDoesNotExist()
    }

    private data class Fixture(val activity: Activity, val type: Class<out Activity>, val movieId: Int, val target: ProgressTarget)

    private fun movie(type: Class<out Activity> = MobileActivity::class.java, tv: Boolean = false, block: (Fixture) -> Unit) {
        val bytes = instrumentation.context.assets.open("synthetic-test.mp4").use { it.readBytes() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.url.encodedPath != "/owned.mp4") return MockResponse(code = 404)
                    val header = request.headers["Range"]
                    val range = header?.let { Regex("bytes=(\\d+)-(\\d*)").matchEntire(it) }
                    if (header != null && range == null) return MockResponse(code = 416)
                    val start = range?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val end = range?.groupValues?.get(2)?.toIntOrNull()?.coerceAtMost(bytes.lastIndex) ?: bytes.lastIndex
                    if (start !in bytes.indices || end < start) return MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${bytes.size}").build()
                    return MockResponse.Builder().code(if (header == null) 200 else 206)
                        .addHeader("Content-Type", "video/mp4").addHeader("Accept-Ranges", "bytes")
                        .apply { if (header != null) addHeader("Content-Range", "bytes $start-$end/${bytes.size}") }
                        .body(Buffer().write(bytes, start, end - start + 1)).build()
                }
            }
            server.start()
            val saved = runBlocking { app.libraryStore.save(SourceSpec.file(), listOf(PlaylistEntry(
                Channel(0, "Owned PiP movie", "Synthetic", ContentKind.MOVIE), MediaAddress.parse(server.url("/owned.mp4").toString())))) }
            val id = saved.entries.single().channel.id
            ActivityScenario.launch<Activity>(Intent(context, type)).use { scenario ->
                var activity: Activity? = null
                scenario.onActivity { activity = it }
                val fixture = Fixture(activity!!, type, id, ProgressTarget(saved.sourceId!!, id, ContentKind.MOVIE))
                compose.openDestination("nav_movies", tv)
                activate("channel_$id", tv)
                activate("detail_play", tv)
                awaitPlayer(fixture) { it.playbackState == Player.STATE_READY && it.isPlaying }
                read(fixture) { attached(it).seekTo(6_000) }
                awaitPlayer(fixture) { it.playbackState == Player.STATE_READY && it.contentPosition >= 5_500 && it.isPlaying }
                block(fixture)
            }
        }
    }

    private fun supportsPip() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    private fun logPausedMinimizeState(fixture: Fixture, originalPlayer: Player, stage: String) {
        // This helper is only called in the owned movie fixture. Never stringify a player,
        // presentation, exception or UI tree: those can include media/provider metadata.
        val stateCaptured = runCatching {
            read(fixture) { activity ->
                val player = playerView(activity)?.player
                android.util.Log.i("IptvPipProbe", "IPTV_PIP_PAUSED_STATE stage=$stage lifecycle=${(activity as LifecycleOwner).lifecycle.currentState.name}" +
                    " nativePip=${activity.isInPictureInPictureMode} focused=${activity.hasWindowFocus()}" +
                    " attached=${player != null} samePlayer=${player === originalPlayer}" +
                    " state=${player?.playbackState} playWhenReady=${player?.playWhenReady}" +
                    " isPlaying=${player?.isPlaying} supportedVideo=${player?.currentTracks?.isTypeSupported(C.TRACK_TYPE_VIDEO)}" +
                    " errorCode=${player?.playerError?.errorCode}")
                // Read the compiled lazy field, avoiding method enumeration that could
                // resolve newer Activity callback parameter classes on API28.
                val holder = MobileActivity::class.java.getDeclaredField("pictureInPicture\$delegate")
                    .apply { isAccessible = true }.get(activity) as Lazy<*>
                check(holder.isInitialized())
                val host = holder.value!!
                fun field(name: String): Any? = host.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(host)
                val presentation = field("presentation") as PlaybackPresentation
                android.util.Log.i("IptvPipProbe", "IPTV_PIP_PAUSED_HOST stage=$stage available=${field("available") as Boolean}" +
                    " entering=${field("entering") as Boolean} nativePip=${activity.isInPictureInPictureMode}" +
                    " canEnter=${presentation.canEnter} wantsToPlay=${presentation.wantsToPlay}" +
                    " isPlaying=${presentation.isPlaying} commands=${field("commands") != null}")
            }
        }.isSuccess
        android.util.Log.i("IptvPipProbe", "IPTV_PIP_PAUSED_DIAGNOSTICS stage=$stage stateCaptured=$stateCaptured")
    }
    private fun diagnosePausedMinimize(fixture: Fixture, originalPlayer: Player) {
        logPausedMinimizeState(fixture, originalPlayer, "timeout")
        for (tag in listOf("player_surface", "player_audio", "player_pip", "playback_status")) {
            val count = runCatching { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size }.getOrNull()
            android.util.Log.i("IptvPipProbe", "IPTV_PIP_PAUSED_TAG tag=$tag count=$count")
        }
        val previewCaptured = runCatching {
            captureSyntheticPreview(instrumentation, "pip-paused-missing-minimize-diagnostic")
        }.isSuccess
        android.util.Log.i("IptvPipProbe", "IPTV_PIP_PAUSED_DIAGNOSTICS previewCaptured=$previewCaptured")
    }
    private fun assertUnsupported(fixture: Fixture) {
        compose.onNodeWithTag("player_pip").assertDoesNotExist()
        read(fixture) { assertFalse(it.isInPictureInPictureMode); assertNotNull(playerView(it)?.player) }
        // This is an asserted unsupported-image result, never an assumption or skipped case.
        println("IPTV_PIP_UNSUPPORTED imageFeature=false activity=MobileActivity")
    }

    private fun assertPipChromeHidden(fixture: Fixture) {
        for (tag in listOf("playback_status", "playback_retry", "player_audio", "player_subtitles", "player_quality", "player_pip", "app_brand", "resume_dialog")) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
        compose.onNodeWithText("Owned PiP movie").assertDoesNotExist()
        read(fixture) { assertFalse(playerView(it)!!.useController); assertFalse(playerView(it)!!.isControllerFullyVisible) }
    }

    private fun home(fixture: Fixture) {
        await { read(fixture) { it.hasWindowFocus() } }
        assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
    }

    private fun fullscreen(fixture: Fixture) {
        context.startActivity(Intent(context, fixture.type).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK))
        await { read(fixture) { !it.isInPictureInPictureMode && it.hasWindowFocus() && (it as LifecycleOwner).lifecycle.currentState == Lifecycle.State.RESUMED } }
    }

    private fun withReopenedActivityAfterNativeClose(fixture: Fixture, block: (Fixture) -> Unit) {
        var current: Activity? = null
        try {
            context.startActivity(Intent(context, fixture.type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            await {
                var ready = false
                instrumentation.runOnMainSync {
                    val resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                        .singleOrNull { fixture.type.isInstance(it) }
                    if (resumed != null) current = resumed
                    ready = resumed?.let { !it.isInPictureInPictureMode && it.hasWindowFocus() } == true
                }
                ready
            }
            block(fixture.copy(activity = current!!))
        } finally {
            // The enclosing scenario owns the original Activity; close any replacement here.
            current?.takeIf { it !== fixture.activity }?.let { activity ->
                instrumentation.runOnMainSync { activity.finish() }
            }
        }
    }

    private fun awaitPip(fixture: Fixture, expected: Boolean) {
        await { read(fixture) { it.isInPictureInPictureMode == expected } }
        android.util.Log.i("IptvPipProbe", "IPTV_PIP_MODE active=$expected sdk=${android.os.Build.VERSION.SDK_INT}")
    }

    private fun closeThroughNativePipMenu() {
        val info = automation.serviceInfo
        val originalFlags = info.flags
        try {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            var bounds: Rect? = null
            await {
                bounds = automation.windows.firstOrNull { it.isInPictureInPictureMode }?.let { window -> Rect().also { window.getBoundsInScreen(it) } }
                bounds?.isEmpty == false
            }
            val rectangle = bounds!!
            tap(rectangle.exactCenterX(), rectangle.exactCenterY())
            var close: AccessibilityNodeInfo? = null
            await {
                close = automation.windows.mapNotNull { it.root }.filter { it.packageName?.toString() == "com.android.systemui" }
                    .firstNotNullOfOrNull(::findNativeClose)
                close != null
            }
            assertTrue("Native SystemUI PiP Close action must succeed", close!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }

    private fun findNativeClose(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val description = listOfNotNull(node.contentDescription?.toString(), node.text?.toString()).joinToString(" ")
        val resource = node.viewIdResourceName.orEmpty()
        if (Regex("(?i)\\b(close|dismiss)\\b").containsMatchIn(description) || resource.endsWith("/pip_dismiss") || resource.endsWith("/close_button")) {
            var clickable: AccessibilityNodeInfo? = node
            repeat(4) { if (clickable?.isClickable == true) return clickable else clickable = clickable?.parent }
        }
        return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let(::findNativeClose) }
    }

    private fun tap(x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }

    private fun dispatchNativeSessionCommand(action: String) {
        require(action == "play" || action == "pause")
        val sdk = android.os.Build.VERSION.SDK_INT
        // MediaSessionService gained its cmd shell handler in Android 11. Older
        // releases expose the same native media-key dispatch through media_cmd.
        val cli = if (sdk < 30) "media" else "cmd media_session"
        val output = shell("$cli dispatch $action")
        val emptyOutput = output.isBlank()
        android.util.Log.i("IptvPipProbe", "IPTV_PIP_NATIVE_COMMAND action=$action cli=$cli sdk=$sdk emptyOutput=$emptyOutput")
        // Do not dump native output: unrelated sessions may contain metadata.
        // API28 exposes stdout only; the same-player assertion remains the
        // bounded semantic check for stderr-only errors or an undelivered key.
        assertTrue("Native MediaSession $action command produced unexpected output (sdk=$sdk)", emptyOutput)
    }
    private fun awaitPlaybackThread(fixture: Fixture) {
        // Paused flags are masked on main before the engine freezes its position. The
        // default playback-looper message follows that pause and its posted state ACK.
        val barrier = read(fixture) { (attached(it) as ExoPlayer).createMessage { _, _ -> }.send() }
        // Keep both loopers free; the caller's subsequent main-thread read consumes the ACK.
        assertTrue("Playback thread must acknowledge the pause", barrier.blockUntilDelivered(5_000))
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun playerView(activity: Activity): PlayerView? {
        fun find(view: View): PlayerView? = when (view) {
            is PlayerView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
            else -> null
        }
        return find(activity.window.decorView)
    }
    private fun attached(activity: Activity) = playerView(activity)?.player ?: error("Actual player is not attached")
    private fun <T> read(fixture: Fixture, block: (Activity) -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching { block(fixture.activity) } }
        return result!!.getOrThrow()
    }
    private fun awaitPlayer(fixture: Fixture, predicate: (Player) -> Boolean) = await { runCatching { read(fixture) { predicate(attached(it)) } }.getOrDefault(false) }
    private fun await(predicate: () -> Boolean) = compose.waitUntil(15_000, predicate)
    private fun awaitTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun activate(tag: String, tv: Boolean = false) {
        awaitTag(tag)
        val node = compose.onNodeWithTag(tag).assertIsEnabled()
        if (!tv && tag.startsWith("player_")) assertPlayerTargetClearOfSystemBars(node)
        if (tv) {
            node.performSemanticsAction(SemanticsActions.RequestFocus)
            await { node.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } && nativeWindowFocused() }
            node.performKeyInput { pressKey(Key.DirectionCenter) }
        } else node.assertIsDisplayed().performClick()
    }
    private fun nativeWindowFocused(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return automation.rootInActiveWindow?.packageName?.toString() == context.packageName
        var focused = false
        instrumentation.runOnMainSync {
            focused = android.view.inspector.WindowInspector.getGlobalWindowViews().any {
                (it.layoutParams as? android.view.WindowManager.LayoutParams)?.type in setOf(
                    android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION,
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION,
                ) && it.hasWindowFocus()
            }
        }
        return focused
    }
}
