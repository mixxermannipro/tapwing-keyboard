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
 * Encrypts user-supplied AI API keys per provider with AES-256-GCM backed by Android Keystore.
 * Credential-encrypted storage is only accessed after UserManager.isUserUnlocked() is true.
 */
class SecureKeyStorage(context: Context) {

    private val appContext = context.applicationContext
    private val keyCache = java.util.concurrent.ConcurrentHashMap<AiProviderType, String>()

    fun saveKey(provider: AiProviderType, rawKey: String) {
        if (!TapwingPreferencesRepository.isUserUnlocked(appContext)) return
        val trimmed = rawKey.trim()
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cipherField = "cipher_${provider.name}"
        val ivField = "iv_${provider.name}"
        if (trimmed.isEmpty()) {
            val editor = prefs.edit().remove(cipherField).remove(ivField)
            if (legacyMatchesProvider(prefs.getString(DICTATION_PROVIDER, null), provider)) {
                editor.remove(KEY_CIPHER).remove(KEY_IV).remove(DICTATION_PROVIDER)
            }
            editor.apply()
            keyCache.remove(provider)
            return
        }
        val (cipherB64, ivB64) = encryptValue(trimmed)
        prefs.edit()
            .putString(cipherField, cipherB64)
            .putString(ivField, ivB64)
            .apply()
        keyCache[provider] = trimmed
    }

    fun deleteKey(provider: AiProviderType) {
        saveKey(provider, "")
    }

    fun readKey(provider: AiProviderType): String {
        if (!TapwingPreferencesRepository.isUserUnlocked(appContext)) return ""
        keyCache[provider]?.let { return it }
        val entries = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
        val cipherB64 = entries["cipher_${provider.name}"] as? String
        val ivB64 = entries["iv_${provider.name}"] as? String
        val resolved = if (!cipherB64.isNullOrBlank() && !ivB64.isNullOrBlank()) {
            decryptValue(cipherB64, ivB64)
        } else {
            // Fallback to legacy single-key slot for smooth migration
            val legacyProv = entries[DICTATION_PROVIDER] as? String
            if (legacyProv.isNullOrBlank() || legacyMatchesProvider(legacyProv, provider)) {
                val legCipher = entries[KEY_CIPHER] as? String
                val legIv = entries[KEY_IV] as? String
                if (!legCipher.isNullOrBlank() && !legIv.isNullOrBlank()) decryptValue(legCipher, legIv) else ""
            } else ""
        }
        keyCache[provider] = resolved
        return resolved
    }

    fun hasKey(provider: AiProviderType): Boolean = readKey(provider).isNotBlank()

    fun maskedPreview(provider: AiProviderType): String {
        val raw = readKey(provider)
        if (raw.isBlank()) return ""
        return if (raw.length <= 8) "••••••••" else "${raw.take(4)}••••${raw.takeLast(4)}"
    }

    fun configuredCloudProviders(): List<AiProviderType> =
        CLOUD_PROVIDERS.filter { hasKey(it) }

    fun saveApiKey(rawKey: String, dictationProvider: String? = null) {
        if (!TapwingPreferencesRepository.isUserUnlocked(appContext)) return
        val trimmed = rawKey.trim()
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_CIPHER).remove(KEY_IV).remove(DICTATION_PROVIDER).apply()
            keyCache.clear()
            return
        }
        val (cipherB64, ivB64) = encryptValue(trimmed)
        prefs.edit()
            .putString(KEY_CIPHER, cipherB64)
            .putString(KEY_IV, ivB64)
            .putString(DICTATION_PROVIDER, dictationProvider)
            .apply()
        keyCache.clear()
        providerFromLegacyName(dictationProvider)?.let { mapped ->
            saveKey(mapped, trimmed)
        }
    }

    fun readApiKey(): String = readLegacyKey()

    fun readDictationKey(provider: String): String {
        providerFromLegacyName(provider)?.let { mapped ->
            val slotKey = readKey(mapped)
            if (slotKey.isNotBlank()) return slotKey
        }
        return readLegacyKey(provider)
    }

    private fun readLegacyKey(dictationProvider: String? = null): String {
        if (!TapwingPreferencesRepository.isUserUnlocked(appContext)) return ""
        val entries = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
        if (dictationProvider != null && entries[DICTATION_PROVIDER] != dictationProvider) return ""
        val cipherB64 = entries[KEY_CIPHER] as? String ?: return ""
        val ivB64 = entries[KEY_IV] as? String ?: return ""
        return decryptValue(cipherB64, ivB64)
    }

    fun hasApiKey(): Boolean = readApiKey().isNotBlank() || CLOUD_PROVIDERS.any { hasKey(it) }

    fun maskedPreview(): String {
        val raw = readApiKey().ifBlank {
            CLOUD_PROVIDERS.firstNotNullOfOrNull { p -> readKey(p).takeIf { it.isNotBlank() } }.orEmpty()
        }
        if (raw.isBlank()) return ""
        return if (raw.length <= 8) "••••••••" else "${raw.take(4)}••••${raw.takeLast(4)}"
    }

    fun clearApiKey() {
        saveApiKey("")
    }

    private fun encryptValue(plainText: String): Pair<String, String> {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    private fun decryptValue(cipherB64: String, ivB64: String): String {
        return runCatching {
            val secretKey = synchronized(keyStoreLock) {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            } ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decoded = cipher.doFinal(Base64.decode(cipherB64, Base64.NO_WRAP))
            String(decoded, StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey = synchronized(keyStoreLock) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return@synchronized existing

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
        generator.generateKey()
    }

    companion object {
        val CLOUD_PROVIDERS = listOf(
            AiProviderType.GEMINI_API,
            AiProviderType.GROQ_API,
            AiProviderType.OPENROUTER_API,
            AiProviderType.XAI_API,
            AiProviderType.OPENAI_COMPATIBLE
        )

        private fun providerFromLegacyName(name: String?): AiProviderType? = when (name?.uppercase()) {
            "GEMINI" -> AiProviderType.GEMINI_API
            "GROQ" -> AiProviderType.GROQ_API
            "OPENAI" -> AiProviderType.OPENAI_COMPATIBLE
            else -> null
        }

        private fun legacyMatchesProvider(legacyName: String?, provider: AiProviderType): Boolean =
            providerFromLegacyName(legacyName) == provider

        internal fun isValidApiKey(raw: String): Boolean {
            val value = raw.trim()
            return value.isNotEmpty() && value.all { it in '!'..'~' }
        }

        private val keyStoreLock = Any()
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "tapwing_ai_api_key_v1"
        private const val PREFS_NAME = "tapwing_secure_keys"
        private const val KEY_CIPHER = "api_key_ciphertext"
        private const val KEY_IV = "api_key_iv"
        private const val DICTATION_PROVIDER = "dictation_provider"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
