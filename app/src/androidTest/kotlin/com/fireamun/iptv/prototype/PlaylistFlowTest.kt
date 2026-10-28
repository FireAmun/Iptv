package com.fireamun.iptv.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.media3.ui.PlayerView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaylistFlowTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun mobileImportsUrlAndPlaysThenReturnsToItsLibrary() = exerciseUrl(MobileActivity::class.java, false)
    @Test fun tvImportsUrlAndRemotePlaybackRestoresEntryFocus() = exerciseUrl(TvActivity::class.java, true)

    @Test fun pickedFileIsReadThroughItsGrantedContentUri() {
        val uri = Uri.parse("content://com.fireamun.iptv.prototype.test.fixtures/playlist")
        instrumentation.targetContext.contentResolver.openOutputStream(uri)!!.use {
            it.write("#EXTM3U\n#EXTINF:-1,File channel\nhttps://example.invalid/live\n".toByteArray())
        }
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)),
            )
            launch(MobileActivity::class.java).use { scenario ->
                compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("import_file"))
                compose.onNodeWithTag("import_file").performClick()
                Intents.intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
                waitForChannel()
                compose.onNodeWithText("File channel").assertIsDisplayed()
                scenario.recreate()
                compose.onNodeWithText("File channel").assertIsDisplayed()
                diagnostics.phase(TestDiagnostics.Phase.BEFORE_SCENARIO_CLOSE)
            }
            diagnostics.phase(TestDiagnostics.Phase.AFTER_SCENARIO_CLOSE)
        } finally { Intents.release() }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun exerciseUrl(activity: Class<out Activity>, tv: Boolean) {
        MockWebServer().use { server ->
            val clip = instrumentation.context.assets.open("synthetic-test.mp4").use { it.readBytes() }
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/playlist" -> MockResponse(body = "#EXTM3U\n#EXTINF:-1,Fixture channel\n${server.url("/clip.mp4")}\n")
                    "/clip.mp4" -> MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip)).build()
                    else -> MockResponse(code = 404)
                }
            }
            server.start()
            launch(activity).use { scenario ->
                try {
                    if (tv) {
                        compose.waitUntil(5000) { compose.onNodeWithTag("language_switch").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
                        compose.onNodeWithTag("add_url").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                        compose.waitUntil(5000) { compose.onNodeWithTag("add_url").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
                        compose.onNodeWithTag("add_url").assertIsFocused().performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
                    } else {
                        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("add_url"))
                        compose.onNodeWithTag("add_url").assertIsDisplayed().performClick()
                    }
                    compose.waitUntil(5000) { compose.onAllNodesWithTag("source_url").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("source_url").performTextInput(server.url("/playlist").toString())
                    if (tv) compose.onNodeWithTag("source_url").performImeAction()
                    else compose.onNodeWithTag("import_url").performClick()
                    compose.onNodeWithTag("http_confirm").assertDoesNotExist()
                    waitForChannel()
                    compose.waitUntil(timeoutMillis = 5000) {
                        var focused = false
                        scenario.onActivity { focused = it.hasWindowFocus() }
                        focused
                    }
                    val channel = compose.onNodeWithTag("channel_0")
                    if (tv) channel.assertIsFocused().performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
                    else channel.performClick()
                    compose.onNodeWithTag("http_confirm").assertDoesNotExist()
                    compose.waitUntil(timeoutMillis = 20_000) {
                        compose.onAllNodesWithText("Playing").fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.onNodeWithTag("playback_status").assertTextEquals("Playing")
                    scenario.onActivity { findPlayerView(it.window.decorView)!!.showController() }
                    onView(withId(androidx.media3.ui.R.id.exo_play_pause)).perform(click())
                    compose.waitUntil(5000) { compose.onAllNodesWithText("Paused").fetchSemanticsNodes().isNotEmpty() }
                    captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-player-paused")
                    onView(withId(androidx.media3.ui.R.id.exo_play_pause)).perform(click())
                    compose.waitUntil(5000) { compose.onAllNodesWithText("Playing").fetchSemanticsNodes().isNotEmpty() }
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.onActivity {
                        val playerView = findPlayerView(it.window.decorView)
                        org.junit.Assert.assertNull("Background playback must detach immediately", playerView?.player)
                    }
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    compose.waitUntil(timeoutMillis = 20_000) { compose.onAllNodesWithText("Playing").fetchSemanticsNodes().isNotEmpty() }
                    pressBack()
                    waitForChannel()
                    compose.onNodeWithText("Fixture channel").assertIsDisplayed()
                    if (tv) compose.onNodeWithTag("channel_0").assertIsFocused()
                } finally {
                    diagnostics.phase(TestDiagnostics.Phase.BEFORE_SCENARIO_CLOSE)
                }
            }
            diagnostics.phase(TestDiagnostics.Phase.AFTER_SCENARIO_CLOSE)
        }
    }

    private fun launch(activity: Class<out Activity>): ActivityScenario<Activity> = ActivityScenario.launch(
        Intent(instrumentation.targetContext, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    private fun waitForChannel() = compose.waitUntil(timeoutMillis = 20_000) {
        compose.onAllNodesWithTag("channel_0").fetchSemanticsNodes().isNotEmpty()
    }
    private fun findPlayerView(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findPlayerView(view.getChildAt(index))?.let { return it }
        return null
    }
}
