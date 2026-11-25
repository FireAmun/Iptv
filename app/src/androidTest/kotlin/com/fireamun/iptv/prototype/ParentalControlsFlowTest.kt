package com.fireamun.iptv.prototype

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.data.PlaylistEntry
import com.fireamun.iptv.core.data.SourceSpec
import com.fireamun.iptv.core.model.Channel
import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.MediaAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ParentalControlsFlowTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun phoneLockedCategoryHidesUntilThePinIsEntered() = exercise(MobileActivity::class.java, false)
    @Test fun tvLockedCategoryHidesUntilThePinIsEnteredWithTheRemote() = exercise(TvActivity::class.java, true)

    private fun exercise(activity: Class<out Activity>, tv: Boolean) {
        val app = instrumentation.targetContext.applicationContext as IptvApplication
        runBlocking {
            app.libraryStore.save(SourceSpec.file(), listOf("Kids", "Night").mapIndexed { index, group ->
                PlaylistEntry(Channel(index, "Synthetic $group", group, ContentKind.LIVE), MediaAddress.parse("https://example.invalid/$index"))
            })
        }
        ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, activity)).use {
            await("channel_1")
            compose.openDestination("nav_settings", tv)
            showParental()
            activate("parental_set_pin", tv)
            enterPin("2468", tv)
            await("pin_title")
            enterPin("2468", tv)
            awaitGone("pin_dialog")
            showParental()
            activate("parental_lock_live", tv)
            await("category_picker")
            activate("category_lock_1", tv)
            compose.waitUntil(5000) { compose.onNodeWithTag("category_lock_1").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected) { false } }
            activate("category_picker_close", tv)
            awaitGone("category_picker")
            assertTrue(app.parentalControls.locks(currentSource(app)).blocks(ContentKind.LIVE, "Night"))
            showParental()
            activate("parental_lock_now", tv)
            compose.waitUntil(5000) { app.parentalControls.restricted }

            compose.openDestination("nav_live", tv)
            await("channel_0")
            awaitGone("channel_1")
            val chip = compose.onNode(hasText("Night", substring = true) and hasClickAction() and !hasAnyAncestor(hasTestTag("category_picker")))
            chip.assertTextContains("Locked", substring = true)
            if (tv) activateRemote(chip) else chip.performClick()
            await("pin_dialog")
            enterPin("1111", tv)
            await("pin_message")
            compose.onNodeWithTag("channel_1").assertDoesNotExist()
            enterPin("2468", tv)
            awaitGone("pin_dialog")
            await("channel_1")
            assertFalse(app.parentalControls.restricted)
        }
    }

    private fun currentSource(app: IptvApplication): String = runBlocking { app.libraryStore.directory().activeSourceId!! }
    private fun showParental() {
        compose.onNodeWithTag("library_list").performScrollToKey("parental")
        await("parental_settings")
    }
    private fun enterPin(pin: String, tv: Boolean) {
        await("pin_key_ok")
        pin.forEach { activate("pin_key_$it", tv) }
        activate("pin_key_ok", tv)
    }
    private fun await(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    private fun activate(tag: String, tv: Boolean) {
        await(tag)
        val node = compose.onNodeWithTag(tag)
        if (tv) activateRemote(node) else { runCatching { node.performScrollTo() }; node.performClick() }
    }
    private fun activateRemote(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(5000) { node.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
        node.performKeyInput { pressKey(Key.DirectionCenter) }
    }
}
