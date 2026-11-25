package com.tapwing.keyboard.voice

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * Pure-Kotlin PCM/WAV preparation and bounded gain normalization.
 * Prevents quiet microphone recordings from starving local ASR frontends (e.g. sherpa-onnx #4024)
 * while strictly protecting against digital clipping.
 */
object AudioSignalProcessor {

    private const val SILENCE_PEAK_GATE = 0.002f
    private const val DC_OFFSET_GATE = 0.003f
    private const val QUIET_PEAK_THRESHOLD = 0.55f
    private const val TARGET_PEAK = 0.75f
    private const val MAX_GAIN = 6.0f
    private const val CLIP_LIMIT = 0.98f

    data class ParsedWav(
        val sampleRate: Int,
        val samples: FloatArray
    )

    fun readWavToNormalizedFloats(wavFile: File): ParsedWav {
        require(wavFile.isFile && wavFile.length() > 44L) { "Audio capture is empty" }
        var sampleRate = 16_000
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = 44L
        var dataSize = (wavFile.length() - 44L).coerceAtLeast(0L)

        RandomAccessFile(wavFile, "r").use { raf ->
            val fileLen = raf.length()
            var offset = 12L
            val hdr = ByteArray(8)
            while (offset + 8L <= fileLen) {
                raf.seek(offset)
                raf.readFully(hdr)
                val id = String(hdr, 0, 4, Charsets.US_ASCII)
                val chunkSize = readIntLE(hdr, 4).toLong() and 0xFFFFFFFFL
                when (id) {
                    "fmt " -> {
                        if (chunkSize >= 16L && offset + 24L <= fileLen) {
                            val fmt = ByteArray(16)
                            raf.readFully(fmt)
                            channels = readShortLE(fmt, 2).coerceAtLeast(1)
                            sampleRate = readIntLE(fmt, 4).coerceAtLeast(8_000)
                            bitsPerSample = readShortLE(fmt, 14).coerceAtLeast(8)
                        }
                    }
                    "data" -> {
                        dataOffset = offset + 8L
                        dataSize = minOf(chunkSize, fileLen - dataOffset)
                        break
                    }
                }
                val padded = chunkSize + (chunkSize and 1L)
                offset += 8L + padded
            }

            if (bitsPerSample != 16 || dataSize <= 0L) {
                return ParsedWav(sampleRate, FloatArray(0))
            }

            val bytesPerFrame = (channels * 2).coerceAtLeast(2)
            val totalFrames = (dataSize / bytesPerFrame).toInt().coerceAtLeast(0)
            val rawBytes = ByteArray(totalFrames * bytesPerFrame)
            raf.seek(dataOffset)
            raf.readFully(rawBytes)

            val floats = FloatArray(totalFrames)
            var sumMean = 0.0
            for (i in 0 until totalFrames) {
                var sum = 0
                val base = i * bytesPerFrame
                for (c in 0 until channels) {
                    val idx = base + c * 2
                    val s = (rawBytes[idx].toInt() and 0xFF) or (rawBytes[idx + 1].toInt() shl 8)
                    sum += s.toShort().toInt()
                }
                val v = (sum / channels) / 32768f
                floats[i] = v
                sumMean += v
            }
            val dcOffset = if (totalFrames > 0) (sumMean / totalFrames).toFloat() else 0f
            var maxAbs = 0f
            if (abs(dcOffset) > DC_OFFSET_GATE) {
                for (i in 0 until totalFrames) {
                    val centered = (floats[i] - dcOffset).coerceIn(-CLIP_LIMIT, CLIP_LIMIT)
                    floats[i] = centered
                    val a = abs(centered)
                    if (a > maxAbs) maxAbs = a
                }
            } else {
                for (i in 0 until totalFrames) {
                    val a = abs(floats[i])
                    if (a > maxAbs) maxAbs = a
                }
            }
            normalizeInPlace(floats, maxAbs)
            return ParsedWav(sampleRate, floats)
        }
    }

    fun pcm16BytesToNormalizedFloats(
        pcmBytes: ByteArray,
        byteCount: Int,
        gain: Float = 1.0f
    ): Pair<FloatArray, Float> {
        val sampleCount = (byteCount.coerceAtMost(pcmBytes.size)) / 2
        if (sampleCount <= 0) return FloatArray(0) to 0f
        val out = FloatArray(sampleCount)
        val clampedGain = gain.coerceIn(1.0f, MAX_GAIN)
        var peak = 0f
        for (i in 0 until sampleCount) {
            val lo = pcmBytes[i * 2].toInt() and 0xFF
            val hi = pcmBytes[i * 2 + 1].toInt() shl 8
            val sample = (lo or hi).toShort().toInt()
            val norm = (sample / 32768f)
            val absNorm = abs(norm)
            if (absNorm > peak) peak = absNorm
            out[i] = (norm * clampedGain).coerceIn(-CLIP_LIMIT, CLIP_LIMIT)
        }
        return out to peak
    }

    fun nextStreamingGain(framePeak: Float, currentGain: Float): Float {
        if (framePeak <= SILENCE_PEAK_GATE) return currentGain.coerceIn(1.0f, 3.5f)
        val projected = framePeak * currentGain
        return when {
            projected > 0.88f -> (0.72f / framePeak).coerceIn(1.0f, currentGain)
            projected < 0.25f -> (currentGain * 1.15f).coerceIn(1.0f, 3.5f)
            else -> currentGain.coerceIn(1.0f, 3.5f)
        }
    }

    internal fun normalizeInPlace(samples: FloatArray, knownMaxAbs: Float = -1f) {
        if (samples.isEmpty()) return
        var peak = knownMaxAbs
        if (peak < 0f) {
            peak = 0f
            for (s in samples) {
                val a = abs(s)
                if (a > peak) peak = a
            }
        }
        if (peak <= SILENCE_PEAK_GATE || peak >= QUIET_PEAK_THRESHOLD) return
        val gain = (TARGET_PEAK / peak).coerceAtMost(MAX_GAIN)
        if (gain <= 1.02f) return
        for (i in samples.indices) {
            samples[i] = (samples[i] * gain).coerceIn(-CLIP_LIMIT, CLIP_LIMIT)
        }
    }

    private fun readIntLE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShortLE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8)
    }
}
