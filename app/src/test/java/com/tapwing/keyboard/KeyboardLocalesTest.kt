package com.tapwing.keyboard.data

import com.tapwing.keyboard.ai.LocalModelCatalog
import com.tapwing.keyboard.ai.LocalModelKind
import com.tapwing.keyboard.ui.keyboard.KeyboardLayoutPage
import com.tapwing.keyboard.ui.keyboard.KeyboardLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLocalesTest {

    @Test
    fun allCountriesHaveValidLayoutsAndNextLocaleCyclesCleanly() {
        KeyboardLocales.allTypingLocales.forEach { spec ->
            val rows = KeyboardLayouts.rowsFor(
                page = KeyboardLayoutPage.LETTERS,
                locale = spec.code,
                showGermanUmlauts = false,
                showNumberRow = true
            )
            assertEquals("Expected 5 rows with number row for ${spec.code}", 5, rows.size)
        }

        val enabled = listOf("de", "fr", "es")
        assertEquals("fr", KeyboardLocales.nextEnabledLocale("de", enabled))
        assertEquals("es", KeyboardLocales.nextEnabledLocale("fr", enabled))
        assertEquals("de", KeyboardLocales.nextEnabledLocale("es", enabled))
    }

    @Test
    fun modelCatalogHasRecommendedWhisperModelsWithHttpsUrlsAndNoLlms() {
        assertTrue(LocalModelCatalog.recommendedWhisper().downloadUrl.startsWith("https://"))
        assertTrue(LocalModelCatalog.whisperModels.size >= 3)
        assertTrue(LocalModelCatalog.allModels.all { it.kind == LocalModelKind.WHISPER_STT })
    }
}
