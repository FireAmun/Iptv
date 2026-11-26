package com.fireamun.iptv.prototype

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.fireamun.iptv.core.remote.RemoteActions
import com.fireamun.iptv.core.remote.RemoteIdentity
import com.fireamun.iptv.core.remote.RemoteRefusal
import com.fireamun.iptv.core.remote.RemoteServer
import com.fireamun.iptv.core.remote.RemoteServerState
import com.fireamun.iptv.core.remote.RemoteTrust
import com.fireamun.iptv.core.remote.TrustedPhone
import com.fireamun.iptv.core.storage.EnvelopeProtector
import com.fireamun.iptv.core.storage.KeystoreEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address

data class RemoteHostUi(
    val enabled: Boolean = true,
    val server: RemoteServerState = RemoteServerState(),
    /** The TV has no Wi-Fi or Ethernet IPv4 address to put in a pairing code. */
    val noNetwork: Boolean = false,
    /** Protected storage failed, so the TV cannot keep an identity. */
    val unavailable: Boolean = false,
)

/**
 * TV side of the phone remote. The server listens only while a TV screen of the app is in front, remote access is
 * on, and either a phone is paired or a pairing code is on screen. Keys go to the attached activity.
 */
class RemoteHost(
    private val context: Context,
    private val scope: CoroutineScope,
    private val protector: EnvelopeProtector = KeystoreEnvelope(REMOTE_KEY_ALIAS),
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("remote_host", Context.MODE_PRIVATE)
    private val trust = PreferencesRemoteTrust(preferences)
    private val mutable = MutableStateFlow(RemoteHostUi(enabled = preferences.getBoolean("enabled", true), server = RemoteServerState(phones = trust.phones())))
    val state: StateFlow<RemoteHostUi> = mutable.asStateFlow()
    @Volatile private var actions: RemoteActions? = null
    private var offering = false
    private var server: RemoteServer? = null
    /** Instrumentation pairs over loopback because CI emulators may report no Wi-Fi network. */
    @Volatile internal var addressOverride: String? = null

    // A successful key returns null, so a missing activity must be checked first rather than with ?:.
    private val forwarding = RemoteActions { key -> actions.let { target -> if (target == null) RemoteRefusal.NOT_FOCUSED else target.perform(key) } }

    /** Called on the main thread when a TV activity starts. */
    fun attach(target: RemoteActions) { actions = target; update() }
    fun detach(target: RemoteActions) { if (actions === target) { actions = null; offering = false; update() } }

    fun setEnabled(on: Boolean) {
        preferences.edit().putBoolean("enabled", on).apply()
        if (!on) offering = false
        mutable.update { it.copy(enabled = on) }
        update()
    }

    /** Shows a new pairing code; false when the TV has no local network address. */
    fun startPairing(): Boolean {
        val host = addressOverride ?: localAddress(context)
        mutable.update { it.copy(noNetwork = host == null) }
        if (host == null || !mutable.value.enabled || actions == null) return false
        val active = server() ?: return false
        offering = true
        return runCatching { active.offer(host) }.onFailure { offering = false; update() }.isSuccess
    }

    fun stopPairing() { offering = false; server?.cancelOffer(); update() }
    fun approve() { server?.approve() }
    fun deny() { server?.deny() }
    fun revoke(phoneId: String) { server?.revoke(phoneId) ?: trust.save(trust.phones().filterNot { it.id == phoneId }); refreshPhones(); update() }
    fun revokeAll() { server?.revokeAll() ?: trust.save(emptyList()); refreshPhones(); update() }

    private fun refreshPhones() = mutable.update { it.copy(server = it.server.copy(phones = trust.phones())) }

    private fun update() {
        val wanted = mutable.value.enabled && actions != null && (offering || trust.phones().isNotEmpty())
        if (wanted) runCatching { server()?.start() } else server?.stop()
    }

    private fun server(): RemoteServer? {
        server?.let { return it }
        val identity = try { identity() } catch (_: Exception) { mutable.update { it.copy(unavailable = true) }; return null }
        return RemoteServer(identity, trust, forwarding, { deviceName(context) }, scope).also { created ->
            server = created
            scope.launch { created.state.collect { value -> mutable.update { it.copy(server = value) } } }
        }
    }

    /** The certificate and key are generated once and kept sealed with the Keystore. */
    private fun identity(): RemoteIdentity {
        preferences.getString("identity", null)?.let { stored ->
            // An identity that can no longer be opened (Keystore key lost) is replaced below.
            runCatching {
                val opened = protector.open(Base64.decode(stored, Base64.NO_WRAP), IDENTITY_CONTEXT)
                try { RemoteIdentity.decode(String(opened, Charsets.UTF_8)) } finally { opened.fill(0) }
            }.getOrNull()?.let { return it }
        }
        val created = RemoteIdentity.generate()
        val plain = created.encode().toByteArray(Charsets.UTF_8)
        try {
            preferences.edit().putString("identity", Base64.encodeToString(protector.seal(plain, IDENTITY_CONTEXT), Base64.NO_WRAP)).commit()
        } finally { plain.fill(0) }
        // Phones paired with an earlier identity cannot reconnect, so they are dropped.
        trust.save(emptyList())
        return created
    }

    companion object {
        internal const val REMOTE_KEY_ALIAS = "iptv.remote.v1"
        private const val IDENTITY_CONTEXT = "remote-identity"

        /** IPv4 address of the active Wi-Fi or Ethernet network; mobile data never carries a pairing code. */
        fun localAddress(context: Context): String? {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val network = connectivity.activeNetwork ?: return null
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return null
            return connectivity.getLinkProperties(network)?.linkAddresses?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }?.hostAddress
        }

        fun deviceName(context: Context): String =
            runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: Build.MODEL.orEmpty().ifBlank { "Android" }
    }
}

/** Trusted phones are not secret (only secret hashes), so they live in plain app-private preferences. */
private class PreferencesRemoteTrust(private val preferences: SharedPreferences) : RemoteTrust {
    override fun phones(): List<TrustedPhone> = runCatching {
        val array = JSONArray(preferences.getString("phones", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            TrustedPhone(item.getString("id"), item.getString("name"), item.getString("hash"), item.getLong("paired"))
        }
    }.getOrDefault(emptyList())

    override fun save(phones: List<TrustedPhone>) {
        val array = JSONArray()
        phones.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("hash", it.secretHash).put("paired", it.pairedAtMs)) }
        preferences.edit().putString("phones", array.toString()).commit()
    }
}
