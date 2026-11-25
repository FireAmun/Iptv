package com.fireamun.iptv.core.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2 verifier; the PIN itself is never stored. A local PIN is a household convenience, not device security. */
class PinVerifier(salt: ByteArray, hash: ByteArray, val iterations: Int) {
    private val salt = salt.copyOf()
    private val hash = hash.copyOf()
    init { require(this.salt.size == SALT_BYTES && this.hash.size == HASH_BYTES && iterations in MIN_ITERATIONS..MAX_ITERATIONS) }
    fun salt() = salt.copyOf()
    fun hash() = hash.copyOf()
    fun matches(pin: CharArray): Boolean = ParentalPin.valid(pin) && MessageDigest.isEqual(ParentalPin.derive(pin, salt, iterations), hash)
    override fun toString() = "[protected pin verifier]"

    companion object {
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
        const val MIN_ITERATIONS = 1_000
        const val MAX_ITERATIONS = 1_000_000
    }
}

object ParentalPin {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8
    const val ITERATIONS = 60_000

    fun valid(pin: CharArray) = pin.size in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun create(pin: CharArray, random: SecureRandom = SecureRandom(), iterations: Int = ITERATIONS): PinVerifier {
        require(valid(pin))
        val salt = ByteArray(PinVerifier.SALT_BYTES).also(random::nextBytes)
        return PinVerifier(salt, derive(pin, salt, iterations), iterations)
    }

    internal fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, PinVerifier.HASH_BYTES * 8)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}

/**
 * Wrong-PIN throttle: [FREE_ATTEMPTS] tries, then a wait starting at 30 seconds that doubles per further failure up
 * to 15 minutes. A wall-clock wait survives restarts; one that lies beyond the maximum (clock moved back) is capped.
 */
data class PinThrottle(val failures: Int = 0, val lockedUntilMs: Long = 0) {
    init { require(failures >= 0 && lockedUntilMs >= 0) }

    fun waitMs(nowMs: Long): Long = (lockedUntilMs - nowMs).coerceIn(0, MAX_WAIT_MS)

    fun failed(nowMs: Long): PinThrottle {
        val next = failures + 1
        if (next < FREE_ATTEMPTS) return PinThrottle(next, 0)
        val wait = (FIRST_WAIT_MS shl (next - FREE_ATTEMPTS).coerceAtMost(5)).coerceAtMost(MAX_WAIT_MS)
        return PinThrottle(next, nowMs + wait)
    }

    companion object {
        const val FREE_ATTEMPTS = 5
        const val FIRST_WAIT_MS = 30_000L
        const val MAX_WAIT_MS = 15 * 60_000L
    }
}
