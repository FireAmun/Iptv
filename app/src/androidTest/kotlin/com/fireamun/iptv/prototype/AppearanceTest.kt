package com.fireamun.iptv.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class AppearanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    // Restore after Compose disposes, with no live Activity to recreate during teardown.
    @get:Rule(order = 1) val restore = object : ExternalResource() {
        private var original = true
        override fun before() { original = Appearance.isDark(instrumentation.targetContext) }
        override fun after() { instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, original) } }
    }
    @get:Rule(order = 2) val compose = createEmptyComposeRule()

    @Test fun mobileAppearanceRetainsLibraryAndSavedChoice() = exercise(MobileActivity::class.java, false)
    @Test fun tvAppearanceRetainsLibraryAndSavedChoice() = exercise(TvActivity::class.java, true)

    private fun exercise(activity: Class<out Activity>, tv: Boolean) {
        val intent = Intent(instrumentation.targetContext, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val original = Appearance.isDark(instrumentation.targetContext)
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            awaitMode(scenario, original)
            if (tv) {
                compose.waitUntil(5000) { compose.onNodeWithTag("language_switch").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
                var rtl = false
                scenario.onActivity { rtl = it.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL }
                compose.onNodeWithTag("language_switch").performKeyInput { pressKey(if (rtl) Key.DirectionRight else Key.DirectionLeft) }
                compose.onNodeWithTag("appearance_switch").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
            } else compose.onNodeWithTag("appearance_switch").performClick()
            awaitMode(scenario, !original)
            assertSaved(!original)
            scenario.recreate()
            awaitMode(scenario, !original)
        }
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            awaitMode(scenario, !original)
            val uri = Uri.parse("content://com.fireamun.iptv.prototype.test.fixtures/playlist")
            instrumentation.targetContext.contentResolver.openOutputStream(uri)!!.use { it.write("#EXTM3U\n#EXTINF:-1,Appearance fixture\nhttps://example.invalid/live\n".toByteArray()) }
            Intents.init()
            try {
                Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
                if (tv) {
                    compose.onNodeWithTag("import_file").performSemanticsAction(SemanticsActions.RequestFocus)
                    compose.onNodeWithTag("import_file").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
                } else compose.onNodeWithTag("import_file").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("channel_0").fetchSemanticsNodes().isNotEmpty() }
            } finally { Intents.release() }
            if (tv) {
                compose.waitUntil(5000) { compose.onNodeWithTag("channel_0").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
                compose.onNodeWithTag("appearance_switch").performSemanticsAction(SemanticsActions.RequestFocus)
                compose.waitUntil(5000) { compose.onNodeWithTag("appearance_switch").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
                compose.onNodeWithTag("appearance_switch").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
            } else compose.onNodeWithTag("appearance_switch").performClick()
            awaitMode(scenario, original)
            assertSaved(original)
            compose.onNodeWithText("Appearance fixture").assertIsDisplayed()
            scenario.recreate()
            awaitMode(scenario, original)
            compose.onNodeWithText("Appearance fixture").assertIsDisplayed()
        }
    }
    private fun awaitMode(scenario: ActivityScenario<Activity>, dark: Boolean) {
        val expected = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        compose.waitUntil(10_000) {
            var current = 0
            scenario.onActivity { current = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK }
            current == expected
        }
        compose.waitForIdle()
    }
    private fun assertSaved(dark: Boolean) {
        assertEquals(dark, Appearance.isDark(instrumentation.targetContext))
    }
}
