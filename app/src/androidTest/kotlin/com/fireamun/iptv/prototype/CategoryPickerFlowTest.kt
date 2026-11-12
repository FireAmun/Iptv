package com.fireamun.iptv.prototype

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.data.PlaylistEntry
import com.fireamun.iptv.core.data.SourceSpec
import com.fireamun.iptv.core.model.Channel
import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.MediaAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class CategoryPickerFlowTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun phonePickerFiltersScrollsAndRetainsChipActions() = exercise(MobileActivity::class.java, false)
    @Test fun tvPickerRestoresRemoteFocusAndKeepsSelectionOnCancel() = exercise(TvActivity::class.java, true)

    private fun exercise(activity: Class<out Activity>, tv: Boolean) {
        val app = instrumentation.targetContext.applicationContext as IptvApplication
        val labels = listOf("News", "Documentaries") + (0..39).map { "Group ${it.toString().padStart(2, '0')}" } + "all"
        runBlocking {
            app.libraryStore.save(SourceSpec.file(), labels.mapIndexed { index, group ->
                PlaylistEntry(Channel(index, "Synthetic item $index", group, ContentKind.LIVE), MediaAddress.parse("https://example.invalid/$index"))
            })
        }
        ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, activity)).use {
            await("channel_0")
            compose.onNodeWithTag("browse_categories").assertExists()
            assertTrue("Settings must follow Favourites", compose.onNodeWithTag("nav_settings").fetchSemanticsNode().boundsInRoot.left > compose.onNodeWithTag("nav_favourites").fetchSemanticsNode().boundsInRoot.left)
            compose.onNodeWithTag("category_all").assertExists()
            activate("browse_categories", tv)
            await("category_picker")
            if (tv) {
                // Dialog semantics can exist before native window focus assigns its initial control.
                compose.waitUntil(5000) {
                    compose.onAllNodesWithTag("category_picker_close").fetchSemanticsNodes().singleOrNull()
                        ?.config?.getOrElse(SemanticsProperties.Focused) { false } == true
                }
                compose.onNodeWithTag("category_picker_close").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
                compose.onNodeWithTag("category_search").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
                val focusedTags = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
                    .map { node -> node.config.getOrElse(SemanticsProperties.TestTag) { "untagged" } }
                assertTrue("Down from search must focus All categories; focused controls: $focusedTags",
                    compose.onNodeWithTag("category_pick_all").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false })
            }
            compose.onNodeWithTag("category_search").performTextInput("all")
            compose.onNodeWithTag("category_pick_all").assertExists()
            compose.onNodeWithTag("category_pick_0").assertTextEquals("all").assertIsDisplayed()
            compose.onNodeWithTag("category_search").performTextClearance()
            compose.onNodeWithTag("category_search").performTextInput("dOc")
            compose.onNode(hasText("Documentaries") and hasAnyAncestor(hasTestTag("category_picker"))).assertIsDisplayed()
            compose.onNode(hasText("News") and hasAnyAncestor(hasTestTag("category_picker"))).assertDoesNotExist()
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-category-filter")
            activateCategory("Documentaries", tv)
            await("channel_1")
            if (tv) awaitPickerFocus()
            compose.onNodeWithTag("category_picker").assertDoesNotExist()
            compose.onNodeWithTag("channel_0").assertDoesNotExist()
            activate("browse_categories", tv)
            compose.onNodeWithTag("category_search").performTextInput("no-synthetic-match")
            compose.onNodeWithTag("category_picker_empty").assertIsDisplayed()
            if (tv) awaitNativeKeyboardShown()
            compose.onNodeWithTag("category_search").performImeAction()
            if (tv) {
                compose.onNodeWithTag("category_picker_close").performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
                awaitNativeBackReady()
                compose.onNodeWithTag("category_picker_close").assertIsFocused()
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            } else activate("category_picker_close", false)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("category_picker").fetchSemanticsNodes().isEmpty() }
            await("channel_1")
            if (tv) compose.waitUntil(5000) { compose.onNodeWithTag("browse_categories").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
            activate("browse_categories", tv)
            compose.onNodeWithTag("category_picker_list").performScrollToNode(hasText("Group 39"))
            activateCategory("Group 39", tv)
            await("channel_41")
            if (tv) awaitPickerFocus()
            compose.onNodeWithTag("category_all").assertExists()
            activate("category_all", tv)
            compose.onNodeWithTag("category_all").assertIsSelected()
            compose.onNodeWithTag("catalogue_list").performScrollToIndex(0)
            await("channel_0")
        }
    }
    private fun awaitNativeKeyboardShown() {
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        val pickerView = (compose.onNodeWithTag("category_picker").fetchSemanticsNode().root as ViewRootForTest).view
        fun ready(): Boolean {
            var nativeReady = false
            instrumentation.runOnMainSync {
                val input = pickerView.context.getSystemService(InputMethodManager::class.java)
                val insets = ViewCompat.getRootWindowInsets(pickerView)
                nativeReady = pickerView.hasWindowFocus() && input.isAcceptingText &&
                    insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            return nativeReady && automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }
        try {
            info.flags = originalFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            // Synthetic text and empty-result semantics can precede the native IME show.
            // Observe that transition before hiding it; an initially hidden IME is not a hide ACK.
            compose.waitUntil(5000) { ready() }
            automation.waitForIdle(100, 5000)
            assertTrue("The native picker editor and keyboard must be shown before its IME action", ready())
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }
    private fun awaitNativeBackReady() {
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        val pickerView = (compose.onNodeWithTag("category_picker").fetchSemanticsNode().root as ViewRootForTest).view
        fun ready(): Boolean {
            var nativeReady = false
            instrumentation.runOnMainSync {
                val input = pickerView.context.getSystemService(InputMethodManager::class.java)
                val insets = ViewCompat.getRootWindowInsets(pickerView)
                nativeReady = pickerView.hasWindowFocus() && !input.isAcceptingText &&
                    insets?.isVisible(WindowInsetsCompat.Type.ime()) == false
            }
            val windows = automation.windows
            return nativeReady && windows.any { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused } &&
                windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }
        try {
            info.flags = originalFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            // Compose focus and absent accessibility IME windows can precede the queued
            // native input-session shutdown. Wait for the actual picker root and editor too.
            compose.waitUntil(5000) { ready() }
            automation.waitForIdle(100, 5000)
            assertTrue("Native picker focus, a closed editor and a hidden keyboard are required before dialog Back", ready())
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }
    private fun await(tag: String) = compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitPickerFocus() = compose.waitUntil(5000) { compose.onNodeWithTag("browse_categories").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
    private fun activateCategory(label: String, tv: Boolean) {
        val node = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("category_picker")))
        if (tv) activateRemote(node)
        else node.performScrollTo().assertIsDisplayed().performClick()
    }
    private fun activate(tag: String, tv: Boolean) {
        val node = compose.onNodeWithTag(tag)
        if (tv) activateRemote(node)
        else { if (tag == "category_all") node.performScrollTo(); node.assertIsDisplayed().performClick() }
    }
    private fun activateRemote(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(5000) { node.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
        node.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
    }
}
