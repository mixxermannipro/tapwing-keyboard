// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.ai.WritingToolsEngine
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.PersonalProfile
import com.tapwing.keyboard.data.PersonalProfileStorage
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.StorageMigration
import com.tapwing.keyboard.data.TapwingPreferencesRepository
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.ThemeMode
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.dictionary.OfflineDictionaryPackManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class StorageMigrationAndDirectBootTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun migrateDeFileToCeCopiesAndSecurelyDeletesLegacySource() {
        val deFile = File(tempFolder.root, "de/adaptive_dict.json").apply {
            parentFile?.mkdirs()
            writeText("""{"customWords":{"Tapwing":5}}""")
        }
        val ceFile = File(tempFolder.root, "ce/adaptive_dict.json")

        val migrated = StorageMigration.migrateDeFileToCe(deFile, ceFile)
        assertTrue(migrated)
        assertFalse(deFile.exists())
        assertTrue(ceFile.isFile)
        assertEquals("""{"customWords":{"Tapwing":5}}""", ceFile.readText())
    }

    @Test
    fun migrateDeFileToCeDoesNotOverwriteExistingCeData() {
        val deFile = File(tempFolder.root, "de/clipboard.json").apply {
            parentFile?.mkdirs()
            writeText("legacy")
        }
        val ceFile = File(tempFolder.root, "ce/clipboard.json").apply {
            parentFile?.mkdirs()
            writeText("current-ce")
        }

        val migrated = StorageMigration.migrateDeFileToCe(deFile, ceFile)
        assertTrue(migrated)
        assertFalse(deFile.exists())
        assertEquals("current-ce", ceFile.readText())
    }

    @Test
    fun personalProfileStorageEnforcesDirectBootLockAndPreventsClearResurrection() {
        var unlocked = false
        val kv = InMemoryKvStore()
        val cipher = FakeCipherBackend()

        val storage = PersonalProfileStorage(
            isUnlocked = { unlocked },
            kvStore = kv,
            cipherBackend = cipher
        )

        // While locked before first unlock, profile returns empty and ignores writes/migrations
        assertTrue(storage.load().isEmpty)
        assertFalse(storage.migrateFromLegacyIfNeeded(PersonalProfile(firstName = "Max").toJson()))

        // Unlock device
        unlocked = true
        val legacyProfile = PersonalProfile(firstName = "Max", emails = listOf("max@example.com"))
        assertTrue(storage.migrateFromLegacyIfNeeded(legacyProfile.toJson()))
        assertEquals("Max", storage.load().firstName)

        // Clear profile: KEY_INITIALIZED prevents re-migration of legacy values
        storage.clear()
        assertTrue(storage.load().isEmpty)
        assertFalse(storage.migrateFromLegacyIfNeeded(legacyProfile.toJson()))
        assertTrue(storage.load().isEmpty)
    }

    @Test
    fun legacyThemeNamesMapCleanlyToRenamedThemes() {
        assertEquals(ThemeMode.SLATE, TapwingPreferencesRepository.parseThemeMode("PIXEL_SLATE"))
        assertEquals(ThemeMode.COBALT, TapwingPreferencesRepository.parseThemeMode("PIXEL_BLUE"))
        assertEquals(ThemeMode.SLATE, TapwingPreferencesRepository.parseThemeMode("SLATE"))
        assertEquals(ThemeMode.COBALT, TapwingPreferencesRepository.parseThemeMode("COBALT"))
        assertEquals(ThemeMode.AMOLED_DARK, TapwingPreferencesRepository.parseThemeMode("AMOLED_DARK"))
    }

    @Test
    fun isUserUnlockedFailsClosedWhenUserManagerUnavailable() {
        val stubContext = object : ContextWrapper(null) {}
        assertFalse(TapwingPreferencesRepository.isUserUnlocked(stubContext))
    }

    @Test
    fun preferencesUpdateWhileLockedPreservesLegacyPersonalKeysUntilUnlock() = runTest {
        var unlocked = false
        val kv = InMemoryKvStore()
        val cipher = FakeCipherBackend()
        val profileStorage = PersonalProfileStorage(
            isUnlocked = { unlocked },
            kvStore = kv,
            cipherBackend = cipher
        )
        val dataStoreFile = File(tempFolder.root, "direct_boot_test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { dataStoreFile }
        )

        val legacyProfile = PersonalProfile(firstName = "Ada", lastName = "Lovelace", emails = listOf("ada@example.com"))
        dataStore.edit { mutable ->
            mutable[TapwingPreferencesRepository.Keys.PERSONAL_PROFILE_JSON] = legacyProfile.toJson()
            mutable[TapwingPreferencesRepository.Keys.SNIPPET_USER_NAME] = "Ada Lovelace"
            mutable[TapwingPreferencesRepository.Keys.SNIPPET_USER_EMAIL] = "ada@example.com"
        }

        val repo = TapwingPreferencesRepository(
            dataStore = dataStore,
            profileStorage = profileStorage,
            isUserUnlockedProvider = { unlocked }
        )

        // Toggle a setting while locked: legacy personal keys must NOT be stripped.
        repo.update { it.copy(hapticFeedback = false) }
        val prefsWhileLocked = dataStore.data.first()
        assertFalse(prefsWhileLocked[TapwingPreferencesRepository.Keys.HAPTIC_FEEDBACK] ?: true)
        assertNotNull(prefsWhileLocked[TapwingPreferencesRepository.Keys.PERSONAL_PROFILE_JSON])
        assertEquals("Ada Lovelace", prefsWhileLocked[TapwingPreferencesRepository.Keys.SNIPPET_USER_NAME])
        assertEquals("ada@example.com", prefsWhileLocked[TapwingPreferencesRepository.Keys.SNIPPET_USER_EMAIL])
        assertTrue(profileStorage.load().isEmpty)

        // Once unlocked, update migrates legacy keys to CE storage and strips them from DE DataStore.
        unlocked = true
        repo.update { it.copy(soundOnKeypress = true) }
        val prefsAfterUnlock = dataStore.data.first()
        assertNull(prefsAfterUnlock[TapwingPreferencesRepository.Keys.PERSONAL_PROFILE_JSON])
        assertNull(prefsAfterUnlock[TapwingPreferencesRepository.Keys.SNIPPET_USER_NAME])
        assertNull(prefsAfterUnlock[TapwingPreferencesRepository.Keys.SNIPPET_USER_EMAIL])
        assertEquals("Ada", profileStorage.load().firstName)
        assertEquals("ada@example.com", profileStorage.load().primaryEmail)
    }

    @Test
    fun managersDoNotTouchCeStorageWhileLocked() {
        val ceAccessCount = AtomicInteger(0)
        val lockedContext = object : ContextWrapper(null) {
            override fun getApplicationContext() = this
            override fun getNoBackupFilesDir(): File {
                ceAccessCount.incrementAndGet()
                return tempFolder.root
            }
            override fun getFilesDir(): File {
                ceAccessCount.incrementAndGet()
                return tempFolder.root
            }
        }

        val packManager = OfflineDictionaryPackManager(lockedContext, isUserUnlocked = { false })
        val modelManager = LocalModelManager(lockedContext, isUserUnlocked = { false })
        try {
            packManager.refreshStates()
            assertNull(packManager.getCachedPack("de"))
            assertNull(packManager.getLoadedPack("de"))
            packManager.preloadLocaleAsync("de")

            modelManager.refreshStates()
            assertNull(modelManager.installedModelFile("sherpa-fastconformer-de-pc-int8"))

            assertEquals(0, ceAccessCount.get())
        } finally {
            packManager.close()
            modelManager.close()
        }
    }

    @Test
    fun writingToolsEngineResolvesLatestStoreAfterUnlock() = runTest {
        val lockedStore = AdaptiveDictionaryStore(storageFile = null)
        val unlockedFile = File(tempFolder.root, "unlocked_dict.json")
        val unlockedStore = AdaptiveDictionaryStore(storageFile = unlockedFile)

        var activeStore = lockedStore
        var observedStore: AdaptiveDictionaryStore? = null

        val stubContext = object : ContextWrapper(null) {
            override fun getApplicationContext() = this
        }
        val engine = WritingToolsEngine(
            secureKeyStorage = SecureKeyStorage(stubContext),
            dictionaryStoreProvider = {
                observedStore = activeStore
                activeStore
            }
        )

        engine.runAction(
            sourceText = "hallo welt",
            action = WritingAction.PROOFREAD,
            prefs = TapwingPrefs(aiProvider = AiProviderType.LOCAL_RULES)
        )
        assertTrue(observedStore === lockedStore)

        activeStore = unlockedStore
        engine.runAction(
            sourceText = "hallo welt",
            action = WritingAction.PROOFREAD,
            prefs = TapwingPrefs(aiProvider = AiProviderType.LOCAL_RULES)
        )
        assertTrue(observedStore === unlockedStore)
    }

    private class InMemoryKvStore : PersonalProfileStorage.KvStore {
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

    private class FakeCipherBackend : PersonalProfileStorage.CipherBackend {
        override fun encrypt(plainText: String): Pair<String, String> = "enc:$plainText" to "iv"
        override fun decrypt(cipherB64: String, ivB64: String): String =
            cipherB64.removePrefix("enc:")
    }
}
