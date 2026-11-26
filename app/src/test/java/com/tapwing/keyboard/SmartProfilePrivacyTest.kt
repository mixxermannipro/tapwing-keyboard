// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard

import com.tapwing.keyboard.data.PersonalProfile
import com.tapwing.keyboard.data.SmartFieldType
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.ime.ImeProfileLearningController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartProfilePrivacyTest {

    @Test
    fun smartProfileAutoLearnDefaultsToOffOptIn() {
        val defaults = TapwingPrefs()
        assertFalse(defaults.smartProfileAutoLearn)
        assertTrue(defaults.consentedCloudProviders.isEmpty())
    }

    @Test
    fun personalProfileRejectsImpossibleCalendarDates() {
        assertNull(PersonalProfile(birthDate = "31.02.1995").parsedBirthDate())
        assertNull(PersonalProfile(birthDate = "29.02.2023").parsedBirthDate())
        assertNull(PersonalProfile(birthDate = "1995-04-31").parsedBirthDate())

        val validLeap = PersonalProfile(birthDate = "29.02.2024").parsedBirthDate()
        assertNotNull(validLeap)
        assertEquals(Triple(2024, 2, 29), validLeap)
    }

    @Test
    fun clearAllLearnedProfileValuesRemovesAllEntries() {
        val store = AdaptiveDictionaryStore(storageFile = null)
        store.recordLearnedProfileValue(SmartFieldType.EMAIL, "alice@example.com", "com.android.chrome")
        store.recordLearnedProfileValue(SmartFieldType.PHONE, "+491701234567", "com.android.chrome")
        store.recordProfileSelection(SmartFieldType.EMAIL, "alice@example.com", "com.android.chrome")
        assertFalse(store.listLearnedProfileValues().isEmpty())

        store.clearAllLearnedProfileValues()
        assertTrue(store.listLearnedProfileValues().isEmpty())
    }

    @Test
    fun profileLearningControllerPreventsCrossEditorRaceAndIgnoresFreeText() {
        val controller = ImeProfileLearningController()

        // Editor 1: structured email field
        controller.updateActiveFieldText(
            fieldType = SmartFieldType.EMAIL,
            rawText = "user@example.org",
            appPackage = "com.example.app",
            editorGeneration = 1L,
            privateEditor = false,
            autoLearnEnabled = true,
            learningEnabled = true,
            incognitoMode = false
        )

        // Switching to a password/private editor with empty text clears nothing unintended until consumed,
        // and free-text/private fields cannot replace it with garbage
        controller.updateActiveFieldText(
            fieldType = SmartFieldType.NONE,
            rawText = "secret chat message",
            appPackage = "com.example.chat",
            editorGeneration = 2L,
            privateEditor = true,
            autoLearnEnabled = true,
            learningEnabled = true,
            incognitoMode = false
        )

        val captured = controller.consumePending()
        assertNotNull(captured)
        assertEquals(SmartFieldType.EMAIL, captured?.fieldType)
        assertEquals("user@example.org", captured?.text)
        assertEquals(1L, captured?.editorGeneration)
        assertNull(controller.consumePending())
    }
}
