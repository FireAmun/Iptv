package com.fireamun.iptv.core.model

import org.junit.Assert.*
import org.junit.Test

class GuideNowTest {
    private val news = GuideSlot("News", 1_000, 2_000)
    private val film = GuideSlot("Film", 2_000, 4_000)
    private val late = GuideSlot("Late", 5_000, 6_000)
    private val guide = GuideNow(listOf(news, film, late))

    @Test fun picksWhatIsOnNowAndNextAsTimePasses() {
        assertEquals(null to news, guide.at(500))
        assertEquals(news to film, guide.at(1_000))
        assertEquals(news to film, guide.at(1_999))
        assertEquals(film to late, guide.at(2_000))
        assertEquals("A gap shows only what comes next", null to late, guide.at(4_500))
        assertEquals(late to null, guide.at(5_500))
        assertEquals(null to null, guide.at(6_000))
        assertEquals(null to null, GuideNow(emptyList()).at(0))
    }

    @Test fun progressIsClampedAndSafeForBrokenSlots() {
        assertEquals(0f, news.progress(0), 0f)
        assertEquals(.5f, news.progress(1_500), 0.0001f)
        assertEquals(1f, news.progress(9_000), 0f)
        assertEquals(0f, GuideSlot("Broken", 10, 10).progress(10), 0f)
    }
}
