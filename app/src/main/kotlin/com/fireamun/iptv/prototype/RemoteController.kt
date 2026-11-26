package com.fireamun.iptv.prototype

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.fireamun.iptv.core.remote.MAX_ERASED_CHARS
import com.fireamun.iptv.core.remote.MAX_TYPED_CHARS
import com.fireamun.iptv.core.remote.PairOutcome
import com.fireamun.iptv.core.remote.PairingLink
import com.fireamun.iptv.core.remote.REMOTE_DEFAULT_PORT
import com.fireamun.iptv.core.remote.RemoteClient
import com.fireamun.iptv.core.remote.RemoteKey
import com.fireamun.iptv.core.remote.RemoteRefusal
import com.fireamun.iptv.core.remote.RemoteRefusedException
import com.fireamun.iptv.core.remote.RemoteSession
import com.fireamun.iptv.core.remote.SavedTv
import com.fireamun.iptv.core.storage.EnvelopeProtector
import com.fireamun.iptv.core.storage.KeystoreEnvelope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

/** Pairing progress shown on the phone's TV remote card. */
enum class PairingPhase { IDLE, CONNECTING, WAITING_APPROVAL }
enum class RemoteConnection { OFFLINE, CONNECTING, CONNECTED }

/** Why the last pairing, connection, key or typing did not work; null after success. */
enum class RemoteProblem { NOT_A_CODE, NOT_AN_ADDRESS, UNREACHABLE, SCANNER_UNAVAILABLE, EXPIRED, DENIED, INVALID, IDENTITY, BUSY, VERSION, REVOKED, FAILED, NO_PLAYBACK, NOT_FOCUSED, NO_FIELD }

data class RemoteControllerUi(
    val tvName: String? = null,
    val pairing: PairingPhase = PairingPhase.IDLE,
    /** The six digits to compare with the TV screen while code pairing waits for approval. */
    val code: String? = null,
    val connection: RemoteConnection = RemoteConnection.OFFLINE,
    val problem: RemoteProblem? = null,
)

