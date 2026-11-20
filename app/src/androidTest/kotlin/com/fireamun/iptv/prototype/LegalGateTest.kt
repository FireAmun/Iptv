package com.fireamun.iptv.prototype

import android.app.Activity
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class LegalGateTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** Other instrumented flows expect the runner's accepted agreement. */
    @After fun restoreAcceptance() = LegalConsent.accept(context, analytics = false)

    @Test fun mobileGateBlocksSourcesUntilAgreementAndKeepsStatisticsSeparate() {
        LegalConsent.reset(context)
        launch(MobileActivity::class.java).use {
            waitFor("legal_gate")
            assertTrue(compose.onAllNodesWithTag("library_list").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("legal_continue").assertIsNotEnabled()
            compose.onNodeWithTag("legal_read_privacy").performClick()
            waitFor("legal_document")
            compose.onNodeWithTag("legal_document_list").performScrollToNode(hasText("Anonymous usage statistics"))
            compose.onNodeWithTag("legal_document_close").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("legal_document").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("legal_analytics").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
                .performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            compose.onNodeWithTag("legal_agree").performClick()
            compose.onNodeWithTag("legal_continue").assertIsEnabled().performClick()
            waitFor("library_list")
            assertTrue(LegalConsent.termsAccepted(context))
            assertFalse(LegalConsent.analyticsEnabled(context))
            assertFalse(LegalConsent.analyticsAllowed(context))
        }
    }

    @Test fun mobileDeclineClosesTheAppWithoutAccepting() {
        LegalConsent.reset(context)
        launch(MobileActivity::class.java).use { scenario ->
            waitFor("legal_gate")
            compose.onNodeWithTag("legal_decline").performClick()
            val deadline = System.currentTimeMillis() + 10_000
            while (scenario.state != Lifecycle.State.DESTROYED && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            assertFalse(LegalConsent.termsAccepted(context))
        }
    }

    @Test fun tvGateAndDocumentsWorkWithTheRemoteOnly() {
        LegalConsent.reset(context)
        launch(TvActivity::class.java).use {
            waitFor("legal_gate")
            compose.waitUntil(5000) { focused("legal_read_terms") }
            compose.onNodeWithTag("legal_read_terms").performKeyInput { pressKey(Key.DirectionCenter) }
            waitFor("legal_document")
            compose.waitUntil(5000) { focused("legal_document_list") }
            compose.onNodeWithTag("legal_document_list").performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitUntil(5000) {
                val list = compose.onNodeWithTag("legal_document_list").fetchSemanticsNode().config
                SemanticsProperties.VerticalScrollAxisRange in list && list[SemanticsProperties.VerticalScrollAxisRange].value() > 0f
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("legal_document").fetchSemanticsNodes().isEmpty() }
            activate("legal_agree")
            compose.onNodeWithTag("legal_agree").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            activate("legal_continue")
            waitFor("library_list")
            assertTrue(LegalConsent.termsAccepted(context))
            assertTrue(LegalConsent.analyticsAllowed(context))
        }
    }

    @Test fun aboutSectionsOpenDocumentsAndChangeStatistics() {
        LegalConsent.accept(context, analytics = true)
        for (activity in listOf(MobileActivity::class.java, TvActivity::class.java)) launch(activity).use {
            waitFor("library_list")
            compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("about_analytics"))
            compose.onNodeWithTag("about_version").assertTextEquals("Version ${BuildConfig.VERSION_NAME}")
            compose.onNodeWithTag("about_analytics").performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(5000) { !LegalConsent.analyticsEnabled(context) }
            compose.onNodeWithTag("about_analytics").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            compose.onNodeWithTag("about_notices").performSemanticsAction(SemanticsActions.OnClick)
            waitFor("legal_document")
            compose.onNodeWithTag("legal_document_list").performScrollToNode(hasText("Apache License 2.0"))
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("legal_document").fetchSemanticsNodes().isEmpty() }
            LegalConsent.setAnalytics(context, true)
        }
    }

    private fun launch(activity: Class<out Activity>): ActivityScenario<Activity> =
        ActivityScenario.launch(Intent(context, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun focused(tag: String) =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Focused) { false } }

    private fun activate(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(5000) { focused(tag) }
        compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
    }
}
