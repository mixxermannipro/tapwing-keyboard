// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.PersonalProfile
import com.tapwing.keyboard.data.PersonalProfileStorage
import com.tapwing.keyboard.data.SecureKeyStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException

class SecureKeyStorageTest {

    @Test
    fun legacyKeyWithoutDictationProviderIsNotLeakedToOtherProviders() {
        val kv = InMemoryKeyKvStore()
        val cipher = FakeKeyCipherBackend()
        val storage = SecureKeyStorage(
            isUnlocked = { true },
            kvStore = kv,
            cipherBackend = cipher
        )

        // Legacy key saved without DICTATION_PROVIDER must not leak across all 5 cloud providers.
        assertTrue(storage.saveApiKey("sk-legacy-only", dictationProvider = null))
        for (provider in SecureKeyStorage.CLOUD_PROVIDERS) {
            assertEquals("", storage.readKey(provider))
        }
        assertEquals("sk-legacy-only", storage.readApiKey())

        // Legacy key with explicit DICTATION_PROVIDER only resolves for that exact provider.
        assertTrue(storage.saveApiKey("sk-groq-legacy", dictationProvider = "GROQ"))
        assertEquals("sk-groq-legacy", storage.readKey(AiProviderType.GROQ_API))
        assertEquals("", storage.readKey(AiProviderType.GEMINI_API))
        assertEquals("", storage.readKey(AiProviderType.OPENROUTER_API))
        assertEquals("", storage.readKey(AiProviderType.XAI_API))
        assertEquals("", storage.readKey(AiProviderType.OPENAI_COMPATIBLE))
    }

    @Test
    fun transientDecryptFailureIsNotCachedAsEmptyString() {
        val kv = InMemoryKeyKvStore()
        val cipher = FakeKeyCipherBackend()
        val storage = SecureKeyStorage(
            isUnlocked = { true },
            kvStore = kv,
            cipherBackend = cipher
        )

        assertTrue(storage.saveKey(AiProviderType.GEMINI_API, "sk-gemini-1234"))

        // Fresh storage reading same store with transient decrypt failure
        val failingCipher = FakeKeyCipherBackend(failDecrypt = true)
        val reader = SecureKeyStorage(
            isUnlocked = { true },
            kvStore = kv,
            cipherBackend = failingCipher
        )
        assertEquals("", reader.readKey(AiProviderType.GEMINI_API))

        // Once keystore recovers, subsequent read must succeed (empty failure was not cached).
        failingCipher.failDecrypt = false
        assertEquals("sk-gemini-1234", reader.readKey(AiProviderType.GEMINI_API))
    }

    @Test
    fun saveKeyRecoversFromKeyPermanentlyInvalidatedAndNeverCrashesOnKeyStoreFailure() {
        val kv = InMemoryKeyKvStore()
        val recoverableCipher = FakeKeyCipherBackend(
            encryptFailures = ArrayDeque(listOf(KeyPermanentlyInvalidatedException()))
        )
        val storage = SecureKeyStorage(
            isUnlocked = { true },
            kvStore = kv,
            cipherBackend = recoverableCipher
        )

        assertTrue(storage.saveKey(AiProviderType.GROQ_API, "sk-recovered-key"))
        assertEquals(1, recoverableCipher.resetCount)
        assertEquals("sk-recovered-key", storage.readKey(AiProviderType.GROQ_API))

        // Persistent KeyStoreException fails gracefully with false instead of crashing.
        val brokenCipher = FakeKeyCipherBackend(
            encryptFailures = ArrayDeque(
                listOf(
                    KeyStoreException("broken-1"),
                    KeyStoreException("broken-2")
                )
            )
        )
        val brokenStorage = SecureKeyStorage(
            isUnlocked = { true },
            kvStore = kv,
            cipherBackend = brokenCipher
        )
        assertFalse(brokenStorage.saveKey(AiProviderType.XAI_API, "sk-never-crash"))
        assertEquals(1, brokenCipher.resetCount)
    }

    @Test
    fun personalProfileStorageRecoversFromUnrecoverableKeyExceptionWithoutCrashing() {
        val kv = InMemoryProfileKvStore()
        val recoverableCipher = FakeProfileCipherBackend(
            encryptFailures = ArrayDeque(listOf(UnrecoverableKeyException("invalidated")))
        )
        val storage = PersonalProfileStorage(
            isUnlocked = { true },
            kvStore = kv,
            cipherBackend = recoverableCipher
        )

        val profile = PersonalProfile(firstName = "Grace", lastName = "Hopper", emails = listOf("grace@example.com"))
        assertTrue(storage.save(profile))
        assertEquals(1, recoverableCipher.resetCount)
        assertEquals("Grace Hopper", storage.load().fullName)

        // Persistent failure returns false without throwing.
        val brokenCipher = FakeProfileCipherBackend(
            encryptFailures = ArrayDeque(
                listOf(
                    KeyStoreException("ks-1"),
                    KeyStoreException("ks-2")
                )
            )
        )
        val brokenStorage = PersonalProfileStorage(
            isUnlocked = { true },
            kvStore = kv,
            cipherBackend = brokenCipher
        )
        assertFalse(brokenStorage.save(profile))
    }

    private class InMemoryKeyKvStore : SecureKeyStorage.KvStore {
        private val map = linkedMapOf<String, String>()
        override fun getAll(): Map<String, *> = map.toMap()
        override fun getString(key: String): String? = map[key]
        override fun putStrings(values: Map<String, String?>) {
            values.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
    }

    private class FakeKeyCipherBackend(
        var failDecrypt: Boolean = false,
        private val encryptFailures: ArrayDeque<Throwable> = ArrayDeque()
    ) : SecureKeyStorage.CipherBackend {
        var resetCount: Int = 0

        override fun encrypt(plainText: String): Pair<String, String> {
            if (encryptFailures.isNotEmpty()) throw encryptFailures.removeFirst()
            return "enc:$plainText" to "iv"
        }

        override fun decrypt(cipherB64: String, ivB64: String): String {
            if (failDecrypt) throw KeyStoreException("transient")
            return cipherB64.removePrefix("enc:")
        }

        override fun resetKey() {
            resetCount++
        }
    }

    private class InMemoryProfileKvStore : PersonalProfileStorage.KvStore {
        private val strings = mutableMapOf<String, String?>()
        private val booleans = mutableMapOf<String, Boolean>()
        override fun getString(key: String): String? = strings[key]
        override fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
        override fun putValues(strings: Map<String, String?>, booleans: Map<String, Boolean>) {
            strings.forEach { (k, v) -> if (v == null) this.strings.remove(k) else this.strings[k] = v }
            this.booleans.putAll(booleans)
        }
        override fun clearAll() {
            strings.clear()
            booleans.clear()
        }
    }

    private class FakeProfileCipherBackend(
        private val encryptFailures: ArrayDeque<Throwable> = ArrayDeque()
    ) : PersonalProfileStorage.CipherBackend {
        var resetCount: Int = 0

        override fun encrypt(plainText: String): Pair<String, String> {
            if (encryptFailures.isNotEmpty()) throw encryptFailures.removeFirst()
            return "enc:$plainText" to "iv"
        }

        override fun decrypt(cipherB64: String, ivB64: String): String =
            cipherB64.removePrefix("enc:")

        override fun resetKey() {
            resetCount++
        }
    }
}
