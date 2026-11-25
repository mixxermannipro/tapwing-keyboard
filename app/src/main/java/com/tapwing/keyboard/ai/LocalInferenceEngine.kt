package com.tapwing.keyboard.ai

import android.util.Log
import com.llamatik.library.platform.WhisperBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thread-safe on-device speech inference engine orchestrating both:
 * - Sherpa-ONNX v2.0.3 (FastConformer, Kroko Live-Streaming, Parakeet Ultra 0.6B, Canary 180M Flash)
 * - Llamatik whisper.cpp (GGML Whisper models for universal 99-language fallback)
 */
object LocalInferenceEngine {

    private const val TAG = "TapwingAI"

    private val whisperMutex = Mutex()

    @Volatile
    private var loadedWhisperPath: String? = null

    suspend fun transcribeWav(
        wavPath: String,
        spec: LocalModelSpec,
        modelPath: File,
        languageCode: String? = null,
        initialPrompt: String? = null,
        translateToEnglish: Boolean = false
    ): Result<String> {
        return when {
            spec.kind.isLlm -> Result.failure(IllegalArgumentException("Model ${spec.id} is an LLM, not a speech model"))
            spec.kind.isSherpa -> SherpaOnnxEngine.transcribeWav(
                wavPath = wavPath,
                spec = spec,
                modelDir = modelPath,
                languageCode = languageCode
            )
            else -> transcribeWav(
                wavPath = wavPath,
                modelFile = modelPath,
                languageCode = languageCode,
                initialPrompt = initialPrompt,
                translateToEnglish = translateToEnglish
            )
        }
    }

    suspend fun warmSpeechModel(
        spec: LocalModelSpec,
        modelPath: File,
        languageCode: String? = null
    ): Result<Unit> {
        return when {
            spec.kind.isLlm -> LocalLlmEngine.warmModel(modelPath)
            spec.kind.isSherpa -> SherpaOnnxEngine.warmModel(spec, modelPath, languageCode)
            else -> warmWhisper(modelPath)
        }
    }

    suspend fun transcribeWav(
        wavPath: String,
        modelFile: File,
        languageCode: String? = null,
        initialPrompt: String? = null,
        translateToEnglish: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        whisperMutex.withLock {
            runCatching {
                if (!modelFile.isFile) error("Whisper model file missing")
                val wav = File(wavPath)
                if (!wav.isFile || wav.length() <= 44L) error("Audio capture is empty")

                loadWhisper(modelFile)

                val tRun = System.currentTimeMillis()
                val langArg = languageCode?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "auto" }
                val raw = if (translateToEnglish) {
                    val json = WhisperBridge.transcribeWavSegments(
                        wavPath = wavPath,
                        language = langArg,
                        initialPrompt = initialPrompt,
                        translate = true,
                        diarize = false
                    )
                    extractSegmentsText(json)
                } else {
                    WhisperBridge.transcribeWav(
                        wavPath = wavPath,
                        language = langArg,
                        initialPrompt = initialPrompt
                    )
                }
                val cleaned = cleanWhisperArtifacts(raw)
                Log.i(
                    TAG,
                    "Whisper transcribe took ${System.currentTimeMillis() - tRun} ms " +
                        "(wav=${wav.length()}B, chars=${cleaned.length})"
                )
                cleaned
            }
        }
    }

    suspend fun warmWhisper(modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        whisperMutex.withLock { runCatching { loadWhisper(modelFile) } }
    }

    private fun loadWhisper(modelFile: File) {
        if (loadedWhisperPath == modelFile.absolutePath) return
        check(modelFile.isFile) { "Whisper model file missing" }
        val start = System.nanoTime()
        releaseWhisper()
        check(WhisperBridge.initModel(modelFile.absolutePath)) { "Could not load the speech model" }
        loadedWhisperPath = modelFile.absolutePath
        Log.i(TAG, "Whisper model loaded in ${(System.nanoTime() - start) / 1_000_000} ms")
    }

    private fun releaseWhisper() {
        if (loadedWhisperPath == null) return
        runCatching { WhisperBridge.release() }
        loadedWhisperPath = null
    }

    fun unloadWhisper() {
        if (!whisperMutex.tryLock()) return
        try {
            releaseWhisper()
        } finally {
            whisperMutex.unlock()
        }
    }

    fun unloadIfModel(path: String) {
        if (whisperMutex.tryLock()) {
            try {
                if (loadedWhisperPath == path) releaseWhisper()
            } finally {
                whisperMutex.unlock()
            }
        }
    }

    fun unloadAll() {
        unloadWhisper()
        SherpaOnnxEngine.unloadAll()
        LocalLlmEngine.unloadAll()
    }

    private fun extractSegmentsText(jsonRaw: String): String {
        return runCatching {
            val obj = org.json.JSONObject(jsonRaw)
            val arr = obj.optJSONArray("segments") ?: return jsonRaw
            buildString {
                for (i in 0 until arr.length()) {
                    val seg = arr.optJSONObject(i)?.optString("text")?.trim().orEmpty()
                    if (seg.isNotEmpty()) {
                        if (isNotEmpty()) append(' ')
                        append(seg)
                    }
                }
            }
        }.getOrDefault(jsonRaw)
    }

    private val BRACKET_ARTIFACT_REGEX = Regex(
        """\[(?:BLANK_AUDIO|SILENCE|MUSIC|APPLAUSE|LAUGHTER|INAUDIBLE|NOISE|MUSIK|STILLE|LACHEN|APPLAUS|GERÄUSCH|STÖHNT)[^\]]*]""",
        RegexOption.IGNORE_CASE
    )
    private val PAREN_ARTIFACT_REGEX = Regex(
        """\((?:Musik|Music|Silence|Stille|Lachen|Laughter|Applaus|Applause|Stöhnt|Groaning|Sighs|Seufzt|Noise|Geräusche?)\)""",
        RegexOption.IGNORE_CASE
    )
    private val MULTI_SPACE_REGEX = Regex("""[ \t]{2,}""")
    private val STOCK_CAPTION_REGEX = Regex(
        """(?:Untertitel\s+der\s+Amara\.org-Community|Vielen\s+Dank\s+für(?:'s|s)?\s+Zuschauen|Thanks?\s+for\s+watching)[.!?]*""",
        RegexOption.IGNORE_CASE
    )

    internal fun cleanWhisperArtifacts(raw: String): String {
        var cleaned = raw
            .replace(BRACKET_ARTIFACT_REGEX, "")
            .replace(PAREN_ARTIFACT_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        if (cleaned.all { !it.isLetterOrDigit() } || STOCK_CAPTION_REGEX.matches(cleaned)) cleaned = ""
        return cleaned
    }
}
