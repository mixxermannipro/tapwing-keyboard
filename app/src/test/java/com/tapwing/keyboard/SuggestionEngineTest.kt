package com.tapwing.keyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    @Test
    fun offlineNextWordsUseThePhraseAndSecondaryLanguage() {
        assertEquals("verstanden", SuggestionEngine.suggestionsFor("Ich habe ", "de").first().text)
        assertEquals("vereinbart", SuggestionEngine.suggestionsFor("Wir haben ", "de").first().text)
        assertEquals("ready", SuggestionEngine.suggestionsFor("I am ", "de", secondaryLocales = listOf("en")).first().text)
        assertTrue(SuggestionEngine.suggestionsFor("Quarzstein ", "de").isEmpty())
        assertEquals("heute" to "", SuggestionEngine.extractPreviousTwoWords("Hallo.\nHeute ".lowercase()))
    }

    @Test
    fun addressSuggestionsAreExclusiveAndDoNotAddSpaces() {
        val suggestions = SuggestionEngine.suggestionsFor("test@gmx.", "de", isEmailField = true)
        assertEquals("gmx.de", suggestions.single().text)
        assertEquals(4, suggestions.single().replaceCharCount)
        assertTrue(!suggestions.single().appendSpace)
        assertTrue(SuggestionEngine.suggestionsFor("hallo", "de", isEmailField = true).isEmpty())
    }

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
        assertTrue(SuggestionEngine.suggestionsFor("https://example.com ", "en", isUrlField = true).none { it.isNextWord })
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

    @Test
    fun doesNotMangleValidGermanWordsLikeSchonOrKonnte() {
        assertNull(SuggestionEngine.autoCorrectionFor("schon", "de"))
        assertNull(SuggestionEngine.autoCorrectionFor("konnte", "de"))
        assertEquals("schön", SuggestionEngine.autoCorrectionFor("schoen", "de"))
        assertEquals("wäre", SuggestionEngine.autoCorrectionFor("waere", "de"))
        assertEquals("hätte", SuggestionEngine.autoCorrectionFor("haette", "de"))
    }

    @Test
    fun autoCapitalizesStandaloneEnglishIAndContractions() {
        assertEquals("I", SuggestionEngine.autoCorrectionFor("i", "en"))
        assertNull(SuggestionEngine.autoCorrectionFor("i", "de"))
        assertEquals("doesn't", SuggestionEngine.autoCorrectionFor("doesnt", "en"))
        assertEquals("that's", SuggestionEngine.autoCorrectionFor("thats", "en"))
    }

    @Test
    fun highConfidenceFuzzyAutocorrectFixesClearTranspositions() {
        assertEquals("heute", SuggestionEngine.autoCorrectionFor("huete", "de"))
        assertEquals("hallo", SuggestionEngine.autoCorrectionFor("hsllo", "de"))
    }

    @Test
    fun earlyAbortDistanceMatchesFullDistanceForCloseWords() {
        val full = SuggestionEngine.weightedKeyboardDistance("hsllo", "hallo", "de")
        val pruned = SuggestionEngine.weightedKeyboardDistance("hsllo", "hallo", "de", maxDistance = 2.15)
        assertEquals(full, pruned, 0.0001)

        val far = SuggestionEngine.weightedKeyboardDistance("zzzzzz", "hallo", "de", maxDistance = 1.5)
        assertTrue("Expected early-abort far distance >= 99.0, got $far", far >= 99.0)
    }

    @Test
    fun contextAwareAutocorrectDisambiguatesShortWordsAndBilingualEnglishI() {
        // 3-letter typo disambiguated by German sentence context "Ich bkn" -> "bin"
        assertEquals(
            "bin",
            SuggestionEngine.autoCorrectionFor("bkn", "de", previousWord = "Ich")
        )
        // Bilingual English sentence on German keyboard auto-capitalizes standalone "i"
        assertEquals(
            "I",
            SuggestionEngine.autoCorrectionFor(
                word = "i",
                locale = "de",
                secondaryLocales = listOf("en"),
                previousWord = "think"
            )
        )
        // German article triggers capitalization of known German nouns in vocab
        assertEquals(
            "Weg",
            SuggestionEngine.autoCorrectionFor("weg", "de", previousWord = "der")
        )
    }

    @Test
    fun urlFieldOffersContextualTldAndSlashChipsOnlyForExplicitUrls() {
        // Normal browser search bar queries must NOT spam https://, www., .de, or .com!
        val initial = SuggestionEngine.suggestionsFor("", "de", isUrlField = true)
        assertTrue("Initial omnibox must not spam https:// or www.: $initial", initial.none { it.text in setOf("https://", "www.", ".de", ".com") })

        val normalSearchWord = SuggestionEngine.suggestionsFor("wett", "de", isUrlField = true)
        assertTrue("Normal search word must not spam .de/.com: $normalSearchWord", normalSearchWord.none { it.text in setOf(".de", ".com", "www.", "https://") })
        assertTrue("Normal search word should still get regular completions: $normalSearchWord", normalSearchWord.any { it.text.equals("Wetter", ignoreCase = true) })

        val multiWordSearch = SuggestionEngine.suggestionsFor("bestes wetter mor", "de", isUrlField = true)
        assertTrue("Multi-word browser search must not suggest TLDs: $multiWordSearch", multiWordSearch.none { it.text in setOf(".de", ".com", ".org", "www.") })
        assertTrue("Multi-word browser search should suggest normal words: $multiWordSearch", multiWordSearch.any { it.text.equals("morgen", ignoreCase = true) })

        // Explicit URL input (dot, www., or ://) DOES offer contextual TLD and '/' chips
        val hostWithDot = SuggestionEngine.suggestionsFor("tapwing.", "de", isUrlField = true)
        assertTrue("Expected 'de' TLD completion after dot in $hostWithDot", hostWithDot.any { it.text == "de" && !it.appendSpace })
        assertTrue("Expected 'com' TLD completion after dot in $hostWithDot", hostWithDot.any { it.text == "com" && !it.appendSpace })

        val partialTld = SuggestionEngine.suggestionsFor("tapwing.d", "de", isUrlField = true)
        assertEquals(".de", partialTld.single().text)
        assertEquals(2, partialTld.single().replaceCharCount)

        val fullHost = SuggestionEngine.suggestionsFor("tapwing.de", "de", isUrlField = true)
        assertEquals("/", fullHost.single().text)

        val explicitScheme = SuggestionEngine.suggestionsFor("https://tapwing", "de", isUrlField = true)
        assertTrue("Expected .de TLD for https:// host in $explicitScheme", explicitScheme.any { it.text == ".de" && !it.appendSpace })
    }
}
