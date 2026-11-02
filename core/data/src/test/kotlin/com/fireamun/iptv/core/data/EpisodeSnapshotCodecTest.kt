package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.ContentKind
import com.fireamun.iptv.core.model.MediaAddress
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class EpisodeSnapshotCodecTest {
    private val address = MediaAddress.parse("https://example.invalid/series/fixture/31.mp4")!!

    @Test fun episodeProviderKeysSurviveReorderedApiRows() {
        val source = XtreamConnection.create("https://example.invalid", "fixture", "fixture")!!
        val first = """{"episodes":{"1":[{"id":31,"title":"First"},{"id":32,"title":"Second"}]}}"""
        val reordered = """{"episodes":{"1":[{"id":32,"title":"Second"},{"id":31,"title":"First"}]}}"""
        val parser = XtreamJson(ParserLimits())
        val before = parser.episodes(ByteArrayInputStream(first.toByteArray()), {}, source)
        val after = parser.episodes(ByteArrayInputStream(reordered.toByteArray()), {}, source)
        assertEquals(listOf("EPISODE:31", "EPISODE:32"), before.map { it.providerKey })
        assertEquals(listOf("EPISODE:32", "EPISODE:31"), after.map { it.providerKey })
        assertEquals(listOf(0, 1), after.map { it.channel.id })
    }

    @Test fun snapshotRoundTripPreservesActualEpisodeIdentityAndLocator() {
        val snapshot = EpisodeSnapshot("EPISODE:31", "Fixture episode", 1, 2, address)
        val encoded = EpisodeSnapshotCodec.encode(snapshot)
        val restored = EpisodeSnapshotCodec.decode(encoded)
        assertEquals("EPISODE:31", restored.providerKey)
        assertEquals("Fixture episode", restored.title)
        assertEquals(1, restored.season)
        assertEquals(2, restored.episode)
        assertEquals(address.reveal(), restored.address.reveal())
        encoded.fill(0)
    }

    @Test fun longUtf8LocatorStaysWithinEncodedBoundAndRoundTrips() {
        val longAddress = MediaAddress.parse("https://example.invalid/" + "界".repeat(3_900))!!
        val snapshot = EpisodeSnapshot("EPISODE:31", "ع".repeat(512), 1, 2, longAddress)
        val encoded = EpisodeSnapshotCodec.encode(snapshot)
        try {
            assertTrue(encoded.size > 11_000)
            assertTrue(encoded.size <= EpisodeSnapshotCodec.MAX_BYTES)
            assertEquals(longAddress.reveal(), EpisodeSnapshotCodec.decode(encoded).address.reveal())
        } finally { encoded.fill(0) }
    }

    @Test fun invalidProviderKeyAndNumbersAreRejected() {
        for (key in listOf("", "EPISODE:0", "EPISODE:-2", "MOVIE:31", "EPISODE:31:extra")) {
            try { EpisodeSnapshot(key, null, null, null, address); fail("Accepted invalid key") }
            catch (_: IllegalArgumentException) { }
        }
        try { EpisodeSnapshot("EPISODE:31", null, -1, null, address); fail("Accepted negative season") }
        catch (_: IllegalArgumentException) { }
        try { EpisodeSnapshot("EPISODE:31", null, null, -1, address); fail("Accepted negative episode") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun boundedCodecRejectsOversizedPayloadAndMalformedAddress() {
        try { EpisodeSnapshot("EPISODE:31", "x".repeat(16_385), 1, 2, address); fail("Accepted oversized snapshot") }
        catch (_: IllegalArgumentException) { }
        val bytes = EpisodeSnapshotCodec.encode(EpisodeSnapshot("EPISODE:31", null, null, null, address))
        try { EpisodeSnapshotCodec.decode(bytes + ByteArray(16_384)); fail("Accepted oversized payload") }
        catch (_: IllegalArgumentException) { }
        bytes.fill(0)
        val malformed = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { output ->
            output.writeByte(1); output.writeUTF("EPISODE:31"); output.writeBoolean(false)
            output.writeInt(-1); output.writeInt(-1); output.writeUTF("file:///private")
        } }.toByteArray()
        try { EpisodeSnapshotCodec.decode(malformed); fail("Accepted local file address") }
        catch (_: IllegalArgumentException) { }
        malformed.fill(0)
    }

    @Test fun progressTargetsValidateKindsAndProtectedHoldersRedact() {
        val movie = ProgressTarget("source", 2, ContentKind.MOVIE)
        val episode = ProgressTarget("source", 3, ContentKind.EPISODE, "EPISODE:31")
        ProgressTarget("source", 4, ContentKind.EPISODE)
        for (invalid in listOf(ContentKind.UNKNOWN, ContentKind.LIVE, ContentKind.SERIES)) {
            try { ProgressTarget("source", 2, invalid); fail("Accepted invalid kind") }
            catch (_: IllegalArgumentException) { }
        }
        try { ProgressTarget("source", 2, ContentKind.MOVIE, "EPISODE:31"); fail("Accepted movie key") }
        catch (_: IllegalArgumentException) { }
        assertFalse(movie.toString().contains("source"))
        assertFalse(episode.toString().contains("31"))
        assertFalse(EpisodeSnapshot("EPISODE:31", null, null, null, address).toString().contains("31"))
    }
}
