package com.tapwing.keyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDictionaryPackManagerTest {

    @Test
    fun parsesFrequencyWordListIndexesPrefixesAndFiltersNoise() {
        val sample = sequenceOf(
            "# comment line",
            "",
            "a 99999", // single char skipped
            "1234 500", // numbers skipped
            "Hallo 12000",
            "Haus 9500",
            "Hase 4200",
            "Beispiel 8000",
            "Welt 7100"
        )

        val pack = OfflineDictionaryPackManager.parseFrequencyWordList("de", sample)

        assertTrue(pack.contains("Hallo"))
        assertTrue(pack.contains("hallo"))
        assertTrue(pack.contains("beispiel"))
        assertFalse(pack.contains("a"))
        assertFalse(pack.contains("1234"))

        val haMatches = pack.prefixMatches("ha", limit = 5)
        assertEquals(listOf("hallo", "haus", "hase"), haMatches)

        val hSingleCharMatches = pack.prefixMatches("h", limit = 5)
        assertEquals(listOf("hallo", "haus", "hase"), hSingleCharMatches)

        assertEquals(listOf("hallo", "haus", "hase", "beispiel", "welt"), pack.topSwipePool)
    }

    @Test
    fun integrationWithSuggestionEngineProvidesOfflinePackCompletions() {
        val sample = sequenceOf(
            "Programmierung 9000",
            "Projekt 8500",
            "Protokoll 4000"
        )
        val pack = OfflineDictionaryPackManager.parseFrequencyWordList("de", sample)
        val suggestions = SuggestionEngine.suggestionsFor(
            textBeforeCursor = "Progr",
            locale = "de",
            offlinePack = pack
        )
        assertTrue(suggestions.any { it.text == "Programmierung" })

        val midSentence = SuggestionEngine.suggestionsFor(
            textBeforeCursor = "ich nutze progr",
            locale = "de",
            offlinePack = pack
        )
        assertTrue(midSentence.any { it.text == "programmierung" })
        assertEquals(9, OfflineDictionaryPackManager.availablePacks.size)
    }
}
