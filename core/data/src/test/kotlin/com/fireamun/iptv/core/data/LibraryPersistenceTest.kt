package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryPersistenceTest {
    private val source = SourceSpec.file()
    private val entries = listOf(PlaylistEntry(Channel(42, "Synthetic", null), MediaAddress.parse("https://example.invalid/item")))
    private open class Store : LibraryPersistence {
        override suspend fun restore(): SavedLibrary? = null
        override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?) = SavedLibrary(source, entries, 1234, "saved")
        override suspend fun clear() = Unit
        override suspend fun directory() = SourceDirectory()
        override suspend fun activate(sourceId: String): SavedLibrary = error("Unused")
        override suspend fun rename(sourceId: String, name: String) = Unit
        override suspend fun remove(sourceId: String) = Unit
    }

    @Test fun sessionSaveFallsBackToTheReturnedCatalogueWhenReopenIsEmpty() = runBlocking {
        val session = Store().saveSession(source, entries)
        assertSame(source, session.source)
        assertEquals("saved", session.sourceId)
        assertEquals("1234", session.generation)
        assertEquals(1234L, session.savedAtMillis)
        assertEquals(1, session.count)
        assertSame(entries, session.initialEntries)
    }

    @Test fun sessionSaveUsesTheCommittedStoreHeaderAndForwardsRefreshTarget() = runBlocking {
        val header = LibrarySession(source, "target", "committed-generation", 5678, 1)
        var committed = false
        val store = object : Store() {
            override suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean, targetSourceId: String?): SavedLibrary {
                assertSame(this@LibraryPersistenceTest.source, source)
                assertSame(this@LibraryPersistenceTest.entries, entries)
                assertTrue(refreshing)
                assertEquals("target", targetSourceId)
                committed = true
                return SavedLibrary(source, entries, 5678, "target")
            }
            override suspend fun openSession(): LibrarySession { assertTrue(committed); return header }
        }
        assertSame(header, store.saveSession(source, entries, true, "target"))
    }
}
