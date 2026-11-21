package com.tapwing.keyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun forcesMaxChunkSplitOnContinuous24SecondSpeechWithoutPause() {
        val chunks = SpeechChunkBoundaries()
        val step = 2_656L // ~83ms PCM
        var offset = step
        var forcedSplit: LongRange? = null
        while (offset <= SpeechChunkBoundaries.PCM_BYTES_PER_SECOND * 26L) {
            val cut = chunks.observe(offset, voiced = true, decoderReady = true)
            if (cut != null) {
                forcedSplit = cut
                break
            }
            offset += step
        }
        assertNotNull(forcedSplit)
        assertTrue(forcedSplit!!.last - forcedSplit.first >= SpeechChunkBoundaries.HARD_MAX_CHUNK_BYTES)
    }

    @Test
    fun cutsOnMicroPauseAfter16SecondsOfSpeech() {
        val chunks = SpeechChunkBoundaries()
        chunks.observe(3_200L, voiced = true, decoderReady = true)
        chunks.observe(SpeechChunkBoundaries.SOFT_MAX_CHUNK_BYTES + 10_000L, voiced = true, decoderReady = true)
        // 220ms micro-pause (< normal 450ms pause, >= 200ms micro-pause)
        val cut = chunks.observe(
            SpeechChunkBoundaries.SOFT_MAX_CHUNK_BYTES + 10_000L + 7_040L,
            voiced = false,
            decoderReady = true
        )
        assertNotNull(cut)
    }

    @Test
    fun adaptiveSpeechDetectorAdaptsToRoomNoiseWithoutTriggeringOnSilence() {
        val vad = AdaptiveSpeechDetector(initialFloor = 24.0)
        repeat(20) {
            assertFalse(vad.observe(18.0))
        }
        assertTrue(vad.noiseFloor < 22.0)
        assertTrue(vad.observe(120.0))

        // In a noisier room (~100 RMS), floor rises gradually while real speech (~400 RMS) still triggers
        val noisyVad = AdaptiveSpeechDetector(initialFloor = 80.0)
        repeat(15) { noisyVad.observe(110.0) }
        assertFalse(noisyVad.observe(120.0))
        assertTrue(noisyVad.observe(450.0))
    }
}
