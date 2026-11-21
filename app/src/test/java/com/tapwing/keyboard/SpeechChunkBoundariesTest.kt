package com.tapwing.keyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechChunkBoundariesTest {
    @Test
    fun waitsForAPauseAndLeavesTheNextSpeechInTheTail() {
        val chunks = SpeechChunkBoundaries()
        assertNull(chunks.observe(3_200, voiced = true, decoderReady = true))
        assertNull(chunks.observe(80_000, voiced = true, decoderReady = true))
        assertNull(chunks.observe(91_000, voiced = false, decoderReady = true))
        assertEquals(0L until 86_400L, chunks.observe(96_000, voiced = false, decoderReady = true))
        assertNull(chunks.tail(99_000))
        chunks.observe(102_000, voiced = true, decoderReady = false)
        chunks.observe(128_000, voiced = true, decoderReady = false)
        assertEquals(86_400L until 130_000L, chunks.tail(130_000))
    }

    @Test
    fun retainsQuietSpeechAfterAnEarlierChunk() {
        val chunks = SpeechChunkBoundaries()
        chunks.observe(3_200, voiced = true, decoderReady = true)
        chunks.observe(80_000, voiced = true, decoderReady = true)
        chunks.observe(96_000, voiced = false, decoderReady = true)
        chunks.observe(103_000, voiced = false, decoderReady = false, hasSignal = true)
        assertEquals(86_400L until 106_000L, chunks.tail(106_000))
    }

    @Test
    fun preservesOneContiguousRecordingWhenTheDecoderIsBusyOrThereIsNoPause() {
        val chunks = SpeechChunkBoundaries()
        chunks.observe(1_000, voiced = true, decoderReady = true)
        chunks.observe(90_000, voiced = true, decoderReady = true)
        assertNull(chunks.observe(120_000, voiced = false, decoderReady = false))
        assertNull(chunks.tail(120_000))
    }
}
