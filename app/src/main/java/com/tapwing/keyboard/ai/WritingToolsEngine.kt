package com.tapwing.keyboard.ai

import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import java.util.Locale

data class WritingToolsOutput(
    val rewrittenText: String,
    val badge: String,
    val fallbackNote: String? = null
)

class WritingToolsEngine(
    private val secureKeyStorage: SecureKeyStorage,
    private val dictionaryStore: AdaptiveDictionaryStore? = null,
    private val modelManager: LocalModelManager? = null
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

        if (prefs.aiProvider == AiProviderType.LOCAL_LLM) {
            return runWithLocalLlm(sourceText, action, prefs)
        }

        if (prefs.aiProvider == AiProviderType.GEMINI_API || prefs.aiProvider == AiProviderType.OPENAI_COMPATIBLE) {
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
                val installed = modelManager?.firstInstalledModel(LocalModelKind.CLEANUP_LLM, prefs.selectedLocalLlmId)
                if (installed != null) {
                    return runWithLocalLlm(sourceText, action, prefs)
                }
                val local = LocalWritingAssistant.transform(sourceText, action, locale, dictionaryStore)
                return WritingToolsOutput(
                    rewrittenText = local,
                    badge = "On-Device",
                    fallbackNote = if (KeyboardLocales.baseLang(locale) == "de") {
                        "Kein API-Key — lokale Regeln genutzt"
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
     * Cleans up voice dictation text using the configured AI backend:
     * 1) Local LLM (when prefs.aiProvider == LOCAL_LLM, or Cloud fallback when no API key is configured)
     * 2) Cloud AI (when Gemini/OpenAI provider is chosen with an API key)
     * 3) Instant offline rule-based NaturalVoiceCleaner (when LOCAL_RULES or as fallback)
     */
    suspend fun cleanupVoiceTranscript(
        rawTranscript: String,
        prefs: TapwingPrefs,
        ruleCleaned: String
    ): WritingToolsOutput {
        if (rawTranscript.isBlank()) return WritingToolsOutput("", "On-Device")

        val installed = modelManager?.firstInstalledModel(LocalModelKind.CLEANUP_LLM, prefs.selectedLocalLlmId)

        if (prefs.voiceAiPolish && prefs.aiProvider == AiProviderType.LOCAL_LLM && installed != null) {
            val (spec, file) = installed
            val sysPrompt = CleanupPromptBuilder.resolveSystemPrompt(
                customPrompt = prefs.voiceCleanupPrompt,
                useSystemLanguage = prefs.voiceCleanupUseSystemLanguage,
                activeKeyboardLocale = prefs.activeLocale,
                customVocabulary = dictionaryStore?.allKnownVocabularyWords().orEmpty()
            )
            val llmRes = LocalInferenceEngine.generateWithLocalLlm(
                modelFile = file,
                spec = spec,
                systemPrompt = sysPrompt,
                userText = rawTranscript
            )
            if (llmRes.isSuccess) {
                val candidate = llmRes.getOrThrow()
                if (!looksOverTruncated(rawTranscript, candidate)) {
                    return WritingToolsOutput(
                        rewrittenText = candidate,
                        badge = "${spec.displayName} (Local)"
                    )
                }
            }
        }

        if (prefs.voiceAiPolish &&
            (prefs.aiProvider == AiProviderType.GEMINI_API || prefs.aiProvider == AiProviderType.OPENAI_COMPATIBLE)
        ) {
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
                if (res is CloudAiResult.Success) {
                    return WritingToolsOutput(
                        rewrittenText = res.text,
                        badge = res.providerLabel
                    )
                }
            } else if (installed != null) {
                val (spec, file) = installed
                val sysPrompt = CleanupPromptBuilder.resolveSystemPrompt(
                    customPrompt = prefs.voiceCleanupPrompt,
                    useSystemLanguage = prefs.voiceCleanupUseSystemLanguage,
                    activeKeyboardLocale = prefs.activeLocale,
                    customVocabulary = dictionaryStore?.allKnownVocabularyWords().orEmpty()
                )
                val llmRes = LocalInferenceEngine.generateWithLocalLlm(
                    modelFile = file,
                    spec = spec,
                    systemPrompt = sysPrompt,
                    userText = rawTranscript
                )
                if (llmRes.isSuccess) {
                    val candidate = llmRes.getOrThrow()
                    if (!looksOverTruncated(rawTranscript, candidate)) {
                        return WritingToolsOutput(
                            rewrittenText = candidate,
                            badge = "${spec.displayName} (Local)"
                        )
                    }
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
        val hasShortenCmd = lower.contains("kürzer") || lower.contains("shorter") || lower.contains("zusammenfass") || lower.contains("summarize")
        if (hasShortenCmd) return false
        val cleanWords = cleaned.trim().split(Regex("""\s+""")).count { it.isNotBlank() }
        return cleanWords * 100 < rawWords * 45
    }

    private suspend fun runWithLocalLlm(
        sourceText: String,
        action: WritingAction,
        prefs: TapwingPrefs
    ): WritingToolsOutput {
        val installed = modelManager?.firstInstalledModel(LocalModelKind.CLEANUP_LLM, prefs.selectedLocalLlmId)
        if (installed == null) {
            val local = LocalWritingAssistant.transform(sourceText, action, prefs.activeLocale, dictionaryStore)
            return WritingToolsOutput(
                rewrittenText = local,
                badge = "On-Device Rules",
                fallbackNote = if (KeyboardLocales.baseLang(prefs.activeLocale) == "de") {
                    "Kein lokales KI-Modell geladen — Regeln genutzt"
                } else {
                    "No local LLM downloaded — used rules"
                }
            )
        }

        val (spec, file) = installed
        val sysPrompt = if (action == WritingAction.VOICE_CLEANUP) {
            CleanupPromptBuilder.resolveSystemPrompt(
                customPrompt = prefs.voiceCleanupPrompt,
                useSystemLanguage = prefs.voiceCleanupUseSystemLanguage,
                activeKeyboardLocale = prefs.activeLocale,
                customVocabulary = dictionaryStore?.allKnownVocabularyWords().orEmpty()
            )
        } else {
            val kbSpec = KeyboardLocales.specFor(prefs.activeLocale)
            val langDisplay = Locale.forLanguageTag(kbSpec.bcp47Tag).getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
            val sysLang = KeyboardLocales.systemLanguageDisplayName()
            val task = when (action) {
                WritingAction.PROOFREAD -> "Fix spelling, punctuation, and grammar while keeping the original meaning and language."
                WritingAction.PROFESSIONAL -> "Rewrite in a polite, clear, professional tone."
                WritingAction.CASUAL -> "Rewrite in a friendly, natural, casual tone."
                WritingAction.CONCISE -> "Make the text shorter and more direct while preserving all key facts."
                WritingAction.EMOTIVE -> "Make the text warmer, more expressive, and engaging."
                WritingAction.REPHRASE -> "Rephrase cleanly with fresh wording while keeping the exact intent."
                WritingAction.SUMMARIZE -> "Summarize the key points clearly as short bullet points."
                WritingAction.TRANSLATE -> "Translate naturally into $langDisplay (or into $sysLang if already in $langDisplay)."
                WritingAction.VOICE_CLEANUP -> ""
            }
            "You are Tapwing Writing Assistant. Language: $langDisplay (${kbSpec.bcp47Tag}). Task: $task Output ONLY the final rewritten text without quotes, commentary, or preamble."
        }

        val res = LocalInferenceEngine.generateWithLocalLlm(
            modelFile = file,
            spec = spec,
            systemPrompt = sysPrompt,
            userText = sourceText
        )
        return if (res.isSuccess) {
            WritingToolsOutput(
                rewrittenText = res.getOrThrow(),
                badge = "${spec.displayName} (Local)"
            )
        } else {
            val local = LocalWritingAssistant.transform(sourceText, action, prefs.activeLocale, dictionaryStore)
            WritingToolsOutput(
                rewrittenText = local,
                badge = "On-Device (Fallback)",
                fallbackNote = res.exceptionOrNull()?.localizedMessage
            )
        }
    }
}
