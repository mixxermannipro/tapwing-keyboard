package com.tapwing.keyboard.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts user-supplied AI API keys per provider with AES-256-GCM backed by Android Keystore.
 * Credential-encrypted storage is only accessed after UserManager.isUserUnlocked() is true.
 */
class SecureKeyStorage internal constructor(
    private val isUnlocked: () -> Boolean,
    private val kvStore: KvStore,
    private val cipherBackend: CipherBackend
) {
    internal interface KvStore {
        fun getAll(): Map<String, *>
        fun getString(key: String): String?
        fun putStrings(values: Map<String, String?>)
    }

    internal interface CipherBackend {
        fun encrypt(plainText: String): Pair<String, String>
        fun decrypt(cipherB64: String, ivB64: String): String
        fun resetKey() {}
    }

    constructor(context: Context) : this(
        isUnlocked = { TapwingPreferencesRepository.isUserUnlocked(context.applicationContext) },
        kvStore = SharedPrefsKvStore(context.applicationContext),
        cipherBackend = AndroidKeyStoreCipherBackend()
    )

    private val keyCache = java.util.concurrent.ConcurrentHashMap<AiProviderType, String>()

    fun saveKey(provider: AiProviderType, rawKey: String): Boolean {
        if (!isUnlocked()) return false
        val trimmed = rawKey.trim()
        val cipherField = "cipher_${provider.name}"
        val ivField = "iv_${provider.name}"
        if (trimmed.isEmpty()) {
            val removals = mutableMapOf<String, String?>(
                cipherField to null,
                ivField to null
            )
            if (legacyMatchesProvider(kvStore.getString(DICTATION_PROVIDER), provider)) {
                removals[KEY_CIPHER] = null
                removals[KEY_IV] = null
                removals[DICTATION_PROVIDER] = null
            }
            kvStore.putStrings(removals)
            keyCache.remove(provider)
            return true
        }
        val (cipherB64, ivB64) = encryptWithRecovery(trimmed).getOrNull() ?: return false
        kvStore.putStrings(
            mapOf(
                cipherField to cipherB64,
                ivField to ivB64
            )
        )
        keyCache[provider] = trimmed
        return true
    }

    fun deleteKey(provider: AiProviderType) {
        saveKey(provider, "")
    }

    fun readKey(provider: AiProviderType): String {
        if (!isUnlocked()) return ""
        keyCache[provider]?.let { return it }
        val entries = kvStore.getAll()
        val cipherB64 = entries["cipher_${provider.name}"] as? String
        val ivB64 = entries["iv_${provider.name}"] as? String
        val decrypted: Result<String> = if (!cipherB64.isNullOrBlank() && !ivB64.isNullOrBlank()) {
            decryptSafe(cipherB64, ivB64)
        } else {
            // Fallback to legacy single-key slot strictly when DICTATION_PROVIDER matches this provider.
            val legacyProv = entries[DICTATION_PROVIDER] as? String
            if (!legacyProv.isNullOrBlank() && legacyMatchesProvider(legacyProv, provider)) {
                val legCipher = entries[KEY_CIPHER] as? String
                val legIv = entries[KEY_IV] as? String
                if (!legCipher.isNullOrBlank() && !legIv.isNullOrBlank()) {
                    decryptSafe(legCipher, legIv)
                } else {
                    Result.success("")
                }
            } else {
                Result.success("")
            }
        }
        val resolved = decrypted.getOrNull() ?: return ""
        if (resolved.isNotBlank()) {
            keyCache[provider] = resolved
        }
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

    fun saveApiKey(rawKey: String, dictationProvider: String? = null): Boolean {
        if (!isUnlocked()) return false
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            kvStore.putStrings(
                mapOf(
                    KEY_CIPHER to null,
                    KEY_IV to null,
                    DICTATION_PROVIDER to null
                )
            )
            keyCache.clear()
            return true
        }
        val (cipherB64, ivB64) = encryptWithRecovery(trimmed).getOrNull() ?: return false
        kvStore.putStrings(
            mapOf(
                KEY_CIPHER to cipherB64,
                KEY_IV to ivB64,
                DICTATION_PROVIDER to dictationProvider
            )
        )
        keyCache.clear()
        providerFromLegacyName(dictationProvider)?.let { mapped ->
            return saveKey(mapped, trimmed)
        }
        return true
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
        if (!isUnlocked()) return ""
        val entries = kvStore.getAll()
        if (dictationProvider != null && entries[DICTATION_PROVIDER] != dictationProvider) return ""
        val cipherB64 = entries[KEY_CIPHER] as? String ?: return ""
        val ivB64 = entries[KEY_IV] as? String ?: return ""
        return decryptSafe(cipherB64, ivB64).getOrDefault("")
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

    private fun encryptWithRecovery(plainText: String): Result<Pair<String, String>> {
        val firstAttempt = runCatching { cipherBackend.encrypt(plainText) }
        if (firstAttempt.isSuccess) return firstAttempt
        val err = firstAttempt.exceptionOrNull()
        if (isRecoverableKeyStoreError(err)) {
            runCatching { cipherBackend.resetKey() }
            return runCatching { cipherBackend.encrypt(plainText) }
        }
        return firstAttempt
    }

    private fun decryptSafe(cipherB64: String, ivB64: String): Result<String> =
        runCatching { cipherBackend.decrypt(cipherB64, ivB64) }

    private class SharedPrefsKvStore(private val appContext: Context) : KvStore {
        private val prefs by lazy {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        override fun getAll(): Map<String, *> = prefs.all

        override fun getString(key: String): String? = prefs.getString(key, null)

        override fun putStrings(values: Map<String, String?>) {
            val editor = prefs.edit()
            values.forEach { (k, v) ->
                if (v == null) editor.remove(k) else editor.putString(k, v)
            }
            editor.apply()
        }
    }

    private class AndroidKeyStoreCipherBackend : CipherBackend {
        override fun encrypt(plainText: String): Pair<String, String> {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val iv = cipher.iv
            return Base64.encodeToString(encrypted, Base64.NO_WRAP) to Base64.encodeToString(iv, Base64.NO_WRAP)
        }

        override fun decrypt(cipherB64: String, ivB64: String): String {
            val secretKey = synchronized(keyStoreLock) {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            } ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decoded = cipher.doFinal(Base64.decode(cipherB64, Base64.NO_WRAP))
            return String(decoded, StandardCharsets.UTF_8)
        }

        override fun resetKey() {
            synchronized(keyStoreLock) {
                runCatching {
                    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    if (keyStore.containsAlias(KEY_ALIAS)) {
                        keyStore.deleteEntry(KEY_ALIAS)
                    }
                }
            }
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
    }

    companion object {
        @Volatile
        private var instance: SecureKeyStorage? = null

        fun getInstance(context: Context): SecureKeyStorage {
            val appCtx = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: SecureKeyStorage(appCtx).also { instance = it }
            }
        }

        val CLOUD_PROVIDERS = listOf(
            AiProviderType.GEMINI_API,
            AiProviderType.GROQ_API,
            AiProviderType.OPENROUTER_API,
            AiProviderType.XAI_API,
            AiProviderType.OPENAI_COMPATIBLE
        )

        private fun isRecoverableKeyStoreError(err: Throwable?): Boolean =
            err is KeyPermanentlyInvalidatedException ||
                err is UnrecoverableKeyException ||
                err is KeyStoreException ||
                err is ProviderException

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
