package com.tapwing.keyboard.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * High-quality 16 kHz Mono 16-bit PCM WAV audio recorder using Android's VOICE_RECOGNITION
 * source and hardware DSP pre-processing (NoiseSuppressor, AcousticEchoCanceler, AGC) when available.
 * Streams directly to disk so dictation can run for unlimited duration with minimal RAM.
 */
class WavAudioRecorder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordJob: Job? = null
    @Volatile
    private var recordingFlag = false
    private var currentFile: File? = null

    @SuppressLint("MissingPermission")
    fun start(
        outputFile: File,
        onRmsDb: (Float) -> Unit,
        onElapsedSeconds: (Int) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (recordingFlag) return false
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            onError("AudioRecord unsupported on this device")
            return false
        }
        val bufferSize = maxOf(minBuf * 2, SAMPLE_RATE) // ~0.5s buffer

        val recorder = createBestAudioRecord(bufferSize)
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { recorder?.release() }
            onError("Could not initialize microphone")
            return false
        }

        val sessionId = recorder.audioSessionId
        val ns = runCatching {
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId)?.apply { enabled = true } else null
        }.getOrNull()
        val aec = runCatching {
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId)?.apply { enabled = true } else null
        }.getOrNull()
        val agc = runCatching {
            if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId)?.apply { enabled = true } else null
        }.getOrNull()

        currentFile = outputFile
        recordingFlag = true

        recordJob = scope.launch {
            var totalBytes = 0L
            val startMs = System.currentTimeMillis()
            var lastSecReported = -1
            val raf = RandomAccessFile(outputFile, "rw")
            try {
                // Reserve 44 bytes for RIFF/WAVE header
                raf.write(ByteArray(44))
                recorder.startRecording()

                val pcm = ShortArray(bufferSize / 2)
                val byteBuf = ByteArray(pcm.size * 2)

                while (isActive && recordingFlag) {
                    val read = recorder.read(pcm, 0, pcm.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = pcm[i]
                            sumSq += (s.toDouble() * s.toDouble())
                            byteBuf[i * 2] = (s.toInt() and 0xFF).toByte()
                            byteBuf[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                        raf.write(byteBuf, 0, read * 2)
                        totalBytes += read * 2

                        val rms = sqrt(sumSq / read).coerceAtLeast(1.0)
                        val db = (20.0 * log10(rms / 32768.0)).toFloat()
                        // Map roughly [-55 dB .. -5 dB] -> [0f .. 12f] for UI meter
                        val uiDb = ((db + 55f) / 4.2f).coerceIn(0f, 12f)
                        onRmsDb(uiDb)

                        val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).toInt()
                        if (elapsedSec != lastSecReported) {
                            lastSecReported = elapsedSec
                            onElapsedSeconds(elapsedSec)
                        }
                    } else if (read < 0) {
                        break
                    }
                }

                writeWavHeader(raf, totalBytes)
            } catch (ex: Exception) {
                onError(ex.localizedMessage ?: "Recording failed")
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                runCatching { ns?.release() }
                runCatching { aec?.release() }
                runCatching { agc?.release() }
                runCatching { raf.close() }
                recordingFlag = false
            }
        }
        return true
    }

    suspend fun stopAndGetFile(): File? {
        recordingFlag = false
        recordJob?.join()
        recordJob = null
        val file = currentFile
        currentFile = null
        return if (file != null && file.isFile && file.length() > 44L) file else null
    }

    fun cancelAndDelete() {
        recordingFlag = false
        recordJob?.cancel()
        recordJob = null
        currentFile?.delete()
        currentFile = null
    }

    @SuppressLint("MissingPermission")
    private fun createBestAudioRecord(bufferSize: Int): AudioRecord? {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (src in sources) {
            val rec = runCatching {
                AudioRecord(src, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
            }.getOrNull()
            if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
                return rec
            }
            runCatching { rec?.release() }
        }
        return null
    }

    private fun writeWavHeader(raf: RandomAccessFile, pcmBytes: Long) {
        val totalDataLen = pcmBytes + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8
        val header = ByteArray(44)
        // "RIFF"
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalDataLen.toInt())
        // "WAVE"
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        // "fmt "
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16) // PCM chunk size
        writeShortLE(header, 20, 1) // PCM format
        writeShortLE(header, 22, 1) // Mono
        writeIntLE(header, 24, SAMPLE_RATE)
        writeIntLE(header, 28, byteRate)
        writeShortLE(header, 32, 2) // Block align
        writeShortLE(header, 34, 16) // Bits per sample
        // "data"
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, pcmBytes.toInt())
        raf.seek(0)
        raf.write(header)
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, v: Int) {
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((v shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(buf: ByteArray, offset: Int, v: Int) {
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
