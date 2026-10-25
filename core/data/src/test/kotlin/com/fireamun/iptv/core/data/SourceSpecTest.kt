package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.MediaAddress
import com.fireamun.iptv.core.model.SourceKind
import org.junit.Assert.*
import org.junit.Test

class SourceSpecTest {
    @Test fun sourcesRoundTripWithoutExposingSecretsInStrings() {
        val sources = listOf(SourceSpec.file(), SourceSpec.url(MediaAddress.parse("https://example.invalid/list?token=synthetic")!!),
            SourceSpec.xtream("Fixture", XtreamConnection.create("https://example.invalid/prefix", "synthetic-user", "synthetic-password")!!))
        for (source in sources) {
            val restored = SourceSpec.decode(source.encode())
            assertEquals(source.kind, restored.kind)
            assertEquals(source.name, restored.name)
            assertArrayEquals(source.encode(), restored.encode())
            assertFalse(source.toString().contains("example.invalid"))
            assertFalse(restored.toString().contains("synthetic"))
        }
        assertEquals(SourceKind.FILE, sources[0].kind)
        assertTrue(SourceSpec.decode(sources[1].encode()).url!!.reveal().contains("token=synthetic"))
        assertEquals("synthetic-password", SourceSpec.decode(sources[2].encode()).xtream!!.apiUrl().queryParameter("password"))
    }
    @Test fun malformedOrOversizedStoredSourceFailsWithFixedError() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(127), ByteArray(32_000), SourceSpec.file().encode() + byteArrayOf(1))) {
            try { SourceSpec.decode(bytes); fail("Expected fixed storage failure") }
            catch (failure: ImportFailure) { assertEquals("STORAGE", failure.code.name); assertNull(failure.cause) }
        }
    }
}
