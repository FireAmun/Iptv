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

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryProgressStateTest {
    @Before fun mainDispatcher() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun resetDispatcher() { Dispatchers.resetMain() }
    private val movie = PlaylistEntry(Channel(7, "Movie", null, ContentKind.MOVIE), MediaAddress.parse("https://example.invalid/movie"))
    private val series = PlaylistEntry(Channel(50, "Series", null, ContentKind.SERIES), null, 800)
    private val target = ProgressTarget("source", 7, ContentKind.MOVIE)
    private class Store(val entries: List<PlaylistEntry>, val progress: ProgressMemoryStore = ProgressMemoryStore()) : LibraryPersistence, PlaybackProgressPersistence by progress {
        val source = SourceSpec.xtream("Synthetic", XtreamConnection.create("https://example.invalid", "u", "p")!!)
        var activationGate: CompletableDeferred<Unit>? = null
        var activeId = "source"
        override suspend fun restore() = SavedLibrary(source, entries, 1, "source")
        override suspend fun directory() = SourceDirectory(listOf(SavedSource("source", "Synthetic", SourceKind.XTREAM, 0), SavedSource("other", "Other", SourceKind.XTREAM, 1)), activeId)
        override suspend fun activate(sourceId: String): SavedLibrary { activationGate?.await(); activeId = sourceId; return SavedLibrary(source, entries, 1, sourceId) }
        override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?) = SavedLibrary(source, entries, 1, "source")
        override suspend fun clear() = Unit
        override suspend fun rename(sourceId: String, name: String) = Unit
        override suspend fun remove(sourceId: String) = Unit
        override suspend fun item(sourceId: String, generation: String, itemId: Int) = entries.firstOrNull { it.channel.id == itemId }
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
}
