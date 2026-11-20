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
1. Language fidelity: Write in $langEnglish ($langTag) by default, OR preserve the exact spoken language(s) if the user clearly speaks or mixes another language (multilingual code-switching). Never translate unless explicitly instructed inside the transcript.
2. Remove speech disfluencies: Strip hesitation fillers ("äh", "ähm", "öhm", "um", "uh", "erm", "like", "eigentlich", "sozusagen" when used as stutters), false starts, and accidental word repetitions.
3. Resolve self-corrections: When the speaker corrects themselves mid-sentence (e.g. "um 14 Uhr, nein warte, 15 Uhr" -> "um 15 Uhr"; "send it to Tom, actually I mean Sarah" -> "send it to Sarah"), keep ONLY the final intended version.
4. Grammar, casing & punctuation: Apply natural grammar, accurate capitalization (including proper nouns), commas, periods, question marks, exclamation marks, and paragraph breaks where the topic shifts or spoken ("neuer Absatz" / "new paragraph").
5. Smart formatting: Format spoken numbers, dates, times, and enumerations cleanly (use bullet points or numbered lines when clearly listing items).
6. Spoken meta-instructions: If the dictation ends with an explicit editing command (e.g. "mach das professioneller", "kürzer fassen", "make it friendly", "as bullet points"), apply that transformation to the preceding message and remove the meta-command.
7. Strict output: Output ONLY the cleaned final text. Do NOT add quotes, explanations, greetings, or answers to questions in the dictated text.
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
            .filter { it.length >= 2 }
            .take(40)
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
