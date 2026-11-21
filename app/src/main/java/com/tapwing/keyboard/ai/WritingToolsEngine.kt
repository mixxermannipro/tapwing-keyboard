package com.tapwing.keyboard.ai

import android.content.Context
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore

data class WritingToolsOutput(
    val rewrittenText: String,
    val badge: String,
    val fallbackNote: String? = null
)

class WritingToolsEngine(
    private val secureKeyStorage: SecureKeyStorage,
    private val dictionaryStore: AdaptiveDictionaryStore? = null,
    @Suppress("UNUSED_PARAMETER") modelManager: LocalModelManager? = null,
    private val appContext: Context? = null
) {

    suspend fun runAction(
        sourceText: String,
        action: WritingAction,
        prefs: TapwingPrefs
    ): WritingToolsOutput {
        val locale = prefs.activeLocale
        if (sourceText.isBlank()) {
            return WritingToolsOutput("", "On-Device")
        }

        if (prefs.aiProvider == AiProviderType.GEMINI_NANO) {
            val ctx = appContext
            if (ctx != null) {
                val nano = GeminiNanoClient.processText(ctx, sourceText, action, locale)
                if (nano.isSuccess) {
                    return WritingToolsOutput(
                        rewrittenText = nano.getOrThrow(),
                        badge = "Gemini Nano (AICore)"
                    )
                }
            }
            val local = LocalWritingAssistant.transform(sourceText, action, locale, dictionaryStore)
            return WritingToolsOutput(
                rewrittenText = local,
                badge = "On-Device",
                fallbackNote = if (KeyboardLocales.baseLang(locale) == "de") {
                    "Gemini Nano auf diesem Gerät nicht verfügbar — Offline-Regeln genutzt"
                } else {
                    "Gemini Nano unavailable on this device — used local rules"
                }
            )
        }

        if (prefs.aiProvider in CLOUD_PROVIDERS) {
            val apiKey = secureKeyStorage.readApiKey()
            if (apiKey.isNotBlank()) {
                when (
                    val res = CloudAiClient.rewrite(
                        text = sourceText,
                        action = action,
                        locale = locale,
                        provider = prefs.aiProvider,
                        apiKey = apiKey,
                        model = prefs.aiModel,
                        customEndpoint = prefs.aiCustomEndpoint,
                        customCleanupPrompt = prefs.voiceCleanupPrompt,
                        useSystemLanguageForCleanup = prefs.voiceCleanupUseSystemLanguage
                    )
                ) {
                    is CloudAiResult.Success -> {
                        return WritingToolsOutput(
                            rewrittenText = res.text,
                            badge = res.providerLabel
                        )
                    }
                    is CloudAiResult.Failure -> {
                        val local = LocalWritingAssistant.transform(sourceText, action, locale, dictionaryStore)
                        return WritingToolsOutput(
                            rewrittenText = local,
                            badge = "On-Device (Fallback)",
                            fallbackNote = res.message
                        )
                    }
                }
            } else {
                val local = LocalWritingAssistant.transform(sourceText, action, locale, dictionaryStore)
                return WritingToolsOutput(
                    rewrittenText = local,
                    badge = "On-Device",
                    fallbackNote = if (KeyboardLocales.baseLang(locale) == "de") {
                        "Kein API-Key — Offline-Regeln genutzt"
                    } else {
                        "No API key set — used local rules"
                    }
                )
            }
        }

        val local = LocalWritingAssistant.transform(sourceText, action, locale, dictionaryStore)
        return WritingToolsOutput(
            rewrittenText = local,
            badge = "On-Device"
        )
    }

    /**
     * Cleans up voice dictation text using the configured fast AI backend:
     * 1) Smart-Bypass for ultra-short clean utterances (< 1 ms local)
     * 2) Gemini Nano / Google AICore on supported devices
     * 3) Fast Cloud AI (Groq LPU / Gemini Flash-Lite / OpenAI) with API key
     * 4) Instant offline rule-based NaturalVoiceCleaner fallback
     */
    suspend fun cleanupVoiceTranscript(
        rawTranscript: String,
        prefs: TapwingPrefs,
        ruleCleaned: String
    ): WritingToolsOutput {
        if (rawTranscript.isBlank()) return WritingToolsOutput("", "On-Device")

        if (!prefs.voiceAiPolish || prefs.aiProvider == AiProviderType.LOCAL_RULES ||
            shouldSkipCloudPolish(rawTranscript, ruleCleaned)
        ) {
            return WritingToolsOutput(
                rewrittenText = ruleCleaned,
                badge = "On-Device Rules"
            )
        }

        if (prefs.aiProvider == AiProviderType.GEMINI_NANO) {
            val ctx = appContext
            if (ctx != null) {
                val nano = GeminiNanoClient.processText(
                    context = ctx,
                    text = ruleCleaned.ifBlank { rawTranscript },
                    action = WritingAction.VOICE_CLEANUP,
                    locale = prefs.activeLocale
                )
                if (nano.isSuccess) {
                    val candidate = nano.getOrThrow()
                    if (!looksOverTruncated(rawTranscript, candidate)) {
                        return WritingToolsOutput(
                            rewrittenText = candidate,
                            badge = "Gemini Nano (AICore)"
                        )
                    }
                }
            }
            return WritingToolsOutput(
                rewrittenText = ruleCleaned,
                badge = "On-Device Rules"
            )
        }

        if (prefs.aiProvider in CLOUD_PROVIDERS) {
            val apiKey = secureKeyStorage.readApiKey()
            if (apiKey.isNotBlank()) {
                val res = CloudAiClient.rewrite(
                    text = rawTranscript,
                    action = WritingAction.VOICE_CLEANUP,
                    locale = prefs.activeLocale,
                    provider = prefs.aiProvider,
                    apiKey = apiKey,
                    model = prefs.aiModel,
                    customEndpoint = prefs.aiCustomEndpoint,
                    customCleanupPrompt = prefs.voiceCleanupPrompt,
                    useSystemLanguageForCleanup = prefs.voiceCleanupUseSystemLanguage
                )
                if (res is CloudAiResult.Success && !looksOverTruncated(rawTranscript, res.text)) {
                    return WritingToolsOutput(
                        rewrittenText = res.text,
                        badge = res.providerLabel
                    )
                }
            }
        }

        return WritingToolsOutput(
            rewrittenText = ruleCleaned,
            badge = "On-Device Rules"
        )
    }

    private fun looksOverTruncated(raw: String, cleaned: String): Boolean {
        val rawWords = raw.trim().split(Regex("""\s+""")).count { it.isNotBlank() }
        if (rawWords < 6) return false
        val lower = raw.lowercase()
        val hasShortenCmd = lower.contains("kürzer") || lower.contains("shorter") ||
            lower.contains("zusammenfass") || lower.contains("summarize")
        if (hasShortenCmd) return false
        val cleanWords = cleaned.trim().split(Regex("""\s+""")).count { it.isNotBlank() }
        return cleanWords * 100 < rawWords * 45
    }

    companion object {
        private val CLOUD_PROVIDERS = setOf(
            AiProviderType.GROQ_API,
            AiProviderType.GEMINI_API,
            AiProviderType.OPENAI_COMPATIBLE
        )

        private val BYPASS_BLOCKING_CUES = setOf(
            "äh", "ähm", "öhm", "um", "uh", "erm", "nein", "warte", "meine",
            "besser", "eigentlich", "actually", "wait", "scratch", "bullet",
            "stichpunkt", "komma", "punkt", "fragezeichen"
        )

        /**
         * Avoids a network round-trip when a short 1-4 word phrase already needs no AI semantic rewrite.
         */
        fun shouldSkipCloudPolish(rawTranscript: String, ruleCleaned: String): Boolean {
            val trimmed = rawTranscript.trim()
            if (trimmed.isEmpty() || ruleCleaned.isBlank()) return true
            val words = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (words.size > 4) return false
            return words.none { word ->
                word.lowercase().trim(',', '.', '!', '?') in BYPASS_BLOCKING_CUES
            }
        }
    }
}
