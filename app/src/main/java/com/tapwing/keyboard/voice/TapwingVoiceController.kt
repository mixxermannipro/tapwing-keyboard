package com.tapwing.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceUiState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isMinimized: Boolean = false,
    val rmsDb: Float = 0f,
    val partialText: String = "",
    val errorMessage: String? = null,
    val activeLocaleTag: String = "de-DE"
)

class TapwingVoiceController(
    private val context: Context,
    private val onTranscriptReady: (String) -> Unit,
    private val onNeedAudioPermission: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var pausedAtMs: Long = 0L
    private var wasListeningBeforeConfigChange: Boolean = false
    private var activeLocale: String = "de"
    private var lastCommittedSnapshot: String = ""
    private val accumulated = StringBuilder()
    private var segmentPartial: String = ""
    private var sessionActive: Boolean = false
    private var stopRequested: Boolean = false
    private var sessionGeneration: Int = 0

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun markConfigurationPause() {
        if (_state.value.isListening) {
            wasListeningBeforeConfigChange = true
            pausedAtMs = System.currentTimeMillis()
        }
    }

    fun resumeIfWithinGrace(graceMs: Long, strictLanguageLock: Boolean) {
        if (!wasListeningBeforeConfigChange) return
        val elapsed = System.currentTimeMillis() - pausedAtMs
        wasListeningBeforeConfigChange = false
        if (elapsed <= graceMs && hasAudioPermission() && !_state.value.isListening) {
            startListening(activeLocale, strictLanguageLock)
        }
    }

    fun toggleMinimized() {
        _state.value = _state.value.copy(isMinimized = !_state.value.isMinimized)
    }

    fun startListening(locale: String, strictLanguageLock: Boolean) {
        activeLocale = locale
        if (!hasAudioPermission()) {
            onNeedAudioPermission()
            return
        }
        if (!isAvailable()) {
            _state.value = _state.value.copy(
                isListening = false,
                errorMessage = if (locale == "de") {
                    "Kein System-Spracherkennungsdienst verfügbar"
                } else {
                    "No system speech recognition service available"
                }
            )
            return
        }

        mainHandler.post {
            // A new dictation session: it keeps recording until the user taps finish.
            sessionGeneration++
            sessionActive = true
            stopRequested = false
            accumulated.setLength(0)
            lastCommittedSnapshot = ""
            segmentPartial = ""
            mainHandler.removeCallbacksAndMessages(null)
            val languageTag = if (locale == "de") "de-DE" else "en-US"
            _state.value = VoiceUiState(
                isListening = true,
                isProcessing = false,
                isMinimized = false,
                partialText = "",
                errorMessage = null,
                activeLocaleTag = languageTag
            )
            startSegment(locale, strictLanguageLock)
        }
    }

    /** Starts one recognizer segment. Segments are chained until the user stops the session. */
    private fun startSegment(locale: String, strictLanguageLock: Boolean) {
        destroyRecognizerLocked()
        val generation = sessionGeneration
        val languageTag = if (locale == "de") "de-DE" else "en-US"
        val sr = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        recognizer = sr
        segmentPartial = ""

        fun isCurrent() = generation == sessionGeneration && sessionActive && recognizer === sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!isCurrent()) return
                _state.value = _state.value.copy(
                    isListening = !stopRequested,
                    isProcessing = stopRequested,
                    errorMessage = null,
                    activeLocaleTag = languageTag
                )
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (!isCurrent()) return
                _state.value = _state.value.copy(rmsDb = rmsdB.coerceIn(-2f, 12f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // The recognizer detected a pause. Keep the UI in "recording" until the user stops.
                if (!isCurrent()) return
                if (stopRequested) {
                    _state.value = _state.value.copy(isListening = false, isProcessing = true)
                }
            }

            override fun onError(error: Int) {
                if (!isCurrent()) return
                val keepGoing = !stopRequested && error in RESTARTABLE_ERRORS
                if (keepGoing) {
                    appendSegment(segmentPartial)
                    scheduleNextSegment(locale, strictLanguageLock, delayMs = 250L)
                    return
                }
                appendSegment(segmentPartial)
                val message = mapSpeechError(error, locale)
                val text = accumulated.toString().trim()
                if (text.isNotEmpty()) {
                    commitAccumulated()
                } else {
                    finishSession()
                    _state.value = _state.value.copy(
                        isListening = false,
                        isProcessing = false,
                        errorMessage = if (stopRequested && error in RESTARTABLE_ERRORS) null else message
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                if (!isCurrent()) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                appendSegment(if (best.isNotEmpty()) best else segmentPartial)
                if (stopRequested) {
                    commitAccumulated()
                } else {
                    scheduleNextSegment(locale, strictLanguageLock, delayMs = 60L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isCurrent()) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotEmpty()) {
                    segmentPartial = text
                    _state.value = _state.value.copy(partialText = joinText(accumulated.toString(), text))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            // Ask the engine to tolerate long pauses between sentences.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            if (strictLanguageLock) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            }
        }
        sr.startListening(intent)
    }

    private fun scheduleNextSegment(locale: String, strictLanguageLock: Boolean, delayMs: Long) {
        val generation = sessionGeneration
        segmentPartial = ""
        _state.value = _state.value.copy(
            isListening = true,
            isProcessing = false,
            partialText = accumulated.toString().trim()
        )
        mainHandler.postDelayed({
            if (generation == sessionGeneration && sessionActive && !stopRequested) {
                startSegment(locale, strictLanguageLock)
            }
        }, delayMs)
    }

    private fun appendSegment(text: String) {
        val clean = text.trim()
        segmentPartial = ""
        if (clean.isEmpty()) return
        val joined = joinText(accumulated.toString(), clean)
        accumulated.setLength(0)
        accumulated.append(joined)
    }

    private fun joinText(a: String, b: String): String {
        val left = a.trim()
        val right = b.trim()
        return when {
            left.isEmpty() -> right
            right.isEmpty() -> left
            else -> "$left $right"
        }
    }

    private fun finishSession() {
        sessionActive = false
        stopRequested = false
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizerLocked()
    }

    private fun commitAccumulated() {
        val text = accumulated.toString().trim()
        accumulated.setLength(0)
        finishSession()
        _state.value = _state.value.copy(
            isListening = false,
            isProcessing = false,
            partialText = "",
            errorMessage = null
        )
        if (text.isNotEmpty() && text != lastCommittedSnapshot) {
            lastCommittedSnapshot = text
            onTranscriptReady(text)
        }
    }

    /** Second tap on the mic/check: stop recording and insert everything that was said. */
    fun finishAndCommit() {
        mainHandler.post {
            if (!sessionActive) {
                _state.value = _state.value.copy(isListening = false, isProcessing = false)
                return@post
            }
            stopRequested = true
            val sr = recognizer
            val generation = sessionGeneration
            _state.value = _state.value.copy(isListening = false, isProcessing = true)
            if (sr != null) {
                runCatching { sr.stopListening() }
                // Safety net: if the engine never answers, commit what we already have.
                mainHandler.postDelayed({
                    if (generation == sessionGeneration && sessionActive) {
                        appendSegment(segmentPartial)
                        commitAccumulated()
                    }
                }, 2500L)
            } else {
                appendSegment(segmentPartial)
                commitAccumulated()
            }
        }
    }

    fun cancel() {
        wasListeningBeforeConfigChange = false
        mainHandler.post {
            sessionGeneration++
            accumulated.setLength(0)
            finishSession()
            _state.value = VoiceUiState()
        }
    }

    fun destroy() {
        mainHandler.post {
            sessionGeneration++
            accumulated.setLength(0)
            finishSession()
            _state.value = VoiceUiState()
        }
    }

    private fun destroyRecognizerLocked() {
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun mapSpeechError(code: Int, locale: String): String {
        val de = locale == "de"
        return when (code) {
            SpeechRecognizer.ERROR_AUDIO -> if (de) "Mikrofonfehler" else "Audio recording error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                if (de) "Mikrofonberechtigung fehlt" else "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                if (de) "Netzwerk-Timeout bei Spracherkennung" else "Speech network timeout"
            SpeechRecognizer.ERROR_NO_MATCH ->
                if (de) "Keine Sprache erkannt" else "No speech matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                if (de) "Spracherkennung beschäftigt" else "Speech recognizer busy"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                if (de) "Keine Eingabe gehört" else "No speech input"
            else -> if (de) "Spracherkennung fehlgeschlagen ($code)" else "Speech recognition error ($code)"
        }
    }

    private companion object {
        val RESTARTABLE_ERRORS = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        )
    }
}
