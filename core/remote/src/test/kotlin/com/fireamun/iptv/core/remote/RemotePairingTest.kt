package com.fireamun.iptv.core.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class RemotePairingTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val identity = RemoteIdentity.generate()
    private val trust = MemoryRemoteTrust()
    private val pressed = CopyOnWriteArrayList<RemoteKey>()
    private var now = 1_000_000L
    private val server = RemoteServer(identity, trust, { key -> pressed += key; if (key == RemoteKey.PLAY_PAUSE) RemoteRefusal.NO_PLAYBACK else null },
        { "Living room TV" }, scope, clock = { now }, preferredPort = 0)
    private val client = RemoteClient(timeoutMs = 3_000, approvalMs = 10_000)

    @After fun tearDown() { server.stop(); scope.cancel() }

    private fun approveWhenAsked(approve: Boolean = true) = scope.async {
        withTimeout(10_000) { server.state.first { it.pending != null } }
        if (approve) server.approve() else server.deny()
    }

    private fun pairPhone(name: String = "Pixel‮ 8\n"): SavedTv = runBlocking {
        val link = server.offer("127.0.0.1").link
        val approval = approveWhenAsked()
        val outcome = client.pair(link, name)
        approval.await()
        (outcome as PairOutcome.Paired).tv
    }

    @Test fun approvedPhoneConnectsAndItsKeysReachTheTv() = runBlocking {
        val tv = pairPhone()
        assertEquals("Living room TV", tv.tvName)
        assertEquals("Control and line-break characters are dropped from the name", listOf("Pixel 8"), trust.phones().map { it.name })
        assertNotEquals("Only a hash of the secret is kept", tv.secret, trust.phones().single().secretHash)
        assertNull("The code is gone once used", server.state.value.offer)
        assertEquals("Pixel 8", server.state.value.approved)

        val session = client.connect(tv, scope)
        assertEquals("Living room TV", session.tvName)
        withTimeout(5_000) { server.state.first { it.connected == setOf(tv.phoneId) } }
        assertNull(session.press(RemoteKey.DOWN))
        assertNull(session.press(RemoteKey.OK))
        assertEquals(RemoteRefusal.NO_PLAYBACK, session.press(RemoteKey.PLAY_PAUSE))
        assertEquals(listOf(RemoteKey.DOWN, RemoteKey.OK, RemoteKey.PLAY_PAUSE), pressed)
        session.close()
    }

    @Test fun codeIsSingleUseAndDeclinedPhonesAreNotTrusted() = runBlocking {
        val link = server.offer("127.0.0.1").link
        val declined = approveWhenAsked(approve = false)
        assertEquals(PairOutcome.Refused(RemoteRefusal.DENIED), client.pair(link, "Stranger"))
        declined.await()
        assertTrue(trust.phones().isEmpty())
        assertEquals("A used code cannot be replayed", PairOutcome.Refused(RemoteRefusal.EXPIRED), client.pair(link, "Stranger"))
    }

    @Test fun codesExpireAndFiveWrongTriesEndThem() = runBlocking {
        val link = server.offer("127.0.0.1").link
        now += 120_000
        assertEquals(PairOutcome.Refused(RemoteRefusal.EXPIRED), client.pair(link, "Late"))

        val fresh = server.offer("127.0.0.1").link
        val wrong = fresh.copy(nonce = "A".repeat(22))
        repeat(5) { assertEquals(PairOutcome.Refused(RemoteRefusal.INVALID), client.pair(wrong, "Guess")) }
        assertNull(server.state.value.offer)
        assertEquals("The right code no longer works after five wrong ones", PairOutcome.Refused(RemoteRefusal.EXPIRED), client.pair(fresh, "Owner"))
        assertTrue(pressed.isEmpty() && trust.phones().isEmpty())
    }

    @Test fun anotherCertificateIsRefusedBeforeAnythingIsSent() = runBlocking {
        val link = server.offer("127.0.0.1").link
        val impostor = link.copy(fingerprint = RemoteIdentity.generate().fingerprint)
        assertEquals(PairOutcome.Refused(RemoteRefusal.IDENTITY), client.pair(impostor, "Phone"))
        assertNotNull("The real code is untouched", server.state.value.offer)
    }

    @Test fun revokingClosesTheSessionAndBlocksReconnects() = runBlocking {
        val tv = pairPhone()
        val session = client.connect(tv, scope)
        assertNull(session.press(RemoteKey.LEFT))
        server.revoke(tv.phoneId)
        withTimeout(5_000) { session.open.first { !it } }
        assertEquals("The phone is told why", RemoteRefusal.REVOKED, session.endedBy)
        assertThrows(IOException::class.java) { runBlocking { session.press(RemoteKey.RIGHT) } }
        val refused = runCatching { client.connect(tv, scope) }.exceptionOrNull() as RemoteRefusedException
        assertEquals(RemoteRefusal.REVOKED, refused.reason)
        val forged = runCatching { client.connect(pairPhone().copy(secret = "A".repeat(43)), scope) }.exceptionOrNull() as RemoteRefusedException
        assertEquals(RemoteRefusal.INVALID, forged.reason)
        assertEquals(listOf(RemoteKey.LEFT), pressed)
    }

    @Test fun stoppingTheServerKeepsTrustButClosesEverything() = runBlocking {
        val tv = pairPhone()
        val session = client.connect(tv, scope)
        server.stop()
        withTimeout(5_000) { session.open.first { !it } }
        assertNull("A stopped TV is not a revocation", session.endedBy)
        assertEquals(1, trust.phones().size)
        assertTrue(runCatching { client.connect(tv, scope) }.exceptionOrNull() is IOException)
    }

    @Test fun pairingLinksAreStrict() {
        val good = PairingLink("192.168.1.20", 47_820, "a".repeat(64), "abcdefghijklmnopqrstuv")
        assertEquals(good, PairingLink.parse(good.encode()))
        assertEquals("PairingLink(host=192.168.1.20, port=47820)", good.toString())
        for (bad in listOf(
            good.encode().replace("v=1", "v=2"),
            good.encode().replace("192.168.1.20", "example.com"),
            good.encode().replace("192.168.1.20", "192.168.01.20"),
            good.encode().replace("192.168.1.20", "256.1.1.1"),
            good.encode().replace("p=47820", "p=70000"),
            good.encode().replace("p=47820", "p=-1"),
            good.encode().replace("f=a", "f=A"),
            good.encode() + "&x=1",
            good.encode() + "&n=abcdefghijklmnopqrstuv",
            good.encode().replace("amariptv://", "https://"),
            "amariptv://pair?" + "a".repeat(300),
        )) assertNull(bad, PairingLink.parse(bad))
    }

    @Test fun framesRejectOversizedAndInvalidInput() {
        fun frame(length: Int, body: ByteArray = ByteArray(0)) = DataInputStream(ByteArrayInputStream(
            byteArrayOf((length ushr 24).toByte(), (length ushr 16).toByte(), (length ushr 8).toByte(), length.toByte()) + body))
        assertThrows(RemoteProtocolException::class.java) { Frames.read(frame(MAX_FRAME_BYTES + 1)) }
        assertThrows(RemoteProtocolException::class.java) { Frames.read(frame(0)) }
        assertThrows(RemoteProtocolException::class.java) { Frames.read(frame(2, byteArrayOf(0xC3.toByte(), 0x28))) }
        assertThrows(RemoteProtocolException::class.java) { Frames.read(frame(2, "[]".toByteArray())) }
        assertThrows(RemoteProtocolException::class.java) { Frames.read(frame(9, """{"t":"key"}""".toByteArray().copyOf(9))) }
        val valid = """{"t":"key","id":3}""".toByteArray()
        assertEquals(3, Frames.read(frame(valid.size, valid)).integer("id"))
    }

    @Test fun identitySurvivesEncoding() {
        assertEquals(identity.fingerprint, RemoteIdentity.decode(identity.encode()).fingerprint)
    }
}
