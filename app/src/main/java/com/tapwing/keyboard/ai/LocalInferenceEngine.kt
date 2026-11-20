package com.tapwing.keyboard.ai

import com.llamatik.library.platform.LlamaBridge
import com.llamatik.library.platform.WhisperBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thread-safe on-device inference engine wrapping Llamatik's whisper.cpp & llama.cpp native bridges.
 * Keeps active model paths cached so consecutive transcriptions/rewrites execute immediately,
 * while permitting clean release when switching models or freeing memory.
 */
object LocalInferenceEngine {

    private val whisperMutex = Mutex()
    private val llmMutex = Mutex()

    private var loadedWhisperPath: String? = null
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
                if (loadedWhisperPath != modelFile.absolutePath) {
                    runCatching { WhisperBridge.release() }
                    val ok = WhisperBridge.initModel(modelFile.absolutePath)
                    if (!ok) {
                        loadedWhisperPath = null
                        error("Failed to initialize Whisper model")
                    }
                    loadedWhisperPath = modelFile.absolutePath
                }

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
                cleanWhisperArtifacts(raw)
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
                if (loadedLlmPath != modelFile.absolutePath) {
                    runCatching { LlamaBridge.shutdown() }
                    val ok = LlamaBridge.initGenerateModel(modelFile.absolutePath)
                    if (!ok) {
                        loadedLlmPath = null
                        error("Failed to initialize local LLM")
                    }
                    loadedLlmPath = modelFile.absolutePath
                    val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
                    LlamaBridge.updateGenerateParams(
                        temperature = 0.15f,
                        maxTokens = 512,
                        topP = 0.9f,
                        topK = 40,
                        repeatPenalty = 1.05f,
                        contextLength = 2048,
                        numThreads = threads,
                        useMmap = true,
                        flashAttention = false,
                        batchSize = 256,
                        gpuLayers = 0
                    )
                }

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
                if (cleaned.isBlank()) error("Local LLM returned empty output")
                cleaned
            }
        }
    }

    fun unloadIfModel(path: String) {
        if (loadedWhisperPath == path) {
            runCatching { WhisperBridge.release() }
            loadedWhisperPath = null
        }
        if (loadedLlmPath == path) {
            runCatching { LlamaBridge.shutdown() }
            loadedLlmPath = null
        }
    }

    fun unloadAll() {
        if (loadedWhisperPath != null) {
            runCatching { WhisperBridge.release() }
            loadedWhisperPath = null
        }
        if (loadedLlmPath != null) {
            runCatching { LlamaBridge.shutdown() }
            loadedLlmPath = null
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

    private fun cleanWhisperArtifacts(raw: String): String {
        return raw
            .replace(Regex("""\[(?:BLANK_AUDIO|SILENCE|MUSIC|APPLAUSE|LAUGHTER)]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\(\s*(?:Musik|Music|Silence|Stille)\s*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()
    }
}
