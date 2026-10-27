package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class XtreamImportStateTest {
    @Before fun mainDispatcher() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }
    private fun entries() = listOf(
        PlaylistEntry(Channel(0, "Synthetic live", "Group", ContentKind.LIVE), MediaAddress.parse("https://example.invalid/live")),
        PlaylistEntry(Channel(1, "Synthetic series", "Group", ContentKind.SERIES), null, 7),
    )
    @Test fun cleartextLoginProceedsAndCredentialsNeverEnterSafeState() = runTest {
        var calls = 0
        val model = LibraryViewModel(loadXtream = { calls++; entries() }, ioDispatcher = StandardTestDispatcher(testScheduler))
        model.importXtream("My playlist", "http://example.invalid:8080", "private-user", "private-password")
        advanceUntilIdle()
        assertFalse(model.state.value.needsHttpApproval)
        assertEquals(1, calls); assertEquals("My playlist", model.state.value.sourceName)
        assertFalse(model.state.value.toString().contains("private-password"))
        assertFalse(model.state.value.toString().contains("example.invalid"))
    }
    @Test fun failedLoginRetainsWorkingSourceAndSeriesBackRestoresSelection() = runTest {
        var fail = false
        val model = LibraryViewModel(loadXtream = { if (fail) throw ImportFailure(ImportError.AUTH_FAILED) else entries() },
            loadEpisodes = { _, _ -> listOf(PlaylistEntry(Channel(0, "Episode", null, ContentKind.EPISODE, 1, 1), MediaAddress.parse("https://example.invalid/episode"))) },
            ioDispatcher = StandardTestDispatcher(testScheduler))
        model.importXtream("Working", "https://example.invalid", "u", "p"); advanceUntilIdle()
        fail = true
        model.importXtream("Failed", "https://other.invalid", "u", "wrong"); advanceUntilIdle()
        assertEquals("Working", model.state.value.sourceName); assertEquals(ImportError.AUTH_FAILED, model.state.value.error)
        model.selectChannel(1); advanceUntilIdle()
        assertTrue(model.state.value.canGoBack); assertEquals("Synthetic series", model.state.value.collectionTitle)
        model.selectChannel(0); assertNotNull(model.playback.value)
        model.closePlayback(); assertTrue(model.state.value.canGoBack)
        model.backToCatalog()
        assertFalse(model.state.value.canGoBack); assertEquals(1, model.state.value.selectedId)
        assertEquals(2, model.state.value.channels.size); assertNull(model.playback.value)
    }
    @Test fun cancelledReplacementAndFailedSeriesLeaveWorkingCatalogueUsable() = runTest {
        val pending = CompletableDeferred<List<PlaylistEntry>>()
        var replacement = false
        val model = LibraryViewModel(loadXtream = { if (replacement) pending.await() else entries() },
            loadEpisodes = { _, _ -> throw ImportFailure(ImportError.NO_CONTENT) }, ioDispatcher = StandardTestDispatcher(testScheduler))
        model.importXtream("Working", "https://example.invalid", "u", "p"); advanceUntilIdle()
        replacement = true
        model.importXtream("New", "https://other.invalid", "u", "p"); runCurrent()
        assertTrue(model.state.value.importing)
        model.cancelImport(); pending.complete(emptyList()); advanceUntilIdle()
        assertEquals("Working", model.state.value.sourceName)
        model.selectChannel(1); advanceUntilIdle()
        assertEquals(ImportError.NO_CONTENT, model.state.value.error)
        assertFalse(model.state.value.canGoBack)
        model.selectChannel(0)
        assertNotNull(model.playback.value)
    }
    @Test fun tabChangeCancelsPendingSeriesAndItsLateResultCannotReopenOldTab() = runTest {
        val episodes = CompletableDeferred<List<PlaylistEntry>>()
        val model = LibraryViewModel(loadXtream = { entries() }, loadEpisodes = { _, _ -> episodes.await() })
        model.importXtream("Working", "https://example.invalid", "u", "p"); advanceUntilIdle()
        model.selectTab(LibraryTab.SERIES); model.selectChannel(1); runCurrent()
        assertTrue(model.state.value.importing)
        model.selectTab(LibraryTab.SEARCH)
        episodes.complete(listOf(PlaylistEntry(Channel(0, "Late", null, ContentKind.EPISODE), MediaAddress.parse("https://example.invalid/late"))))
        advanceUntilIdle()
        assertEquals(LibraryTab.SEARCH, model.state.value.tab)
        assertFalse(model.state.value.canGoBack); assertFalse(model.state.value.importing)
        assertNull(model.playback.value)
    }
}
