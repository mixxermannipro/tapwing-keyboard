package com.tapwing.keyboard

import com.tapwing.keyboard.ai.CleanupPromptBuilder
import com.tapwing.keyboard.ai.LocalLlmEngine
import com.tapwing.keyboard.ai.LocalWritingAssistant
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.voice.AudioSignalProcessor
import com.tapwing.keyboard.voice.GeminiLiveConfig
import com.tapwing.keyboard.voice.NaturalVoiceCleaner
import com.tapwing.keyboard.voice.VoiceCommandResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.sin

class SuperchargedSttAndLocalLlmTest {

    @Test
    fun automaticBulletPointDetectionHandlesGermanAndEnglishLists() {
        val deWithColon = NaturalVoiceCleaner.clean(
            "Einkaufsliste: Milch, Eier, Brot und Butter",
            locale = "de-DE",
            autoBulletLists = true
        )
        assertEquals("Einkaufsliste:\n• Milch\n• Eier\n• Brot\n• Butter", deWithColon)

        val deWithoutColon = NaturalVoiceCleaner.clean(
            "Einkaufsliste Milch, Eier, Brot und Butter",
            locale = "de-DE",
            autoBulletLists = true
        )
        assertEquals("Einkaufsliste:\n• Milch\n• Eier\n• Brot\n• Butter", deWithoutColon)

        val enList = NaturalVoiceCleaner.clean(
            "Shopping list: milk, eggs, coffee and oat milk",
            locale = "en-US",
            autoBulletLists = true
        )
        assertEquals("Shopping list:\n• Milk\n• Eggs\n• Coffee\n• Oat milk", enList)

        val numberedPoints = NaturalVoiceCleaner.clean(
            "Für das Meeting Punkt 1 Budget Punkt 2 Zeitplan Punkt 3 Release",
            locale = "de-DE",
            autoBulletLists = true
        )
        assertEquals("Für das Meeting:\n• Budget\n• Zeitplan\n• Release", numberedPoints)

        val inlineTrigger = NaturalVoiceCleaner.clean(
            "Milch, Eier, Brot und Butter als Stichpunkte",
            locale = "de-DE",
            autoBulletLists = true
        )
        assertEquals("• Milch\n• Eier\n• Brot\n• Butter", inlineTrigger)
    }

    @Test
    fun automaticBulletDetectionRespectsToggleAndDoesNotCorruptNormalProse() {
        val disabled = NaturalVoiceCleaner.clean(
            "Einkaufsliste: Milch, Eier, Brot und Butter",
            locale = "de-DE",
            autoBulletLists = false
        )
        assertFalse(disabled.contains("• "))

        val prose = NaturalVoiceCleaner.clean(
            "Ich komme morgen vorbei, weil wir noch das ganze Projekt gemeinsam besprechen und danach essen gehen",
            locale = "de-DE",
            autoBulletLists = true
        )
        assertFalse("Normal prose must not become a bullet list: $prose", prose.contains("• "))
    }

    @Test
    fun multilingualCodeSwitchingPreservesGermanUmAndErWhileCleaningMixedUtterances() {
        val mixed = NaturalVoiceCleaner.clean(
            "ähm er kommt um 15 Uhr zum uh Design Review für das erm Frontend Deployment",
            locale = "de-DE",
            multilingual = true
        )
        assertEquals("Er kommt um 15 Uhr zum Design Review für das Frontend Deployment.", mixed)

        val bilingualCorrection = NaturalVoiceCleaner.clean(
            "wir starten das Release am Monday no wait am Tuesday",
            locale = "de-DE",
            multilingual = true
        )
        assertEquals("Wir starten das Release am Tuesday.", bilingualCorrection)

        val smartTimeAndEmail = NaturalVoiceCleaner.clean(
            "Meeting um 14 uhr 30 und schreib an max at firma punkt de",
            locale = "de-DE",
            multilingual = true
        )
        assertEquals("Meeting um 14:30 Uhr und schreib an max@firma.de.", smartTimeAndEmail)
    }

    @Test
    fun detectsExtendedVoiceEditingAndBulletListCommands() {
        assertEquals(
            VoiceCommandResult.RewriteAction(WritingAction.BULLET_LIST),
            NaturalVoiceCleaner.parseCommandOrClean("als Stichpunkte", "de-DE")
        )
        assertEquals(
            VoiceCommandResult.RewriteAction(WritingAction.BULLET_LIST),
            NaturalVoiceCleaner.parseCommandOrClean("turn into bullet points", "en-US")
        )
        assertEquals(
            VoiceCommandResult.SelectAll,
            NaturalVoiceCleaner.parseCommandOrClean("alles markieren", "de-DE")
        )
        assertEquals(
            VoiceCommandResult.CopyAll,
            NaturalVoiceCleaner.parseCommandOrClean("copy all", "en-US")
        )
        assertEquals(
            VoiceCommandResult.PasteClipboard,
            NaturalVoiceCleaner.parseCommandOrClean("einfügen", "de-DE")
        )
    }

