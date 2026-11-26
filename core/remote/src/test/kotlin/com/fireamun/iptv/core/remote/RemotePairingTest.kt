package com.fireamun.iptv.core.remote

import kotlinx.coroutines.CompletableDeferred
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class RemotePairingTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val identity = RemoteIdentity.generate()
    private val trust = MemoryRemoteTrust()
    private val pressed = CopyOnWriteArrayList<RemoteKey>()
    private val typed = CopyOnWriteArrayList<Pair<Int, String>>()
    private var now = 1_000_000L
    private val actions = object : RemoteActions {
        override suspend fun perform(key: RemoteKey): RemoteRefusal? { pressed += key; return if (key == RemoteKey.PLAY_PAUSE) RemoteRefusal.NO_PLAYBACK else null }
        override suspend fun type(erase: Int, text: String): RemoteRefusal? { typed += erase to text; return null }
    }
    private val server = RemoteServer(identity, trust, actions, { "Living room TV" }, scope, clock = { now }, preferredPort = 0)
    private val client = RemoteClient(timeoutMs = 3_000, approvalMs = 10_000)

    @After fun tearDown() { server.stop(); scope.cancel() }

    private fun approveWhenAsked(approve: Boolean = true) = scope.async {
        withTimeout(10_000) { server.state.first { it.pending != null } }
        if (approve) server.approve() else server.deny()
    }

    private fun pairPhone(name: String = "Pixel${Char(0x202E)} 8\n"): SavedTv = runBlocking {
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

    @Test fun codePairingShowsTheSameCodeOnBothScreensAndPinsTheTv() = runBlocking {
        val port = server.offer("127.0.0.1").link.port
        val phoneCode = CompletableDeferred<String>()
        val approval = scope.async {
            val pending = withTimeout(10_000) { server.state.first { it.pending != null } }.pending!!
            // The phone shows its code before anyone can allow it on the TV.
            assertEquals(withTimeout(10_000) { phoneCode.await() }, pending.code)
            assertEquals("Tablet", pending.name)
            server.approve()
        }
        val outcome = client.pairWithCode("127.0.0.1", port, "Tablet") { phoneCode.complete(it) } as PairOutcome.Paired
        approval.await()
        assertTrue(phoneCode.await().matches(Regex("[0-9]{6}")))
        assertEquals("The certificate seen while pairing is pinned", identity.fingerprint, outcome.tv.fingerprint)
        assertEquals(listOf("Tablet"), trust.phones().map { it.name })
        val session = client.connect(outcome.tv, scope)
        assertNull(session.press(RemoteKey.OK))
        session.close()
    }

    @Test fun codePairingNeedsTheOpenPairingScreenAndAnApproval() = runBlocking {
        val port = server.start()
        var shown = false
        assertEquals(PairOutcome.Refused(RemoteRefusal.EXPIRED), client.pairWithCode("127.0.0.1", port, "Phone") { shown = true })
        assertFalse("No code without the pairing screen", shown)
        server.offer("127.0.0.1")
        val declined = approveWhenAsked(approve = false)
        assertEquals(PairOutcome.Refused(RemoteRefusal.DENIED), client.pairWithCode("127.0.0.1", port, "Stranger") { })
        declined.await()
        assertTrue(trust.phones().isEmpty())
        assertEquals("Each attempt uses up the pairing screen", PairOutcome.Refused(RemoteRefusal.EXPIRED), client.pairWithCode("127.0.0.1", port, "Again") { })
    }

    @Test fun aRevealThatDoesNotMatchTheCommitmentIsRefused() = runBlocking {
        val port = server.offer("127.0.0.1").link.port
        openUnverified("127.0.0.1", port, 3_000).first.use { socket ->
            val input = DataInputStream(BufferedInputStream(socket.inputStream))
            val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
            Frames.write(output, message("code", "v" to REMOTE_PROTOCOL, "commit" to sha256Hex(ByteArray(32) { 1 }), "name" to "Cheat"))
            assertEquals("challenge", Frames.read(input).type())
            // Picking a value after seeing the TV's would let a device steer the code, so the TV refuses it.
            Frames.write(output, message("reveal", "nonce" to base64Url.encodeToString(ByteArray(32) { 2 })))
            assertEquals(RemoteRefusal.INVALID.name, Frames.read(input).text("code", 32))
        }
        assertNull(server.state.value.pending)
        assertNull(server.state.value.offer)
        assertTrue(trust.phones().isEmpty())
    }

    @Test fun theCodeCoversTheCertificateAndBothValues() {
        val phone = ByteArray(32) { 3 }
        val tv = ByteArray(32) { 4 }
        val code = pairingCode("a".repeat(64), phone, tv)
        assertTrue(code.matches(Regex("[0-9]{6}")))
        assertEquals(code, pairingCode("a".repeat(64), phone.copyOf(), tv.copyOf()))
        assertNotEquals("A device in the middle presents another certificate", code, pairingCode("b".repeat(64), phone, tv))
        assertNotEquals(code, pairingCode("a".repeat(64), ByteArray(32) { 5 }, tv))
        assertNotEquals(code, pairingCode("a".repeat(64), phone, ByteArray(32) { 5 }))
    }

    @Test fun typedTextReachesTheTvCleanedAndBounded() = runBlocking {
        val session = client.connect(pairPhone(), scope)
        assertNull(session.type(0, "مرحبا News"))
        assertNull(session.type(3, "${Char(0x202E)}ab\nc"))
        assertEquals(listOf(0 to "مرحبا News", 3 to "abc"), typed.toList())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { session.type(0, "x".repeat(MAX_TYPED_CHARS + 1)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { session.type(MAX_ERASED_CHARS + 1, "") } }
        session.close()
        assertEquals("Without a text box the TV says so", RemoteRefusal.NO_FIELD, RemoteActions { null }.type(0, "a"))
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
