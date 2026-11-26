package com.fireamun.iptv.prototype

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address

/** A TV running Amar Iptv's phone remote, found on the local network. */
data class FoundTv(val name: String, val host: String, val port: Int, val fingerprint: String)

private const val SERVICE_TYPE = "_amariptv._tcp."
private val FINGERPRINT = Regex("[0-9a-f]{64}")

/**
 * Announces the TV on the local network (DNS-SD) while its remote server listens, so phones can list it for code
 * pairing and find it again after its address changes. The record carries the TV name, port and certificate
 * fingerprint; it is a public value and grants nothing on its own.
 */
internal class RemoteAdvertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null
    private var advertised: Triple<String, Int, String>? = null

    /** Main thread. Re-announces only when the name, port or certificate changed. */
    fun advertise(name: String, port: Int, fingerprint: String) {
        val wanted = Triple(name, port, fingerprint)
        if (advertised == wanted) return
        stop()
        val manager = nsd ?: return
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("v", "1")
            setAttribute("f", fingerprint)
        }
        val callback = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) { if (listener === this) { listener = null; advertised = null } }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        // Discovery is a convenience: without it phones still pair by QR code or typed address.
        if (runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, callback) }.isSuccess) {
            listener = callback
            advertised = wanted
        }
    }

    fun stop() {
        listener?.let { registered -> runCatching { nsd?.unregisterService(registered) } }
        listener = null
        advertised = null
    }
}

/** Finds TVs announced by [RemoteAdvertiser]. Nothing found is trusted: pairing still checks the TV itself. */
internal class RemoteDiscovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    /** The TVs found so far, updated as they appear and disappear; searching stops when collection stops. */
    fun search(): Flow<List<FoundTv>> = callbackFlow {
        val manager = nsd ?: run { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val lock = Any()
        val found = LinkedHashMap<String, FoundTv>()
        val queue = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        fun publish() = trySend(synchronized(lock) { found.values.toList() })

        // Older Android versions resolve one service at a time, so services wait their turn.
        fun next() {
            val info = synchronized(lock) {
                if (resolving) return
                queue.removeFirstOrNull()?.also { resolving = true } ?: return
            }
            val resolved = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, error: Int) { synchronized(lock) { resolving = false }; next() }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val tv = info.toFoundTv()
                    synchronized(lock) { resolving = false; if (tv != null) found[info.serviceName] = tv }
                    if (tv != null) publish()
                    next()
                }
            }
            @Suppress("DEPRECATION")
            if (runCatching { manager.resolveService(info, resolved) }.isFailure) { synchronized(lock) { resolving = false }; next() }
        }

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, error: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, error: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(lock) { if (queue.none { it.serviceName == info.serviceName }) queue.addLast(info) }
                next()
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                if (synchronized(lock) { found.remove(info.serviceName) } != null) publish()
            }
        }
        trySend(emptyList())
        val started = runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }.isSuccess
        awaitClose { if (started) runCatching { manager.stopServiceDiscovery(discovery) } }
    }

    /** Looks for the TV with this certificate, for example after the router gave it a new address. */
    suspend fun find(fingerprint: String, timeoutMs: Long): FoundTv? = withTimeoutOrNull(timeoutMs) {
        search().mapNotNull { tvs -> tvs.firstOrNull { it.fingerprint == fingerprint } }.first()
    }

    private fun NsdServiceInfo.toFoundTv(): FoundTv? {
        @Suppress("DEPRECATION")
        val address = host as? Inet4Address ?: return null
        val attributes = attributes ?: return null
        if (attributes["v"]?.toString(Charsets.UTF_8) != "1") return null
        val fingerprint = attributes["f"]?.toString(Charsets.UTF_8)?.takeIf { FINGERPRINT.matches(it) } ?: return null
        if (port !in 1..65_535) return null
        val name = serviceName.orEmpty().filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }.trim().take(40).ifEmpty { "TV" }
        return FoundTv(name, address.hostAddress ?: return null, port, fingerprint)
    }
}
