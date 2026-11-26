// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard

import com.tapwing.keyboard.data.PersonalProfile
import com.tapwing.keyboard.data.PersonalProfileStorage
import com.tapwing.keyboard.data.StorageMigration
import com.tapwing.keyboard.data.TapwingPreferencesRepository
import com.tapwing.keyboard.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
