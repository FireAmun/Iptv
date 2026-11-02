package com.fireamun.iptv.core.model

import org.junit.Assert.*
import org.junit.Test

class PlaybackProgressTest {
    @Test fun bookmarksRequirePreparedSeekableStaticVod() {
        for (kind in listOf(ContentKind.MOVIE, ContentKind.EPISODE))
            assertTrue(isBookmarkEligible(kind, prepared = true, seekable = true, dynamic = false, live = false))
        for (kind in listOf(ContentKind.LIVE, ContentKind.UNKNOWN, ContentKind.SERIES))
            assertFalse(isBookmarkEligible(kind, true, true, false, false))
        assertFalse(isBookmarkEligible(ContentKind.MOVIE, false, true, false, false))
        assertFalse(isBookmarkEligible(ContentKind.MOVIE, true, false, false, false))
        assertFalse(isBookmarkEligible(ContentKind.MOVIE, true, true, true, false))
        assertFalse(isBookmarkEligible(ContentKind.MOVIE, true, true, false, true))
    }

    @Test fun normalizesOnlyEligibleNonnegativeCheckpoints() {
        assertNull(normalizedCheckpoint(PlaybackCheckpoint(-1, 30_000, false, true)))
        assertNull(normalizedCheckpoint(PlaybackCheckpoint(1_000, 30_000, false, false)))
        assertEquals(PlaybackCheckpoint(500, null, false, true), normalizedCheckpoint(PlaybackCheckpoint(500, 0, false, true)))
        assertEquals(PlaybackCheckpoint(30_000, 30_000, false, true), normalizedCheckpoint(PlaybackCheckpoint(35_000, 30_000, false, true)))
        assertEquals(PlaybackCheckpoint(1_000, null, true, true), normalizedCheckpoint(PlaybackCheckpoint(1_000, -1, true, true)))
    }

    @Test fun resumeClampsToLastPositionInsideKnownDuration() {
        assertEquals(0, resumeOffset(-5, 30_000))
        assertEquals(12_000, resumeOffset(12_000, null))
        assertEquals(29_999, resumeOffset(35_000, 30_000))
        assertEquals(2_000, resumeOffset(2_000, 0))
    }

    @Test fun requestRedactsAddressAndToken() {
        val request = PlaybackRequest("secret-session", MediaAddress.parse("https://example.com/secret")!!, ContentKind.MOVIE, 12_000)
        assertEquals("[protected playback request]", request.toString())
    }
}
