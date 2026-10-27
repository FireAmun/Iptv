package com.fireamun.iptv.prototype

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import com.fireamun.iptv.core.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LibraryBrowseStorageTest {
    @get:Rule val diagnostics = TestDiagnostics()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(test: suspend (String, RoomLibraryStore) -> Unit) = runBlocking {
        val name = "browse-${UUID.randomUUID()}.db"
        val protector = KeystoreEnvelope("iptv.test.$name")
        val store = RoomLibraryStore.open(context, name, protector)
        try { test(name, store) } finally { store.close(); context.deleteDatabase(name); protector.clear() }
    }
    private fun entry(id: Int, title: String, kind: ContentKind = ContentKind.LIVE, group: String = "Sport") =
        PlaylistEntry(Channel(id, title, group, kind, year = 2024, rating = 8.0, hasArtwork = true), MediaAddress.parse("https://example.invalid/$id"), providerKey = "$kind:$id", artwork = MediaAddress.parse("https://images.invalid/$id?token=private-fixture"))

    @Test fun lazyHeaderAndMetadataPageDoNotReadOrDecryptOffscreenPlaybackLocators() = fixture { name, store ->
        store.save(SourceSpec.file(), (0..120).map { entry(it, "Channel $it") })
        val header = store.openSession()!!
        assertTrue(header.initialEntries.isEmpty())
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE library_entries SET address = zeroblob(64) WHERE position = 120")
            // Stress the metadata path without spending device-test time encrypting 100k locators.
            db.execSQL("WITH RECURSIVE numbers(n) AS (SELECT 121 UNION ALL SELECT n+1 FROM numbers WHERE n<99999) INSERT INTO library_entries (id,position,title,groupLabel,kind,sourceId) SELECT n,n,'Fixture','Sport','LIVE',? FROM numbers", arrayOf(header.sourceId))
        }
        assertEquals(100000, store.openSession()!!.count)
        val request = BrowseRequest(header.sourceId!!, header.generation, ContentKind.LIVE)
        val page = store.page(request, 0, 60)
        assertEquals(60, page.items.size); assertEquals(100000, page.total)
        assertNotNull(store.item(header.sourceId!!, header.generation, page.items.first().id)!!.address)
        try { store.item(header.sourceId!!, header.generation, 120); fail("Tampered locator must fail on selection") }
        catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
        assertEquals(60, store.page(request, 60, 60).items.size)
        assertTrue(store.page(request.copy(generation = "old"), 0, 60).items.isEmpty())
    }
    @Test fun searchKindsCategoriesAndFavouriteSurviveRefreshWithStableIdentity() = fixture { _, store ->
        val saved = store.save(SourceSpec.file(), listOf(entry(0, "أَهْلًا 1 HD"), entry(1, "اهلا 11 HD"), entry(2, "A film", ContentKind.MOVIE, "Drama")))
        var session = store.openSession()!!
        var request = BrowseRequest(session.sourceId!!, session.generation)
        assertEquals(listOf(saved.entries[0].channel.id), store.page(request.copy(query = "اهلا 1"), 0, 60).items.map { it.id })
        assertEquals(2, store.page(request.copy(query = "اهل"), 0, 60).total)
        assertEquals(1, store.page(request.copy(query = "fil"), 0, 60).total)
        assertEquals(1, store.page(request.copy(kind = ContentKind.MOVIE, category = "Drama"), 0, 60).total)
        assertEquals(listOf("Drama", "Sport"), store.categories(request))
        val film = saved.entries[2].channel.id
        store.setFavourite(session.sourceId!!, session.generation, film, true)
        assertEquals(film, store.page(request.copy(favouritesOnly = true), 0, 60).items.single().id)
        assertEquals("https://images.invalid/2?token=private-fixture", store.artwork(LibraryArtwork(session.sourceId!!, film, session.generation))!!.reveal())
        store.save(SourceSpec.file(), listOf(entry(2, "Renamed film", ContentKind.MOVIE, "Drama"), entry(0, "أهلا 1 HD")), true)
        session = store.openSession()!!; request = BrowseRequest(session.sourceId!!, session.generation)
        assertTrue(store.page(request.copy(favouritesOnly = true), 0, 60).items.single().favourite)
        store.remove(session.sourceId!!)
        assertNull(store.openSession())
    }
}
