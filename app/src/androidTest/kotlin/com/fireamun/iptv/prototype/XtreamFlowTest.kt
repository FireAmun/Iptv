package com.fireamun.iptv.prototype

import android.accessibilityservice.AccessibilityServiceInfo
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
    @Test fun tvLoginKeepsCurrentFieldAfterAnotherWindowCloses() {
        ActivityScenario.launch<TvActivity>(Intent(instrumentation.targetContext, TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
            awaitFocus("language_switch")
            click("add_login", true)
            awaitFocus("playlist_name")
            val autoAdvance = compose.mainClock.autoAdvance
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithTag("server_url").performSemanticsAction(SemanticsActions.RequestFocus)
                compose.mainClock.advanceTimeBy(500)
                compose.onNodeWithTag("server_url").assertIsFocused()
                var overlay: Activity? = null
                try {
                    scenario.onActivity { it.startActivity(Intent(it, androidx.activity.ComponentActivity::class.java)) }
                    compose.waitUntil(5000) {
                        instrumentation.runOnMainSync {
                            overlay = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                                .firstOrNull { it.javaClass == androidx.activity.ComponentActivity::class.java }
                        }
                        overlay != null
                    }
                    compose.mainClock.advanceTimeBy(100)
                } finally {
                    instrumentation.runOnMainSync {
                        val remaining = overlay ?: androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                            .firstOrNull { it.javaClass == androidx.activity.ComponentActivity::class.java }
                        remaining?.finish()
                    }
                }
                compose.waitUntil(5000) { scenario.state == androidx.lifecycle.Lifecycle.State.RESUMED }
                instrumentation.uiAutomation.waitForIdle(100, 5000)
                compose.mainClock.advanceTimeBy(500)
                compose.onNodeWithTag("server_url").assertIsFocused()
            } finally { compose.mainClock.autoAdvance = autoAdvance }
        }
    }

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
            instrumentation.uiAutomation.waitForIdle(100, 5000)
            for ((tag, value) in listOf("playlist_name" to "Fixture playlist", "server_url" to server,
                "server_username" to "fixture-user", "server_password" to password)) {
                val field = compose.onNodeWithTag(tag)
                try {
                    var previousGeometry: List<Any>? = null
                    compose.waitUntil(5000) {
                        compose.mainClock.advanceTimeByFrame()
                        // Native IME layout can change while Compose relocation is paused.
                        // Keep both moving until a fully visible field stays in place.
                        try {
                            instrumentation.uiAutomation.waitForIdle(100, 250)
                        } catch (_: java.util.concurrent.TimeoutException) {
                            previousGeometry = null
                            return@waitUntil false
                        }
                        compose.mainClock.advanceTimeByFrame()
                        val node = field.fetchSemanticsNode()
                        val bounds = node.boundsInRoot
                        val viewport = compose.onNodeWithTag(if (tv) "server_login_fields" else "mobile_form_scroll").fetchSemanticsNode()
                        val scroll = viewport.config[SemanticsProperties.VerticalScrollAxisRange]
                        val geometry = listOf(bounds, node.positionInRoot, node.size, viewport.boundsInRoot,
                            scroll.value(), scroll.maxValue())
                        val ready = node.config.getOrElse(SemanticsProperties.Focused) { false } &&
                            field.isDisplayed() && bounds.width >= node.size.width - 1f && bounds.height >= node.size.height - 1f
                        val stable = geometry == previousGeometry
                        previousGeometry = if (ready) geometry else null
                        ready && stable
                    }
                    field.assertIsFocused().assertIsDisplayed().performTextInput(value)
                    compose.mainClock.advanceTimeBy(100)
                    field.performImeAction()
                    compose.mainClock.advanceTimeBy(100)
                    // The virtual clock does not drive native IME/window layout.
                    instrumentation.uiAutomation.waitForIdle(100, 5000)
                } catch (failure: Throwable) {
                    if (failure is ComposeTimeoutException || failure is AssertionError) {
                        // Native geometry can change after the poll, before the visibility assertion.
                        runCatching { loginDiagnostics(tag) }
                        runCatching { captureSyntheticPreview(instrumentation, if (tv) "tv-login-failure" else "phone-login-failure") }
                    }
                    throw failure
                }
            }
        } finally { compose.mainClock.autoAdvance = autoAdvance }
    }
    private fun loginDiagnostics(expected: String) {
        // Only geometry/known tags from synthetic fixtures; never dump text/semantics trees.
        println("IPTV_LOGIN_STATE expected=$expected virtual=${compose.mainClock.currentTime}")
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        try {
            info.flags = originalFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            for (window in automation.windows) {
                val bounds = android.graphics.Rect()
                window.getBoundsInScreen(bounds)
                println("IPTV_LOGIN_WINDOW type=${window.type} focused=${window.isFocused} bounds=$bounds")
            }
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
        for (tag in listOf("playlist_name", "server_url", "server_username", "server_password", "server_login_fields", "mobile_form_scroll")) {
            val node = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull() ?: continue
            val focused = node.config.getOrElse(SemanticsProperties.Focused) { false }
            val range = if (node.config.contains(SemanticsProperties.VerticalScrollAxisRange)) node.config[SemanticsProperties.VerticalScrollAxisRange] else null
            println("IPTV_LOGIN_FIELD tag=$tag focused=$focused visible=${compose.onNodeWithTag(tag).isDisplayed()} bounds=${node.boundsInRoot} position=${node.positionInRoot} size=${node.size} scroll=${range?.value?.invoke()}/${range?.maxValue?.invoke()}")
        }
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
            if (tv) awaitFocus("language_switch")
            compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("add_login"))
            compose.onNodeWithTag("add_login").assertIsDisplayed()
        }
    }
    private fun snapshot(name: String) {
        // Synthetic test screens only. Files survive Gradle's APK uninstall for CI review.
        compose.waitForIdle()
        captureSyntheticPreview(instrumentation, name)
    }
    private fun click(tag: String, tv: Boolean) {
        if (tag in listOf("appearance_switch", "language_switch", "add_login", "add_url", "import_file")) {
            compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag(tag))
        }
        if (tv) {
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
            awaitFocus(tag)
            compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
        } else {
            // Dialog actions share the stable scroll container with its fields.
            if (tag == "login_cancel") compose.onNodeWithTag(tag).performScrollTo()
            compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
        }
    }
    private fun awaitFocus(tag: String) {
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()
                ?.config?.getOrElse(SemanticsProperties.Focused) { false } == true
        }
    }
}
