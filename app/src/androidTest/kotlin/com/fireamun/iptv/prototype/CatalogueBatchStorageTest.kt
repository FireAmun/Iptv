package com.fireamun.iptv.prototype

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import com.fireamun.iptv.core.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CatalogueBatchStorageTest {
    @get:Rule val diagnostics = TestDiagnostics()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fixture(test: suspend (String, RoomLibraryStore) -> Unit) = runBlocking {
        val name = "batch-${UUID.randomUUID()}.db"
        val protector = KeystoreEnvelope("iptv.test.$name")
        val store = RoomLibraryStore.open(context, name, protector)
        try { test(name, store) } finally { store.close(); context.deleteDatabase(name); protector.clear() }
    }
    private fun entry(index: Int, title: String = "Candidate $index", kind: ContentKind = ContentKind.LIVE, key: String? = "ITEM:$index", path: String = "$index") =
        PlaylistEntry(Channel(index, title, "Synthetic", kind),
            if (kind == ContentKind.SERIES) null else MediaAddress.parse("https://example.invalid/$path")!!,
            if (kind == ContentKind.SERIES) index.toLong() else null, key,
            MediaAddress.parse("https://images.invalid/$path")!!)

    private fun <T> database(name: String, block: (SQLiteDatabase) -> T): T =
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use(block)
    private fun counts(name: String): List<Int> = database(name) { db ->
        listOf("library_entries", "entry_search", "item_identities").map { table ->
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor -> assertTrue(cursor.moveToFirst()); cursor.getInt(0) }
        }
    }

    @Test fun duplicateOccurrencesAcrossBatchBoundariesKeepDistinctIdsAfterReorder() = fixture { name, store ->
        val input = (0..600).map { entry(it) }.toMutableList().apply {
            this[249] = entry(249, key = "duplicate-provider")
            this[250] = entry(250, key = "duplicate-provider")
            this[499] = entry(499, key = null, path = "duplicate-locator")
            this[500] = entry(500, key = null, path = "duplicate-locator")
        }
        val first = store.save(SourceSpec.file(), input)
        val reordered = store.save(SourceSpec.file(), input.reversed(), true, first.sourceId)
        assertEquals(601, reordered.entries.map { it.channel.id }.toSet().size)
        assertEquals(first.entries[249].channel.id, reordered.entries[350].channel.id)
        assertEquals(first.entries[250].channel.id, reordered.entries[351].channel.id)
        assertEquals(first.entries[499].channel.id, reordered.entries[100].channel.id)
        assertEquals(first.entries[500].channel.id, reordered.entries[101].channel.id)
        assertEquals(first.entries[0].channel.id, reordered.entries[600].channel.id)
        assertEquals(listOf(601, 601, 601), counts(name))
        val session = store.openSession()!!
        val selected = store.item(first.sourceId!!, session.generation, reordered.entries[100].channel.id)!!
        assertEquals("https://example.invalid/duplicate-locator", selected.address!!.reveal())
        assertEquals("https://images.invalid/duplicate-locator", store.artwork(LibraryArtwork(first.sourceId!!, selected.channel.id, session.generation))!!.reveal())
    }

    private class Committed(val session: LibrarySession, val movie: ProgressTarget, val episode: ProgressTarget)
    private suspend fun committed(store: RoomLibraryStore): Committed {
        val saved = store.save(SourceSpec.file(), listOf(entry(1, "Oldmovie", ContentKind.MOVIE), entry(2, "Oldseries", ContentKind.SERIES)))
        val session = store.openSession()!!
        val sourceId = saved.sourceId!!
        val movie = ProgressTarget(sourceId, saved.entries[0].channel.id, ContentKind.MOVIE)
        val episode = ProgressTarget(sourceId, saved.entries[1].channel.id, ContentKind.EPISODE, "EPISODE:31")
        store.setFavourite(sourceId, session.generation, movie.rootItemId, true)
        store.save(movie, WatchProgress(12_000, 30_000, false, 100))
        store.save(episode, WatchProgress(15_000, 30_000, false, 200),
            EpisodeSnapshot("EPISODE:31", "Oldepisode", 1, 2, MediaAddress.parse("https://example.invalid/episode-31")!!))
        return Committed(session, movie, episode)
    }
    private suspend fun assertCommitted(name: String, store: RoomLibraryStore, before: Committed) {
        val session = store.openSession()!!
        assertEquals(before.session.sourceId, session.sourceId)
        assertEquals(before.session.generation, session.generation)
        assertEquals(before.session.savedAtMillis, session.savedAtMillis)
        assertEquals(2, session.count)
        val request = BrowseRequest(session.sourceId!!, session.generation)
        assertEquals("Oldmovie", store.page(request.copy(query = "Oldmovie"), 0, 60).items.single().title)
        assertEquals(0, store.page(request.copy(query = "Candidate"), 0, 60).total)
        assertEquals(before.movie.rootItemId, store.page(request.copy(favouritesOnly = true), 0, 60).items.single().id)
        assertEquals(12_000L, store.read(before.movie)!!.progress.positionMs)
        val episode = store.read(before.episode)!!
        assertEquals(15_000L, episode.progress.positionMs)
        assertEquals("https://example.invalid/episode-31", episode.snapshot!!.address.reveal())
        assertEquals(listOf(2, 2, 2), counts(name))
    }

    @Test fun lateBatchWriteFailureRollsBackSearchIdentitiesAndUserState() = fixture { name, store ->
        val before = committed(store)
        database(name) { db ->
            db.execSQL("CREATE TRIGGER reject_late_batch BEFORE INSERT ON library_entries WHEN NEW.position = 500 BEGIN SELECT RAISE(ABORT, 'Synthetic late batch rejection'); END")
        }
        try {
            store.saveSession(SourceSpec.file(), (0..750).map { entry(it) }, true, before.session.sourceId)
            fail("Expected late write rejection")
        } catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
        assertCommitted(name, store, before)
    }

    @Test fun cancellationAfterTwoBatchesRollsBackToCommittedCatalogue() = fixture { name, store ->
        val before = committed(store)
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val input = object : AbstractList<PlaylistEntry>() {
            override val size = 751
            override fun get(index: Int): PlaylistEntry {
                if (index == 500) {
                    reached.countDown()
                    check(release.await(20, TimeUnit.SECONDS)) { "Cancellation fixture was not released" }
                }
                return entry(index)
            }
        }
        coroutineScope {
            val saving = launch(Dispatchers.Default) { store.saveSession(SourceSpec.file(), input, true, before.session.sourceId) }
            try {
                assertTrue("Save did not reach the third batch", withContext(Dispatchers.IO) { reached.await(20, TimeUnit.SECONDS) })
                saving.cancel()
            } finally { release.countDown(); saving.cancelAndJoin() }
            assertTrue(saving.isCancelled)
        }
        assertCommitted(name, store, before)
    }

    @Test fun sessionSavePublishesOnlyAHeaderAndKeepsPagedIdsAndProtectedLookupsAcrossRefresh() = fixture { name, store ->
        val input = (0..750).map { entry(it) }
        val first = store.saveSession(SourceSpec.file(), input)
        val sourceId = first.sourceId!!
        assertEquals(751, first.count)
        assertTrue(first.initialEntries.isEmpty())
        assertEquals(first.generation, store.openSession()!!.generation)
        val originalFirst = store.page(BrowseRequest(sourceId, first.generation), 0, 60).items.first()
        val originalLast = store.page(BrowseRequest(sourceId, first.generation), 750, 60).items.single()
        val refreshed = store.saveSession(SourceSpec.file(), input.reversed(), true, sourceId)
        assertEquals(sourceId, refreshed.sourceId)
        assertEquals(751, refreshed.count)
        assertTrue(refreshed.initialEntries.isEmpty())
        assertNotEquals(first.generation, refreshed.generation)
        val reopened = store.openSession()!!
        assertEquals(refreshed.generation, reopened.generation)
        assertEquals(refreshed.savedAtMillis, reopened.savedAtMillis)
        val request = BrowseRequest(sourceId, refreshed.generation)
        assertEquals(originalLast.id, store.page(request, 0, 60).items.first().id)
        assertEquals(originalFirst.id, store.page(request, 750, 60).items.single().id)
        assertEquals("https://example.invalid/750", store.item(sourceId, refreshed.generation, originalLast.id)!!.address!!.reveal())
        assertEquals("https://images.invalid/750", store.artwork(LibraryArtwork(sourceId, originalLast.id, refreshed.generation))!!.reveal())
        assertEquals(listOf(751, 751, 751), counts(name))
    }

    @Test fun repeatedRefreshReplacesEntryAndSearchRowsWithoutGrowingIdentities() = fixture { name, store ->
        val input = (0..750).map { entry(it) }
        val first = store.save(SourceSpec.file(), input)
        repeat(3) { round ->
            val refreshed = store.save(SourceSpec.file(), if (round % 2 == 0) input.reversed() else input, true, first.sourceId)
            assertEquals(first.entries.map { it.channel.id }.toSet(), refreshed.entries.map { it.channel.id }.toSet())
            assertEquals(listOf(751, 751, 751), counts(name))
            val session = store.openSession()!!
            assertEquals(751, store.page(BrowseRequest(first.sourceId!!, session.generation, query = "Candidate"), 0, 60).total)
        }
    }
}
