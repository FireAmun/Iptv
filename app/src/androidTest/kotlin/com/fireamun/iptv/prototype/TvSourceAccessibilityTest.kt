package com.fireamun.iptv.prototype

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fireamun.iptv.core.model.*
import com.fireamun.iptv.ui.tv.TvWelcome
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class TvSourceAccessibilityTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createComposeRule()

    @Test fun longNamedRemovalKeepsBothActionsVisibleAtLargeTextScale() {
        val removed = AtomicReference<String?>()
        val longName = "Family playlist ".repeat(9).take(128)
        val state = LibraryState(channels = listOf(Channel(7, "Fixture", null)), hasSavedSource = true, sourceName = longName,
            sources = listOf(SavedSource("fixture-source", longName, SourceKind.FILE, 1)), activeSourceId = "fixture-source")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                TvWelcome(state = state, onChangeLanguage = {}, onImportFile = {}, onImportUrl = {}, onSelectChannel = {}, onCancelImport = {},
                    onApproveHttp = {}, onDismissHttp = {}, onChangeAppearance = {}, onServerLogin = { _, _, _, _ -> }, onLibraryBack = {},
                    onRefreshSource = {}, onRemoveSource = {}, onSelectSource = {}, onRenameSource = { _, _ -> }, onRemoveSourceById = { removed.set(it) })
            }
        }
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("manage_sources"))
        activate("manage_sources")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("source_manager").fetchSemanticsNodes().isNotEmpty() }
        // The dialog assigns its initial focus once its window is focused; act only after that, as a remote user would.
        // Requesting focus earlier races that assignment on slow devices and the dialog moves focus back to the pick row.
        compose.waitUntil(5000) { focused("source_pick_0") }
        compose.onNodeWithTag("source_manager").performScrollToNode(hasTestTag("source_remove_0"))
        activate("source_remove_0")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("remove_cancel").fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Focused) { false } } }
        compose.onNodeWithTag("remove_cancel").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("remove_confirm").assertIsDisplayed()
        compose.onNodeWithTag("remove_cancel").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("remove_confirm").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("fixture-source", removed.get()) }
    }
    private fun focused(tag: String) =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Focused) { false } }
    private fun activate(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(5000) { focused(tag) }
        compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
    }
}
