package com.tapwing.keyboard.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the user-supplied AI API key with an AES-256-GCM key backed by Android Keystore.
 * Credential-encrypted storage is only accessed after UserManager.isUserUnlocked() is true.
 */
class SecureKeyStorage(context: Context) {

    private val appContext = context.applicationContext

    fun saveApiKey(rawKey: String, dictationProvider: String? = null) {
        if (!TapwingPreferencesRepository.isUserUnlocked(appContext)) return
        val trimmed = rawKey.trim()
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_CIPHER).remove(KEY_IV).remove(DICTATION_PROVIDER).apply()
            return
        }
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        prefs.edit()
            .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(DICTATION_PROVIDER, dictationProvider)
            .apply()
    }

    fun readApiKey(): String = readKey()

    fun readDictationKey(provider: String): String = readKey(provider)

    private fun readKey(dictationProvider: String? = null): String {
        if (!TapwingPreferencesRepository.isUserUnlocked(appContext)) return ""
        val entries = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
        if (dictationProvider != null && entries[DICTATION_PROVIDER] != dictationProvider) return ""
        val cipherB64 = entries[KEY_CIPHER] as? String ?: return ""
        val ivB64 = entries[KEY_IV] as? String ?: return ""
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decoded = cipher.doFinal(Base64.decode(cipherB64, Base64.NO_WRAP))
            String(decoded, StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    fun hasApiKey(): Boolean = readApiKey().isNotBlank()

    fun maskedPreview(): String {
        val raw = readApiKey()
        if (raw.isBlank()) return ""
        return if (raw.length <= 8) "••••••••" else "${raw.take(4)}••••${raw.takeLast(4)}"
    }

    fun clearApiKey() {
        saveApiKey("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tapwing_ai_api_key_v1"
        const val PREFS_NAME = "tapwing_secure_keys"
        const val KEY_CIPHER = "api_key_ciphertext"
        const val KEY_IV = "api_key_iv"
        const val DICTATION_PROVIDER = "dictation_provider"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
