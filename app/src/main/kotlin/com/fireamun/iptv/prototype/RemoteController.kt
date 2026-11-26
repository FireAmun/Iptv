package com.fireamun.iptv.prototype

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.fireamun.iptv.core.remote.PairOutcome
import com.fireamun.iptv.core.remote.PairingLink
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
import kotlinx.coroutines.Job
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

/** Why the last pairing or connection did not work; null after success. */
enum class RemoteProblem { NOT_A_CODE, UNREACHABLE, SCANNER_UNAVAILABLE, EXPIRED, DENIED, INVALID, IDENTITY, BUSY, VERSION, REVOKED, FAILED, NO_PLAYBACK, NOT_FOCUSED }

data class RemoteControllerUi(
    val tvName: String? = null,
    val pairing: PairingPhase = PairingPhase.IDLE,
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
    private val mutable = MutableStateFlow(RemoteControllerUi(tvName = preferences.getString("name", null)))
    val state: StateFlow<RemoteControllerUi> = mutable.asStateFlow()
    private var pairingJob: Job? = null
    private var connectJob: Job? = null
    @Volatile private var session: RemoteSession? = null

    /** Handles text from the QR scanner. */
    fun pair(code: String) {
        val link = PairingLink.parse(code.trim()) ?: run { mutable.update { it.copy(problem = RemoteProblem.NOT_A_CODE) }; return }
        disconnect()
        pairingJob?.cancel()
        mutable.update { it.copy(pairing = PairingPhase.CONNECTING, problem = null) }
        pairingJob = scope.launch {
            val outcome = try {
                client.pair(link, RemoteHost.deviceName(context)) { mutable.update { it.copy(pairing = PairingPhase.WAITING_APPROVAL) } }
            } catch (cancelled: CancellationException) { mutable.update { it.copy(pairing = PairingPhase.IDLE) }; throw cancelled }
            when (outcome) {
                is PairOutcome.Paired -> if (save(outcome.tv)) mutable.update { RemoteControllerUi(tvName = outcome.tv.tvName) }
                    else mutable.update { it.copy(pairing = PairingPhase.IDLE, problem = RemoteProblem.FAILED) }
                is PairOutcome.Refused -> mutable.update { it.copy(pairing = PairingPhase.IDLE, problem = outcome.reason.problem()) }
                PairOutcome.Unreachable -> mutable.update { it.copy(pairing = PairingPhase.IDLE, problem = RemoteProblem.UNREACHABLE) }
            }
        }
    }

    fun scannerUnavailable() = mutable.update { it.copy(problem = RemoteProblem.SCANNER_UNAVAILABLE) }
    fun cancelPairing() { pairingJob?.cancel(); mutable.update { it.copy(pairing = PairingPhase.IDLE) } }

    /** Opens the connection the remote screen uses; a refused or failed attempt leaves it offline with a reason. */
    fun connect() {
        val tv = saved() ?: return
        if (session?.open?.value == true || connectJob?.isActive == true) return
        mutable.update { it.copy(connection = RemoteConnection.CONNECTING, problem = null) }
        connectJob = scope.launch {
            try {
                val opened = client.connect(tv, scope)
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
            active.press(key).also { refusal ->
                mutable.update { it.copy(problem = refusal?.takeIf { r -> r == RemoteRefusal.NO_PLAYBACK || r == RemoteRefusal.NOT_FOCUSED }?.problem()) }
            }
        } catch (_: IOException) { RemoteRefusal.FAILED }
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
        RemoteRefusal.RATE_LIMITED, RemoteRefusal.FAILED -> RemoteProblem.FAILED
    }

    private companion object { const val SAVED_CONTEXT = "remote-saved-tv" }
}
