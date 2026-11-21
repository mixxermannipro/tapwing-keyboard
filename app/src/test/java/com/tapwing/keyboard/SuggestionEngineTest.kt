package com.tapwing.keyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun supportsMultilingualTypingAndOtpExtraction() {
        // Bilingual typing: typing English "teh" while active layout is German still corrects cleanly
        val corr = SuggestionEngine.autoCorrectionFor("teh", "de", secondaryLocales = listOf("en"))
        assertEquals("the", corr)

        // Valid English word on German keyboard is not mangled
        assertNull(SuggestionEngine.autoCorrectionFor("tomorrow", "de", secondaryLocales = listOf("en")))

        // 2FA OTP code extraction
        assertEquals("482910", SuggestionEngine.extractOtpFromClipboard("Dein Bestätigungscode lautet 482910."))
        assertNull(SuggestionEngine.extractOtpFromClipboard("Heute ist der 2026 Tag."))
    }

    @Test
    fun supportsEmailDomainChips() {
        val chips = SuggestionEngine.suggestionsFor("max@gm", "de", isEmailField = true)
        assertTrue(chips.any { it.text == "gmail.com" })
    }

    @Test
    fun recognisesCopiedCodesWithoutPickingUnrelatedNumbers() {
        assertEquals("482910", SuggestionEngine.extractOtpFromClipboard("Bestätigungscode: 482-910"))
        assertEquals("763241", SuggestionEngine.extractOtpFromClipboard("Order 2026. Your verification code is 763241."))
        assertEquals("987654", SuggestionEngine.extractOtpFromClipboard("987 654 is your sign-in code"))
        assertNull(SuggestionEngine.extractOtpFromClipboard("Source code for issue 123456"))
        assertNull(SuggestionEngine.extractOtpFromClipboard("Your verification code is https://example.com/123456"))
    }

    @Test
    fun continuesWithContextAfterRepeatedSuggestionTaps() {
        val afterIch = SuggestionEngine.suggestionsFor("Ich ", "de")
        assertEquals("bin", afterIch.first().text)
        assertTrue(afterIch.all { it.isNextWord && it.replaceCharCount == 0 })
        val afterBin = SuggestionEngine.suggestionsFor("Ich bin ", "de")
        assertEquals("gleich", afterBin.first().text)
        assertTrue(SuggestionEngine.suggestionsFor("Ich bin gleich ", "de").any { it.text == "da" })
    }

    @Test
    fun keepsNextWordsOutOfAddressesAndPartialWordCompletions() {
        assertTrue(SuggestionEngine.suggestionsFor("mail ", "en", isEmailField = true).none { it.isNextWord })
        assertTrue(SuggestionEngine.suggestionsFor("I am ", "en", isUrlField = true).none { it.isNextWord })
        assertTrue(SuggestionEngine.suggestionsFor("Ich bi", "de").none { it.isNextWord })
        assertTrue(SuggestionEngine.suggestionsFor("Ich bin. ", "de").none { it.text == "gleich" })
    }

    @Test
    fun keyboardProximityRanksAdjacentKeyTyposHigherThanDistantTypos() {
        val adjDist = SuggestionEngine.weightedKeyboardDistance("hsllo", "hallo", "de")
        val farDist = SuggestionEngine.weightedKeyboardDistance("hpllo", "hallo", "de")
        assertTrue("Expected adjacent s<->a distance ($adjDist) < distant p<->a ($farDist)", adjDist < farDist)

        val chips = SuggestionEngine.suggestionsFor("hsllo", "de", includeEmoji = false)
        assertTrue(chips.any { it.text.equals("hallo", ignoreCase = true) })
    }

    @Test
    fun matchesGermanUmlautWordsFromAsciiTyping() {
        val uberChips = SuggestionEngine.suggestionsFor("uber", "de", includeEmoji = false)
        assertTrue(uberChips.any { it.text == "über" })

        val fuerChips = SuggestionEngine.suggestionsFor("fuer", "de", includeEmoji = false)
        assertTrue(fuerChips.any { it.text == "für" })
    }
}
