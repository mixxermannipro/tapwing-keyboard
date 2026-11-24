package com.tapwing.keyboard

import com.tapwing.keyboard.voice.AudioSignalProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.sin
import kotlin.system.measureNanoTime

class AudioSignalProcessorTest {

    @Test
    fun parsesWavWithExtraMetadataChunksAndBoostsQuietAudio() {
        val dir = Files.createTempDirectory("audio-proc-test").toFile()
        try {
            val wav = File(dir, "quiet.wav")
            val sampleRate = 16_000
            val count = 16_000
            val pcm = ShortArray(count) { idx ->
                // Quiet tone peaking at ~0.04 (1300 / 32768)
                (sin(idx * 0.08) * 1300.0).toInt().toShort()
            }
            writeWavWithListChunk(wav, sampleRate, pcm)

            val parsed = AudioSignalProcessor.readWavToNormalizedFloats(wav)
            assertEquals(sampleRate, parsed.sampleRate)
            assertEquals(count, parsed.samples.size)

            val peak = parsed.samples.maxOf { abs(it) }
            // Quiet input (~0.04) should be boosted near target peak 0.75 while capped by MAX_GAIN 6.0x (~0.24)
            assertTrue("Expected boosted peak > 0.18, got $peak", peak > 0.18f)
            assertTrue("Expected strictly unclipped peak <= 0.98, got $peak", peak <= 0.98f)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun silenceGateDoesNotAmplifyNoiseFloorAndNormalLoudAudioIsNotScaledUp() {
        val silence = FloatArray(1000) { if (it % 2 == 0) 0.0008f else -0.0008f }
        AudioSignalProcessor.normalizeInPlace(silence)
        assertTrue(silence.maxOf { abs(it) } <= 0.001f)

        val loud = FloatArray(1000) { if (it % 2 == 0) 0.85f else -0.85f }
        AudioSignalProcessor.normalizeInPlace(loud)
        assertEquals(0.85f, loud[0], 0.0001f)
    }

    @Test
    fun streamingPcmConversionAndAdaptiveGainStayWithinSafeBounds() {
        val samples = 1600
        val bytes = ByteArray(samples * 2)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        repeat(samples) { i ->
            buf.putShort(((if (i % 2 == 0) 1 else -1) * 1800).toShort())
        }
        var gain = 1.0f
        repeat(8) {
            val (floats, peak) = AudioSignalProcessor.pcm16BytesToNormalizedFloats(bytes, bytes.size, gain)
            assertEquals(samples, floats.size)
            assertTrue(floats.maxOf { abs(it) } <= 0.98f)
            gain = AudioSignalProcessor.nextStreamingGain(peak, gain)
            assertTrue(gain in 1.0f..3.5f)
        }
        assertTrue("Quiet speech stream should raise gain above 1.5, got $gain", gain > 1.5f)
    }

    @Test
    fun thirtySecondAudioNormalizationLatencyBenchmark() {
        val sampleCount = 16_000 * 30
        val dir = Files.createTempDirectory("audio-bench-test").toFile()
        try {
            val wav = File(dir, "30s.wav")
            val pcm = ShortArray(sampleCount) { idx ->
                (sin(idx * 0.05) * 3200.0).toInt().toShort()
            }
            writeWavWithListChunk(wav, 16_000, pcm)

            var parsedSize = 0
            val elapsedNs = measureNanoTime {
                val parsed = AudioSignalProcessor.readWavToNormalizedFloats(wav)
                parsedSize = parsed.samples.size
            }
            val elapsedMs = elapsedNs / 1_000_000.0
            println("AudioSignalProcessor 30s (480k samples) WAV read + normalize: ${"%.2f".format(elapsedMs)} ms")
            assertEquals(sampleCount, parsedSize)
            assertTrue("30s audio prep exceeded 400ms ceiling: $elapsedMs ms", elapsedMs < 400.0)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun writeWavWithListChunk(target: File, sampleRate: Int, pcm: ShortArray) {
        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun writeIntLE(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun writeShortLE(v: Short) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array())

        val listPayload = "INFOINAM\u0004\u0000\u0000\u0000tapw".toByteArray(Charsets.US_ASCII)
        val dataBytesLen = pcm.size * 2
        val riffChunkSize = 4 + 24 + (8 + listPayload.size) + (8 + dataBytesLen)

        writeAscii("RIFF")
        writeIntLE(riffChunkSize)
        writeAscii("WAVE")

        // fmt chunk
        writeAscii("fmt ")
        writeIntLE(16)
        writeShortLE(1) // PCM
        writeShortLE(1) // mono
        writeIntLE(sampleRate)
        writeIntLE(sampleRate * 2)
        writeShortLE(2)
        writeShortLE(16)

        // Extra metadata chunk before data
        writeAscii("LIST")
        writeIntLE(listPayload.size)
        out.write(listPayload)

        // data chunk
        writeAscii("data")
        writeIntLE(dataBytesLen)
        val pcmBuf = ByteBuffer.allocate(dataBytesLen).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) pcmBuf.putShort(s)
        out.write(pcmBuf.array())

        target.writeBytes(out.toByteArray())
    }
}
