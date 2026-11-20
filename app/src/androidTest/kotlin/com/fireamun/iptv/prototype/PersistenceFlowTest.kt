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
import org.junit.runner.RunWith
import android.app.Instrumentation
import android.net.Uri
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import org.junit.rules.ExternalResource

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PersistenceFlowTest {
    @get:Rule(order = 0) val diagnostics = TestDiagnostics()
    @get:Rule(order = 1) val locales = RestoreLocalesRule(diagnostics)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule(order = 2) val appearance = object : ExternalResource() {
        private var original = true
        override fun before() { original = Appearance.isDark(instrumentation.targetContext); instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, true) } }
        override fun after() { instrumentation.runOnMainSync { Appearance.setDark(instrumentation.targetContext, original) } }
    }
    @get:Rule(order = 3) val compose = createEmptyComposeRule()
    private val store get() = (instrumentation.targetContext.applicationContext as IptvApplication).libraryStore
    private fun seed(source: SourceSpec) = runBlocking { store.save(source, listOf(PlaylistEntry(Channel(0, "Cached fixture", null), MediaAddress.parse("https://example.invalid/clip")))) }

    @Test fun phoneRestoresFileLibraryAndConfirmedRemovalStaysRemoved() = exercise(MobileActivity::class.java, false, SourceSpec.file())
    @Test fun tvRestoresXtreamLibraryAndConfirmedRemovalStaysRemoved() = exercise(TvActivity::class.java, true,
        SourceSpec.xtream("Saved provider", XtreamConnection.create("https://example.invalid", "fixture-u", "fixture-p")!!))

    private fun exercise(activity: Class<out Activity>, tv: Boolean, source: SourceSpec) {
        seed(source)
        val intent = Intent(instrumentation.targetContext, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        repeat(2) { iteration ->
            ActivityScenario.launch<Activity>(intent).use { scenario ->
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Cached fixture").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Cached fixture").assertIsDisplayed()
                if (tv) awaitFocus("channel_0")
                if (iteration == 1) {
                    click("appearance_switch", tv)
                    compose.waitUntil(10_000) {
                        var light = false
                        scenario.onActivity { light = it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_NO }
                        light
                    }
                    if (tv) awaitFocus("language_switch")
                    click("language_switch", tv)
                    compose.waitUntil(10_000) {
                        var rtl = false
                        scenario.onActivity { rtl = it.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL }
                        rtl
                    }
                    if (tv) awaitFocus("language_switch")
                }
                compose.openDestination("nav_settings", tv)
                // The phone settings list is lazy: after the language switch it can restore a position where the
                // library card is not composed, so scroll the list to it rather than expecting the node to exist.
                if (tv) compose.onNodeWithTag("refresh_source").performScrollTo()
                else compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("refresh_source"))
                compose.waitForIdle()
                captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-saved-${if (iteration == 0) "dark-en" else "light-ar"}")
            }
        }
        ActivityScenario.launch<Activity>(intent).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Cached fixture").fetchSemanticsNodes().isNotEmpty() }
            if (tv) awaitFocus("channel_0")
            click("remove_source", tv)
            if (tv) awaitFocus("remove_cancel")
            compose.waitForIdle()
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-remove-light-ar")
            click("remove_cancel", tv)
            if (tv) awaitFocus("language_switch")
            assertNotNull(runBlocking { store.restore() })
            click("remove_source", tv)
            if (tv) awaitFocus("remove_cancel")
            click("remove_confirm", tv)
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Cached fixture").fetchSemanticsNodes().isEmpty() }
            compose.waitUntil(10000) { runBlocking { store.directory().sources.isEmpty() } }
            assertNull(runBlocking { store.restore() })
        }
        ActivityScenario.launch<Activity>(intent).use {
            compose.waitUntil(10_000) { !compose.onNodeWithTag("add_login").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
            compose.onNodeWithText("Cached fixture").assertDoesNotExist()
        }
    }
    @Test fun urlRefreshProceedsWithoutConsentAndFailedRefreshKeepsSavedCatalogue() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 503)); server.start()
            seed(SourceSpec.url(MediaAddress.parse(server.url("/playlist").toString())!!))
            ActivityScenario.launch<MobileActivity>(Intent(instrumentation.targetContext, MobileActivity::class.java)).use {
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Cached fixture").fetchSemanticsNodes().isNotEmpty() }
                click("refresh_source", false)
                compose.onNodeWithTag("http_confirm").assertDoesNotExist()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("import_error").fetchSemanticsNodes().isNotEmpty() }
                assertEquals("Cached fixture", runBlocking { store.restore() }!!.entries.single().channel.title)
                assertEquals(1, server.requestCount)
            }
        }
    }
    @Test fun fileRefreshUsesPickerAndPreservesItsSavedIdentity() {
        seed(SourceSpec.file())
        val originalId = runBlocking { store.restore() }!!.entries.single().channel.id
        val uri = Uri.parse("content://com.fireamun.iptv.prototype.test.fixtures/playlist")
        instrumentation.targetContext.contentResolver.openOutputStream(uri)!!.use { it.write("#EXTM3U\n#EXTINF:-1,Refreshed fixture\nhttps://example.invalid/clip\n".toByteArray()) }
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            ActivityScenario.launch<MobileActivity>(Intent(instrumentation.targetContext, MobileActivity::class.java)).use {
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Cached fixture").fetchSemanticsNodes().isNotEmpty() }
                click("refresh_source", false)
                compose.waitUntil(10000) { runBlocking { store.restore()?.entries?.single()?.channel?.title == "Refreshed fixture" } }
                compose.openDestination("nav_live", false)
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Refreshed fixture").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(originalId, runBlocking { store.restore() }!!.entries.single().channel.id)
            }
        } finally { Intents.release() }
    }
    private fun click(tag: String, tv: Boolean) {
        if (tag in listOf("appearance_switch", "language_switch", "refresh_source", "remove_source")) compose.openDestination("nav_settings", tv)
        if (!tv && tag in listOf("appearance_switch", "language_switch", "refresh_source", "remove_source")) compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag(tag))
        val node = compose.onNodeWithTag(tag)
        if (tv) {
            node.performSemanticsAction(SemanticsActions.RequestFocus); awaitFocus(tag)
            node.performKeyInput { pressKey(Key.DirectionCenter) }
        } else {
            if (tag.endsWith("_source") || tag.endsWith("_switch")) node.performScrollTo()
            node.assertIsDisplayed().performClick()
        }
    }
    private fun awaitFocus(tag: String) { compose.waitUntil(5000) { compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } } }
}
