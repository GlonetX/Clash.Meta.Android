package com.github.kr328.clash.service.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Protects subscription decryption keys at rest with a device-bound key. */
internal object ProfileCredentialsRepository {
    private const val ALIAS = "glonetx.profile.credentials.v1"
    private const val PREFIX = "gxcred:v1:"
    fun isProtected(value: String): Boolean = value.startsWith(PREFIX)

    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun encrypt(value: String?, scope: String, id: String): String? {
        if (value == null) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD("$scope/$id".toByteArray(Charsets.UTF_8))
        val bytes = value.toByteArray(Charsets.UTF_8)
        return try { PREFIX + Base64.encodeToString(cipher.iv + cipher.doFinal(bytes), Base64.NO_WRAP) }
        finally { bytes.fill(0) }
    }

    fun decrypt(value: String?, scope: String, id: String): String? {
        if (value == null) return null
        require(value.startsWith(PREFIX)) { "Unprotected subscription credential" }
        try {
            val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            require(payload.size >= 28) { "Invalid protected subscription credential" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
            cipher.updateAAD("$scope/$id".toByteArray(Charsets.UTF_8))
            val plain = cipher.doFinal(payload, 12, payload.size - 12)
            return try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
        } catch (e: Exception) {
            throw IllegalStateException("Subscription credential cannot be decrypted", e)
        }
    }
}
