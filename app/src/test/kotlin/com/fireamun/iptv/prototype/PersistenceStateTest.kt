package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class PersistenceStateTest {
    @Before fun main() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun reset() { Dispatchers.resetMain() }
    private fun entries(title: String = "Cached") = listOf(PlaylistEntry(Channel(7, title, null), MediaAddress.parse("https://example.invalid/clip")))
    private class Store(var value: SavedLibrary? = null) : LibraryPersistence {
        var fail = false
        var refresh = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun restore() = value
        override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?): SavedLibrary {
            gate?.await()
            if (fail) throw ImportFailure(ImportError.STORAGE)
            refresh = refreshing
            return SavedLibrary(source, entries, 1234, "saved").also { value = it }
        }
        override suspend fun clear() { value = null }
        override suspend fun directory() = SourceDirectory(value?.let { listOf(SavedSource("saved", it.source.name, it.source.kind, 1)) } ?: emptyList(), if (value == null) null else "saved")
        override suspend fun activate(sourceId: String) = value!!
        override suspend fun rename(sourceId: String, name: String) {}
        override suspend fun remove(sourceId: String) { value = null }
    }
    @Test fun restoresCachedLibraryWithoutProviderRequestsAndPlaysHttpDirectly() = runTest {
        val source = SourceSpec.xtream("Saved", XtreamConnection.create("http://example.invalid", "u", "p")!!)
        val cached = listOf(PlaylistEntry(Channel(7, "Cached", null), MediaAddress.parse("http://example.invalid/clip")))
        var requests = 0
        val model = LibraryViewModel(persistence = Store(SavedLibrary(source, cached, 1234)), loadXtream = { requests++; entries() })
        advanceUntilIdle()
        assertEquals(0, requests); assertEquals("Saved", model.state.value.sourceName)
        assertTrue(model.state.value.hasSavedSource); assertEquals(1234L, model.state.value.savedAtMillis)
        model.selectChannel(7); assertNotNull(model.playback.value); assertFalse(model.state.value.needsHttpApproval)
    }
    @Test fun failedSaveKeepsPreviousCommittedLibraryAndRefreshUsesItsSavedSource() = runTest {
        val store = Store(SavedLibrary(SourceSpec.url(MediaAddress.parse("https://example.invalid/list")!!), entries(), 1234))
        val model = LibraryViewModel(persistence = store, loadUrl = { entries("Updated") })
        advanceUntilIdle(); store.fail = true
        model.importUrl("https://other.invalid/list"); advanceUntilIdle()
        assertEquals("Cached", model.state.value.channels.single().title); assertEquals(ImportError.STORAGE, model.state.value.error)
        store.fail = false; model.refreshSource {}; advanceUntilIdle()
        assertTrue(store.refresh); assertEquals("Updated", model.state.value.channels.single().title)
        assertEquals("https://example.invalid/list", store.value!!.source.url!!.reveal())
    }
    @Test fun localCommitCannotBeCancelledHalfwayAndRemovalClearsTheSavedSource() = runTest {
        val store = Store().apply { gate = CompletableDeferred() }
        val model = LibraryViewModel(persistence = store, loadUrl = { entries() })
        advanceUntilIdle(); model.importUrl("https://example.invalid/list"); runCurrent()
        assertTrue(model.state.value.saving)
        model.cancelImport(); assertTrue(model.state.value.importing)
        store.gate!!.complete(Unit); advanceUntilIdle()
        assertTrue(model.state.value.hasSavedSource); assertNotNull(store.value)
        model.removeSavedSource(); advanceUntilIdle()
        assertNull(store.value); assertTrue(model.state.value.channels.isEmpty()); assertFalse(model.state.value.hasSavedSource)
    }
    @Test fun importAndRefreshCommitSessionHeadersWithoutRequestingLegacyCatalogueOutput() = runTest {
        val loaded = entries("Imported")
        val gate = CompletableDeferred<Unit>()
        var header: LibrarySession? = null
        var opens = 0
        val operations = mutableListOf<Pair<Boolean, String?>>()
        val store = object : LibraryPersistence by Store() {
            override suspend fun openSession(): LibrarySession? { opens++; return header }
            override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?): SavedLibrary =
                throw ImportFailure(ImportError.STORAGE)
            override suspend fun saveSession(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?): LibrarySession {
                assertSame(loaded, entries)
                gate.await()
                operations.add(refreshing to targetSourceId)
                return LibrarySession(source, "session-source", "generation-${operations.size}", 5678, entries.size).also { header = it }
            }
            override suspend fun directory() = SourceDirectory(header?.let { listOf(SavedSource("session-source", null, it.source.kind, 1)) }.orEmpty(), header?.sourceId)
        }
        val model = LibraryViewModel(persistence = store, loadUrl = { loaded })
        advanceUntilIdle()
        model.importUrl("https://example.invalid/list"); runCurrent()
        assertTrue(model.state.value.saving)
        model.cancelImport()
        gate.complete(Unit); advanceUntilIdle()
        assertNull(model.state.value.error)
        assertTrue(model.state.value.hasSavedSource)
        assertEquals(1, model.state.value.entryCount)
        assertEquals("generation-1", model.state.value.catalogueGeneration)
        assertEquals(5678L, model.state.value.savedAtMillis)
        assertTrue(model.state.value.channels.isEmpty())
        model.refreshSource {}; advanceUntilIdle()
        assertEquals(listOf(false to null, true to "session-source"), operations)
        assertEquals("generation-2", model.state.value.catalogueGeneration)
        assertEquals(1, opens)
    }
    @Test fun fileRefreshRequestsAFileAndFailedRestoreDoesNotOverwriteUnreadableData() = runTest {
        val store = Store(SavedLibrary(SourceSpec.file(), entries(), 1234))
        val model = LibraryViewModel(persistence = store, ioDispatcher = StandardTestDispatcher(testScheduler))
        advanceUntilIdle(); var picked = false
        model.refreshSource { picked = true }; assertTrue(picked)
        model.importFile(refreshing = true) { "#EXTM3U\n#EXTINF:-1,Reordered file\nhttps://example.invalid/clip\n".byteInputStream() }
        advanceUntilIdle(); assertTrue(store.refresh)
        val broken = object : LibraryPersistence {
            override suspend fun restore(): SavedLibrary? = throw ImportFailure(ImportError.STORAGE)
            override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?): SavedLibrary = error("Must not replace unreadable storage")
            override suspend fun clear() {}
            override suspend fun directory() = SourceDirectory(listOf(SavedSource("broken", null, null, 1)), "broken")
            override suspend fun activate(sourceId: String): SavedLibrary = throw ImportFailure(ImportError.STORAGE)
            override suspend fun rename(sourceId: String, name: String) {}
            override suspend fun remove(sourceId: String) {}
        }
        val blocked = LibraryViewModel(persistence = broken)
        advanceUntilIdle(); assertTrue(blocked.state.value.storageBlocked)
        blocked.importUrl("https://example.invalid/list"); advanceUntilIdle()
        assertEquals(ImportError.STORAGE, blocked.state.value.error)
    }
}
