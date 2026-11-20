package com.tapwing.keyboard.ai

import com.tapwing.keyboard.data.KeyboardLocales
import java.util.Locale

/**
 * Builds the multilingual, device-system-language-aware cleanup prompt modeled to match and
 * surpass Google Rambler, while allowing complete user customization.
 */
object CleanupPromptBuilder {

    /**
     * Builds the default Rambler-grade system prompt targeting the device's system language
     * (or active keyboard language if preferred) while preserving multilingual code-switching.
     */
    fun buildDefaultPrompt(
        systemLocale: Locale = Locale.getDefault(),
        activeKeyboardLocale: String = "de",
        useSystemLanguage: Boolean = true
    ): String {
        val targetLocale = if (useSystemLanguage) {
            systemLocale
        } else {
            Locale.forLanguageTag(KeyboardLocales.specFor(activeKeyboardLocale).bcp47Tag)
        }
        val langEnglish = targetLocale.getDisplayLanguage(Locale.ENGLISH).ifBlank { "German" }
        val langTag = targetLocale.toLanguageTag().ifBlank { "de-DE" }

        return """
You are Tapwing Voice Cleanup — an ultra-accurate, privacy-first dictation editor running locally on the user's phone.
Primary device language: $langEnglish ($langTag).

Transform the raw speech transcript into clean, ready-to-send written text by following these rules:
1. Language fidelity: Write in $langEnglish ($langTag) by default, OR preserve the exact spoken language(s) if mixed. Never translate unless explicitly instructed inside the transcript.
2. Remove speech disfluencies: Strip hesitation fillers ("äh", "ähm", "öhm", "um", "uh", "erm", "like", "sozusagen"), false starts, and accidental word repetitions.
3. Resolve self-corrections locally: ALWAYS keep the full surrounding sentence intact; replace ONLY the corrected phrase itself (e.g. "Ich fahre nach Berlin, nein warte, Hamburg" -> "Ich fahre nach Hamburg.").
4. Grammar, casing & punctuation: Apply natural grammar, capitalization (nouns/names), commas, periods/question marks, and paragraph breaks where appropriate.
5. Smart formatting: Format spoken numbers, dates, times, and clear lists cleanly.
6. Spoken meta-instructions: ONLY if the transcript explicitly ends with an editing command (e.g. "mach das professioneller", "kürzer fassen"), apply it and drop the command.
7. Strict output: Preserve ALL facts and words not explicitly corrected. Output ONLY the cleaned final text without quotes, explanations, or answers.
        """.trimIndent()
    }

    fun resolveSystemPrompt(
        customPrompt: String,
        useSystemLanguage: Boolean = true,
        activeKeyboardLocale: String = "de",
        customVocabulary: Set<String> = emptySet(),
        systemLocale: Locale = Locale.getDefault()
    ): String {
        val base = if (customPrompt.isBlank()) {
            buildDefaultPrompt(
                systemLocale = systemLocale,
                activeKeyboardLocale = activeKeyboardLocale,
                useSystemLanguage = useSystemLanguage
            )
        } else {
            val targetLocale = if (useSystemLanguage) {
                systemLocale
            } else {
                Locale.forLanguageTag(KeyboardLocales.specFor(activeKeyboardLocale).bcp47Tag)
            }
            val langEnglish = targetLocale.getDisplayLanguage(Locale.ENGLISH).ifBlank { "German" }
            val langTag = targetLocale.toLanguageTag().ifBlank { "de-DE" }
            val trimmed = customPrompt.trim()
            if (trimmed.contains(langTag, ignoreCase = true) || trimmed.contains("{LANGUAGE}")) {
                trimmed.replace("{LANGUAGE}", "$langEnglish ($langTag)")
            } else {
                "$trimmed\n\nTarget device system language: $langEnglish ($langTag). Output ONLY the final cleaned text."
            }
        }

        val vocabHint = customVocabulary
            .asSequence()
            .map { it.trim() }
            .filter { it.length in 3..28 && it.first().isUpperCase() }
            .take(10)
            .joinToString(", ")

        return if (vocabHint.isNotEmpty()) {
            "$base\n\nPreserve exact spelling of these personal terms/names when relevant: $vocabHint"
        } else {
            base
        }
    }

    fun sanitizeModelOutput(rawOutput: String): String {
        var cleaned = rawOutput
            .replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<think>[\s\S]*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        // Strip accidental surrounding quotes if the model wrapped the entire output
        if (cleaned.length >= 2 &&
            ((cleaned.startsWith('"') && cleaned.endsWith('"')) ||
                (cleaned.startsWith('„') && cleaned.endsWith('“')))
        ) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        return cleaned
    }
}
