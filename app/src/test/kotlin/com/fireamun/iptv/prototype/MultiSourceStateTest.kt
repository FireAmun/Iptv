package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class MultiSourceStateTest {
    @Before fun main() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun reset() { Dispatchers.resetMain() }
    private fun entries(title: String) = listOf(PlaylistEntry(Channel(7, title, null), MediaAddress.parse("https://example.invalid/$title")))
    private inner class Store : LibraryPersistence {
        val values = linkedMapOf(
            "first" to SavedLibrary(SourceSpec.file(), entries("First"), 100, "first"),
            "second" to SavedLibrary(SourceSpec.url(MediaAddress.parse("https://example.invalid/list")!!), entries("Second"), 200, "second"),
        )
        val names = mutableMapOf<String, String>()
        val broken = mutableSetOf<String>()
        var active: String? = "second"
        var gate: CompletableDeferred<Unit>? = null
        private var sequence = 0
        override suspend fun directory() = SourceDirectory(values.values.mapIndexed { index, saved -> SavedSource(saved.sourceId!!, names[saved.sourceId] ?: saved.source.name, saved.source.kind, index + 1) }, active)
        override suspend fun restore(): SavedLibrary? {
            if (active in broken) throw ImportFailure(ImportError.STORAGE)
            return values[active]
        }
        override suspend fun activate(sourceId: String): SavedLibrary {
            if (sourceId in broken) throw ImportFailure(ImportError.STORAGE)
            return values.getValue(sourceId).also { active = sourceId }
        }
        override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?): SavedLibrary {
            gate?.await()
            val id = if (refreshing) targetSourceId ?: active!! else "new-${++sequence}"
            return SavedLibrary(source, entries, 300, id).also { values[id] = it; active = id }
        }
        override suspend fun rename(sourceId: String, name: String) { names[sourceId] = name }
        override suspend fun remove(sourceId: String) {
            values.remove(sourceId); names.remove(sourceId)
            if (active == sourceId) active = values.keys.firstOrNull()
        }
        override suspend fun clear() { values.clear(); names.clear(); active = null }
    }
    @Test fun switchingUsesCachedRootAndClearsPlaybackAndPendingConsentWithoutNetwork() = runTest {
        val store = Store(); var requests = 0
        val model = LibraryViewModel(persistence = store, loadUrl = { requests++; entries("Network") })
        advanceUntilIdle()
        assertEquals(2, model.state.value.sources.size)
        assertEquals("second", model.state.value.activeSourceId)
        model.selectChannel(7); assertNotNull(model.playback.value)
        model.importUrl("http://example.invalid/pending"); assertTrue(model.state.value.needsHttpApproval)
        model.selectSource("first"); advanceUntilIdle()
        assertEquals("first", model.state.value.activeSourceId)
        assertEquals("First", model.state.value.channels.single().title)
        assertFalse(model.state.value.needsHttpApproval); assertNull(model.playback.value)
        model.approveHttp(); advanceUntilIdle(); assertEquals(0, requests)
    }
    @Test fun anotherImportPreservesExistingSourcesAndRefreshTargetsOnlyTheSelectedSource() = runTest {
        val store = Store(); val model = LibraryViewModel(persistence = store, loadUrl = { entries("Updated") })
        advanceUntilIdle(); model.importUrl("https://example.invalid/third"); advanceUntilIdle()
        assertEquals(3, model.state.value.sources.size)
        model.selectSource("second"); advanceUntilIdle(); model.refreshSource {}; advanceUntilIdle()
        assertEquals("Updated", model.state.value.channels.single().title)
        assertEquals("First", store.values.getValue("first").entries.single().channel.title)
        assertEquals(3, store.values.size)
    }
    @Test fun renameAndTargetedRemovalKeepOtherSourcesAndSelectAFallback() = runTest {
        val store = Store(); val model = LibraryViewModel(persistence = store)
        advanceUntilIdle(); model.renameSource("first", "  Family TV  "); advanceUntilIdle()
        assertEquals("Family TV", model.state.value.sources.first().name)
        assertEquals("second", model.state.value.activeSourceId)
        model.removeSource("second"); advanceUntilIdle()
        assertEquals("first", model.state.value.activeSourceId)
        assertEquals("Family TV", model.state.value.sourceName)
        model.removeSource("first"); advanceUntilIdle()
        assertTrue(model.state.value.sources.isEmpty()); assertTrue(model.state.value.channels.isEmpty())
        assertFalse(model.state.value.hasSavedSource)
    }
    @Test fun failedActivationKeepsTheWorkingSourceAndDoesNotBlockOtherChoices() = runTest {
        val store = Store().apply { broken.add("first") }; val model = LibraryViewModel(persistence = store)
        advanceUntilIdle(); model.selectChannel(7)
        model.selectSource("first"); advanceUntilIdle()
        assertEquals("second", model.state.value.activeSourceId)
        assertEquals("Second", model.state.value.channels.single().title)
        assertNotNull(model.playback.value); assertFalse(model.state.value.storageBlocked)
        assertEquals(ImportError.STORAGE, model.state.value.error)
    }
    @Test fun unreadableActiveSourceStillAllowsSwitchingToAnotherSavedSource() = runTest {
        val store = Store().apply { broken.add("second") }; val model = LibraryViewModel(persistence = store)
        advanceUntilIdle(); assertTrue(model.state.value.storageBlocked)
        assertEquals(2, model.state.value.sources.size)
        model.selectSource("first"); advanceUntilIdle()
        assertFalse(model.state.value.storageBlocked)
        assertEquals("First", model.state.value.channels.single().title)
    }
    @Test fun invalidRenameDoesNotMutateSavedNames() = runTest {
        val store = Store(); val model = LibraryViewModel(persistence = store)
        advanceUntilIdle(); model.renameSource("first", "\n"); advanceUntilIdle()
        assertTrue(store.names.isEmpty()); assertEquals(ImportError.INVALID_NAME, model.state.value.error)
    }
    @Test fun managementCannotInterruptAnAtomicImportCommit() = runTest {
        val store = Store().apply { gate = CompletableDeferred() }
        val model = LibraryViewModel(persistence = store, loadUrl = { entries("Third") })
        advanceUntilIdle(); model.importUrl("https://example.invalid/third"); runCurrent()
        assertTrue(model.state.value.saving)
        model.selectSource("first"); model.renameSource("first", "Changed"); model.removeSource("second")
        assertEquals(2, store.values.size); assertTrue(store.names.isEmpty())
        store.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(3, model.state.value.sources.size)
    }
}
