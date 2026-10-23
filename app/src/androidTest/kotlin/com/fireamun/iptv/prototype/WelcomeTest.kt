package com.fireamun.iptv.prototype

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun mobileLanguageSelectionUpdatesDirectionAndSurvivesRecreation() {
        exerciseLanguageSwitch(MobileActivity::class.java, "مكتبتك على شاشتك", tv = false)
    }

    @Test fun tvRemoteCanSwitchLanguageAndKeepItAfterRecreation() {
        exerciseLanguageSwitch(TvActivity::class.java, "مكتبتك على الشاشة الكبيرة", tv = true)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun exerciseLanguageSwitch(activity: Class<out Activity>, arabicTitle: String, tv: Boolean) {
        val intent = Intent(instrumentation.targetContext, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            try {
                // AppCompat needs a live delegate to access LocaleManager on API 33+.
                scenario.onActivity { setLocale("en") }
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText("العربية").fetchSemanticsNodes().isNotEmpty()
                }
                val switch = compose.onNodeWithTag("language_switch")
                if (tv) {
                    switch.assertIsFocused().performKeyInput {
                        keyDown(Key.DirectionCenter)
                        keyUp(Key.DirectionCenter)
                    }
                } else {
                    switch.performClick()
                }
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText(arabicTitle).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(arabicTitle).assertIsDisplayed()
                scenario.onActivity {
                    assertEquals(View.LAYOUT_DIRECTION_RTL, it.resources.configuration.layoutDirection)
                }
                scenario.recreate()
                compose.onNodeWithText(arabicTitle).assertIsDisplayed()
                compose.onNodeWithTag("language_switch").assertTextEquals("English")
                if (tv) compose.onNodeWithTag("language_switch").assertIsFocused()
                if (tv) {
                    compose.onNodeWithTag("language_switch").performKeyInput {
                        keyDown(Key.DirectionCenter)
                        keyUp(Key.DirectionCenter)
                    }
                } else {
                    compose.onNodeWithTag("language_switch").performClick()
                }
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText("العربية").fetchSemanticsNodes().isNotEmpty()
                }
                scenario.onActivity {
                    assertEquals(View.LAYOUT_DIRECTION_LTR, it.resources.configuration.layoutDirection)
                }
                scenario.recreate()
                compose.onNodeWithTag("language_switch").assertTextEquals("العربية")
            } finally {
                scenario.onActivity { setLocale("") }
                compose.waitForIdle()
            }
        }
    }

    private fun setLocale(tags: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
    }
}
