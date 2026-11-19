package com.tapwing.keyboard.ai

import com.tapwing.keyboard.data.AiProviderType
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
    private val dictionaryStore: AdaptiveDictionaryStore? = null
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

        if (prefs.aiProvider != AiProviderType.LOCAL_RULES) {
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
                        customEndpoint = prefs.aiCustomEndpoint
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
                val note = if (locale == "de") {
                    "Kein API-Schlüssel hinterlegt — lokale Regeln genutzt"
                } else {
                    "No API key set — used local rules"
                }
                return WritingToolsOutput(
                    rewrittenText = local,
                    badge = "On-Device",
                    fallbackNote = note
                )
            }
        }

        val local = LocalWritingAssistant.transform(sourceText, action, locale, dictionaryStore)
        return WritingToolsOutput(
            rewrittenText = local,
            badge = "On-Device"
        )
    }
}
