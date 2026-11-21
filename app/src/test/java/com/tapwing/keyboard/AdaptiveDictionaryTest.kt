package com.tapwing.keyboard.dictionary

import com.tapwing.keyboard.ai.LocalWritingAssistant
import com.tapwing.keyboard.ai.WritingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDictionaryTest {

    @Test
    fun importedWordsProtectFromAutocorrectAndPrioritizeSuggestions() {
        val store = AdaptiveDictionaryStore(storageFile = null)
        store.importText("standart\nTapwingFlow,150\nbrb => Bin gleich zurück")

        assertNull(SuggestionEngine.autoCorrectionFor("standart", "de", store))

        val chips = SuggestionEngine.suggestionsFor("Tapw", "de", dictionaryStore = store)
        assertEquals("TapwingFlow", chips.first().text)

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

        store.recordCorrectionRejected("nciht", learningEnabled = true, incognito = false)
        assertNull(SuggestionEngine.autoCorrectionFor("nciht", "de", store))

        store.addCustomWord("TapwingAI")
        val proof = LocalWritingAssistant.transform("ich nutze tapwingai heute", WritingAction.PROOFREAD, "de", store)
        assertTrue(proof.contains("TapwingAI"))
    }

    @Test
    fun sentenceStartCapitalizationNeverForcesMidSentenceUnd() {
        val store = AdaptiveDictionaryStore(storageFile = null)

        // User repeatedly starts sentences with "Und"
        repeat(10) {
            store.recordTypedWord(
                word = "Und",
                previousWord = null,
                learningEnabled = true,
                incognito = false,
                wasSentenceStart = true,
                locale = "de"
            )
        }

        // Mid-sentence "und" MUST NOT be autocorrected to "Und"
        assertNull(SuggestionEngine.autoCorrectionFor("und", "de", store, atSentenceStart = false))

        // Proofreading mid-sentence "ich und du" keeps lowercase "und"
        val proof = LocalWritingAssistant.transform("ich und du", WritingAction.PROOFREAD, "de", store)
        assertEquals("Ich und du.", proof)
    }

    @Test
    fun trigramAndContextPrefixRankingImproveOverTime() {
        val store = AdaptiveDictionaryStore(storageFile = null)

        repeat(3) {
            store.recordTypedWord(
                word = "Büro",
                previousWord = "ins",
                previousTwoWord = "fahre",
                learningEnabled = true,
                incognito = false,
                wasSentenceStart = false,
                locale = "de"
            )
        }

        val next = store.predictNextWords(previousWord = "ins", previousTwoWord = "fahre")
        assertEquals("Büro", next.first())

        val strip = SuggestionEngine.suggestionsFor("ich fahre ins ", "de", dictionaryStore = store)
        assertEquals("Büro", strip.first().text)

        assertNotNull(store.lookupShortcut("/woche", "de"))
        assertNotNull(store.lookupShortcut("/jahr", "en"))
    }

    @Test
    fun perAppContextLearnsWordsAndWritingTonesSeparately() {
        val store = AdaptiveDictionaryStore(storageFile = null)

        // Teach "Quartalsbericht" specifically for Gmail and "Brudi" for WhatsApp
        repeat(4) {
            store.recordTypedWord(
                word = "Quartalsbericht",
                previousWord = null,
                learningEnabled = true,
                incognito = false,
                locale = "de",
                appPackage = "com.google.android.gm"
            )
            store.recordTypedWord(
                word = "Quartalsplan",
                previousWord = null,
                learningEnabled = true,
                incognito = false,
                locale = "de",
                appPackage = "com.slack"
            )
        }

        val gmCandidates = store.rankedPrefixCandidates("Quar", appPackage = "com.google.android.gm")
        val slackCandidates = store.rankedPrefixCandidates("Quar", appPackage = "com.slack")
        assertEquals("Quartalsbericht", gmCandidates.first())
        assertEquals("Quartalsplan", slackCandidates.first())

        assertEquals(WritingAction.CASUAL, store.preferredToneForApp("com.whatsapp"))
        assertEquals(WritingAction.PROFESSIONAL, store.preferredToneForApp("com.google.android.gm"))

        store.recordAppWritingAction("com.whatsapp", WritingAction.CONCISE, learningEnabled = true, incognito = false)
        assertEquals(WritingAction.CONCISE, store.preferredToneForApp("com.whatsapp"))
    }

    @Test
    fun perAppBigramsPersonalCorrectionsAndRecentEmojisPersist() {
        val tempFile = java.io.File.createTempFile("tapwing_dict_test", ".json")
        tempFile.deleteOnExit()
        val store = AdaptiveDictionaryStore(storageFile = tempFile)

        repeat(3) {
            store.recordTypedWord(
                word = "Alter",
                previousWord = "hallo",
                learningEnabled = true,
                incognito = false,
                locale = "de",
                appPackage = "com.whatsapp"
            )
            store.recordTypedWord(
                word = "Kollegen",
                previousWord = "hallo",
                learningEnabled = true,
                incognito = false,
                locale = "de",
                appPackage = "com.google.android.gm"
            )
        }

        assertEquals("Alter", store.predictNextWords("hallo", appPackage = "com.whatsapp").first())
        assertEquals("Kollegen", store.predictNextWords("hallo", appPackage = "com.google.android.gm").first())

        store.recordChosenCorrection("proejkt", "Projekt", learningEnabled = true, incognito = false)
        assertEquals("Projekt", SuggestionEngine.autoCorrectionFor("proejkt", "de", store))

        store.recordRecentEmoji("🚀", learningEnabled = true, incognito = false)
        store.recordRecentEmoji("🔥", learningEnabled = true, incognito = false)
        store.recordTextBatch("Morgen treffen wir uns", learningEnabled = true, incognito = false, locale = "de")

        val reloaded = AdaptiveDictionaryStore(storageFile = tempFile)
        assertEquals(listOf("🔥", "🚀"), reloaded.listRecentEmojis())
        assertEquals("Projekt", reloaded.preferredCorrectionFor("proejkt"))
        assertEquals("Alter", reloaded.predictNextWords("hallo", appPackage = "com.whatsapp").first())
        assertTrue(reloaded.isKnownWord("treffen") || reloaded.allKnownVocabularyWords().any { it.equals("treffen", ignoreCase = true) })
    }
}
