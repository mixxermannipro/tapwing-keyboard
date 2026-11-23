package com.tapwing.keyboard.voice

import com.tapwing.keyboard.ai.LocalInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adaptive Voice Activity Detector that tracks ambient noise floor using an asymmetric EMA
 * so quiet environments remain sensitive while noisy rooms don't falsely lock as continuous speech.
 */
internal class AdaptiveSpeechDetector(
    initialFloor: Double = INITIAL_NOISE_FLOOR
) {
    var noiseFloor: Double = initialFloor.coerceIn(MIN_FLOOR, MAX_FLOOR)
        private set

    fun observe(rms: Double): Boolean {
        val voiced = isVoiced(rms)
        if (!voiced) {
            val alpha = if (rms < noiseFloor) ALPHA_DOWN else ALPHA_UP
            noiseFloor = (noiseFloor * (1.0 - alpha) + rms * alpha).coerceIn(MIN_FLOOR, MAX_FLOOR)
        }
        return voiced
    }

    fun isVoiced(rms: Double): Boolean = rms >= maxOf(MIN_VOICED_RMS, noiseFloor * SNR_MULTIPLIER)

    fun hasSignal(framePeak: Int): Boolean = framePeak >= MIN_SIGNAL_PEAK

    companion object {
        private const val INITIAL_NOISE_FLOOR = 24.0
        private const val MIN_FLOOR = 15.0
        private const val MAX_FLOOR = 260.0
        private const val MIN_VOICED_RMS = 42.0
        private const val SNR_MULTIPLIER = 2.35
        private const val ALPHA_DOWN = 0.20
        private const val ALPHA_UP = 0.04
        private const val MIN_SIGNAL_PEAK = 35
    }
}

/**
 * Byte ranges in a 16 kHz mono PCM stream.
 * Cuts naturally on pauses, uses micro-pauses after 16 s, and forces a split at 24 s
 * so Whisper's 30 s window never overflows on unlimited-length dictation.
 */
internal class SpeechChunkBoundaries {
    private var nextOffset = 0L
    private var firstVoicedEnd = -1L
    private var lastVoicedEnd = 0L
    private var lastSignalEnd = 0L

    fun observe(end: Long, voiced: Boolean, decoderReady: Boolean, hasSignal: Boolean = voiced): LongRange? {
        if (hasSignal) lastSignalEnd = end
        if (voiced) {
            if (firstVoicedEnd < 0) firstVoicedEnd = end
            lastVoicedEnd = end
            if (decoderReady && firstVoicedEnd >= 0 && end - firstVoicedEnd >= HARD_MAX_CHUNK_BYTES) {
                val start = if (nextOffset == 0L) (firstVoicedEnd - PADDING_BYTES).coerceAtLeast(0) else nextOffset
                val range = start until end
                nextOffset = end
                firstVoicedEnd = -1L
                return range
            }
            return null
        }
        if (!decoderReady || firstVoicedEnd < 0) return null

        val speechBytes = lastVoicedEnd - firstVoicedEnd
        val pauseBytes = end - lastVoicedEnd
        val requiredPause = if (speechBytes >= SOFT_MAX_CHUNK_BYTES) MICRO_PAUSE_BYTES else PAUSE_BYTES
        if (speechBytes < MIN_SPEECH_BYTES || pauseBytes < requiredPause) return null

        val cut = minOf(end, lastVoicedEnd + PADDING_BYTES)
        val start = if (nextOffset == 0L) (firstVoicedEnd - PADDING_BYTES).coerceAtLeast(0) else nextOffset
        val range = start until cut
        nextOffset = cut
        firstVoicedEnd = -1L
        return range
    }

    fun tail(end: Long): LongRange? {
        // With no earlier cut, transcribe the original WAV once.
        if (nextOffset == 0L || lastSignalEnd <= nextOffset) return null
        return nextOffset until minOf(end, lastSignalEnd + PADDING_BYTES)
    }

    companion object {
        internal const val PCM_BYTES_PER_SECOND = 32_000L
        private const val MIN_SPEECH_BYTES = PCM_BYTES_PER_SECOND * 2
        private const val PAUSE_BYTES = PCM_BYTES_PER_SECOND * 45 / 100
        private const val MICRO_PAUSE_BYTES = PCM_BYTES_PER_SECOND * 20 / 100
        private const val PADDING_BYTES = PCM_BYTES_PER_SECOND * 20 / 100
        internal const val SOFT_MAX_CHUNK_BYTES = PCM_BYTES_PER_SECOND * 16
        internal const val HARD_MAX_CHUNK_BYTES = PCM_BYTES_PER_SECOND * 24
    }
}

internal class IncrementalWhisper(
    scope: CoroutineScope,
    private val model: File,
    private val language: String?,
    private val vocabularyHint: String? = null,
    onPreview: (String) -> Unit
) {
    private val pending = Channel<File>(Channel.UNLIMITED, onUndeliveredElement = { it.delete() })
    private val busy = AtomicBoolean()
    private val worker = scope.async {
        val warmRes = LocalInferenceEngine.warmWhisper(model)
        if (warmRes.isFailure) {
            for (part in pending) part.delete()
            return@async null
        }
        var combined = ""
        var failed = false
        for (part in pending) {
            try {
                if (!failed) {
                    val prompt = buildContextPrompt(vocabularyHint, combined)
                    val result = LocalInferenceEngine.transcribeWav(
                        wavPath = part.absolutePath,
                        modelFile = model,
                        languageCode = language,
                        initialPrompt = prompt
                    )
                    if (result.isFailure) {
                        failed = true
                    } else {
                        val text = result.getOrNull()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            combined = joinTranscriptSegments(combined, text)
                            onPreview(combined)
                        }
                    }
                }
            } finally {
                part.delete()
                busy.set(false)
            }
        }
        if (failed || combined.isBlank()) null else combined
    }

    fun canSchedule(): Boolean = !busy.get() && !worker.isCompleted

    fun offer(file: File) {
        busy.set(true)
        if (pending.trySend(file).isFailure) {
            busy.set(false)
            file.delete()
        }
    }

    suspend fun finish(): String? {
        pending.close()
        return worker.await()
    }

    fun cancel() {
        pending.cancel()
        worker.cancel()
    }

    companion object {
        internal fun buildContextPrompt(vocabularyHint: String?, existingText: String): String? {
            val tail = existingText.trim().takeLast(140)
            val vocab = vocabularyHint?.trim().orEmpty()
            val combined = listOf(vocab, tail).filter { it.isNotEmpty() }.joinToString(". ")
            return combined.ifEmpty { null }
        }

        internal fun joinTranscriptSegments(existing: String, next: String): String {
            val left = existing.trim()
            val right = next.trim()
            if (left.isEmpty()) return right
            if (right.isEmpty()) return left

            val leftWords = left.split(Regex("""\s+"""))
            val rightWords = right.split(Regex("""\s+"""))
            val maxOverlap = minOf(4, leftWords.size, rightWords.size)
            for (k in maxOverlap downTo 1) {
                val leftTail = leftWords.takeLast(k).map(::normalizeWordForOverlap)
                val rightHead = rightWords.take(k).map(::normalizeWordForOverlap)
                if (leftTail == rightHead && leftTail.all { it.isNotEmpty() }) {
                    val remainder = rightWords.drop(k).joinToString(" ")
                    return if (remainder.isEmpty()) left else "$left $remainder"
                }
            }
            return "$left $right"
        }

        private fun normalizeWordForOverlap(word: String): String =
            word.lowercase().trim('.', ',', '!', '?', ':', ';', '"', '\'', '(', ')')
    }
}
