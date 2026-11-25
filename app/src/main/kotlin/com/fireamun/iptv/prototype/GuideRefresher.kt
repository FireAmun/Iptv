package com.fireamun.iptv.prototype

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.fireamun.iptv.core.data.GuideLoader
import com.fireamun.iptv.core.data.GuidePersistence
import com.fireamun.iptv.core.data.GuideState
import com.fireamun.iptv.core.data.ImportFailure
import com.fireamun.iptv.core.data.ParsedGuide
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.MediaAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the settings card shows for the active source. [noLink] means neither the playlist, the user nor the login gives a guide. */
data class GuideUiState(val sourceId: String? = null, val refreshing: Boolean = false, val saved: GuideState? = null, val noLink: Boolean = false)

/**
 * Keeps the active source's programme guide current: automatically when the guide is over 12 hours old and the network
 * is unmetered (at most one automatic try an hour), and on demand on any network. Process-wide so a refresh survives
 * Activity recreation. Never logs or reports guide links.
 */
class GuideRefresher(
    private val store: GuidePersistence,
    private val scope: CoroutineScope,
    private val unmetered: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val load: suspend (MediaAddress, Long, Set<String>?) -> ParsedGuide = GuideLoader()::load,
) {
    private val mutable = MutableStateFlow(GuideUiState())
    val state = mutable.asStateFlow()
    private val stored = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Source ids whose guide was just replaced by a download. */
    val updates = stored.asSharedFlow()
    private var job: Job? = null
    /** Instrumentation turns automatic downloads off so fixture servers see only the requests a test expects. */
    @Volatile var automatic = true

    /** The active source changed or the app opened; refresh only if the guide is due. */
    @Synchronized fun select(sourceId: String?) {
        if (sourceId == mutable.value.sourceId && job?.isActive == true) return
        job?.cancel()
        mutable.value = GuideUiState(sourceId, saved = mutable.value.saved?.takeIf { it.sourceId == sourceId })
        if (sourceId != null) job = scope.launch { update(sourceId, force = false) }
    }

    /** A playlist was imported or refreshed: remember its header guide link, then refresh if due. */
    @Synchronized fun imported(sourceId: String, link: MediaAddress?) {
        job?.cancel()
        mutable.value = GuideUiState(sourceId, saved = mutable.value.saved?.takeIf { it.sourceId == sourceId })
        job = scope.launch {
            try { withContext(NonCancellable) { store.setPlaylistGuideLink(sourceId, link) } }
            catch (_: ImportFailure) { }
            update(sourceId, force = false)
        }
    }

    /** "Refresh guide": works on any network. */
    @Synchronized fun refresh() {
        val sourceId = mutable.value.sourceId ?: return
        if (mutable.value.refreshing) return
        job?.cancel()
        job = scope.launch { update(sourceId, force = true) }
    }

    /** Saves (or with null clears) the manual guide link and refreshes at once. Returns false for an unusable link. */
    @Synchronized fun setManualLink(text: String?): Boolean {
        val sourceId = mutable.value.sourceId ?: return false
        val link = text?.trim()?.let { value -> MediaAddress.parse(value)?.takeIf { value.length <= 4096 && (value.startsWith("http://", true) || value.startsWith("https://", true)) } ?: return false }
        job?.cancel()
        job = scope.launch {
            try { withContext(NonCancellable) { store.setManualGuideLink(sourceId, link) } }
            catch (_: ImportFailure) { publish(sourceId); return@launch }
            update(sourceId, force = true)
        }
        return true
    }

    private fun due(saved: GuideState, now: Long): Boolean {
        val fetched = saved.fetchedAtMs
        val attempted = saved.attemptedAtMs
        // A clock moved backwards counts as due rather than postponing the guide indefinitely.
        val stale = fetched == null || now - fetched >= STALE_AFTER_MS || now < fetched
        val retry = attempted == null || now - attempted >= RETRY_AFTER_MS || now < attempted
        return stale && retry
    }

    private suspend fun publish(sourceId: String): GuideState? {
        val saved = try { store.guideState(sourceId) } catch (_: ImportFailure) { null }
        if (mutable.value.sourceId == sourceId) mutable.value = mutable.value.copy(saved = saved)
        return saved
    }

    private suspend fun update(sourceId: String, force: Boolean) {
        val saved = publish(sourceId) ?: return
        if (!force && !(automatic && due(saved, clock()) && unmetered())) return
        val link = try { store.guideLink(sourceId) } catch (_: ImportFailure) { null }
        if (link == null) { if (mutable.value.sourceId == sourceId) mutable.value = mutable.value.copy(noLink = true); return }
        if (mutable.value.sourceId == sourceId) mutable.value = mutable.value.copy(refreshing = true, noLink = false)
        try {
            val wanted = store.guideKeys(sourceId)
            val now = clock()
            // A playlist without live channels has nothing to match; skip the download entirely.
            val guide = if (wanted.isEmpty()) ParsedGuide(emptyMap(), emptyMap()) else load(link, now, wanted)
            withContext(NonCancellable) { store.replaceGuide(sourceId, guide, now) }
            stored.tryEmit(sourceId)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ImportFailure) { record(sourceId, failure.code) }
        catch (_: Exception) { record(sourceId, ImportError.READ_FAILED) }
        finally {
            withContext(NonCancellable) {
                if (mutable.value.sourceId == sourceId) mutable.value = mutable.value.copy(refreshing = false)
                publish(sourceId)
            }
        }
    }

    private suspend fun record(sourceId: String, error: ImportError) {
        try { withContext(NonCancellable) { store.guideFailed(sourceId, error, clock()) } } catch (_: ImportFailure) { }
    }

    companion object {
        const val STALE_AFTER_MS = 12 * 3_600_000L
        const val RETRY_AFTER_MS = 3_600_000L

        /** Wi-Fi and Ethernet count as unmetered; mobile data and metered hotspots do not. */
        fun unmetered(context: Context): Boolean = runCatching {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        }.getOrDefault(false)
    }
}
