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

    @Test fun resetFailureKeepsChoicesAndLoadingDoesNotReclaimCancelFocus() {
        val state = mutableStateOf(ContinueWatchingState(prompt = ResumePrompt("Movie fixture", 12_000, 30_000)))
        var resets = 0
        var cancelled = false
        compose.setContent { ProgressOverlays(state.value, true, {}, { resets++; state.value = state.value.copy(error = ProgressProblem.RESET) }, { cancelled = true; state.value = state.value.copy(prompt = null) }, {}) }
        awaitFocus("resume_continue")
        compose.onNodeWithTag("resume_continue").performKeyInput { pressKey(Key.DirectionDown) }
        awaitFocus("resume_start_over")
        compose.onNodeWithTag("resume_start_over").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("progress_error").assertExists()
        compose.onNodeWithTag("resume_continue").assertExists()
        assertEquals(1, resets)
        compose.onNodeWithTag("resume_start_over").performKeyInput { pressKey(Key.DirectionDown) }
        awaitFocus("resume_cancel")
        compose.onNodeWithTag("resume_cancel").performKeyInput { pressKey(Key.DirectionUp) }
        awaitFocus("resume_start_over")
        compose.onNodeWithTag("resume_start_over").performKeyInput { pressKey(Key.DirectionDown) }
        awaitFocus("resume_cancel")
        compose.runOnIdle { state.value = state.value.copy(loading = true) }
        compose.onNodeWithTag("resume_continue").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(loading = false) }
        compose.onNodeWithTag("resume_cancel").assertIsFocused()
        compose.onNodeWithTag("resume_cancel").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("resume_dialog").assertDoesNotExist()
        assertTrue(cancelled)
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

    private fun awaitFocus(tag: String) = compose.waitUntil(5000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Focused) { false } }
    }
    private fun activate(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(tag)
        compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
    }
}
