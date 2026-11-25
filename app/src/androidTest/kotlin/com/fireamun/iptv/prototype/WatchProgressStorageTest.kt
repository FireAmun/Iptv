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
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class WatchProgressStorageTest {
    @get:Rule val diagnostics = TestDiagnostics()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(test: suspend (String, KeystoreEnvelope) -> Unit) = runBlocking {
        val name = "watch-${UUID.randomUUID()}.db"
        val protector = KeystoreEnvelope("iptv.test.$name")
        try { test(name, protector) } finally { context.deleteDatabase(name); protector.clear() }
    }
    private fun entry(title: String, kind: ContentKind, key: String? = null) = PlaylistEntry(
        Channel(0, title, null, kind), if (kind == ContentKind.SERIES) null else MediaAddress.parse("https://example.invalid/fixture")!!,
        if (kind == ContentKind.SERIES) 7L else null, key)

    @Test fun moviePositionAndRecentRowSurviveReopenWithoutPlaintextLocator() = fixture { name, protector ->
        var store = RoomLibraryStore.open(context, name, protector)
        try {
            val saved = store.save(SourceSpec.file(), listOf(entry("Private movie fixture", ContentKind.MOVIE)))
            val sourceId = saved.sourceId!!
            val target = ProgressTarget(sourceId, saved.entries.single().channel.id, ContentKind.MOVIE)
            store.save(target, WatchProgress(12_000, 30_000, false, 100))
            store.close(); store = RoomLibraryStore.open(context, name, protector)
            assertEquals(12_000L, store.read(target)!!.progress.positionMs)
            val recent = store.recent(sourceId, ContentKind.MOVIE).single()
            assertEquals(target.rootItemId, recent.rootItemId)
            assertEquals("Private movie fixture", recent.title)
            assertEquals(target, store.read(recent.reference)!!.target)
            assertFalse(recent.reference.toString().contains(sourceId))
        } finally { store.close() }
    }

    @Test fun keyedEpisodeSnapshotSurvivesReopenAndIsEncrypted() = fixture { name, protector ->
        var store = RoomLibraryStore.open(context, name, protector)
        try {
            val saved = store.save(SourceSpec.file(), listOf(entry("Parent series", ContentKind.SERIES, "SERIES:7")))
            val target = ProgressTarget(saved.sourceId!!, saved.entries.single().channel.id, ContentKind.EPISODE, "EPISODE:31")
            val snapshot = EpisodeSnapshot("EPISODE:31", "Private episode fixture", 1, 2, MediaAddress.parse("https://example.invalid/private-episode-31")!!)
            store.save(target, WatchProgress(15_000, 30_000, false, 200), snapshot)
            store.close(); store = RoomLibraryStore.open(context, name, protector)
            val recent = store.recent(saved.sourceId!!, ContentKind.EPISODE).single()
            assertEquals("Parent series", recent.parentTitle)
            assertEquals(1, recent.season)
            assertEquals(2, recent.episode)
            assertEquals("Private episode fixture", recent.title)
            assertEquals("https://example.invalid/private-episode-31", store.read(recent.reference)!!.snapshot!!.address.reveal())
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT snapshot FROM watch_progress", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertFalse(cursor.getBlob(0).toString(Charsets.ISO_8859_1).contains("private-episode-31"))
                }
            }
        } finally { store.close() }
        for (file in context.getDatabasePath(name).parentFile!!.listFiles()!!.filter { it.name.startsWith(name) }) {
            val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(bytes.contains("Private episode fixture"))
            assertFalse(bytes.contains("private-episode-31"))
        }
    }

    @Test fun rootEpisodeNeedsNoProviderKeyAndWrongRootKindCannotBeSaved() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val saved = store.save(SourceSpec.file(), listOf(entry("Root episode", ContentKind.EPISODE)))
            val root = saved.entries.single().channel.id
            val target = ProgressTarget(saved.sourceId!!, root, ContentKind.EPISODE)
            store.save(target, WatchProgress(4_000, null, false, 5))
            assertEquals(4_000L, store.read(target)!!.progress.positionMs)
            assertEquals("Root episode", store.recent(saved.sourceId!!, ContentKind.EPISODE).single().title)
            try { store.save(ProgressTarget(saved.sourceId!!, root, ContentKind.MOVIE), WatchProgress(5_000, null, false, 6)); fail("Accepted wrong root kind") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
            try { store.save(ProgressTarget(saved.sourceId!!, root, ContentKind.EPISODE, "EPISODE:31"), WatchProgress(5_000, null, false, 6),
                EpisodeSnapshot("EPISODE:31", null, null, null, MediaAddress.parse("https://example.invalid/episode-31")!!)); fail("Accepted keyed episode under episode root") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
        } finally { store.close() }
    }

    @Test fun refreshAndRenameKeepProgressButMissingRootsStayHiddenUntilTheyReappear() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val source = SourceSpec.file()
            val initial = store.save(source, listOf(entry("Series before", ContentKind.SERIES, "SERIES:7")))
            val target = ProgressTarget(initial.sourceId!!, initial.entries.single().channel.id, ContentKind.EPISODE, "EPISODE:31")
            val snapshot = EpisodeSnapshot("EPISODE:31", "Episode before", 1, 2, MediaAddress.parse("https://example.invalid/episode-31")!!)
            store.save(target, WatchProgress(8_000, 30_000, false, 5), snapshot)
            store.rename(initial.sourceId!!, "Renamed fixture")
            val refreshed = store.save(source, listOf(entry("Other series", ContentKind.SERIES, "SERIES:8")), true, initial.sourceId)
            assertTrue(store.recent(initial.sourceId!!, ContentKind.EPISODE).isEmpty())
            assertNull(store.read(target))
            store.save(source, listOf(entry("Series after", ContentKind.SERIES, "SERIES:7")), true, initial.sourceId)
            val recent = store.recent(initial.sourceId!!, ContentKind.EPISODE).single()
            assertEquals("Series after", recent.parentTitle)
            assertEquals(8_000L, store.read(recent.reference)!!.progress.positionMs)
            assertEquals(target.rootItemId, recent.rootItemId)
            assertEquals("Renamed fixture", store.directory().sources.single().name)
            assertNotEquals(refreshed.entries.single().channel.id, recent.rootItemId)
        } finally { store.close() }
    }

    @Test fun recentJoinsAvailableRootsBeforeTakingTwentyAndSeparatesSources() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val source = SourceSpec.file()
            val initial = store.save(source, (1..30).map { entry("Film $it", ContentKind.MOVIE, "MOVIE:$it") })
            val id = initial.sourceId!!
            initial.entries.forEachIndexed { index, item ->
                store.save(ProgressTarget(id, item.channel.id, ContentKind.MOVIE), WatchProgress(1_000L + index, null, false, index.toLong()))
            }
            store.save(source, (1..20).map { entry("Retained $it", ContentKind.MOVIE, "MOVIE:$it") }, true, id)
            assertEquals(20, store.recent(id, ContentKind.MOVIE).size)
            val second = store.save(source, listOf(entry("Other source", ContentKind.MOVIE, "MOVIE:1")))
            assertTrue(store.recent(second.sourceId!!, ContentKind.MOVIE).isEmpty())
            assertEquals(20, store.recent(id, ContentKind.MOVIE).size)
        } finally { store.close() }
    }

    @Test fun failedSaveKeepsPreviousRecordAndCorruptSnapshotFailsClosed() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val saved = store.save(SourceSpec.file(), listOf(entry("Series", ContentKind.SERIES, "SERIES:7")))
            val target = ProgressTarget(saved.sourceId!!, saved.entries.single().channel.id, ContentKind.EPISODE, "EPISODE:31")
            val snapshot = EpisodeSnapshot("EPISODE:31", "Safe episode", 1, 2, MediaAddress.parse("https://example.invalid/episode-31")!!)
            store.save(target, WatchProgress(9_000, null, false, 5), snapshot)
            try { store.save(target, WatchProgress(2_000, null, false, 6), EpisodeSnapshot("EPISODE:32", null, null, null, snapshot.address)); fail("Accepted mismatched snapshot") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
            assertEquals(9_000L, store.read(target)!!.progress.positionMs)
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("UPDATE watch_progress SET snapshot = zeroblob(64) WHERE sourceId = ?", arrayOf(saved.sourceId))
            }
            try { store.read(target); fail("Accepted corrupt snapshot") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
        } finally { store.close() }
    }

    @Test fun referenceReadRevalidatesAuthenticatedRootIdentity() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val saved = store.save(SourceSpec.file(), listOf(entry("First", ContentKind.MOVIE, "MOVIE:1"), entry("Second", ContentKind.MOVIE, "MOVIE:2")))
            val original = ProgressTarget(saved.sourceId!!, saved.entries[0].channel.id, ContentKind.MOVIE)
            store.save(original, WatchProgress(5_000, null, false, 1))
            val reference = store.recent(saved.sourceId!!, ContentKind.MOVIE).single().reference
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("UPDATE watch_progress SET rootItemId = ? WHERE sourceId = ?", arrayOf(saved.entries[1].channel.id, saved.sourceId))
            }
            try { store.read(reference); fail("Accepted moved history reference") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
        } finally { store.close() }
    }

    @Test fun completedProgressLeavesRecentAndResetRemovesSavedPosition() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val saved = store.save(SourceSpec.file(), listOf(entry("Movie", ContentKind.MOVIE)))
            val target = ProgressTarget(saved.sourceId!!, saved.entries.single().channel.id, ContentKind.MOVIE)
            store.save(target, WatchProgress(30_000, 30_000, true, 5))
            assertTrue(store.recent(saved.sourceId!!, ContentKind.MOVIE).isEmpty())
            assertTrue(store.read(target)!!.progress.completed)
            store.reset(target)
            assertNull(store.read(target))
        } finally { store.close() }
    }

    @Test fun targetedRemoveAndClearDeleteHistoryAndRejectDelayedWrites() = fixture { name, protector ->
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            val first = store.save(SourceSpec.file(), listOf(entry("First", ContentKind.MOVIE)))
            val firstTarget = ProgressTarget(first.sourceId!!, first.entries.single().channel.id, ContentKind.MOVIE)
            store.save(firstTarget, WatchProgress(2_000, null, false, 1))
            val second = store.save(SourceSpec.file(), listOf(entry("Second", ContentKind.MOVIE)))
            val secondTarget = ProgressTarget(second.sourceId!!, second.entries.single().channel.id, ContentKind.MOVIE)
            store.save(secondTarget, WatchProgress(3_000, null, false, 2))
            store.remove(first.sourceId!!)
            assertNull(store.read(firstTarget))
            assertEquals(3_000L, store.read(secondTarget)!!.progress.positionMs)
            try { store.save(firstTarget, WatchProgress(4_000, null, false, 3)); fail("Recreated removed source") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
            store.clear()
            assertNull(store.read(secondTarget))
            try { store.save(secondTarget, WatchProgress(4_000, null, false, 3)); fail("Recreated cleared source") }
            catch (failure: ImportFailure) { assertEquals(ImportError.STORAGE, failure.code) }
        } finally { store.close() }
    }

    @Test fun schemaTwoThreeAndFourUpgradePreservesOldSourceCiphertextAndItemId() = runBlocking {
        for (version in 2..4) {
            val name = "watch-migration-$version-${UUID.randomUUID()}.db"
            val protector = KeystoreEnvelope("iptv.test.$name")
            val sourceId = "legacy-source-$version"
            val generation = "legacy-generation"
            val source = SourceSpec.file()
            val key = ByteArray(32) { (it + 1).toByte() }
            val sourceBytes = source.encode()
            val envelope = ByteArrayOutputStream().apply { write(key); write(sourceBytes) }.toByteArray()
            val secret = protector.seal(envelope, "source:$sourceId")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
                updateAAD("entry:$sourceId:$generation:123".toByteArray())
            }
            val address = byteArrayOf(1) + cipher.iv + cipher.doFinal("https://example.invalid/legacy-movie".toByteArray())
            try {
                val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.fireamun.iptv.core.storage.LibraryDatabase/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")
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
                    db.execSQL("INSERT INTO source_state(slot,sourceId,generation,secret,savedAt,kind) VALUES (1,?,?,?,?,?)", arrayOf(sourceId, generation, secret, 1234, "FILE"))
                    db.execSQL("INSERT INTO active_library(slot,sourceId) VALUES (1,?)", arrayOf(sourceId))
                    db.execSQL("INSERT INTO item_identities(id,sourceId,identityKey) VALUES (123,?,?)", arrayOf(sourceId, "old-digest:0"))
                    db.execSQL("INSERT INTO library_entries(id,position,title,kind,providerKey,address,sourceId) VALUES (123,0,'Legacy movie','MOVIE','MOVIE:7',?,?)", arrayOf(address, sourceId))
                    db.version = version
                }
                val store = RoomLibraryStore.open(context, name, protector)
                try {
                    val restored = store.restore()!!
                    assertEquals(sourceId, restored.sourceId)
                    assertEquals(123, restored.entries.single().channel.id)
                    assertEquals("https://example.invalid/legacy-movie", restored.entries.single().address!!.reveal())
                    val target = ProgressTarget(sourceId, 123, ContentKind.MOVIE)
                    store.save(target, WatchProgress(6_000, null, false, 7))
                    assertEquals(6_000L, store.read(target)!!.progress.positionMs)
                    // Version 5 adds an empty guide; upgraded rows have no guide key until their playlist is refreshed.
                    assertTrue(store.guideKeys(sourceId).isEmpty())
                    assertEquals(0, store.guideState(sourceId)!!.programmeCount)
                    SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        db.rawQuery("SELECT secret FROM source_state WHERE sourceId = ?", arrayOf(sourceId)).use { cursor -> assertTrue(cursor.moveToFirst()); assertArrayEquals(secret, cursor.getBlob(0)) }
                        db.rawQuery("SELECT address FROM library_entries WHERE id = 123", null).use { cursor -> assertTrue(cursor.moveToFirst()); assertArrayEquals(address, cursor.getBlob(0)) }
                    }
                } finally { store.close() }
            } finally { key.fill(0); sourceBytes.fill(0); envelope.fill(0); context.deleteDatabase(name); protector.clear() }
        }
    }
}
