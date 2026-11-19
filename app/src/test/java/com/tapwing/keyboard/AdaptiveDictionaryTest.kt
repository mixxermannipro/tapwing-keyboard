package com.tapwing.keyboard.dictionary

import com.tapwing.keyboard.ai.LocalWritingAssistant
import com.tapwing.keyboard.ai.WritingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDictionaryTest {

    @Test
    fun importedWordsProtectFromAutocorrectAndPrioritizeSuggestions() {
        val store = AdaptiveDictionaryStore(storageFile = null)
        store.importText("standart\nTapwingFlow,150\nbrb => Bin gleich zurück")

        // Custom word 'standart' must NOT be overridden by stock German typo map ('Standard')
        assertNull(SuggestionEngine.autoCorrectionFor("standart", "de", store))

        // Prefix suggestions must prioritize imported high-weight word
        val chips = SuggestionEngine.suggestionsFor("Tapw", "de", dictionaryStore = store)
        assertEquals("TapwingFlow", chips.first().text)

        // Shortcut lookup works cleanly
        val sc = SuggestionEngine.suggestionsFor("brb", "de", dictionaryStore = store)
        assertTrue(sc.any { it.isShortcut && it.text == "Bin gleich zurück" })
    }

    @Test
    fun adaptiveLearningPredictsNextWordAndRespectsRejectedCorrections() {
        val store = AdaptiveDictionaryStore(storageFile = null)

        repeat(4) {
            store.recordTypedWord("Kaffee", previousWord = "guten", learningEnabled = true, incognito = false)
        }
        val predictions = store.predictNextWords("guten")
        assertEquals("Kaffee", predictions.first())

        val stripSuggestions = SuggestionEngine.suggestionsFor("guten ", "de", dictionaryStore = store)
        assertEquals("Kaffee", stripSuggestions.first().text)

        // Rejecting correction learns user's intended word and stops fighting them
        store.recordCorrectionRejected("nciht", learningEnabled = true, incognito = false)
        assertNull(SuggestionEngine.autoCorrectionFor("nciht", "de", store))

        // Writing Tools proofread preserves custom word casing
        store.addCustomWord("TapwingAI")
        val proof = LocalWritingAssistant.transform("ich nutze tapwingai heute", WritingAction.PROOFREAD, "de", store)
        assertTrue(proof.contains("TapwingAI"))
    }
}
