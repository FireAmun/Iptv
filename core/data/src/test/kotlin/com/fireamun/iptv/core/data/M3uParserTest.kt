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
        assertEquals("https://host.invalid/private-token", entry.address!!.reveal())
        assertFalse(entry.toString().contains("private-token"))
    }
    @Test fun attributesInsideQuotedValuesDoNotOverrideRealGroup() {
        val entry = parse("#EXTM3U\n#EXTINF:-1 tvg-name=\"My group-title=Fake\" group-title=\"Actual\",Name\nhttps://host.invalid/live").single()
        assertEquals("Actual", entry.channel.group)
    }
    @Test fun preservesLinesWithLfBeforeAtAndAfterAChunkBoundary() {
        // The header occupies eight chars; these comment LFs have offsets 1023, 1024 and 1025.
        for (commentLength in listOf(1015, 1016, 1017)) {
            val text = "#EXTM3U\n#" + "x".repeat(commentLength - 1) +
                "\n#EXTINF:-1 group-title=\"أخبار\",القناة\nhttps://host.invalid/live\n"
            val entry = parse(text).single()
            assertEquals("القناة", entry.channel.title)
            assertEquals("أخبار", entry.channel.group)
            assertEquals("https://host.invalid/live", entry.address!!.reveal())
        }
    }
    @Test fun acceptsSplitCrLfAndFinalUnterminatedLinesWithOrWithoutCr() {
        // CR is at offset 1023 and LF at 1024, followed by another complete entry.
        val prefix = "#EXTM3U\n#" + "x".repeat(1014) + "\r\n#EXTINF:-1,Final\r\n"
        for (ending in listOf("", "\r")) {
            val entry = parse(prefix + "https://host.invalid/final" + ending).single()
            assertEquals("Final", entry.channel.title)
            assertEquals("https://host.invalid/final", entry.address!!.reveal())
        }
    }
    @Test fun decodesBomAndArabicAcrossOneToThreeByteReads() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:-1 group-title=\"أخبار\",القناة 🎬\r\nhttps://host.invalid/live"
        for (readSize in 1..3) {
            val source = chunkedInput(text.toByteArray(), readSize)
            val entry = M3uParser().parse(source).single()
            assertEquals("القناة 🎬", entry.channel.title)
            assertEquals("أخبار", entry.channel.group)
            assertFalse(source.closed)
        }
    }
    @Test fun rejectsMalformedAndTruncatedUtf8AcrossSmallReads() {
        val prefix = ("#EXTM3U\n#" + "x".repeat(1100) + "\nhttps://host.invalid/live\n#").toByteArray()
        for (suffix in listOf(byteArrayOf(0xc3.toByte(), 0x28), byteArrayOf(0xe2.toByte(), 0x82.toByte()))) {
            for (readSize in 1..3) {
                val failure = assertThrows(ImportFailure::class.java) {
                    M3uParser().parse(chunkedInput(prefix + suffix, readSize))
                }
                assertEquals(ImportError.INVALID_ENCODING, failure.code)
            }
        }
    }
    @Test fun enforcesExactLineCapBeforeStrippingCrAtLfOrEof() {
        val prefix = "#EXTM3U\nhttps://host.invalid/live\n"
        for ((ending, contentChars) in listOf("\n" to 16384, "\r\n" to 16383, "" to 16384, "\r" to 16383)) {
            // The optional CR counts against the UTF-16 line cap, even though it is removed later.
            val exact = prefix + "#" + "ع".repeat(contentChars - 1) + ending
            assertEquals(1, parse(exact).size)
            fails(ImportError.LINE_TOO_LONG, prefix + "#" + "ع".repeat(contentChars) + ending)
        }
    }
    @Test fun cancelsDuringALongCommentWithoutConsumingTheRemainingInput() {
        val bytes = ("#EXTM3U\n#" + "x".repeat(12000) + "\nhttps://host.invalid/live").toByteArray()
        val source = chunkedInput(bytes, 1024)
        assertThrows(CancellationException::class.java) {
            M3uParser().parse(source) {
                if (source.bytesRead >= 2048) throw CancellationException()
            }
        }
        assertTrue("Cancellation must interrupt the comment rather than wait for its LF", source.bytesRead < bytes.size)
        assertFalse(source.closed)
    }
    @Test fun rejectsTimeBudgetExpiryDuringALaterRead() {
        var clock = 0L
        val bytes = ("#EXTM3U\n#" + "x".repeat(12000) + "\nhttps://host.invalid/live").toByteArray()
        val source = chunkedInput(bytes, 1024) { consumed ->
            if (consumed >= 2048) clock = 6_000_000_000L
        }
        val failure = assertThrows(ImportFailure::class.java) {
            M3uParser(ParserLimits(maxMillis = 5_000), nanoTime = { clock }).parse(source)
        }
        assertEquals(ImportError.TIME_LIMIT, failure.code)
        assertTrue(source.bytesRead < bytes.size)
    }
    @Test fun acceptsExactByteCapAndRejectsOneExtraByteWithMultibyteText() {
        val text = "\uFEFF#EXTM3U\n#EXTINF:-1,القناة\nhttps://host.invalid/live"
        val byteCount = text.toByteArray().size.toLong()
        assertEquals("القناة", parse(text, ParserLimits(maxBytes = byteCount)).single().channel.title)
        fails(ImportError.TOO_LARGE, text, ParserLimits(maxBytes = byteCount - 1))
    }
    @Test fun delayedFinalEofCannotCommitAfterTheParserBudgetExpires() {
        for (ending in listOf("\n", "")) {
            var clock = 0L
            val source = object : ByteArrayInputStream(("#EXTM3U\nhttps://host.invalid/live" + ending).toByteArray()) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val count = super.read(buffer, offset, length)
                    if (count == -1) clock = 6_000_000_000L
                    return count
                }
            }
            assertEquals(ImportError.TIME_LIMIT, assertThrows(ImportFailure::class.java) {
                M3uParser(ParserLimits(maxMillis = 5_000), nanoTime = { clock }).parse(source)
            }.code)
        }
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

    private fun chunkedInput(bytes: ByteArray, readSize: Int, afterRead: (Int) -> Unit = {}) =
        object : ByteArrayInputStream(bytes) {
            var bytesRead = 0
                private set
            var closed = false
                private set

            // Prevent decoder read-ahead from hiding the controlled stream boundaries.
            override fun available(): Int = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, minOf(length, readSize))
                if (count > 0) bytesRead += count
                afterRead(bytesRead)
                return count
            }
            override fun close() { closed = true; super.close() }
        }
}
