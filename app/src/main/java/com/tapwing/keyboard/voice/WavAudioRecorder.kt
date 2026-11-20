package com.tapwing.keyboard.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class WavRecordingResult(
    val file: File,
    val durationMs: Long,
    val peakSampleAbs: Int
)

/**
 * High-quality 16 kHz Mono 16-bit PCM WAV audio recorder using Android's VOICE_RECOGNITION
 * source and optional NoiseSuppressor/AGC when available.
 * Reads responsive ~80 ms chunks so VU metering is fluid and stopping returns almost immediately.
 */
class WavAudioRecorder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordJob: Job? = null
    @Volatile
    private var recordingFlag = false
    private var currentFile: File? = null
    @Volatile
    private var lastDurationMs: Long = 0L
    @Volatile
    private var lastPeakAbs: Int = 0

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
        lastDurationMs = 0L
        lastPeakAbs = 0

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            onError("AudioRecord unsupported on this device")
            return false
        }
        val ringBufferBytes = maxOf(minBuf * 4, SAMPLE_RATE * 2) // >= 1s kernel ring buffer

        val created = createBestAudioRecord(ringBufferBytes)
        if (created == null) {
            onError("Could not initialize microphone")
            return false
        }
        val (recorder, sourceName) = created

        // Do NOT attach AcousticEchoCanceler for dictation: without far-end VoIP playback,
        // AEC on some vendor HALs attenuates normal voice input.
        val sessionId = recorder.audioSessionId
        val ns = runCatching {
            if (sourceName != "VOICE_RECOGNITION" && NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()
        val agc = runCatching {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()

        currentFile = outputFile
        recordingFlag = true
        Log.i(TAG, "AudioRecord started source=$sourceName minBuf=$minBuf ring=$ringBufferBytes")

        recordJob = scope.launch {
            var totalBytes = 0L
            val startMs = System.currentTimeMillis()
            var lastSecReported = -1
            var maxPeak = 0
            val raf = RandomAccessFile(outputFile, "rw")
            try {
                raf.write(ByteArray(44))
                recorder.startRecording()

                val pcm = ShortArray(READ_CHUNK_SAMPLES)
                val byteBuf = ByteArray(pcm.size * 2)

                while (isActive && recordingFlag) {
                    val read = recorder.read(pcm, 0, pcm.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = pcm[i]
                            val a = abs(s.toInt())
                            if (a > maxPeak) maxPeak = a
                            sumSq += (s.toDouble() * s.toDouble())
                            byteBuf[i * 2] = (s.toInt() and 0xFF).toByte()
                            byteBuf[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                        raf.write(byteBuf, 0, read * 2)
                        totalBytes += read * 2
                        lastPeakAbs = maxPeak

                        val rms = sqrt(sumSq / read).coerceAtLeast(1.0)
                        val db = (20.0 * log10(rms / 32768.0)).toFloat()
                        val uiDb = ((db + 55f) / 4.2f).coerceIn(0f, 12f)
                        onRmsDb(uiDb)

                        val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).toInt()
                        if (elapsedSec != lastSecReported) {
                            lastSecReported = elapsedSec
                            onElapsedSeconds(elapsedSec)
                        }
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord.read returned error code $read")
                        break
                    }
                }

                lastDurationMs = (totalBytes * 1000L) / (SAMPLE_RATE * 2L)
                lastPeakAbs = maxPeak
                writeWavHeader(raf, totalBytes)
                Log.i(TAG, "AudioRecord stopped: bytes=$totalBytes durationMs=$lastDurationMs peakAbs=$maxPeak")
            } catch (ex: Exception) {
                Log.e(TAG, "Recording loop failed", ex)
                onError(ex.localizedMessage ?: "Recording failed")
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                runCatching { ns?.release() }
                runCatching { agc?.release() }
                runCatching { raf.close() }
                recordingFlag = false
            }
        }
        return true
    }

    suspend fun stopAndGetResult(): WavRecordingResult? {
        recordingFlag = false
        recordJob?.join()
        recordJob = null
        val file = currentFile
        currentFile = null
        return if (file != null && file.isFile && file.length() > 44L) {
            WavRecordingResult(file = file, durationMs = lastDurationMs, peakSampleAbs = lastPeakAbs)
        } else {
            null
        }
    }

    fun cancelAndDelete() {
        recordingFlag = false
        recordJob?.cancel()
        recordJob = null
        currentFile?.delete()
        currentFile = null
    }

    @SuppressLint("MissingPermission")
    private fun createBestAudioRecord(bufferSize: Int): Pair<AudioRecord, String>? {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.MIC to "MIC"
        )
        for ((src, name) in sources) {
            val rec = runCatching {
                AudioRecord(src, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
            }.getOrNull()
            if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
                return rec to name
            }
            runCatching { rec?.release() }
        }
        return null
    }

    private fun writeWavHeader(raf: RandomAccessFile, pcmBytes: Long) {
        val totalDataLen = pcmBytes + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalDataLen.toInt())
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1)
        writeShortLE(header, 22, 1)
        writeIntLE(header, 24, SAMPLE_RATE)
        writeIntLE(header, 28, byteRate)
        writeShortLE(header, 32, 2)
        writeShortLE(header, 34, 16)
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
        private const val TAG = "TapwingVoice"
        const val SAMPLE_RATE = 16_000
        private const val READ_CHUNK_SAMPLES = SAMPLE_RATE / 12 // ~83ms per read
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
