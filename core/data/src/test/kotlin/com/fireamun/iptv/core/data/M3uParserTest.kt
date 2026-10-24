package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ImportError
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException
import java.io.ByteArrayInputStream

class M3uParserTest {
    private fun parse(text: String, limits: ParserLimits = ParserLimits()) = M3uParser(limits).parse(text.byteInputStream())
    private fun fails(code: ImportError, text: String, limits: ParserLimits = ParserLimits()) {
        val failure = assertThrows(ImportFailure::class.java) { parse(text, limits) }
        assertEquals(code, failure.code)
        assertFalse(failure.toString().contains("private-token"))
    }

    @Test fun readsBomCrLfArabicAndQuotedCommasWithoutMergingDuplicates() {
        val entries = parse("\uFEFF#EXTM3U\r\n#EXTINF:-1 group-title=\"News, World\",القناة, الأولى\r\nhttps://example.invalid/live\r\n# comment\r\n#EXTINF:-1,Second\r\nhttps://example.invalid/live\r\n")
        assertEquals(2, entries.size)
        assertEquals("القناة, الأولى", entries[0].channel.title)
        assertEquals("News, World", entries[0].channel.group)
        assertEquals("Second", entries[1].channel.title)
        assertNotEquals(entries[0].channel.id, entries[1].channel.id)
    }
    @Test fun permitsPlainEntryWithNoUrlDerivedDisplayLabel() {
        val entry = parse("#EXTM3U\nhttps://host.invalid/private-token\n").single()
        assertNull(entry.channel.title)
        assertEquals("https://host.invalid/private-token", entry.address.reveal())
        assertFalse(entry.toString().contains("private-token"))
    }
    @Test fun attributesInsideQuotedValuesDoNotOverrideRealGroup() {
        val entry = parse("#EXTM3U\n#EXTINF:-1 tvg-name=\"My group-title=Fake\" group-title=\"Actual\",Name\nhttps://host.invalid/live").single()
        assertEquals("Actual", entry.channel.group)
    }
    @Test fun delayedFinalEofCannotCommitAfterTheParserBudgetExpires() {
        var clock = 0L
        val source = object : ByteArrayInputStream("#EXTM3U\nhttps://host.invalid/live\n".toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                if (count == -1) clock = 6_000_000_000L
                return count
            }
        }
        assertEquals(ImportError.TIME_LIMIT, assertThrows(ImportFailure::class.java) {
            M3uParser(nanoTime = { clock }).parse(source)
        }.code)
    }
    @Test fun rejectsHlsManifestsAndInvalidCatalogueStructure() {
        fails(ImportError.HLS_MANIFEST, "#EXTM3U\n#EXT-X-TARGETDURATION:10\nsegment.ts")
        fails(ImportError.INVALID_PLAYLIST, "<html>private-token</html>")
        fails(ImportError.INVALID_PLAYLIST, "#EXTM3U\n#EXTINF:-1,Missing URI\n")
        fails(ImportError.INVALID_PLAYLIST, "#EXTM3U\n#EXTINF:-1,One\n#EXTINF:-1,Two\nhttps://host.invalid/live")
        fails(ImportError.INVALID_PLAYLIST, "#EXTM3U\n#EXTINF:-1 group-title=\"unclosed,Title\nhttps://host.invalid/live")
        fails(ImportError.EMPTY, "#EXTM3U\n# nothing here")
        fails(ImportError.INVALID_ADDRESS, "#EXTM3U\nfile:///private-token")
    }
    @Test fun enforcesByteLineFieldAndEntryLimits() {
        fails(ImportError.TOO_LARGE, "#EXTM3U\n" + "# comment\n".repeat(20), ParserLimits(maxBytes = 64))
        fails(ImportError.LINE_TOO_LONG, "#EXTM3U\n#" + "a".repeat(40), ParserLimits(maxLineChars = 32))
        fails(ImportError.FIELD_TOO_LONG, "#EXTM3U\n#EXTINF:-1,Long title\nhttps://host.invalid/a", ParserLimits(maxFieldChars = 4))
        fails(ImportError.TOO_MANY_ENTRIES, "#EXTM3U\nhttps://host.invalid/a\nhttps://host.invalid/b", ParserLimits(maxEntries = 1))
    }
    @Test fun rejectsInvalidUtf8AndHonorsCancellationAndTimeBudget() {
        val bad = byteArrayOf(0x23, 0x45, 0x58, 0x54, 0x4d, 0x33, 0x55, 0x0a, 0xc3.toByte(), 0x28)
        assertEquals(ImportError.INVALID_ENCODING, assertThrows(ImportFailure::class.java) { M3uParser().parse(bad.inputStream()) }.code)
        assertThrows(CancellationException::class.java) {
            M3uParser().parse("#EXTM3U\nhttps://host.invalid/a".byteInputStream()) { throw CancellationException() }
        }
        var clock = 0L
        val timed = M3uParser(ParserLimits(maxMillis = 1)) { clock += 2_000_000; clock }
        assertEquals(ImportError.TIME_LIMIT, assertThrows(ImportFailure::class.java) { timed.parse("#EXTM3U".byteInputStream()) }.code)
    }
}
