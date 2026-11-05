package com.fireamun.iptv.prototype

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.runBlocking
import mockwebserver3.*
import okio.Buffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ContinueWatchingFlowTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as IptvApplication
    private val store get() = app.libraryStore

    @Test fun phoneShowsSavedMovieAndCancelPreservesItsRow() = savedMovie(MobileActivity::class.java, false)
    @Test fun tvShowsSavedMovieAndCancelPreservesItsRow() = savedMovie(TvActivity::class.java, true)
    @Test fun phoneActualMovieResumesResetsCompletesAndReopens() = actualMovie(MobileActivity::class.java, false)
    @Test fun tvActualMovieResumesResetsCompletesAndReopens() = actualMovie(TvActivity::class.java, true)
    @Test fun phoneActualEpisodesKeepIdentityAfterReordering() = actualEpisodes(MobileActivity::class.java, false)
    @Test fun tvActualEpisodesKeepIdentityAfterReordering() = actualEpisodes(TvActivity::class.java, true)

    private class Fixture(val server: MockWebServer, val sourceId: String, val movieId: Int, val seriesId: Int, val liveId: Int,
        val unknownId: Int, val reordered: AtomicBoolean, val failMedia: AtomicBoolean, val providerRequests: AtomicInteger)

    private fun withFixture(block: (Fixture) -> Unit) {
        val bytes = instrumentation.context.assets.open("synthetic-test.mp4").use { it.readBytes() }
        val reordered = AtomicBoolean()
        val failMedia = AtomicBoolean()
        val providerRequests = AtomicInteger()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.url.encodedPath == "/player_api.php") {
                        providerRequests.incrementAndGet()
                        val a = """{"id":31,"title":"Episode A","episode_num":1,"container_extension":"mp4"}"""
                        val b = """{"id":32,"title":"Episode B","episode_num":2,"container_extension":"mp4"}"""
                        return MockResponse(body = """{"episodes":{"1":[${if (reordered.get()) "$b,$a" else "$a,$b"}]}}""")
                    }
                    if (failMedia.get()) return MockResponse(code = 503)
                    val offset = Regex("bytes=(\\d+)-").find(request.headers["Range"] ?: "")?.groupValues?.get(1)?.toIntOrNull()
                    if (offset != null && offset >= bytes.size) return MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${bytes.size}").build()
                    val start = offset ?: 0
                    return MockResponse.Builder().code(if (offset == null) 200 else 206).addHeader("Content-Type", "video/mp4")
                        .addHeader("Accept-Ranges", "bytes").addHeader("Content-Range", "bytes $start-${bytes.lastIndex}/${bytes.size}")
                        .body(Buffer().write(bytes, start, bytes.size - start)).build()
                }
            }
            server.start()
            val fixture = runBlocking {
                val source = SourceSpec.xtream("Continue fixture", XtreamConnection.create(server.url("/").toString(), "fixture", "fixture")!!)
                val saved = store.save(source, listOf(
                    PlaylistEntry(Channel(0, "Movie fixture", "Drama", ContentKind.MOVIE), MediaAddress.parse(server.url("/movie.mp4").toString()), providerKey = "MOVIE:20"),
                    PlaylistEntry(Channel(1, "Series fixture", "Stories", ContentKind.SERIES), null, 30, "SERIES:30"),
                    PlaylistEntry(Channel(2, "Live fixture", null, ContentKind.LIVE), MediaAddress.parse(server.url("/live.mp4").toString())),
                    PlaylistEntry(Channel(3, "Unknown fixture", null, ContentKind.UNKNOWN), MediaAddress.parse(server.url("/unknown.mp4").toString()))))
                val second = store.save(SourceSpec.file(), listOf(PlaylistEntry(Channel(0, "Other source movie", null, ContentKind.MOVIE), MediaAddress.parse(server.url("/other.mp4").toString()))))
                store.save(ProgressTarget(second.sourceId!!, second.entries.single().channel.id, ContentKind.MOVIE), WatchProgress(9_000, 30_000, false, 999))
                store.activate(saved.sourceId!!)
                Fixture(server, saved.sourceId!!, saved.entries[0].channel.id, saved.entries[1].channel.id, saved.entries[2].channel.id,
                    saved.entries[3].channel.id, reordered, failMedia, providerRequests)
            }
            block(fixture)
        }
    }

    private fun actualMovie(activity: Class<out Activity>, tv: Boolean) = withFixture { fixture ->
        val originalDark = Appearance.isDark(instrumentation.targetContext)
        instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, true) }
        val target = ProgressTarget(fixture.sourceId, fixture.movieId, ContentKind.MOVIE)
        val intent = Intent(instrumentation.targetContext, activity)
        var scenario = ActivityScenario.launch<Activity>(intent)
        try {
            compose.openDestination("nav_movies", tv)
            compose.onNodeWithText("Other source movie").assertDoesNotExist()
            activate("channel_${fixture.movieId}", tv); activate("detail_play", tv)
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY }
            scenario.onActivity { player(it).seekTo(8_000); player(it).pause() }
            awaitPosition(target, 7_000L..10_000L)
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED)
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition in 7_000..12_000 }
            scenario.recreate()
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition in 7_000..14_000 }
            scenario.onActivity { player(it).pause() }
            back()
            val recent = runBlocking { app.progressRecorder.flush().await(); store.recent(fixture.sourceId, ContentKind.MOVIE).single() }
            val tag = "recent_${recent.reference.refDigest}"
            awaitTag(tag)
            if (tv) awaitFocus("channel_${fixture.movieId}")
            exposeRecent(tag, tv)
            compose.onNodeWithTag("recent_time_${recent.reference.refDigest}", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag(tag).assertIsDisplayed()
            captureSyntheticPreview(instrumentation, "continue-${if (tv) "tv" else "phone"}-row-dark-en")
            activate(tag, tv); awaitTag("resume_continue")
            captureSyntheticPreview(instrumentation, "continue-${if (tv) "tv" else "phone"}-prompt-dark-en")
            activate("resume_continue", tv)
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition in 7_000..16_000 }
            scenario.onActivity { player(it).pause() }; back(); awaitTag(tag)
            if (tv) awaitFocus(tag)
            // Reopen the Activity with a new VM; the separate-process probe supplies cold-PID evidence.
            scenario.close(); scenario = ActivityScenario.launch<Activity>(intent)
            compose.openDestination("nav_movies", tv); awaitTag(tag)
            assertEquals(0, fixture.providerRequests.get())
            activate(tag, tv); activate("resume_start_over", tv)
            awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition < 5_000 }
            scenario.onActivity { player(it).seekTo(29_500); player(it).play() }
            awaitPlayer(scenario) { it.playbackState == Player.STATE_ENDED }
            compose.waitUntil(10_000) { runBlocking { store.read(target)?.progress?.completed == true } }
            back(); awaitTag("channel_${fixture.movieId}")
            compose.waitUntil(5000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
            if (tv) awaitFocus("channel_${fixture.movieId}")
            // LIVE and generic M3U UNKNOWN remain direct playback with no bookmark or prompt.
            compose.openDestination("nav_live", tv)
            for (id in listOf(fixture.liveId, fixture.unknownId)) {
                activate("channel_$id", tv); awaitPlayer(scenario) { it.playbackState == Player.STATE_READY }
                scenario.onActivity { player(it).seekTo(6_000); player(it).pause() }; back(); awaitTag("channel_$id")
                compose.onNodeWithTag("resume_continue").assertDoesNotExist()
            }
            assertTrue(runBlocking { store.recent(fixture.sourceId, ContentKind.MOVIE).isEmpty() })
        } finally {
            scenario.close()
            instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, originalDark) }
        }
    }

    private fun actualEpisodes(activity: Class<out Activity>, tv: Boolean) = withFixture { fixture ->
        val originalDark = Appearance.isDark(instrumentation.targetContext)
        instrumentation.runOnMainSync {
            Appearance.setDark(instrumentation.targetContext, false)
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.targetContext.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags("ar")
            else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        }
        val target = ProgressTarget(fixture.sourceId, fixture.seriesId, ContentKind.EPISODE, "EPISODE:31")
        val intent = Intent(instrumentation.targetContext, activity)
        try { ActivityScenario.launch<Activity>(intent).use { scenario ->
                compose.openDestination("nav_series", tv)
                compose.onNodeWithTag("nav_series").assertTextContains("المسلسلات", substring = true)
                activate("channel_${fixture.seriesId}", tv); activate("detail_play", tv)
                awaitEpisode(0, "Episode A"); activate("channel_0", tv)
                awaitPlayer(scenario) { it.playbackState == Player.STATE_READY }
                scenario.onActivity { player(it).seekTo(10_000); player(it).pause() }
                awaitPosition(target, 9_000L..12_000L)
                back(); awaitTag("library_back"); activate("library_back", tv)
                val recent = runBlocking { app.progressRecorder.flush().await(); store.recent(fixture.sourceId, ContentKind.EPISODE).single() }
                val tag = "recent_${recent.reference.refDigest}"
                awaitTag(tag)
                exposeRecent(tag, tv)
                compose.onNodeWithTag("recent_time_${recent.reference.refDigest}", useUnmergedTree = true).assertIsDisplayed()
                compose.onNodeWithTag(tag).assertIsDisplayed()
                captureSyntheticPreview(instrumentation, "continue-${if (tv) "tv" else "phone"}-row-light-ar")
                activate(tag, tv); awaitTag("resume_continue")
                if (tv) awaitFocus("resume_continue")
                captureSyntheticPreview(instrumentation, "continue-${if (tv) "tv" else "phone"}-prompt-light-ar")
                activate("resume_cancel", tv); awaitTag(tag)
                assertEquals(1, fixture.providerRequests.get())
                activate(tag, tv); activate("resume_continue", tv)
                awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition in 9_000..16_000 }
                scenario.onActivity { player(it).pause() }; back(); awaitTag(tag)
                assertEquals(1, fixture.providerRequests.get())
                // A failed cached locator keeps its saved position and retries after recovery.
                fixture.failMedia.set(true)
                activate(tag, tv); activate("resume_continue", tv); awaitTag("playback_retry")
                fixture.failMedia.set(false); activate("playback_retry", tv)
                awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition >= 9_000 }
                scenario.onActivity { player(it).pause() }; back(); awaitTag(tag)
                fixture.reordered.set(true)
                activate("channel_${fixture.seriesId}", tv); activate("detail_play", tv)
                awaitEpisode(1, "Episode A"); activate("channel_1", tv)
                awaitTag("resume_continue")
                assertEquals(2, fixture.providerRequests.get())
                activate("resume_continue", tv)
                awaitPlayer(scenario) { it.playbackState == Player.STATE_READY && it.contentPosition >= 9_000 }
                scenario.onActivity { player(it).pause() }; back()
                assertNotNull(runBlocking { store.read(target) })
                assertEquals("Episode A", runBlocking { store.read(target)!!.snapshot!!.title })
        } } finally { instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, originalDark) } }
    }

    private fun player(activity: Activity): Player {
        fun find(view: View): PlayerView? = when (view) {
            is PlayerView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) }
            else -> null
        }
        return find(activity.window.decorView)?.player ?: error("Player not attached")
    }
    private fun awaitPlayer(scenario: ActivityScenario<Activity>, predicate: (Player) -> Boolean) {
        compose.waitUntil(15_000) {
            var ready = false
            scenario.onActivity { ready = runCatching { predicate(player(it)) }.getOrDefault(false) }
            ready
        }
    }
    private fun awaitPosition(target: ProgressTarget, range: LongRange) = compose.waitUntil(10_000) { runBlocking { store.read(target)?.progress?.positionMs?.let { it in range } == true } }
    private fun back() { instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) }

    private fun savedMovie(activity: Class<out Activity>, tv: Boolean) {
        val recent = runBlocking {
            val saved = store.save(SourceSpec.file(), listOf(
                PlaylistEntry(Channel(0, "Movie fixture", "Drama", ContentKind.MOVIE), MediaAddress.parse("https://example.invalid/movie")),
                PlaylistEntry(Channel(1, "Live fixture", null, ContentKind.LIVE), MediaAddress.parse("https://example.invalid/live"))))
            val target = ProgressTarget(saved.sourceId!!, saved.entries.first().channel.id, ContentKind.MOVIE)
            store.save(target, WatchProgress(12_000, 30_000, false, 100))
            store.recent(saved.sourceId!!, ContentKind.MOVIE).single()
        }
        ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, activity)).use {
            compose.openDestination("nav_movies", tv)
            val tag = "recent_${recent.reference.refDigest}"
            awaitTag(tag)
            if (tv) awaitFocus(tag)
            try { compose.onNodeWithTag("continue_watching").assertIsDisplayed() }
            catch (failure: AssertionError) { geometry("saved-row"); throw failure }
            assertEquals(1, compose.onNodeWithTag("catalogue_grid").fetchSemanticsNode().config[LoadedCatalogueItems])
            activate(tag, tv)
            awaitTag("resume_continue")
            if (tv) awaitFocus("resume_continue")
            compose.onNodeWithTag("resume_continue").assertTextContains("12", substring = true)
            activate("resume_cancel", tv)
            awaitTag(tag)
            compose.onNodeWithTag("resume_continue").assertDoesNotExist()
            compose.onNodeWithTag("player_surface").assertDoesNotExist()
            assertEquals(12_000L, runBlocking { store.read(recent.reference)!!.progress.positionMs })
            // Ordinary catalogue selection must also leave the row intact after Cancel.
            activate("channel_${recent.rootItemId}", tv)
            activate("detail_play", tv)
            awaitTag("resume_continue")
            activate("resume_cancel", tv)
            exposeRecent(tag, tv)
            compose.onNodeWithTag(tag).assertExists()
        }
    }
    private fun awaitTag(tag: String, enabled: Boolean = false) {
        val target = if (enabled) hasTestTag(tag) and isEnabled() else hasTestTag(tag)
        try {
            compose.waitUntil(10_000) { compose.onAllNodes(target).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for ${if (enabled) "enabled " else ""}test target $tag", failure)
        }
    }
    private fun geometry(stage: String) {
        for (tag in listOf("continue_watching", "continue_watching_row", "catalogue_grid", "nav_movies")) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().forEach {
                android.util.Log.i("ContinueGeometry", "$stage $tag bounds=${it.boundsInRoot} position=${it.positionInRoot}")
            }
        }
        captureSyntheticPreview(instrumentation, "continue-failure-$stage")
    }
    private fun awaitFocus(tag: String) = compose.waitUntil(5000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Focused) { false } }
    }
    private fun exposeRecent(tag: String, tv: Boolean) {
        // A late history reload can prepend the header after stable poster keys anchor the grid.
        compose.waitUntil(10_000) {
            compose.onNodeWithTag("catalogue_grid").performScrollToIndex(0)
            compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        if (tv) {
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
            awaitFocus(tag)
        }
    }
    private fun awaitEpisode(id: Int, title: String) {
        // Episode indices can match outgoing root IDs while the screen is changing.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("library_back").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasTestTag("channel_$id") and hasText(title) and isEnabled())
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun activate(tag: String, tv: Boolean) {
        // Lazy layouts may not compose a catalogue card below a tall history header yet.
        if (compose.onAllNodesWithTag("catalogue_grid").fetchSemanticsNodes().isNotEmpty()) {
            if (tag.startsWith("recent_")) compose.onNodeWithTag("catalogue_grid").performScrollToIndex(0)
            else if (tag.startsWith("channel_")) compose.onNodeWithTag("catalogue_grid").performScrollToNode(hasTestTag(tag))
        }
        awaitTag(tag, enabled = true)
        if (tv) {
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
            awaitFocus(tag)
            compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
        } else {
            // A recent card's closest scroll parent is horizontal; expose its outer header first.
            if (tag.startsWith("recent_")) compose.onNodeWithTag("catalogue_grid").performScrollToIndex(0)
            val node = compose.onNodeWithTag(tag)
            try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
            try { node.assertIsDisplayed().performClick() }
            catch (failure: AssertionError) { geometry("touch-target"); throw failure }
        }
    }
}
