package com.fireamun.iptv.core.model

import org.junit.Assert.*
import org.junit.Test

class MediaAddressTest {
    @Test fun acceptsHttpAndHttpsWithoutExposingCredentialsInDiagnostics() {
        val raw = "https://user:secret@example.invalid/path?token=private"
        val address = requireNotNull(MediaAddress.parse(raw))
        assertEquals(raw, address.reveal())
        assertFalse(address.isCleartext)
        assertFalse(address.toString().contains("secret"))
        assertFalse(address.toString().contains("private"))
        assertTrue(requireNotNull(MediaAddress.parse("HTTP://example.invalid/live")).isCleartext)
    }
    @Test fun rejectsLocalRelativeMalformedAndNonHttpAddresses() {
        listOf("file:///etc/passwd", "content://private/item", "javascript:alert(1)", "/live", "https:///missing", "http://host/a b", "https://host/%zz", "https://host:99999/live", "https://host/" + "x".repeat(4096)).forEach {
            assertNull(MediaAddress.parse(it))
        }
    }
}
