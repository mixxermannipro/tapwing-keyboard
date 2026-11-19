package com.tapwing.keyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    @Test
    fun suggestsTypoCorrectionAndCapitalizesGermanNouns() {
        assertEquals("nicht", SuggestionEngine.autoCorrectionFor("nciht", "de"))
        assertEquals("Zeit", SuggestionEngine.autoCorrectionFor("zeit", "de"))
        assertEquals("the", SuggestionEngine.autoCorrectionFor("teh", "en"))
    }

    @Test
    fun detectsDuplicateWordsAndEmojiCandidates() {
        val grammar = SuggestionEngine.detectGrammarHint("Hallo der der", "de")
        assertNotNull(grammar)
        assertEquals("der", grammar?.replacement)

        val chips = SuggestionEngine.suggestionsFor("danke", "de", includeEmoji = true)
        assertTrue(chips.any { it.isEmoji && it.text == "🙏" })
    }

    @Test
    fun providesPrefixSuggestions() {
        val chips = SuggestionEngine.suggestionsFor("morg", "de", includeEmoji = false)
        assertTrue(chips.any { it.text.equals("morgen", ignoreCase = true) })
    }
}
