package com.fireamun.iptv.core.remote

import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * The TV's long-lived self-signed certificate. Phones trust it only through the fingerprint shown in the pairing QR
 * code, never through a certificate authority or discovery. Replacing it requires pairing every phone again.
 */
class RemoteIdentity private constructor(private val held: HeldCertificate) {
    val fingerprint: String = fingerprint(held.certificate)

    /** PEM certificate followed by the PKCS#8 private key; the caller stores it encrypted. */
    fun encode(): String = held.certificatePem() + held.privateKeyPkcs8Pem()

    internal fun serverContext(): SSLContext = HandshakeCertificates.Builder().heldCertificate(held).build().sslContext()

    companion object {
        fun generate(clock: () -> Long = System::currentTimeMillis): RemoteIdentity {
            val now = clock()
            return RemoteIdentity(HeldCertificate.Builder().ecdsa256().commonName("Amar Iptv TV")
                .validityInterval(now - TimeUnit.DAYS.toMillis(1), now + TimeUnit.DAYS.toMillis(365L * 30)).build())
        }

        fun decode(encoded: String): RemoteIdentity = RemoteIdentity(HeldCertificate.decode(encoded))

        fun fingerprint(certificate: X509Certificate): String = sha256Hex(certificate.encoded)
    }
}

/** Accepts exactly one server certificate, identified by the SHA-256 of its encoding. */
internal class PinnedTrustManager(private val expected: String) : X509TrustManager {
    var mismatch = false
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("No certificate")
        if (!constantEquals(RemoteIdentity.fingerprint(leaf), expected)) {
            mismatch = true
            throw CertificateException("Unexpected TV certificate")
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("Client certificates are not used")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal val modernProtocols = arrayOf("TLSv1.3", "TLSv1.2")
internal fun SSLSocket.restrictProtocols() {
    enabledProtocols = supportedProtocols.filter { it in modernProtocols }.toTypedArray()
}

/** Opens a TLS connection that only succeeds against the pinned TV certificate. */
internal fun openPinned(host: String, port: Int, fingerprint: String, timeoutMs: Int): Pair<SSLSocket, PinnedTrustManager> {
    val trust = PinnedTrustManager(fingerprint)
    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
    val raw = Socket()
    try {
        raw.connect(InetSocketAddress(host, port), timeoutMs)
        raw.soTimeout = timeoutMs
        val socket = context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        socket.restrictProtocols()
        socket.startHandshake()
        return socket to trust
    } catch (failure: Exception) {
        raw.close()
        if (trust.mismatch) throw RemoteRefusedException(RemoteRefusal.IDENTITY)
        throw failure
    }
}

/** The TV answered and refused; [reason] says why. */
class RemoteRefusedException(val reason: RemoteRefusal) : java.io.IOException("Refused: $reason")
