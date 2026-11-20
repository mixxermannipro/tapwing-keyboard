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
    fun validatesGgufAndGgmlModelMagicHeaders() {
        val gguf = File.createTempFile("model_", ".gguf")
        val html = File.createTempFile("bad_", ".gguf")
        val ggml = File.createTempFile("whisper_", ".bin")
        try {
            gguf.writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), 3, 0, 0, 0))
            html.writeBytes("<html".toByteArray(Charsets.US_ASCII))
            ggml.writeBytes(byteArrayOf('l'.code.toByte(), 'm'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 1, 0, 0, 0))

            assertTrue(LocalModelManager.hasValidModelHeader(gguf, LocalModelKind.CLEANUP_LLM))
            assertFalse(LocalModelManager.hasValidModelHeader(html, LocalModelKind.CLEANUP_LLM))
            assertTrue(LocalModelManager.hasValidModelHeader(ggml, LocalModelKind.WHISPER_STT))
            assertFalse(LocalModelManager.hasValidModelHeader(html, LocalModelKind.WHISPER_STT))
        } finally {
            gguf.delete()
            html.delete()
            ggml.delete()
        }
    }
}
