package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.ImportError
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class GuideParsingTest {
    // 2026-11-25 12:00:00 UTC
    private val now = 1_795_608_000_000L
    private val hour = 3_600_000L
    private fun xml(body: String) = """<?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv generator-info-name="fixture">$body</tv>""".byteInputStream()

    @Test fun xmltvTimesUseTheirOffsetAndDefaultToUtc() {
        assertEquals(now, XmltvParser.parseTime("20261125120000 +0000"))
        assertEquals(now, XmltvParser.parseTime("20261125140000 +0200"))
        assertEquals(now, XmltvParser.parseTime("20261125070000 -0500"))
        assertEquals(now, XmltvParser.parseTime("20261125120000"))
        assertEquals(now, XmltvParser.parseTime("202611251200"))
        listOf("", "2026", "20261325120000", "20261125250000", "20261125120000 +2500", "20261125120000 abc", "x0261125120000").forEach {
            assertNull(it, XmltvParser.parseTime(it))
        }
    }

    @Test fun readsChannelsAndProgrammesWithEntitiesCdataAndOffsets() {
        val guide = XmltvParser().parse(xml("""
            <channel id="news.one"><display-name lang="en">News One HD</display-name><display-name>Alt &amp; Name</display-name></channel>
            <programme start="20261125110000 +0000" stop="20261125123000 +0000" channel="news.one">
              <title lang="en">Morning &amp; More</title><title lang="ar">ثانٍ</title><desc><![CDATA[Live <b>news</b> & weather]]></desc>
            </programme>
            <programme start="20261125143000 +0200" stop="20261125133000 +0000" channel="news.one"><title>Midday &#x41;&#66;</title></programme>
            <!-- <programme start="20261125120000" channel="news.one"><title>Commented</title></programme> -->
        """), now)
        val list = guide.programmes.getValue("news.one")
        assertEquals(listOf("Morning & More", "Midday AB"), list.map { it.title })
        assertEquals("Live <b>news</b> & weather", list[0].description)
        assertEquals(now - hour, list[0].startMs)
        assertEquals(now + hour / 2, list[0].endMs)
        assertEquals(now + hour / 2, list[1].startMs)
        assertEquals("news.one", guide.aliases[GuideKeys.forId("NEWS.ONE")])
        assertEquals("news.one", guide.aliases[GuideKeys.forTitle("UK: News One")])
        assertEquals("news.one", guide.aliases[GuideKeys.forTitle("alt & name")])
        assertEquals(2, guide.programmeCount)
    }

    @Test fun keepsOnlyTheWindowAndOnlyWantedChannels() {
        val guide = XmltvParser().parse(xml("""
            <channel id="a"><display-name>Alpha</display-name></channel>
            <channel id="b"><display-name>Beta HD</display-name></channel>
            <channel id="c"><display-name>Gamma</display-name></channel>
            <programme start="20261125080000" stop="20261125090000" channel="a"><title>Too old</title></programme>
            <programme start="20261125100000" stop="20261125113000" channel="a"><title>Recent</title></programme>
            <programme start="20261128000000" stop="20261128010000" channel="a"><title>Too far</title></programme>
            <programme start="20261125120000" stop="20261125130000" channel="b"><title>By name</title></programme>
            <programme start="20261125120000" stop="20261125130000" channel="c"><title>Unwanted</title></programme>
        """), now, wanted = setOf(GuideKeys.forId("a"), GuideKeys.forTitle("Beta")!!))
        assertEquals(listOf("Recent"), guide.programmes.getValue("a").map { it.title })
        assertEquals(listOf("By name"), guide.programmes.getValue("b").map { it.title })
        assertNull(guide.programmes["c"])
        assertFalse(guide.aliases.values.contains("c"))
    }

    @Test fun missingOrBrokenStopEndsAtTheNextStartOrAfterAnHour() {
        val guide = XmltvParser().parse(xml("""
            <programme start="20261125120000" channel="x"><title>First</title></programme>
            <programme start="20261125124500" stop="20261125120000" channel="x"><title>Second</title></programme>
            <programme start="20261125124500" stop="20261125130000" channel="x"><title>Duplicate start</title></programme>
            <programme start="20261125130000" channel="x"><title></title></programme>
        """), now)
        val list = guide.programmes.getValue("x")
        assertEquals(listOf("First", "Second"), list.map { it.title })
        assertEquals(now + 45 * 60_000L, list[0].endMs)
        assertEquals(list[1].startMs + hour, list[1].endMs)
    }

    @Test fun gzipInputIsDetectedAndEntityDeclarationsAreNeverExpanded() {
        val text = """<?xml version="1.0"?>
            <!DOCTYPE tv [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">]>
            <tv><programme start="20261125120000" channel="z"><title>&b; &unknown; ok</title></programme></tv>"""
        val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
        val guide = XmltvParser().parse(bytes.inputStream(), now)
        assertEquals("&b; &unknown; ok", guide.programmes.getValue("z").single().title)
    }

    @Test fun rejectsNonGuidesAndEnforcesLimits() {
        assertEquals(ImportError.INVALID_PLAYLIST, assertThrows(ImportFailure::class.java) { XmltvParser().parse("<html><body>login</body></html>".byteInputStream(), now) }.code)
        val many = (0 until 5).joinToString("") { """<programme start="20261125120000" channel="c$it"><title>T</title></programme>""" }
        assertEquals(ImportError.TOO_MANY_ENTRIES, assertThrows(ImportFailure::class.java) { XmltvParser(GuideLimits(maxProgrammes = 3)).parse(xml(many), now) }.code)
        assertEquals(ImportError.TOO_MANY_ENTRIES, assertThrows(ImportFailure::class.java) { XmltvParser(GuideLimits(maxChannels = 2)).parse(xml(many), now) }.code)
        assertEquals(ImportError.TOO_LARGE, assertThrows(ImportFailure::class.java) { XmltvParser(GuideLimits(maxBytes = 64)).parse(xml(many), now) }.code)
        var clock = 0L
        val timed = XmltvParser(GuideLimits(maxMillis = 1)) { clock += 2_000_000; clock }
        assertEquals(ImportError.TIME_LIMIT, assertThrows(ImportFailure::class.java) { timed.parse(xml(many.repeat(400)), now) }.code)
    }

    @Test fun titleKeysIgnoreQualityTagsCountryPrefixesAndArabicMarks() {
        assertEquals("title:bbc one", GuideKeys.forTitle("UK: BBC One FHD"))
        assertEquals(GuideKeys.forTitle("BBC ONE"), GuideKeys.forTitle("uk | bbc one hd"))
        assertEquals(GuideKeys.forTitle("الجزيرة"), GuideKeys.forTitle("الْجَزِيرَة HD"))
        assertNull(GuideKeys.forTitle("HD"))
        assertEquals("id:bbc1.uk", GuideKeys.forEntry(" BBC1.uk ", "Whatever"))
        assertEquals("title:news", GuideKeys.forEntry(null, "News"))
    }

    @Test fun playlistsCarryGuideIdsAndTheHeaderGuideLink() {
        val parsed = M3uParser().parse("""#EXTM3U url-tvg="ftp://bad.invalid/a.xml, https://epg.invalid/guide.xml.gz?token=secret" x-other="1"
            #EXTINF:-1 tvg-id="news.one" group-title="News",News One
            https://example.invalid/1
            #EXTINF:-1 tvg-id="",No id
            https://example.invalid/2
        """.trimIndent().byteInputStream())
        assertEquals("https://epg.invalid/guide.xml.gz?token=secret", parsed.guideLink!!.reveal())
        assertFalse(parsed.toString().contains("secret"))
        assertEquals(listOf("news.one", null), parsed.map { it.guideId })
        assertNull(M3uParser().parse("#EXTM3U x-tvg-url='nope'\nhttps://example.invalid/1".byteInputStream()).guideLink)
        assertEquals("https://epg.invalid/b.xml", M3uParser().parse("#EXTM3U x-tvg-url='https://epg.invalid/b.xml'\nhttps://example.invalid/1".byteInputStream()).guideLink!!.reveal())
    }

    @Test fun xtreamLiveStreamsCarryTheirGuideIdAndTheAccountGuideLink() {
        val source = XtreamConnection.create("https://example.invalid/base", "fixture-user", "fixture-password")!!
        val input = """[{"stream_id":5,"name":"Live fixture","epg_channel_id":"live.fixture","category_id":"1"},{"stream_id":6,"name":"No guide","epg_channel_id":null}]"""
        val entries = XtreamJson(ParserLimits()).catalogue(input.byteInputStream(), {}, ContentKind.LIVE, source, emptyMap(), "ts", 0)
        assertEquals(listOf("live.fixture", null), entries.map { it.guideId })
        assertEquals("https://example.invalid/base/xmltv.php?username=fixture-user&password=fixture-password", SourceSpec.xtream("Fixture", source).guideAddress()!!.reveal())
        assertNull(SourceSpec.file().guideAddress())
    }
}
