package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryProgressStateTest {
    @Before fun mainDispatcher() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }
    private val movie = PlaylistEntry(Channel(7, "Movie", null, ContentKind.MOVIE), MediaAddress.parse("https://example.invalid/movie"))
    private val series = PlaylistEntry(Channel(50, "Series", null, ContentKind.SERIES), null, 800)
    private val target = ProgressTarget("source", 7, ContentKind.MOVIE)
    private class Store(val entries: List<PlaylistEntry>, val progress: ProgressMemoryStore = ProgressMemoryStore(),
        val source: SourceSpec = SourceSpec.xtream("Synthetic", XtreamConnection.create("https://example.invalid", "u", "p")!!),
    ) : LibraryPersistence, PlaybackProgressPersistence by progress {
        var activationGate: CompletableDeferred<Unit>? = null
        var activeId = "source"
        override suspend fun restore() = SavedLibrary(source, entries, 1, "source")
        override suspend fun directory() = SourceDirectory(listOf(SavedSource("source", "Synthetic", source.kind, 0), SavedSource("other", "Other", SourceKind.XTREAM, 1)), activeId)
        override suspend fun activate(sourceId: String): SavedLibrary { activationGate?.await(); activeId = sourceId; return SavedLibrary(source, entries, 1, sourceId) }
        override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?) = SavedLibrary(source, entries, 1, "source")
        override suspend fun clear() = Unit
        override suspend fun rename(sourceId: String, name: String) = Unit
        override suspend fun remove(sourceId: String) = Unit
        override suspend fun item(sourceId: String, generation: String, itemId: Int) = entries.firstOrNull { it.channel.id == itemId }
    }

    private class PagedStore(val backing: Store) : LibraryPersistence by backing, PlaybackProgressPersistence by backing.progress {
        var heldLookup: Continuation<PlaylistEntry?>? = null
        var lateLookupReturned = false
        override suspend fun openSession() = LibrarySession(backing.source, "source", "generation", 1, backing.entries.size)
        override suspend fun item(sourceId: String, generation: String, itemId: Int): PlaylistEntry? {
            if (itemId != 7) return backing.entries.firstOrNull { it.channel.id == itemId }
            // This callback deliberately ignores Job cancellation, like an uncooperative disk API.
            return suspendCoroutine<PlaylistEntry?> { heldLookup = it }.also { lateLookupReturned = true }
        }
    }

    @Test fun restoredMovieChoiceCloseFinalAndRetryAreWiredWithoutStartupProviderCalls() = runTest {
        val store = Store(listOf(movie))
        var network = 0
        store.progress.records[target] = ProgressRecord(target, WatchProgress(12_000, null, false, 1), null)
        val recorder = PlaybackProgressRecorder(backgroundScope, store)
        val model = LibraryViewModel(persistence = store, progressRecorder = recorder, loadXtream = { network++; emptyList() }, loadEpisodes = { _, _ -> network++; emptyList() })
        runCurrent()
        assertEquals(0, network)
        assertTrue(store.progress.recentQueries.isEmpty())
        model.selectTab(LibraryTab.MOVIES); runCurrent()
        model.selectChannel(7); runCurrent()
        assertNull(model.playback.value)
        assertEquals(12_000L, model.continueWatching.value.prompt!!.positionMs)
        model.continuePlayback()
        var request = model.playback.value!!
        assertEquals(12_000L, request.initialOffsetMs)
        val instance = model.startPlaybackInstance(request.sessionId)!!
        model.playbackCheckpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(20_000, null, false, true))
        model.playbackCheckpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(7_000, null, false, true))
        model.retryPlayback(request)
        assertEquals(7_000L, model.playback.value!!.initialOffsetMs)
        request = model.playback.value!!
        val finalInstance = model.startPlaybackInstance(request.sessionId)!!
        model.closePlayback()
        assertNull(model.playback.value)
        model.playbackCheckpoint(request.sessionId, finalInstance.instanceId, PlaybackCheckpoint(17_000, null, false, true))
        model.playbackDisposed(request.sessionId); runCurrent()
        assertEquals(17_000L, store.progress.records[target]!!.progress.positionMs)
        assertEquals(0, network)
    }

    @Test fun sourceSwitchInvalidatesHeldReadBeforeActivationCanSuspend() = runTest {
        val store = Store(listOf(movie))
        store.progress.records[target] = ProgressRecord(target, WatchProgress(12_000, null, false, 1), null)
        val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store))
        runCurrent()
        val readGate = CompletableDeferred<Unit>()
        store.progress.readGate = readGate
        model.selectChannel(7); runCurrent()
        store.activationGate = CompletableDeferred()
        model.selectSource("other")
        readGate.complete(Unit); runCurrent()
        assertNull(model.continueWatching.value.prompt)
        assertNull(model.playback.value)
        store.activationGate!!.complete(Unit); runCurrent()
        assertEquals("other", model.state.value.activeSourceId)
    }

    @Test fun eventEntryInvalidatesBeforeQueuedReadsResumeForQueryCategoryTabBackImportAndCancel() = runTest {
        for (event in 0..4) {
            val store = Store(listOf(movie))
            store.progress.records[target] = ProgressRecord(target, WatchProgress(12_000, null, false, 1), null)
            val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store), loadUrl = { awaitCancellation() })
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            store.progress.readGate = gate
            model.selectChannel(7); runCurrent()
            when (event) {
                0 -> model.search("changed")
                1 -> model.selectCategory("group")
                2 -> model.selectTab(LibraryTab.SERIES)
                3 -> model.importUrl("https://example.invalid/new")
                4 -> model.cancelResumeChoice()
            }
            gate.complete(Unit); runCurrent()
            assertNull(model.continueWatching.value.prompt)
            assertNull(model.playback.value)
            model.cancelImport()
        }
    }

    @Test fun transientEpisodeIdsNeverReplaceStableParentRootAndBackInvalidatesChoice() = runTest {
        val store = Store(listOf(series))
        val episodeTarget = ProgressTarget("source", 50, ContentKind.EPISODE, "EPISODE:99")
        store.progress.records[episodeTarget] = ProgressRecord(episodeTarget, WatchProgress(12_000, null, false, 1), EpisodeSnapshot("EPISODE:99", "Episode", 1, 2, movie.address!!))
        var calls = 0
        val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store), loadEpisodes = { _, seriesId ->
            assertEquals(800L, seriesId); calls++
            listOf(PlaylistEntry(Channel(0, "Fresh episode", null, ContentKind.EPISODE, 1, 2), movie.address, providerKey = "EPISODE:99"))
        })
        runCurrent(); assertEquals(0, calls)
        model.selectTab(LibraryTab.SERIES); runCurrent()
        model.selectChannel(50); runCurrent()
        model.selectChannel(0); runCurrent()
        assertEquals(12_000L, model.continueWatching.value.prompt!!.positionMs)
        model.backToCatalog()
        model.continuePlayback()
        assertNull(model.playback.value)
        model.selectChannel(50); runCurrent()
        model.selectChannel(0); runCurrent()
        model.continuePlayback()
        assertEquals("Fresh episode", model.state.value.selectedTitle)
        val request = model.playback.value!!
        val instance = model.startPlaybackInstance(request.sessionId)!!
        model.playbackCheckpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(15_000, null, false, true)); runCurrent()
        assertEquals(15_000L, store.progress.records[episodeTarget]!!.progress.positionMs)
        assertEquals(1, store.progress.records.size)
    }

    @Test fun acceptedAndFinalWritesSurviveActualViewModelStoreClear() = runTest {
        val store = Store(listOf(movie))
        val recorder = PlaybackProgressRecorder(backgroundScope, store)
        val model = LibraryViewModel(persistence = store, progressRecorder = recorder)
        val holder = ViewModelStore().apply { put("library", model) }
        runCurrent()
        model.selectChannel(7); runCurrent()
        val request = model.playback.value!!
        val instance = model.startPlaybackInstance(request.sessionId)!!
        val gate = CompletableDeferred<Unit>()
        store.progress.saveGate = gate
        model.playbackCheckpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(12_000, null, false, true)); runCurrent()
        model.closePlayback()
        holder.clear()
        model.playbackCheckpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(17_000, null, false, true))
        model.playbackDisposed(request.sessionId)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("save:12000", "save:17000"), store.progress.operations)
        assertEquals(17_000L, store.progress.records[target]!!.progress.positionMs)
        assertNull(recorder.startInstance(request.sessionId))
    }

    @Test fun missingStableEpisodeKeyKeepsImmediatePlaybackWithoutBookmarking() = runTest {
        val store = Store(listOf(series))
        val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store), loadEpisodes = { _, _ ->
            listOf(PlaylistEntry(Channel(0, "Unkeyed episode", null, ContentKind.EPISODE), movie.address))
        })
        runCurrent()
        model.selectChannel(50); runCurrent()
        model.selectChannel(0)
        val request = model.playback.value!!
        val instance = model.startPlaybackInstance(request.sessionId)!!
        model.playbackCheckpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(15_000, null, false, true)); runCurrent()
        assertTrue(store.progress.records.isEmpty())
        assertNull(model.continueWatching.value.prompt)
    }

    @Test fun selectingAlreadyActiveSourceInvalidatesChoiceAndKeepsHistoryUsable() = runTest {
        val store = Store(listOf(movie))
        val reference = ProgressReference("source", "a".repeat(64))
        store.progress.records[target] = ProgressRecord(target, WatchProgress(12_000, null, false, 1), null)
        store.progress.recentItems = listOf(RecentWatch(reference, ContentKind.MOVIE, "Movie", null, 7, null, null, WatchProgress(12_000, null, false, 1), null))
        val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store))
        runCurrent()
        model.selectTab(LibraryTab.MOVIES); runCurrent()
        model.selectChannel(7); runCurrent()
        assertNotNull(model.continueWatching.value.prompt)
        model.selectSource("source"); runCurrent()
        assertNull(model.continueWatching.value.prompt)
        model.playRecent(reference); runCurrent()
        assertNotNull(model.continueWatching.value.prompt)
    }

    @Test fun newerRecentSelectionRejectsUncooperativeLatePagedLookupForPromptAndPlayback() = runTest {
        for (continueRecent in listOf(false, true)) {
            val recentMovie = PlaylistEntry(Channel(8, "Recent movie", null, ContentKind.MOVIE), MediaAddress.parse("https://example.invalid/recent"))
            val store = PagedStore(Store(listOf(movie, recentMovie)))
            val recentTarget = ProgressTarget("source", 8, ContentKind.MOVIE)
            val reference = ProgressReference("source", "b".repeat(64))
            val saved = WatchProgress(23_000, null, false, 1)
            store.backing.progress.records[recentTarget] = ProgressRecord(recentTarget, saved, null)
            store.backing.progress.recentItems = listOf(RecentWatch(reference, ContentKind.MOVIE, "Recent movie", null, 8, null, null, saved, null))
            val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store))
            runCurrent()
            assertTrue(model.state.value.channels.isEmpty())
            model.selectTab(LibraryTab.MOVIES); runCurrent()
            model.selectChannel(7); runCurrent()
            assertNotNull(store.heldLookup)
            assertFalse(store.lateLookupReturned)
            assertEquals(reference, model.continueWatching.value.items.single().reference)
            model.playRecent(reference); runCurrent()
            assertEquals("Recent movie", model.continueWatching.value.prompt!!.title)
            if (continueRecent) model.continuePlayback()
            val recentRequest = model.playback.value
            store.heldLookup!!.resume(movie); runCurrent()
            assertTrue(store.lateLookupReturned)
            if (continueRecent) {
                assertSame(recentRequest, model.playback.value)
                assertEquals(23_000L, model.playback.value!!.initialOffsetMs)
                assertEquals("Recent movie", model.state.value.selectedTitle)
                assertNull(model.continueWatching.value.prompt)
            } else {
                assertNull(model.playback.value)
                assertEquals("Recent movie", model.continueWatching.value.prompt!!.title)
                assertEquals(23_000L, model.continueWatching.value.prompt!!.positionMs)
            }
        }
    }

    @Test fun cancellingOrdinaryMovieChoiceRestoresSameHistoryWithoutChangingProgressOrPlaying() = runTest {
        val store = Store(listOf(movie))
        val reference = ProgressReference("source", "a".repeat(64))
        val saved = WatchProgress(12_000, 100_000, false, 1)
        val row = RecentWatch(reference, ContentKind.MOVIE, "Movie", null, 7, null, null, saved, null)
        store.progress.records[target] = ProgressRecord(target, saved, null)
        store.progress.recentItems = listOf(row)
        val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store))
        runCurrent()
        model.selectTab(LibraryTab.MOVIES); runCurrent()
        assertEquals(listOf(row), model.continueWatching.value.items)
        model.selectChannel(7); runCurrent()
        assertNotNull(model.continueWatching.value.prompt)
        model.cancelResumeChoice(); runCurrent()
        assertNull(model.continueWatching.value.prompt)
        assertNull(model.playback.value)
        assertEquals(saved, store.progress.records[target]!!.progress)
        assertTrue(store.progress.operations.isEmpty())
        assertEquals(listOf(row), model.continueWatching.value.items)
        model.playRecent(reference); runCurrent()
        assertEquals(12_000L, model.continueWatching.value.prompt!!.positionMs)
    }

    @Test fun cancelledFileRefreshRestoresUnchangedHistoryWithoutAnImportCallback() = runTest {
        val store = Store(listOf(movie), source = SourceSpec.file())
        val row = rememberMovie(store)
        val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store))
        runCurrent()
        model.selectTab(LibraryTab.MOVIES); runCurrent()
        assertEquals(listOf(row), model.continueWatching.value.items)
        var pickerOpened = 0
        model.refreshSource { pickerOpened++ }
        // Returning without importFile models a cancelled picker (null URI has no VM callback).
        runCurrent()
        assertEquals(1, pickerOpened)
        assertFalse(model.state.value.importing)
        assertEquals("source", model.state.value.activeSourceId)
        assertEquals(listOf(row), model.continueWatching.value.items)
        assertUnchangedHistoryAndCanChoose(model, store, row)
    }

    @Test fun rejectedUrlLoginAndSourceNameRestoreUnchangedHistoryAndActions() = runTest {
        for (rejection in 0..2) {
            val store = Store(listOf(movie))
            val row = rememberMovie(store)
            val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store))
            runCurrent()
            model.selectTab(LibraryTab.MOVIES); runCurrent()
            when (rejection) {
                0 -> model.importUrl("file:///invalid")
                1 -> model.importXtream("", "invalid", "", "")
                2 -> model.renameSource("source", "\n")
            }
            runCurrent()
            assertEquals(when (rejection) { 0 -> ImportError.INVALID_ADDRESS; 1 -> ImportError.INVALID_LOGIN; else -> ImportError.INVALID_NAME }, model.state.value.error)
            assertEquals(listOf(row), model.continueWatching.value.items)
            assertUnchangedHistoryAndCanChoose(model, store, row)
        }
    }

    @Test fun noOpSourceManagementAndRootBackRestoreCurrentHistory() = runTest {
        for (noOp in 0..3) {
            val store = Store(listOf(movie))
            val row = rememberMovie(store)
            val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store))
            runCurrent()
            model.selectTab(LibraryTab.MOVIES); runCurrent()
            when (noOp) {
                0 -> model.selectSource("missing")
                1 -> model.renameSource("missing", "Valid name")
                2 -> model.removeSource("missing")
                3 -> model.backToCatalog()
            }
            runCurrent()
            assertEquals(listOf(row), model.continueWatching.value.items)
            assertUnchangedHistoryAndCanChoose(model, store, row)
        }
    }

    @Test fun rejectedInputDuringImportKeepsHistoryUnavailableUntilImportFinishesOrCancels() = runTest {
        val pending = CompletableDeferred<List<PlaylistEntry>>()
        val store = Store(listOf(movie), source = SourceSpec.file())
        val row = rememberMovie(store)
        val model = LibraryViewModel(persistence = store, progressRecorder = PlaybackProgressRecorder(backgroundScope, store), loadUrl = { pending.await() })
        runCurrent()
        model.selectTab(LibraryTab.MOVIES); runCurrent()
        model.refreshSource { model.importUrl("https://example.invalid/pending") }
        runCurrent()
        assertTrue(model.state.value.importing)
        model.importUrl("file:///invalid"); runCurrent()
        assertTrue(model.state.value.importing)
        assertTrue(model.continueWatching.value.items.isEmpty())
        model.playRecent(row.reference); runCurrent()
        assertNull(model.continueWatching.value.prompt)
        assertNull(model.playback.value)
        assertEquals(row.progress, store.progress.records[target]!!.progress)
        assertTrue(store.progress.operations.isEmpty())
        model.cancelImport()
        pending.complete(emptyList()); runCurrent()
        assertEquals(listOf(row), model.continueWatching.value.items)
    }

    private fun rememberMovie(store: Store): RecentWatch {
        val reference = ProgressReference("source", "a".repeat(64))
        val saved = WatchProgress(12_000, 100_000, false, 1)
        store.progress.records[target] = ProgressRecord(target, saved, null)
        return RecentWatch(reference, ContentKind.MOVIE, "Movie", null, 7, null, null, saved, null).also { store.progress.recentItems = listOf(it) }
    }

    private fun TestScope.assertUnchangedHistoryAndCanChoose(model: LibraryViewModel, store: Store, row: RecentWatch) {
        assertFalse(model.state.value.importing)
        assertEquals("source", model.state.value.activeSourceId)
        assertEquals(row.progress, store.progress.records[target]!!.progress)
        assertTrue(store.progress.operations.isEmpty())
        assertNull(model.playback.value)
        model.playRecent(row.reference); runCurrent()
        assertEquals(12_000L, model.continueWatching.value.prompt!!.positionMs)
        assertNull(model.playback.value)
    }
}
