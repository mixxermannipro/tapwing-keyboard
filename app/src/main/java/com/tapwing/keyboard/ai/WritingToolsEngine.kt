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
    private val dictionaryStoreProvider: () -> AdaptiveDictionaryStore? = { null },
    private val modelManager: LocalModelManager? = null,
    appContext: Context? = null
) {
    private val appContext: Context? = appContext?.applicationContext

    constructor(
        secureKeyStorage: SecureKeyStorage,
        dictionaryStore: AdaptiveDictionaryStore?,
        modelManager: LocalModelManager? = null,
        appContext: Context? = null
    ) : this(
        secureKeyStorage = secureKeyStorage,
        dictionaryStoreProvider = { dictionaryStore },
        modelManager = modelManager,
        appContext = appContext
    )

    private val currentDictionaryStore: AdaptiveDictionaryStore?
        get() = dictionaryStoreProvider()

    suspend fun runAction(
        sourceText: String,
        action: WritingAction,
        prefs: TapwingPrefs,
        appPackage: String? = null
    ): WritingToolsOutput {
        val locale = prefs.activeLocale
        if (sourceText.isBlank()) {
            return WritingToolsOutput("", "On-Device")
        }

        val primary = tryProvider(
            sourceText = sourceText,
            action = action,
            locale = locale,
            provider = prefs.aiProvider,
            model = prefs.aiModel,
            prefs = prefs,
            appPackage = appPackage
        )
        if (primary is ProviderAttempt.Success) {
            return primary.output
        }

        val fallback = if (prefs.aiFallbackProvider != AiProviderType.LOCAL_RULES &&
            (prefs.aiFallbackProvider != prefs.aiProvider || prefs.aiFallbackModel != prefs.aiModel)
        ) {
            tryProvider(
                sourceText = sourceText,
                action = action,
                locale = locale,
                provider = prefs.aiFallbackProvider,
                model = prefs.aiFallbackModel,
                prefs = prefs,
                appPackage = appPackage
            )
        } else null

        if (fallback is ProviderAttempt.Success) {
            return fallback.output.copy(
                fallbackNote = (primary as? ProviderAttempt.Failure)?.reason
            )
        }

        val local = LocalWritingAssistant.transform(sourceText, action, locale, currentDictionaryStore)
        val reason = (primary as? ProviderAttempt.Failure)?.reason
            ?: (fallback as? ProviderAttempt.Failure)?.reason
        return WritingToolsOutput(
            rewrittenText = local,
            badge = if (reason != null) "On-Device (Fallback)" else "On-Device",
            fallbackNote = reason
        )
    }

    /**
     * Cleans up voice dictation text using the configured Cleanup provider + optional Fallback provider:
     * 1) Smart-Bypass for ultra-short clean utterances (< 1 ms local)
     * 2) Primary Cleanup backend (Local GGUF LLM / Gemini / Groq / OpenRouter / Grok / OpenAI / Gemini Nano)
     * 3) Configured Cleanup Fallback provider if primary fails or key/model is missing
     * 4) Instant offline rule-based NaturalVoiceCleaner fallback
     */
    suspend fun cleanupVoiceTranscript(
        rawTranscript: String,
        prefs: TapwingPrefs,
        ruleCleaned: String,
        appPackage: String? = null
    ): WritingToolsOutput {
        if (rawTranscript.isBlank()) return WritingToolsOutput("", "On-Device")

        val primaryProvider = prefs.cleanupProvider
        val primaryModel = prefs.cleanupModel.ifBlank { AiModelCatalog.defaultTextModelFor(primaryProvider) }

        if (!prefs.voiceAiPolish || shouldSkipCloudPolish(rawTranscript, ruleCleaned)) {
            return WritingToolsOutput(
                rewrittenText = ruleCleaned,
                badge = "On-Device Rules"
            )
        }

        if (primaryProvider == AiProviderType.LOCAL_RULES &&
            prefs.cleanupFallbackProvider == AiProviderType.LOCAL_RULES
        ) {
            return WritingToolsOutput(
                rewrittenText = ruleCleaned,
                badge = "On-Device Rules"
            )
        }

        if (primaryProvider != AiProviderType.LOCAL_RULES) {
            val primary = tryProvider(
                sourceText = if (primaryProvider == AiProviderType.GEMINI_NANO) ruleCleaned.ifBlank { rawTranscript } else rawTranscript,
                action = WritingAction.VOICE_CLEANUP,
                locale = prefs.activeLocale,
                provider = primaryProvider,
                model = primaryModel,
                prefs = prefs,
                appPackage = appPackage
            )
            if (primary is ProviderAttempt.Success && !looksOverTruncated(rawTranscript, primary.output.rewrittenText)) {
                return primary.output
            }
        }

        val fallbackProvider = prefs.cleanupFallbackProvider
        if (fallbackProvider != AiProviderType.LOCAL_RULES &&
            (fallbackProvider != primaryProvider || prefs.cleanupFallbackModel != primaryModel)
        ) {
            val fallbackModel = prefs.cleanupFallbackModel.ifBlank { AiModelCatalog.defaultTextModelFor(fallbackProvider) }
            val fallback = tryProvider(
                sourceText = if (fallbackProvider == AiProviderType.GEMINI_NANO) ruleCleaned.ifBlank { rawTranscript } else rawTranscript,
                action = WritingAction.VOICE_CLEANUP,
                locale = prefs.activeLocale,
                provider = fallbackProvider,
                model = fallbackModel,
                prefs = prefs,
                appPackage = appPackage
            )
            if (fallback is ProviderAttempt.Success && !looksOverTruncated(rawTranscript, fallback.output.rewrittenText)) {
                return fallback.output
            }
        }

        return WritingToolsOutput(
            rewrittenText = ruleCleaned,
            badge = "On-Device Rules"
        )
    }

    private sealed interface ProviderAttempt {
        data class Success(val output: WritingToolsOutput) : ProviderAttempt
        data class Failure(val reason: String) : ProviderAttempt
    }

    private suspend fun tryProvider(
        sourceText: String,
        action: WritingAction,
        locale: String,
        provider: AiProviderType,
        model: String,
        prefs: TapwingPrefs,
        appPackage: String?
    ): ProviderAttempt {
        if (provider == AiProviderType.LOCAL_RULES) {
            return ProviderAttempt.Success(
                WritingToolsOutput(
                    rewrittenText = LocalWritingAssistant.transform(sourceText, action, locale, currentDictionaryStore),
                    badge = "On-Device"
                )
            )
        }

        val personalVocabulary = currentDictionaryStore?.topVocabularyForVoice(appPackage = appPackage, limit = 25).orEmpty()

        if (provider == AiProviderType.LOCAL_LLM) {
            val preferredLlmId = model.trim().takeIf { LocalModelCatalog.findById(it)?.kind?.isLlm == true }
                ?: prefs.selectedLlmModelId
            val installed = modelManager?.bestInstalledLlmModel(preferredLlmId)
            if (installed == null) {
                val msg = if (KeyboardLocales.baseLang(locale) == "de") {
                    "Kein lokales KI-Modell geladen – lade eins unter Schreibhilfe"
                } else {
                    "No local LLM downloaded – install one in Writing Aid"
                }
                return ProviderAttempt.Failure(msg)
            }
            val (spec, modelFile) = installed
            val res = LocalLlmEngine.processText(
                text = sourceText,
                action = action,
                locale = locale,
                spec = spec,
                modelFile = modelFile,
                customCleanupPrompt = prefs.voiceCleanupPrompt,
                useSystemLanguageForCleanup = prefs.voiceCleanupUseSystemLanguage,
                customVocabulary = personalVocabulary,
                autoBulletLists = prefs.voiceAutoBulletLists
            )
            return if (res.isSuccess) {
                ProviderAttempt.Success(
                    WritingToolsOutput(
                        rewrittenText = res.getOrThrow(),
                        badge = "Lokal · ${spec.shortEngineLabel}"
                    )
                )
            } else {
                ProviderAttempt.Failure(
                    res.exceptionOrNull()?.localizedMessage
                        ?: if (KeyboardLocales.baseLang(locale) == "de") "Lokales LLM fehlgeschlagen" else "Local LLM failed"
                )
            }
        }

        if (provider == AiProviderType.GEMINI_NANO) {
            val ctx = appContext
            if (ctx != null) {
                val nano = GeminiNanoClient.processText(ctx, sourceText, action, locale)
                if (nano.isSuccess) {
                    return ProviderAttempt.Success(
                        WritingToolsOutput(
                            rewrittenText = nano.getOrThrow(),
                            badge = "Gemini Nano (AICore)"
                        )
                    )
                }
            }
            val msg = if (KeyboardLocales.baseLang(locale) == "de") {
                "Gemini Nano auf diesem Gerät nicht verfügbar"
            } else {
                "Gemini Nano unavailable on this device"
            }
            return ProviderAttempt.Failure(msg)
        }

        if (provider in CLOUD_PROVIDERS) {
            val apiKey = secureKeyStorage.readKey(provider)
            if (apiKey.isBlank()) {
                val msg = if (KeyboardLocales.baseLang(locale) == "de") {
                    "Kein API-Key für ${AiModelCatalog.displayName(provider, true)}"
                } else {
                    "No API key for ${AiModelCatalog.displayName(provider, false)}"
                }
                return ProviderAttempt.Failure(msg)
            }
            val resolvedModel = model.trim().ifEmpty { AiModelCatalog.defaultTextModelFor(provider) }
            return when (
                val res = CloudAiClient.rewrite(
                    text = sourceText,
                    action = action,
                    locale = locale,
                    provider = provider,
                    apiKey = apiKey,
                    model = resolvedModel,
                    customEndpoint = prefs.aiCustomEndpoint,
                    customCleanupPrompt = prefs.voiceCleanupPrompt,
                    useSystemLanguageForCleanup = prefs.voiceCleanupUseSystemLanguage,
                    customVocabulary = personalVocabulary,
                    autoBulletLists = prefs.voiceAutoBulletLists
                )
            ) {
                is CloudAiResult.Success -> ProviderAttempt.Success(
                    WritingToolsOutput(
                        rewrittenText = res.text,
                        badge = res.providerLabel
                    )
                )
                is CloudAiResult.Failure -> ProviderAttempt.Failure(res.message)
            }
        }

        return ProviderAttempt.Failure("Unsupported provider")
    }

    private fun looksOverTruncated(raw: String, cleaned: String): Boolean {
        val rawWords = raw.trim().split(WHITESPACE_REGEX).count { it.isNotBlank() }
        if (rawWords < 6) return false
        val lower = raw.lowercase()
        val hasShortenCmd = lower.contains("kürzer") || lower.contains("shorter") ||
            lower.contains("zusammenfass") || lower.contains("summarize")
        if (hasShortenCmd) return false
        val cleanWords = cleaned.trim().split(WHITESPACE_REGEX).count { it.isNotBlank() }
        return cleanWords * 100 < rawWords * 45
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("""\s+""")

        val CLOUD_PROVIDERS = setOf(
            AiProviderType.GROQ_API,
            AiProviderType.GEMINI_API,
            AiProviderType.OPENROUTER_API,
            AiProviderType.XAI_API,
            AiProviderType.OPENAI_COMPATIBLE
        )

        private val BYPASS_BLOCKING_CUES = setOf(
            "äh", "ähm", "öhm", "um", "uh", "erm", "nein", "warte", "meine",
            "besser", "eigentlich", "actually", "wait", "scratch", "bullet",
            "stichpunkt", "stichpunkte", "komma", "punkt", "fragezeichen",
            "einkaufsliste", "aufgaben", "liste", "todo", "to-do", "tasks",
            "erstens", "zweitens", "first", "second", "agenda", "checkliste"
        )

        /**
         * Avoids a network/LLM round-trip when a short 1-4 word phrase already needs no AI semantic rewrite.
         */
        fun shouldSkipCloudPolish(rawTranscript: String, ruleCleaned: String): Boolean {
            val trimmed = rawTranscript.trim()
            if (trimmed.isEmpty() || ruleCleaned.isBlank()) return true
            val words = trimmed.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
            if (words.size > 4) return false
            return words.none { word ->
                word.lowercase().trim(',', '.', '!', '?', ':') in BYPASS_BLOCKING_CUES
            }
        }
    }
}
