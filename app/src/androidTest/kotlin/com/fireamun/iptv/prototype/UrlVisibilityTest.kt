package com.fireamun.iptv.prototype

import android.app.Activity
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class UrlVisibilityTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule(order = 2) val appearance = object : ExternalResource() {
        private var original = true
        override fun before() { original = Appearance.isDark(instrumentation.targetContext); instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, true) } }
        override fun after() { instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, original) } }
    }
    @get:Rule(order = 3) val compose = createEmptyComposeRule()

    @Test fun phoneCanInspectEditAndSubmitWithoutRetainingRevealedLinks() = exercise(false, false)
    @Test fun tvRemoteCanInspectEditAndSubmitWithoutRetainingRevealedLinks() = exercise(true, false)
    @Test fun phoneUrlVisibilityWorksInArabicLightMode() = exercise(false, true)
    @Test fun tvUrlVisibilityWorksInArabicLightMode() = exercise(true, true)

    private fun exercise(tv: Boolean, arabicLight: Boolean) = withSoftwareKeyboard {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse(body = "#EXTM3U\n#EXTINF:-1,URL visibility fixture\nhttps://example.invalid/live\n")) }
            server.start()
            val activity = if (tv) TvActivity::class.java else MobileActivity::class.java
            ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                await("library_list")
                if (tv) awaitFocus("language_switch")
                if (arabicLight) {
                    clickSettings("appearance_switch", tv)
                    compose.waitUntil(10_000) {
                        var light = false
                        scenario.onActivity { light = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO }
                        light
                    }
                    if (tv) awaitFocus("language_switch")
                    clickSettings("language_switch", tv)
                    compose.waitUntil(10_000) {
                        var rtl = false
                        scenario.onActivity { rtl = it.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL }
                        rtl
                    }
                    if (tv) awaitFocus("language_switch")
                }
                val first = server.url("/list.m3u?token=fixture%2Bfirst").toString()
                val edited = server.url("/list.m3u?token=edited%2Bfixture").toString()
                val password = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
                fun open() {
                    clickSettings("add_url", tv)
                    await("source_url")
                    compose.onNodeWithTag("source_url").assertIsDisplayed().assert(password)
                }
                fun toggle(expectedLabel: String) {
                    if (tv) {
                        compose.onNodeWithTag("source_url").performSemanticsAction(SemanticsActions.RequestFocus)
                        awaitFocus("source_url")
                        compose.onNodeWithTag("source_url").performKeyInput { pressKey(Key.DirectionUp) }
                        awaitFocus("url_visibility")
                        compose.onNodeWithTag("url_visibility").assertIsDisplayed().assertTextEquals(expectedLabel)
                            .performKeyInput { pressKey(Key.DirectionCenter) }
                        compose.onNodeWithTag("url_visibility").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
                        awaitFocus("source_url")
                    } else compose.onNodeWithTag("url_visibility").assertIsDisplayed().assertTextEquals(expectedLabel).performClick()
                }
                val show = if (arabicLight) "\u0625\u0638\u0647\u0627\u0631" else "Show"
                val hide = if (arabicLight) "\u0625\u062e\u0641\u0627\u0621" else "Hide"
                open()
                compose.onNodeWithTag("source_url").performTextInput(first)
                assertRenderedUrl("\u2022".repeat(first.length))
                toggle(show)
                compose.onNodeWithTag("source_url").assertTextEquals(first)
                assertRenderedUrl(first)
                captureSyntheticPreview(instrumentation, "url-${if (tv) "tv" else "phone"}-${if (arabicLight) "arabic-light" else "english-dark"}")
                if (tv) assertUrlAboveKeyboard()
                compose.onNodeWithTag("source_url").performTextReplacement(edited)
                toggle(hide)
                compose.onNodeWithTag("source_url").assert(password)
                assertRenderedUrl("\u2022".repeat(edited.length))
                toggle(show)
                compose.onNodeWithTag("source_url").assertTextEquals(edited)
                assertRenderedUrl(edited)
                activate("url_cancel", tv)
                compose.onNodeWithTag("source_url").assertDoesNotExist()
                assertEquals(0, server.requestCount)
                if (tv) awaitFocus("language_switch")
                open()
                compose.onNodeWithTag("source_url").assertTextEquals("")
                compose.onNodeWithTag("source_url").performTextInput(edited)
                toggle(show)
                compose.onNodeWithTag("source_url").assertTextEquals(edited)
                compose.onNodeWithTag("source_url").performImeAction()
                awaitImportAndOpenSettings(tv, 1)
                val revealedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("/list.m3u", revealedRequest.url.encodedPath)
                assertEquals("token=edited%2Bfixture", revealedRequest.url.encodedQuery)
                open()
                compose.onNodeWithTag("source_url").assertTextEquals("")
                compose.onNodeWithTag("source_url").performTextInput(first)
                compose.onNodeWithTag("source_url").assert(password)
                assertRenderedUrl("\u2022".repeat(first.length))
                activate("import_url", tv)
                awaitImportAndOpenSettings(tv, 2)
                val hiddenRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("/list.m3u", hiddenRequest.url.encodedPath)
                assertEquals("token=fixture%2Bfirst", hiddenRequest.url.encodedQuery)
                assertEquals(2, server.requestCount)
                diagnostics.phase(TestDiagnostics.Phase.BEFORE_SCENARIO_CLOSE)
            }
            diagnostics.phase(TestDiagnostics.Phase.AFTER_SCENARIO_CLOSE)
        }
    }

    private fun awaitImportAndOpenSettings(tv: Boolean, sourceCount: Int) {
        val app = instrumentation.targetContext.applicationContext as IptvApplication
        compose.waitUntil(20_000) { runBlocking { app.libraryStore.directory().sources.size == sourceCount } }
        await("nav_settings")
        compose.openDestination("nav_settings", tv)
        if (tv) awaitFocus("language_switch")
    }
    private fun assertRenderedUrl(expected: String) {
        // Password keyboard mode retains the sensitive-input semantics flag even while revealed.
        // Assert the actual layout text, which is what the field draws.
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("source_url").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue(action(layouts))
        }
        assertEquals(expected, layouts.single().layoutInput.text.text)
    }
    private fun withSoftwareKeyboard(block: () -> Unit) {
        val setting = "show_ime_with_hard_keyboard"
        val original = Settings.Secure.getString(instrumentation.targetContext.contentResolver, setting)
        require(original == null || original == "0" || original == "1")
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        try {
            shell("settings put secure $setting 1")
            assertEquals("1", Settings.Secure.getString(instrumentation.targetContext.contentResolver, setting))
            block()
        } finally {
            shell(if (original == null) "settings delete secure $setting" else "settings put secure $setting $original")
        }
    }
    private fun assertUrlAboveKeyboard() {
        // Native IME windows can cover a node that Compose still considers displayed.
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        val expectedSize = compose.onNodeWithTag("source_url").fetchSemanticsNode().size
        var field: Rect? = null
        var keyboard: Rect? = null
        fun editable(node: AccessibilityNodeInfo): Rect? {
            if (node.isEditable) return Rect().also(node::getBoundsInScreen)
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                editable(child)?.let { return it }
            }
            return null
        }
        try {
            info.flags = originalFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            compose.waitUntil(5000) {
                val windows = automation.windows
                keyboard = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                    ?.let { Rect().also(it::getBoundsInScreen) }
                field = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    .mapNotNull { it.root?.let(::editable) }.singleOrNull()
                val input = field
                val ime = keyboard
                input != null && input.width() >= expectedSize.width - 1 && input.height() >= expectedSize.height - 1 &&
                    ime != null && !ime.isEmpty && !Rect.intersects(input, ime)
            }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError("The complete URL field must be clear of the native keyboard: field=$field measured=$expectedSize keyboard=$keyboard", failure)
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }
    private fun await(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun clickSettings(tag: String, tv: Boolean) {
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag(tag))
        activate(tag, tv)
    }

    private fun awaitFocus(tag: String) = compose.waitUntil(5000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()
            ?.config?.getOrElse(SemanticsProperties.Focused) { false } == true
    }
    private fun activate(tag: String, tv: Boolean) {
        if (tv) {
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
            awaitFocus(tag)
            compose.onNodeWithTag(tag).assertIsDisplayed().performKeyInput { pressKey(Key.DirectionCenter) }
        } else compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }
}
