package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ContinueWatchingState(
    val sourceId: String? = null,
    val kind: ContentKind? = null,
    val items: List<RecentWatch> = emptyList(),
    val prompt: ResumePrompt? = null,
    val loading: Boolean = false,
    val error: ProgressProblem? = null,
)
data class ResumePrompt(val title: String?, val positionMs: Long, val durationMs: Long?)
enum class ProgressProblem { READ, SAVE, RESET }

/** VM lifetime choices and reads; application lifetime checkpoints are owned by [recorder]. */
class PlaybackProgressCoordinator(
    private val scope: CoroutineScope,
    private val store: PlaybackProgressPersistence?,
    private val recorder: PlaybackProgressRecorder,
    private val resolve: suspend (String, String, Int) -> PlaylistEntry?,
    private val onReady: (PlaybackRequest, Channel) -> Unit,
) {
    private class Candidate(val entry: PlaylistEntry, val target: ProgressTarget?, val snapshot: EpisodeSnapshot?, val offset: Long = 0, val duration: Long? = null)
    private val mutableState = MutableStateFlow(ContinueWatchingState())
    val state = mutableState.asStateFlow()
    private var revision = 0L
    private var available = false
    private var sourceId: String? = null
    private var generation: String? = null
    private var pending: Candidate? = null
    private var active: Candidate? = null
    private var activeSession: String? = null
    private var closingSession: String? = null

    init {
        scope.launch {
            recorder.failure.collect { failure ->
                if (failure != null && failure.sessionId == activeSession) mutableState.value = mutableState.value.copy(error = ProgressProblem.SAVE)
            }
        }
    }

    /** Must run at VM event entry, before cancellation, a launch or any suspended operation. */
    fun invalidate() {
        revision++
        available = false
        pending = null
        mutableState.value = mutableState.value.copy(items = emptyList(), prompt = null, loading = false)
    }

    fun context(sourceId: String?, generation: String?, tab: LibraryTab, nested: Boolean, query: String, category: String?) {
        invalidate()
        this.sourceId = sourceId
        this.generation = generation
        available = true
        val kind = if (nested || query.isNotEmpty() || category != null) null else when (tab) {
            LibraryTab.MOVIES -> ContentKind.MOVIE
            LibraryTab.SERIES -> ContentKind.EPISODE
            else -> null
        }
        mutableState.value = ContinueWatchingState(sourceId, kind, error = mutableState.value.error)
        if (closingSession != null) { mutableState.value = mutableState.value.copy(loading = true); return }
        refreshHistory()
    }

    private fun refreshHistory() {
        val source = sourceId ?: return
        val kind = mutableState.value.kind ?: return
        val persistence = store ?: return
        if (!available || closingSession != null) return
        val token = revision
        mutableState.value = mutableState.value.copy(loading = true)
        scope.launch {
            try {
                recorder.flush().await()
                if (!valid(token)) return@launch
                val items = persistence.recent(source, kind, 20)
                if (!valid(token)) return@launch
                mutableState.value = mutableState.value.copy(items = items.filter { it.reference.sourceId == source && it.kind == kind }.take(20), loading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (valid(token)) mutableState.value = mutableState.value.copy(loading = false, error = ProgressProblem.READ) }
        }
    }

    fun play(entry: PlaylistEntry, sourceId: String?, parentItemId: Int?) {
        if (!available || closingSession != null || sourceId != this.sourceId || entry.address == null) return
        val token = nextSelection()
        val candidate = candidate(entry, sourceId, parentItemId)
        if (store == null || candidate.target == null) { ready(candidate); return }
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        scope.launch {
            try {
                recorder.flush().await()
                if (!valid(token)) return@launch
                val saved = store.read(candidate.target)
                if (!valid(token)) return@launch
                choose(candidate, saved?.progress)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { readFailed(token) }
        }
    }

    private fun candidate(entry: PlaylistEntry, sourceId: String?, parentItemId: Int?): Candidate {
        if (sourceId == null || store == null || entry.channel.kind !in setOf(ContentKind.MOVIE, ContentKind.EPISODE)) return Candidate(entry, null, null)
        return try {
            val snapshot = if (parentItemId != null) {
                if (entry.channel.kind != ContentKind.EPISODE || entry.providerKey == null) return Candidate(entry, null, null)
                EpisodeSnapshot(entry.providerKey!!, entry.channel.title, entry.channel.season, entry.channel.episode, entry.address!!).also {
                    // Validate the protected payload's bound before retaining it for the recorder.
                    EpisodeSnapshotCodec.encode(it).fill(0)
                }
            } else null
            Candidate(entry, ProgressTarget(sourceId, parentItemId ?: entry.channel.id, entry.channel.kind, snapshot?.providerKey), snapshot)
        } catch (_: IllegalArgumentException) { Candidate(entry, null, null) }
    }

    fun playRecent(reference: ProgressReference) {
        val persistence = store ?: return
        val catalogueGeneration = generation ?: return
        val row = mutableState.value.items.firstOrNull { it.reference == reference } ?: return
        if (!available || closingSession != null || mutableState.value.loading || reference.sourceId != sourceId) return
        val token = nextSelection()
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        scope.launch {
            try {
                recorder.flush().await()
                if (!valid(token)) return@launch
                val saved = persistence.read(reference) ?: run { readFailed(token); return@launch }
                if (!valid(token)) return@launch
                if (saved.target.sourceId != sourceId || saved.target.rootItemId != row.rootItemId || saved.target.kind != row.kind) { readFailed(token); return@launch }
                val root = resolve(reference.sourceId, catalogueGeneration, saved.target.rootItemId) ?: run { readFailed(token); return@launch }
                if (!valid(token)) return@launch
                val snapshot = saved.snapshot
                val entry = if (saved.target.episodeProviderKey != null) {
                    if (snapshot == null || root.channel.kind != ContentKind.SERIES) { readFailed(token); return@launch }
                    PlaylistEntry(Channel(root.channel.id, snapshot.title, root.channel.group, ContentKind.EPISODE, snapshot.season, snapshot.episode), snapshot.address, providerKey = snapshot.providerKey)
                } else root
                if (entry.address == null || entry.channel.kind != saved.target.kind) { readFailed(token); return@launch }
                choose(Candidate(entry, saved.target, snapshot), saved.progress)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { readFailed(token) }
        }
    }

    private fun nextSelection(): Long {
        revision++
        pending = null
        mutableState.value = mutableState.value.copy(prompt = null, loading = false)
        return revision
    }

    private fun choose(candidate: Candidate, saved: WatchProgress?) {
        mutableState.value = mutableState.value.copy(loading = false)
        if (saved == null || saved.completed || saved.positionMs < 1_000) { ready(candidate); return }
        pending = Candidate(candidate.entry, candidate.target, candidate.snapshot, resumeOffset(saved.positionMs, saved.durationMs), saved.durationMs)
        mutableState.value = mutableState.value.copy(prompt = ResumePrompt(candidate.entry.channel.title, pending!!.offset, saved.durationMs))
    }

    fun continuePlayback() {
        if (!available || mutableState.value.loading) return
        pending?.let { ready(it) }
    }

    fun startOver() {
        val candidate = pending ?: return
        val target = candidate.target ?: return
        if (!available || mutableState.value.loading) return
        val token = revision
        // Registry invalidation and FIFO reset acceptance happen before launching/cancellation.
        val reset = recorder.reset(target)
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        scope.launch {
            val result = reset.await()
            if (!valid(token)) return@launch
            mutableState.value = mutableState.value.copy(loading = false)
            if (result.isSuccess) ready(Candidate(candidate.entry, target, candidate.snapshot))
            else mutableState.value = mutableState.value.copy(error = ProgressProblem.RESET)
        }
    }

    fun cancelChoice() {
        nextSelection()
    }

    fun dismissError() { mutableState.value = mutableState.value.copy(error = null) }
    private fun valid(token: Long) = available && revision == token
    private fun readFailed(token: Long) {
        if (valid(token)) mutableState.value = mutableState.value.copy(loading = false, error = ProgressProblem.READ)
    }

    private fun ready(candidate: Candidate) {
        val address = candidate.entry.address ?: return
        pending = null
        active = candidate
        val request = recorder.open(address, candidate.entry.channel.kind, candidate.offset, candidate.target, candidate.snapshot)
        activeSession = request.sessionId
        mutableState.value = mutableState.value.copy(prompt = null, loading = false)
        onReady(request, candidate.entry.channel)
    }

    fun retry(request: PlaybackRequest) {
        if (!available || request.sessionId != activeSession) return
        val channel = active?.entry?.channel ?: return
        val next = recorder.retry(request) ?: return
        activeSession = next.sessionId
        onReady(next, channel)
    }
    fun startInstance(sessionId: String): PlaybackStart? = recorder.startInstance(sessionId)
    fun checkpoint(sessionId: String, instanceId: String, value: PlaybackCheckpoint) { recorder.checkpoint(sessionId, instanceId, value) }
    fun closing(sessionId: String) {
        if (!recorder.closing(sessionId)) {
            // A request closed before a Surface started has no final Player callback to await.
            recorder.disposed(sessionId)
            refreshHistory()
            return
        }
        closingSession = sessionId
        mutableState.value = mutableState.value.copy(loading = true)
    }
    fun disposed(sessionId: String) {
        // Enqueue even if scope is already cancelled; the app actor owns accepted commands.
        val barrier = recorder.disposed(sessionId)
        scope.launch {
            barrier.await()
            if (closingSession == sessionId) {
                closingSession = null
                if (available) {
                    mutableState.value = mutableState.value.copy(loading = false)
                    // Start a fresh read for the current context, never resume an old choice.
                    refreshHistory()
                }
            }
        }
    }
}
