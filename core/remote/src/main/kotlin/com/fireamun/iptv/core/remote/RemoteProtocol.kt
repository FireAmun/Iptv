package com.fireamun.iptv.core.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

/** Version of the phone-to-TV protocol; both sides refuse any other. */
const val REMOTE_PROTOCOL = 1
/** Port the TV tries first so a paired phone can find it again; another free port is used if it is taken. */
const val REMOTE_DEFAULT_PORT = 47_820
internal const val MAX_FRAME_BYTES = 8 * 1024
/** Most characters one typing message adds; longer pastes are sent in parts. */
const val MAX_TYPED_CHARS = 500
/** Most characters one typing message erases. */
const val MAX_ERASED_CHARS = 4_096

/** Buttons a paired phone can press on the TV. */
enum class RemoteKey { UP, DOWN, LEFT, RIGHT, OK, BACK, PLAY_PAUSE, CHANNEL_UP, CHANNEL_DOWN }

/** Why the TV refused a pairing, a connection or a key; safe to show and to log. */
enum class RemoteRefusal {
    /** The pairing code is no longer offered (timed out, used or replaced). */
    EXPIRED,
    /** Someone declined the phone on the TV, or nobody answered in time. */
    DENIED,
    /** The pairing code or the phone's secret is wrong. */
    INVALID,
    /** The TV no longer trusts this phone. */
    REVOKED,
    /** The TV is already handling another pairing or has no room for more phones. */
    BUSY,
    /** The other side speaks a different protocol version. */
    VERSION,
    /** The TV is not the one the code or saved pairing describes (certificate mismatch). */
    IDENTITY,
    /** A playback key arrived while nothing was playing. */
    NO_PLAYBACK,
    /** The TV app is not in front, so a navigation key had nowhere to go. */
    NOT_FOCUSED,
    /** Typed text arrived while no text box was selected on the TV. */
    NO_FIELD,
    /** Keys arrived faster than the TV accepts them. */
    RATE_LIMITED,
    /** Anything else; the connection stays usable. */
    FAILED,
}

/** Malformed, oversized or unexpected traffic; the connection is closed. */
class RemoteProtocolException(message: String) : IOException(message)

/** Length-prefixed (4-byte big-endian) UTF-8 JSON objects. */
internal object Frames {
    fun read(input: DataInputStream): JsonObject {
        val length = input.readInt()
        if (length <= 0 || length > MAX_FRAME_BYTES) throw RemoteProtocolException("Frame size")
        val bytes = ByteArray(length)
        input.readFully(bytes)
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) { throw RemoteProtocolException("Encoding") }
        return try { JsonParser.parseString(text) as? JsonObject } catch (_: JsonParseException) { null }
            ?: throw RemoteProtocolException("Not an object")
    }

    fun write(output: DataOutputStream, message: JsonObject) {
        val bytes = message.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FRAME_BYTES) throw RemoteProtocolException("Frame size")
        synchronized(output) {
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        }
    }
}

internal fun message(type: String, vararg fields: Pair<String, Any?>): JsonObject = JsonObject().apply {
    addProperty("t", type)
    for ((name, value) in fields) when (value) {
        null -> Unit
        is String -> addProperty(name, value)
        is Number -> addProperty(name, value)
        is Boolean -> addProperty(name, value)
        else -> error("Unsupported field")
    }
}

/** A string field no longer than [max] characters; anything else (missing, number, object) is null. */
internal fun JsonObject.text(name: String, max: Int): String? =
    (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.asString?.takeIf { it.length <= max }

internal fun JsonObject.integer(name: String): Int? =
    (get(name) as? JsonPrimitive)?.takeIf { it.isNumber }?.asString?.toIntOrNull()

internal fun JsonObject.type(): String? = text("t", 16)

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun constantEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

internal val base64Url: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
internal fun decodeBase64Url(value: String): ByteArray? = try { Base64.getUrlDecoder().decode(value) } catch (_: IllegalArgumentException) { null }

/** Typed text for single-line fields: no control characters and no text-direction overrides. */
internal fun cleanTyped(value: String): String =
    value.filterNot { it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' }

/**
 * The six digits both screens show during code pairing. They cover the TV certificate each side actually used, so a
 * device in the middle, which cannot present the TV's certificate, leaves the two screens showing different codes.
 * Each side commits to or reveals its random value in an order that leaves such a device one guess in a million.
 */
internal fun pairingCode(fingerprint: String, phoneNonce: ByteArray, tvNonce: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("amariptv-pairing-code-v1".toByteArray(Charsets.UTF_8))
    digest.update(fingerprint.toByteArray(Charsets.UTF_8))
    digest.update(phoneNonce)
    digest.update(tvNonce)
    val hash = digest.digest()
    var value = 0L
    for (index in 0 until 5) value = (value shl 8) or (hash[index].toLong() and 0xff)
    return "%06d".format(value % 1_000_000)
}

internal val HEX_64 = Regex("[0-9a-f]{64}")

/** Names shown on the other screen: printable, trimmed and short. */
internal fun cleanDeviceName(value: String?): String? =
    value?.filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }?.trim()?.take(40)?.takeIf { it.isNotEmpty() }

/**
 * What the TV's pairing QR code carries: where the TV listens, the SHA-256 of its certificate and a single-use
 * random code. It holds no provider data and no long-lived secret.
 */
data class PairingLink(val host: String, val port: Int, val fingerprint: String, val nonce: String) {
    init {
        require(ipv4(host)) { "IPv4 address" }
        require(port in 1..65_535) { "Port" }
        require(FINGERPRINT.matches(fingerprint)) { "Fingerprint" }
        require(NONCE.matches(nonce)) { "Nonce" }
    }

    fun encode(): String = "$PREFIX?v=$REMOTE_PROTOCOL&h=$host&p=$port&f=$fingerprint&n=$nonce"
    override fun toString(): String = "PairingLink(host=$host, port=$port)"

    companion object {
        private const val PREFIX = "amariptv://pair"
        private val FINGERPRINT = Regex("[0-9a-f]{64}")
        private val NONCE = Regex("[A-Za-z0-9_-]{22}")
        private val KEYS = setOf("v", "h", "p", "f", "n")

        /** Null for anything that is not a version-1 pairing code from a TV. */
        fun parse(text: String): PairingLink? {
            if (text.length > 256 || !text.startsWith("$PREFIX?")) return null
            val pairs = text.substring(PREFIX.length + 1).split('&').map { it.split('=', limit = 2) }
            if (pairs.any { it.size != 2 }) return null
            val fields = pairs.associate { it[0] to it[1] }
            if (fields.size != pairs.size || fields.keys != KEYS || fields["v"] != REMOTE_PROTOCOL.toString()) return null
            val port = fields.getValue("p").takeIf { it.length <= 5 && it.all(Char::isDigit) }?.toInt() ?: return null
            return runCatching { PairingLink(fields.getValue("h"), port, fields.getValue("f"), fields.getValue("n")) }.getOrNull()
        }

        internal fun ipv4(host: String): Boolean {
            val parts = host.split('.')
            return parts.size == 4 && parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) && (part.length == 1 || part[0] != '0') && part.toInt() <= 255
            }
        }
    }
}
