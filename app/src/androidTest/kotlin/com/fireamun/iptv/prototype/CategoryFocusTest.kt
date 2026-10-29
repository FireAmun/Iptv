package com.fireamun.iptv.prototype

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class CategoryFocusTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createComposeRule()

    @Test fun lateCategoryPageKeepsTheUsersNewChipFocus() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IptvApplication
        val channels = listOf(Channel(0, "News fixture", "News", ContentKind.LIVE),
            Channel(1, "Documentary fixture", "Documentaries", ContentKind.LIVE))
        val state = mutableStateOf(LibraryState(channels = channels, tab = LibraryTab.LIVE,
            categories = listOf("News", "Documentaries")))
        val pages = MutableStateFlow(PagingData.from(channels))
        compose.setContent {
            LibraryBrowser(state.value, pages.collectAsLazyPagingItems(), true, app.artworkLoader,
                onTab = {}, onQuery = {}, onCategory = { state.value = state.value.copy(category = it) },
                onPlay = {}, onFavourite = {}, onBack = {}, onCloseSearch = {}, settings = {})
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("channel_0").fetchSemanticsNodes().isNotEmpty() }
        activate("browse_categories")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("category_pick_1").fetchSemanticsNodes().isNotEmpty() }
        activate("category_pick_1")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("category_picker").fetchSemanticsNodes().isEmpty() }
        awaitFocus("browse_categories")

        // The picker has restored focus. The user moves on before the filtered
        // page arrives; a later Paging emission must not reclaim that focus.
        compose.onNodeWithTag("category_all").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("category_all")
        compose.runOnIdle { pages.value = PagingData.from(listOf(channels[1])) }
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("channel_1").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("channel_0").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("category_all").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("category_all").assertIsSelected()
        compose.onNodeWithTag("category_picker").assertDoesNotExist()
    }

    private fun awaitFocus(tag: String) = compose.waitUntil(5000) {
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
    }
    private fun activate(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus(tag)
        compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
    }
}
