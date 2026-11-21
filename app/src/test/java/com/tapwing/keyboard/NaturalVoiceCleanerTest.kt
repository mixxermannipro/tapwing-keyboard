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

    @Test
    fun cleansFrenchAndSpanishDictation() {
        val fr = NaturalVoiceCleaner.clean("euh bonjour virgule comment ça va point d'interrogation", locale = "fr")
        assertEquals("Bonjour, comment ça va?", fr)

        val es = NaturalVoiceCleaner.clean("eh hola coma qué tal", locale = "es")
        assertEquals("Hola, qué tal.", es)
    }

    @Test
    fun preservesNumbersLinksAndMeaningfulWords() {
        assertEquals(
            "Er hält den Preis bei 3,50 € und die URL lautet example.de.",
            NaturalVoiceCleaner.clean("äh er hält den Preis bei 3,50 € und die URL lautet example.de", "de")
        )
        assertEquals("Ich wollte halt kurz fragen.", NaturalVoiceCleaner.clean("ich wollte halt kurz fragen", "de"))
        assertEquals("I am ready.", NaturalVoiceCleaner.clean("i i am ready", "en"))
    }

    @Test
    fun replacesClockTimeWithoutLosingTheRestOfTheMessage() {
        assertEquals(
            "Schick mir morgen um 15 Uhr bitte den Entwurf.",
            NaturalVoiceCleaner.clean("schick mir morgen um 14 Uhr, nein, warte, um 15 Uhr bitte den Entwurf", "de")
        )
        assertEquals(
            "Wir treffen uns morgen im Büro. Ich meine es ernst.",
            NaturalVoiceCleaner.clean("Wir treffen uns morgen im Büro. Ich meine es ernst.", "de")
        )
    }

    @Test
    fun handlesRamblerSelfCorrectionsNumbersBulletsAndStutters() {
        assertEquals(
            "Bitte überweise 65 € bis Freitag.",
            NaturalVoiceCleaner.clean("bitte überweise 50 € nein warte 65 € bis Freitag", "de")
        )
        assertEquals(
            "Let's meet at 2 PM today.",
            NaturalVoiceCleaner.clean("um let's meet at 3 PM actually make that 2 PM today", "en")
        )
        assertEquals(
            "Das ist sehr sehr gut.",
            NaturalVoiceCleaner.clean("das das ist sehr sehr gut", "de")
        )
        assertEquals(
            "Einkaufsliste:\n• Milch\n• Brot",
            NaturalVoiceCleaner.clean("einkaufsliste doppelpunkt stichpunkt milch stichpunkt brot", "de")
        )
    }

    @Test
    fun detectsExtendedRamblerVoiceEditingCommands() {
        assertEquals(
            VoiceCommandResult.DeleteLastSentence,
            NaturalVoiceCleaner.parseCommandOrClean("lösche den letzten Satz.", "de")
        )
        assertEquals(
            VoiceCommandResult.DeleteLastSentence,
            NaturalVoiceCleaner.parseCommandOrClean("scratch that", "en")
        )
        assertEquals(
            VoiceCommandResult.ClearAll,
            NaturalVoiceCleaner.parseCommandOrClean("alles löschen", "de")
        )
        assertEquals(
            VoiceCommandResult.UndoLast,
            NaturalVoiceCleaner.parseCommandOrClean("rückgängig", "de")
        )
        assertEquals(
            VoiceCommandResult.RewriteAction(WritingAction.SUMMARIZE),
            NaturalVoiceCleaner.parseCommandOrClean("fasse das zusammen", "de")
        )
        assertEquals(
            VoiceCommandResult.RewriteAction(WritingAction.REPHRASE),
            NaturalVoiceCleaner.parseCommandOrClean("rephrase that", "en")
        )
    }
}
