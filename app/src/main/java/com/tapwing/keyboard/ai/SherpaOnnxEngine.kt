package com.tapwing.keyboard.ai

import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineCanaryModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.voice.AudioSignalProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * Thread-safe on-device speech recognition engine powered by Sherpa-ONNX (v2.0.3).
 * Supports both ultra-fast offline NeMo models (FastConformer DE PC, Parakeet Ultra 0.6B, Canary 180M Flash)
 * and real-time word-by-word streaming transducers (Kroko Zipformer DE).
 */
object SherpaOnnxEngine {

    private const val TAG = "TapwingSherpa"
    private const val SAMPLE_RATE = 16_000
    private val CANARY_LANGS = setOf("de", "en", "fr", "es")

    private val engineMutex = Mutex()

    @Volatile
    private var loadedOfflineKey: String? = null
    private var offlineRecognizer: OfflineRecognizer? = null

    @Volatile
    private var loadedOnlineKey: String? = null
    private var onlineRecognizer: OnlineRecognizer? = null

    private fun optimalThreads(): Int =
        (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    suspend fun warmModel(
        spec: LocalModelSpec,
        modelDir: File,
        languageCode: String? = null
    ): Result<Unit> = withContext(Dispatchers.Default) {
        engineMutex.withLock {
            runCatching {
                when (spec.kind) {
                    LocalModelKind.SHERPA_STREAMING -> {
                        ensureOnlineLocked(spec, modelDir)
                        Unit
                    }
                    LocalModelKind.SHERPA_TRANSDUCER,
                    LocalModelKind.SHERPA_CANARY -> {
                        ensureOfflineLocked(spec, modelDir, languageCode)
                        Unit
                    }
                    LocalModelKind.WHISPER_STT,
                    LocalModelKind.LOCAL_LLM -> Unit
                }
            }
        }
    }

    suspend fun transcribeWav(
        wavPath: String,
        spec: LocalModelSpec,
        modelDir: File,
        languageCode: String? = null
    ): Result<String> = withContext(Dispatchers.Default) {
        engineMutex.withLock {
            runCatching {
                require(LocalModelManager.hasValidSherpaInstallation(spec, modelDir)) {
                    "Sherpa model files missing for ${spec.id}"
                }
                val parsed = AudioSignalProcessor.readWavToNormalizedFloats(File(wavPath))
                if (parsed.samples.isEmpty()) return@runCatching ""
                val t0 = System.currentTimeMillis()
                val text = when (spec.kind) {
                    LocalModelKind.SHERPA_STREAMING -> {
                        val rec = ensureOnlineLocked(spec, modelDir)
                        val stream = rec.createStream("")
                        try {
                            configureStreamingLanguage(spec, stream, languageCode)
                            stream.acceptWaveform(parsed.samples, parsed.sampleRate)
                            stream.acceptWaveform(FloatArray(SAMPLE_RATE / 3), parsed.sampleRate)
                            stream.inputFinished()
                            while (rec.isReady(stream)) {
                                rec.decode(stream)
                            }
                            rec.getResult(stream).text.trim()
                        } finally {
                            runCatching { stream.release() }
                        }
                    }
                    LocalModelKind.SHERPA_TRANSDUCER,
                    LocalModelKind.SHERPA_CANARY -> {
                        val rec = ensureOfflineLocked(spec, modelDir, languageCode)
                        val stream = rec.createStream()
                        try {
                            stream.acceptWaveform(parsed.samples, parsed.sampleRate)
                            rec.decode(stream)
                            rec.getResult(stream).text.trim()
                        } finally {
                            runCatching { stream.release() }
                        }
                    }
                    LocalModelKind.WHISPER_STT,
                    LocalModelKind.LOCAL_LLM -> ""
                }
                val cleaned = cleanTranscript(text)
                Log.i(
                    TAG,
                    "Sherpa(${spec.id}) transcribed ${parsed.samples.size} samples in ${System.currentTimeMillis() - t0} ms -> ${cleaned.length} chars"
                )
                cleaned
            }
        }
    }

    suspend fun createStreamingSession(
        scope: CoroutineScope,
        spec: LocalModelSpec,
        modelDir: File,
        languageCode: String? = null,
        onPreview: (String) -> Unit
    ): Result<SherpaStreamingSession> = withContext(Dispatchers.Default) {
        engineMutex.withLock {
            runCatching {
                require(spec.kind == LocalModelKind.SHERPA_STREAMING) {
                    "Model ${spec.id} is not a streaming model"
                }
                val rec = ensureOnlineLocked(spec, modelDir)
                val stream = rec.createStream("")
                configureStreamingLanguage(spec, stream, languageCode)
                SherpaStreamingSession(
                    scope = scope,
                    recognizer = rec,
                    stream = stream,
                    onPreview = onPreview
                )
            }
        }
    }

    private fun configureStreamingLanguage(
        spec: LocalModelSpec,
        stream: OnlineStream,
        languageCode: String?
    ) {
        if (spec.streamingModelType.isEmpty()) {
            // Auto-detected streaming architectures such as NVIDIA Nemotron 3.5 accept a stream-level language option.
            val lang = languageCode?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "auto"
            runCatching { stream.setOption("language", lang) }
        }
    }

    fun unloadIfModel(modelId: String) {
        if (!engineMutex.tryLock()) return
        try {
            if (loadedOfflineKey?.startsWith("$modelId:") == true || loadedOfflineKey == modelId) {
                releaseOfflineLocked()
            }
            if (loadedOnlineKey == modelId) {
                releaseOnlineLocked()
            }
        } finally {
            engineMutex.unlock()
        }
    }

    fun unloadAll() {
        if (!engineMutex.tryLock()) return
        try {
            releaseOfflineLocked()
            releaseOnlineLocked()
        } finally {
            engineMutex.unlock()
        }
    }

    private fun ensureOfflineLocked(
        spec: LocalModelSpec,
        modelDir: File,
        languageCode: String?
    ): OfflineRecognizer {
        ensureOffMainThread()
        val canaryLang = resolveCanaryLanguage(languageCode)
        val cacheKey = if (spec.kind == LocalModelKind.SHERPA_CANARY) "${spec.id}:$canaryLang" else spec.id
        offlineRecognizer?.let { existing ->
            if (loadedOfflineKey == cacheKey) return existing
        }

        require(LocalModelManager.hasValidSherpaInstallation(spec, modelDir)) {
            "Sherpa model files missing for ${spec.id}"
        }
        val t0 = System.nanoTime()
        releaseOfflineLocked()

        val threads = optimalThreads()
        val tokensPath = File(modelDir, "tokens.txt").absolutePath
        val modelConfig = OfflineModelConfig().apply {
            tokens = tokensPath
            numThreads = threads
            debug = false
            provider = "cpu"
            when (spec.kind) {
                LocalModelKind.SHERPA_TRANSDUCER -> {
                    transducer = OfflineTransducerModelConfig().apply {
                        encoder = File(modelDir, "encoder.int8.onnx").absolutePath
                        decoder = File(modelDir, "decoder.int8.onnx").absolutePath
                        joiner = File(modelDir, "joiner.int8.onnx").absolutePath
                    }
                    modelType = "nemo_transducer"
                }
                LocalModelKind.SHERPA_CANARY -> {
                    canary = OfflineCanaryModelConfig().apply {
                        encoder = File(modelDir, "encoder.int8.onnx").absolutePath
                        decoder = File(modelDir, "decoder.int8.onnx").absolutePath
                        srcLang = canaryLang
                        tgtLang = canaryLang
                        usePnc = true
                    }
                    modelType = "canary"
                }
                else -> error("Unsupported offline Sherpa kind: ${spec.kind}")
            }
        }

        val config = OfflineRecognizerConfig().apply {
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
            this.modelConfig = modelConfig
            decodingMethod = "greedy_search"
        }

        val created = OfflineRecognizer(assetManager = null, config = config)
        offlineRecognizer = created
        loadedOfflineKey = cacheKey
        Log.i(TAG, "Loaded offline Sherpa model $cacheKey in ${(System.nanoTime() - t0) / 1_000_000} ms (threads=$threads)")
        return created
    }

    private fun ensureOnlineLocked(spec: LocalModelSpec, modelDir: File): OnlineRecognizer {
        ensureOffMainThread()
        onlineRecognizer?.let { existing ->
            if (loadedOnlineKey == spec.id) return existing
        }

        require(LocalModelManager.hasValidSherpaInstallation(spec, modelDir)) {
            "Sherpa streaming model files missing for ${spec.id}"
        }
        val t0 = System.nanoTime()
        releaseOnlineLocked()

        val threads = optimalThreads()
        val encoderName = spec.requiredFiles.firstOrNull { it.startsWith("encoder") && it.endsWith(".onnx") } ?: "encoder.onnx"
        val decoderName = spec.requiredFiles.firstOrNull { it.startsWith("decoder") && it.endsWith(".onnx") } ?: "decoder.onnx"
        val joinerName = spec.requiredFiles.firstOrNull { it.startsWith("joiner") && it.endsWith(".onnx") } ?: "joiner.onnx"
        val modelConfig = OnlineModelConfig().apply {
            transducer = OnlineTransducerModelConfig().apply {
                encoder = File(modelDir, encoderName).absolutePath
                decoder = File(modelDir, decoderName).absolutePath
                joiner = File(modelDir, joinerName).absolutePath
            }
            tokens = File(modelDir, "tokens.txt").absolutePath
            numThreads = threads
            debug = false
            provider = "cpu"
            modelType = spec.streamingModelType
        }

        val config = OnlineRecognizerConfig().apply {
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
            this.modelConfig = modelConfig
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0f),
                rule2 = EndpointRule(true, 1.2f, 0f),
                rule3 = EndpointRule(false, 0f, 18.0f)
            )
            enableEndpoint = true
            decodingMethod = "greedy_search"
        }