    @Test
    fun dictionaryAndPersonalizedVoiceLearningFlowEndToEnd() {
        val dir = Files.createTempDirectory("tapwing-voice-dict").toFile()
        try {
            val file = File(dir, "dict.json")
            val store = AdaptiveDictionaryStore(file, synchronousPersistence = true)
            store.addCustomWord("Tapwing", weight = 50)
            store.addCustomWord("Kubernetes", weight = 40)
            store.recordVoiceCorrection("Maier", "Meyer", learningEnabled = true, incognito = false)
            repeat(3) {
                store.recordTypedWord(
                    word = "Sprintboard",
                    previousWord = "das",
                    learningEnabled = true,
                    incognito = false,
                    locale = "de-DE",
                    appPackage = "com.slack"
                )
            }

            // Reload from disk to verify persistence of voiceCorrections
            val reloaded = AdaptiveDictionaryStore(file, synchronousPersistence = true)
            assertEquals("Meyer", reloaded.preferredVoiceCorrectionFor("maier"))

            val vocab = reloaded.topVocabularyForVoice(appPackage = "com.slack", limit = 20)
            assertTrue("Expected custom word Tapwing in voice vocab", "Tapwing" in vocab)
            assertTrue("Expected voice correction target Meyer in voice vocab", "Meyer" in vocab)
            assertTrue("Expected per-app word Sprintboard in voice vocab", "Sprintboard" in vocab)

            // Fuzzy custom word matching fixes near-miss ASR spellings without breaking normal words
            assertEquals("Tapwing", reloaded.bestFuzzyCustomMatch("Tapwig", "de-DE"))
            assertEquals("Kubernetes", reloaded.bestFuzzyCustomMatch("Kubernetis", "de-DE"))
            assertNull(reloaded.bestFuzzyCustomMatch("machen", "de-DE"))
            assertNull(reloaded.bestFuzzyCustomMatch("kommen", "de-DE"))

            // End-to-end voice cleaning applies voice correction + fuzzy custom dictionary match
            val cleaned = NaturalVoiceCleaner.clean(
                "ähm ich treffe maier wegen tapwig und kubernetis",
                locale = "de-DE",
                dictionaryStore = reloaded,
                multilingual = true
            )
            assertEquals("Ich treffe Meyer wegen Tapwing und Kubernetes.", cleaned)

            reloaded.clearLearnedData()
            assertNull(reloaded.preferredVoiceCorrectionFor("maier"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun localWritingAssistantBulletListAndCleanupPromptBuilderWorkCleanly() {
        val bullets = LocalWritingAssistant.transform(
            "Einkaufsliste: Milch, Eier, Brot und Butter",
            WritingAction.BULLET_LIST,
            "de-DE"
        )
        assertEquals("Einkaufsliste:\n• Milch\n• Eier\n• Brot\n• Butter", bullets)

        val prompt = CleanupPromptBuilder.resolveSystemPrompt(
            customPrompt = "",
            useSystemLanguage = false,
            activeKeyboardLocale = "de-DE",
            customVocabulary = setOf("Tapwing", "Kubernetes", "Meyer"),
            autoBulletLists = true
        )
        assertTrue(prompt.contains("Tapwing"))
        assertTrue(prompt.contains("Kubernetes"))
        assertTrue(prompt.contains("code-switching"))
        assertTrue(prompt.contains("bullet"))

        val sanitized = CleanupPromptBuilder.sanitizeModelOutput(
            "<think>internal reasoning</think>\nSure, here is the cleaned text:\n```text\n• Milch\n• Brot\n```<|im_end|>"
        )
        assertEquals("• Milch\n• Brot", sanitized)

        val chatMl = LocalLlmEngine.buildChatMlFallback("sys", "usr")
        assertTrue(chatMl.contains("<|im_start|>system"))
        assertTrue(chatMl.contains("<|im_start|>assistant"))
        assertTrue(LocalLlmEngine.computeMaxTokens("Hallo Welt", WritingAction.VOICE_CLEANUP) in 96..320)
        assertTrue(LocalLlmEngine.buildUserMessage("Hallo", WritingAction.VOICE_CLEANUP, disableThinking = true).endsWith("/no_think"))
    }

    @Test
    fun geminiLiveConfigSupportsMultilingualLanguageCodes() {
        val cfg = GeminiLiveConfig(
            apiKey = "test-key",
            smartFormatting = true,
            languageCode = "de-DE",
            languageCodes = listOf("de-DE", "en-US")
        )
        val payload = JSONObject(cfg.setup()).getJSONObject("setup")
        val codes = payload.getJSONObject("inputAudioTranscription").getJSONArray("languageCodes")
        assertEquals(2, codes.length())
        assertEquals("de-DE", codes.getString(0))
        assertEquals("en-US", codes.getString(1))
    }

    @Test
    fun audioSignalProcessorRemovesDcOffsetWhilePreservingLength() {
        val dir = Files.createTempDirectory("dc-offset-test").toFile()
        try {
            val wav = File(dir, "dc_bias.wav")
            val sampleRate = 16_000
            val count = 8_000
            // Sine wave with heavy +6000 DC bias
            val pcm = ShortArray(count) { idx ->
                (6000.0 + sin(idx * 0.1) * 1500.0).toInt().toShort()
            }
            writeSimpleWav(wav, sampleRate, pcm)

            val parsed = AudioSignalProcessor.readWavToNormalizedFloats(wav)
            assertEquals(count, parsed.samples.size)
            val meanAfter = parsed.samples.sumOf { it.toDouble() } / parsed.samples.size
            assertTrue("Expected DC mean near 0 after removal, got $meanAfter", abs(meanAfter) < 0.01)
            assertNotNull(parsed.samples.maxOrNull())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun writeSimpleWav(target: File, sampleRate: Int, pcm: ShortArray) {
        val out = ByteArrayOutputStream()
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun intLE(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun shortLE(v: Short) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array())
        val dataBytesLen = pcm.size * 2
        ascii("RIFF"); intLE(36 + dataBytesLen); ascii("WAVE")
        ascii("fmt "); intLE(16); shortLE(1); shortLE(1); intLE(sampleRate); intLE(sampleRate * 2); shortLE(2); shortLE(16)
        ascii("data"); intLE(dataBytesLen)
        val buf = ByteBuffer.allocate(dataBytesLen).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) buf.putShort(s)
        out.write(buf.array())
        target.writeBytes(out.toByteArray())
    }
}
