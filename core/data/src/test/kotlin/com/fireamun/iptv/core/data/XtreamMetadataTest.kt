package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ContentKind
import org.junit.Assert.*
import org.junit.Test

class XtreamMetadataTest {
    @Test fun retainsUsefulCatalogueMetadataWithoutPuttingArtworkUrlsInChannel() {
        val input = """[{"stream_id":21,"name":"Fixture film","category_id":"4","stream_icon":"https://images.invalid/poster?token=protected","rating":"8.3","year":"2024","container_extension":"mp4"}]"""
        val source = XtreamConnection.create("https://example.invalid", "fixture-user", "fixture-password")!!
        val entry = XtreamJson(ParserLimits()).catalogue(input.byteInputStream(), {}, ContentKind.MOVIE, source, mapOf("4" to "Drama"), "ts", 0).single()
        assertEquals(2024, entry.channel.year)
        assertEquals(8.3, entry.channel.rating!!, 0.001)
        assertEquals("4", entry.channel.categoryKey)
        assertTrue(entry.channel.hasArtwork)
        assertEquals("https://images.invalid/poster?token=protected", entry.artwork!!.reveal())
        assertFalse(entry.channel.toString().contains("images.invalid"))
        assertEquals("[protected catalogue entry]", entry.toString())
    }
    @Test fun optionalMetadataDoesNotInventOrRejectInvalidPicturesAndRatings() {
        val input = """[{"series_id":11,"name":"Fixture series","cover":"file:///private","rating":"n/a","rating_5based":"6","year":"unknown"}]"""
        val source = XtreamConnection.create("https://example.invalid", "u", "p")!!
        val entry = XtreamJson(ParserLimits()).catalogue(input.byteInputStream(), {}, ContentKind.SERIES, source, emptyMap(), "ts", 0).single()
        assertNull(entry.artwork)
        assertNull(entry.channel.year)
        assertNull(entry.channel.rating)
        assertFalse(entry.channel.hasArtwork)
    }
}
