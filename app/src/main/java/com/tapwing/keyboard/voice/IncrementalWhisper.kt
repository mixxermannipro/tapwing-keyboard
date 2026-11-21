package com.tapwing.keyboard.voice

import com.tapwing.keyboard.ai.LocalInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Byte ranges in a 16 kHz mono PCM stream. Cut only inside an observed pause. */
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
            return null
        }
        if (!decoderReady || firstVoicedEnd < 0 ||
            lastVoicedEnd - firstVoicedEnd < MIN_SPEECH_BYTES ||
            end - lastVoicedEnd < PAUSE_BYTES
        ) return null

        val cut = lastVoicedEnd + PADDING_BYTES
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
        private const val PCM_BYTES_PER_SECOND = 32_000L
        private const val MIN_SPEECH_BYTES = PCM_BYTES_PER_SECOND * 2
        private const val PAUSE_BYTES = PCM_BYTES_PER_SECOND * 45 / 100
        private const val PADDING_BYTES = PCM_BYTES_PER_SECOND * 20 / 100
    }
}

internal class IncrementalWhisper(
    scope: CoroutineScope,
    private val model: File,
    private val language: String,
    onPreview: (String) -> Unit
) {
    private val pending = Channel<File>(Channel.UNLIMITED, onUndeliveredElement = { it.delete() })
    private val busy = AtomicBoolean()
    private val worker = scope.async {
        LocalInferenceEngine.warmWhisper(model)
        val words = mutableListOf<String>()
        var invalid = false
        for (part in pending) {
            try {
                if (!invalid) {
                    val text = LocalInferenceEngine.transcribeWav(part.absolutePath, model, language)
                        .getOrNull()?.trim().orEmpty()
                    if (text.isBlank()) {
                        invalid = true
                    } else {
                        words += text
                        onPreview(words.joinToString(" "))
                    }
                }
            } finally {
                part.delete()
                busy.set(false)
            }
        }
        if (invalid || words.isEmpty()) null else words.joinToString(" ")
    }

    fun canSchedule(): Boolean = !busy.get() && !worker.isCompleted

    fun offer(file: File) {
        busy.set(true)
        if (pending.trySend(file).isFailure) file.delete()
    }

    suspend fun finish(): String? {
        pending.close()
        return worker.await()
    }

    fun cancel() {
        pending.cancel()
        worker.cancel()
    }
}
