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
 * Clean REST client for user-configured Gemini or OpenAI-compatible endpoints.
 * API keys are transmitted strictly via request headers, never logged or embedded in URLs.
 */
object CloudAiClient {

    private const val TIMEOUT_MS = 20_000

    suspend fun rewrite(
        text: String,
        action: WritingAction,
        locale: String,
        provider: AiProviderType,
        apiKey: String,
        model: String,
        customEndpoint: String,
        customCleanupPrompt: String = "",
        useSystemLanguageForCleanup: Boolean = true
    ): CloudAiResult = withContext(Dispatchers.IO) {
        if (provider == AiProviderType.LOCAL_RULES || provider == AiProviderType.LOCAL_LLM) {
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
            useSystemLanguageForCleanup = useSystemLanguageForCleanup
        )
        return@withContext when (provider) {
            AiProviderType.GEMINI_API -> callGemini(apiKey, model, prompt)
            AiProviderType.OPENAI_COMPATIBLE -> callOpenAi(apiKey, model, customEndpoint, prompt)
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
        return rewrite(sample, WritingAction.PROOFREAD, locale, provider, apiKey, model, customEndpoint)
    }

    fun buildPrompt(
        text: String,
        action: WritingAction,
        locale: String,
        customCleanupPrompt: String = "",
        useSystemLanguageForCleanup: Boolean = true
    ): String {
        if (action == WritingAction.VOICE_CLEANUP) {
            val sys = CleanupPromptBuilder.resolveSystemPrompt(
                customPrompt = customCleanupPrompt,
                useSystemLanguage = useSystemLanguageForCleanup,
                activeKeyboardLocale = locale
            )
            return "$sys\n\nTranscript:\n$text"
        }
        val spec = KeyboardLocales.specFor(locale)
        val langDisplay = Locale.forLanguageTag(spec.bcp47Tag).getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
        val sysLang = KeyboardLocales.systemLanguageDisplayName()
        val instruction = when (action) {
            WritingAction.PROOFREAD -> "Fix spelling, punctuation, and grammar while keeping original meaning and language."
            WritingAction.PROFESSIONAL -> "Rewrite in a polite, clear, professional tone."
            WritingAction.CASUAL -> "Rewrite in a friendly, natural, casual tone."
            WritingAction.CONCISE -> "Make the text shorter and more direct while preserving all facts."
            WritingAction.EMOTIVE -> "Make the text warmer, more expressive, and engaging."
            WritingAction.REPHRASE -> "Rephrase cleanly with fresh wording while keeping the exact intent."
            WritingAction.SUMMARIZE -> "Summarize the key points clearly and concisely as short bullet points."
            WritingAction.TRANSLATE -> "Translate naturally into $langDisplay (or into $sysLang if already in $langDisplay)."
            WritingAction.VOICE_CLEANUP -> ""
        }
        return "Language: $langDisplay (${spec.bcp47Tag}). Task: $instruction Return ONLY the rewritten text with no quotes or explanation.\n\nText:\n$text"
    }

    private fun callGemini(apiKey: String, model: String, prompt: String): CloudAiResult {
        val cleanModel = model.trim().ifEmpty { "gemini-2.5-flash" }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent"
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
            put(
                "generationConfig",
                JSONObject().put("temperature", 0.25)
            )
        }.toString()

        return runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) {
                return@runCatching CloudAiResult.Failure("Gemini HTTP $code")
            }
            val json = JSONObject(raw)
            val candidates = json.optJSONArray("candidates")
            val first = candidates?.optJSONObject(0)
            val content = first?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val out = parts?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
            if (out.isEmpty()) {
                CloudAiResult.Failure("Empty Gemini response")
            } else {
                CloudAiResult.Success(CleanupPromptBuilder.sanitizeModelOutput(out), "Gemini ($cleanModel)")
            }
        }.getOrElse { ex ->
            CloudAiResult.Failure(ex.localizedMessage ?: "Network error")
        }
    }

    private fun callOpenAi(
        apiKey: String,
        model: String,
        customEndpoint: String,
        prompt: String
    ): CloudAiResult {
        val cleanModel = model.trim().ifEmpty { "gpt-4o-mini" }
        val base = customEndpoint.trim().ifEmpty { "https://api.openai.com/v1/chat/completions" }
        val body = JSONObject().apply {
            put("model", cleanModel)
            put("temperature", 0.25)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", prompt)
                )
            )
        }.toString()

        return runCatching {
            val conn = (URL(base).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) {
                return@runCatching CloudAiResult.Failure("API HTTP $code")
            }
            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices")
            val message = choices?.optJSONObject(0)?.optJSONObject("message")
            val out = message?.optString("content")?.trim().orEmpty()
            if (out.isEmpty()) {
                CloudAiResult.Failure("Empty API response")
            } else {
                CloudAiResult.Success(CleanupPromptBuilder.sanitizeModelOutput(out), "API ($cleanModel)")
            }
        }.getOrElse { ex ->
            CloudAiResult.Failure(ex.localizedMessage ?: "Network error")
        }
    }
}
