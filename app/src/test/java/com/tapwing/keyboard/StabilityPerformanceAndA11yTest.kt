package com.tapwing.keyboard

import com.tapwing.keyboard.ai.CloudAiClient
import com.tapwing.keyboard.ai.LocalModelCatalog
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.ai.LocalWritingAssistant
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.settings.SettingsCatalog
import com.tapwing.keyboard.settings.SettingsRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityPerformanceAndA11yTest {

    @Test
    fun storagePreflightReservesExtractionHeadroomForSherpaArchives() {
        val sherpa = LocalModelCatalog.recommendedSherpa()
        val gguf = LocalModelCatalog.recommendedLlm()

        val sherpaRequiredMb = LocalModelManager.requiredSpaceMbFor(sherpa)
        val ggufRequiredMb = LocalModelManager.requiredSpaceMbFor(gguf)

        // Archives need ~2.2x space for download + tar.bz2 extraction; GGUF needs ~1.15x
        assertTrue(sherpaRequiredMb >= (sherpa.approxSizeMb * 2L))
        assertTrue(ggufRequiredMb >= gguf.approxSizeMb)
        assertTrue(ggufRequiredMb < gguf.approxSizeMb * 2L)

        // Reject when disk has less than required bytes; allow when generous headroom exists
        assertFalse(LocalModelManager.hasEnoughStorageForDownload(100_000_000L, sherpa))
        assertTrue(LocalModelManager.hasEnoughStorageForDownload(sherpaRequiredMb * 1_500_000L, sherpa))
        // When usableSpace reports 0 (unmounted/emulated edge case), do not hard-block download
        assertTrue(LocalModelManager.hasEnoughStorageForDownload(0L, sherpa))
    }

    @Test
    fun cloudCustomEndpointUrlValidationRejectsUnsafeAndMalformedUrls() {
        assertTrue(CloudAiClient.isValidHttpUrl("https://api.openai.com/v1"))
        assertTrue(CloudAiClient.isValidHttpUrl("http://192.168.1.50:11434/v1"))
        assertFalse(CloudAiClient.isValidHttpUrl(""))
        assertFalse(CloudAiClient.isValidHttpUrl("ftp://example.com/v1"))
        assertFalse(CloudAiClient.isValidHttpUrl("file:///etc/passwd"))
        assertFalse(CloudAiClient.isValidHttpUrl("not a url"))
        assertFalse(CloudAiClient.isValidHttpUrl("https://"))
    }

    @Test
    fun accessibilityPrefsDefaultOffAndCopyCleanly() {
        val defaults = TapwingPrefs()
        assertFalse(defaults.highContrastKeys)
        assertFalse(defaults.largeKeyLabels)

        val accessible = defaults.copy(highContrastKeys = true, largeKeyLabels = true)
        assertTrue(accessible.highContrastKeys)
        assertTrue(accessible.largeKeyLabels)
    }

    @Test
    fun settingsSearchFindsAccessibilityLlmAndMultilingualRoutes() {
        val a11yHits = SettingsCatalog.search("barrierefreiheit") { it.name }
        assertTrue(SettingsRoute.PREFERENCES in a11yHits)

        val contrastHits = SettingsCatalog.search("kontrast") { it.name }
        assertTrue(SettingsRoute.APPEARANCE in contrastHits)
        assertTrue(SettingsRoute.PREFERENCES in contrastHits)

        val llmHits = SettingsCatalog.search("lfm") { it.name }
        assertTrue(SettingsRoute.WRITING_TOOLS in llmHits)

        val sttHits = SettingsCatalog.search("nemotron") { it.name }
        assertTrue(SettingsRoute.VOICE in sttHits)
    }

    @Test
    fun precompiledLocalWritingAssistantHandlesHotLoopRapidly() {
        val sample = "ähm hallo wir brauchen für das meeting morgen: design review, sprint planning und deployment, eigentlich übermorgen"
        // Warmup JIT
        repeat(10) {
            LocalWritingAssistant.transform(sample, WritingAction.VOICE_CLEANUP, "de-DE")
        }
        val iterations = 150
        val startNs = System.nanoTime()
        repeat(iterations) {
            LocalWritingAssistant.transform(sample, WritingAction.VOICE_CLEANUP, "de-DE")
            LocalWritingAssistant.transform(sample, WritingAction.PROOFREAD, "de-DE")
            LocalWritingAssistant.transform(sample, WritingAction.PROFESSIONAL, "de-DE")
        }
        val totalMs = (System.nanoTime() - startNs) / 1_000_000.0
        val avgMsPerOp = totalMs / (iterations * 3)
        // Must stay fast (< 5ms/op including full German dictionary spellcheck)
        assertTrue("Expected < 5.0 ms/op, got $avgMsPerOp ms/op", avgMsPerOp < 5.0)
    }
}
