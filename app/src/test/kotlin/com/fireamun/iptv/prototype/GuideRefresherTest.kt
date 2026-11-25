package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.data.*
import com.fireamun.iptv.core.model.ImportError
import com.fireamun.iptv.core.model.MediaAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GuideRefresherTest {
    private class MemoryGuide : GuidePersistence {
        var manual: MediaAddress? = null
        var playlist: MediaAddress? = null
        var xtream: MediaAddress? = null
        var fetched: Long? = null
        var attempted: Long? = null
        var count = 0
        var error: ImportError? = null
        var keys = setOf("id:a")
        var replaced = 0
        override suspend fun guideState(sourceId: String) = if (sourceId != "s") null else GuideState(sourceId, manual != null, playlist != null, fetched, attempted, count, error)
        override suspend fun guideLink(sourceId: String) = manual ?: playlist ?: xtream
        override suspend fun setManualGuideLink(sourceId: String, link: MediaAddress?) { manual = link; fetched = null; attempted = null; error = null }
        override suspend fun setPlaylistGuideLink(sourceId: String, link: MediaAddress?) { if (link?.reveal() != playlist?.reveal()) { playlist = link; if (manual == null) { fetched = null; attempted = null } } }
        override suspend fun guideKeys(sourceId: String) = keys
        override suspend fun replaceGuide(sourceId: String, guide: ParsedGuide, fetchedAtMs: Long) { replaced++; fetched = fetchedAtMs; attempted = fetchedAtMs; count = guide.programmeCount; error = null }
        override suspend fun guideFailed(sourceId: String, error: ImportError, attemptedAtMs: Long) { this.error = error; attempted = attemptedAtMs }
        override suspend fun schedule(sourceId: String, itemId: Int, fromMs: Long, untilMs: Long, limit: Int) = emptyList<GuideProgramme>()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val store = MemoryGuide()
    private var now = 100L * GuideRefresher.STALE_AFTER_MS
    private var wifi = true
    private val loaded = mutableListOf<String>()
    private var failure: ImportFailure? = null
    private fun refresher() = GuideRefresher(store, scope, { wifi }, { now }) { link, _, wanted ->
        loaded += link.reveal()
        failure?.let { throw it }
        ParsedGuide(mapOf("id:a" to "a"), mapOf("a" to listOf(GuideProgramme(now, now + 1, "Show")))).also { assertEquals(store.keys, wanted) }
    }
    private fun link(text: String) = MediaAddress.parse(text)!!
    @After fun close() = scope.cancel()

    @Test fun automaticRefreshWaitsForAnUnmeteredNetworkAndTwelveHours() {
        store.xtream = link("https://example.invalid/xmltv.php")
        wifi = false
        val guide = refresher()
        guide.select("s")
        assertTrue("Metered networks never download automatically", loaded.isEmpty())
        wifi = true
        guide.select(null); guide.select("s")
        assertEquals(listOf("https://example.invalid/xmltv.php"), loaded)
        assertEquals(1, guide.state.value.saved!!.programmeCount)
        now += GuideRefresher.STALE_AFTER_MS - 1
        guide.select(null); guide.select("s")
        assertEquals(1, loaded.size)
        now += 1
        guide.select(null); guide.select("s")
        assertEquals(2, loaded.size)
        guide.automatic = false
        now += GuideRefresher.STALE_AFTER_MS
        guide.select(null); guide.select("s")
        assertEquals(2, loaded.size)
    }

    @Test fun manualRefreshWorksOnAnyNetworkAndFailuresKeepTheOldGuideAndBackOff() {
        store.playlist = link("https://epg.invalid/guide.xml")
        wifi = false
        val guide = refresher()
        guide.select("s")
        guide.refresh()
        assertEquals(1, loaded.size)
        failure = ImportFailure(ImportError.HTTP_ERROR)
        wifi = true
        now += GuideRefresher.STALE_AFTER_MS
        guide.select(null); guide.select("s")
        assertEquals(2, loaded.size)
        assertEquals(ImportError.HTTP_ERROR, guide.state.value.saved!!.error)
        assertEquals(1, guide.state.value.saved!!.programmeCount)
        assertFalse(guide.state.value.refreshing)
        guide.select(null); guide.select("s")
        assertEquals("A failed attempt waits an hour before the next automatic try", 2, loaded.size)
        now += GuideRefresher.RETRY_AFTER_MS
        guide.select(null); guide.select("s")
        assertEquals(3, loaded.size)
    }

    @Test fun manualLinkOverridesPlaylistLinkAndInvalidLinksAreRefused() {
        val guide = refresher()
        guide.select("s")
        assertTrue(guide.state.value.noLink)
        assertFalse(guide.setManualLink("ftp://epg.invalid/guide.xml"))
        assertFalse(guide.setManualLink("not a link"))
        guide.imported("s", link("https://epg.invalid/playlist.xml"))
        assertEquals(listOf("https://epg.invalid/playlist.xml"), loaded)
        assertTrue(guide.setManualLink(" https://epg.invalid/mine.xml "))
        assertEquals("https://epg.invalid/mine.xml", loaded.last())
        assertTrue(guide.state.value.saved!!.hasManualLink)
        now += GuideRefresher.STALE_AFTER_MS
        guide.imported("s", link("https://epg.invalid/other.xml"))
        assertEquals("A manual link stays in use after a playlist refresh", "https://epg.invalid/mine.xml", loaded.last())
        assertTrue(guide.setManualLink(null))
        assertEquals("https://epg.invalid/other.xml", loaded.last())
    }

    @Test fun playlistsWithoutLiveChannelsSkipTheDownload() {
        store.xtream = link("https://example.invalid/xmltv.php")
        store.keys = emptySet()
        val guide = refresher()
        guide.select("s")
        assertTrue(loaded.isEmpty())
        assertEquals(1, store.replaced)
        assertEquals(0, guide.state.value.saved!!.programmeCount)
    }
}
