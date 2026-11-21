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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * source, with optional noise suppression and gain control for the fallback microphone source.
 * Reads responsive ~80 ms chunks so VU metering is fluid and stopping returns almost immediately.
 */
class WavAudioRecorder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private class Capture(val file: File) {
        @Volatile var stop = false
        lateinit var job: Job
        var result: WavRecordingResult? = null
    }

    private val microphone = Mutex()
    private var current: Capture? = null

    @SuppressLint("MissingPermission")
    fun start(
        outputFile: File,
        onRmsDb: (Float) -> Unit,
        onElapsedSeconds: (Int) -> Unit,
        onError: (String) -> Unit,
        onReady: () -> Unit = {},
        onPcm: ((ByteArray, Int) -> Unit)? = null,
        canScheduleChunk: (() -> Boolean)? = null,
        onSpeechChunk: ((File) -> Unit)? = null,
        skipFinalTrim: Boolean = false
    ): Boolean {
        if (current != null) return false
        val capture = Capture(outputFile)
        current = capture
        capture.job = scope.launch {
            try {
                microphone.withLock {
                    if (!capture.stop) {
                        capture.result = record(
                            capture, onReady, onRmsDb, onElapsedSeconds,
                            onPcm, canScheduleChunk, onSpeechChunk, skipFinalTrim
                        )
                    }
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Microphone capture failed", ex)
                onError("Microphone unavailable")
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun record(
        capture: Capture,
        onReady: () -> Unit,
        onRmsDb: (Float) -> Unit,
        onElapsedSeconds: (Int) -> Unit,
        onPcm: ((ByteArray, Int) -> Unit)?,
        canScheduleChunk: (() -> Boolean)?,
        onSpeechChunk: ((File) -> Unit)?,
        skipFinalTrim: Boolean
    ): WavRecordingResult {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        check(minBuffer > 0)
        val (recorder, sourceName) = checkNotNull(createBestAudioRecord(maxOf(minBuffer * 4, SAMPLE_RATE * 2)))
        val sessionId = recorder.audioSessionId
        val noiseSuppression = runCatching {
            if (sourceName == "MIC" && NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()
        val gainControl = runCatching {
            if (sourceName == "MIC" && AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()

        try {
            return RandomAccessFile(capture.file, "rw").use { wav ->
                wav.write(ByteArray(WAV_HEADER_BYTES))
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                onReady()
                val pcm = ShortArray(READ_CHUNK_SAMPLES)
                val bytes = ByteArray(pcm.size * 2)
                var totalBytes = 0L
                var peak = 0
                var firstActivity = -1L
                var lastActivityEnd = 0L
                var reportedSeconds = 0
                val vad = AdaptiveSpeechDetector()
                val boundaries = onSpeechChunk?.let { SpeechChunkBoundaries() }

                while (!capture.stop) {
                    val read = recorder.read(pcm, 0, pcm.size)
                    check(read >= 0) { "AudioRecord read error $read" }
                    if (read == 0) continue
                    var sumSquares = 0.0
                    var framePeak = 0
                    for (i in 0 until read) {
                        val sample = pcm[i].toInt()
                        framePeak = maxOf(framePeak, abs(sample))
                        sumSquares += sample.toDouble() * sample
                        writeShortLE(bytes, i * 2, sample)
                    }
                    peak = maxOf(peak, framePeak)
                    val byteCount = read * 2
                    val rms = sqrt(sumSquares / read).coerceAtLeast(1.0)
                    val voiced = vad.observe(rms)
                    val hasSignal = vad.hasSignal(framePeak)
                    if (voiced) {
                        if (firstActivity < 0) firstActivity = totalBytes
                        lastActivityEnd = totalBytes + byteCount
                    }
                    onPcm?.invoke(bytes, byteCount)
                    wav.write(bytes, 0, byteCount)
                    totalBytes += byteCount
                    boundaries?.observe(
                        totalBytes, voiced = voiced, decoderReady = canScheduleChunk?.invoke() == true,
                        hasSignal = hasSignal
                    )?.let { range -> onSpeechChunk?.invoke(copySpeechChunk(wav, capture.file, range)) }

                    val db = (20.0 * log10(rms / 32768.0)).toFloat()
                    onRmsDb(((db + 55f) / 4.2f).coerceIn(0f, 12f))
                    val seconds = (totalBytes / PCM_BYTES_PER_SECOND).toInt()
                    if (seconds != reportedSeconds) {
                        reportedSeconds = seconds
                        onElapsedSeconds(seconds)
                    }
                }
                boundaries?.tail(totalBytes)?.let { range ->
                    onSpeechChunk?.invoke(copySpeechChunk(wav, capture.file, range))
                }
                val usedBytes = if (!skipFinalTrim && firstActivity >= 0 && totalBytes <= MAX_TRIM_BYTES) {
                    trimQuietEdges(wav, totalBytes, firstActivity, lastActivityEnd)
                } else totalBytes
                WavHeaderUtil.writeWavHeader(wav, usedBytes)
                WavRecordingResult(capture.file, usedBytes * 1_000 / PCM_BYTES_PER_SECOND, peak)
            }
        } finally {
            runCatching { recorder.stop() }
            runCatching { gainControl?.release() }
            runCatching { noiseSuppression?.release() }
            recorder.release()
        }
    }

    private fun copySpeechChunk(source: RandomAccessFile, file: File, range: LongRange): File {
        val part = File.createTempFile("tapwing_voice_chunk_", ".wav", file.parentFile)
        val resumeAt = source.filePointer
        try {
            source.seek(WAV_HEADER_BYTES + range.first)
            RandomAccessFile(part, "rw").use { target ->
                var remaining = range.last - range.first + 1
                WavHeaderUtil.writeWavHeader(target, remaining)
                val buffer = ByteArray(16_384)
                while (remaining > 0) {
                    val count = minOf(remaining, buffer.size.toLong()).toInt()
                    source.readFully(buffer, 0, count)
                    target.write(buffer, 0, count)
                    remaining -= count
                }
            }
            return part
        } catch (error: Exception) {
            part.delete()
            throw error
        } finally {
            source.seek(resumeAt)
        }
    }

    private fun trimQuietEdges(wav: RandomAccessFile, total: Long, first: Long, last: Long): Long {
        val start = (first - EDGE_PADDING_BYTES).coerceAtLeast(0)
        val end = (last + EDGE_PADDING_BYTES).coerceAtMost(total)
        val length = end - start
        if (start > 0) {
            val buffer = ByteArray(16_384)
            var copied = 0L
            while (copied < length) {
                val count = (length - copied).coerceAtMost(buffer.size.toLong()).toInt()
                wav.seek(WAV_HEADER_BYTES + start + copied)
                wav.readFully(buffer, 0, count)
                wav.seek(WAV_HEADER_BYTES + copied)
                wav.write(buffer, 0, count)
                copied += count
            }
        }
        wav.setLength(WAV_HEADER_BYTES + length)
        return length
    }

    suspend fun stopAndGetResult(): WavRecordingResult? {
        val capture = current ?: return null
        capture.stop = true
        capture.job.join()
        if (current !== capture) return null
        current = null
        return capture.result.also { if (it == null) capture.file.delete() }
    }

    fun cancelAndDelete() {
        val capture = current ?: return
        current = null
        capture.stop = true
        // Wait for the reader to close before unlinking its file.
        scope.launch {
            capture.job.join()
            capture.file.delete()
        }
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

    private fun writeShortLE(buf: ByteArray, offset: Int, v: Int) {
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }

    companion object {
        private const val TAG = "TapwingVoice"
        const val SAMPLE_RATE = 16_000
        private const val PCM_BYTES_PER_SECOND = SAMPLE_RATE * 2L
        private const val EDGE_PADDING_BYTES = PCM_BYTES_PER_SECOND * 3 / 10
        private const val MAX_TRIM_BYTES = PCM_BYTES_PER_SECOND * 45L
        private const val WAV_HEADER_BYTES = 44
        private const val READ_CHUNK_SAMPLES = SAMPLE_RATE / 12 // ~83ms per read
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}

internal object WavHeaderUtil {
    const val HEADER_BYTES = 44
    const val SAMPLE_RATE = 16_000
    const val PCM_BYTES_PER_SECOND = SAMPLE_RATE * 2L

    fun buildHeader(pcmBytes: Long): ByteArray {
        val totalDataLen = pcmBytes + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8
        val header = ByteArray(HEADER_BYTES)
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
        return header
    }

    fun writeWavHeader(raf: RandomAccessFile, pcmBytes: Long) {
        raf.seek(0)
        raf.write(buildHeader(pcmBytes))
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
}

/**
 * Splits long 16 kHz mono WAV recordings into clean standalone WAV chunk files
 * so cloud APIs and batch decoders process unlimited-length speech reliably.
 */
internal object WavChunker {
    fun splitIfNeeded(wavFile: File, maxChunkSeconds: Int = 45): List<File> {
        val maxPcmBytes = maxChunkSeconds.coerceAtLeast(1) * WavHeaderUtil.PCM_BYTES_PER_SECOND
        val fileLen = wavFile.length()
        if (fileLen <= WavHeaderUtil.HEADER_BYTES) return listOf(wavFile)
        val totalPcmBytes = fileLen - WavHeaderUtil.HEADER_BYTES
        if (totalPcmBytes <= maxPcmBytes) return listOf(wavFile)

        val chunks = mutableListOf<File>()
        try {
            RandomAccessFile(wavFile, "r").use { src ->
                src.seek(WavHeaderUtil.HEADER_BYTES.toLong())
                var remaining = totalPcmBytes
                val buffer = ByteArray(16_384)
                while (remaining > 0) {
                    val partSize = minOf(remaining, maxPcmBytes)
                    val part = File.createTempFile("tapwing_cloud_chunk_", ".wav", wavFile.parentFile)
                    chunks += part
                    RandomAccessFile(part, "rw").use { dst ->
                        dst.write(WavHeaderUtil.buildHeader(partSize))
                        var leftInPart = partSize
                        while (leftInPart > 0) {
                            val toRead = minOf(leftInPart, buffer.size.toLong()).toInt()
                            src.readFully(buffer, 0, toRead)
                            dst.write(buffer, 0, toRead)
                            leftInPart -= toRead
                        }
                    }
                    remaining -= partSize
                }
            }
            return chunks
        } catch (ex: Exception) {
            chunks.forEach { it.delete() }
            throw ex
        }
    }
}
