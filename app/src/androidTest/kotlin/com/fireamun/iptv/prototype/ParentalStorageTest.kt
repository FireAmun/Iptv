package com.fireamun.iptv.prototype

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
class ParentalStorageTest {
    @get:Rule val diagnostics = TestDiagnostics()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun lockedCategoriesLeaveBrowsingFavouritesAndSearchButStayListed() = runBlocking {
        val name = "parental-${UUID.randomUUID()}.db"
        val protector = KeystoreEnvelope("iptv.test.$name")
        val store = RoomLibraryStore.open(context, name, protector)
        try {
            fun entry(id: Int, title: String, kind: ContentKind, group: String?) =
                PlaylistEntry(Channel(id, title, group, kind), MediaAddress.parse("https://example.invalid/$id"), seriesId = if (kind == ContentKind.SERIES) id.toLong() else null, providerKey = "$kind:$id")
            val saved = store.save(SourceSpec.file(), listOf(
                entry(0, "Kids channel", ContentKind.LIVE, "Kids"),
                entry(1, "Night channel", ContentKind.LIVE, "Night"),
                entry(2, "Night film", ContentKind.MOVIE, "Night"),
                entry(3, "Night series", ContentKind.SERIES, "Night"),
                entry(4, "Night unknown", ContentKind.UNKNOWN, "Night"),
                entry(5, "Night ungrouped", ContentKind.LIVE, null),
            ))
            val ids = saved.entries.map { it.channel.id }
            val session = store.openSession()!!
            val open = BrowseRequest(session.sourceId!!, session.generation)
            val hidden = CategoryLocks().with(ContentKind.LIVE, "Night", true).with(ContentKind.SERIES, "Night", true)
            val locked = open.copy(hidden = hidden)
            fun BrowsePage.ids() = items.map { it.id }

            assertEquals(listOf(ids[0], ids[5]), store.page(locked.copy(kind = ContentKind.LIVE), 0, 60).ids())
            assertEquals("Movies keep their own Night category", listOf(ids[2]), store.page(locked.copy(kind = ContentKind.MOVIE), 0, 60).ids())
            assertTrue(store.page(locked.copy(kind = ContentKind.SERIES), 0, 60).items.isEmpty())
            assertEquals(listOf(ids[2], ids[5]), store.page(locked.copy(query = "night"), 0, 60).ids())
            assertEquals(6, store.page(open, 0, 60).total)

            store.setFavourite(session.sourceId!!, session.generation, ids[1], true)
            assertTrue(store.page(locked.copy(favouritesOnly = true), 0, 60).items.isEmpty())
            assertEquals(listOf(ids[1]), store.page(open.copy(favouritesOnly = true), 0, 60).ids())

            assertEquals("Locked categories stay listed so choosing one can ask for the PIN",
                listOf("Kids", "Night"), store.categories(locked.copy(kind = ContentKind.LIVE)))
        } finally { store.close(); context.deleteDatabase(name); protector.clear() }
    }
}
