package com.fireamun.iptv.core.model

import org.junit.Assert.*
import org.junit.Test

class SourceNameTest {
    @Test fun keepsFriendlyNamesAndTrimsSurroundingSpace() {
        assertEquals("Family TV", normalizeSourceName("  Family TV  "))
        assertEquals("قنواتي", normalizeSourceName("قنواتي"))
    }
    @Test fun rejectsBlankControlAndOverlongLabels() {
        assertNull(normalizeSourceName("  "))
        assertNull(normalizeSourceName("TV\nNews"))
        assertNull(normalizeSourceName("x".repeat(129)))
        assertEquals("x".repeat(128), normalizeSourceName("x".repeat(128)))
    }
}
