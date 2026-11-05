package com.fireamun.iptv.prototype

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ContinueWatchingPresentationTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createComposeRule()
    private var beforeNavigation = ""

    @Test fun resetFailureKeepsChoicesAndLoadingDoesNotReclaimCancelFocus() {
        val prompt = ResumePrompt("Movie fixture", 12_000, 30_000)
        val state = mutableStateOf(ContinueWatchingState(prompt = prompt))
        var resets = 0
        var cancelled = false
        compose.setContent { ProgressOverlays(state.value, true, {}, { resets++; state.value = state.value.copy(error = ProgressProblem.RESET) }, { cancelled = true; state.value = ContinueWatchingState() }, { state.value = ContinueWatchingState(prompt = prompt) }) }
        repeat(2) { round ->
            if (round > 0) {
                compose.runOnIdle { cancelled = false; state.value = ContinueWatchingState(error = ProgressProblem.READ) }
                compose.onNodeWithTag("progress_dismiss").performTouchInput { click() }
            }
            awaitFocus("resume_continue")
            beforeNavigation = focusDiagnostics("before-down-$round")
            compose.onNodeWithTag("resume_continue").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocus("resume_start_over")
            compose.onNodeWithTag("resume_start_over").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithTag("progress_error").assertExists()
            compose.onNodeWithTag("resume_continue").assertExists()
            assertEquals(round + 1, resets)
            compose.onNodeWithTag("resume_start_over").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocus("resume_cancel")
            compose.onNodeWithTag("resume_cancel").performKeyInput { pressKey(Key.DirectionUp) }
            awaitFocus("resume_start_over")
            compose.onNodeWithTag("resume_start_over").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocus("resume_cancel")
            compose.runOnIdle { state.value = state.value.copy(loading = true) }
            compose.onNodeWithTag("resume_continue").assertIsNotEnabled()
            compose.onNodeWithTag("resume_cancel").performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithTag("resume_cancel").assertIsFocused()
            compose.runOnIdle { state.value = state.value.copy(loading = false) }
            compose.onNodeWithTag("resume_cancel").assertIsFocused()
            compose.onNodeWithTag("resume_cancel").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithTag("resume_dialog").assertDoesNotExist()
            assertTrue(cancelled)
        }
    }

    @Test fun readAndSaveFailuresCanBeDismissedWithoutStartingPlayback() {
        val state = mutableStateOf(ContinueWatchingState(error = ProgressProblem.READ))
        var dismissals = 0
        compose.setContent { ProgressOverlays(state.value, true, { error("Unexpected playback") }, { error("Unexpected reset") }, {}, { dismissals++; state.value = state.value.copy(error = null) }) }
        compose.onNodeWithTag("progress_error").assertTextContains("select the item again", substring = true)
        activate("progress_dismiss")
        compose.onNodeWithTag("progress_error").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(error = ProgressProblem.SAVE) }
        compose.onNodeWithTag("progress_error").assertTextContains("previous saved position", substring = true)
        activate("progress_dismiss")
        compose.onNodeWithTag("progress_error").assertDoesNotExist()
        assertEquals(2, dismissals)
    }

    private fun focusDiagnostics(stage: String): String {
        val tags = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config.getOrElse(SemanticsProperties.TestTag) { "untagged" } }
        var windows = emptyList<String>()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                windows = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .map { "${it.javaClass.simpleName}:type=${(it.layoutParams as? android.view.WindowManager.LayoutParams)?.type}:focused=${it.hasWindowFocus()}:touch=${it.isInTouchMode}" }
            }
        }
        return "IPTV_RESUME_FOCUS $stage tags=$tags windows=$windows"
    }
    private fun dialogHasNativeFocus(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return true
        var focused = false
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            focused = android.view.inspector.WindowInspector.getGlobalWindowViews().any {
                val params = it.layoutParams as? android.view.WindowManager.LayoutParams
                params?.type == android.view.WindowManager.LayoutParams.TYPE_APPLICATION && it.hasWindowFocus()
            }
        }
        return focused
    }
    private fun awaitFocus(tag: String) {
        try {
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Focused) { false } } && dialogHasNativeFocus()
            }
        } catch (failure: ComposeTimeoutException) {
            println(beforeNavigation)
            println(focusDiagnostics("timeout-$tag"))
            captureSyntheticPreview(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(), "continue-dialog-focus-failure")
            throw failure
        }
    }
    private fun activate(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(tag)
        compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
    }
}
