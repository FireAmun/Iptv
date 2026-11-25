package com.fireamun.iptv.core.model

import java.util.Locale

enum class LibraryTab { LIVE, MOVIES, SERIES, SETTINGS, FAVOURITES, SEARCH }
data class BrowseRequest(val sourceId: String, val generation: String, val kind: ContentKind? = null, val category: String? = null, val query: String = "", val favouritesOnly: Boolean = false,
    val hidden: CategoryLocks = CategoryLocks())

/**
 * Parental category locks for one source, keyed by the browse tab's kind (LIVE, MOVIE or SERIES). Labels are the
 * provider's original group labels. A request carries them only while the parental lock is closed.
 */
data class CategoryLocks(val byKind: Map<ContentKind, Set<String>> = emptyMap()) {
    init { require(byKind.keys.all { it in TAB_KINDS }) }
    val isEmpty get() = byKind.values.all { it.isEmpty() }
    fun labels(kind: ContentKind): Set<String> = byKind[tabKind(kind)].orEmpty()
    fun blocks(kind: ContentKind, group: String?): Boolean = group != null && group in labels(kind)
    fun with(kind: ContentKind, label: String, locked: Boolean): CategoryLocks {
        val key = tabKind(kind)
        val next = if (locked) labels(key) + label else labels(key) - label
        return CategoryLocks((byKind - key).let { if (next.isEmpty()) it else it + (key to next) })
    }
    companion object {
        val TAB_KINDS = setOf(ContentKind.LIVE, ContentKind.MOVIE, ContentKind.SERIES)
        /** Unknown entries browse as Live; episodes belong to the Series tab. */
        fun tabKind(kind: ContentKind) = when (kind) {
            ContentKind.LIVE, ContentKind.UNKNOWN -> ContentKind.LIVE
            ContentKind.MOVIE -> ContentKind.MOVIE
            ContentKind.SERIES, ContentKind.EPISODE -> ContentKind.SERIES
        }
        fun storedKinds(tabKind: ContentKind): List<ContentKind> = when (tabKind) {
            ContentKind.LIVE -> listOf(ContentKind.LIVE, ContentKind.UNKNOWN)
            ContentKind.MOVIE -> listOf(ContentKind.MOVIE)
            ContentKind.SERIES -> listOf(ContentKind.SERIES, ContentKind.EPISODE)
            else -> error("Not a tab kind")
        }
    }
}
data class BrowsePage(val items: List<Channel>, val total: Int)
data class LibraryArtwork(val sourceId: String, val itemId: Int, val generation: String) {
    override fun toString() = "[protected artwork reference]"
}
fun matchesKind(actual: ContentKind, requested: ContentKind?): Boolean = requested == null || actual == requested || (requested == ContentKind.LIVE && actual == ContentKind.UNKNOWN)

// Original labels stay untouched. Do not conflate ya/alif maqsura or ta marbuta/ha.
fun normalizeSearch(value: String): String = buildString {
    value.lowercase(Locale.ROOT).forEach { ch ->
        when (ch) {
            '\u0640', in '\u064b'..'\u065f', '\u0670', in '\u06d6'..'\u06ed' -> Unit
            '\u0622', '\u0623', '\u0625', '\u0671' -> append('\u0627')
            else -> append(ch)
        }
    }
}
fun searchTerms(value: String): List<String> = Regex("[\\p{L}\\p{N}]+").findAll(normalizeSearch(value.take(160))).take(12).map { it.value.take(32) }.toList()
// Whitespace is implicit AND in both standard and enhanced FTS4 builds.
fun searchExpression(value: String): String? = searchTerms(value).joinToString(" ") { term ->
    "\"$term${if (term.all(Char::isDigit)) "" else "*"}\""
}.takeIf { it.isNotEmpty() }
