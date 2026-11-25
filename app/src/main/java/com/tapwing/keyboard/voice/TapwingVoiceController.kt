package com.tapwing.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.tapwing.keyboard.ai.LocalInferenceEngine
import com.tapwing.keyboard.ai.LocalModelKind
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.ai.LocalModelSpec
import com.tapwing.keyboard.ai.SherpaOnnxEngine
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.VoiceEngineMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

data class VoiceUiState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val isPaused: Boolean = false,
    val canPause: Boolean = false,
    val isMinimized: Boolean = false,
    val rmsDb: Float = 0f,
    val elapsedSeconds: Int = 0,
    val isLocalWhisper: Boolean = false,
    val isCloud: Boolean = false,
    val isAndroidOffline: Boolean = false,
    val partialText: String = "",
    val processingLabel: String? = null,
    val errorMessage: String? = null,
    val activeLocaleTag: String = "de-DE",
    val engineLabel: String? = null,
    val isMultilingual: Boolean = false,
    val multilingualLabel: String? = null
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
    private var supportRecognizer: SpeechRecognizer? = null
    private var checkedLanguageTag: String? = null
    private var checkedLanguageAt = 0L
    private var readyLanguageTag: String? = null
    private var useOfflineAndroidSession = false
    private var pausedAtMs: Long = 0L
    private var wasListeningBeforeConfigChange: Boolean = false
    private var activeLocale: String = "de"
    private var activeEngineMode: VoiceEngineMode = VoiceEngineMode.AUTO_LOCAL_FIRST
    private var activeWhisperModelId: String = "sherpa-fastconformer-de-pc-int8"
    private var activeLocalModel: Pair<LocalModelSpec, File>? = null
    private var activeCloud: CloudDictation? = null
    private var activeFallbackCloud: CloudDictation? = null
    private var activeFallbackLocalModel: Pair<LocalModelSpec, File>? = null
    private var activeLive: GeminiLiveDictation? = null
    @Volatile
    private var liveFailed: Boolean = false
    private var activeIncremental: IncrementalWhisper? = null
    private var activeSherpaStreamJob: Deferred<SherpaOnnxEngine.SherpaStreamingSession?>? = null
    @Volatile
    private var activeSherpaStream: SherpaOnnxEngine.SherpaStreamingSession? = null
    private var activeVocabularyHint: String? = null
    private var stopStartedAt = 0L
    private var lastPartialChangedAt = 0L
    private var segmentSpeechEnded = false
    private var activeStrictLanguage = false
    private var activeMultilingual = true
    private var activeMultilingualLabel: String? = null
    private var activeOfflineOnly = false
    private var activeFormatting = true
    private var transcriptionJob: Job? = null
    private var warmJob: Job? = null
    private var usingLocalAudioCapture: Boolean = false
    private var systemSpeechStartedAtMs: Long = 0L
    private var consecutiveSegmentErrors: Int = 0

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
                        (it.name.startsWith("tapwing_voice_") || it.name.startsWith("tapwing_cloud_chunk_")) &&
                            it.extension == "wav" &&
                            it.lastModified() < createdAt
                    }?.forEach { it.delete() }
                }
            }
        }
    }

    fun warmInstalledLocalModel(
        locale: String,
        preferredModelId: String,
        preferMultilingual: Boolean = false
    ) {
        if (sessionActive || _state.value.isProcessing) return
        val best = modelManager.bestInstalledSpeechModel(
            preferredId = preferredModelId,
            locale = locale,
            preferMultilingual = preferMultilingual
        ) ?: return
        warmJob?.cancel()
        warmJob = controllerScope.launch {
            LocalInferenceEngine.warmSpeechModel(
                spec = best.first,
                modelPath = best.second,
                languageCode = if (preferMultilingual) null else KeyboardLocales.baseLang(locale)
            )
        }
    }

    fun prepareOfflineSpeech(locale: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isOnDeviceSpeechAvailable(context)) return
        val tag = KeyboardLocales.specFor(locale).bcp47Tag
        val now = SystemClock.elapsedRealtime()
        if (checkedLanguageTag == tag && now - checkedLanguageAt < 60_000L) return
        checkedLanguageTag = tag
        checkedLanguageAt = now
        supportRecognizer?.destroy()
        supportRecognizer = null
        val checker = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull() ?: return
        supportRecognizer = checker
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        fun finish(installed: Boolean) {
            if (supportRecognizer !== checker) return
            supportRecognizer = null
            readyLanguageTag = tag.takeIf { installed }
            checker.destroy()
        }
        runCatching {
            checker.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    finish(support.installedOnDeviceLanguages.any { it.replace('_', '-').equals(tag, ignoreCase = true) })
                }

                override fun onError(error: Int) = finish(installed = false)
            })
        }.onFailure { finish(installed = false) }
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
                formatText = activeFormatting,
                vocabularyHint = activeVocabularyHint,
                multilingual = activeMultilingual,
                multilingualLabel = activeMultilingualLabel
            )
        }
    }

    fun toggleMinimized() {
        _state.value = _state.value.copy(isMinimized = !_state.value.isMinimized)
    }

    fun togglePause() {
        if (!sessionActive || !usingLocalAudioCapture || stopRequested) return
        val nextPaused = !wavRecorder.isPaused()
        if (wavRecorder.setPaused(nextPaused)) {
            _state.value = _state.value.copy(
                isPaused = nextPaused,
                rmsDb = if (nextPaused) 0f else _state.value.rmsDb
            )
        }
    }

    fun clearError() {
        if (_state.value.errorMessage != null) {
            _state.value = _state.value.copy(errorMessage = null)
        }
    }

    fun beginExternalProcessing(label: String) {
        _state.value = _state.value.copy(
            isListening = false,
            isPaused = false,
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
        cloud: CloudDictation? = null,
        live: GeminiLiveConfig? = null,
        fallbackCloud: CloudDictation? = null,
        vocabularyHint: String? = null,
        multilingual: Boolean = false,
        multilingualLabel: String? = null
    ) {
        if (sessionActive || _state.value.isProcessing) return
        activeLocale = locale
        activeEngineMode = engineMode
        activeWhisperModelId = whisperModelId
        activeMultilingual = multilingual
        activeMultilingualLabel = multilingualLabel
        activeStrictLanguage = strictLanguageLock && !multilingual
        activeFormatting = formatText
        activeOfflineOnly = offlineOnly
        activeVocabularyHint = vocabularyHint?.trim()?.takeIf { it.isNotEmpty() }
        stopStartedAt = 0L
        lastPartialChangedAt = 0L

        if (!hasAudioPermission()) {
            onNeedAudioPermission()
            return
        }
        val de = KeyboardLocales.baseLang(locale) == "de"
        val bestLocal = modelManager.bestInstalledSpeechModel(
            preferredId = whisperModelId,
            locale = locale,
            preferMultilingual = multilingual
        )

        if (engineMode == VoiceEngineMode.GEMINI_LIVE) {
            if (offlineOnly || live == null) {
                if (bestLocal != null) {
                    startAudioCapture(locale, localModel = bestLocal, cloud = null)
                } else {
                    showError(
                        if (de) "Speichere für Gemini Live einen Google-API-Schlüssel"
                        else "Save a Google API key for Gemini Live"
                    )
                }
            } else {
                startAudioCapture(
                    locale = locale,
                    localModel = null,
                    cloud = null,
                    liveConfig = live,
                    fallbackCloud = fallbackCloud,
                    fallbackLocalModel = bestLocal
                )
            }
            return
        }
        if (engineMode == VoiceEngineMode.CLOUD) {
            val effectiveCloud = cloud ?: fallbackCloud
            val effectiveFallback = if (cloud != null) fallbackCloud else null
            if (effectiveCloud == null) {
                if (!offlineOnly && bestLocal != null) {
                    startAudioCapture(locale, localModel = bestLocal, cloud = null)
                } else {
                    showError(
                        if (de) "Cloud-Diktat braucht einen API-Schlüssel"
                        else "Cloud dictation needs an API key"
                    )
                }
            } else {
                startAudioCapture(
                    locale = locale,
                    localModel = null,
                    cloud = effectiveCloud,
                    fallbackCloud = effectiveFallback,
                    fallbackLocalModel = bestLocal
                )
            }
            return
        }

        // In AUTO_LOCAL_FIRST: prefer an installed next-gen Sherpa-ONNX model matching the active language first.
        if (engineMode == VoiceEngineMode.AUTO_LOCAL_FIRST &&
            bestLocal != null &&
            bestLocal.first.kind.isSherpa &&
            bestLocal.first.supportsLocale(locale)
        ) {
            startAudioCapture(locale, localModel = bestLocal, cloud = null)
            return
        }

        if (engineMode == VoiceEngineMode.AUTO_LOCAL_FIRST && strictLanguageLock &&
            readyLanguageTag == KeyboardLocales.specFor(locale).bcp47Tag
        ) {
            startSystemSpeechSession(locale, strictLanguageLock, onDevice = true)
            return
        }
        if (engineMode == VoiceEngineMode.LOCAL_WHISPER || engineMode == VoiceEngineMode.AUTO_LOCAL_FIRST) {
            if (bestLocal != null) {
                startAudioCapture(locale, localModel = bestLocal, cloud = null)
                return
            }
            if (engineMode == VoiceEngineMode.LOCAL_WHISPER) {
                showError(
                    if (de) "Lade unter Diktat ein Offline-Sprachmodell"
                    else "Download an offline model in Dictation settings"
                )
                return
            }
        }
        val onDevice = offlineOnly || engineMode == VoiceEngineMode.ON_DEVICE_SPEECH
        val available = if (onDevice) {
            isOnDeviceSpeechAvailable(context)
        } else {
            SpeechRecognizer.isRecognitionAvailable(context)
        }
        if (available) {
            startSystemSpeechSession(locale, strictLanguageLock, onDevice)
        } else {
            showError(
                if (de) "Android-Diktat fehlt. Lade ein Offline-Sprachmodell"
                else "Android dictation unavailable. Download an offline model"
            )
        }
    }

    private fun showError(message: String) {
        _state.value = VoiceUiState(errorMessage = message)
    }

    private fun startAudioCapture(
        locale: String,
        localModel: Pair<LocalModelSpec, File>?,
        cloud: CloudDictation?,
        liveConfig: GeminiLiveConfig? = null,
        fallbackCloud: CloudDictation? = null,
        fallbackLocalModel: Pair<LocalModelSpec, File>? = null
    ) {
        sessionGeneration++
        val generation = sessionGeneration
        sessionActive = true
        stopRequested = false
        usingLocalAudioCapture = true
        liveFailed = false
        activeLocalModel = localModel
        activeCloud = cloud
        activeFallbackCloud = fallbackCloud
        activeFallbackLocalModel = fallbackLocalModel
        activeSherpaStream = null
        activeSherpaStreamJob = null
        val de = KeyboardLocales.baseLang(locale) == "de"

        fun preview(text: String) {
            if (generation == sessionGeneration && _state.value.isListening) {
                _state.value = _state.value.copy(partialText = text)
            }
        }

        val live = liveConfig?.let { config ->
            GeminiLiveDictation(
                config = config,
                onPreview = { text -> mainHandler.post { if (!liveFailed) preview(text) } },
                onFailure = {
                    mainHandler.post {
                        if (generation == sessionGeneration && sessionActive && !stopRequested) {
                            liveFailed = true
                            activeLive?.close()
                            activeLive = null
                            val hasFallback = activeFallbackCloud != null || activeFallbackLocalModel != null
                            if (hasFallback) {
                                _state.value = _state.value.copy(
                                    isCloud = activeFallbackCloud != null,
                                    isLocalWhisper = activeFallbackCloud == null && activeFallbackLocalModel != null,
                                    engineLabel = activeFallbackCloud?.let { "Cloud" }
                                        ?: activeFallbackLocalModel?.first?.shortEngineLabel,
                                    processingLabel = null
                                )
                            } else {
                                wavRecorder.cancelAndDelete()
                                finishSession()
                                showError(
                                    if (de) "Gemini Live ist nicht erreichbar. Prüfe Netz und Schlüssel."
                                    else "Gemini Live is unavailable. Check your connection and key."
                                )
                            }
                        }
                    }
                }
            )
        }

        val langHint = KeyboardLocales.baseLang(locale).takeIf { activeStrictLanguage && !activeMultilingual }
        val streamingPair = localModel?.takeIf {
            it.first.kind == LocalModelKind.SHERPA_STREAMING && live == null && cloud == null
        }
        val isStreamingSherpa = streamingPair != null

        val preStreamBuffer = if (streamingPair != null) ConcurrentLinkedQueue<ByteArray>() else null
        val streamingDeferred = if (streamingPair != null) {
            controllerScope.async(Dispatchers.Default) {
                val res = SherpaOnnxEngine.createStreamingSession(
                    scope = controllerScope,
                    spec = streamingPair.first,
                    modelDir = streamingPair.second,
                    languageCode = if (activeMultilingual) "auto" else KeyboardLocales.baseLang(locale),
                    onPreview = { text -> mainHandler.post { preview(text) } }
                )
                val session = res.getOrNull()
                if (session != null && generation == sessionGeneration && sessionActive) {
                    activeSherpaStream = session
                    while (true) {
                        val queued = preStreamBuffer?.poll() ?: break
                        session.offerPcm(queued, queued.size)
                    }
                } else {
                    preStreamBuffer?.clear()
                }
                session
            }
        } else null
        activeSherpaStreamJob = streamingDeferred

        val incremental = if (!isStreamingSherpa && localModel != null) {
            IncrementalWhisper(
                scope = controllerScope,
                model = localModel.second,
                language = langHint,
                vocabularyHint = activeVocabularyHint,
                modelSpec = localModel.first,
                onPreview = ::preview
            )
        } else null

        activeLive = live
        activeIncremental = incremental
        val wavFile = File(context.cacheDir, "tapwing_voice_${java.util.UUID.randomUUID()}.wav")

        val pcmHook: ((ByteArray, Int) -> Unit)? = when {
            live != null -> { pcmBytes, count -> if (!liveFailed) live.offerPcm(pcmBytes, count) }
            isStreamingSherpa -> { pcmBytes, count ->
                val session = activeSherpaStream
                if (session != null) {
                    session.offerPcm(pcmBytes, count)
                } else if (preStreamBuffer != null && preStreamBuffer.size < 160) {
                    preStreamBuffer.offer(pcmBytes.copyOf(count))
                }
            }
            else -> null
        }

        val softMaxSec = if (localModel?.first?.kind?.isSherpa == true) 14 else 16
        val hardMaxSec = if (localModel?.first?.kind?.isSherpa == true) 18 else 24

        val started = wavRecorder.start(
            outputFile = wavFile,
            onPcm = pcmHook,
            canScheduleChunk = incremental?.let { it::canSchedule },
            onSpeechChunk = incremental?.let { it::offer },
            chunkSoftMaxSeconds = softMaxSec,
            chunkHardMaxSeconds = hardMaxSec,
            onReady = {
                mainHandler.post {
                    if (generation == sessionGeneration && _state.value.isListening) {
                        _state.value = _state.value.copy(processingLabel = null)
                    }
                }
            },
            onRmsDb = { db ->
                mainHandler.post {
                    if (generation == sessionGeneration && _state.value.isListening) {
                        _state.value = _state.value.copy(rmsDb = db)
                    }
                }
            },
            onElapsedSeconds = { seconds ->
                mainHandler.post {
                    if (generation == sessionGeneration && _state.value.isListening) {
                        _state.value = _state.value.copy(elapsedSeconds = seconds)
                        if (seconds >= MAX_RECORDING_SECONDS) finishAndCommit()
                    }
                }
            },
            onError = {
                mainHandler.post {
                    if (generation == sessionGeneration && sessionActive) {
                        wavRecorder.cancelAndDelete()
                        finishSession()
                        showError(if (de) "Mikrofon nicht verfügbar" else "Microphone unavailable")
                    }
                }
            }
        )
        if (!started) {
            finishSession()
            showError(if (de) "Mikrofon wird noch freigegeben" else "Microphone is still closing")
            return
        }
        val engineLabel = when {
            live != null -> "Gemini Live"
            cloud != null -> "Cloud"
            localModel != null -> localModel.first.shortEngineLabel
            else -> if (de) "Lokal" else "Local"
        }
        _state.value = VoiceUiState(
            isListening = true,
            canPause = true,
            isLocalWhisper = localModel != null,
            isCloud = cloud != null || live != null,
            processingLabel = if (de) "Mikrofon startet …" else "Starting microphone…",
            activeLocaleTag = KeyboardLocales.specFor(locale).bcp47Tag,
            engineLabel = engineLabel,
            isMultilingual = activeMultilingual,
            multilingualLabel = activeMultilingualLabel
        )
        if (localModel != null && incremental == null && streamingDeferred == null) {
            controllerScope.launch {
                LocalInferenceEngine.warmSpeechModel(localModel.first, localModel.second, langHint)
            }
        }
    }

    private fun startSystemSpeechSession(locale: String, strictLanguageLock: Boolean, onDevice: Boolean) {
        useOfflineAndroidSession = onDevice
        sessionGeneration++
        sessionActive = true
        stopRequested = false
        usingLocalAudioCapture = false
        activeLocalModel = null
        activeCloud = null
        activeFallbackCloud = null
        activeFallbackLocalModel = null
        accumulated.setLength(0)
        lastCommittedSnapshot = ""
        segmentPartial = ""
        consecutiveSegmentErrors = 0
        systemSpeechStartedAtMs = SystemClock.elapsedRealtime()
        mainHandler.removeCallbacksAndMessages(null)
        _state.value = VoiceUiState(
            isListening = true,
            canPause = false,
            isAndroidOffline = onDevice,
            elapsedSeconds = 0,
            activeLocaleTag = KeyboardLocales.specFor(locale).bcp47Tag,
            engineLabel = if (onDevice) "Android Offline" else "Android",
            isMultilingual = activeMultilingual,
            multilingualLabel = activeMultilingualLabel
        )
        scheduleSystemSpeechTimer(sessionGeneration)
        startSegment(locale, strictLanguageLock && !activeMultilingual)
    }

    private fun scheduleSystemSpeechTimer(generation: Int) {
        mainHandler.postDelayed({
            if (generation == sessionGeneration && sessionActive && !stopRequested && !usingLocalAudioCapture) {
                val secs = ((SystemClock.elapsedRealtime() - systemSpeechStartedAtMs) / 1000L).toInt()
                if (secs != _state.value.elapsedSeconds) {
                    _state.value = _state.value.copy(elapsedSeconds = secs)
                }
                if (secs >= MAX_RECORDING_SECONDS) {
                    finishAndCommit()
                } else {
                    scheduleSystemSpeechTimer(generation)
                }
            }
        }, 1_000L)
    }

    private fun startSegment(locale: String, strictLanguageLock: Boolean) {
        destroyRecognizerLocked()
        val generation = sessionGeneration
        val languageTag = KeyboardLocales.specFor(locale).bcp47Tag
        val localRecognizer = useOfflineAndroidSession
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
        segmentSpeechEnded = false

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

            override fun onBeginningOfSpeech() {
                if (isCurrent()) {
                    segmentSpeechEnded = false
                    consecutiveSegmentErrors = 0
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (!isCurrent()) return
                _state.value = _state.value.copy(rmsDb = rmsdB.coerceIn(-2f, 12f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (!isCurrent()) return
                segmentSpeechEnded = true
                if (stopRequested) {
                    _state.value = _state.value.copy(isListening = false, isProcessing = true)
                }
            }

            override fun onError(error: Int) {
                if (!isCurrent()) return
                val keepGoing = !stopRequested && error in RESTARTABLE_ERRORS
                if (keepGoing) {
                    appendSegment(segmentPartial)
                    consecutiveSegmentErrors++
                    val backoffMs = when {
                        consecutiveSegmentErrors <= 1 -> 150L
                        consecutiveSegmentErrors == 2 -> 300L
                        else -> 500L
                    }
                    scheduleNextSegment(locale, strictLanguageLock, delayMs = backoffMs)
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
                consecutiveSegmentErrors = 0
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                appendSegment(if (best.isNotEmpty()) best else segmentPartial)
                if (stopRequested) {
                    commitAccumulated()
                } else {
                    scheduleNextSegment(locale, strictLanguageLock, delayMs = 40L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isCurrent()) return
                consecutiveSegmentErrors = 0
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotEmpty()) {
                    if (segmentPartial != text) lastPartialChangedAt = SystemClock.elapsedRealtime()
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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 20_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 20_000L)
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

    private fun joinText(a: String, b: String): String =
        IncrementalWhisper.joinTranscriptSegments(a, b)

    private fun finishSession() {
        sessionActive = false
        stopRequested = false
        usingLocalAudioCapture = false
        liveFailed = false
        activeLocalModel = null
        activeCloud = null
        activeFallbackCloud = null
        activeFallbackLocalModel = null
        activeLive?.close()
        activeLive = null
        activeIncremental?.cancel()
        activeIncremental = null
        activeSherpaStream?.cancel()
        activeSherpaStream = null
        activeSherpaStreamJob?.cancel()
        activeSherpaStreamJob = null
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
        stopStartedAt = SystemClock.elapsedRealtime()
        val generation = sessionGeneration
        _state.value = _state.value.copy(isListening = false, isPaused = false, isProcessing = true)
        if (usingLocalAudioCapture) {
            transcribeCapture(generation)
            return
        }

        val sr = recognizer
        if (sr == null) {
            appendSegment(segmentPartial)
            commitAccumulated()
        } else {
            val speechHadEnded = segmentSpeechEnded
            runCatching { sr.stopListening() }
            val waitMs = when {
                segmentPartial.isBlank() -> 2_200L
                speechHadEnded && SystemClock.elapsedRealtime() - lastPartialChangedAt >= 220L -> 650L
                else -> 1_200L
            }
            mainHandler.postDelayed({
                if (generation == sessionGeneration && sessionActive) {
                    appendSegment(segmentPartial)
                    commitAccumulated()
                }
            }, waitMs)
        }
    }

    private fun transcribeCapture(generation: Int) {
        val localModel = activeLocalModel
        val cloud = activeCloud
        val fallbackCloud = activeFallbackCloud
        val fallbackLocal = activeFallbackLocalModel
        val live = activeLive?.takeUnless { liveFailed }
        val incremental = activeIncremental
        val streamDeferred = activeSherpaStreamJob
        val vocabHint = activeVocabularyHint
        val lang = KeyboardLocales.baseLang(activeLocale)
        val languageHint = lang.takeIf { activeStrictLanguage && !activeMultilingual }
        val de = lang == "de"
        _state.value = _state.value.copy(
            processingLabel = when {
                live != null && de -> "Diktat abschließen …"
                live != null -> "Finishing dictation…"
                cloud != null && de -> "Cloud transkribiert …"
                cloud != null -> "Transcribing in the cloud…"
                de -> "Erkenne blitzschnell lokal …"
                else -> "Transcribing locally…"
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
                showError(
                    if (de) "Keine Sprache erkannt. Sprich nach dem Start."
                    else "No speech detected. Speak after starting."
                )
                return@launch
            }

            var outputFormatted = false
            val result: Result<String> = try {
                when {
                    live != null -> {
                        val liveRes = live.finish()
                        val liveText = liveRes.getOrNull().orEmpty()
                        if (liveRes.isSuccess && liveText.isNotBlank()) {
                            outputFormatted = live.formatsText
                            liveRes
                        } else if (fallbackCloud != null) {
                            val fcRes = fallbackCloud.transcribe(recording.file, languageHint)
                            if (fcRes.isSuccess && fcRes.getOrNull().orEmpty().isNotBlank()) {
                                outputFormatted = fallbackCloud.formatsText
                                fcRes
                            } else if (fallbackLocal != null) {
                                transcribeLocalWavSafely(recording.file, fallbackLocal, languageHint, vocabHint)
                            } else {
                                fcRes
                            }
                        } else if (fallbackLocal != null) {
                            transcribeLocalWavSafely(recording.file, fallbackLocal, languageHint, vocabHint)
                        } else {
                            liveRes
                        }
                    }
                    cloud != null -> {
                        val cloudRes = cloud.transcribe(recording.file, languageHint)
                        if (cloudRes.isSuccess && cloudRes.getOrNull().orEmpty().isNotBlank()) {
                            outputFormatted = cloud.formatsText
                            cloudRes
                        } else if (fallbackCloud != null) {
                            val fcRes = fallbackCloud.transcribe(recording.file, languageHint)
                            if (fcRes.isSuccess && fcRes.getOrNull().orEmpty().isNotBlank()) {
                                outputFormatted = fallbackCloud.formatsText
                                fcRes
                            } else if (fallbackLocal != null) {
                                transcribeLocalWavSafely(recording.file, fallbackLocal, languageHint, vocabHint)
                            } else {
                                fcRes
                            }
                        } else if (fallbackLocal != null) {
                            transcribeLocalWavSafely(recording.file, fallbackLocal, languageHint, vocabHint)
                        } else {
                            cloudRes
                        }
                    }
                    fallbackCloud != null && liveFailed -> {
                        val fcRes = fallbackCloud.transcribe(recording.file, languageHint)
                        if (fcRes.isSuccess && fcRes.getOrNull().orEmpty().isNotBlank()) {
                            outputFormatted = fallbackCloud.formatsText
                            fcRes
                        } else if (fallbackLocal != null) {
                            transcribeLocalWavSafely(recording.file, fallbackLocal, languageHint, vocabHint)
                        } else {
                            fcRes
                        }
                    }
                    fallbackLocal != null && liveFailed -> {
                        transcribeLocalWavSafely(recording.file, fallbackLocal, languageHint, vocabHint)
                    }
                    streamDeferred != null && localModel != null -> {
                        val session = runCatching { streamDeferred.await() }.getOrNull()
                        val streamed = session?.let { runCatching { it.finish() }.getOrNull() }.orEmpty()
                        activeSherpaStream = null
                        if (streamed.isNotBlank()) {
                            Result.success(streamed)
                        } else {
                            transcribeLocalWavSafely(recording.file, localModel, languageHint, vocabHint)
                        }
                    }
                    localModel != null -> {
                        val incText = incremental?.finish()
                        if (!incText.isNullOrBlank()) {
                            Result.success(incText)
                        } else {
                            transcribeLocalWavSafely(recording.file, localModel, languageHint, vocabHint)
                        }
                    }
                    else -> Result.failure(IllegalStateException("Missing speech model"))
                }
            } finally {
                recording.file.delete()
            }
            if (generation != sessionGeneration || !sessionActive) return@launch
            finishSession()
            val text = result.getOrNull().orEmpty()
            when {
                result.isFailure && (cloud != null || live != null || fallbackCloud != null) -> {
                    showError(
                        if (de) "Cloud-Diktat fehlgeschlagen. Prüfe Netz und Schlüssel."
                        else "Cloud dictation failed. Check your connection and API key."
                    )
                }
                result.isFailure -> {
                    showError(
                        if (de) "Offline-Modell fehlgeschlagen. Prüfe den Download."
                        else "Offline model failed. Check the download."
                    )
                }
                text.isBlank() -> showError(
                    if (de) "Keine Sprache erkannt. Versuch es erneut."
                    else "No speech detected. Try again."
                )
                else -> {
                    _state.value = VoiceUiState()
                    onTranscriptReady(text, outputFormatted)
                }
            }
        }
    }

    private suspend fun transcribeLocalWavSafely(
        wavFile: File,
        localModel: Pair<LocalModelSpec, File>,
        languageHint: String?,
        vocabularyHint: String?
    ): Result<String> {
        val (spec, path) = localModel
        val chunkSecs = if (spec.kind.isSherpa) SHERPA_CHUNK_SECONDS else LOCAL_FALLBACK_CHUNK_SECONDS
        val parts = withContext(Dispatchers.IO) {
            WavChunker.splitIfNeeded(wavFile, maxChunkSeconds = chunkSecs)
        }
        val tempParts = parts.filter { it != wavFile }
        return try {
            var stitched = ""
            for (part in parts) {
                val prompt = IncrementalWhisper.buildContextPrompt(vocabularyHint, stitched)
                val res = LocalInferenceEngine.transcribeWav(
                    wavPath = part.absolutePath,
                    spec = spec,
                    modelPath = path,
                    languageCode = languageHint,
                    initialPrompt = prompt
                )
                if (res.isFailure) return res
                val seg = res.getOrNull()?.trim().orEmpty()
                if (seg.isNotEmpty()) {
                    stitched = IncrementalWhisper.joinTranscriptSegments(stitched, seg)
                }
            }
            Result.success(stitched)
        } finally {
            withContext(Dispatchers.IO) {
                tempParts.forEach { it.delete() }
            }
        }
    }

    fun recordInsertionTiming() {
        if (stopStartedAt == 0L) return
        Log.i("TapwingVoice", "Stop to insert (${activeEngineMode.name}): ${SystemClock.elapsedRealtime() - stopStartedAt} ms")
        stopStartedAt = 0L
    }

    fun cancel(keepRotationResume: Boolean = false) {
        stopStartedAt = 0L
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
        warmJob?.cancel()
        supportRecognizer?.destroy()
        supportRecognizer = null
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
        private const val MAX_RECORDING_SECONDS = 3600
        private const val SHERPA_CHUNK_SECONDS = 16
        private const val LOCAL_FALLBACK_CHUNK_SECONDS = 24

        fun isOnDeviceSpeechAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        private val RESTARTABLE_ERRORS = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        )
    }
}
