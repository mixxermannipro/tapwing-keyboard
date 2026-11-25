package com.tapwing.keyboard.ai

import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.KeyboardLocales
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

sealed interface CloudAiResult {
    data class Success(val text: String, val providerLabel: String) : CloudAiResult
    data class Failure(val message: String) : CloudAiResult
}

/**
 * Clean REST client for user-configured Gemini, Groq, OpenRouter, xAI Grok, and OpenAI-compatible endpoints.
 * API keys are transmitted strictly via request headers, never logged or embedded in URLs.
 */
object CloudAiClient {

    private const val TIMEOUT_MS = 20_000
    private const val FAST_CONNECT_TIMEOUT_MS = 5_000
    private const val FAST_READ_TIMEOUT_MS = 10_000
    const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    const val XAI_ENDPOINT = "https://api.x.ai/api/v1/chat/completions"
    const val OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions"
    const val DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile"

    suspend fun rewrite(
        text: String,
        action: WritingAction,
        locale: String,
        provider: AiProviderType,
        apiKey: String,
        model: String,
        customEndpoint: String,
        customCleanupPrompt: String = "",
        useSystemLanguageForCleanup: Boolean = true,
        customVocabulary: Set<String> = emptySet(),
        autoBulletLists: Boolean = true
    ): CloudAiResult = withContext(Dispatchers.IO) {
        if (provider == AiProviderType.LOCAL_RULES ||
            provider == AiProviderType.GEMINI_NANO ||
            provider == AiProviderType.LOCAL_LLM
        ) {
            return@withContext CloudAiResult.Success(
                LocalWritingAssistant.transform(text, action, locale),
                "On-Device"
            )
        }
        if (apiKey.isBlank()) {
            return@withContext CloudAiResult.Failure("No API key configured")
        }
        val prompt = buildPrompt(
            text = text,
            action = action,
            locale = locale,
            customCleanupPrompt = customCleanupPrompt,
            useSystemLanguageForCleanup = useSystemLanguageForCleanup,
            customVocabulary = customVocabulary,
            autoBulletLists = autoBulletLists
        )
        val fastMode = action == WritingAction.VOICE_CLEANUP
        return@withContext when (provider) {
            AiProviderType.GROQ_API -> callGroq(apiKey, model, prompt, fastMode)
            AiProviderType.GEMINI_API -> callGemini(apiKey, model, prompt, fastMode)
            AiProviderType.OPENROUTER_API -> callOpenRouter(apiKey, model, prompt, fastMode)
            AiProviderType.XAI_API -> callXai(apiKey, model, prompt, fastMode)
            AiProviderType.OPENAI_COMPATIBLE -> callOpenAi(apiKey, model, customEndpoint, prompt, fastMode)
            else -> CloudAiResult.Success(LocalWritingAssistant.transform(text, action, locale), "On-Device")
        }
    }

    suspend fun testConnection(
        provider: AiProviderType,
        apiKey: String,
        model: String,
        customEndpoint: String,
        locale: String
    ): CloudAiResult {
        val sample = if (KeyboardLocales.baseLang(locale) == "de") "Hallo Welt" else "Hello world"
        val effectiveModel = model.trim().ifEmpty { AiModelCatalog.defaultTextModelFor(provider) }
        return rewrite(sample, WritingAction.PROOFREAD, locale, provider, apiKey, effectiveModel, customEndpoint)
    }

    fun buildPrompt(
        text: String,
        action: WritingAction,
        locale: String,
        customCleanupPrompt: String = "",
        useSystemLanguageForCleanup: Boolean = true,
        customVocabulary: Set<String> = emptySet(),
        autoBulletLists: Boolean = true
    ): String {
        if (action == WritingAction.VOICE_CLEANUP) {
            val sys = CleanupPromptBuilder.resolveSystemPrompt(
                customPrompt = customCleanupPrompt,
                useSystemLanguage = useSystemLanguageForCleanup,
                activeKeyboardLocale = locale,
                customVocabulary = customVocabulary,
                autoBulletLists = autoBulletLists
            )
            return "$sys\n\nTranscript:\n$text"
        }
        val sys = CleanupPromptBuilder.buildWritingActionSystemPrompt(
            action = action,
            locale = locale,
            customVocabulary = customVocabulary
        )
        return "$sys\n\nText:\n$text"
    }

