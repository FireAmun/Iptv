package com.fireamun.iptv.prototype

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import com.fireamun.iptv.core.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.util.UUID
import android.database.sqlite.SQLiteDatabase

@RunWith(AndroidJUnit4::class)
class RoomLibraryStoreTest {
    @get:Rule val diagnostics = TestDiagnostics()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun entry(title: String, path: String = title) = PlaylistEntry(Channel(0, title, "Fixture"), MediaAddress.parse("https://example.invalid/$path?token=protected-fixture"))
    private fun source() = SourceSpec.xtream("Saved fixture", XtreamConnection.create("https://example.invalid", "fixture-user", "fixture-password")!!)
    private fun fixture(test: suspend (String, KeystoreEnvelope) -> Unit) = runBlocking {
        val name = "fixture-${UUID.randomUUID()}.db"
        val protector = KeystoreEnvelope("iptv.test.$name")
        try { test(name, protector) } finally { context.deleteDatabase(name); protector.clear() }
    }
    @Test fun realKeystoreAndRoomReopenRestoreProtectedConnectionAndCatalogue() = fixture { name, protector ->
        val original = source()
        val first = RoomLibraryStore.open(context, name, protector)
        val saved = first.save(original, listOf(entry("Live")))
        first.close()
        val second = RoomLibraryStore.open(context, name, KeystoreEnvelope("iptv.test.$name"))
        try {
            val restored = second.restore()!!
            assertArrayEquals(original.encode(), restored.source.encode())
            assertEquals(saved.entries.single().channel.id, restored.entries.single().channel.id)
            assertEquals("Live", restored.entries.single().channel.title)
            assertEquals(saved.entries.single().address!!.reveal(), restored.entries.single().address!!.reveal())
        } finally { second.close() }
        for (file in context.getDatabasePath(name).parentFile!!.listFiles()!!.filter { it.name.startsWith(name) }) {
            val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
            for (privateValue in listOf("fixture-password", "fixture-user", "example.invalid", "protected-fixture")) assertFalse("Protected value must not appear in database", bytes.contains(privateValue))
        }
    }
    @Test fun failedDatabaseActivationRollsBackToPreviousSnapshot() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            store.save(SourceSpec.file(), listOf(entry("Working")))
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("CREATE TRIGGER reject_fixture BEFORE INSERT ON library_entries WHEN NEW.title = 'Rejected' BEGIN SELECT RAISE(ABORT, 'Synthetic activation rejection'); END")
            }
            try { store.save(SourceSpec.file(), listOf(entry("Rejected")), true); fail("Expected rollback") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code); assertNull(failure.cause) }
            assertEquals("Working", store.restore()!!.entries.single().channel.title)
        } finally { store.close() }
    }
    @Test fun missingKeyFailsClosedUntilExplicitClear() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            store.save(source(), listOf(entry("Working")))
            protector.clear()
            for (action in listOf<suspend () -> Unit>({ store.restore() }, { store.save(SourceSpec.file(), listOf(entry("Replacement"))) })) {
                try { action(); fail("Expected protected storage failure") }
                catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code); assertNull(failure.cause) }
            }
            store.clear(); assertNull(store.restore())
            store.save(SourceSpec.file(), listOf(entry("Recovered")))
            assertEquals("Recovered", store.restore()!!.entries.single().channel.title)
        } finally { store.close() }
    }
    @Test fun refreshPreservesConfidentIdsAndDistinctDuplicateEntries() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val first = store.save(SourceSpec.file(), listOf(entry("A"), entry("B"), entry("A")))
            val next = store.save(SourceSpec.file(), listOf(entry("B"), entry("Renamed A", "A"), entry("A")), true)
            assertEquals(first.entries[1].channel.id, next.entries[0].channel.id)
            assertEquals(first.entries[0].channel.id, next.entries[1].channel.id)
            assertEquals(first.entries[2].channel.id, next.entries[2].channel.id)
            assertNotEquals(next.entries[1].channel.id, next.entries[2].channel.id)
            store.clear(); assertNull(store.restore())
        } finally { store.close() }
    }
    @Test fun tamperedPlaybackCiphertextIsNeverReturnedAsAnAddress() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            store.save(SourceSpec.file(), listOf(entry("Protected")))
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("UPDATE library_entries SET address = zeroblob(64)") }
            try { store.restore(); fail("Expected authenticated-storage rejection") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code); assertNull(failure.cause) }
        } finally { store.close() }
    }
    @Test fun failedEncryptionNeverReplacesTheWorkingCatalogue() = fixture { name, protector ->
        var rejectEncryption = false
        val wrapping = object : EnvelopeProtector by protector {
            override fun seal(plaintext: ByteArray, context: String): ByteArray {
                if (rejectEncryption) throw java.security.ProviderException("Synthetic encryption rejection")
                return protector.seal(plaintext, context)
            }
        }
        val store = RoomLibraryStore.open(context, name, wrapping)
        try {
            store.save(SourceSpec.file(), listOf(entry("Working")))
            rejectEncryption = true
            try { store.save(SourceSpec.file(), listOf(entry("New"))); fail("Expected safe rejection") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
            assertEquals("Working", store.restore()!!.entries.single().channel.title)
        } finally { store.close() }
    }
    @Test fun catalogueSpansDatabaseBatchesAndRetainsIdsWhenReordered() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val input = (0..600).map { entry("Fixture $it", "live$it") }
            val saved = store.save(SourceSpec.file(), input)
            assertEquals(601, store.restore()!!.entries.size)
            val refreshed = store.save(SourceSpec.file(), input.reversed(), true)
            assertEquals(saved.entries.map { it.channel.id }.reversed(), refreshed.entries.map { it.channel.id })
            assertEquals(601, refreshed.entries.map { it.channel.id }.toSet().size)
        } finally { store.close() }
    }

    @Test fun largeCatalogueSurvivesEncryptedSaveRefreshAndReopen() = fixture { name, protector ->
        val input = (0 until 100_000).map { index ->
            PlaylistEntry(Channel(index, "Large fixture $index", "Synthetic", ContentKind.LIVE),
                MediaAddress.parse("https://example.invalid/live/$index"), providerKey = "LIVE:$index")
        }
        val store = RoomLibraryStore.open(context, name, protector)
        val firstId: Int
        val lastId: Int
        try {
            val saved = store.save(SourceSpec.file(), input)
            firstId = saved.entries.first().channel.id
            lastId = saved.entries.last().channel.id
            val refreshed = store.save(SourceSpec.file(), input.reversed(), true, saved.sourceId)
            assertEquals(100_000, refreshed.entries.size)
            assertEquals(lastId, refreshed.entries.first().channel.id)
            assertEquals(firstId, refreshed.entries.last().channel.id)
        } finally { store.close() }
        val reopened = RoomLibraryStore.open(context, name, protector)
        try {
            val restored = reopened.restore()!!
            assertEquals(100_000, restored.entries.size)
            assertEquals(lastId, restored.entries.first().channel.id)
            assertEquals(firstId, restored.entries.last().channel.id)
            assertTrue(restored.entries.last().address!!.reveal().endsWith("/0"))
        } finally { reopened.close() }
    }
}
