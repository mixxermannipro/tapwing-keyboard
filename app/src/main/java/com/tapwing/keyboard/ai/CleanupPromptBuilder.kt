package com.tapwing.keyboard.ai

import com.tapwing.keyboard.data.KeyboardLocales
import java.util.Locale

/**
 * Builds crisp, deterministic multilingual dictation cleanup & writing-tool prompts
 * tuned for both cloud LLMs and fast on-device GGUF models (LFM2.5, Qwen 3.5, Gemma 3).
 */
object CleanupPromptBuilder {

    /**
     * Uses the device or keyboard language as a hint while preserving all spoken languages
     * (including seamless mid-sentence DE+EN code-switching) and automatically structuring lists.
     */
    fun buildDefaultPrompt(
        systemLocale: Locale = Locale.getDefault(),
        activeKeyboardLocale: String = "de",
        useSystemLanguage: Boolean = true,
        autoBulletLists: Boolean = true
    ): String {
        val targetLocale = if (useSystemLanguage) {
            systemLocale
        } else {
            Locale.forLanguageTag(KeyboardLocales.specFor(activeKeyboardLocale).bcp47Tag)
        }
        val langEnglish = targetLocale.getDisplayLanguage(Locale.ENGLISH).ifBlank { "German" }
        val langTag = targetLocale.toLanguageTag().ifBlank { "de-DE" }
        val bulletRule = if (autoBulletLists) {
            "5. Smart formatting & automatic bullet lists: Format spoken numbers, dates, times, URLs, and emails cleanly. " +
                "Automatically convert spoken enumerations, to-dos, shopping lists, or multi-point sequences into clean bullet points (• Item) or numbered items (1. Item)."
        } else {
            "5. Smart formatting: Format spoken numbers, dates, times, URLs, and emails cleanly."
        }

        return """
You are the Tapwing dictation editor.
Primary device language: $langEnglish ($langTag).

Transform the raw speech transcript into clean, ready-to-send written text by following these rules:
1. Language fidelity & multilingual code-switching: Preserve every spoken language naturally, including seamless mid-sentence code-switching (e.g. German mixed with English technical or everyday terms). Use $langEnglish ($langTag) only to resolve ambiguous spellings. Never translate.
2. Remove speech disfluencies: Strip hesitation fillers ("äh", "ähm", "öhm", "um", "uh", "erm", "sozusagen"), false starts, and accidental word repetitions.
3. Resolve self-corrections locally: ALWAYS keep the full surrounding sentence intact; replace ONLY the corrected phrase itself (e.g. "Ich fahre nach Berlin, nein warte, Hamburg" -> "Ich fahre nach Hamburg.", "Let's meet at 3 PM, actually make that 2 PM" -> "Let's meet at 2 PM.").
4. Grammar, casing & punctuation: Apply natural grammar, capitalization (nouns/names), commas, periods/question marks, and paragraph breaks where appropriate.
$bulletRule
6. Spoken meta-instructions: ONLY if the transcript explicitly ends with an editing command (e.g. "mach das professioneller", "kürzer fassen", "als Stichpunkte"), apply it and drop the command.
7. Strict output: Preserve ALL facts and words not explicitly corrected. Output ONLY the cleaned final text without quotes, explanations, or answers.
        """.trimIndent()
    }

