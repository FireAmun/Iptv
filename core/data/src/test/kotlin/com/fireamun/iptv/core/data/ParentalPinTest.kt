package com.fireamun.iptv.core.data

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom

class ParentalPinTest {
    @Test fun verifierMatchesOnlyTheSamePinAndKeepsNoPlaintext() {
        val verifier = ParentalPin.create("2468".toCharArray(), SecureRandom(), iterations = 1_000)
        assertTrue(verifier.matches("2468".toCharArray()))
        assertFalse(verifier.matches("2469".toCharArray()))
        assertFalse(verifier.matches("24680".toCharArray()))
        assertFalse(verifier.hash().contentEquals("2468".toByteArray()))
        assertEquals("[protected pin verifier]", verifier.toString())
        val again = ParentalPin.create("2468".toCharArray(), SecureRandom(), iterations = 1_000)
        assertFalse("Salt must differ per PIN", verifier.salt().contentEquals(again.salt()))
        assertTrue(PinVerifier(verifier.salt(), verifier.hash(), verifier.iterations).matches("2468".toCharArray()))
    }

    @Test fun onlyFourToEightAsciiDigitsAreValid() {
        assertTrue(ParentalPin.valid("0000".toCharArray()))
        assertTrue(ParentalPin.valid("12345678".toCharArray()))
        listOf("", "123", "123456789", "12a4", "١٢٣٤", " 1234").forEach { assertFalse(it, ParentalPin.valid(it.toCharArray())) }
        assertThrows(IllegalArgumentException::class.java) { ParentalPin.create("12".toCharArray()) }
    }

    @Test fun malformedStoredVerifierIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PinVerifier(ByteArray(4), ByteArray(32), 60_000) }
        assertThrows(IllegalArgumentException::class.java) { PinVerifier(ByteArray(16), ByteArray(32), 0) }
    }

    @Test fun throttleGivesFiveTriesThenDoublingWaitsCappedAtFifteenMinutes() {
        var throttle = PinThrottle()
        repeat(4) { throttle = throttle.failed(1_000); assertEquals(0, throttle.waitMs(1_000)) }
        throttle = throttle.failed(1_000)
        assertEquals(30_000, throttle.waitMs(1_000))
        assertEquals(0, throttle.waitMs(31_000))
        throttle = throttle.failed(31_000)
        assertEquals(60_000, throttle.waitMs(31_000))
        repeat(10) { throttle = throttle.failed(0) }
        assertEquals(PinThrottle.MAX_WAIT_MS, throttle.waitMs(0))
        // A clock moved back cannot extend the wait beyond the maximum.
        assertEquals(PinThrottle.MAX_WAIT_MS, PinThrottle(9, Long.MAX_VALUE / 2).waitMs(0))
    }
}
