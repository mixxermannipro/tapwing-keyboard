package com.tapwing.keyboard.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume

object VoiceSelfTest {

    suspend fun synthesizeGermanWav16k(
        context: Context,
        text: String,
        dest16kWav: File
    ): Result<Pair<File, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val rawFile = File(context.cacheDir, "tapwing_tts_raw.wav")
            if (rawFile.exists()) rawFile.delete()
            if (dest16kWav.exists()) dest16kWav.delete()

            val synthOk = suspendCancellableCoroutine { cont ->
                var ttsRef: TextToSpeech? = null
                ttsRef = TextToSpeech(context.applicationContext) { status ->
                    val tts = ttsRef
                    if (status != TextToSpeech.SUCCESS || tts == null) {
                        runCatching { tts?.shutdown() }
                        if (cont.isActive) cont.resume(false)
                        return@TextToSpeech
                    }
                    tts.language = Locale.GERMANY
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            runCatching { tts.shutdown() }
                            if (cont.isActive) cont.resume(true)
                        }
                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun onError(utteranceId: String?) {
                            runCatching { tts.shutdown() }
                            if (cont.isActive) cont.resume(false)
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            runCatching { tts.shutdown() }
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    val res = tts.synthesizeToFile(text, null, rawFile, "tapwing_tts_1")
                    if (res != TextToSpeech.SUCCESS) {
                        runCatching { tts.shutdown() }
                        if (cont.isActive) cont.resume(false)
                    }
                }
            }
            if (!synthOk || !rawFile.isFile || rawFile.length() <= 44L) {
                error("Android TTS failed to synthesize German WAV")
            }
            val info = convertWavTo16kMono(rawFile, dest16kWav)
            rawFile.delete()
            dest16kWav to info
        }
    }

    private fun convertWavTo16kMono(src: File, dst: File): String {
        val bytes = src.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var channels = 1
        var sampleRate = 24000
        var bitsPerSample = 16
        var dataOffset = 44
        var dataSize = bytes.size - 44

        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(offset + 4)
            when (id) {
                "fmt " -> {
                    channels = buf.getShort(offset + 10).toInt()
                    sampleRate = buf.getInt(offset + 12)
                    bitsPerSample = buf.getShort(offset + 22).toInt()
                }
                "data" -> {
                    dataOffset = offset + 8
                    dataSize = minOf(chunkSize, bytes.size - dataOffset)
                    break
                }
            }
            offset += 8 + chunkSize + (chunkSize and 1)
        }

        val bytesPerFrame = (channels * (bitsPerSample / 8)).coerceAtLeast(2)
        val inFrames = dataSize / bytesPerFrame
        val monoIn = ShortArray(inFrames)
        for (i in 0 until inFrames) {
            var sum = 0
            for (c in 0 until channels) {
                val pos = dataOffset + i * bytesPerFrame + c * 2
                if (pos + 1 < bytes.size) {
                    sum += buf.getShort(pos).toInt()
                }
            }
            monoIn[i] = (sum / channels.coerceAtLeast(1)).toShort()
        }

        val targetRate = 16_000
        val outFrames = ((inFrames.toLong() * targetRate) / sampleRate.coerceAtLeast(8000)).toInt().coerceAtLeast(1)
        val outSamples = ShortArray(outFrames)
        for (i in 0 until outFrames) {
            val srcPos = (i.toDouble() * sampleRate) / targetRate
            val idx0 = srcPos.toInt().coerceIn(0, inFrames - 1)
            val idx1 = (idx0 + 1).coerceIn(0, inFrames - 1)
            val frac = srcPos - idx0
            outSamples[i] = (monoIn[idx0] * (1.0 - frac) + monoIn[idx1] * frac).toInt().toShort()
        }

        FileOutputStream(dst).use { out ->
            val pcmBytes = outFrames * 2
            val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            hdr.put("RIFF".toByteArray(Charsets.US_ASCII))
            hdr.putInt(pcmBytes + 36)
            hdr.put("WAVE".toByteArray(Charsets.US_ASCII))
            hdr.put("fmt ".toByteArray(Charsets.US_ASCII))
            hdr.putInt(16)
            hdr.putShort(1)
            hdr.putShort(1)
            hdr.putInt(targetRate)
            hdr.putInt(targetRate * 2)
            hdr.putShort(2)
            hdr.putShort(16)
            hdr.put("data".toByteArray(Charsets.US_ASCII))
            hdr.putInt(pcmBytes)
            out.write(hdr.array())
            val dataBuf = ByteArray(pcmBytes)
            for (i in 0 until outFrames) {
                val s = outSamples[i].toInt()
                dataBuf[i * 2] = (s and 0xFF).toByte()
                dataBuf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            out.write(dataBuf)
        }
        return "in=${sampleRate}Hz/${channels}ch/${inFrames}f -> 16k/${outFrames}f"
    }
}
