package com.fireamun.iptv.core.remote

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.UUID
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** A phone the TV approved. Only a hash of its secret is kept. */
data class TrustedPhone(val id: String, val name: String, val secretHash: String, val pairedAtMs: Long)

/** Where the TV keeps its trusted phones. */
interface RemoteTrust {
    fun phones(): List<TrustedPhone>
    fun save(phones: List<TrustedPhone>)
}

class MemoryRemoteTrust(initial: List<TrustedPhone> = emptyList()) : RemoteTrust {
    @Volatile private var phones = initial
    override fun phones() = phones
    override fun save(phones: List<TrustedPhone>) { this.phones = phones }
}

/** Carries out a pressed key on the TV; returns null when done or the reason it could not be. */
fun interface RemoteActions {
    suspend fun perform(key: RemoteKey): RemoteRefusal?
}

/** The pairing code currently offered on the TV screen. */
data class PairingOffer(val link: PairingLink, val expiresAtMs: Long)

data class RemoteServerState(
    val listening: Boolean = false,
    val port: Int? = null,
    val offer: PairingOffer? = null,
    /** Name of a phone that used the code and waits for approval on the TV. */
    val pending: String? = null,
    /** Name of the phone approved most recently while the code was on screen. */
    val approved: String? = null,
    val phones: List<TrustedPhone> = emptyList(),
    val connected: Set<String> = emptySet(),
)

/**
 * TV side of the phone remote: a TLS server on the local network. Pairing needs the single-use code from the TV
 * screen (120 seconds, five wrong tries) and an explicit approval on the TV; afterwards a phone authenticates with
 * its own random secret. Keys from all phones run one at a time through [actions].
 */
