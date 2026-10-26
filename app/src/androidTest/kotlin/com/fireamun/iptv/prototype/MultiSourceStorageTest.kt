package com.fireamun.iptv.prototype

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.*
import com.fireamun.iptv.core.storage.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class MultiSourceStorageTest {
    @get:Rule val diagnostics = TestDiagnostics()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun entry(title: String, path: String = title.replace(' ', '-')) = PlaylistEntry(Channel(0, title, null), MediaAddress.parse("https://example.invalid/$path") ?: error("Invalid synthetic fixture address"))
    private fun fixture(test: suspend (String, KeystoreEnvelope) -> Unit) = runBlocking {
        val name = "multi-${UUID.randomUUID()}.db"
        val protector = KeystoreEnvelope("iptv.test.$name")
        try { test(name, protector) } finally { context.deleteDatabase(name); protector.clear() }
    }
    @Test fun separateCataloguesSurviveReopenSelectionAndTargetedRemoval() = fixture { name, protector ->
        var store = RoomLibraryStore.open(context, name, protector)
        try {
            val first = store.save(SourceSpec.file(), listOf(entry("First")))
            val second = store.save(SourceSpec.url(MediaAddress.parse("https://example.invalid/list")!!), listOf(entry("Second")))
            assertNotEquals(first.sourceId, second.sourceId)
            assertNotEquals(first.entries.single().channel.id, second.entries.single().channel.id)
            assertEquals(2, store.directory().sources.size)
            store.close(); store = RoomLibraryStore.open(context, name, protector)
            assertEquals(second.sourceId, store.restore()!!.sourceId)
            store.activate(first.sourceId!!)
            assertEquals("First", store.restore()!!.entries.single().channel.title)
            store.remove(second.sourceId!!)
            assertEquals(first.sourceId, store.restore()!!.sourceId)
            assertEquals("First", store.restore()!!.entries.single().channel.title)
            assertEquals(1, store.directory().sources.size)
            store.remove(first.sourceId!!)
            assertNull(store.restore()); assertTrue(store.directory().sources.isEmpty())
            store.save(SourceSpec.file(), listOf(entry("After removal")))
            assertEquals("After removal", store.restore()!!.entries.single().channel.title)
        } finally { store.close() }
    }
    @Test fun refreshingAndEncryptedRenameDoNotModifyOtherPlaylists() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val first = store.save(SourceSpec.file(), listOf(entry("First", "same")))
            val second = store.save(SourceSpec.file(), listOf(entry("Second")))
            store.rename(first.sourceId!!, "Private friendly fixture")
            assertEquals(second.sourceId, store.directory().activeSourceId)
            store.activate(first.sourceId!!)
            val refreshed = store.save(SourceSpec.file(), listOf(entry("First updated", "same")), true)
            assertEquals(first.entries.single().channel.id, refreshed.entries.single().channel.id)
            assertEquals("Private friendly fixture", store.directory().sources.first().name)
            assertEquals("Second", store.activate(second.sourceId!!).entries.single().channel.title)
        } finally { store.close() }
        for (file in context.getDatabasePath(name).parentFile!!.listFiles()!!.filter { it.name.startsWith(name) }) {
            assertFalse("Friendly label must be encrypted", file.readBytes().toString(Charsets.ISO_8859_1).contains("Private friendly fixture"))
        }
    }
    @Test fun corruptInactiveSourceCannotReplaceWorkingSelectionAndCanBeRemovedAlone() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val first = store.save(SourceSpec.file(), listOf(entry("First")))
            val second = store.save(SourceSpec.file(), listOf(entry("Second")))
            store.activate(first.sourceId!!)
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("UPDATE library_entries SET address = zeroblob(64) WHERE sourceId = ?", arrayOf(second.sourceId))
            }
            try { store.activate(second.sourceId!!); fail("Corrupt source must not activate") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
            assertEquals(first.sourceId, store.restore()!!.sourceId)
            store.remove(second.sourceId!!)
            assertEquals("First", store.restore()!!.entries.single().channel.title)
        } finally { store.close() }
    }
    @Test fun refreshUsesTheExplicitSourceEvenWhenAnotherIdenticalConnectionIsSelected() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val source = SourceSpec.url(MediaAddress.parse("https://example.invalid/list")!!)
            val first = store.save(source, listOf(entry("First")))
            val second = store.save(source, listOf(entry("Second")))
            store.save(source, listOf(entry("First updated")), refreshing = true, targetSourceId = first.sourceId)
            assertEquals(first.sourceId, store.restore()!!.sourceId)
            assertEquals("First updated", store.restore()!!.entries.single().channel.title)
            assertEquals("Second", store.activate(second.sourceId!!).entries.single().channel.title)
            store.remove(second.sourceId!!)
            assertEquals(first.sourceId, store.restore()!!.sourceId)
        } finally { store.close() }
    }
    @Test fun keyCleanupFailureAfterDeletionDoesNotReviveTheRemovedSourceAndFreshImportRetriesCleanup() = fixture { name, protector ->
        var rejectCleanup = false
        val wrapping = object : EnvelopeProtector by protector {
            override fun clear() {
                if (rejectCleanup) { rejectCleanup = false; throw java.security.ProviderException("Synthetic cleanup rejection") }
                protector.clear()
            }
        }
        val store = RoomLibraryStore.open(context, name, wrapping)
        try {
            val saved = store.save(SourceSpec.file(), listOf(entry("Before")))
            val oldSecret = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT secret FROM source_state WHERE sourceId = ?", arrayOf(saved.sourceId)).use { cursor -> check(cursor.moveToFirst()); cursor.getBlob(0) }
            }
            rejectCleanup = true
            store.remove(saved.sourceId!!)
            assertNull(store.restore()); assertTrue(store.directory().sources.isEmpty())
            store.save(SourceSpec.file(), listOf(entry("After")))
            assertEquals("After", store.restore()!!.entries.single().channel.title)
            try { protector.open(oldSecret, "source:${saved.sourceId}"); fail("Fresh import must rotate the orphaned wrapping key") }
            catch (_: java.security.GeneralSecurityException) { }
        } finally { store.close() }
    }
    @Test fun versionOneEncryptedLibraryMigratesWithoutChangingItsKeyCiphertextOrItemIdentity() = fixture { name, protector ->
        val source = SourceSpec.xtream("Legacy playlist", XtreamConnection.create("https://example.invalid", "fixture-user", "fixture-password")!!)
        val address = MediaAddress.parse("https://example.invalid/legacy")!!
        val key = ByteArray(32) { (it + 1).toByte() }
        val payload = ByteArrayOutputStream().apply { write(key); write(source.encode()) }.toByteArray()
        val secret = protector.seal(payload, "source:legacy-source")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            updateAAD("entry:legacy-source:legacy-generation:42".toByteArray())
        }
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(address.reveal().toByteArray())
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val identity = mac.doFinal("locator:${address.reveal()}".toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } + ":0"
        val schema = JSONObject(instrumentation.context.assets.open("com.fireamun.iptv.core.storage.LibraryDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { i ->
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) repeat(indices.length()) { j -> db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { db.execSQL(setup.getString(it)) }
            db.execSQL("INSERT INTO source_state VALUES (1, ?, ?, ?, ?)", arrayOf("legacy-source", "legacy-generation", secret, 1234))
            db.execSQL("INSERT INTO item_identities VALUES (42, ?, ?)", arrayOf("legacy-source", identity))
            db.execSQL("INSERT INTO library_entries VALUES (42, 0, 'Legacy', NULL, 'UNKNOWN', NULL, NULL, ?)", arrayOf(encrypted))
            db.version = 1
        }
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val restored = store.restore()!!
            assertEquals("legacy-source", restored.sourceId)
            assertEquals(42, restored.entries.single().channel.id)
            assertEquals(address.reveal(), restored.entries.single().address!!.reveal())
            assertEquals(1, store.directory().sources.size)
            assertEquals("Legacy playlist", store.directory().sources.single().name)
            val refreshed = store.save(source, listOf(entry("Legacy renamed", "legacy")), true)
            assertEquals(42, refreshed.entries.single().channel.id)
            assertEquals("Legacy playlist", store.directory().sources.single().name)
            val second = store.save(SourceSpec.file(), listOf(entry("Second")))
            store.remove(second.sourceId!!)
            assertEquals("legacy-source", store.restore()!!.sourceId)
        } finally { store.close(); key.fill(0); payload.fill(0) }
    }
}
