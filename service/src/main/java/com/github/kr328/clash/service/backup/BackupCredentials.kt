package com.github.kr328.clash.service.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-bound WebDAV credentials; never part of portable backup data. */
internal class BackupCredentials(context: Context) {
    private val prefs = context.getSharedPreferences("backup_credentials", Context.MODE_PRIVATE)
    fun config() = BackupConfig(prefs.getString("url", "")!!, prefs.getString("directory", "GlonetX")!!,
        prefs.getString("username", "")!!, prefs.contains("secret"))

    @Synchronized fun save(config: BackupConfig, password: String?) {
        WebDavClient.validate(config)
        val previous = this.config()
        val editor = prefs.edit().putString("url", config.url.trim()).putString("directory", config.directory.trim('/'))
            .putString("username", config.username)
        if (password != null) {
            if (password.isEmpty()) editor.remove("secret").remove("iv")
            else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val plain = password.toByteArray(Charsets.UTF_8)
                try {
                    editor.putString("secret", Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP))
                        .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                } finally { plain.fill(0) }
            }
        } else if (previous.url != config.url.trim() || previous.username != config.username) {
            editor.remove("secret").remove("iv")
        }
        check(editor.commit()) { "Unable to save WebDAV configuration" }
    }

    fun password(): String {
        val secret = prefs.getString("secret", null) ?: return ""
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val bytes = cipher.doFinal(Base64.decode(secret, Base64.NO_WRAP))
            return try { bytes.toString(Charsets.UTF_8) } finally { bytes.fill(0) }
        } catch (e: Exception) {
            throw IllegalStateException("WebDAV credentials cannot be decrypted; enter the password again")
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object { private const val ALIAS = "glonetx.backup.webdav.v1" }
}
