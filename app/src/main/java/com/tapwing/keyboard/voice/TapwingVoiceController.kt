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
import com.tapwing.keyboard.ai.LocalInferenceEngine
import com.tapwing.keyboard.ai.LocalModelKind
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.VoiceEngineMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class VoiceUiState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isMinimized: Boolean = false,
    val rmsDb: Float = 0f,
    val elapsedSeconds: Int = 0,
    val isLocalWhisper: Boolean = false,
    val partialText: String = "",
    val processingLabel: String? = null,
    val errorMessage: String? = null,
    val activeLocaleTag: String = "de-DE"
)

class TapwingVoiceController(
    private val context: Context,
    private val modelManager: LocalModelManager,
    private val onTranscriptReady: (String) -> Unit,
    private val onNeedAudioPermission: () -> Unit,
    private val initialPromptProvider: () -> String = { "" }
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wavRecorder = WavAudioRecorder()

    private var recognizer: SpeechRecognizer? = null
    private var pausedAtMs: Long = 0L
    private var wasListeningBeforeConfigChange: Boolean = false
    private var activeLocale: String = "de"
    private var activeEngineMode: VoiceEngineMode = VoiceEngineMode.AUTO_LOCAL_FIRST
    private var activeWhisperModelId: String = "whisper-small-q5_1"
    private var activeWhisperFile: File? = null
    private var usingLocalAudioCapture: Boolean = false

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

    fun isSystemSpeechAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun markConfigurationPause() {
        if (_state.value.isListening && !usingLocalAudioCapture) {
            wasListeningBeforeConfigChange = true
            pausedAtMs = System.currentTimeMillis()
        }
    }

    fun resumeIfWithinGrace(graceMs: Long, strictLanguageLock: Boolean) {
        if (!wasListeningBeforeConfigChange) return
        val elapsed = System.currentTimeMillis() - pausedAtMs
        wasListeningBeforeConfigChange = false
        if (elapsed <= graceMs && hasAudioPermission() && !_state.value.isListening) {
            startListening(
                locale = activeLocale,
                strictLanguageLock = strictLanguageLock,
                engineMode = activeEngineMode,
                whisperModelId = activeWhisperModelId
            )
        }
    }

    fun toggleMinimized() {
        _state.value = _state.value.copy(isMinimized = !_state.value.isMinimized)
    }

    fun clearError() {
        if (_state.value.errorMessage != null) {
            _state.value = _state.value.copy(errorMessage = null)
        }
    }

    fun beginExternalProcessing(label: String) {
        mainHandler.post {
            _state.value = _state.value.copy(
                isListening = false,
                isProcessing = true,
                processingLabel = label,
                errorMessage = null
            )
        }
    }

    fun finishExternalProcessing(errorMessage: String? = null) {
        mainHandler.post {
            _state.value = VoiceUiState(errorMessage = errorMessage)
        }
    }

    fun startListening(
        locale: String,
        strictLanguageLock: Boolean,
        engineMode: VoiceEngineMode = VoiceEngineMode.AUTO_LOCAL_FIRST,
        whisperModelId: String = "whisper-small-q5_1"
    ) {
        activeLocale = locale
        activeEngineMode = engineMode
        activeWhisperModelId = whisperModelId

        if (!hasAudioPermission()) {
            onNeedAudioPermission()
            return
        }

        val installedWhisper = if (engineMode != VoiceEngineMode.SYSTEM_SPEECH) {
            modelManager.firstInstalledModel(LocalModelKind.WHISPER_STT, whisperModelId)
        } else {
            null
        }

        if (installedWhisper != null) {
            startLocalWhisperRecording(locale, installedWhisper.second)
            return
        }

        if (engineMode == VoiceEngineMode.LOCAL_WHISPER) {
            val base = KeyboardLocales.baseLang(locale)
            _state.value = _state.value.copy(
                isListening = false,
                errorMessage = if (base == "de") {
                    "Kein lokales Whisper-Modell geladen (Einstellungen > Spracheingabe)"
                } else {
                    "No local Whisper model downloaded (Settings > Voice)"
                }
            )
            return
        }

        if (!isSystemSpeechAvailable()) {
            val base = KeyboardLocales.baseLang(locale)
            _state.value = _state.value.copy(
                isListening = false,
                errorMessage = if (base == "de") {
                    "Kein Spracherkennungsdienst verfügbar — lade ein lokales Whisper-Modell"
                } else {
                    "No speech service available — download a local Whisper model"
                }
            )
            return
        }

        startSystemSpeechSession(locale, strictLanguageLock)
    }

    private fun startLocalWhisperRecording(locale: String, modelFile: File) {
        mainHandler.post {
            sessionGeneration++
            sessionActive = true
            stopRequested = false
            usingLocalAudioCapture = true
            activeWhisperFile = modelFile
            accumulated.setLength(0)
            lastCommittedSnapshot = ""
            segmentPartial = ""
            mainHandler.removeCallbacksAndMessages(null)
            destroyRecognizerLocked()

            val languageTag = KeyboardLocales.specFor(locale).bcp47Tag
            val wavFile = File(context.cacheDir, "tapwing_voice_capture.wav")
            val started = wavRecorder.start(
                outputFile = wavFile,
                onRmsDb = { db ->
                    if (sessionActive && usingLocalAudioCapture) {
                        _state.value = _state.value.copy(rmsDb = db)
                    }
                },
                onElapsedSeconds = { sec ->
                    if (sessionActive && usingLocalAudioCapture) {
                        _state.value = _state.value.copy(elapsedSeconds = sec)
                    }
                },
                onError = { err ->
                    mainHandler.post {
                        sessionActive = false
                        usingLocalAudioCapture = false
                        _state.value = VoiceUiState(errorMessage = err)
                    }
                }
            )

            if (started) {
                _state.value = VoiceUiState(
                    isListening = true,
                    isProcessing = false,
                    isMinimized = false,
                    rmsDb = 0f,
                    elapsedSeconds = 0,
                    isLocalWhisper = true,
                    partialText = "",
                    errorMessage = null,
                    activeLocaleTag = languageTag
                )
            } else {
                sessionActive = false
                usingLocalAudioCapture = false
            }
        }
    }

    private fun startSystemSpeechSession(locale: String, strictLanguageLock: Boolean) {
        mainHandler.post {
            sessionGeneration++
            sessionActive = true
            stopRequested = false
            usingLocalAudioCapture = false
            activeWhisperFile = null
            accumulated.setLength(0)
            lastCommittedSnapshot = ""
            segmentPartial = ""
            mainHandler.removeCallbacksAndMessages(null)
            val languageTag = KeyboardLocales.specFor(locale).bcp47Tag
            _state.value = VoiceUiState(
                isListening = true,
                isProcessing = false,
                isMinimized = false,
                isLocalWhisper = false,
                partialText = "",
                errorMessage = null,
                activeLocaleTag = languageTag
            )
            startSegment(locale, strictLanguageLock)
        }
    }

    private fun startSegment(locale: String, strictLanguageLock: Boolean) {
        destroyRecognizerLocked()
        val generation = sessionGeneration
        val languageTag = KeyboardLocales.specFor(locale).bcp47Tag
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
                    scheduleNextSegment(locale, strictLanguageLock, delayMs = 200L)
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
                    scheduleNextSegment(locale, strictLanguageLock, delayMs = 50L)
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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000L)
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
        usingLocalAudioCapture = false
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizerLocked()
    }

    private fun commitAccumulated() {
        val text = accumulated.toString().trim()
        accumulated.setLength(0)
        finishSession()
        _state.value = VoiceUiState()
        if (text.isNotEmpty() && text != lastCommittedSnapshot) {
            lastCommittedSnapshot = text
            onTranscriptReady(text)
        }
    }

    /** Second tap on mic/check: stops recording and runs local Whisper transcription + cleanup. */
    fun finishAndCommit() {
        mainHandler.post {
            if (!sessionActive) {
                _state.value = VoiceUiState()
                return@post
            }
            if (usingLocalAudioCapture) {
                val modelFile = activeWhisperFile
                val locale = activeLocale
                val base = KeyboardLocales.baseLang(locale)
                val gen = sessionGeneration
                stopRequested = true
                _state.value = _state.value.copy(
                    isListening = false,
                    isProcessing = true,
                    processingLabel = if (base == "de") "Transkribiere lokal…" else "Transcribing locally…"
                )
                controllerScope.launch {
                    val recResult = wavRecorder.stopAndGetResult()
                    val wavFile = recResult?.file
                    if (gen != sessionGeneration) {
                        wavFile?.delete()
                        return@launch
                    }
                    if (recResult == null || wavFile == null || modelFile == null || recResult.durationMs < 250L) {
                        wavFile?.delete()
                        finishSession()
                        _state.value = VoiceUiState(
                            errorMessage = if (base == "de") "Zu kurz — erst sprechen, dann ✓" else "Too short — speak before ✓"
                        )
                        return@launch
                    }
                    if (recResult.peakSampleAbs < 35) {
                        wavFile.delete()
                        finishSession()
                        _state.value = VoiceUiState(
                            errorMessage = if (base == "de") "Kein Mikrofonsignal erkannt" else "No microphone signal detected"
                        )
                        return@launch
                    }
                    val langCode = KeyboardLocales.baseLang(locale)
                    val initialPrompt = initialPromptProvider().take(300)
                    val res = try {
                        LocalInferenceEngine.transcribeWav(
                            wavPath = wavFile.absolutePath,
                            modelFile = modelFile,
                            languageCode = langCode,
                            initialPrompt = initialPrompt.takeIf { it.isNotBlank() }
                        )
                    } finally {
                        wavFile.delete()
                    }
                    if (gen != sessionGeneration) return@launch
                    finishSession()
                    val transcript = res.getOrNull()?.trim().orEmpty()
                    if (transcript.isNotEmpty()) {
                        _state.value = VoiceUiState(
                            isListening = false,
                            isProcessing = true,
                            isLocalWhisper = true,
                            processingLabel = if (base == "de") "Bereinige Text…" else "Polishing text…"
                        )
                        onTranscriptReady(transcript)
                    } else {
                        val fallbackErr = if (base == "de") {
                            "Keine Sprache verstanden — bitte deutlicher sprechen"
                        } else {
                            "No speech detected — please try again"
                        }
                        _state.value = VoiceUiState(
                            errorMessage = res.exceptionOrNull()?.localizedMessage ?: fallbackErr
                        )
                    }
                }
                return@post
            }

            stopRequested = true
            val sr = recognizer
            val generation = sessionGeneration
            _state.value = _state.value.copy(isListening = false, isProcessing = true)
            if (sr != null) {
                runCatching { sr.stopListening() }
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
            wavRecorder.cancelAndDelete()
            accumulated.setLength(0)
            finishSession()
            _state.value = VoiceUiState()
        }
    }

    fun destroy() {
        mainHandler.post {
            sessionGeneration++
            wavRecorder.cancelAndDelete()
            accumulated.setLength(0)
            finishSession()
            _state.value = VoiceUiState()
        }
        controllerScope.cancel()
    }

    private fun destroyRecognizerLocked() {
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun mapSpeechError(code: Int, locale: String): String {
        val de = KeyboardLocales.baseLang(locale) == "de"
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
