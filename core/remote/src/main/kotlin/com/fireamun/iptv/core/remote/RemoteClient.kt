package com.fireamun.iptv.core.remote

import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket

/** What a phone keeps after pairing. [secret] is sensitive and must be stored encrypted. */
data class SavedTv(val host: String, val port: Int, val fingerprint: String, val phoneId: String, val secret: String, val tvName: String) {
    override fun toString(): String = "SavedTv(host=$host, port=$port, tvName=$tvName)"
}

sealed interface PairOutcome {
    data class Paired(val tv: SavedTv) : PairOutcome
    data class Refused(val reason: RemoteRefusal) : PairOutcome
    /** No TLS connection to the address in the code (other network, TV app closed, firewall). */
    data object Unreachable : PairOutcome
}

/** Phone side of the remote. Every connection after pairing pins the TV certificate learnt while pairing. */
class RemoteClient(private val timeoutMs: Int = 5_000, private val approvalMs: Int = 75_000, private val random: SecureRandom = SecureRandom()) {

    /** Uses the code from the TV, then waits for someone to approve this phone on the TV. */
    suspend fun pair(link: PairingLink, phoneName: String, onPending: () -> Unit = {}): PairOutcome = withContext(Dispatchers.IO) {
        val socket = try { openPinned(link.host, link.port, link.fingerprint, timeoutMs).first }
            catch (refused: RemoteRefusedException) { return@withContext PairOutcome.Refused(refused.reason) }
            catch (_: IOException) { return@withContext PairOutcome.Unreachable }
        try {
            val input = DataInputStream(BufferedInputStream(socket.inputStream))
            val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
            Frames.write(output, message("pair", "v" to REMOTE_PROTOCOL, "nonce" to link.nonce, "name" to (cleanDeviceName(phoneName) ?: "Phone")))
            var reply = Frames.read(input)
            if (reply.type() == "pending") {
                onPending()
                socket.soTimeout = approvalMs
                reply = Frames.read(input)
            }
            finish(reply, link.host, link.port, link.fingerprint)
        } catch (_: IOException) { PairOutcome.Unreachable }
        finally { runCatching { socket.close() } }
    }

    /**
     * Pairs without the QR code with a TV whose pairing screen is open. [onCode] receives the six digits to compare
     * with the TV screen; the person at the TV allows the phone only if they match. The TV certificate seen here is
     * the one saved, so a device in the middle would have produced a different code.
     */
    suspend fun pairWithCode(host: String, port: Int, phoneName: String, onCode: (String) -> Unit): PairOutcome = withContext(Dispatchers.IO) {
        val (socket, fingerprint) = try { openUnverified(host, port, timeoutMs) }
            catch (_: IOException) { return@withContext PairOutcome.Unreachable }
            catch (_: java.security.GeneralSecurityException) { return@withContext PairOutcome.Unreachable }
            catch (_: IllegalArgumentException) { return@withContext PairOutcome.Unreachable }
        val phoneNonce = ByteArray(32).also(random::nextBytes)
        try {
            val input = DataInputStream(BufferedInputStream(socket.inputStream))
            val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
            Frames.write(output, message("code", "v" to REMOTE_PROTOCOL, "commit" to sha256Hex(phoneNonce), "name" to (cleanDeviceName(phoneName) ?: "Phone")))
            val challenge = Frames.read(input)
            if (challenge.type() == "rejected") return@withContext PairOutcome.Refused(refusal(challenge))
            val tvNonce = challenge.takeIf { it.type() == "challenge" }?.text("nonce", 64)?.let(::decodeBase64Url)?.takeIf { it.size == 32 }
                ?: return@withContext PairOutcome.Refused(RemoteRefusal.FAILED)
            Frames.write(output, message("reveal", "nonce" to base64Url.encodeToString(phoneNonce)))
            val pending = Frames.read(input)
            // The code must be on the phone screen before the TV can be approved; no other order is accepted.
            when (pending.type()) {
                "pending" -> Unit
                "rejected" -> return@withContext PairOutcome.Refused(refusal(pending))
                else -> return@withContext PairOutcome.Refused(RemoteRefusal.FAILED)
            }
            onCode(pairingCode(fingerprint, phoneNonce, tvNonce))
            socket.soTimeout = approvalMs
            finish(Frames.read(input), host, port, fingerprint)
        } catch (_: IOException) { PairOutcome.Unreachable }
        finally { phoneNonce.fill(0); runCatching { socket.close() } }
    }

    private fun finish(reply: JsonObject, host: String, port: Int, fingerprint: String): PairOutcome = when (reply.type()) {
        "paired" -> {
            val id = reply.text("phone", 64)
            val secret = reply.text("secret", 64)?.takeIf { decodeBase64Url(it)?.size == 32 }
            val tv = cleanDeviceName(reply.text("tv", 120)) ?: "TV"
            if (id == null || secret == null) PairOutcome.Refused(RemoteRefusal.FAILED)
            else PairOutcome.Paired(SavedTv(host, port, fingerprint, id, secret, tv))
        }
        "rejected" -> PairOutcome.Refused(refusal(reply))
        else -> PairOutcome.Refused(RemoteRefusal.FAILED)
    }

