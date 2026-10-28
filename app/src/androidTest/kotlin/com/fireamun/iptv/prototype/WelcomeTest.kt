package com.fireamun.iptv.prototype

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()
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
                if (!tv) reveal(hasTestTag("language_switch"))
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText("العربية").fetchSemanticsNodes().isNotEmpty()
                }
                val switch = compose.onNodeWithTag("language_switch")
                compose.onNodeWithTag("app_brand").assertTextEquals("Amar Iptv")
                assertEquals("Amar Iptv", instrumentation.targetContext.applicationInfo.loadLabel(instrumentation.targetContext.packageManager).toString())
                if (tv) {
                    compose.waitUntil(timeoutMillis = 5000) {
                        compose.onAllNodesWithTag("language_switch").fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Focused) { false } }
                    }
                    switch.assertIsFocused().performKeyInput {
                        keyDown(Key.DirectionCenter)
                        keyUp(Key.DirectionCenter)
                    }
                } else {
                    switch.performClick()
                }
                if (!tv) reveal(hasText(arabicTitle))
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText(arabicTitle).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(arabicTitle).assertIsDisplayed()
                scenario.onActivity {
                    assertEquals(View.LAYOUT_DIRECTION_RTL, it.resources.configuration.layoutDirection)
                }
                scenario.recreate()
                if (!tv) reveal(hasText(arabicTitle))
                compose.onNodeWithTag("app_brand").assertTextEquals("Amar Iptv")
                compose.onNodeWithText(arabicTitle).assertIsDisplayed()
                if (!tv) reveal(hasTestTag("language_switch"))
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
                if (!tv) reveal(hasTestTag("language_switch"))
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText("العربية").fetchSemanticsNodes().isNotEmpty()
                }
                scenario.onActivity {
                    assertEquals(View.LAYOUT_DIRECTION_LTR, it.resources.configuration.layoutDirection)
                }
                scenario.recreate()
                if (!tv) reveal(hasTestTag("language_switch"))
                compose.onNodeWithTag("language_switch").assertTextEquals("العربية")
            } finally {
                diagnostics.phase(TestDiagnostics.Phase.BEFORE_SCENARIO_CLOSE)
            }
        }
        diagnostics.phase(TestDiagnostics.Phase.AFTER_SCENARIO_CLOSE)
    }

    private fun reveal(matcher: SemanticsMatcher) {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("library_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("library_list").performScrollToNode(matcher)
    }

}