    fun resolveSystemPrompt(
        customPrompt: String,
        useSystemLanguage: Boolean = true,
        activeKeyboardLocale: String = "de",
        customVocabulary: Set<String> = emptySet(),
        systemLocale: Locale = Locale.getDefault(),
        autoBulletLists: Boolean = true
    ): String {
        val base = if (customPrompt.isBlank()) {
            buildDefaultPrompt(
                systemLocale = systemLocale,
                activeKeyboardLocale = activeKeyboardLocale,
                useSystemLanguage = useSystemLanguage,
                autoBulletLists = autoBulletLists
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

        val vocabHint = formatVocabularyHint(customVocabulary)
        return if (vocabHint.isNotEmpty()) {
            "$base\n\nPersonal dictionary (preserve exact spelling of these names/terms when relevant): $vocabHint"
        } else {
            base
        }
    }

    /**
     * Builds a concise, high-precision system prompt for any WritingAction (works identically on
     * fast 0.8B-4B local GGUF models and cloud LLMs).
     */
    fun buildWritingActionSystemPrompt(
        action: WritingAction,
        locale: String,
        customCleanupPrompt: String = "",
        useSystemLanguageForCleanup: Boolean = true,
        customVocabulary: Set<String> = emptySet(),
        autoBulletLists: Boolean = true
    ): String {
        if (action == WritingAction.VOICE_CLEANUP) {
            return resolveSystemPrompt(
                customPrompt = customCleanupPrompt,
                useSystemLanguage = useSystemLanguageForCleanup,
                activeKeyboardLocale = locale,
                customVocabulary = customVocabulary,
                autoBulletLists = autoBulletLists
            )
        }
        val spec = KeyboardLocales.specFor(locale)
        val langDisplay = Locale.forLanguageTag(spec.bcp47Tag).getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
        val sysLang = KeyboardLocales.systemLanguageDisplayName()
        val instruction = when (action) {
            WritingAction.PROOFREAD ->
                "Fix spelling, punctuation, capitalization, and grammar while keeping the exact meaning, tone, and language (including mixed-language terms)."
            WritingAction.PROFESSIONAL ->
                "Rewrite in a clear, polite, well-structured professional tone suitable for business emails or messages. Keep the original language."
            WritingAction.CASUAL ->
                "Rewrite in a friendly, natural, relaxed conversational tone. Keep the original language."
            WritingAction.CONCISE ->
                "Make the text noticeably shorter, tighter, and more direct while preserving every essential fact. Keep the original language."
            WritingAction.EMOTIVE ->
                "Make the text warmer, more expressive, and engaging without sounding artificial. Keep the original language."
            WritingAction.REPHRASE ->
                "Rephrase fluently with fresh, polished wording while preserving the exact intent and language."
            WritingAction.SUMMARIZE ->
                "Summarize the key points clearly and concisely as short bullet points (• Point). Keep the original language."
            WritingAction.BULLET_LIST ->
                "Convert the text into a clean, well-structured bulleted list (using '• ' per line). Keep an optional short heading if one is present, and preserve all items and the original language."
            WritingAction.TRANSLATE ->
                "Translate naturally and idiomatically into $langDisplay (or into $sysLang if the text is already in $langDisplay)."
            WritingAction.VOICE_CLEANUP -> ""
        }
        val vocabHint = formatVocabularyHint(customVocabulary)
        val vocabSuffix = if (vocabHint.isNotEmpty()) {
            "\nPersonal dictionary (preserve exact spelling when relevant): $vocabHint"
        } else ""
        return """
You are the Tapwing on-device writing assistant.
Active language: $langDisplay (${spec.bcp47Tag}).
Task: $instruction
Rules: Output ONLY the final rewritten text. No preamble, no quotes, no markdown fences, no commentary.$vocabSuffix
        """.trimIndent()
    }

    internal fun formatVocabularyHint(customVocabulary: Set<String>, limit: Int = 25): String {
        return customVocabulary
            .asSequence()
            .map { it.trim() }
            .filter { it.length in 2..32 && it.any(Char::isLetter) }
            .distinctBy { it.lowercase() }
            .take(limit)
            .joinToString(", ")
    }

    private val THINK_BLOCK_REGEX = Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE)
    private val THINK_UNCLOSED_REGEX = Regex("""<think>[\s\S]*$""", RegexOption.IGNORE_CASE)
    private val EOS_TOKEN_REGEX = Regex("""<\|im_end\|>|<\|eot_id\|>|<end_of_turn>|</s>""", RegexOption.IGNORE_CASE)
    private val PREAMBLE_REGEX = Regex(
        """^(?:(?:Sure|Certainly|Of course|Gerne|Klar)[,!]?\s+)?(?:Here(?:'s| is) the (?:cleaned|rewritten|corrected|polished) text:|Hier ist der (?:bereinigte|korrigierte|überarbeitete) Text:|Output:|Ergebnis:)\s*""",
        RegexOption.IGNORE_CASE
    )

    fun sanitizeModelOutput(rawOutput: String): String {
        var cleaned = rawOutput
            .replace(THINK_BLOCK_REGEX, "")
            .replace(THINK_UNCLOSED_REGEX, "")
            .replace(EOS_TOKEN_REGEX, "")
            .trim()
        // Strip common assistant prefixes a small LLM might accidentally emit
        cleaned = cleaned.replace(PREAMBLE_REGEX, "").trim()
        // Strip accidental markdown code fences if a small model wrapped output
        if (cleaned.startsWith("```") && cleaned.endsWith("```") && cleaned.length >= 6) {
            cleaned = cleaned.removePrefix("```").substringAfter('\n').removeSuffix("```").trim()
        }
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
