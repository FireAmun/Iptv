package com.fireamun.iptv.core.model

import org.junit.Assert.*
import org.junit.Test

class BrowseModelsTest {
    @Test fun searchNormalizesArabicWithoutChangingOriginalLabelsOrLossyLetters() {
        assertEquals("اهلا وسهلا", normalizeSearch("أَهْلًا وَسَهْلًاـ"))
        assertEquals("ابراهيم اثار اسلام", normalizeSearch("إبراهيم آثار إسلام"))
        assertNotEquals(normalizeSearch("قناة"), normalizeSearch("قناه"))
        assertNotEquals(normalizeSearch("على"), normalizeSearch("علي"))
    }
    @Test fun queryWordsAreLiteralBoundedTermsAndNumbersMatchExactly() {
        assertEquals("\"bein*\" \"1\" \"hd*\"", searchExpression("beIN 1 HD"))
        assertEquals("\"a*\" \"or*\" \"b*\"", searchExpression("a\" OR b*"))
        assertEquals("\"اهلا*\"", searchExpression("أَهْلًا"))
        assertNull(searchExpression("* \" () :"))
        assertTrue(searchExpression("word ".repeat(100))!!.length < 200)
    }
    @Test fun untypedEntriesBelongToLiveAndArtworkReferencesExposeNoProviderData() {
        assertTrue(matchesKind(ContentKind.UNKNOWN, ContentKind.LIVE))
        assertFalse(matchesKind(ContentKind.MOVIE, ContentKind.LIVE))
        assertTrue(matchesKind(ContentKind.SERIES, null))
        assertEquals("[protected artwork reference]", LibraryArtwork("opaque-source", 4, "opaque-generation").toString())
        assertEquals(listOf(LibraryTab.LIVE, LibraryTab.MOVIES, LibraryTab.SERIES, LibraryTab.SETTINGS, LibraryTab.FAVOURITES, LibraryTab.SEARCH), LibraryTab.entries)
    }
}
