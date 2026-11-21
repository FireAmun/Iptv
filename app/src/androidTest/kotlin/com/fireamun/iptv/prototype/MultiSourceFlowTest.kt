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
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.runBlocking
import mockwebserver3.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class MultiSourceFlowTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val store get() = (instrumentation.targetContext.applicationContext as IptvApplication).libraryStore
    @get:Rule(order = 2) val appearance = object : ExternalResource() {
        private var original = true
        override fun before() { original = Appearance.isDark(instrumentation.targetContext); instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, true) } }
        override fun after() { instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, original) } }
    }
    @get:Rule(order = 3) val compose = createEmptyComposeRule()
    private fun entries(title: String) = listOf(PlaylistEntry(Channel(0, title, null), MediaAddress.parse("https://example.invalid/clip")))

    @Test fun phoneCanSwitchRenameAndRemoveOnePlaylistWithoutLosingAnother() = exercise(MobileActivity::class.java, false)
    @Test fun tvRemoteCanSwitchRenameAndRemoveOnePlaylistWithoutLosingAnother() = exercise(TvActivity::class.java, true)

    private fun exercise(activity: Class<out Activity>, tv: Boolean) {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(body = """{"user_info":{"auth":0}}""")); server.start()
            val (first, second) = runBlocking {
                val a = store.save(SourceSpec.xtream("Family", XtreamConnection.create(server.url("/").toString(), "fixture-user", "fixture-password")!!), entries("First cached fixture"))
                val b = store.save(SourceSpec.file(), entries("Second cached fixture"))
                store.rename(b.sourceId!!, "Sports")
                a to b
            }
            ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                awaitText("Second cached fixture", tv)
                openManager(tv)
                if (tv) awaitFocus("source_pick_1")
                compose.onNodeWithTag("source_pick_1").assertTextContains("Sports").assertIsDisplayed()
                captureSyntheticPreview(instrumentation, if (tv) "tv-playlists-dark-en" else "phone-playlists-dark-en")
                click("source_pick_0", tv)
                awaitText("First cached fixture", tv)
                assertEquals(first.sourceId, runBlocking { store.directory().activeSourceId })
                compose.onNodeWithTag("http_confirm").assertDoesNotExist()
                openManager(tv); click("source_rename_0", tv)
                compose.onNodeWithTag("source_name").performTextReplacement("Family renamed")
                compose.onNodeWithTag("rename_source_confirm").assertIsEnabled()
                compose.onNodeWithTag("source_name").performImeAction()
                compose.waitUntil(10_000) { runBlocking { store.directory().sources.first().name == "Family renamed" } }
                click("appearance_switch", tv)
                compose.waitUntil(10_000) {
                    var light = false
                    scenario.onActivity { light = it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_NO }
                    light
                }
                click("language_switch", tv)
                compose.waitUntil(10_000) {
                    var rtl = false
                    scenario.onActivity { rtl = it.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL }
                    rtl
                }
                awaitText("First cached fixture", tv)
                openManager(tv)
                compose.onNodeWithTag("source_pick_0").assertTextContains("Family renamed").assertIsDisplayed()
                captureSyntheticPreview(instrumentation, if (tv) "tv-playlists-light-ar" else "phone-playlists-light-ar")
                click("source_remove_0", tv)
                if (tv) awaitFocus("remove_cancel")
                click("remove_cancel", tv)
                if (tv) awaitFocus("source_pick_0")
                assertEquals(2, runBlocking { store.directory().sources.size })
                click("source_remove_0", tv); click("remove_confirm", tv)
                awaitText("Second cached fixture", tv)
                assertEquals(second.sourceId, runBlocking { store.directory().activeSourceId })
                assertEquals("Sports", runBlocking { store.directory().sources.single().name })
                scenario.recreate(); awaitText("Second cached fixture", tv)
                assertEquals(second.sourceId, runBlocking { store.restore()!!.sourceId })
                assertEquals(0, server.requestCount)
            }
        }
    }
    private fun awaitText(text: String, tv: Boolean) { compose.openDestination("nav_live", tv); compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun openManager(tv: Boolean) {
        click("manage_sources", tv)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("source_manager").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(tag: String, tv: Boolean) {
        if (tag in listOf("manage_sources", "appearance_switch", "language_switch")) compose.openDestination("nav_settings", tv)
        if (tag.matches(Regex("source_(pick|rename|remove)_\\d+"))) compose.onNodeWithTag("source_manager").performScrollToNode(hasTestTag(tag))
        else if (tag in listOf("manage_sources", "appearance_switch", "language_switch")) compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag(tag))
        val node = compose.onNodeWithTag(tag)
        if (tv) {
            node.performSemanticsAction(SemanticsActions.RequestFocus); awaitFocus(tag)
            node.performKeyInput { pressKey(Key.DirectionCenter) }
        } else node.assertIsDisplayed().performClick()
    }
    // TV Settings dialogs return focus after scrolling back to the header; a lazy item can be composed but not placed
    // meanwhile, so a missing node means "not focused yet".
    private fun awaitFocus(tag: String) { compose.waitUntil(5000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { it.config.getOrElse(SemanticsProperties.Focused) { false } } } }
}
