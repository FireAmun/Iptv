package com.fireamun.iptv.prototype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fireamun.iptv.core.data.HttpPlaylistLoader
import com.fireamun.iptv.core.data.M3uParser
import com.fireamun.iptv.core.data.PlaylistEntry
import com.fireamun.iptv.core.data.ImportFailure
import com.fireamun.iptv.core.model.LibraryState
import com.fireamun.iptv.core.model.MediaAddress
import com.fireamun.iptv.core.model.ImportError
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
) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryState())
    val state = mutableState.asStateFlow()
    private val mutablePlayback = MutableStateFlow<MediaAddress?>(null)
    val playback = mutablePlayback.asStateFlow()
    private var library: List<PlaylistEntry> = emptyList()
    private var importJob: Job? = null
    private var generation = 0
    private var pending: (() -> Unit)? = null

    fun importFile(cancelOpen: () -> Unit = {}, open: () -> InputStream) = startImport {
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
        val address = MediaAddress.parse(text)
        if (address == null) { reportError(ImportError.INVALID_ADDRESS); return }
        cancelImport()
        requireHttpApproval(address) { startImport { loadUrl(address) } }
    }

    private fun startImport(load: suspend () -> List<PlaylistEntry>) {
        cancelImport()
        val current = generation
        mutableState.value = mutableState.value.copy(importing = true, error = null)
        importJob = viewModelScope.launch {
            try {
                val entries = load()
                ensureActive()
                if (entries.isEmpty()) throw ImportFailure(ImportError.EMPTY)
                if (current == generation) {
                    library = entries
                    mutableState.value = LibraryState(channels = entries.map { it.channel })
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
                if (current == generation) mutableState.value = mutableState.value.copy(importing = false)
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
        requireHttpApproval(entry.address) { mutablePlayback.value = entry.address }
    }
    fun closePlayback() { mutablePlayback.value = null }
    fun cancelImport() {
        generation++
        importJob?.cancel()
        importJob = null
        dismissHttp()
        mutableState.value = mutableState.value.copy(importing = false)
    }
    fun reportError(error: ImportError) { mutableState.value = mutableState.value.copy(error = error) }
}
