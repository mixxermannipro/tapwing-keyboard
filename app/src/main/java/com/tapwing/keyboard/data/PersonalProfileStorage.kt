// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * Stores [PersonalProfile] in Credential-Encrypted (CE) storage encrypted with an
 * AndroidKeyStore AES-256-GCM key. Never touches Device-Protected (DE) storage and
 * returns an empty profile while the device is locked before first unlock.
 */
class PersonalProfileStorage internal constructor(
    private val isUnlocked: () -> Boolean,
    private val kvStore: KvStore,
    private val cipherBackend: CipherBackend,
    loadInitialAsync: Boolean = false
) {
    internal interface KvStore {
        fun getString(key: String): String?
        fun getBoolean(key: String, default: Boolean): Boolean
        fun putValues(strings: Map<String, String?>, booleans: Map<String, Boolean>)
        fun clearAll()
    }

    internal interface CipherBackend {
        fun encrypt(plainText: String): Pair<String, String>
        fun decrypt(cipherB64: String, ivB64: String): String
        fun resetKey() {}
    }

    constructor(context: Context) : this(
        isUnlocked = { TapwingPreferencesRepository.isUserUnlocked(context.applicationContext) },
        kvStore = SharedPrefsKvStore(context.applicationContext),
        cipherBackend = AndroidKeyStoreCipherBackend(),
        loadInitialAsync = true
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _profileFlow = MutableStateFlow(PersonalProfile())
    val profileFlow: StateFlow<PersonalProfile> = _profileFlow.asStateFlow()

    @Volatile
    private var hasInitializedState = false

    init {
        if (loadInitialAsync && isUnlocked()) {
            ioScope.launch {
                synchronized(this@PersonalProfileStorage) {
                    if (!hasInitializedState) {
                        _profileFlow.value = loadInternal()
                        hasInitializedState = true
                    }
                }
            }
        }
    }

    @Synchronized
    fun load(): PersonalProfile {
        val loaded = loadInternal()
        if (!hasInitializedState) {
            _profileFlow.value = loaded
            hasInitializedState = true
        }
        return loaded
    }

    @Synchronized
    fun reloadAfterUnlock(): PersonalProfile {
        val loaded = loadInternal()
        _profileFlow.value = loaded
        hasInitializedState = true
        return loaded
    }

    @Synchronized
    fun save(profile: PersonalProfile): Boolean {
        if (!isUnlocked()) return false
        val json = profile.toJson()
        val (cipherB64, ivB64) = encryptWithRecovery(json).getOrNull() ?: return false
        kvStore.putValues(
            strings = mapOf(
                KEY_CIPHER to cipherB64,
                KEY_IV to ivB64
            ),
            booleans = mapOf(KEY_INITIALIZED to true)
        )
        _profileFlow.value = profile
        hasInitializedState = true
        return true
    }

    @Synchronized
    fun clear() {
        if (!isUnlocked()) return
        kvStore.putValues(
            strings = mapOf(KEY_CIPHER to null, KEY_IV to null),
            booleans = mapOf(KEY_INITIALIZED to true)
        )
        _profileFlow.value = PersonalProfile()
        hasInitializedState = true
    }

    /**
     * Performs a one-time migration from legacy DE DataStore values into encrypted CE storage.
     * Once CE storage has been initialized, clearing all profile fields will NOT resurrect
     * legacy values.
     */
    @Synchronized
    fun migrateFromLegacyIfNeeded(
        legacyProfileJson: String?,
        legacyUserName: String = "",
        legacyUserEmail: String = ""
    ): Boolean {
        if (!isUnlocked()) return false
        if (kvStore.getBoolean(KEY_INITIALIZED, false)) {
            return false
        }
        val hasLegacy = !legacyProfileJson.isNullOrBlank() ||
            legacyUserName.isNotBlank() ||
            legacyUserEmail.isNotBlank()
        if (!hasLegacy) return false
        val migrated = PersonalProfile.fromJson(
            rawJson = legacyProfileJson,
            legacyUserName = legacyUserName,
            legacyUserEmail = legacyUserEmail
        )
        return save(migrated)
    }

    fun isInitialized(): Boolean = if (!isUnlocked()) false else kvStore.getBoolean(KEY_INITIALIZED, false)

    private fun loadInternal(): PersonalProfile {
        if (!isUnlocked()) return PersonalProfile()
        val cipherB64 = kvStore.getString(KEY_CIPHER).orEmpty()
        val ivB64 = kvStore.getString(KEY_IV).orEmpty()
        if (cipherB64.isBlank() || ivB64.isBlank()) return PersonalProfile()
        val json = runCatching { cipherBackend.decrypt(cipherB64, ivB64) }.getOrDefault("")
        if (json.isBlank()) return PersonalProfile()
        return PersonalProfile.fromJson(json)
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

    private class SharedPrefsKvStore(private val appContext: Context) : KvStore {
        private val prefs by lazy {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        override fun getString(key: String): String? = prefs.getString(key, null)

        override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

        override fun putValues(strings: Map<String, String?>, booleans: Map<String, Boolean>) {
            val editor = prefs.edit()
            strings.forEach { (k, v) ->
                if (v == null) editor.remove(k) else editor.putString(k, v)
            }
            booleans.forEach { (k, v) -> editor.putBoolean(k, v) }
            editor.apply()
        }

        override fun clearAll() {
            prefs.edit().clear().apply()
        }
    }

    private class AndroidKeyStoreCipherBackend : CipherBackend {
        override fun encrypt(plainText: String): Pair<String, String> {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            return Base64.encodeToString(encrypted, Base64.NO_WRAP) to
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        }

        override fun decrypt(cipherB64: String, ivB64: String): String {
            return runCatching {
                val secretKey = synchronized(keyStoreLock) {
                    val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
                } ?: return ""
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                val decoded = cipher.doFinal(Base64.decode(cipherB64, Base64.NO_WRAP))
                String(decoded, StandardCharsets.UTF_8)
            }.getOrDefault("")
        }

        override fun resetKey() {
            synchronized(keyStoreLock) {
                runCatching {
                    val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    if (ks.containsAlias(KEYSTORE_ALIAS)) {
                        ks.deleteEntry(KEYSTORE_ALIAS)
                    }
                }
            }
        }

        private fun getOrCreateKey(): SecretKey = synchronized(keyStoreLock) {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return@synchronized existing

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
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
        private var instance: PersonalProfileStorage? = null

        fun getInstance(context: Context): PersonalProfileStorage {
            val appCtx = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: PersonalProfileStorage(appCtx).also { instance = it }
            }
        }

        private fun isRecoverableKeyStoreError(err: Throwable?): Boolean =
            err is KeyPermanentlyInvalidatedException ||
                err is UnrecoverableKeyException ||
                err is KeyStoreException ||
                err is ProviderException

        private val keyStoreLock = Any()
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "tapwing_personal_profile_v1"
        private const val PREFS_NAME = "tapwing_personal_profile_secure"
        private const val KEY_CIPHER = "profile_ciphertext"
        private const val KEY_IV = "profile_iv"
        private const val KEY_INITIALIZED = "profile_initialized"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