/** Phone side of the remote: one paired TV, kept with its secret sealed by the Keystore. */
class RemoteController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: RemoteClient = RemoteClient(),
    private val protector: EnvelopeProtector = KeystoreEnvelope(RemoteHost.REMOTE_KEY_ALIAS),
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("remote_client", Context.MODE_PRIVATE)
    private val discovery = RemoteDiscovery(context)
    private val mutable = MutableStateFlow(RemoteControllerUi(tvName = preferences.getString("name", null)))
    val state: StateFlow<RemoteControllerUi> = mutable.asStateFlow()
    private var pairingJob: Job? = null
    private var connectJob: Job? = null
    @Volatile private var session: RemoteSession? = null

    private val typingText = MutableStateFlow("")
    /** What the phone's typing box shows: text this phone added to the TV's selected box since the last [finishTyping]. */
    val typing: StateFlow<String> = typingText.asStateFlow()
    /** The part of [typing] the TV has confirmed. Touched on the main thread only. */
    private var typed = ""
    private var typingJob: Job? = null

    /** Handles text from the QR scanner. */
    fun pair(code: String) {
        val link = PairingLink.parse(code.trim()) ?: run { mutable.update { it.copy(problem = RemoteProblem.NOT_A_CODE) }; return }
        startPairing { client.pair(link, RemoteHost.deviceName(context)) { mutable.update { it.copy(pairing = PairingPhase.WAITING_APPROVAL) } } }
    }

    /** Pairs without the camera by comparing codes with a TV found on the network. */
    fun pairWith(tv: FoundTv) = pairWithCode(tv.host, tv.port)

    /** Pairs without the camera with a TV typed as "192.168.1.20" or "192.168.1.20:47820"; false when that is not an address. */
    fun pairWithAddress(text: String): Boolean {
        val (host, port) = parseAddress(text) ?: run { mutable.update { it.copy(problem = RemoteProblem.NOT_AN_ADDRESS) }; return false }
        pairWithCode(host, port)
        return true
    }

    private fun pairWithCode(host: String, port: Int) = startPairing {
        client.pairWithCode(host, port, RemoteHost.deviceName(context)) { code -> mutable.update { it.copy(pairing = PairingPhase.WAITING_APPROVAL, code = code) } }
    }

    private fun startPairing(attempt: suspend () -> PairOutcome) {
        disconnect()
        pairingJob?.cancel()
        mutable.update { it.copy(pairing = PairingPhase.CONNECTING, code = null, problem = null) }
        pairingJob = scope.launch {
            val outcome = try { attempt() }
            catch (cancelled: CancellationException) { mutable.update { it.copy(pairing = PairingPhase.IDLE, code = null) }; throw cancelled }
            when (outcome) {
                is PairOutcome.Paired -> if (save(outcome.tv)) mutable.update { RemoteControllerUi(tvName = outcome.tv.tvName) }
                    else mutable.update { it.copy(pairing = PairingPhase.IDLE, code = null, problem = RemoteProblem.FAILED) }
                is PairOutcome.Refused -> mutable.update { it.copy(pairing = PairingPhase.IDLE, code = null, problem = outcome.reason.problem()) }
                PairOutcome.Unreachable -> mutable.update { it.copy(pairing = PairingPhase.IDLE, code = null, problem = RemoteProblem.UNREACHABLE) }
            }
        }
    }

    /** TVs announcing the phone remote on this network, for code pairing. */
    fun searchTvs(): Flow<List<FoundTv>> = discovery.search()

    fun scannerUnavailable() = mutable.update { it.copy(problem = RemoteProblem.SCANNER_UNAVAILABLE) }
    fun cancelPairing() { pairingJob?.cancel(); mutable.update { it.copy(pairing = PairingPhase.IDLE, code = null) } }
    fun clearProblem() = mutable.update { it.copy(problem = null) }

    /** Opens the connection the remote screen uses; a refused or failed attempt leaves it offline with a reason. */
    fun connect() {
        val tv = saved() ?: return
        if (session?.open?.value == true || connectJob?.isActive == true) return
        mutable.update { it.copy(connection = RemoteConnection.CONNECTING, problem = null) }
        connectJob = scope.launch {
            try {
                val opened = try { client.connect(tv, scope) } catch (unreachable: IOException) {
                    if (unreachable is RemoteRefusedException) throw unreachable
                    // The router may have given the TV a new address; the same certificate identifies it.
                    val moved = discovery.find(tv.fingerprint, FIND_MS)?.takeIf { it.host != tv.host || it.port != tv.port } ?: throw unreachable
                    val updated = tv.copy(host = moved.host, port = moved.port)
                    client.connect(updated, scope).also { save(updated) }
                }
                session = opened
                mutable.update { it.copy(connection = RemoteConnection.CONNECTED, tvName = opened.tvName) }
                opened.open.first { !it }
                if (session === opened) {
                    session = null
                    val ended = opened.endedBy
                    if (ended == RemoteRefusal.REVOKED) clearSaved()
                    mutable.update { it.copy(connection = RemoteConnection.OFFLINE, problem = ended?.problem() ?: it.problem) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (refused: RemoteRefusedException) {
                // A TV that removed this phone, or replaced its identity, will never accept it again.
                if (refused.reason == RemoteRefusal.REVOKED || refused.reason == RemoteRefusal.IDENTITY) clearSaved()
                mutable.update { it.copy(connection = RemoteConnection.OFFLINE, problem = refused.reason.problem()) }
            } catch (_: Exception) {
                mutable.update { it.copy(connection = RemoteConnection.OFFLINE, problem = RemoteProblem.UNREACHABLE) }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        session?.close()
        session = null
        mutable.update { it.copy(connection = RemoteConnection.OFFLINE) }
    }

    /** Sends one key; the result is also reflected in [state] as a problem when the TV could not act on it. */
    suspend fun press(key: RemoteKey): RemoteRefusal? {
        val active = session ?: return RemoteRefusal.FAILED
        return try {
            active.press(key).also(::showRefusal)
        } catch (_: IOException) { RemoteRefusal.FAILED }
    }

    /**
     * Sets the phone's typing box. The difference from what the TV already has is sent in order: erased characters
     * first, then added ones. If the TV cannot take it (no box selected), the phone box returns to what the TV has.
     * Main thread.
     */
    fun type(text: String) {
        typingText.value = text
        if (typingJob?.isActive != true) typingJob = scope.launch(Dispatchers.Main.immediate) { sendTyping() }
    }

    /** Starts a new line of typing; the TV box keeps its text. Main thread. */
    fun finishTyping() {
        typingJob?.cancel()
        typingJob = null
        typed = ""
        typingText.value = ""
    }

    private suspend fun sendTyping() {
        while (true) {
            val wanted = typingText.value
            if (wanted == typed) return
            val active = session ?: run { typingText.value = typed; return }
            var common = typed.commonPrefixWith(wanted).length
            if (common > 0 && typed[common - 1].isHighSurrogate()) common--
            val erase = typed.length - common
            // Erasing finishes before anything is added, so the TV box never mixes old and new text.
            val (erasing, adding) = if (erase > MAX_ERASED_CHARS) MAX_ERASED_CHARS to "" else {
                val rest = wanted.substring(common)
                var end = minOf(rest.length, MAX_TYPED_CHARS)
                if (end < rest.length && rest[end - 1].isHighSurrogate()) end--
                erase to rest.substring(0, end)
            }
            val refusal = try { active.type(erasing, adding) } catch (_: IOException) { typingText.value = typed; return }
            if (refusal != null) {
                showRefusal(refusal)
                typingText.value = typed
                return
            }
            typed = typed.dropLast(erasing) + adding
            mutable.update { if (it.problem == RemoteProblem.NO_FIELD) it.copy(problem = null) else it }
        }
    }

    private fun showRefusal(refusal: RemoteRefusal?) = mutable.update {
        it.copy(problem = refusal?.takeIf { r -> r == RemoteRefusal.NO_PLAYBACK || r == RemoteRefusal.NOT_FOCUSED || r == RemoteRefusal.NO_FIELD }?.problem())
    }

    /** Forgets the TV on this phone. The TV keeps listing the phone until it is removed there. */
    fun forget() {
        cancelPairing()
        disconnect()
        clearSaved()
        mutable.value = RemoteControllerUi()
    }

    private fun clearSaved() {
        preferences.edit().clear().commit()
        mutable.update { it.copy(tvName = null) }
    }

    private fun save(tv: SavedTv): Boolean = runCatching {
        val json = JSONObject().put("host", tv.host).put("port", tv.port).put("fingerprint", tv.fingerprint)
            .put("phone", tv.phoneId).put("secret", tv.secret).put("name", tv.tvName).toString().toByteArray(Charsets.UTF_8)
        try {
            preferences.edit().putString("tv", Base64.encodeToString(protector.seal(json, SAVED_CONTEXT), Base64.NO_WRAP))
                .putString("name", tv.tvName).commit()
        } finally { json.fill(0) }
    }.getOrDefault(false)

    private fun saved(): SavedTv? = runCatching {
        val stored = preferences.getString("tv", null) ?: return null
        val opened = protector.open(Base64.decode(stored, Base64.NO_WRAP), SAVED_CONTEXT)
        try {
            val json = JSONObject(String(opened, Charsets.UTF_8))
            SavedTv(json.getString("host"), json.getInt("port"), json.getString("fingerprint"), json.getString("phone"), json.getString("secret"), json.getString("name"))
        } finally { opened.fill(0) }
    }.getOrNull()

    private fun RemoteRefusal.problem(): RemoteProblem = when (this) {
        RemoteRefusal.EXPIRED -> RemoteProblem.EXPIRED
        RemoteRefusal.DENIED -> RemoteProblem.DENIED
        RemoteRefusal.INVALID -> RemoteProblem.INVALID
        RemoteRefusal.REVOKED -> RemoteProblem.REVOKED
        RemoteRefusal.BUSY -> RemoteProblem.BUSY
        RemoteRefusal.VERSION -> RemoteProblem.VERSION
        RemoteRefusal.IDENTITY -> RemoteProblem.IDENTITY
        RemoteRefusal.NO_PLAYBACK -> RemoteProblem.NO_PLAYBACK
        RemoteRefusal.NOT_FOCUSED -> RemoteProblem.NOT_FOCUSED
        RemoteRefusal.NO_FIELD -> RemoteProblem.NO_FIELD
        RemoteRefusal.RATE_LIMITED, RemoteRefusal.FAILED -> RemoteProblem.FAILED
    }

    companion object {
        private const val SAVED_CONTEXT = "remote-saved-tv"
        private const val FIND_MS = 4_000L

        /** "192.168.1.20" or "192.168.1.20:47820" as typed from the TV screen; null for anything else. */
        internal fun parseAddress(text: String): Pair<String, Int>? {
            val parts = text.trim().split(':')
            if (parts.size > 2) return null
            val host = parts[0]
            val octets = host.split('.')
            val valid = octets.size == 4 && octets.all { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) && (it.length == 1 || it[0] != '0') && it.toInt() <= 255 }
            if (!valid) return null
            val port = if (parts.size == 2) parts[1].takeIf { it.length in 1..5 && it.all(Char::isDigit) }?.toInt()?.takeIf { it in 1..65_535 } ?: return null
                else REMOTE_DEFAULT_PORT
            return host to port
        }
    }
}
