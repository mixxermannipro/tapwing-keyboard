package com.tapwing.keyboard.ai

import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.dictionary.SuggestionEngine
import com.tapwing.keyboard.voice.NaturalVoiceCleaner

enum class WritingAction {
    PROOFREAD,
    PROFESSIONAL,
    CASUAL,
    CONCISE,
    EMOTIVE,
    REPHRASE,
    SUMMARIZE,
    TRANSLATE,
    VOICE_CLEANUP
}

/**
 * Pure Kotlin, offline rule-based writing assistant supporting multi-language proofreading
 * and German/English tone transforms when no local LLM or cloud key is active.
 */
object LocalWritingAssistant {

    fun transform(
        input: String,
        action: WritingAction,
        locale: String,
        dictionaryStore: AdaptiveDictionaryStore? = null
    ): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val base = KeyboardLocales.baseLang(locale)
        val de = base == "de"

        return when (action) {
            WritingAction.PROOFREAD -> proofread(trimmed, locale, dictionaryStore)
            WritingAction.PROFESSIONAL -> makeProfessional(proofread(trimmed, locale, dictionaryStore), de)
            WritingAction.CASUAL -> makeCasual(proofread(trimmed, locale, dictionaryStore), de)
            WritingAction.CONCISE -> makeConcise(proofread(trimmed, locale, dictionaryStore), de)
            WritingAction.EMOTIVE -> makeEmotive(proofread(trimmed, locale, dictionaryStore), de)
            WritingAction.REPHRASE -> rephrase(proofread(trimmed, locale, dictionaryStore), de)
            WritingAction.SUMMARIZE -> summarize(proofread(trimmed, locale, dictionaryStore))
            WritingAction.TRANSLATE -> proofread(trimmed, locale, dictionaryStore)
            WritingAction.VOICE_CLEANUP -> NaturalVoiceCleaner.clean(
                rawTranscript = trimmed,
                locale = locale,
                removeFillers = true,
                autoPunctuate = true,
                dictionaryStore = dictionaryStore
            )
        }
    }

    fun proofread(
        input: String,
        locale: String,
        dictionaryStore: AdaptiveDictionaryStore? = null
    ): String {
        var text = input.trim()
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex(""" *([,.!?;:])"""), "$1")
            .replace(Regex("""([,.!?;:])([^\s\n.,!?;:])"""), "$1 $2")

        // Fix consecutive repeated words ("the the" -> "the")
        text = text.replace(Regex("""\b(\p{L}{2,})\s+\1\b""", RegexOption.IGNORE_CASE), "$1")

        // Fix known typos & apply custom/mid-sentence casing safely without forcing sentence-start caps
        val wordRegex = Regex("""\p{L}+(['-]\p{L}+)?""")
        text = wordRegex.replace(text) { match ->
            val word = match.value
            val startIdx = match.range.first
            val prefix = text.substring(0, startIdx)
            val atStart = prefix.isBlank() || prefix.endsWith("\n") || Regex("""[.!?]\s+$""").containsMatchIn(prefix)
            val customCanonical = dictionaryStore?.canonicalMidSentenceCasingFor(word, locale)
            customCanonical
                ?: SuggestionEngine.autoCorrectionFor(word, locale, dictionaryStore, atSentenceStart = atStart)
                ?: word
        }

        text = capitalizeSentences(text)
        if (text.isNotEmpty() && text.last() !in setOf('.', '!', '?', ':', ';')) {
            text += "."
        }
        return text
    }

    private fun summarize(text: String): String {
        val sentences = text.split(Regex("""(?<=[.!?])\s+""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.size <= 2) return text
        return sentences.take(2).joinToString(" ") { "• $it" }
    }

    private fun makeProfessional(text: String, de: Boolean): String {
        var out = text
        val replacements = if (de) {
            listOf(
                Regex("""^(hi|hey|hallo)\b[,!\s]*""", RegexOption.IGNORE_CASE) to "Guten Tag, ",
                Regex("""\b(thx|danke dir|vielen dank dir)\b""", RegexOption.IGNORE_CASE) to "vielen Dank",
                Regex("""\b(sorry|entschuldigung)\b""", RegexOption.IGNORE_CASE) to "bitte entschuldigen Sie",
                Regex("""\b(schick mir|send mir)\b""", RegexOption.IGNORE_CASE) to "bitte senden Sie mir",
                Regex("""\b(kannst du)\b""", RegexOption.IGNORE_CASE) to "könnten Sie",
                Regex("""\b(hast du)\b""", RegexOption.IGNORE_CASE) to "haben Sie",
                Regex("""\b(bitte melde dich|sag bescheid)\b""", RegexOption.IGNORE_CASE) to "ich freue mich auf Ihre Rückmeldung",
                Regex("""\b(lg|viele grüße|liebe grüße)\b""", RegexOption.IGNORE_CASE) to "Mit freundlichen Grüßen"
            )
        } else {
            listOf(
                Regex("""^(hey|hi)\b[,!\s]*""", RegexOption.IGNORE_CASE) to "Hello, ",
                Regex("""\b(thx|thanx|ty)\b""", RegexOption.IGNORE_CASE) to "thank you",
                Regex("""\b(plz|pls)\b""", RegexOption.IGNORE_CASE) to "please",
                Regex("""\b(sorry)\b""", RegexOption.IGNORE_CASE) to "I apologize for the inconvenience",
                Regex("""\b(gonna)\b""", RegexOption.IGNORE_CASE) to "going to",
                Regex("""\b(wanna)\b""", RegexOption.IGNORE_CASE) to "want to",
                Regex("""\b(can you)\b""", RegexOption.IGNORE_CASE) to "could you please",
                Regex("""\b(let me know)\b""", RegexOption.IGNORE_CASE) to "please let me know at your earliest convenience"
            )
        }
        for ((pattern, repl) in replacements) {
            out = out.replace(pattern, repl)
        }
        out = out.replace("!", ".")
        return capitalizeSentences(out)
    }

    private fun makeCasual(text: String, de: Boolean): String {
        var out = text
        val replacements = if (de) {
            listOf(
                Regex("""^(sehr geehrte[r]?|guten tag)\b[,!\s]*""", RegexOption.IGNORE_CASE) to "Hey, ",
                Regex("""\b(mit freundlichen grüßen)\b""", RegexOption.IGNORE_CASE) to "Viele Grüße",
                Regex("""\b(könnten sie)\b""", RegexOption.IGNORE_CASE) to "kannst du",
                Regex("""\b(haben sie)\b""", RegexOption.IGNORE_CASE) to "hast du",
                Regex("""\b(vielen dank im voraus)\b""", RegexOption.IGNORE_CASE) to "danke dir schon mal"
            )
        } else {
            listOf(
                Regex("""^(dear sir or madam|hello)\b[,!\s]*""", RegexOption.IGNORE_CASE) to "Hey, ",
                Regex("""\b(sincerely|best regards|kind regards)\b""", RegexOption.IGNORE_CASE) to "Cheers",
                Regex("""\b(could you please)\b""", RegexOption.IGNORE_CASE) to "can you",
                Regex("""\b(i apologize)\b""", RegexOption.IGNORE_CASE) to "sorry"
            )
        }
        for ((pattern, repl) in replacements) {
            out = out.replace(pattern, repl)
        }
        return capitalizeSentences(out)
    }

    private fun makeConcise(text: String, de: Boolean): String {
        val fillers = if (de) {
            listOf(
                "eigentlich", "sozusagen", "irgendwie", "halt", "übrigens",
                "einfach mal", "im prinzip", "grundsätzlich", "vielleicht noch"
            )
        } else {
            listOf(
                "actually", "basically", "really", "just", "kind of",
                "sort of", "in order to", "very", "literally"
            )
        }
        var out = text
        for (f in fillers) {
            out = out.replace(Regex("""\b$f\b""", RegexOption.IGNORE_CASE), "")
        }
        out = out.replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""\s+([,.!?])"""), "$1")
            .trim()
        return capitalizeSentences(out)
    }

    private fun makeEmotive(text: String, de: Boolean): String {
        var out = text.trim()
        if (out.endsWith(".")) {
            out = out.dropLast(1) + "!"
        }
        val alreadyWarm = out.contains("😊") || out.contains("✨") || out.contains("🙏")
        if (!alreadyWarm) {
            out += if (de) " 😊" else " ✨"
        }
        return out
    }

    private fun rephrase(text: String, de: Boolean): String {
        var out = text
        val synonyms = if (de) {
            listOf(
                Regex("""\b(sehr gut)\b""", RegexOption.IGNORE_CASE) to "ausgezeichnet",
                Regex("""\b(problem)\b""", RegexOption.IGNORE_CASE) to "Anliegen",
                Regex("""\b(schnell)\b""", RegexOption.IGNORE_CASE) to "zeitnah",
                Regex("""\b(schicken)\b""", RegexOption.IGNORE_CASE) to "zusenden",
                Regex("""\b(wichtig)\b""", RegexOption.IGNORE_CASE) to "wesentlich",
                Regex("""\b(machen)\b""", RegexOption.IGNORE_CASE) to "umsetzen"
            )
        } else {
            listOf(
                Regex("""\b(very good)\b""", RegexOption.IGNORE_CASE) to "excellent",
                Regex("""\b(problem)\b""", RegexOption.IGNORE_CASE) to "issue",
                Regex("""\b(fast|quick)\b""", RegexOption.IGNORE_CASE) to "prompt",
                Regex("""\b(send)\b""", RegexOption.IGNORE_CASE) to "share",
                Regex("""\b(important)\b""", RegexOption.IGNORE_CASE) to "essential",
                Regex("""\b(make)\b""", RegexOption.IGNORE_CASE) to "create"
            )
        }
        for ((pattern, repl) in synonyms) {
            out = out.replace(pattern, repl)
        }
        if (out == text && out.isNotEmpty()) {
            out = if (de) {
                "Kurz gesagt: ${out.replaceFirstChar { it.lowercaseChar() }}"
            } else {
                "To put it clearly: ${out.replaceFirstChar { it.lowercaseChar() }}"
            }
        }
        return capitalizeSentences(out)
    }

    private fun capitalizeSentences(input: String): String {
        val sb = StringBuilder(input.length)
        var cap = true
        for (ch in input) {
            if (cap && ch.isLetter()) {
                sb.append(ch.uppercaseChar())
                cap = false
            } else {
                sb.append(ch)
                if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                    cap = true
                }
            }
        }
        return sb.toString().trim()
    }
}
