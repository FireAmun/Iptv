package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseStateTest {
    @Before fun main() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun reset() { Dispatchers.resetMain() }
    private class Store : LibraryPersistence {
        val header = LibrarySession(SourceSpec.file(), "source", "generation", 1234, 100000)
        var resolved = 0
        var favourite: Boolean? = null
        override suspend fun restore(): SavedLibrary? = error("Bulk restore must not run")
        override suspend fun openSession() = header
        override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?): SavedLibrary = error("No provider import")
        override suspend fun clear() {}
        override suspend fun directory() = SourceDirectory(listOf(SavedSource("source", null, SourceKind.FILE, 1)), "source")
        override suspend fun activate(sourceId: String): SavedLibrary = error("Bulk activation must not run")
        override suspend fun rename(sourceId: String, name: String) {}
        override suspend fun remove(sourceId: String) {}
        override suspend fun item(sourceId: String, generation: String, itemId: Int): PlaylistEntry {
            assertEquals("source", sourceId); assertEquals("generation", generation); resolved++
            return PlaylistEntry(Channel(itemId, "Selected", null), MediaAddress.parse("http://example.invalid/clip"))
        }
        override suspend fun setFavourite(sourceId: String, generation: String, itemId: Int, favourite: Boolean) { this.favourite = favourite }
    }
    @Test fun startupUsesOnlyHeaderAndSelectionResolvesOneLocator() = runTest {
        val store = Store()
        val model = LibraryViewModel(persistence = store, loadUrl = { error("No startup network") })
        advanceUntilIdle()
        assertFalse(model.state.value.restoring); assertEquals(100000, model.state.value.entryCount)
        assertTrue(model.state.value.channels.isEmpty()); assertEquals(0, store.resolved)
        model.selectChannel(812); advanceUntilIdle()
        assertEquals(1, store.resolved); assertNotNull(model.playback.value)
        assertEquals("Selected", model.state.value.selectedTitle); assertFalse(model.state.value.needsHttpApproval)
    }
    @Test fun navigationAndSearchDoNotRefreshProviderAndFavouritesUseSavedIdentity() = runTest {
        val store = Store(); val model = LibraryViewModel(persistence = store)
        advanceUntilIdle()
        model.selectTab(LibraryTab.SEARCH); model.search("أفلام".repeat(100)); advanceUntilIdle()
        assertEquals(160, model.state.value.query.length)
        model.selectTab(LibraryTab.MOVIES); advanceUntilIdle()
        assertEquals("", model.state.value.query); assertEquals(LibraryTab.MOVIES, model.state.value.tab)
        model.selectTab(LibraryTab.SEARCH); model.search("Film"); model.closeSearch(); advanceUntilIdle()
        assertEquals(LibraryTab.MOVIES, model.state.value.tab); assertEquals("", model.state.value.query)
        model.toggleFavourite(Channel(812, "Fixture", null)); advanceUntilIdle()
        assertEquals(true, store.favourite); assertEquals(0, store.resolved)
    }
}
