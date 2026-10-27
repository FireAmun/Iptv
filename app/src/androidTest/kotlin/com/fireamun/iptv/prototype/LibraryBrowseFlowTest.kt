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
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class LibraryBrowseFlowTest {
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
    @Test fun phoneBrowsesPagesSearchesArabicAndPersistsFavourites() = exercise(MobileActivity::class.java, false)
    @Test fun tvRemoteBrowsesPagesSearchesArabicAndPersistsFavourites() = exercise(TvActivity::class.java, true)

    private fun exercise(activity: Class<out Activity>, tv: Boolean) {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val index = request.url.encodedPath.substringAfterLast('/').toIntOrNull() ?: 1
                return MockResponse.Builder().addHeader("Content-Type", "image/png").body(Buffer().write(syntheticPoster(index))).build()
            }
        }
        server.start()
        try {
        val entries = listOf(PlaylistEntry(Channel(0, "أَهْلًا 1 HD", "News", ContentKind.LIVE), MediaAddress.parse("https://example.invalid/live"))) +
            (1..200).map { PlaylistEntry(Channel(it, "Cinema $it", "Drama", ContentKind.MOVIE, year = 2024, rating = 8.1), MediaAddress.parse("https://example.invalid/movie/$it"), providerKey = "MOVIE:$it", artwork = MediaAddress.parse(server.url("/poster/$it").toString())) } +
            PlaylistEntry(Channel(201, "Series fixture", "Stories", ContentKind.SERIES), null, 201, "SERIES:201")
        val saved = runBlocking { store.save(SourceSpec.file(), entries) }
        ActivityScenario.launch<Activity>(Intent(instrumentation.targetContext, activity)).use { scenario ->
            awaitTag("channel_0")
            assertTrue("Search belongs above the bottom navigation", compose.onNodeWithTag("nav_search").fetchSemanticsNode().boundsInRoot.bottom < compose.onNodeWithTag("nav_live").fetchSemanticsNode().boundsInRoot.top)
            for (tag in listOf("add_url", "add_login", "import_file", "manage_sources", "refresh_source", "remove_source")) compose.onNodeWithTag(tag).assertDoesNotExist()
            if (tv) awaitFocus("channel_0")
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-live-dark-en")
            activate("favourite_0", tv)
            compose.openDestination("nav_favourites", tv)
            awaitTag("channel_0")
            compose.onNodeWithTag("favourite_0").assertContentDescriptionEquals("Remove from favourites")
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-favourites-dark-en")
            compose.openDestination("nav_movies", tv)
            awaitTag("channel_1")
            compose.waitUntil(10000) { compose.onNodeWithTag("artwork_1", useUnmergedTree = true).fetchSemanticsNode().config[ArtworkLoaded] }
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-movies-dark-en")
            compose.onNodeWithTag("catalogue_grid").performScrollToIndex(50)
            compose.waitUntil(10000) { compose.onNodeWithTag("catalogue_grid").fetchSemanticsNode().config[LoadedCatalogueItems] >= 120 }
            compose.onNodeWithTag("catalogue_grid").performScrollToIndex(110)
            compose.waitUntil(10000) { compose.onNodeWithTag("catalogue_grid").fetchSemanticsNode().config[LoadedCatalogueItems] >= 180 }
            compose.onNodeWithTag("catalogue_grid").performScrollToIndex(150)
            awaitTag("channel_151")
            compose.onNodeWithTag("channel_151").assertIsDisplayed()
            compose.openDestination("nav_series", tv); awaitTag("channel_201")
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-series-dark-en")
            compose.openDestination("nav_search", tv)
            compose.onNodeWithTag("catalogue_search").performTextInput("اهلا 1")
            compose.onNodeWithTag("catalogue_search").performImeAction()
            awaitTag("channel_0")
            compose.waitUntil(10000) { compose.onAllNodesWithTag("channel_1").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("channel_1").assertDoesNotExist()
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-search-dark-en")
            compose.openDestination("nav_settings", tv)
            compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("refresh_source"))
            compose.onNodeWithTag("refresh_source").assertIsDisplayed()
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-settings-dark-en")
            compose.onNodeWithTag("channel_0").assertDoesNotExist()
            scenario.recreate()
            compose.openDestination("nav_favourites", tv); awaitTag("channel_0")
            val session = runBlocking { store.openSession()!! }
            assertEquals(saved.sourceId, session.sourceId)
            assertTrue(runBlocking { store.page(BrowseRequest(session.sourceId!!, session.generation, favouritesOnly = true), 0, 60).items.single().favourite })
            compose.onNodeWithTag("favourite_0").assertContentDescriptionEquals("Remove from favourites")
            activate("favourite_0", tv)
            compose.waitUntil(10000) { runBlocking { store.page(BrowseRequest(session.sourceId!!, session.generation, favouritesOnly = true), 0, 60).items.isEmpty() } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("Save something you love").fetchSemanticsNodes().isNotEmpty() }
            compose.openDestination("nav_settings", tv)
            compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("appearance_switch"))
            activate("appearance_switch", tv)
            compose.waitUntil(10000) { var light = false; scenario.onActivity { light = it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_NO }; light }
            if (tv) awaitFocus("language_switch")
            activate("language_switch", tv)
            compose.waitUntil(10000) { var rtl = false; scenario.onActivity { rtl = it.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL }; rtl }
            if (tv) awaitFocus("language_switch")
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-settings-light-ar")
            for ((tab, item) in listOf("live" to "0", "movies" to "1", "series" to "201")) {
                compose.openDestination("nav_$tab", tv)
                if (tab == "movies") {
                    awaitTag("catalogue_grid")
                    compose.onNodeWithTag("catalogue_grid").performScrollToIndex(0)
                }
                awaitTag("channel_$item")
                captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-$tab-light-ar")
            }
            compose.openDestination("nav_favourites", tv)
            compose.waitUntil(10000) { compose.onAllNodesWithText("احفظ ما تحب مشاهدته").fetchSemanticsNodes().isNotEmpty() }
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-favourites-light-ar")
            compose.openDestination("nav_search", tv)
            compose.onNodeWithTag("catalogue_search").performTextInput("اهلا 1")
            compose.onNodeWithTag("catalogue_search").performImeAction()
            awaitTag("channel_0")
            compose.waitUntil(10000) { compose.onAllNodesWithTag("channel_1").fetchSemanticsNodes().isEmpty() }
            captureSyntheticPreview(instrumentation, "${if (tv) "tv" else "phone"}-search-light-ar")
        }
        } finally { server.close() }
    }
    private fun awaitTag(tag: String) = compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitFocus(tag: String) = compose.waitUntil(5000) { compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false } }
    private fun activate(tag: String, tv: Boolean) {
        if (tv) {
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus); awaitFocus(tag)
            compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
        } else compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
    }
}
