package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Application lifetime owner. Only immutable storage commands enter the FIFO, never UI callbacks. */
class PlaybackProgressRecorder(
    scope: CoroutineScope,
    private val store: PlaybackProgressPersistence?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class SaveFailure(val sessionId: String, val sequence: Long)
    private data class Binding(
        val request: PlaybackRequest,
        val target: ProgressTarget?,
        val snapshot: EpisodeSnapshot?,
        var position: Long,
        var duration: Long? = null,
        var instance: String? = null,
        var closing: Boolean = false,
    )
    private sealed interface Command {
        data class Save(val sessionId: String, val target: ProgressTarget, val progress: WatchProgress, val snapshot: EpisodeSnapshot?) : Command
        data class Reset(val target: ProgressTarget, val result: CompletableDeferred<Result<Unit>>) : Command
        data class Flush(val result: CompletableDeferred<Unit>) : Command
    }
    private val lock = Any()
    private var binding: Binding? = null
    private val queue = Channel<Command>(Channel.UNLIMITED)
    private val mutableFailure = MutableStateFlow<SaveFailure?>(null)
    val failure = mutableFailure.asStateFlow()

    init {
        scope.launch {
            for (command in queue) when (command) {
                is Command.Save -> try {
                    store?.save(command.target, command.progress, command.snapshot)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    mutableFailure.value = SaveFailure(command.sessionId, (mutableFailure.value?.sequence ?: 0) + 1)
                }
                is Command.Reset -> {
                    val result = try { store?.reset(command.target); Result.success(Unit) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { Result.failure<Unit>(IllegalStateException("Progress reset failed")) }
                    command.result.complete(result)
                }
                is Command.Flush -> command.result.complete(Unit)
            }
        }
    }

    fun open(address: MediaAddress, kind: ContentKind, offset: Long, target: ProgressTarget? = null, snapshot: EpisodeSnapshot? = null): PlaybackRequest = synchronized(lock) {
        openLocked(address, kind, offset, target, snapshot)
    }

    private fun openLocked(address: MediaAddress, kind: ContentKind, offset: Long, target: ProgressTarget?, snapshot: EpisodeSnapshot?): PlaybackRequest {
        val request = PlaybackRequest(UUID.randomUUID().toString(), address, kind, offset.coerceAtLeast(0))
        // A new media epoch invalidates reports from every earlier VM/session in this process.
        // Accepted commands already in the queue remain valid and must finish.
        binding = Binding(request, target, snapshot, request.initialOffsetMs)
        return request
    }

    fun startInstance(sessionId: String): PlaybackStart? = synchronized(lock) {
        val current = binding?.takeIf { it.request.sessionId == sessionId && !it.closing } ?: return@synchronized null
        val instance = UUID.randomUUID().toString()
        current.instance = instance
        PlaybackStart(instance, resumeOffset(current.position, current.duration))
    }

    fun checkpoint(sessionId: String, instanceId: String, value: PlaybackCheckpoint) = synchronized(lock) {
        val current = binding?.takeIf { it.request.sessionId == sessionId && it.instance == instanceId } ?: return@synchronized
        val checkpoint = normalizedCheckpoint(value) ?: return@synchronized
        // RAM changes before IO submission, including backwards seeks and sub-second seeks.
        current.position = if (checkpoint.ended) 0 else checkpoint.positionMs
        current.duration = checkpoint.durationMs
        val target = current.target ?: return@synchronized
        if (current.request.kind !in setOf(ContentKind.MOVIE, ContentKind.EPISODE)) return@synchronized
        if (checkpoint.positionMs < 1_000 && !checkpoint.ended) return@synchronized
        queue.trySend(Command.Save(sessionId, target, WatchProgress(checkpoint.positionMs, checkpoint.durationMs, checkpoint.ended, clock().coerceAtLeast(0)), current.snapshot))
    }

    fun retry(request: PlaybackRequest): PlaybackRequest? = synchronized(lock) {
        val current = binding?.takeIf { it.request.sessionId == request.sessionId && !it.closing } ?: return@synchronized null
        openLocked(request.address, request.kind, resumeOffset(current.position, current.duration), current.target, current.snapshot)
    }

    fun reset(target: ProgressTarget): Deferred<Result<Unit>> = synchronized(lock) {
        if (binding?.target == target) binding = null
        CompletableDeferred<Result<Unit>>().also { queue.trySend(Command.Reset(target, it)) }
    }

    fun closing(sessionId: String): Boolean = synchronized(lock) {
        val current = binding?.takeIf { it.request.sessionId == sessionId } ?: return@synchronized false
        current.closing = true
        current.instance != null
    }

    fun disposed(sessionId: String): Deferred<Unit> = synchronized(lock) {
        binding?.takeIf { it.request.sessionId == sessionId }?.let {
            if (it.closing) binding = null else it.instance = null
        }
        flushLocked()
    }

    fun flush(): Deferred<Unit> = synchronized(lock) { flushLocked() }
    private fun flushLocked() = CompletableDeferred<Unit>().also { queue.trySend(Command.Flush(it)) }
}
