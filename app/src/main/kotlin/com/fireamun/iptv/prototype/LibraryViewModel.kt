package com.fireamun.iptv.prototype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fireamun.iptv.core.data.HttpPlaylistLoader
import com.fireamun.iptv.core.data.M3uParser
import com.fireamun.iptv.core.data.PlaylistEntry
import com.fireamun.iptv.core.data.ImportFailure
import com.fireamun.iptv.core.data.XtreamConnection
import com.fireamun.iptv.core.data.XtreamLoader
import com.fireamun.iptv.core.data.SourceSpec
import com.fireamun.iptv.core.data.LibraryPersistence
import com.fireamun.iptv.core.model.SourceKind
import com.fireamun.iptv.core.model.LibraryState
import com.fireamun.iptv.core.model.MediaAddress
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.SavedSource
import com.fireamun.iptv.core.model.normalizeSourceName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import java.io.InputStream

class LibraryViewModel(
    private val parser: M3uParser = M3uParser(),
    private val loadUrl: suspend (MediaAddress) -> List<PlaylistEntry> = HttpPlaylistLoader()::load,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loadXtream: suspend (XtreamConnection) -> List<PlaylistEntry> = XtreamLoader()::load,
    private val loadEpisodes: suspend (XtreamConnection, Long) -> List<PlaylistEntry> = XtreamLoader()::episodes,
    private val persistence: LibraryPersistence? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryState(importing = persistence != null, restoring = persistence != null))
    val state = mutableState.asStateFlow()
    private val mutablePlayback = MutableStateFlow<MediaAddress?>(null)
    val playback = mutablePlayback.asStateFlow()
    private var library: List<PlaylistEntry> = emptyList()
    private var importJob: Job? = null
    private var generation = 0
    private var pending: (() -> Unit)? = null
    private var activeSource: SourceSpec? = null
    private var parent: Pair<List<PlaylistEntry>, LibraryState>? = null

    init {
        if (persistence != null) importJob = viewModelScope.launch {
            val current = generation
            try {
                refreshDirectory()
                val saved = persistence.restore()
                ensureActive()
                if (current == generation && saved != null) commitRoot(saved.source, saved.entries, saved.savedAtMillis, saved.sourceId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ImportFailure) {
                if (current == generation) mutableState.value = mutableState.value.copy(error = ImportError.STORAGE, storageBlocked = true, hasSavedSource = true)
            } finally { if (current == generation) mutableState.value = mutableState.value.copy(importing = false, restoring = false) }
        }
    }

    fun importFile(cancelOpen: () -> Unit = {}, refreshing: Boolean = false, open: () -> InputStream) = startImport(source = SourceSpec.file(), refreshing = refreshing) {
        coroutineScope {
            val opened = AtomicReference<InputStream?>()
            val reader = async(ioDispatcher) {
                val input = open()
                opened.set(input)
                try {
                    val context = currentCoroutineContext()
                    context.ensureActive()
                    parser.parse(input) { context.ensureActive() }
                } finally {
                    opened.compareAndSet(input, null)
                    input.close()
                }
            }
            try { reader.await() }
            finally {
                // await is cancellable even if the worker's read ignores thread interruption.
                withContext(NonCancellable + ioDispatcher) {
                    try { cancelOpen() } finally { opened.getAndSet(null)?.close() }
                }
            }
        }
    }

    fun importUrl(text: String) {
        if (blocked()) return
        val address = MediaAddress.parse(text)
        if (address == null) { reportError(ImportError.INVALID_ADDRESS); return }
        cancelImport()
        requireHttpApproval(address) { startImport(source = SourceSpec.url(address)) { loadUrl(address) } }
    }

    fun importXtream(name: String, server: String, username: String, password: String) {
        if (blocked()) return
        val label = name.trim()
        val source = XtreamConnection.create(server, username, password)
        if (label.isBlank() || label.length > 128 || label.any { it.isISOControl() } || source == null) {
            reportError(ImportError.INVALID_LOGIN)
            return
        }
        cancelImport()
        requireHttpApproval(source.address) {
            startImport(source = SourceSpec.xtream(label, source)) { loadXtream(source) }
        }
    }

    private fun commitRoot(source: SourceSpec, entries: List<PlaylistEntry>, savedAt: Long?, sourceId: String? = null) {
        val before = mutableState.value
        val id = sourceId ?: before.activeSourceId
        val sources = if (id != null && before.sources.none { it.id == id }) before.sources + SavedSource(id, source.name, source.kind, (before.sources.maxOfOrNull { it.ordinal } ?: 0) + 1) else before.sources
        library = entries
        activeSource = source
        parent = null
        closePlayback()
        mutableState.value = LibraryState(channels = entries.map { it.channel }, sourceName = sources.firstOrNull { it.id == id }?.name ?: source.name,
            savedAtMillis = savedAt, hasSavedSource = savedAt != null, sources = sources, activeSourceId = id,
            importing = before.importing, saving = before.saving, restoring = before.restoring, switchingSource = before.switchingSource)
    }

    private suspend fun refreshDirectory() {
        val directory = persistence?.directory() ?: return
        mutableState.value = mutableState.value.copy(sources = directory.sources, activeSourceId = directory.activeSourceId,
            sourceName = directory.sources.firstOrNull { it.id == directory.activeSourceId }?.name ?: activeSource?.name)
    }

    private fun blocked() = mutableState.value.saving || mutableState.value.restoring || mutableState.value.storageBlocked

    private fun startImport(source: SourceSpec? = null, refreshing: Boolean = false, onSuccess: ((List<PlaylistEntry>) -> Unit)? = null, load: suspend () -> List<PlaylistEntry>) {
        if (blocked()) return
        val targetSourceId = if (refreshing) mutableState.value.activeSourceId else null
        cancelImport()
        val current = generation
        mutableState.value = mutableState.value.copy(importing = true, error = null)
        importJob = viewModelScope.launch {
            try {
                val entries = load()
                ensureActive()
                if (entries.isEmpty()) throw ImportFailure(ImportError.EMPTY)
                if (current == generation) {
                    if (source == null) onSuccess?.invoke(entries)
                    else if (persistence == null) commitRoot(source, entries, null)
                    else {
                        // This is the local commit point. A user cancellation cannot split saved and visible state.
                        mutableState.value = mutableState.value.copy(saving = true)
                        withContext(NonCancellable) {
                            val saved = persistence.save(source, entries, refreshing, targetSourceId)
                            commitRoot(saved.source, saved.entries, saved.savedAtMillis, saved.sourceId)
                            refreshDirectory()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ImportFailure) {
                if (current == generation) reportError(failure.code)
            } catch (_: IOException) {
                if (current == generation) reportError(ImportError.READ_FAILED)
            } catch (_: SecurityException) {
                if (current == generation) reportError(ImportError.READ_FAILED)
            } finally {
                if (current == generation) mutableState.value = mutableState.value.copy(importing = false, saving = false)
            }
        }
    }

    private fun requireHttpApproval(address: MediaAddress, action: () -> Unit) {
        if (address.isCleartext) {
            pending = action
            mutableState.value = mutableState.value.copy(needsHttpApproval = true, error = null)
        } else action()
    }

    fun approveHttp() {
        val approved = pending
        dismissHttp()
        approved?.invoke()
    }
    fun dismissHttp() {
        pending = null
        mutableState.value = mutableState.value.copy(needsHttpApproval = false)
    }
    fun selectChannel(id: Int) {
        if (mutableState.value.importing) return
        val entry = library.firstOrNull { it.channel.id == id } ?: return
        mutableState.value = mutableState.value.copy(selectedId = id, error = null)
        val source = activeSource?.xtream
        val seriesId = entry.seriesId
        if (source != null && seriesId != null) {
            val root = library to mutableState.value
            requireHttpApproval(source.address) {
                startImport(onSuccess = { entries ->
                    parent = root
                    library = entries
                    mutableState.value = LibraryState(channels = entries.map { it.channel }, sourceName = root.second.sourceName,
                        collectionTitle = entry.channel.title, canGoBack = true, savedAtMillis = root.second.savedAtMillis, hasSavedSource = root.second.hasSavedSource,
                        sources = root.second.sources, activeSourceId = root.second.activeSourceId)
                }) { loadEpisodes(source, seriesId) }
            }
        } else entry.address?.let { address -> requireHttpApproval(address) { mutablePlayback.value = address } }
    }
    fun backToCatalog() {
        if (mutableState.value.saving || mutableState.value.restoring) return
        val root = parent ?: return
        cancelImport()
        closePlayback()
        library = root.first
        val latest = mutableState.value
        mutableState.value = root.second.copy(importing = false, needsHttpApproval = false, error = null,
            sources = latest.sources, activeSourceId = latest.activeSourceId,
            sourceName = latest.sources.firstOrNull { it.id == latest.activeSourceId }?.name ?: activeSource?.name)
        parent = null
    }
    fun closePlayback() { mutablePlayback.value = null }
    fun cancelImport() {
        if (mutableState.value.saving || mutableState.value.restoring) return
        generation++
        importJob?.cancel()
        importJob = null
        dismissHttp()
        mutableState.value = mutableState.value.copy(importing = false)
    }
    fun refreshSource(onFileNeeded: () -> Unit) {
        if (mutableState.value.importing || blocked()) return
        val source = activeSource ?: return
        when (source.kind) {
            SourceKind.FILE -> onFileNeeded()
            SourceKind.URL -> {
                val address = source.url!!
                requireHttpApproval(address) { startImport(source, refreshing = true) { loadUrl(address) } }
            }
            SourceKind.XTREAM -> {
                val connection = source.xtream!!
                requireHttpApproval(connection.address) { startImport(source, refreshing = true) { loadXtream(connection) } }
            }
        }
    }
    fun removeSavedSource() {
        mutableState.value.activeSourceId?.let { removeSource(it) }
    }
    fun selectSource(sourceId: String) {
        if (sourceId == mutableState.value.activeSourceId && !mutableState.value.storageBlocked) return
        manageSource(sourceId, switching = true) {
            val saved = persistence!!.activate(sourceId)
            commitRoot(saved.source, saved.entries, saved.savedAtMillis, saved.sourceId)
            refreshDirectory()
        }
    }
    fun renameSource(sourceId: String, name: String) {
        if (mutableState.value.importing) return
        val normalized = normalizeSourceName(name) ?: run { reportError(ImportError.INVALID_NAME); return }
        manageSource(sourceId) { persistence!!.rename(sourceId, normalized); refreshDirectory() }
    }
    fun removeSource(sourceId: String) {
        manageSource(sourceId) {
            val wasActive = mutableState.value.activeSourceId == sourceId
            persistence!!.remove(sourceId)
            if (wasActive) {
                activeSource = null; parent = null; library = emptyList(); closePlayback()
                mutableState.value = LibraryState(importing = true, saving = true, sources = mutableState.value.sources)
            }
            refreshDirectory()
            if (wasActive) {
                val saved = persistence.restore()
                if (saved != null) commitRoot(saved.source, saved.entries, saved.savedAtMillis, saved.sourceId)
            }
        }
    }
    private fun manageSource(sourceId: String, switching: Boolean = false, action: suspend () -> Unit) {
        if (persistence == null || mutableState.value.importing || mutableState.value.sources.none { it.id == sourceId }) return
        cancelImport()
        mutableState.value = mutableState.value.copy(importing = true, saving = true, switchingSource = switching, error = null)
        importJob = viewModelScope.launch {
            try {
                withContext(NonCancellable) { action() }
            } catch (failure: ImportFailure) {
                mutableState.value = mutableState.value.copy(error = failure.code,
                    storageBlocked = mutableState.value.storageBlocked || (activeSource == null && mutableState.value.sources.isNotEmpty()),
                    hasSavedSource = mutableState.value.hasSavedSource || mutableState.value.sources.isNotEmpty())
            } finally { mutableState.value = mutableState.value.copy(importing = false, saving = false, switchingSource = false) }
        }
    }
    fun reportError(error: ImportError) { mutableState.value = mutableState.value.copy(error = error) }
}
