package com.fireamun.iptv.prototype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.fireamun.iptv.core.data.LibrarySession
import com.fireamun.iptv.core.model.*
import com.fireamun.iptv.core.data.HttpPlaylistLoader
import com.fireamun.iptv.core.data.M3uParser
import com.fireamun.iptv.core.data.PlaylistEntry
import com.fireamun.iptv.core.data.ParsedPlaylist
import com.fireamun.iptv.core.data.GuidePersistence
import com.fireamun.iptv.core.data.GuideProgramme
import com.fireamun.iptv.core.data.ImportFailure
import com.fireamun.iptv.core.data.XtreamConnection
import com.fireamun.iptv.core.data.XtreamLoader
import com.fireamun.iptv.core.data.SourceSpec
import com.fireamun.iptv.core.data.LibraryPersistence
import com.fireamun.iptv.core.data.PlaybackProgressPersistence
import com.fireamun.iptv.core.data.ProgressReference
import com.fireamun.iptv.core.model.SourceKind
import com.fireamun.iptv.core.model.LibraryState
import com.fireamun.iptv.core.model.MediaAddress
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.SavedSource
import com.fireamun.iptv.core.model.normalizeSourceName
import com.posthog.PostHog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.drop
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
    progressRecorder: PlaybackProgressRecorder? = null,
    private val parental: ParentalControls? = null,
    /** Called after an import or refresh is saved, with the playlist header's guide link (if any). */
    private val onImported: (String, MediaAddress?) -> Unit = { _, _ -> },
) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryState(importing = persistence != null, restoring = persistence != null))
    val state = mutableState.asStateFlow()
    private val mutablePlayback = MutableStateFlow<PlaybackRequest?>(null)
    val playback = mutablePlayback.asStateFlow()
    private var library: List<PlaylistEntry> = emptyList()
    private var importJob: Job? = null
    private var generation = 0
    private var activeSource: SourceSpec? = null
    private var parent: Pair<List<PlaylistEntry>, LibraryState>? = null
    private var session: LibrarySession? = null
    private var categoryJob: Job? = null
    private var loadingEpisodes = false
    private var searchOrigin = LibraryTab.LIVE
    private var parentRootItemId: Int? = null
    private var selectionRevision = 0L
    private val progressStore = persistence as? PlaybackProgressPersistence
    private val progress = PlaybackProgressCoordinator(viewModelScope, progressStore,
        progressRecorder ?: PlaybackProgressRecorder(viewModelScope, progressStore),
        { sourceId, catalogueGeneration, id -> persistence?.item(sourceId, catalogueGeneration, id) },
        { recent -> parental?.blocks(recent.reference.sourceId, recent.kind, recent.rootGroup) == true },
    ) { request, channel ->
        mutableState.value = mutableState.value.copy(selectedId = channel.id, selectedTitle = channel.title, error = null)
        mutablePlayback.value = request
        capture("content_playback_started", mapOf("content_kind" to channel.kind.name.lowercase()))
    }
    val continueWatching = progress.state

    private fun progressEvent() { selectionRevision++; progress.invalidate() }
    private fun syncProgressContext() {
        val state = mutableState.value
        if (state.importing || state.saving || state.restoring || state.switchingSource || state.storageBlocked) progress.invalidate()
        else progress.context(state.activeSourceId, state.catalogueGeneration ?: session?.generation, state.tab, state.canGoBack, state.query, state.category)
    }
    private data class BrowseInput(val request: BrowseRequest?, val local: List<Channel>?, val revision: Int)
    private val browseInput = MutableStateFlow(BrowseInput(null, emptyList(), 0))
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val catalogue = browseInput.flatMapLatest { input ->
        Pager(PagingConfig(pageSize = 60, initialLoadSize = 60, prefetchDistance = 12, enablePlaceholders = false, maxSize = 300)) {
            LibraryPagingSource(persistence, input.request, input.local)
        }.flow
    }.cachedIn(viewModelScope)

    private val mutablePinPrompt = MutableStateFlow(false)
    /** True while a locked category or item waits for the parental PIN. */
    val pinPrompt = mutablePinPrompt.asStateFlow()
    private var pendingUnlock: (() -> Unit)? = null
    private fun requirePin(action: () -> Unit) { pendingUnlock = action; mutablePinPrompt.value = true }
    /** Called by the PIN dialog after [ParentalControls.unlock] accepted the PIN. */
    fun pinAccepted() {
        mutablePinPrompt.value = false
        pendingUnlock.also { pendingUnlock = null }?.invoke()
    }
    fun dismissPinPrompt() { pendingUnlock = null; mutablePinPrompt.value = false }

    private val guideStore = persistence as? GuidePersistence
    /** Now/next slots of one live item in the active playlist, for the player; null without a guide. */
    suspend fun guideNow(itemId: Int): GuideNow? {
        val sourceId = mutableState.value.activeSourceId ?: return null
        return try { guideStore?.nowNext(sourceId, listOf(itemId), System.currentTimeMillis())?.get(itemId) } catch (_: ImportFailure) { null }
    }
    /** A live item's schedule from two hours ago to two days ahead. */
    suspend fun schedule(itemId: Int): List<GuideProgramme> {
        val sourceId = mutableState.value.activeSourceId ?: return emptyList()
        val now = System.currentTimeMillis()
        return try { guideStore?.schedule(sourceId, itemId, now - 2 * 3_600_000L, now + 48 * 3_600_000L, 500).orEmpty() } catch (_: ImportFailure) { emptyList() }
    }
    /** A new guide was stored: lists re-read so live rows show it. */
    fun guideUpdated(sourceId: String) { if (sourceId == mutableState.value.activeSourceId) updateBrowse(loadCategories = false) }

    /** Every category label of [kind] in the active playlist, for choosing parental locks. */
    suspend fun lockableCategories(kind: ContentKind): List<String> {
        val sourceId = mutableState.value.activeSourceId
        val saved = session
        if (saved != null && saved.initialEntries.isEmpty() && persistence != null && sourceId != null)
            return persistence.categories(BrowseRequest(sourceId, saved.generation, kind))
        return (parent?.first ?: library).map { it.channel }.filter { matchesKind(it.kind, kind) }.mapNotNull { it.group?.takeIf(String::isNotEmpty) }.distinct().sorted()
    }

    /** "Forgot PIN": every playlist, login, favourite and watch record goes with the PIN. A failed removal keeps the PIN. */
    fun forgetPin() {
        val controls = parental ?: return
        progressEvent()
        val store = persistence ?: run { controls.forget(); syncProgressContext(); return }
        cancelImport()
        mutableState.value = mutableState.value.copy(importing = true, saving = true, error = null)
        progress.invalidate()
        importJob = viewModelScope.launch {
            try {
                withContext(NonCancellable) { store.clear() }
                controls.forget()
                activeSource = null; parent = null; parentRootItemId = null; library = emptyList(); session = null; closePlayback()
                mutableState.value = LibraryState(importing = true, saving = true)
                refreshDirectory()
                updateBrowse()
            } catch (failure: ImportFailure) { reportError(failure.code) }
            finally { mutableState.value = mutableState.value.copy(importing = false, saving = false); syncProgressContext() }
        }
    }

    init {
        parental?.let { controls ->
            viewModelScope.launch {
                controls.state.drop(1).collect {
                    // Relocking leaves no locked category selected; every list re-reads with the new hidden set.
                    val state = mutableState.value
                    if (controls.blocks(state.activeSourceId, tabKind(state.tab) ?: ContentKind.LIVE, state.category) && tabKind(state.tab) != null)
                        mutableState.value = state.copy(category = null)
                    updateBrowse()
                }
            }
        }
    }
    private fun tabKind(tab: LibraryTab) = when (tab) {
        LibraryTab.LIVE -> ContentKind.LIVE; LibraryTab.MOVIES -> ContentKind.MOVIE; LibraryTab.SERIES -> ContentKind.SERIES; else -> null
    }

    private fun capture(event: String, properties: Map<String, Any>) {
        runCatching { PostHog.capture(event = event, properties = properties) }
    }

    private fun logImportCompleted(source: SourceSpec, entryCount: Int, refreshing: Boolean) {
        runCatching {
            PostHog.logger.info("library import completed", mapOf(
                "operation" to if (refreshing) "refresh" else "import",
                "source_kind" to source.kind.name.lowercase(),
                "entry_count" to entryCount,
            ))
        }
    }

    private fun logImportFailed(source: SourceSpec?, error: ImportError, refreshing: Boolean) {
        runCatching {
            PostHog.logger.warn("library import failed", mapOf(
                "operation" to if (source == null) "load_series" else if (refreshing) "refresh" else "import",
                "source_kind" to (source?.kind?.name?.lowercase() ?: "unknown"),
                "error_code" to error.name.lowercase(),
            ))
        }
    }

    init {
        if (persistence != null) importJob = viewModelScope.launch {
            val current = generation
            try {
                refreshDirectory()
                val saved = persistence.openSession()
                ensureActive()
                if (current == generation && saved != null) commitSession(saved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ImportFailure) {
                if (current == generation) mutableState.value = mutableState.value.copy(error = ImportError.STORAGE, storageBlocked = true, hasSavedSource = true)
            } finally { if (current == generation) { mutableState.value = mutableState.value.copy(importing = false, restoring = false); syncProgressContext() } }
        }
        else syncProgressContext()
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
        progressEvent()
        if (blocked()) return
        val address = MediaAddress.parse(text)
        if (address == null) { reportError(ImportError.INVALID_ADDRESS); syncProgressContext(); return }
        cancelImport()
        startImport(source = SourceSpec.url(address)) { loadUrl(address) }
    }

    fun importXtream(name: String, server: String, username: String, password: String) {
        progressEvent()
        if (blocked()) return
        val label = name.trim()
        val source = XtreamConnection.create(server, username, password)
        if (label.isBlank() || label.length > 128 || label.any { it.isISOControl() } || source == null) {
            reportError(ImportError.INVALID_LOGIN)
            syncProgressContext()
            return
        }
        cancelImport()
        startImport(source = SourceSpec.xtream(label, source)) { loadXtream(source) }
    }

    private fun commitSession(saved: LibrarySession) {
        session = saved
        commitRoot(saved.source, saved.initialEntries, saved.savedAtMillis, saved.sourceId)
        mutableState.value = mutableState.value.copy(entryCount = saved.count, catalogueGeneration = saved.generation)
        updateBrowse()
    }

    private fun commitRoot(source: SourceSpec, entries: List<PlaylistEntry>, savedAt: Long?, sourceId: String? = null) {
        val before = mutableState.value
        val id = sourceId ?: before.activeSourceId
        val sources = if (id != null && before.sources.none { it.id == id }) before.sources + SavedSource(id, source.name, source.kind, (before.sources.maxOfOrNull { it.ordinal } ?: 0) + 1) else before.sources
        library = entries
        activeSource = source
        parent = null
        parentRootItemId = null
        closePlayback()
        mutableState.value = LibraryState(channels = entries.map { it.channel }, sourceName = sources.firstOrNull { it.id == id }?.name ?: source.name,
            savedAtMillis = savedAt, hasSavedSource = savedAt != null, sources = sources, activeSourceId = id,
            importing = before.importing, saving = before.saving, restoring = before.restoring, switchingSource = before.switchingSource,
            tab = if (before.entryCount == 0) LibraryTab.LIVE else before.tab)
        updateBrowse()
    }

    private suspend fun refreshDirectory() {
        val directory = persistence?.directory() ?: return
        mutableState.value = mutableState.value.copy(sources = directory.sources, activeSourceId = directory.activeSourceId,
            sourceName = directory.sources.firstOrNull { it.id == directory.activeSourceId }?.name ?: activeSource?.name)
    }

    private fun blocked() = mutableState.value.saving || mutableState.value.restoring || mutableState.value.storageBlocked

    private fun startImport(source: SourceSpec? = null, refreshing: Boolean = false, transient: Boolean = false, onSuccess: ((List<PlaylistEntry>) -> Unit)? = null, load: suspend () -> List<PlaylistEntry>) {
        progressEvent()
        if (blocked()) return
        val targetSourceId = if (refreshing) mutableState.value.activeSourceId else null
        cancelImport()
        loadingEpisodes = transient
        val current = generation
        val selection = selectionRevision
        mutableState.value = mutableState.value.copy(importing = true, error = null)
        progress.invalidate()
        importJob = viewModelScope.launch {
            try {
                val entries = load()
                ensureActive()
                if (entries.isEmpty()) throw ImportFailure(ImportError.EMPTY)
                if (current == generation) {
                    if (source == null) { if (!transient || selection == selectionRevision) onSuccess?.invoke(entries) }
                    else {
                        if (persistence == null) commitRoot(source, entries, null)
                        else {
                            // This is the local commit point. A user cancellation cannot split saved and visible state.
                            mutableState.value = mutableState.value.copy(saving = true)
                            withContext(NonCancellable) {
                                val saved = persistence.saveSession(source, entries, refreshing, targetSourceId)
                                commitSession(saved)
                                refreshDirectory()
                                saved.sourceId?.let { onImported(it, (entries as? ParsedPlaylist)?.guideLink) }
                            }
                        }
                        capture(if (refreshing) "library_refreshed" else "library_imported", mapOf(
                            "source_kind" to source.kind.name.lowercase(),
                            "entry_count" to entries.size,
                        ))
                        logImportCompleted(source, entries.size, refreshing)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ImportFailure) {
                if (current == generation) {
                    reportError(failure.code)
                    logImportFailed(source, failure.code, refreshing)
                }
            } catch (_: IOException) {
                if (current == generation) {
                    reportError(ImportError.READ_FAILED)
                    logImportFailed(source, ImportError.READ_FAILED, refreshing)
                }
            } catch (_: SecurityException) {
                if (current == generation) {
                    reportError(ImportError.READ_FAILED)
                    logImportFailed(source, ImportError.READ_FAILED, refreshing)
                }
            } finally {
                if (current == generation) { loadingEpisodes = false; mutableState.value = mutableState.value.copy(importing = false, saving = false); syncProgressContext() }
            }
        }
    }

    // Kept as no-op callbacks for older test hosts; production UI has no consent dialog.
    fun approveHttp() = dismissHttp()
    fun dismissHttp() {
        mutableState.value = mutableState.value.copy(needsHttpApproval = false)
    }
    fun selectChannel(id: Int) {
        progressEvent()
        if (mutableState.value.importing) return
        cancelImport()
        val local = library.firstOrNull { it.channel.id == id }
        if (local != null) { openEntry(local); return }
        val selected = session ?: return
        val sourceId = selected.sourceId ?: return
        val current = generation
        val selection = selectionRevision
        importJob = viewModelScope.launch {
            try {
                val entry = persistence?.item(sourceId, selected.generation, id) ?: return@launch
                if (current == generation && selection == selectionRevision && session === selected) openEntry(entry)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: ImportFailure) { if (current == generation && selection == selectionRevision && session === selected) reportError(failure.code) }
        }
    }
    private fun openEntry(entry: PlaylistEntry) {
        // The same rule guards every way an item can open: lists, search, favourites, history and later remotes.
        if (parental?.blocks(mutableState.value.activeSourceId, entry.channel.kind, entry.channel.group) == true) {
            requirePin { openEntry(entry) }
            return
        }
        mutableState.value = mutableState.value.copy(selectedId = entry.channel.id, selectedTitle = entry.channel.title, error = null)
        val source = activeSource?.xtream
        val seriesId = entry.seriesId
        if (source != null && seriesId != null) {
            val root = library to mutableState.value
            startImport(transient = true, onSuccess = { entries ->
                    parent = root
                    parentRootItemId = entry.channel.id
                    library = entries
                    mutableState.value = root.second.copy(channels = entries.map { it.channel }, collectionTitle = entry.channel.title,
                        canGoBack = true, query = "", category = null, categories = emptyList(), importing = false)
                    updateBrowse()
                }) { loadEpisodes(source, seriesId) }
        } else progress.play(entry, mutableState.value.activeSourceId, parentRootItemId)
    }
    fun backToCatalog() {
        progressEvent()
        if (mutableState.value.saving || mutableState.value.restoring) return
        val root = parent ?: run { syncProgressContext(); return }
        cancelImport()
        closePlayback()
        library = root.first
        val latest = mutableState.value
        mutableState.value = root.second.copy(importing = false, needsHttpApproval = false, error = null,
            sources = latest.sources, activeSourceId = latest.activeSourceId,
            sourceName = latest.sources.firstOrNull { it.id == latest.activeSourceId }?.name ?: activeSource?.name)
        parent = null
        parentRootItemId = null
        updateBrowse()
    }
    fun closePlayback() {
        progress.cancelChoice()
        mutablePlayback.value?.let { progress.closing(it.sessionId) }
        mutablePlayback.value = null
    }
    fun playRecent(reference: ProgressReference) { selectionRevision++; progress.playRecent(reference) }
    fun continuePlayback() = progress.continuePlayback()
    fun startPlaybackOver() = progress.startOver()
    fun cancelResumeChoice() { selectionRevision++; progress.cancelChoice(); syncProgressContext() }
    fun dismissProgressError() = progress.dismissError()
    fun retryPlayback(request: PlaybackRequest) = progress.retry(request)
    fun startPlaybackInstance(sessionId: String) = progress.startInstance(sessionId)
    fun playbackCheckpoint(sessionId: String, instanceId: String, checkpoint: PlaybackCheckpoint) = progress.checkpoint(sessionId, instanceId, checkpoint)
    fun playbackDisposed(sessionId: String) = progress.disposed(sessionId)
    fun cancelImport() {
        progressEvent()
        if (mutableState.value.saving || mutableState.value.restoring) return
        generation++
        importJob?.cancel()
        importJob = null
        loadingEpisodes = false
        dismissHttp()
        mutableState.value = mutableState.value.copy(importing = false)
        syncProgressContext()
    }
    fun refreshSource(onFileNeeded: () -> Unit) {
        progressEvent()
        if (mutableState.value.importing || blocked()) return
        val source = activeSource ?: run { syncProgressContext(); return }
        when (source.kind) {
            SourceKind.FILE -> { onFileNeeded(); syncProgressContext() }
            SourceKind.URL -> {
                val address = source.url!!
                startImport(source, refreshing = true) { loadUrl(address) }
            }
            SourceKind.XTREAM -> {
                val connection = source.xtream!!
                startImport(source, refreshing = true) { loadXtream(connection) }
            }
        }
    }
    fun removeSavedSource() {
        mutableState.value.activeSourceId?.let { removeSource(it) }
    }
    fun selectSource(sourceId: String) {
        progressEvent()
        if (sourceId == mutableState.value.activeSourceId && !mutableState.value.storageBlocked) { syncProgressContext(); return }
        manageSource(sourceId, switching = true) {
            val saved = persistence!!.activateSession(sourceId)
            commitSession(saved)
            refreshDirectory()
        }
    }
    fun renameSource(sourceId: String, name: String) {
        progressEvent()
        if (mutableState.value.importing) return
        val normalized = normalizeSourceName(name) ?: run { reportError(ImportError.INVALID_NAME); syncProgressContext(); return }
        manageSource(sourceId) { persistence!!.rename(sourceId, normalized); refreshDirectory() }
    }
    fun removeSource(sourceId: String) {
        progressEvent()
        manageSource(sourceId) {
            val wasActive = mutableState.value.activeSourceId == sourceId
            persistence!!.remove(sourceId)
            parental?.sourceRemoved(sourceId)
            if (wasActive) {
                activeSource = null; parent = null; parentRootItemId = null; library = emptyList(); session = null; closePlayback()
                mutableState.value = LibraryState(importing = true, saving = true, sources = mutableState.value.sources)
            }
            refreshDirectory()
            if (wasActive) {
                val saved = persistence.openSession()
                if (saved != null) commitSession(saved) else updateBrowse()
            }
        }
    }
    private fun manageSource(sourceId: String, switching: Boolean = false, action: suspend () -> Unit) {
        progressEvent()
        if (persistence == null || mutableState.value.importing || mutableState.value.sources.none { it.id == sourceId }) { syncProgressContext(); return }
        cancelImport()
        mutableState.value = mutableState.value.copy(importing = true, saving = true, switchingSource = switching, error = null)
        progress.invalidate()
        importJob = viewModelScope.launch {
            try {
                withContext(NonCancellable) { action() }
            } catch (failure: ImportFailure) {
                mutableState.value = mutableState.value.copy(error = failure.code,
                    storageBlocked = mutableState.value.storageBlocked || (activeSource == null && mutableState.value.sources.isNotEmpty()),
                    hasSavedSource = mutableState.value.hasSavedSource || mutableState.value.sources.isNotEmpty())
            } finally { mutableState.value = mutableState.value.copy(importing = false, saving = false, switchingSource = false); syncProgressContext() }
        }
    }
    fun reportError(error: ImportError) { mutableState.value = mutableState.value.copy(error = error) }

    fun selectTab(tab: LibraryTab) {
        progressEvent()
        if (tab == LibraryTab.SEARCH && mutableState.value.tab != LibraryTab.SEARCH) searchOrigin = mutableState.value.tab
        // Source commits are atomic, but a transient series request must not reopen an abandoned tab.
        if (loadingEpisodes || !mutableState.value.importing) cancelImport()
        if (mutableState.value.canGoBack) backToCatalog()
        mutableState.value = mutableState.value.copy(tab = tab, query = "", category = null, error = null)
        updateBrowse()
    }
    fun closeSearch() { selectTab(searchOrigin) }
    fun search(query: String) {
        progressEvent()
        mutableState.value = mutableState.value.copy(query = query.take(160))
        updateBrowse(loadCategories = false)
    }
    fun selectCategory(category: String?) {
        val kind = tabKind(mutableState.value.tab)
        if (kind != null && parental?.blocks(mutableState.value.activeSourceId, kind, category) == true) {
            requirePin { selectCategory(category) }
            return
        }
        progressEvent()
        mutableState.value = mutableState.value.copy(category = category)
        updateBrowse(loadCategories = false)
    }
    fun toggleFavourite(channel: Channel) {
        if (mutableState.value.canGoBack || mutableState.value.importing) return
        val saved = session
        if (saved != null && saved.initialEntries.isEmpty()) {
            val sourceId = saved.sourceId ?: return
            viewModelScope.launch {
                try {
                    persistence!!.setFavourite(sourceId, saved.generation, channel.id, !channel.favourite)
                    capture("favourite_changed", mapOf(
                        "content_kind" to channel.kind.name.lowercase(),
                        "is_favourite" to !channel.favourite,
                    ))
                    if (session === saved) updateBrowse()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: ImportFailure) { reportError(failure.code) }
            }
        } else {
            library = library.map { if (it.channel.id == channel.id) PlaylistEntry(it.channel.copy(favourite = !it.channel.favourite), it.address, it.seriesId, it.providerKey, it.artwork) else it }
            mutableState.value = mutableState.value.copy(channels = library.map { it.channel })
            capture("favourite_changed", mapOf(
                "content_kind" to channel.kind.name.lowercase(),
                "is_favourite" to !channel.favourite,
            ))
            updateBrowse()
        }
    }
    private fun updateBrowse(loadCategories: Boolean = true) {
        syncProgressContext()
        val state = mutableState.value
        val kind = if (state.canGoBack) null else when (state.tab) {
            LibraryTab.LIVE -> ContentKind.LIVE; LibraryTab.MOVIES -> ContentKind.MOVIE; LibraryTab.SERIES -> ContentKind.SERIES; else -> null
        }
        val request = BrowseRequest(state.activeSourceId.orEmpty(), state.catalogueGeneration ?: session?.generation.orEmpty(), kind,
            state.category, if (state.tab == LibraryTab.SEARCH) state.query else "", state.tab == LibraryTab.FAVOURITES && !state.canGoBack,
            parental?.hidden(state.activeSourceId) ?: CategoryLocks())
        val local = if (state.canGoBack || session == null || session!!.initialEntries.isNotEmpty()) state.channels else null
        browseInput.value = BrowseInput(request, local, browseInput.value.revision + 1)
        if (!loadCategories) return
        categoryJob?.cancel()
        if (state.tab == LibraryTab.SETTINGS || state.canGoBack) return
        categoryJob = viewModelScope.launch {
            try {
                val categories = if (local != null) local.filter { matchesKind(it.kind, kind) }.mapNotNull { it.group }.distinct().sorted()
                    else persistence?.categories(request).orEmpty()
                mutableState.value = mutableState.value.copy(categories = categories)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: ImportFailure) { reportError(failure.code) }
        }
    }
}
