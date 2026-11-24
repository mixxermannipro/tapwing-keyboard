package com.tapwing.keyboard

import com.tapwing.keyboard.voice.NaturalVoiceCleaner
import com.tapwing.keyboard.voice.VoiceCommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class NaturalVoiceCleanerRamblerTest {

    @Test
    fun removesPrefixStuttersAndCommaSeparatedWordDuplicates() {
        assertEquals(
            "Ich gehe heute einkaufen.",
            NaturalVoiceCleaner.clean("ich g- gehe heute, heute einkaufen", "de-DE")
        )
        assertEquals(
            "Ich bin sehr sehr froh.",
            NaturalVoiceCleaner.clean("ich bin sehr sehr froh", "de-DE")
        )
    }

    @Test
    fun discardsAbandonedClausesBeforeSelfCorrectionRestart() {
        assertEquals(
            "Treffen wir uns erst morgen.",
            NaturalVoiceCleaner.clean("ich fahre heute ins büro vergiss das treffen wir uns erst morgen", "de-DE")
        )
        assertEquals(
            "Let's meet on Friday instead.",
            NaturalVoiceCleaner.clean("let's book wednesday morning never mind let's meet on Friday instead", "en-US")
        )
    }

    @Test
    fun correctsGermanAndEnglishPrepositionPairsMidSentence() {
        assertEquals(
            "Tisch für 4 personen reservieren.",
            NaturalVoiceCleaner.clean("tisch für 2 personen äh nein für 4 personen reservieren", "de-DE")
        )
        assertEquals(
            "Send it to Sarah.",
            NaturalVoiceCleaner.clean("send it to Alex no wait to Sarah", "en-US")
        )
    }

    @Test
    fun formatsGermanAndEnglishSpokenOrdinalLists() {
        val de = NaturalVoiceCleaner.clean(
            "erstens termin buchen zweitens hotel prüfen drittens zug buchen",
            "de-DE"
        )
        assertEquals("1. Termin buchen\n2. Hotel prüfen\n3. Zug buchen", de)

        val en = NaturalVoiceCleaner.clean(
            "first, call the client second, send the invoice third, archive the file",
            "en-US"
        )
        assertEquals("1. Call the client\n2. Send the invoice\n3. Archive the file", en)
    }

    @Test
    fun insertsGreetingCommasSubordinateCommasTagQuestionsAndEmojis() {
        assertEquals(
            "Hallo tim,\n\nWie geht es? 👍",
            NaturalVoiceCleaner.clean("hallo tim neuer absatz wie geht es fragezeichen daumen hoch emoji", "de-DE")
        )
        assertEquals(
            "Ich bleibe daheim, weil es heute stark regnet.",
            NaturalVoiceCleaner.clean("ich bleibe daheim weil es heute stark regnet", "de-DE")
        )
        assertEquals(
            "Wir schaffen das noch, oder?",
            NaturalVoiceCleaner.clean("wir schaffen das noch komma oder", "de-DE")
        )
    }

    @Test
    fun recognizesSendVoiceCommandsInGermanAndEnglish() {
        assertEquals(
            VoiceCommandResult.SendAction,
            NaturalVoiceCleaner.parseCommandOrClean("Nachricht senden.", "de-DE")
        )
        assertEquals(
            VoiceCommandResult.SendAction,
            NaturalVoiceCleaner.parseCommandOrClean("send message", "en-US")
        )
    }

    @Test
    fun ramblerCleanupBenchmarkStaysSubMillisecondPerUtterance() {
        val sample = "ähm hallo tim neuer absatz ich g- gehe am montag ich meine am dienstag ins büro " +
            "weil wir einen termin haben erstens zahlen prüfen zweitens angebot senden daumen hoch emoji"
        // Warmup
        repeat(50) { NaturalVoiceCleaner.clean(sample, "de-DE") }

        val iterations = 1000
        val elapsedNs = measureNanoTime {
            repeat(iterations) {
                NaturalVoiceCleaner.clean(sample, "de-DE")
            }
        }
        val avgMs = (elapsedNs / 1_000_000.0) / iterations
        println("NaturalVoiceCleaner Rambler avg latency ($iterations runs): ${"%.4f".format(avgMs)} ms/op")
        assertTrue("Expected avg < 2.0 ms/op, got $avgMs", avgMs < 2.0)
    }
}
