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
    fun modelCatalogHasRecommendedSpeechAndWhisperModelsWithHttpsUrls() {
        assertTrue(LocalModelCatalog.recommendedWhisper().downloadUrl.startsWith("https://"))
        assertTrue(LocalModelCatalog.recommendedSherpa().downloadUrl.startsWith("https://"))
        assertTrue(LocalModelCatalog.sherpaModels.size >= 6)
        assertTrue(LocalModelCatalog.whisperModels.size >= 3)
        assertTrue(LocalModelCatalog.llmModels.size >= 6)
        assertTrue(LocalModelCatalog.speechModels.all { it.kind.isSpeechModel && it.downloadUrl.startsWith("https://") })
        assertTrue(LocalModelCatalog.llmModels.all { it.kind.isLlm && it.downloadUrl.startsWith("https://") })
        assertEquals("sherpa-fastconformer-de-pc-int8", LocalModelCatalog.recommendedForLocale("de-DE").id)
        assertEquals("sherpa-nemotron-3.5-streaming-multilingual-int8", LocalModelCatalog.recommendedForLocale("de-DE", multilingual = true).id)
        assertEquals("sherpa-canary-180m-flash-int8", LocalModelCatalog.recommendedForLocale("en-US").id)
        assertEquals("sherpa-canary-180m-flash-int8", LocalModelCatalog.recommendedForLocale("fr-FR").id)
        assertEquals("sherpa-parakeet-ultra-0.6b-int8", LocalModelCatalog.recommendedForLocale("pl-PL").id)
        assertEquals("whisper-base-q5_1", LocalModelCatalog.recommendedForLocale("zh-CN").id)
    }
}
