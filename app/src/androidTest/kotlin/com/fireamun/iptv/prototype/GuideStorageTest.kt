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
class GuideStorageTest {
    @get:Rule val diagnostics = TestDiagnostics()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = 1_795_608_000_000L
    private val hour = 3_600_000L

    private fun fixture(test: suspend (String, RoomLibraryStore) -> Unit) = runBlocking {
        val name = "guide-${UUID.randomUUID()}.db"
        val protector = KeystoreEnvelope("iptv.test.$name")
        val store = RoomLibraryStore.open(context, name, protector)
        try { test(name, store) } finally { store.close(); context.deleteDatabase(name); protector.clear() }
    }
    private fun live(id: Int, title: String, guideId: String? = null) =
        PlaylistEntry(Channel(id, title, "News", ContentKind.LIVE), MediaAddress.parse("https://example.invalid/live/$id"), providerKey = "LIVE:$id", guideId = guideId)
    private fun count(name: String, table: String, sourceId: String? = null): Int =
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM $table" + if (sourceId != null) " WHERE sourceId = ?" else "", sourceId?.let { arrayOf(it) }).use { it.moveToFirst(); it.getInt(0) }
        }
    private fun guide() = ParsedGuide(
        mapOf(GuideKeys.forId("news.one") to "news.one", GuideKeys.forTitle("Sport")!! to "sport.ch"),
        mapOf("news.one" to listOf(GuideProgramme(now - hour, now, "Earlier"), GuideProgramme(now, now + hour, "Now", "Headlines"), GuideProgramme(now + hour, now + 2 * hour, "Next")),
            "sport.ch" to listOf(GuideProgramme(now, now + 2 * hour, "Match"))))

    @Test fun entriesMatchTheGuideByIdOrTitleAndLinksStaySealed() = fixture { name, store ->
        val movie = PlaylistEntry(Channel(9, "Sport", "Films", ContentKind.MOVIE), MediaAddress.parse("https://example.invalid/movie"), providerKey = "MOVIE:9")
        val saved = store.save(SourceSpec.file(), listOf(live(0, "News One HD", "News.One"), live(1, "UK: Sport FHD"), live(2, "Unlisted"), movie))
        val sourceId = saved.sourceId!!
        val ids = saved.entries.map { it.channel.id }
        assertEquals(setOf("id:news.one", "title:sport", "title:unlisted"), store.guideKeys(sourceId))
        assertNull("No guide link until one is known", store.guideLink(sourceId))
        assertEquals(0, store.guideState(sourceId)!!.programmeCount)

        store.replaceGuide(sourceId, guide(), now)
        assertEquals(listOf("Now", "Next"), store.schedule(sourceId, ids[0], now, now + 48 * hour).map { it.title })
        assertEquals("Headlines", store.schedule(sourceId, ids[0], now, now + 1).single().description)
        assertEquals(listOf("Match"), store.schedule(sourceId, ids[1], now, now + hour).map { it.title })
        assertTrue(store.schedule(sourceId, ids[2], now, now + hour).isEmpty())
        assertTrue("Movies never take a guide key", store.schedule(sourceId, ids[3], now, now + hour).isEmpty())
        assertEquals(4, store.guideState(sourceId)!!.programmeCount)
        assertEquals(now, store.guideState(sourceId)!!.fetchedAtMs)

        val playlistLink = MediaAddress.parse("https://epg.invalid/guide.xml?token=guide-secret")!!
        store.setPlaylistGuideLink(sourceId, playlistLink)
        assertEquals(playlistLink.reveal(), store.guideLink(sourceId)!!.reveal())
        assertNull("A new playlist link makes the schedule due", store.guideState(sourceId)!!.fetchedAtMs)
        store.setManualGuideLink(sourceId, MediaAddress.parse("https://epg.invalid/mine.xml?token=manual-secret"))
        assertEquals("https://epg.invalid/mine.xml?token=manual-secret", store.guideLink(sourceId)!!.reveal())
        assertTrue(store.guideState(sourceId)!!.hasManualLink)
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT manualLink, playlistLink FROM guide_state", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                for (column in 0..1) assertFalse(String(cursor.getBlob(column), Charsets.ISO_8859_1).contains("secret"))
            }
        }
        store.guideFailed(sourceId, ImportError.HTTP_ERROR, now + 5)
        assertEquals(ImportError.HTTP_ERROR, store.guideState(sourceId)!!.error)
        assertEquals("A failure keeps the last schedule", 1, store.schedule(sourceId, ids[1], now, now + hour).size)

        // A playlist refresh keeps the guide link and schedule; only the playlist's own guide link can change.
        store.save(SourceSpec.file(), listOf(live(0, "News One HD", "news.one"), live(1, "Sport")), refreshing = true, targetSourceId = sourceId)
        assertEquals("https://epg.invalid/mine.xml?token=manual-secret", store.guideLink(sourceId)!!.reveal())
        store.setManualGuideLink(sourceId, null)
        assertEquals(playlistLink.reveal(), store.guideLink(sourceId)!!.reveal())
        store.setPlaylistGuideLink(sourceId, null)
        assertNull(store.guideLink(sourceId))
        assertEquals(1, store.schedule(sourceId, ids[1], now, now + hour).size)

        val login = XtreamConnection.create("https://example.invalid/base", "fixture-user", "fixture-password")!!
        val xtream = store.save(SourceSpec.xtream("Fixture", login), listOf(live(0, "Xtream live", "x.1"))).sourceId!!
        assertEquals("https://example.invalid/base/xmltv.php?username=fixture-user&password=fixture-password", store.guideLink(xtream)!!.reveal())
        assertNull(store.guideState("missing-source"))
    }

    @Test fun removingOrClearingSourcesDeletesTheirGuide() = fixture { name, store ->
        val first = store.save(SourceSpec.file(), listOf(live(0, "News One", "news.one"))).sourceId!!
        val second = store.save(SourceSpec.file(), listOf(live(0, "Sport"))).sourceId!!
        for (source in listOf(first, second)) {
            store.replaceGuide(source, guide(), now)
            store.setManualGuideLink(source, MediaAddress.parse("https://epg.invalid/$source.xml"))
        }
        assertEquals(4, count(name, "guide_programmes", first))
        store.remove(first)
        for (table in listOf("guide_programmes", "guide_channels", "guide_state")) assertEquals(table, 0, count(name, table, first))
        assertEquals(4, count(name, "guide_programmes", second))
        store.clear()
        for (table in listOf("guide_programmes", "guide_channels", "guide_state")) assertEquals(table, 0, count(name, table))
    }
}