class RemoteServer(
    private val identity: RemoteIdentity,
    private val trust: RemoteTrust,
    private val actions: RemoteActions,
    private val tvName: () -> String,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val preferredPort: Int = REMOTE_DEFAULT_PORT,
    private val timeouts: Timeouts = Timeouts(),
) {
    data class Timeouts(val handshakeMs: Int = 10_000, val idleMs: Int = 45_000, val approvalMs: Long = 60_000, val offerMs: Long = 120_000)

    private val mutable = MutableStateFlow(RemoteServerState(phones = trust.phones()))
    val state: StateFlow<RemoteServerState> = mutable.asStateFlow()
    private val lock = Any()
    private var server: SSLServerSocket? = null
    private var acceptJob: Job? = null
    private val sockets = mutableSetOf<SSLSocket>()
    private val authenticated = mutableMapOf<SSLSocket, String>()
    private val outputs = mutableMapOf<SSLSocket, DataOutputStream>()
    private var offerNonce: String? = null
    private var attemptsLeft = 0
    private var decision: CompletableDeferred<Boolean>? = null
    private val keys = Mutex()

    /** Starts listening on all interfaces; returns the port. Calling it again keeps the running server. */
    fun start(): Int = synchronized(lock) {
        server?.let { return it.localPort }
        val factory = identity.serverContext().serverSocketFactory
        val socket = (factory.createServerSocket() as SSLServerSocket).apply { reuseAddress = true }
        try { socket.bind(InetSocketAddress(preferredPort), 8) }
        catch (_: IOException) { socket.bind(InetSocketAddress(0), 8) }
        socket.enabledProtocols = socket.supportedProtocols.filter { it in modernProtocols }.toTypedArray()
        server = socket
        mutable.update { it.copy(listening = true, port = socket.localPort) }
        acceptJob = scope.launch(Dispatchers.IO) { accept(socket) }
        socket.localPort
    }

    /** Closes the listener, every connection and any code on screen. Trusted phones stay trusted. */
    fun stop() {
        val open = synchronized(lock) {
            server?.close()
            server = null
            acceptJob?.cancel()
            acceptJob = null
            offerNonce = null
            decision?.complete(false)
            decision = null
            sockets.toList().also { sockets.clear(); authenticated.clear(); outputs.clear() }
        }
        open.forEach { runCatching { it.close() } }
        mutable.update { it.copy(listening = false, port = null, offer = null, pending = null, approved = null, connected = emptySet()) }
    }

    /** Shows a new single-use code for [host], replacing any earlier one. */
    fun offer(host: String): PairingOffer {
        val port = start()
        val bytes = ByteArray(16).also(random::nextBytes)
        val link = PairingLink(host, port, identity.fingerprint, base64Url.encodeToString(bytes))
        val offer = PairingOffer(link, clock() + timeouts.offerMs)
        synchronized(lock) { offerNonce = link.nonce; attemptsLeft = 5 }
        mutable.update { it.copy(offer = offer, approved = null) }
        return offer
    }

    /** Removes the code from the screen; a phone waiting for approval is declined. */
    fun cancelOffer() {
        synchronized(lock) { offerNonce = null; decision?.complete(false) }
        mutable.update { it.copy(offer = null, approved = null) }
    }

    fun approve() { synchronized(lock) { decision?.complete(true) } }
    fun deny() { synchronized(lock) { decision?.complete(false) } }

    /** Forgets one phone and closes its connections at once. */
    fun revoke(phoneId: String) = forget { it.id == phoneId }
    fun revokeAll() = forget { true }

    private fun forget(match: (TrustedPhone) -> Boolean) {
        val closing = synchronized(lock) {
            val kept = trust.phones().filterNot(match)
            val removed = trust.phones().filter(match).map { it.id }.toSet()
            trust.save(kept)
            mutable.update { it.copy(phones = kept) }
            authenticated.filterValues { it in removed }.keys.map { it to outputs[it] }
        }
        // The phone learns why, so it can forget this TV instead of retrying a connection that will never work.
        closing.forEach { (socket, output) ->
            output?.let { runCatching { Frames.write(it, message("rejected", "code" to RemoteRefusal.REVOKED.name)) } }
            runCatching { socket.close() }
        }
    }

    private suspend fun accept(listener: SSLServerSocket) {
        while (true) {
            val socket = try { listener.accept() as SSLSocket } catch (_: IOException) { return }
            val admitted = synchronized(lock) { (server === listener && sockets.size < MAX_CONNECTIONS).also { if (it) sockets += socket } }
            if (!admitted) { runCatching { socket.close() }; continue }
            scope.launch(Dispatchers.IO) {
                try { serve(socket) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { }
                finally {
                    val id = synchronized(lock) { sockets -= socket; outputs -= socket; authenticated.remove(socket) }
                    runCatching { socket.close() }
                    if (id != null) publishConnected()
                }
            }
        }
    }

    private suspend fun serve(socket: SSLSocket) {
        socket.soTimeout = timeouts.handshakeMs
        socket.startHandshake()
        val input = DataInputStream(BufferedInputStream(socket.inputStream))
        val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
        val hello = Frames.read(input)
        if (hello.integer("v") != REMOTE_PROTOCOL) { refuse(output, RemoteRefusal.VERSION); return }
        val phone = when (hello.type()) {
            "pair" -> pair(hello, output) ?: return
            "auth" -> authenticate(hello, output) ?: return
            else -> throw RemoteProtocolException("Unexpected hello")
        }
        synchronized(lock) {
            if (trust.phones().none { it.id == phone.id } || socket !in sockets) return
            authenticated[socket] = phone.id
            outputs[socket] = output
        }
        publishConnected()
        socket.soTimeout = timeouts.idleMs
        val bucket = TokenBucket(clock)
        while (true) {
            val request = Frames.read(input)
            val id = request.integer("id") ?: throw RemoteProtocolException("Missing id")
            when (request.type()) {
                "ping" -> Frames.write(output, message("pong", "id" to id))
                "key" -> {
                    val key = request.text("key", 16)?.let { name -> RemoteKey.entries.firstOrNull { it.name == name } }
                    val refusal = when {
                        key == null -> RemoteRefusal.FAILED
                        !bucket.take() -> RemoteRefusal.RATE_LIMITED
                        else -> keys.withLock {
                            // A revoke between reading and running the key must win.
                            if (synchronized(lock) { authenticated[socket] } == null) return
                            try { actions.perform(key) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { RemoteRefusal.FAILED }
                        }
                    }
                    Frames.write(output, message("result", "id" to id, "ok" to (refusal == null), "code" to refusal?.name))
                }
                else -> Frames.write(output, message("result", "id" to id, "ok" to false, "code" to RemoteRefusal.FAILED.name))
            }
        }
    }

    private suspend fun pair(hello: JsonObject, output: DataOutputStream): TrustedPhone? {
        val nonce = hello.text("nonce", 64)
        val name = cleanDeviceName(hello.text("name", 120)) ?: "Phone"
        val waiting = CompletableDeferred<Boolean>()
        val refusal: RemoteRefusal? = synchronized(lock) {
            val expected = offerNonce
            val offer = mutable.value.offer
            when {
                expected == null || offer == null || clock() >= offer.expiresAtMs -> RemoteRefusal.EXPIRED
                decision != null -> RemoteRefusal.BUSY
                nonce == null || !constantEquals(nonce, expected) -> {
                    attemptsLeft--
                    if (attemptsLeft <= 0) { offerNonce = null; mutable.update { it.copy(offer = null) } }
                    RemoteRefusal.INVALID
                }
                trust.phones().size >= MAX_PHONES -> RemoteRefusal.BUSY
                else -> null
            }.also { if (it == null) { offerNonce = null; decision = waiting } }
        }
        if (refusal != null) { refuse(output, refusal); return null }
        mutable.update { it.copy(offer = null, pending = name) }
        val approved = try {
            Frames.write(output, message("pending"))
            withTimeoutOrNull(timeouts.approvalMs) { waiting.await() } ?: false
        } finally {
            synchronized(lock) { if (decision === waiting) decision = null }
            mutable.update { it.copy(pending = null) }
        }
        if (!approved) { refuse(output, RemoteRefusal.DENIED); return null }
        val secret = ByteArray(32).also(random::nextBytes)
        val phone = TrustedPhone(UUID.randomUUID().toString(), name, sha256Hex(secret), clock())
        synchronized(lock) {
            trust.save(trust.phones() + phone)
            mutable.update { it.copy(phones = trust.phones(), approved = name) }
        }
        try {
            Frames.write(output, message("paired", "phone" to phone.id, "secret" to base64Url.encodeToString(secret), "tv" to tvName()))
        } catch (failure: IOException) {
            forget { it.id == phone.id }
            throw failure
        } finally { secret.fill(0) }
        return phone
    }

    private fun authenticate(hello: JsonObject, output: DataOutputStream): TrustedPhone? {
        val id = hello.text("phone", 64)
        val secret = hello.text("secret", 64)?.let(::decodeBase64Url)
        val phone = trust.phones().firstOrNull { it.id == id }
        val refusal = when {
            phone == null -> RemoteRefusal.REVOKED
            secret == null || !constantEquals(sha256Hex(secret), phone.secretHash) -> RemoteRefusal.INVALID
            else -> null
        }
        secret?.fill(0)
        if (refusal != null) { refuse(output, refusal); return null }
        Frames.write(output, message("ready", "tv" to tvName()))
        return phone
    }

    private fun refuse(output: DataOutputStream, reason: RemoteRefusal) =
        runCatching { Frames.write(output, message("rejected", "code" to reason.name)) }

    private fun publishConnected() {
        val ids = synchronized(lock) { authenticated.values.toSet() }
        mutable.update { it.copy(connected = ids) }
    }

    /** Twenty keys at once, refilled at twenty per second: enough for held buttons, not for floods. */
    private class TokenBucket(private val clock: () -> Long) {
        private var tokens = 20.0
        private var last = clock()
        fun take(): Boolean {
            val now = clock()
            tokens = minOf(20.0, tokens + (now - last).coerceAtLeast(0) / 50.0)
            last = now
            if (tokens < 1) return false
            tokens -= 1
            return true
        }
    }

    private companion object {
        const val MAX_CONNECTIONS = 6
        const val MAX_PHONES = 10
    }
}
