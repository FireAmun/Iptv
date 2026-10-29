package com.fireamun.iptv.prototype

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
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
import org.junit.rules.ExternalResource

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class XtreamFlowTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule(order = 2) val appearance = object : ExternalResource() {
        private var original = true
        override fun before() { original = Appearance.isDark(instrumentation.targetContext); instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, true) } }
        override fun after() { instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, original) } }
    }
    @get:Rule(order = 3) val compose = createEmptyComposeRule()
    @Test fun mobileServerLoginLoadsCataloguesAndSeriesPlayback() = exercise(MobileActivity::class.java, false)
    @Test fun tvServerLoginLoadsCataloguesAndSeriesPlayback() = exercise(TvActivity::class.java, true)
    @Test fun mobileAccountLinkFallbackRestoresAndPlaysSeries() = exercise(MobileActivity::class.java, false, true)
    @Test fun tvAccountLinkFallbackRestoresAndPlaysSeries() = exercise(TvActivity::class.java, true, true)
    @Test fun mobileArabicLightLoginCanBeCancelled() = preview(MobileActivity::class.java, false)
    @Test fun tvArabicLightLoginCanBeCancelled() = preview(TvActivity::class.java, true)

    private fun exercise(activity: Class<out Activity>, tv: Boolean, accountLink: Boolean = false) {
        val clip = instrumentation.context.assets.open("synthetic-test.mp4").use { it.readBytes() }
        MockWebServer().use { server ->
            val closedConnection = java.util.concurrent.atomic.AtomicBoolean()
            val categoryAttempts = java.util.concurrent.CopyOnWriteArrayList<RecordedRequest>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.url.encodedPath == "/get.php") return MockResponse(code = 404)
                    if (request.url.encodedPath.startsWith("/series/")) return MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(clip)).build()
                    if (request.url.queryParameter("password") == "wrong") return MockResponse(body = """{"user_info":{"auth":0}}""")
                    if (request.url.queryParameter("action") == "get_series_categories") {
                        categoryAttempts.add(request)
                        if (closedConnection.compareAndSet(false, true)) return MockResponse.Builder().onResponseStart(mockwebserver3.SocketEffect.CloseSocket()).build()
                    }
                    return MockResponse(body = when (request.url.queryParameter("action")) {
                        null -> """{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["ts"]}}"""
                        "get_live_streams" -> """[{"stream_id":10,"name":"Live fixture"}]"""
                        "get_vod_streams" -> """[{"stream_id":20,"name":"Film fixture","container_extension":"mp4"}]"""
                        "get_series" -> """[{"series_id":30,"name":"Series fixture"}]"""
                        "get_series_info" -> """{"episodes":{"1":[{"id":31,"title":"Episode fixture","episode_num":1,"container_extension":"mp4"}]}}"""
                        else -> "[]"
                    })
                }
            }
            server.start()
            val intent = Intent(instrumentation.targetContext, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            var scenario = ActivityScenario.launch<Activity>(intent)
            try {
                if (tv) awaitFocus("language_switch")
                snapshot(if (tv) "tv-home-dark-en" else "phone-home-dark-en")
                if (accountLink) {
                    click("add_url", tv)
                    compose.waitUntil(5000) { compose.onAllNodesWithTag("source_url").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("source_url").performTextInput(server.url("/get.php?username=fixture-user&password=fixture-password&type=m3u&output=ts").toString())
                    compose.onNodeWithTag("source_url").performImeAction()
                } else {
                    login(server.url("/").toString(), "wrong", tv)
                    compose.onNodeWithTag("http_confirm").assertDoesNotExist()
                    compose.waitUntil(10_000) { compose.onAllNodesWithTag("import_error").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithText("Login failed. Check your username and password.").assertIsDisplayed()
                    org.junit.Assert.assertEquals(1, server.requestCount)
                    login(server.url("/").toString(), "fixture-password", tv)
                }
                compose.onNodeWithTag("http_confirm").assertDoesNotExist()
                compose.waitUntil(20_000) { compose.onAllNodesWithText("Live fixture").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Live fixture").assertExists()
                compose.onNodeWithText("Film fixture").assertDoesNotExist()
                compose.openDestination("nav_movies", tv)
                compose.waitUntil(10000) { compose.onAllNodesWithText("Film fixture").fetchSemanticsNodes().isNotEmpty() }
                compose.openDestination("nav_live", tv)
                compose.onNodeWithTag("server_password").assertDoesNotExist()
                org.junit.Assert.assertEquals(2, categoryAttempts.size)
                org.junit.Assert.assertTrue(categoryAttempts[0].exchangeIndex > 0)
                org.junit.Assert.assertNotEquals(categoryAttempts[0].connectionIndex, categoryAttempts[1].connectionIndex)
                if (accountLink) {
                    org.junit.Assert.assertEquals(9, server.requestCount)
                    scenario.close()
                    scenario = ActivityScenario.launch<Activity>(intent)
                    compose.waitUntil(20_000) { compose.onAllNodesWithText("Live fixture").fetchSemanticsNodes().isNotEmpty() }
                    org.junit.Assert.assertEquals(9, server.requestCount)
                }
                if (tv) awaitFocus("channel_0")
                if (accountLink) {
                    compose.openDestination("nav_search", tv)
                    compose.onNodeWithTag("catalogue_search").performTextInput("Ser")
                } else compose.openDestination("nav_series", tv)
                compose.waitUntil(10000) { compose.onAllNodesWithTag("channel_2").fetchSemanticsNodes().isNotEmpty() }
                if (tv && !accountLink) awaitFocus("channel_2")
                click("channel_2", tv)
                click("detail_play", tv)
                compose.onNodeWithTag("http_confirm").assertDoesNotExist()
                compose.waitUntil(20_000) { compose.onAllNodesWithText("Episode fixture").fetchSemanticsNodes().isNotEmpty() }
                if (tv) awaitFocus("channel_0")
                click("channel_0", tv)
                compose.onNodeWithTag("http_confirm").assertDoesNotExist()
                compose.waitUntil(20_000) { compose.onAllNodesWithText("Playing").fetchSemanticsNodes().isNotEmpty() }
                pressBack()
                compose.onNodeWithText("Episode fixture").assertIsDisplayed()
                pressBack()
                compose.waitUntil(5000) { compose.onAllNodesWithText("Series fixture").fetchSemanticsNodes().isNotEmpty() }
                if (tv) awaitFocus("channel_2")
            } finally { scenario.close() }
        }
    }
    private fun login(server: String, password: String, tv: Boolean) {
        click("add_login", tv)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("playlist_name").fetchSemanticsNodes().isNotEmpty() }
        if (password == "wrong") snapshot(if (tv) "tv-login-dark-en" else "phone-login-dark-en")
        // Auto-pumping stalls Material field focus on the TV image. Step frames, but
        // wait for focus-driven scrolling and Android IME/layout before entering text.
        val autoAdvance = compose.mainClock.autoAdvance
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("playlist_name").performSemanticsAction(SemanticsActions.RequestFocus)
            compose.mainClock.advanceTimeBy(100)
            for ((tag, value) in listOf("playlist_name" to "Fixture playlist", "server_url" to server,
                "server_username" to "fixture-user", "server_password" to password)) {
                val field = compose.onNodeWithTag(tag)
                compose.waitUntil(5000) {
                    compose.mainClock.advanceTimeByFrame()
                    field.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } &&
                        field.isDisplayed()
                }
                field.assertIsFocused().assertIsDisplayed().performTextInput(value)
                compose.mainClock.advanceTimeBy(100)
                field.performImeAction()
                compose.mainClock.advanceTimeBy(100)
            }
        } finally { compose.mainClock.autoAdvance = autoAdvance }
    }
    private fun preview(activity: Class<out Activity>, tv: Boolean) {
        ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
            if (tv) awaitFocus("language_switch")
            click("appearance_switch", tv)
            compose.waitUntil(10_000) {
                var light = false
                scenario.onActivity { light = it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_NO }
                light
            }
            if (tv) awaitFocus("language_switch")
            click("language_switch", tv)
            compose.waitUntil(10_000) {
                var rtl = false
                scenario.onActivity { rtl = it.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL }
                rtl
            }
            compose.waitForIdle()
            if (tv) awaitFocus("language_switch")
            snapshot(if (tv) "tv-home-light-ar" else "phone-home-light-ar")
            click("add_login", tv)
            compose.onNodeWithTag("playlist_name").assertExists()
            compose.onNodeWithTag("submit_login").assertIsNotEnabled()
            snapshot(if (tv) "tv-login-light-ar" else "phone-login-light-ar")
            click("login_cancel", tv)
            compose.onNodeWithTag("server_password").assertDoesNotExist()
            compose.onNodeWithTag("add_login").assertExists()
        }
    }
    private fun snapshot(name: String) {
        // Synthetic test screens only. Files survive Gradle's APK uninstall for CI review.
        compose.waitForIdle()
        captureSyntheticPreview(instrumentation, name)
    }
    private fun click(tag: String, tv: Boolean) {
        if (!tv && tag in listOf("appearance_switch", "language_switch", "add_login", "add_url", "import_file")) {
            compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag(tag))
        }
        if (tv) {
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
            awaitFocus(tag)
            compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
        } else compose.onNodeWithTag(tag).performClick()
    }
    private fun awaitFocus(tag: String) { compose.waitUntil(5000) { compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } } }
}
