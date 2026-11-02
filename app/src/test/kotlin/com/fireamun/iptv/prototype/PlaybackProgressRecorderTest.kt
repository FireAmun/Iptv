package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressRecorderTest {
    private val target = ProgressTarget("source", 7, ContentKind.MOVIE)
    private val address = MediaAddress.parse("https://example.invalid/movie")!!
    private fun checkpoint(position: Long) = PlaybackCheckpoint(position, 100_000, false, true)

    @Test fun acceptedSaveThenResetRejectsLateReportAndPreservesFifo() = runTest {
        val store = ProgressMemoryStore()
        val gate = CompletableDeferred<Unit>()
        store.saveGate = gate
        val recorder = PlaybackProgressRecorder(backgroundScope, store) { 5 }
        val request = recorder.open(address, ContentKind.MOVIE, 0, target)
        val instance = recorder.startInstance(request.sessionId)!!
        recorder.checkpoint(request.sessionId, instance.instanceId, checkpoint(12_000))
        runCurrent()
        val reset = recorder.reset(target)
        recorder.checkpoint(request.sessionId, instance.instanceId, checkpoint(18_000))
        val barrier = recorder.flush()
        assertFalse(reset.isCompleted)
        assertFalse(barrier.isCompleted)
        gate.complete(Unit)
        runCurrent()
        assertTrue(reset.await().isSuccess)
        barrier.await()
        assertEquals(listOf("save:12000", "reset"), store.operations)
        assertNull(store.records[target])
    }

    @Test fun acceptedWriteSurvivesOriginatingScopeCancellation() = runTest {
        val store = ProgressMemoryStore()
        val gate = CompletableDeferred<Unit>()
        store.saveGate = gate
        val recorder = PlaybackProgressRecorder(backgroundScope, store)
        val vm = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val request = recorder.open(address, ContentKind.MOVIE, 0, target)
        val instance = recorder.startInstance(request.sessionId)!!
        vm.launch { recorder.checkpoint(request.sessionId, instance.instanceId, checkpoint(12_000)) }
        runCurrent()
        vm.cancel()
        gate.complete(Unit)
        runCurrent()
        assertEquals(12_000L, store.records[target]!!.progress.positionMs)
    }

    @Test fun closingAcceptsFinalCheckpointBeforeDisposalBarrierAndRetiresSession() = runTest {
        val store = ProgressMemoryStore()
        val recorder = PlaybackProgressRecorder(backgroundScope, store)
        val request = recorder.open(address, ContentKind.MOVIE, 0, target)
        val instance = recorder.startInstance(request.sessionId)!!
        recorder.closing(request.sessionId)
        recorder.checkpoint(request.sessionId, instance.instanceId, checkpoint(17_000))
        val disposed = recorder.disposed(request.sessionId)
        recorder.checkpoint(request.sessionId, instance.instanceId, checkpoint(22_000))
        runCurrent()
        disposed.await()
        assertEquals(17_000L, store.records[target]!!.progress.positionMs)
        assertNull(recorder.startInstance(request.sessionId))
    }

    @Test fun rotationAndRetryUseLatestBackwardSeekAndRejectSupersededInstancesAndSessions() = runTest {
        val store = ProgressMemoryStore()
        val recorder = PlaybackProgressRecorder(backgroundScope, store)
        val request = recorder.open(address, ContentKind.MOVIE, 0, target)
        val first = recorder.startInstance(request.sessionId)!!
        recorder.checkpoint(request.sessionId, first.instanceId, checkpoint(20_000))
        recorder.disposed(request.sessionId)
        val rotated = recorder.startInstance(request.sessionId)!!
        assertEquals(20_000L, rotated.positionMs)
        recorder.checkpoint(request.sessionId, first.instanceId, checkpoint(40_000))
        recorder.checkpoint(request.sessionId, rotated.instanceId, checkpoint(7_000))
        val retry = recorder.retry(request)!!
        assertNotEquals(request.sessionId, retry.sessionId)
        assertEquals(7_000L, retry.initialOffsetMs)
        recorder.checkpoint(request.sessionId, rotated.instanceId, checkpoint(22_000))
        val retryInstance = recorder.startInstance(retry.sessionId)!!
        assertEquals(7_000L, retryInstance.positionMs)
        runCurrent()
        assertEquals(7_000L, store.records[target]!!.progress.positionMs)
    }

    @Test fun failedSavePreservesPreviousRecordAndIneligiblePreparationCannotOverwrite() = runTest {
        val store = ProgressMemoryStore()
        val recorder = PlaybackProgressRecorder(backgroundScope, store)
        val request = recorder.open(address, ContentKind.MOVIE, 0, target)
        val instance = recorder.startInstance(request.sessionId)!!
        recorder.checkpoint(request.sessionId, instance.instanceId, checkpoint(10_000))
        runCurrent()
        store.failSave = true
        recorder.checkpoint(request.sessionId, instance.instanceId, checkpoint(15_000))
        recorder.checkpoint(request.sessionId, instance.instanceId, PlaybackCheckpoint(0, null, false, false))
        runCurrent()
        assertEquals(10_000L, store.records[target]!!.progress.positionMs)
        assertEquals(request.sessionId, recorder.failure.value!!.sessionId)
        assertEquals(15_000L, recorder.retry(request)!!.initialOffsetMs)
    }

    @Test fun newViewModelSessionRejectsOldReportsButDoesNotDiscardAcceptedWrites() = runTest {
        val store = ProgressMemoryStore()
        val gate = CompletableDeferred<Unit>()
        store.saveGate = gate
        val recorder = PlaybackProgressRecorder(backgroundScope, store)
        val old = recorder.open(address, ContentKind.MOVIE, 0, target)
        val instance = recorder.startInstance(old.sessionId)!!
        recorder.checkpoint(old.sessionId, instance.instanceId, checkpoint(12_000))
        runCurrent()
        val newer = recorder.open(address, ContentKind.MOVIE, 0, target)
        assertNotEquals(old.sessionId, newer.sessionId)
        recorder.checkpoint(old.sessionId, instance.instanceId, checkpoint(30_000))
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("save:12000"), store.operations)
    }
}

internal class ProgressMemoryStore : PlaybackProgressPersistence {
    val records = mutableMapOf<ProgressTarget, ProgressRecord>()
    val operations = mutableListOf<String>()
    var saveGate: CompletableDeferred<Unit>? = null
    var readGate: CompletableDeferred<Unit>? = null
    var failSave = false
    var failRead = false
    var failReset = false
    var recentItems = emptyList<RecentWatch>()
    val recentQueries = mutableListOf<Pair<ContentKind, Int>>()
    override suspend fun read(target: ProgressTarget): ProgressRecord? {
        readGate?.await()
        if (failRead) error("private locator")
        return records[target]
    }
    override suspend fun read(reference: ProgressReference): ProgressRecord? {
        readGate?.await()
        if (failRead) error("private locator")
        return records.values.firstOrNull { it.target.sourceId == reference.sourceId }
    }
    override suspend fun save(target: ProgressTarget, progress: WatchProgress, snapshot: EpisodeSnapshot?) {
        saveGate?.await()
        if (failSave) error("private locator")
        operations += "save:${progress.positionMs}"
        records[target] = ProgressRecord(target, progress, snapshot)
    }
    override suspend fun reset(target: ProgressTarget) {
        if (failReset) error("private locator")
        operations += "reset"
        records.remove(target)
    }
    override suspend fun recent(sourceId: String, kind: ContentKind, limit: Int): List<RecentWatch> {
        readGate?.await()
        if (failRead) error("private locator")
        recentQueries += kind to limit
        return recentItems
    }
}
