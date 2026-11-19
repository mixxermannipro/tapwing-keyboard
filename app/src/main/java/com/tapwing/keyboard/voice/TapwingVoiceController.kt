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
            destroyRecognizerLocked()
            val languageTag = if (locale == "de") "de-DE" else "en-US"
            val sr = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            recognizer = sr
            lastCommittedSnapshot = ""

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(
                        isListening = true,
                        isProcessing = false,
                        errorMessage = null,
                        activeLocaleTag = languageTag
                    )
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    _state.value = _state.value.copy(rmsDb = rmsdB.coerceIn(-2f, 12f))
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _state.value = _state.value.copy(isListening = false, isProcessing = true)
                }

                override fun onError(error: Int) {
                    val message = mapSpeechError(error, locale)
                    _state.value = _state.value.copy(
                        isListening = false,
                        isProcessing = false,
                        errorMessage = message
                    )
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    val fallbackPartial = _state.value.partialText.trim()
                    val chosen = if (best.isNotEmpty()) best else fallbackPartial
                    _state.value = _state.value.copy(
                        isListening = false,
                        isProcessing = false,
                        partialText = "",
                        errorMessage = null
                    )
                    if (chosen.isNotEmpty() && chosen != lastCommittedSnapshot) {
                        lastCommittedSnapshot = chosen
                        onTranscriptReady(chosen)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    if (text.isNotEmpty()) {
                        _state.value = _state.value.copy(partialText = text)
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
                if (strictLanguageLock) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
                }
            }

            _state.value = VoiceUiState(
                isListening = true,
                isProcessing = false,
                isMinimized = false,
                partialText = "",
                errorMessage = null,
                activeLocaleTag = languageTag
            )
            sr.startListening(intent)
        }
    }

    fun finishAndCommit() {
        mainHandler.post {
            val sr = recognizer
            val currentPartial = _state.value.partialText.trim()
            if (sr != null && _state.value.isListening) {
                sr.stopListening()
                _state.value = _state.value.copy(isListening = false, isProcessing = true)
            } else if (currentPartial.isNotEmpty() && currentPartial != lastCommittedSnapshot) {
                lastCommittedSnapshot = currentPartial
                _state.value = _state.value.copy(
                    isListening = false,
                    isProcessing = false,
                    partialText = ""
                )
                onTranscriptReady(currentPartial)
            } else {
                _state.value = _state.value.copy(isListening = false, isProcessing = false)
            }
        }
    }

    fun cancel() {
        wasListeningBeforeConfigChange = false
        mainHandler.post {
            destroyRecognizerLocked()
            _state.value = VoiceUiState()
        }
    }

    fun destroy() {
        mainHandler.post {
            destroyRecognizerLocked()
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
}
