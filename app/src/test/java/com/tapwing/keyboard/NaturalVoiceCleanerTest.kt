package com.tapwing.keyboard.voice

import com.tapwing.keyboard.ai.WritingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalVoiceCleanerTest {

    @Test
    fun removesGermanFillersAndAppliesSpokenPunctuation() {
        val raw = "ähm hallo tim komma wie geht es dir heute fragezeichen"
        val cleaned = NaturalVoiceCleaner.clean(raw, locale = "de")
        assertEquals("Hallo tim, wie geht es dir heute?", cleaned)
    }

    @Test
    fun handlesSelfCorrectionInGermanAndEnglish() {
        val de = NaturalVoiceCleaner.clean("ich komme um acht uhr nein warte um neun uhr", locale = "de")
        assertEquals("Ich komme um neun uhr.", de)

        val en = NaturalVoiceCleaner.clean("um let us meet on monday wait no tuesday period", locale = "en")
        assertEquals("Let us meet on tuesday.", en)
    }

    @Test
    fun addsSmartQuestionMarkForQuestions() {
        val de = NaturalVoiceCleaner.clean("kannst du mir das morgen schicken", locale = "de")
        assertTrue(de.endsWith("?"))

        val en = NaturalVoiceCleaner.clean("can you send me the notes tomorrow", locale = "en")
        assertTrue(en.endsWith("?"))
    }

    @Test
    fun detectsVoiceRewriteAndDeleteCommands() {
        assertEquals(
            VoiceCommandResult.RewriteAction(WritingAction.CASUAL),
            NaturalVoiceCleaner.parseCommandOrClean("mach es freundlicher", "de")
        )
        assertEquals(
            VoiceCommandResult.RewriteAction(WritingAction.PROFESSIONAL),
            NaturalVoiceCleaner.parseCommandOrClean("make it professional", "en")
        )
        assertEquals(
            VoiceCommandResult.DeleteLastWord,
            NaturalVoiceCleaner.parseCommandOrClean("lösche das letzte Wort", "de")
        )
    }
}
