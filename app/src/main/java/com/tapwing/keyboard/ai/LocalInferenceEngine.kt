package com.tapwing.keyboard.ai

import android.util.Log
import com.llamatik.library.platform.LlamaBridge
import com.llamatik.library.platform.WhisperBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thread-safe on-device inference engine wrapping Llamatik's whisper.cpp & llama.cpp native bridges.
 * Keeps active model paths cached so consecutive transcriptions/rewrites execute quickly,
 * while permitting clean release when switching models or under memory pressure.
 */
object LocalInferenceEngine {

    private const val TAG = "TapwingAI"

    private val whisperMutex = Mutex()
    private val llmMutex = Mutex()

    @Volatile
    private var loadedWhisperPath: String? = null
    @Volatile
    private var loadedLlmPath: String? = null

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

    suspend fun generateWithLocalLlm(
        modelFile: File,
        spec: LocalModelSpec,
        systemPrompt: String,
        userText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        llmMutex.withLock {
            runCatching {
                if (!modelFile.isFile) error("Local LLM model file missing")

                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                val t0 = System.currentTimeMillis()
                if (loadedLlmPath != modelFile.absolutePath) {
                    runCatching { LlamaBridge.shutdown() }
                    // Apply context/thread/batch settings before and after init so both init-time
                    // and generate-time params in Llamatik pick them up.
                    runCatching {
                        LlamaBridge.updateGenerateParams(
                            temperature = 0.15f,
                            maxTokens = 256,
                            topP = 0.9f,
                            topK = 40,
                            repeatPenalty = 1.05f,
                            contextLength = 1024,
                            numThreads = threads,
                            useMmap = true,
                            flashAttention = false,
                            batchSize = 512,
                            gpuLayers = 0
                        )
                    }
                    val ok = LlamaBridge.initGenerateModel(modelFile.absolutePath)
                    if (!ok) {
                        loadedLlmPath = null
                        error("Failed to initialize local LLM (${modelFile.name})")
                    }
                    loadedLlmPath = modelFile.absolutePath
                    runCatching {
                        LlamaBridge.updateGenerateParams(
                            temperature = 0.15f,
                            maxTokens = 256,
                            topP = 0.9f,
                            topK = 40,
                            repeatPenalty = 1.05f,
                            contextLength = 1024,
                            numThreads = threads,
                            useMmap = true,
                            flashAttention = false,
                            batchSize = 512,
                            gpuLayers = 0
                        )
                    }
                    Log.i(TAG, "LLM init (${spec.id}, threads=$threads) took ${System.currentTimeMillis() - t0} ms")
                }

                val tRun = System.currentTimeMillis()
                runCatching { LlamaBridge.sessionReset() }
                val effectiveUser = if (spec.supportsNoThink) {
                    "/no_think\n$userText"
                } else {
                    userText
                }
                val formatted = runCatching {
                    LlamaBridge.applyChatTemplate(
                        messages = listOf(
                            "system" to systemPrompt,
                            "user" to effectiveUser
                        ),
                        addAssistantPrefix = true
                    )
                }.getOrNull()

                val rawOutput = if (!formatted.isNullOrBlank()) {
                    LlamaBridge.generate(formatted)
                } else {
                    LlamaBridge.generateWithContext(systemPrompt, "", effectiveUser)
                }
                val cleaned = CleanupPromptBuilder.sanitizeModelOutput(rawOutput)
                Log.i(
                    TAG,
                    "LLM generate (${spec.id}) took ${System.currentTimeMillis() - tRun} ms " +
                        "(rawChars=${rawOutput.length}, cleanChars=${cleaned.length})"
                )
                if (cleaned.isBlank()) error("Local LLM returned empty output")
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

    private fun releaseLlm() {
        if (loadedLlmPath == null) return
        runCatching { LlamaBridge.shutdown() }
        loadedLlmPath = null
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
        if (llmMutex.tryLock()) {
            try {
                if (loadedLlmPath == path) releaseLlm()
            } finally {
                llmMutex.unlock()
            }
        }
    }

    fun unloadAll() {
        unloadWhisper()
        if (!llmMutex.tryLock()) return
        try {
            releaseLlm()
        } finally {
            llmMutex.unlock()
        }
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

    internal fun cleanWhisperArtifacts(raw: String): String {
        var cleaned = raw
            .replace(Regex("""\[(?:BLANK_AUDIO|SILENCE|MUSIC|APPLAUSE|LAUGHTER|INAUDIBLE|NOISE|MUSIK|STILLE|LACHEN|APPLAUS|GERÄUSCH|STÖHNT)[^\]]*]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\((?:Musik|Music|Silence|Stille|Lachen|Laughter|Applaus|Applause|Stöhnt|Groaning|Sighs|Seufzt|Noise|Geräusche?)\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()
        val stockCaption = Regex(
            """(?:Untertitel\s+der\s+Amara\.org-Community|Vielen\s+Dank\s+für(?:'s|s)?\s+Zuschauen|Thanks?\s+for\s+watching)[.!?]*""",
            RegexOption.IGNORE_CASE
        )
        if (cleaned.all { !it.isLetterOrDigit() } || stockCaption.matches(cleaned)) cleaned = ""
        return cleaned
    }
}
