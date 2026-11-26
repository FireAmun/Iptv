package com.fireamun.iptv.prototype

import com.fireamun.iptv.core.remote.SentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The TV names only the site of a playlist a phone sent, and only when it could add that playlist itself. */
class SentSourceHostTest {
    @Test fun showsOnlyTheHostOfAPlaylistLink() {
        assertEquals("lists.example.com", usableSourceHost(SentSource.Playlist("https://viewer:secret@lists.example.com:8443/private/list.m3u?token=abc#top")))
        assertEquals("192.168.1.5", usableSourceHost(SentSource.Playlist("http://192.168.1.5/list.m3u")))
        assertEquals("[::1]", usableSourceHost(SentSource.Playlist("http://[::1]:8080/list.m3u")))
    }

    @Test fun showsTheServerHostInTheFormTheTvUses() {
        assertEquals("tv.example.test", usableSourceHost(SentSource.ServerLogin("Family", "http://tv.example.test:8080/portal/", "user", "pass")))
        assertEquals("xn--bcher-kva.example", usableSourceHost(SentSource.ServerLogin("Family", "https://bücher.example", "user", "pass")))
    }

    @Test fun refusesWhatTheTvCouldNotAdd() {
        assertNull(usableSourceHost(SentSource.Playlist("ftp://example.com/list.m3u")))
        assertNull(usableSourceHost(SentSource.Playlist("not a link")))
        assertNull(usableSourceHost(SentSource.ServerLogin("Family", "http://example.com/?x=1", "user", "pass")))
        assertNull(usableSourceHost(SentSource.ServerLogin("Family", "example.com", "user", "pass")))
        // The TV's own login form refuses these too.
        assertNull(usableSourceHost(SentSource.ServerLogin("Family", "http://tv_box.example.test/", "user", "pass")))
        assertNull(usableSourceHost(SentSource.ServerLogin("Family", "http://example.com", "..", "pass")))
    }
}
