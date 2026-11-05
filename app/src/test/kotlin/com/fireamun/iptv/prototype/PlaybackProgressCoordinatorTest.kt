package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressCoordinatorTest {
    private val movie = PlaylistEntry(Channel(7, "Current movie", null, ContentKind.MOVIE), MediaAddress.parse("https://example.invalid/movie")!!)
    private val target = ProgressTarget("source", 7, ContentKind.MOVIE)
    private fun ProgressMemoryStore.remember(target: ProgressTarget = this@PlaybackProgressCoordinatorTest.target, position: Long = 12_000, completed: Boolean = false, snapshot: EpisodeSnapshot? = null) {
        records[target] = ProgressRecord(target, WatchProgress(position, 100_000, completed, 1), snapshot)
    }
    private class Harness(scope: CoroutineScope, appScope: CoroutineScope, val store: ProgressMemoryStore = ProgressMemoryStore(), val resolve: suspend (String, String, Int) -> PlaylistEntry?) {
        val ready = mutableListOf<Pair<PlaybackRequest, Channel>>()
        val recorder = PlaybackProgressRecorder(appScope, store)
        val coordinator = PlaybackProgressCoordinator(scope, store, recorder, resolve) { request, channel -> ready += request to channel }
        fun context(source: String = "source", tab: LibraryTab = LibraryTab.MOVIES) = coordinator.context(source, "generation", tab, false, "", null)
    }

    @Test fun movieChoiceContinueAndCompletedOrAbsentStartAtZero() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        h.store.remember()
        h.context(); runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        assertTrue(h.ready.isEmpty())
        assertEquals(12_000L, h.coordinator.state.value.prompt!!.positionMs)
        h.coordinator.continuePlayback()
        assertEquals(12_000L, h.ready.single().first.initialOffsetMs)
        h.store.remember(completed = true)
        h.coordinator.play(movie, "source", null); runCurrent()
        assertEquals(0L, h.ready.last().first.initialOffsetMs)
        h.store.records.clear()
        h.coordinator.play(movie, "source", null); runCurrent()
        assertEquals(0L, h.ready.last().first.initialOffsetMs)
    }

    @Test fun startOverWaitsForAcceptedSaveThenResetAndRejectsOldCheckpoint() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        h.context(); runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        val request = h.ready.single().first
        val instance = h.coordinator.startInstance(request.sessionId)!!
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(10_000, null, false, true))
        runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        assertNotNull(h.coordinator.state.value.prompt)
        val gate = CompletableDeferred<Unit>()
        h.store.saveGate = gate
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(12_000, null, false, true))
        runCurrent()
        h.coordinator.startOver()
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(18_000, null, false, true))
        runCurrent()
        assertEquals(1, h.ready.size)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("save:10000", "save:12000", "reset"), h.store.operations)
        assertEquals(0L, h.ready.last().first.initialOffsetMs)
        assertNull(h.store.records[target])
    }

    @Test fun failedResetRetainsChoiceAndDoesNotStartAtZero() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        h.store.remember(); h.context(); runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        h.store.failReset = true
        h.coordinator.startOver(); runCurrent()
        assertEquals(ProgressProblem.RESET, h.coordinator.state.value.error)
        assertNotNull(h.coordinator.state.value.prompt)
        assertTrue(h.ready.isEmpty())
        assertEquals(12_000L, h.store.records[target]!!.progress.positionMs)
        h.store.failReset = false
        h.coordinator.startOver(); runCurrent()
        assertEquals(0L, h.ready.single().first.initialOffsetMs)
    }

    @Test fun failedReadNeverStartsOrOverwritesAndReselectCanRetry() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        h.store.remember(); h.context(); runCurrent()
        h.store.failRead = true
        h.coordinator.play(movie, "source", null); runCurrent()
        assertEquals(ProgressProblem.READ, h.coordinator.state.value.error)
        assertTrue(h.ready.isEmpty())
        assertEquals(12_000L, h.store.records[target]!!.progress.positionMs)
        h.store.failRead = false
        h.coordinator.play(movie, "source", null); runCurrent()
        assertNotNull(h.coordinator.state.value.prompt)
    }

    @Test fun heldReadCannotOpenPromptAfterSourceTabCancelOrBusyInvalidation() = runTest {
        for (change in 0..3) {
            val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
            h.store.remember(); h.context(); runCurrent()
            val gate = CompletableDeferred<Unit>()
            h.store.readGate = gate
            h.coordinator.play(movie, "source", null); runCurrent()
            when (change) {
                0 -> h.context("other")
                1 -> h.context(tab = LibraryTab.LIVE)
                2 -> h.coordinator.cancelChoice()
                3 -> h.coordinator.invalidate()
            }
            gate.complete(Unit); runCurrent()
            assertNull(h.coordinator.state.value.prompt)
            assertTrue(h.ready.isEmpty())
        }
    }

    @Test fun heldFlushCannotOpenPlaybackAfterSynchronousInvalidation() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        h.context(); runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        val request = h.ready.single().first
        val instance = h.coordinator.startInstance(request.sessionId)!!
        val gate = CompletableDeferred<Unit>()
        h.store.saveGate = gate
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(12_000, null, false, true))
        runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        h.coordinator.invalidate()
        gate.complete(Unit); runCurrent()
        assertEquals(1, h.ready.size)
        assertNull(h.coordinator.state.value.prompt)
    }

    @Test fun closingFinalReportIsFlushedBeforeHistoryReadAndOldInstanceCannotChangeRetryOffset() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        h.context(); runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        var request = h.ready.single().first
        val instance = h.coordinator.startInstance(request.sessionId)!!
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(20_000, null, false, true))
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(7_000, null, false, true))
        h.coordinator.retry(request)
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(22_000, null, false, true))
        request = h.ready.last().first
        assertEquals(7_000L, request.initialOffsetMs)
        val next = h.coordinator.startInstance(request.sessionId)!!
        h.coordinator.closing(request.sessionId)
        h.coordinator.checkpoint(request.sessionId, next.instanceId, PlaybackCheckpoint(17_000, null, false, true))
        h.coordinator.disposed(request.sessionId)
        runCurrent()
        assertEquals(17_000L, h.store.records[target]!!.progress.positionMs)
        assertEquals(ContentKind.MOVIE to 20, h.store.recentQueries.last())
    }

    @Test fun episodeUsesStableParentAndProviderKeyAcrossReorderingAndRefreshesSnapshotOnSave() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        val episodeTarget = ProgressTarget("source", 50, ContentKind.EPISODE, "EPISODE:99")
        h.store.remember(episodeTarget, snapshot = EpisodeSnapshot("EPISODE:99", "Old title", 1, 2, movie.address!!))
        h.context(tab = LibraryTab.SERIES); runCurrent()
        val episode = PlaylistEntry(Channel(1, "Fresh episode", null, ContentKind.EPISODE, 1, 2), MediaAddress.parse("https://example.invalid/fresh")!!, providerKey = "EPISODE:99")
        h.coordinator.play(episode, "source", 50); runCurrent()
        assertEquals(12_000L, h.coordinator.state.value.prompt!!.positionMs)
        h.coordinator.continuePlayback()
        val request = h.ready.single().first
        val instance = h.coordinator.startInstance(request.sessionId)!!
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(15_000, null, false, true))
        runCurrent()
        assertEquals("Fresh episode", h.store.records[episodeTarget]!!.snapshot!!.title)
        assertEquals("https://example.invalid/fresh", h.store.records[episodeTarget]!!.snapshot!!.address.reveal())
        assertEquals(ContentKind.EPISODE to 20, h.store.recentQueries.single())
    }

    @Test fun historyLaunchResolvesCurrentMovieAndEpisodeSnapshotWithoutProviderRequests() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { source, generation, id ->
            assertEquals("source", source); assertEquals("generation", generation)
            if (id == 7) movie else PlaylistEntry(Channel(50, "Parent", null, ContentKind.SERIES), null)
        }
        val reference = ProgressReference("source", "a".repeat(64))
        h.store.remember()
        h.store.recentItems = listOf(RecentWatch(reference, ContentKind.MOVIE, "Stale title", null, 7, null, null, WatchProgress(12_000, null, false, 1), null))
        h.context(); runCurrent()
        h.coordinator.playRecent(reference); runCurrent()
        assertEquals("Current movie", h.coordinator.state.value.prompt!!.title)
        h.coordinator.continuePlayback()
        assertEquals("Current movie", h.ready.single().second.title)
        val episodeTarget = ProgressTarget("source", 50, ContentKind.EPISODE, "EPISODE:99")
        h.store.records.clear()
        h.store.remember(episodeTarget, snapshot = EpisodeSnapshot("EPISODE:99", "Cached episode", 1, 2, movie.address!!))
        h.store.recentItems = listOf(RecentWatch(reference, ContentKind.EPISODE, "Cached episode", "Parent", 50, 1, 2, WatchProgress(12_000, null, false, 1), null))
        h.context(tab = LibraryTab.SERIES); runCurrent()
        h.coordinator.playRecent(reference); runCurrent()
        h.coordinator.continuePlayback()
        assertEquals("Cached episode", h.ready.last().second.title)
    }

    @Test fun startupUnrelatedTabsFiltersAndNestedContextNeverReadHistory() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> error("unexpected catalogue access") }
        runCurrent(); assertTrue(h.store.recentQueries.isEmpty())
        h.context(tab = LibraryTab.LIVE)
        h.coordinator.context("source", "g", LibraryTab.MOVIES, true, "", null)
        h.coordinator.context("source", "g", LibraryTab.MOVIES, false, "query", null)
        h.coordinator.context("source", "g", LibraryTab.MOVIES, false, "", "group")
        runCurrent(); assertTrue(h.store.recentQueries.isEmpty())
        h.context(); runCurrent()
        assertEquals(listOf(ContentKind.MOVIE to 20), h.store.recentQueries)
    }

    @Test fun closingBlocksHistoryAndCatalogueActivationUntilFinalDisposalWriteHasFinished() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        val reference = ProgressReference("source", "a".repeat(64))
        h.store.recentItems = listOf(RecentWatch(reference, ContentKind.MOVIE, "Movie", null, 7, null, null, WatchProgress(10_000, null, false, 1), null))
        h.context(); runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        val request = h.ready.single().first
        val instance = h.coordinator.startInstance(request.sessionId)!!
        h.coordinator.closing(request.sessionId)
        h.coordinator.playRecent(reference)
        h.coordinator.play(movie, "source", null)
        runCurrent()
        assertEquals(1, h.ready.size)
        assertNull(h.coordinator.state.value.prompt)
        assertTrue(h.coordinator.state.value.loading)
        val gate = CompletableDeferred<Unit>()
        h.store.saveGate = gate
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(17_000, null, false, true))
        h.coordinator.disposed(request.sessionId); runCurrent()
        h.coordinator.playRecent(reference); runCurrent()
        assertNull(h.coordinator.state.value.prompt)
        gate.complete(Unit); runCurrent()
        h.coordinator.playRecent(reference); runCurrent()
        assertEquals(17_000L, h.coordinator.state.value.prompt!!.positionMs)
    }

    @Test fun closingBeforeSurfaceStartsDoesNotWaitForANonexistentDisposalCallback() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        h.context(); runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        h.coordinator.closing(h.ready.single().first.sessionId)
        runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        assertEquals(2, h.ready.size)
    }

    @Test fun contextChangedDuringFinalFlushLoadsOnlyTheCurrentHistoryAfterBarrier() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        h.context(); runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        val request = h.ready.single().first
        val instance = h.coordinator.startInstance(request.sessionId)!!
        h.coordinator.closing(request.sessionId)
        val gate = CompletableDeferred<Unit>()
        h.store.saveGate = gate
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(17_000, null, false, true))
        h.coordinator.disposed(request.sessionId); runCurrent()
        h.context(tab = LibraryTab.SERIES); runCurrent()
        assertTrue(h.coordinator.state.value.loading)
        gate.complete(Unit); runCurrent()
        assertFalse(h.coordinator.state.value.loading)
        assertEquals(listOf(ContentKind.MOVIE to 20, ContentKind.EPISODE to 20), h.store.recentQueries)
        assertNull(h.coordinator.state.value.prompt)
    }

    @Test fun saveFailureIsSafeDismissibleAndDoesNotStopPlaybackOrReplacePriorProgress() = runTest {
        val h = Harness(backgroundScope, backgroundScope) { _, _, _ -> movie }
        h.context(); runCurrent()
        h.coordinator.play(movie, "source", null); runCurrent()
        val request = h.ready.single().first
        val instance = h.coordinator.startInstance(request.sessionId)!!
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(10_000, null, false, true)); runCurrent()
        h.store.failSave = true
        h.coordinator.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(15_000, null, false, true)); runCurrent()
        assertEquals(10_000L, h.store.records[target]!!.progress.positionMs)
        assertEquals(ProgressProblem.SAVE, h.coordinator.state.value.error)
        assertFalse(h.coordinator.state.value.toString().contains("private"))
        h.coordinator.dismissError()
        assertNull(h.coordinator.state.value.error)
        h.coordinator.retry(request)
        assertEquals(15_000L, h.ready.last().first.initialOffsetMs)
    }
}
