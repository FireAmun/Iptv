package com.fireamun.iptv.core.storage

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator
import java.security.GeneralSecurityException

class AesGcmTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun randomNoncesAndAuthenticatedRoundTrip() {
        val cipher = AesGcm { key }
        val first = cipher.seal("synthetic secret".toByteArray(), "source:1")
        val second = cipher.seal("synthetic secret".toByteArray(), "source:1")
        assertFalse(first.contentEquals(second))
        assertEquals("synthetic secret", cipher.open(first, "source:1").toString(Charsets.UTF_8))
        assertFalse(first.toString(Charsets.ISO_8859_1).contains("synthetic secret"))
    }
    @Test fun tamperingWrongKeyAndWrongContextAreRejected() {
        val cipher = AesGcm { key }
        val bytes = cipher.seal(byteArrayOf(1, 2, 3), "entry:1")
        val wrong = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        for (action in listOf<() -> Unit>(
            { cipher.open(bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }, "entry:1") },
            { cipher.open(bytes, "entry:2") },
            { AesGcm { wrong }.open(bytes, "entry:1") },
            { cipher.open(byteArrayOf(1), "entry:1") },
        )) try { action(); fail("Expected authentication rejection") } catch (_: GeneralSecurityException) { }
    }
}
