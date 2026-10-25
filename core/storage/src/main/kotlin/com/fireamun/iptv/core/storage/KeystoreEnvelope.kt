package com.fireamun.iptv.core.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface EnvelopeProtector {
    fun seal(plaintext: ByteArray, context: String): ByteArray
    fun open(ciphertext: ByteArray, context: String): ByteArray
    fun clear()
}

class KeystoreEnvelope(private val alias: String = "iptv.library.v1") : EnvelopeProtector {
    override fun seal(plaintext: ByteArray, context: String) = AesGcm { key(create = true) }.seal(plaintext, context)
    override fun open(ciphertext: ByteArray, context: String) = AesGcm { key(create = false) }.open(ciphertext, context)
    private fun key(create: Boolean): SecretKey = synchronized(lock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey) ?: if (create) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
            }.generateKey()
        } else throw KeyStoreException("Protected storage unavailable")
    }
    override fun clear() { synchronized(lock) { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } } }
    private companion object { val lock = Any() }
}
