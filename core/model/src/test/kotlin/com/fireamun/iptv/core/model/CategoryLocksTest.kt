package com.fireamun.iptv.core.model

import org.junit.Assert.*
import org.junit.Test

class CategoryLocksTest {
    @Test fun locksFollowTheBrowseTabOfEachKind() {
        val locks = CategoryLocks().with(ContentKind.LIVE, "Adults", true).with(ContentKind.SERIES, "Horror", true)
        assertTrue(locks.blocks(ContentKind.LIVE, "Adults"))
        assertTrue("Unknown entries browse as Live", locks.blocks(ContentKind.UNKNOWN, "Adults"))
        assertFalse("Same label in another tab stays open", locks.blocks(ContentKind.MOVIE, "Adults"))
        assertTrue(locks.blocks(ContentKind.SERIES, "Horror"))
        assertTrue("Episodes belong to the Series tab", locks.blocks(ContentKind.EPISODE, "Horror"))
        assertFalse(locks.blocks(ContentKind.LIVE, null))
        assertFalse(locks.isEmpty)
    }

    @Test fun unlockingTheLastLabelRemovesTheKind() {
        val locks = CategoryLocks().with(ContentKind.MOVIE, "Drama", true).with(ContentKind.MOVIE, "Drama", false)
        assertTrue(locks.isEmpty)
        assertTrue(locks.byKind.isEmpty())
        assertEquals(setOf("Drama"), CategoryLocks().with(ContentKind.MOVIE, "Drama", true).labels(ContentKind.MOVIE))
    }

    @Test fun onlyTabKindsMayBeKeys() {
        assertThrows(IllegalArgumentException::class.java) { CategoryLocks(mapOf(ContentKind.EPISODE to setOf("x"))) }
        assertEquals(listOf(ContentKind.SERIES, ContentKind.EPISODE), CategoryLocks.storedKinds(ContentKind.SERIES))
    }
}
