package com.tapwing.keyboard.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalModelValidationTest {

    @Test
    fun stripsCommonWhisperHallucinationsAndNoiseTags() {
        assertEquals("", LocalInferenceEngine.cleanWhisperArtifacts("[BLANK_AUDIO]"))
        assertEquals("", LocalInferenceEngine.cleanWhisperArtifacts("(Stöhnt) [Musik]"))
        assertEquals("", LocalInferenceEngine.cleanWhisperArtifacts("Vielen Dank fürs Zuschauen."))
        assertEquals("", LocalInferenceEngine.cleanWhisperArtifacts("..."))
        assertEquals(
            "Hallo, wir treffen uns morgen.",
            LocalInferenceEngine.cleanWhisperArtifacts("[BLANK_AUDIO] Hallo, wir treffen uns morgen. (Musik)")
        )
    }

    @Test
    fun validatesWhisperModelMagicHeaders() {
        val html = File.createTempFile("bad_", ".bin")
        val ggml = File.createTempFile("whisper_", ".bin")
        try {
            html.writeBytes("<html".toByteArray(Charsets.US_ASCII))
            ggml.writeBytes(byteArrayOf('l'.code.toByte(), 'm'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 1, 0, 0, 0))

            assertTrue(LocalModelManager.hasValidModelHeader(ggml, LocalModelKind.WHISPER_STT))
            assertFalse(LocalModelManager.hasValidModelHeader(html, LocalModelKind.WHISPER_STT))
        } finally {
            html.delete()
            ggml.delete()
        }
    }
}