        val created = OnlineRecognizer(assetManager = null, config = config)
        onlineRecognizer = created
        loadedOnlineKey = spec.id
        Log.i(TAG, "Loaded streaming Sherpa model ${spec.id} in ${(System.nanoTime() - t0) / 1_000_000} ms (threads=$threads)")
        return created
    }

    private fun ensureOffMainThread() {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Sherpa-ONNX model loading must execute on a background dispatcher"
        }
    }

    private fun releaseOfflineLocked() {
        val current = offlineRecognizer
        offlineRecognizer = null
        loadedOfflineKey = null
        runCatching { current?.release() }
    }

    private fun releaseOnlineLocked() {
        val current = onlineRecognizer
        onlineRecognizer = null
        loadedOnlineKey = null
        runCatching { current?.release() }
    }

    private fun resolveCanaryLanguage(languageCode: String?): String {
        val base = languageCode?.let { KeyboardLocales.baseLang(it) } ?: "de"
        return if (base in CANARY_LANGS) base else "en"
    }

    private val MULTI_SPACE_REGEX = Regex("""[ \t]{2,}""")

    internal fun cleanTranscript(raw: String): String =
        raw.replace(MULTI_SPACE_REGEX, " ").trim()

    class SherpaStreamingSession internal constructor(
        scope: CoroutineScope,
        private val recognizer: OnlineRecognizer,
        private val stream: OnlineStream,
        private val onPreview: (String) -> Unit
    ) {
        private val pcmQueue = Channel<FloatArray>(capacity = 96)
        private val committedSegments = StringBuilder()

        @Volatile
        private var currentGain: Float = 1.0f

        @Volatile
        private var cancelled: Boolean = false

        @Volatile
        private var released: Boolean = false

        @Volatile
        private var lastEmitted: String = ""

        private val worker: Job = scope.launch(Dispatchers.Default) {
            try {
                for (chunk in pcmQueue) {
                    if (cancelled) break
                    engineMutex.withLock {
                        if (cancelled || released || onlineRecognizer !== recognizer) return@withLock
                        stream.acceptWaveform(chunk, SAMPLE_RATE)
                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream)
                        }
                        val segment = recognizer.getResult(stream).text.trim()
                        if (recognizer.isEndpoint(stream)) {
                            if (segment.isNotEmpty()) {
                                if (committedSegments.isNotEmpty()) committedSegments.append(' ')
                                committedSegments.append(segment)
                            }
                            recognizer.reset(stream)
                        }
                        val previewText = buildCombinedPreview(if (recognizer.isEndpoint(stream)) "" else segment)
                        if (previewText.isNotEmpty() && previewText != lastEmitted) {
                            lastEmitted = previewText
                            onPreview(previewText)
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Normal session cancellation
            } catch (ex: Exception) {
                Log.w(TAG, "Streaming decode interrupted", ex)
            }
        }

        fun offerPcm(pcmBytes: ByteArray, byteCount: Int) {
            if (cancelled || released) return
            val (floats, rawPeak) = AudioSignalProcessor.pcm16BytesToNormalizedFloats(
                pcmBytes = pcmBytes,
                byteCount = byteCount,
                gain = currentGain
            )
            if (floats.isEmpty()) return
            var peakScaled = 0f
            for (s in floats) {
                val a = abs(s)
                if (a > peakScaled) peakScaled = a
            }
            if (peakScaled > 0.001f) {
                currentGain = AudioSignalProcessor.nextStreamingGain(rawPeak, currentGain)
            }
            pcmQueue.trySend(floats)
        }

        suspend fun finish(): String = withContext(Dispatchers.Default) {
            pcmQueue.close()
            worker.join()
            if (cancelled) return@withContext ""
            engineMutex.withLock {
                if (cancelled || released) return@withContext ""
                try {
                    if (onlineRecognizer === recognizer) {
                        // Feed 0.35s silence tail so Zipformer flushes the final word lookahead window.
                        stream.acceptWaveform(FloatArray(SAMPLE_RATE * 35 / 100), SAMPLE_RATE)
                        stream.inputFinished()
                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream)
                        }
                        val tail = recognizer.getResult(stream).text.trim()
                        if (tail.isNotEmpty()) {
                            if (committedSegments.isNotEmpty()) committedSegments.append(' ')
                            committedSegments.append(tail)
                        }
                    }
                    cleanTranscript(committedSegments.toString())
                } finally {
                    releaseStreamLocked()
                }
            }
        }

        fun cancel() {
            cancelled = true
            pcmQueue.cancel()
            worker.cancel()
            CoroutineScope(Dispatchers.Default).launch {
                runCatching { worker.join() }
                engineMutex.withLock {
                    releaseStreamLocked()
                }
            }
        }

        private fun buildCombinedPreview(currentPartial: String): String {
            val base = committedSegments.toString().trim()
            val tail = currentPartial.trim()
            return when {
                base.isEmpty() -> tail
                tail.isEmpty() -> base
                else -> "$base $tail"
            }
        }

        private fun releaseStreamLocked() {
            if (released) return
            released = true
            runCatching { stream.release() }
        }
    }
}
