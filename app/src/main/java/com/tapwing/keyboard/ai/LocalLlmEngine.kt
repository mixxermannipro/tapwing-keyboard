package com.tapwing.keyboard.ai

import android.util.Log
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Thread-safe, mobile-tuned on-device GGUF LLM inference engine backed by Llamatik (llama.cpp).
 * Optimized for sub-2-second smartphone rewriting & dictation cleanup with LFM2.5, Qwen 3.5, and Gemma 3.
 */
object LocalLlmEngine {

    private const val TAG = "TapwingLocalLLM"
    private const val CONTEXT_LENGTH = 2048
    private const val BATCH_SIZE = 256
    private const val INFER_TIMEOUT_MS = 22_000L

    private val llmMutex = Mutex()

    @Volatile
    private var loadedModelPath: String? = null

    private fun optimalThreads(): Int =
        (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    suspend fun warmModel(modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        llmMutex.withLock {
            runCatching { ensureLoadedLocked(modelFile) }
        }
    }

    suspend fun processText(
        text: String,
        action: WritingAction,
        locale: String,
        spec: LocalModelSpec,
        modelFile: File,
        customCleanupPrompt: String = "",
        useSystemLanguageForCleanup: Boolean = true,
        customVocabulary: Set<String> = emptySet(),
        autoBulletLists: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.success("")
        llmMutex.withLock {
            runCatching {
                ensureLoadedLocked(modelFile)

                val fastDeterministic = action == WritingAction.VOICE_CLEANUP || action == WritingAction.PROOFREAD
                val maxTokens = computeMaxTokens(text, action)
                LlamaBridge.updateGenerateParams(
                    if (fastDeterministic) 0.10f else 0.25f,
                    maxTokens,
                    0.90f,
                    40,
                    1.08f,
                    CONTEXT_LENGTH,
                    optimalThreads(),
                    true,
                    true,
                    BATCH_SIZE,
                    0
                )

                val systemPrompt = CleanupPromptBuilder.buildWritingActionSystemPrompt(
                    action = action,
                    locale = locale,
                    customCleanupPrompt = customCleanupPrompt,
                    useSystemLanguageForCleanup = useSystemLanguageForCleanup,
                    customVocabulary = customVocabulary,
                    autoBulletLists = autoBulletLists
                )
                val userPrompt = buildUserMessage(
                    text = text,
                    action = action,
                    disableThinking = spec.supportsNoThinkTag
                )
                val formattedPrompt = formatPrompt(systemPrompt, userPrompt)

                val t0 = System.currentTimeMillis()
                val raw = try {
                    withTimeoutOrNull(INFER_TIMEOUT_MS) {
                        LlamaBridge.generate(formattedPrompt)
                    } ?: run {
                        runCatching { LlamaBridge.nativeCancelGenerate() }
                        error("Local LLM timeout after ${INFER_TIMEOUT_MS}ms")
                    }
                } catch (ce: CancellationException) {
                    runCatching { LlamaBridge.nativeCancelGenerate() }
                    throw ce
                }

                val cleaned = CleanupPromptBuilder.sanitizeModelOutput(raw)
                val elapsed = System.currentTimeMillis() - t0
                Log.i(TAG, "LocalLLM(${spec.id}, $action) finished in $elapsed ms (in=${text.length}c, out=${cleaned.length}c)")
                check(cleaned.isNotBlank()) { "Local LLM returned empty response" }
                cleaned
            }
        }
    }

    private val WHITESPACE_REGEX = Regex("""\s+""")

    internal fun computeMaxTokens(input: String, action: WritingAction): Int {
        val approxWords = input.trim().split(WHITESPACE_REGEX).count { it.isNotBlank() }.coerceAtLeast(1)
        val multiplier = when (action) {
            WritingAction.CONCISE, WritingAction.SUMMARIZE -> 1.4
            WritingAction.VOICE_CLEANUP, WritingAction.PROOFREAD -> 1.8
            else -> 2.2
        }
        return ((approxWords * multiplier).toInt() + 64).coerceIn(96, 640)
    }

    internal fun buildUserMessage(text: String, action: WritingAction, disableThinking: Boolean): String {
        val header = if (action == WritingAction.VOICE_CLEANUP) "Transcript:" else "Text:"
        val noThinkSuffix = if (disableThinking) "\n/no_think" else ""
        return "$header\n${text.trim()}$noThinkSuffix"
    }

    internal fun buildChatMlFallback(systemPrompt: String, userPrompt: String): String {
        return "<|im_start|>system\n$systemPrompt<|im_end|>\n" +
            "<|im_start|>user\n$userPrompt<|im_end|>\n" +
            "<|im_start|>assistant\n"
    }

    private fun formatPrompt(systemPrompt: String, userPrompt: String): String {
        val templated = runCatching {
            LlamaBridge.applyChatTemplate(
                listOf("system" to systemPrompt, "user" to userPrompt),
                true
            )
        }.getOrNull()
        return if (!templated.isNullOrBlank()) templated else buildChatMlFallback(systemPrompt, userPrompt)
    }

    private fun ensureLoadedLocked(modelFile: File) {
        if (loadedModelPath == modelFile.absolutePath) return
        check(modelFile.isFile && modelFile.length() > 1_000_000L) { "Local LLM model file missing or incomplete" }
        val t0 = System.nanoTime()
        releaseLocked()
        LlamaBridge.updateGenerateParams(
            0.15f,
            256,
            0.90f,
            40,
            1.08f,
            CONTEXT_LENGTH,
            optimalThreads(),
            true,
            true,
            BATCH_SIZE,
            0
        )
        check(LlamaBridge.initGenerateModel(modelFile.absolutePath)) {
            "Could not load local GGUF model ${modelFile.name}"
        }
        loadedModelPath = modelFile.absolutePath
        Log.i(TAG, "Loaded local LLM ${modelFile.name} in ${(System.nanoTime() - t0) / 1_000_000} ms")
    }

    private fun releaseLocked() {
        if (loadedModelPath == null) return
        runCatching { LlamaBridge.shutdown() }
        loadedModelPath = null
    }

    fun unloadIfModel(path: String) {
        if (!llmMutex.tryLock()) return
        try {
            if (loadedModelPath == path) releaseLocked()
        } finally {
            llmMutex.unlock()
        }
    }

    fun unloadAll() {
        if (!llmMutex.tryLock()) return
        try {
            releaseLocked()
        } finally {
            llmMutex.unlock()
        }
    }
}
