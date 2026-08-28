package com.guardianlink.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts local session tokens with an Android Keystore AES key. Legacy plaintext is migrated on first read. */
class SecureTokenStore(context: Context, private val preferencesName: String) {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val alias = "guardianlink.$preferencesName.tokens.v1"

    fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        if (!stored.startsWith(PREFIX)) {
            put(key, stored) // One-time upgrade of sessions created by earlier app versions.
            return stored
        }
        return decrypt(stored.removePrefix(PREFIX))
    }

    fun put(key: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key)
        else editor.putString(key, encrypt(value) ?: value)
        editor.apply()
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size).put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
        PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(value: String): String? = runCatching {
        val packed = Base64.decode(value, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(packed)
        val ivLength = buffer.get().toInt()
        require(ivLength in 12..32 && buffer.remaining() > ivLength)
        val iv = ByteArray(ivLength).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private companion object {
        const val PREFIX = "keystore:v1:"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