    /**
     * Authenticates as a paired phone. Throws [RemoteRefusedException] when the TV refuses (for example
     * [RemoteRefusal.REVOKED]) and [IOException] when it cannot be reached.
     */
    suspend fun connect(tv: SavedTv, scope: CoroutineScope): RemoteSession = withContext(Dispatchers.IO) {
        val socket = openPinned(tv.host, tv.port, tv.fingerprint, timeoutMs).first
        try {
            val input = DataInputStream(BufferedInputStream(socket.inputStream))
            val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
            Frames.write(output, message("auth", "v" to REMOTE_PROTOCOL, "phone" to tv.phoneId, "secret" to tv.secret))
            val reply = Frames.read(input)
            when (reply.type()) {
                "ready" -> RemoteSession(socket, input, output, cleanDeviceName(reply.text("tv", 120)) ?: tv.tvName, scope, timeoutMs.toLong(), approvalMs.toLong())
                "rejected" -> throw RemoteRefusedException(refusal(reply))
                else -> throw RemoteProtocolException("Unexpected reply")
            }
        } catch (failure: Exception) {
            runCatching { socket.close() }
            throw failure
        }
    }

    private fun refusal(reply: JsonObject): RemoteRefusal =
        reply.text("code", 32)?.let { code -> RemoteRefusal.entries.firstOrNull { it.name == code } } ?: RemoteRefusal.FAILED
}

/** An authenticated connection. Keys are answered in order; the session closes on any network or protocol error. */
class RemoteSession internal constructor(
    private val socket: SSLSocket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    val tvName: String,
    scope: CoroutineScope,
    private val replyMs: Long,
    /** How long a sent playlist may wait for the person at the TV. */
    private val answerMs: Long = replyMs,
) : Closeable {
    private val mutableOpen = MutableStateFlow(true)
    val open: StateFlow<Boolean> = mutableOpen.asStateFlow()
    /** Set when the TV ended the session on purpose, for example [RemoteRefusal.REVOKED]. */
    @Volatile var endedBy: RemoteRefusal? = null
        private set
    private val ids = AtomicInteger()
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<RemoteRefusal?>>()
    @Volatile private var reader: Job? = null
    @Volatile private var pinger: Job? = null

    init {
        socket.soTimeout = 0
        reader = scope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val reply = Frames.read(input)
                    if (reply.type() == "rejected") {
                        endedBy = reply.text("code", 32)?.let { code -> RemoteRefusal.entries.firstOrNull { it.name == code } } ?: RemoteRefusal.FAILED
                        break
                    }
                    val id = reply.integer("id") ?: continue
                    if (reply.type() == "result") waiting.remove(id)?.complete(
                        if (reply.get("ok")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) null
                        else reply.text("code", 32)?.let { code -> RemoteRefusal.entries.firstOrNull { it.name == code } } ?: RemoteRefusal.FAILED)
                }
            } catch (_: Exception) { } finally { close() }
        }
        // Keeps the TV's idle timeout from closing a quiet remote.
        pinger = scope.launch(Dispatchers.IO) {
            while (mutableOpen.value) {
                delay(15_000)
                runCatching { Frames.write(output, message("ping", "id" to ids.incrementAndGet())) }.onFailure { close() }
            }
        }
    }

    /** Null when the TV carried out the key, otherwise why not. Throws [IOException] once the session is closed. */
    suspend fun press(key: RemoteKey): RemoteRefusal? = request { id -> message("key", "id" to id, "key" to key.name) }

    /** Erases [erase] characters at the end of the TV's selected text box, then adds [text]; answered like [press]. */
    suspend fun type(erase: Int, text: String): RemoteRefusal? {
        require(erase in 0..MAX_ERASED_CHARS && text.length <= MAX_TYPED_CHARS) { "Typing message too large" }
        return request { id -> message("text", "id" to id, "erase" to erase, "type" to text) }
    }

    /**
     * Asks the TV to add [source]; null once the person at the TV agreed and the TV started adding it. Waits for their
     * answer. A source too large for one message is refused here with [RemoteRefusal.BAD_SOURCE] and never sent.
     */
    suspend fun sendSource(source: SentSource): RemoteRefusal? {
        if (source.toMessage(Int.MAX_VALUE).toString().toByteArray(Charsets.UTF_8).size > MAX_FRAME_BYTES) return RemoteRefusal.BAD_SOURCE
        return request(answerMs) { id -> source.toMessage(id) }
    }

    private suspend fun request(waitMs: Long = replyMs, build: (Int) -> JsonObject): RemoteRefusal? = withContext(Dispatchers.IO) {
        if (!mutableOpen.value) throw IOException("Closed")
        val id = ids.incrementAndGet()
        val reply = CompletableDeferred<RemoteRefusal?>()
        waiting[id] = reply
        try {
            Frames.write(output, build(id))
            // Wrapped because a successful reply is itself null.
            val answer = withTimeoutOrNull(waitMs) { Result.success(reply.await()) }
            when {
                answer != null -> answer.getOrThrow()
                !mutableOpen.value -> throw IOException("Closed")
                else -> RemoteRefusal.FAILED
            }
        } catch (failure: IOException) { close(); throw failure }
        finally { waiting.remove(id) }
    }

    override fun close() {
        if (!mutableOpen.compareAndSet(true, false)) return
        runCatching { socket.close() }
        reader?.cancel()
        pinger?.cancel()
        waiting.values.forEach { it.completeExceptionally(IOException("Closed")) }
        waiting.clear()
    }
}
