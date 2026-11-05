package com.fireamun.iptv.prototype

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.fireamun.iptv.core.data.*
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

    @Test fun lateHistoryHeaderIsVisibleAtTheInitialPhoneGridPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as IptvApplication
        val channels = listOf(Channel(0, "Movie fixture", null, ContentKind.MOVIE))
        val pages = MutableStateFlow(PagingData.from(channels))
        val watch = mutableStateOf(ContinueWatchingState("fixture", ContentKind.MOVIE, loading = true))
        compose.setContent {
            LibraryBrowser(LibraryState(channels = channels, tab = LibraryTab.MOVIES, activeSourceId = "fixture"), pages.collectAsLazyPagingItems(), false, app.artworkLoader,
                onTab = {}, onQuery = {}, onCategory = {}, onPlay = {}, onFavourite = {}, onBack = {}, onCloseSearch = {}, watch = watch.value, settings = {})
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("channel_0").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { watch.value = watch.value.copy(loading = false, items = listOf(recent(0))) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("continue_watching").fetchSemanticsNodes().isNotEmpty() }
        captureSyntheticPreview(instrumentation, "continue-phone-late-header-diagnostic")
        compose.onNodeWithTag("continue_watching").assertIsDisplayed()
    }

    @Test fun lateHistoryPreservesThePhonesDeliberateTouchScroll() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IptvApplication
        val channels = (0..39).map { Channel(it, "Movie $it", null, ContentKind.MOVIE) }
        val pages = MutableStateFlow(PagingData.from(channels))
        val watch = mutableStateOf(ContinueWatchingState("fixture", ContentKind.MOVIE, loading = true))
        compose.setContent {
            LibraryBrowser(LibraryState(channels = channels, tab = LibraryTab.MOVIES, activeSourceId = "fixture"), pages.collectAsLazyPagingItems(), false, app.artworkLoader,
                onTab = {}, onQuery = {}, onCategory = {}, onPlay = {}, onFavourite = {}, onBack = {}, onCloseSearch = {}, watch = watch.value, settings = {})
        }
        compose.onNodeWithTag("catalogue_grid").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val anchor = channels.first { channel ->
            compose.onAllNodesWithTag("channel_${channel.id}").fetchSemanticsNodes().any { it.boundsInRoot.height > 40 && it.boundsInRoot.width > 0 }
        }
        val tag = "channel_${anchor.id}"
        val before = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y
        compose.runOnIdle { watch.value = watch.value.copy(loading = false, items = listOf(recent(0))) }
        compose.waitForIdle()
        org.junit.Assert.assertEquals(before, compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y, 1f)
        compose.runOnIdle { watch.value = watch.value.copy(loading = true) }
        compose.runOnIdle { watch.value = watch.value.copy(loading = false) }
        org.junit.Assert.assertEquals(before, compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y, 1f)
    }

    @Test fun historyHeaderWorksBeforePagingAndLatePagesKeepNavigationAndChipFocus() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IptvApplication
        val channels = listOf(Channel(0, "Movie fixture", "Drama", ContentKind.MOVIE))
        val state = mutableStateOf(LibraryState(channels = channels, tab = LibraryTab.MOVIES, activeSourceId = "fixture", categories = listOf("Drama")))
        val pages = MutableStateFlow(PagingData.empty<Channel>())
        val watch = mutableStateOf(ContinueWatchingState("fixture", ContentKind.MOVIE, listOf(recent(0))))
        compose.setContent {
            LibraryBrowser(state.value, pages.collectAsLazyPagingItems(), true, app.artworkLoader,
                onTab = {}, onQuery = {}, onCategory = {}, onPlay = {}, onFavourite = {}, onBack = {}, onCloseSearch = {}, watch = watch.value, settings = {})
        }
        awaitFocus("recent_${recent(0).reference.refDigest}")
        compose.onNodeWithTag("catalogue_grid").assert(SemanticsMatcher.expectValue(LoadedCatalogueItems, 0))
        compose.onNodeWithTag("nav_search").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("nav_search")
        compose.runOnIdle { pages.value = PagingData.from(channels) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("channel_0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_search").assertIsFocused()
        compose.onNodeWithTag("catalogue_grid").assert(SemanticsMatcher.expectValue(LoadedCatalogueItems, 1))
        compose.onNodeWithTag("category_all").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("category_all")
        compose.runOnIdle { watch.value = watch.value.copy(items = listOf(recent(1), recent(0))); pages.value = PagingData.from(channels) }
        compose.waitForIdle()
        compose.onNodeWithTag("category_all").assertIsFocused()
    }

    @Test fun pendingHistoryDoesNotStealTheUsersLaterBottomNavigationFocus() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IptvApplication
        val channels = listOf(Channel(0, "Movie fixture", null, ContentKind.MOVIE))
        val pages = MutableStateFlow(PagingData.from(channels))
        val watch = mutableStateOf(ContinueWatchingState("fixture", ContentKind.MOVIE, loading = true))
        compose.setContent {
            LibraryBrowser(LibraryState(channels = channels, tab = LibraryTab.MOVIES, activeSourceId = "fixture"), pages.collectAsLazyPagingItems(), true, app.artworkLoader,
                onTab = {}, onQuery = {}, onCategory = {}, onPlay = {}, onFavourite = {}, onBack = {}, onCloseSearch = {}, watch = watch.value, settings = {})
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("channel_0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_series").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("nav_series")
        compose.runOnIdle { watch.value = watch.value.copy(loading = false, items = listOf(recent(0))) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("continue_watching").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_series").assertIsFocused()
    }

    @Test fun outgoingMoviePageWaitsForLiveDataAndPreservesLaterNavigation() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IptvApplication
        val live = Channel(0, "Live fixture", null, ContentKind.LIVE)
        val movie = Channel(1, "Movie fixture", null, ContentKind.MOVIE)
        val series = Channel(2, "Series fixture", null, ContentKind.SERIES)
        val state = mutableStateOf(LibraryState(channels = listOf(live, movie, series), tab = LibraryTab.LIVE, activeSourceId = "fixture"))
        val pages = MutableStateFlow(PagingData.from(listOf(movie)))
        compose.setContent {
            LibraryBrowser(state.value, pages.collectAsLazyPagingItems(), true, app.artworkLoader,
                onTab = {}, onQuery = {}, onCategory = {}, onPlay = {}, onFavourite = {}, onBack = {}, onCloseSearch = {},
                watch = ContinueWatchingState("fixture", ContentKind.EPISODE), settings = {})
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("channel_1").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.runOnIdle { pages.value = PagingData.from(listOf(live)) }
        awaitFocus("channel_0")

        // Another tab change is pending, but a deliberate navigation choice owns focus.
        compose.runOnIdle { pages.value = PagingData.from(listOf(movie)); state.value = state.value.copy(tab = LibraryTab.SERIES) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("channel_1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_settings").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("nav_settings")
        compose.runOnIdle { pages.value = PagingData.from(listOf(series)) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("channel_2").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("nav_settings").assertIsFocused()
    }

    @Test fun pendingHistoryPreservesTheUsersCatalogueFavouriteFocus() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IptvApplication
        val channels = listOf(Channel(0, "Movie fixture", null, ContentKind.MOVIE))
        val pages = MutableStateFlow(PagingData.from(channels))
        val watch = mutableStateOf(ContinueWatchingState("fixture", ContentKind.MOVIE, loading = true))
        compose.setContent {
            LibraryBrowser(LibraryState(channels = channels, tab = LibraryTab.MOVIES, activeSourceId = "fixture"), pages.collectAsLazyPagingItems(), true, app.artworkLoader,
                onTab = {}, onQuery = {}, onCategory = {}, onPlay = {}, onFavourite = {}, onBack = {}, onCloseSearch = {}, watch = watch.value, settings = {})
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("favourite_0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("favourite_0").performSemanticsAction(SemanticsActions.RequestFocus)
        awaitFocus("favourite_0")
        compose.runOnIdle { watch.value = watch.value.copy(loading = false, items = listOf(recent(0))) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("continue_watching").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("favourite_0").assertIsFocused()
    }

    @Test fun returningToMovedHistoryScrollsBothContainersAndCompletedItemUsesLoadedParent() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IptvApplication
        val channels = (0..24).map { Channel(it, "Movie $it", null, ContentKind.MOVIE) }
        val pages = MutableStateFlow(PagingData.from(channels))
        val watch = mutableStateOf(ContinueWatchingState("fixture", ContentKind.MOVIE, (0..19).map(::recent)))
        val playing = mutableStateOf(false)
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            if (!playing.value) holder.SaveableStateProvider("browser") {
                LibraryBrowser(LibraryState(channels = channels, tab = LibraryTab.MOVIES, activeSourceId = "fixture"), pages.collectAsLazyPagingItems(), true, app.artworkLoader,
                    onTab = {}, onQuery = {}, onCategory = {}, onPlay = {}, onFavourite = {}, onBack = {}, onCloseSearch = {}, watch = watch.value,
                    onRecent = { playing.value = true }, settings = {})
            }
        }
        val first = "recent_${recent(0).reference.refDigest}"
        awaitFocus(first)
        activate(first)
        compose.runOnIdle { watch.value = watch.value.copy(items = (1..19).map(::recent) + recent(0)); playing.value = false }
        awaitFocus(first)
        compose.onNodeWithTag(first).assertIsDisplayed()
        activate(first)
        compose.runOnIdle { watch.value = watch.value.copy(items = (1..19).map(::recent)); playing.value = false }
        val next = "recent_${recent(19).reference.refDigest}"
        awaitFocus(next)
        compose.onNodeWithTag(next).assertIsDisplayed()
        activate(next)
        compose.runOnIdle { watch.value = watch.value.copy(items = emptyList()); playing.value = false }
        awaitFocus("channel_19")
        compose.onNodeWithTag("continue_watching").assertDoesNotExist()
    }

    @Test fun completedHistoryWithUnloadedParentFallsBackToVisibleCatalogue() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IptvApplication
        val channels = (5..24).map { Channel(it, "Movie $it", null, ContentKind.MOVIE) }
        val pages = MutableStateFlow(PagingData.from(channels))
        val watch = mutableStateOf(ContinueWatchingState("fixture", ContentKind.MOVIE, listOf(recent(0))))
        val playing = mutableStateOf(false)
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            if (!playing.value) holder.SaveableStateProvider("browser") {
                LibraryBrowser(LibraryState(channels = channels, tab = LibraryTab.MOVIES, activeSourceId = "fixture"), pages.collectAsLazyPagingItems(), true, app.artworkLoader,
                    onTab = {}, onQuery = {}, onCategory = {}, onPlay = {}, onFavourite = {}, onBack = {}, onCloseSearch = {}, watch = watch.value,
                    onRecent = { playing.value = true }, settings = {})
            }
        }
        val tag = "recent_${recent(0).reference.refDigest}"
        awaitFocus(tag); activate(tag)
        compose.runOnIdle { watch.value = watch.value.copy(items = emptyList()); playing.value = false }
        awaitFocus("channel_5")
        compose.onNodeWithTag("catalogue_grid").assert(SemanticsMatcher.expectValue(LoadedCatalogueItems, 20))
    }

    private fun recent(index: Int) = RecentWatch(ProgressReference("fixture", index.toString(16).padStart(64, '0')), ContentKind.MOVIE,
        "Movie $index", null, index, null, null, WatchProgress(12_000, 30_000, false, index.toLong()), null)

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
