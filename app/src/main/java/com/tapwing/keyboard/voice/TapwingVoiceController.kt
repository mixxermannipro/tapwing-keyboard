package com.tapwing.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VoiceUiState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isMinimized: Boolean = false,
    val rmsDb: Float = 0f,
    val elapsedSeconds: Int = 0,
    val isLocalWhisper: Boolean = false,
    val isCloud: Boolean = false,
    val isAndroidOffline: Boolean = false,
    val partialText: String = "",
    val processingLabel: String? = null,
    val errorMessage: String? = null,
    val activeLocaleTag: String = "de-DE"
)

internal class TapwingVoiceController(
    private val context: Context,
    private val modelManager: LocalModelManager,
    private val onTranscriptReady: (String, Boolean) -> Unit,
    private val onNeedAudioPermission: () -> Unit
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
    private var activeCloud: CloudDictation? = null
    private var activeStrictLanguage = true
    private var activeOfflineOnly = false
    private var activeFormatting = true
    private var transcriptionJob: Job? = null
    private var usingLocalAudioCapture: Boolean = false

    private var lastCommittedSnapshot: String = ""
    private val accumulated = StringBuilder()
    private var segmentPartial: String = ""
    private var sessionActive: Boolean = false
    private var stopRequested: Boolean = false
    private var sessionGeneration: Int = 0

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    init {
        val createdAt = System.currentTimeMillis()
        controllerScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.cacheDir.listFiles()?.filter {
                        it.name.startsWith("tapwing_voice_") && it.extension == "wav" && it.lastModified() < createdAt
                    }?.forEach { it.delete() }
                }
            }
        }
    }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

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
                offlineOnly = activeOfflineOnly,
                whisperModelId = activeWhisperModelId,
                formatText = activeFormatting
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
        _state.value = _state.value.copy(
            isListening = false,
            isProcessing = true,
            processingLabel = label,
            errorMessage = null
        )
    }

    fun finishExternalProcessing(errorMessage: String? = null) {
        _state.value = VoiceUiState(errorMessage = errorMessage)
    }

    fun startListening(
        locale: String,
        strictLanguageLock: Boolean,
        engineMode: VoiceEngineMode,
        offlineOnly: Boolean,
        whisperModelId: String,
        formatText: Boolean,
        cloud: CloudDictation? = null
    ) {
        if (sessionActive || _state.value.isProcessing) return
        activeLocale = locale
        activeEngineMode = engineMode
        activeWhisperModelId = whisperModelId
        activeStrictLanguage = strictLanguageLock
        activeFormatting = formatText
        activeOfflineOnly = offlineOnly

        if (!hasAudioPermission()) {
            onNeedAudioPermission()
            return
        }
        val de = KeyboardLocales.baseLang(locale) == "de"
        if (engineMode == VoiceEngineMode.CLOUD) {
            if (cloud == null) {
                showError(if (de) "Cloud-Diktat braucht einen Gemini- oder OpenAI-Schlüssel"
                          else "Cloud dictation needs a Gemini or OpenAI key")
            } else {
                startAudioCapture(locale, modelFile = null, cloud = cloud)
            }
            return
        }
        if (engineMode == VoiceEngineMode.LOCAL_WHISPER || engineMode == VoiceEngineMode.AUTO_LOCAL_FIRST) {
            val installed = modelManager.firstInstalledModel(LocalModelKind.WHISPER_STT, whisperModelId)
            if (installed != null) {
                startAudioCapture(locale, modelFile = installed.second, cloud = null)
                return
            }
            if (engineMode == VoiceEngineMode.LOCAL_WHISPER) {
                showError(if (de) "Lade unter Diktat ein Offline-Sprachmodell" else "Download an offline model in Dictation settings")
                return
            }
        }
        val available = if (offlineOnly || engineMode == VoiceEngineMode.ON_DEVICE_SPEECH) {
            isOnDeviceSpeechAvailable(context)
        } else {
            SpeechRecognizer.isRecognitionAvailable(context)
        }
        if (available) {
            startSystemSpeechSession(locale, strictLanguageLock)
        } else {
            showError(if (de) "Android-Diktat fehlt. Lade ein Offline-Sprachmodell"
                      else "Android dictation unavailable. Download an offline model")
        }
    }

    private fun showError(message: String) {
        _state.value = VoiceUiState(errorMessage = message)
    }

    private fun startAudioCapture(locale: String, modelFile: File?, cloud: CloudDictation?) {
        sessionGeneration++
        val generation = sessionGeneration
        sessionActive = true
        stopRequested = false
        usingLocalAudioCapture = true
        activeWhisperFile = modelFile
        activeCloud = cloud
        val de = KeyboardLocales.baseLang(locale) == "de"
        val wavFile = File(context.cacheDir, "tapwing_voice_${java.util.UUID.randomUUID()}.wav")
        val started = wavRecorder.start(
            outputFile = wavFile,
            onReady = { mainHandler.post {
                if (generation == sessionGeneration && _state.value.isListening) {
                    _state.value = _state.value.copy(processingLabel = null)
                }
            } },
            onRmsDb = { db -> mainHandler.post {
                if (generation == sessionGeneration && _state.value.isListening) {
                    _state.value = _state.value.copy(rmsDb = db)
                }
            } },
            onElapsedSeconds = { seconds -> mainHandler.post {
                if (generation == sessionGeneration && _state.value.isListening) {
                    _state.value = _state.value.copy(elapsedSeconds = seconds)
                    if (seconds >= MAX_RECORDING_SECONDS) finishAndCommit()
                }
            } },
            onError = { mainHandler.post {
                if (generation == sessionGeneration && sessionActive) {
                    wavRecorder.cancelAndDelete()
                    finishSession()
                    showError(if (de) "Mikrofon nicht verfügbar" else "Microphone unavailable")
                }
            } }
        )
        if (!started) {
            finishSession()
            showError(if (de) "Mikrofon wird noch freigegeben" else "Microphone is still closing")
            return
        }
        _state.value = VoiceUiState(
            isListening = true,
            isLocalWhisper = modelFile != null,
            isCloud = cloud != null,
            processingLabel = if (de) "Mikrofon startet …" else "Starting microphone…",
            activeLocaleTag = KeyboardLocales.specFor(locale).bcp47Tag
        )
        if (modelFile != null) {
            controllerScope.launch { LocalInferenceEngine.warmWhisper(modelFile) }
        }
    }

    private fun startSystemSpeechSession(locale: String, strictLanguageLock: Boolean) {
        sessionGeneration++
        sessionActive = true
        stopRequested = false
        usingLocalAudioCapture = false
        activeWhisperFile = null
        activeCloud = null
        accumulated.setLength(0)
        lastCommittedSnapshot = ""
        segmentPartial = ""
        mainHandler.removeCallbacksAndMessages(null)
        _state.value = VoiceUiState(
            isListening = true,
            isAndroidOffline = activeOfflineOnly || activeEngineMode == VoiceEngineMode.ON_DEVICE_SPEECH,
            activeLocaleTag = KeyboardLocales.specFor(locale).bcp47Tag
        )
        startSegment(locale, strictLanguageLock)
    }

    private fun startSegment(locale: String, strictLanguageLock: Boolean) {
        destroyRecognizerLocked()
        val generation = sessionGeneration
        val languageTag = KeyboardLocales.specFor(locale).bcp47Tag
        val localRecognizer = activeOfflineOnly || activeEngineMode == VoiceEngineMode.ON_DEVICE_SPEECH
        val sr = runCatching {
            if (localRecognizer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            }
        }.getOrElse {
            finishSession()
            showError(mapSpeechError(SpeechRecognizer.ERROR_CLIENT, locale))
            return
        }
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
                val wasStopping = stopRequested
                val text = accumulated.toString().trim()
                if (text.isNotEmpty()) {
                    commitAccumulated()
                } else {
                    finishSession()
                    _state.value = _state.value.copy(
                        isListening = false,
                        isProcessing = false,
                        errorMessage = if (wasStopping && error in RESTARTABLE_ERRORS) null else message
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
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activeFormatting) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY)
                putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
            }
            if (localRecognizer) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (strictLanguageLock) putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000L)
            if (strictLanguageLock) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            }
        }
        try {
            sr.startListening(intent)
        } catch (_: RuntimeException) {
            finishSession()
            showError(mapSpeechError(SpeechRecognizer.ERROR_CLIENT, locale))
        }
    }

    private fun scheduleNextSegment(locale: String, strictLanguageLock: Boolean, delayMs: Long) {
        val generation = sessionGeneration
        destroyRecognizerLocked()
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
        activeWhisperFile = null
        activeCloud = null
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
            onTranscriptReady(text, false)
        }
    }

    fun finishAndCommit() {
        if (!sessionActive || stopRequested) return
        stopRequested = true
        val generation = sessionGeneration
        _state.value = _state.value.copy(isListening = false, isProcessing = true)
        if (usingLocalAudioCapture) {
            transcribeCapture(generation)
            return
        }

        val sr = recognizer
        if (sr == null) {
            appendSegment(segmentPartial)
            commitAccumulated()
        } else {
            runCatching { sr.stopListening() }
            val waitMs = if (segmentPartial.isNotBlank()) 1_300L else 2_500L
            mainHandler.postDelayed({
                if (generation == sessionGeneration && sessionActive) {
                    appendSegment(segmentPartial)
                    commitAccumulated()
                }
            }, waitMs)
        }
    }

    private fun transcribeCapture(generation: Int) {
        val model = activeWhisperFile
        val cloud = activeCloud
        val lang = KeyboardLocales.baseLang(activeLocale)
        val languageHint = lang.takeIf { activeStrictLanguage }
        val de = lang == "de"
        _state.value = _state.value.copy(
            processingLabel = when {
                cloud != null && de -> "Cloud transkribiert …"
                cloud != null -> "Transcribing in the cloud…"
                de -> "Transkribiere auf dem Gerät …"
                else -> "Transcribing on your device…"
            }
        )
        transcriptionJob = controllerScope.launch {
            val recording = wavRecorder.stopAndGetResult()
            if (generation != sessionGeneration || !sessionActive) {
                recording?.file?.delete()
                return@launch
            }
            if (recording == null || recording.durationMs < 250L || recording.peakSampleAbs < 35) {
                recording?.file?.delete()
                finishSession()
                showError(if (de) "Keine Sprache erkannt. Sprich nach dem Start." else "No speech detected. Speak after starting.")
                return@launch
            }
            val result = try {
                when {
                    cloud != null -> cloud.transcribe(recording.file, languageHint)
                    model != null -> LocalInferenceEngine.transcribeWav(
                        wavPath = recording.file.absolutePath,
                        modelFile = model,
                        languageCode = languageHint
                    )
                    else -> Result.failure(IllegalStateException("Missing speech model"))
                }
            } finally {
                recording.file.delete()
            }
            if (generation != sessionGeneration || !sessionActive) return@launch
            finishSession()
            val text = result.getOrNull().orEmpty()
            when {
                result.isFailure && cloud != null -> {
                    showError(if (de) "Cloud-Diktat fehlgeschlagen. Prüfe Netz und Schlüssel."
                              else "Cloud dictation failed. Check your connection and API key.")
                }
                result.isFailure -> {
                    showError(if (de) "Offline-Modell fehlgeschlagen. Prüfe den Download."
                              else "Offline model failed. Check the download.")
                }
                text.isBlank() -> showError(if (de) "Keine Sprache erkannt. Versuch es erneut."
                                            else "No speech detected. Try again.")
                else -> {
                    _state.value = VoiceUiState()
                    onTranscriptReady(text, cloud?.formatsText == true)
                }
            }
        }
    }

    fun cancel(keepRotationResume: Boolean = false) {
        if (!keepRotationResume) wasListeningBeforeConfigChange = false
        sessionGeneration++
        transcriptionJob?.cancel()
        transcriptionJob = null
        wavRecorder.cancelAndDelete()
        accumulated.setLength(0)
        finishSession()
        _state.value = VoiceUiState()
    }

    fun destroy() {
        cancel()
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
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                if (de) "Android-Sprachpaket fehlt. Prüfe die Spracheinstellungen." else "Android language pack missing. Check language settings."
            SpeechRecognizer.ERROR_NO_MATCH ->
                if (de) "Keine Sprache erkannt" else "No speech matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                if (de) "Spracherkennung beschäftigt" else "Speech recognizer busy"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                if (de) "Keine Eingabe gehört" else "No speech input"
            else -> if (de) "Spracherkennung fehlgeschlagen ($code)" else "Speech recognition error ($code)"
        }
    }

    companion object {
        private const val MAX_RECORDING_SECONDS = 120

        fun isOnDeviceSpeechAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        private val RESTARTABLE_ERRORS = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        )
    }
}