    internal fun isValidHttpUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        return runCatching {
            val u = URL(trimmed)
            (u.protocol == "https" || u.protocol == "http") && u.host.isNotBlank()
        }.getOrDefault(false)
    }

    private fun readStreamSafely(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
    }

    private fun executeWithTransientRetry(block: () -> Pair<Int, CloudAiResult>): CloudAiResult {
        var attempt = 0
        while (true) {
            val (status, result) = block()
            if (result is CloudAiResult.Success) return result
            val isTransient = status == -1 || status == 429 || status in 500..599
            if (attempt >= 1 || !isTransient) return result
            attempt++
            runCatching { Thread.sleep(RETRY_DELAY_MS) }
        }
    }

    private fun callGemini(apiKey: String, model: String, prompt: String, fastMode: Boolean): CloudAiResult {
        val cleanModel = model.trim().ifEmpty { "gemini-3.8-flash" }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent"
        val genConfig = JSONObject().put("temperature", if (fastMode) 0.1 else 0.25)
        if (fastMode && cleanModel.startsWith("gemini-")) {
            val thinking = if (cleanModel.contains("2.5")) {
                JSONObject().put("thinkingBudget", 0)
            } else {
                JSONObject().put("thinkingLevel", "MINIMAL")
            }
            genConfig.put("thinkingConfig", thinking)
        }
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put("generationConfig", genConfig)
        }.toString()

        return executeWithTransientRetry {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = if (fastMode) FAST_CONNECT_TIMEOUT_MS else TIMEOUT_MS
                    readTimeout = if (fastMode) FAST_READ_TIMEOUT_MS else TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("x-goog-api-key", apiKey)
                }
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
                val code = conn.responseCode
                val raw = readStreamSafely(if (code in 200..299) conn.inputStream else conn.errorStream)
                if (code !in 200..299) {
                    return@executeWithTransientRetry code to CloudAiResult.Failure("Gemini HTTP $code")
                }
                val json = JSONObject(raw)
                val candidates = json.optJSONArray("candidates")
                val first = candidates?.optJSONObject(0)
                val content = first?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val out = (0 until (parts?.length() ?: 0)).mapNotNull { idx ->
                    parts?.optJSONObject(idx)?.takeUnless { it.optBoolean("thought") }?.optString("text")
                }.joinToString("").trim()
                if (out.isEmpty()) {
                    code to CloudAiResult.Failure("Empty Gemini response")
                } else {
                    code to CloudAiResult.Success(CleanupPromptBuilder.sanitizeModelOutput(out), "Gemini ($cleanModel)")
                }
            } catch (ex: Exception) {
                val status = if (ex is java.io.IOException) -1 else 0
                status to CloudAiResult.Failure(ex.localizedMessage ?: "Network error")
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun callGroq(apiKey: String, model: String, prompt: String, fastMode: Boolean): CloudAiResult {
        val cleanModel = model.trim().ifEmpty { DEFAULT_GROQ_MODEL }
        return callOpenAiCompatible(
            apiKey = apiKey,
            model = cleanModel,
            endpoint = GROQ_ENDPOINT,
            prompt = prompt,
            fastMode = fastMode,
            label = "Groq ($cleanModel)"
        )
    }

    private fun callOpenRouter(apiKey: String, model: String, prompt: String, fastMode: Boolean): CloudAiResult {
        val cleanModel = model.trim().ifEmpty { AiModelCatalog.defaultTextModelFor(AiProviderType.OPENROUTER_API) }
        return callOpenAiCompatible(
            apiKey = apiKey,
            model = cleanModel,
            endpoint = OPENROUTER_ENDPOINT,
            prompt = prompt,
            fastMode = fastMode,
            label = "OpenRouter ($cleanModel)",
            extraHeaders = mapOf(
                "HTTP-Referer" to "https://tapwing-keyboard.mixxermannipropvp.workers.dev",
                "X-Title" to "Tapwing Keyboard"
            )
        )
    }

    private fun callXai(apiKey: String, model: String, prompt: String, fastMode: Boolean): CloudAiResult {
        val cleanModel = model.trim().ifEmpty { AiModelCatalog.defaultTextModelFor(AiProviderType.XAI_API) }
        return callOpenAiCompatible(
            apiKey = apiKey,
            model = cleanModel,
            endpoint = XAI_ENDPOINT,
            prompt = prompt,
            fastMode = fastMode,
            label = "Grok ($cleanModel)"
        )
    }

    private fun callOpenAi(
        apiKey: String,
        model: String,
        customEndpoint: String,
        prompt: String,
        fastMode: Boolean
    ): CloudAiResult {
        val cleanModel = model.trim().ifEmpty { "gpt-4.1-mini" }
        val base = customEndpoint.trim().ifEmpty { OPENAI_ENDPOINT }
        if (!isValidHttpUrl(base)) {
            return CloudAiResult.Failure("Invalid custom endpoint URL")
        }
        return callOpenAiCompatible(
            apiKey = apiKey,
            model = cleanModel,
            endpoint = base,
            prompt = prompt,
            fastMode = fastMode,
            label = "API ($cleanModel)"
        )
    }

    private fun callOpenAiCompatible(
        apiKey: String,
        model: String,
        endpoint: String,
        prompt: String,
        fastMode: Boolean,
        label: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): CloudAiResult {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", if (fastMode) 0.1 else 0.25)
            if (fastMode) put("max_tokens", 512)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", prompt)
                )
            )
        }.toString()

        return executeWithTransientRetry {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = if (fastMode) FAST_CONNECT_TIMEOUT_MS else TIMEOUT_MS
                    readTimeout = if (fastMode) FAST_READ_TIMEOUT_MS else TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
                val code = conn.responseCode
                val raw = readStreamSafely(if (code in 200..299) conn.inputStream else conn.errorStream)
                if (code !in 200..299) {
                    return@executeWithTransientRetry code to CloudAiResult.Failure("API HTTP $code")
                }
                val json = JSONObject(raw)
                val choices = json.optJSONArray("choices")
                val message = choices?.optJSONObject(0)?.optJSONObject("message")
                val out = message?.optString("content")?.trim().orEmpty()
                if (out.isEmpty()) {
                    code to CloudAiResult.Failure("Empty API response")
                } else {
                    code to CloudAiResult.Success(CleanupPromptBuilder.sanitizeModelOutput(out), label)
                }
            } catch (ex: Exception) {
                val status = if (ex is java.io.IOException) -1 else 0
                status to CloudAiResult.Failure(ex.localizedMessage ?: "Network error")
            } finally {
                conn?.disconnect()
            }
        }
    }

    private const val RETRY_DELAY_MS = 350L
}
