package com.tapwing.keyboard.voice

import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore

sealed interface VoiceCommandResult {
    data class Text(val cleanedText: String) : VoiceCommandResult
    data class RewriteAction(val action: WritingAction) : VoiceCommandResult
    data object DeleteLastWord : VoiceCommandResult
}

/**
 * Multilingual natural voice dictation cleanup engine:
 * strips hesitation fillers, applies mid-sentence self-corrections,
 * converts spoken punctuation, auto-capitalizes sentences, and detects voice edit commands.
 */
object NaturalVoiceCleaner {

    private val fillersByLang = mapOf(
        "de" to setOf("äh", "ähm", "öhm", "öh", "hm", "hmm", "mhm", "ähhh", "halt", "sozusagen"),
        "en" to setOf("um", "uh", "erm", "er", "ah", "hmm", "hm", "uhm"),
        "fr" to setOf("euh", "ben", "bah", "hm", "hmm", "voilà", "genre"),
        "es" to setOf("eh", "em", "este", "pues", "o sea", "hm", "hmm"),
        "it" to setOf("ehm", "eh", "allora", "cioè", "tipo", "hm", "hmm"),
        "pt" to setOf("é", "hm", "hmm", "tipo", "assim", "né"),
        "nl" to setOf("eh", "uh", "uhm", "zeg maar", "eigenlijk", "hm", "hmm"),
        "pl" to setOf("yy", "yym", "ee", "hm", "hmm", "no", "znaczy"),
        "tr" to setOf("şey", "yani", "ıı", "ee", "hm", "hmm")
    )

    private val questionStartersByLang = mapOf(
        "de" to setOf(
            "wer", "wie", "was", "warum", "wieso", "weshalb", "wann", "wo", "wohin",
            "woher", "welche", "welcher", "welches", "kannst", "können", "hast", "haben",
            "bist", "seid", "ist", "sind", "darf", "soll", "willst", "möchtest"
        ),
        "en" to setOf(
            "who", "what", "where", "when", "why", "how", "which", "can", "could",
            "would", "should", "do", "does", "did", "is", "are", "am", "have", "has", "will"
        ),
        "fr" to setOf("qui", "quoi", "où", "quand", "pourquoi", "comment", "quel", "quelle", "est-ce", "peux", "pouvez"),
        "es" to setOf("quién", "qué", "dónde", "cuándo", "por qué", "cómo", "cuál", "puedes"),
        "it" to setOf("chi", "cosa", "dove", "quando", "perché", "come", "quale", "puoi"),
        "pt" to setOf("quem", "que", "onde", "quando", "por que", "como", "qual", "pode"),
        "nl" to setOf("wie", "wat", "waar", "wanneer", "waarom", "hoe", "welke", "kun"),
        "pl" to setOf("kto", "co", "gdzie", "kiedy", "dlaczego", "jak", "czy", "możesz"),
        "tr" to setOf("kim", "ne", "nerede", "ne zaman", "neden", "nasıl", "hangi")
    )

    fun parseCommandOrClean(
        rawTranscript: String,
        locale: String,
        removeFillers: Boolean = true,
        autoPunctuate: Boolean = true,
        dictionaryStore: AdaptiveDictionaryStore? = null
    ): VoiceCommandResult {
        val trimmed = rawTranscript.trim()
        if (trimmed.isEmpty()) return VoiceCommandResult.Text("")
        val lower = trimmed.lowercase().trimEnd('.', '!', '?')

        when {
            lower == "lösche das letzte wort" || lower == "letztes wort löschen" ||
                lower == "delete last word" || lower == "scratch last word" -> {
                return VoiceCommandResult.DeleteLastWord
            }
            lower in setOf("mach es freundlicher", "freundlicher formulieren", "make it friendly", "make it casual") -> {
                return VoiceCommandResult.RewriteAction(WritingAction.CASUAL)
            }
            lower in setOf("mach es professionell", "professioneller formulieren", "make it professional") -> {
                return VoiceCommandResult.RewriteAction(WritingAction.PROFESSIONAL)
            }
            lower in setOf("mach es kürzer", "kürzer fassen", "make it shorter", "make it concise") -> {
                return VoiceCommandResult.RewriteAction(WritingAction.CONCISE)
            }
            lower in setOf("korrigiere das", "korrigieren", "proofread", "fix grammar") -> {
                return VoiceCommandResult.RewriteAction(WritingAction.PROOFREAD)
            }
        }

        return VoiceCommandResult.Text(
            clean(
                rawTranscript = trimmed,
                locale = locale,
                removeFillers = removeFillers,
                autoPunctuate = autoPunctuate,
                dictionaryStore = dictionaryStore
            )
        )
    }

    fun clean(
        rawTranscript: String,
        locale: String,
        removeFillers: Boolean = true,
        autoPunctuate: Boolean = true,
        dictionaryStore: AdaptiveDictionaryStore? = null
    ): String {
        var text = rawTranscript.trim()
        if (text.isEmpty()) return ""
        val baseLang = KeyboardLocales.baseLang(locale)

        text = applySelfCorrections(text, baseLang)

        if (removeFillers) {
            text = stripFillers(text, baseLang)
        }

        text = replaceSpokenPunctuation(text, baseLang)
        text = normalizeWhitespaceAndPunctuation(text)

        if (dictionaryStore != null && text.isNotEmpty()) {
            val wordRegex = Regex("""\p{L}+(['-]\p{L}+)?""")
            text = wordRegex.replace(text) { match ->
                dictionaryStore.canonicalMidSentenceCasingFor(match.value, locale) ?: match.value
            }
        }

        if (autoPunctuate && text.isNotEmpty()) {
            text = capitalizeSentences(text)
            text = ensureTerminalPunctuation(text, baseLang)
        }

        return text.trim()
    }

    private fun applySelfCorrections(input: String, baseLang: String): String {
        var current = input
        val triggers = when (baseLang) {
            "de" -> listOf("nein warte", "ich meine", "besser gesagt", "äh ich meine")
            "fr" -> listOf("non attends", "je veux dire", "enfin")
            "es" -> listOf("no espera", "quiero decir", "mejor dicho")
            "it" -> listOf("no aspetta", "intendo dire", "anzi")
            else -> listOf("wait no", "actually i mean", "i mean", "scratch that", "no wait")
        }

        val trailingPunctTokens = when (baseLang) {
            "de" -> setOf("punkt", "komma", "fragezeichen", "ausrufezeichen")
            "fr" -> setOf("point", "virgule")
            "es" -> setOf("punto", "coma")
            else -> setOf("period", "comma", "question", "exclamation")
        }

        for (trigger in triggers) {
            val regex = Regex("""\b$trigger\b[,\s]*""", RegexOption.IGNORE_CASE)
            while (true) {
                val match = regex.find(current) ?: break
                val prefix = current.substring(0, match.range.first).trimEnd()
                val suffix = current.substring(match.range.last + 1).trimStart()
                val prefixWords = if (prefix.isEmpty()) emptyList() else prefix.split(Regex("""\s+"""))
                val suffixWords = if (suffix.isEmpty()) emptyList() else suffix.split(Regex("""\s+"""))

                val replacementSpan = suffixWords
                    .takeWhile { it.lowercase().trim(',', '.', '!', '?') !in trailingPunctTokens }
                    .size
                    .coerceIn(1, 4)
                val dropCount = replacementSpan.coerceAtMost(prefixWords.size)
                val keptPrefix = prefixWords.dropLast(dropCount).joinToString(" ")
                current = listOf(keptPrefix, suffix).filter { it.isNotEmpty() }.joinToString(" ")
            }
        }
        return current
    }

    private fun stripFillers(input: String, baseLang: String): String {
        val fillers = fillersByLang[baseLang] ?: fillersByLang["en"]!!
        val tokens = input.split(Regex("""\s+"""))
        val kept = tokens.filter { token ->
            val stripped = token.lowercase().trim(',', '.', '!', '?')
            stripped.isNotEmpty() && stripped !in fillers
        }
        return kept.joinToString(" ")
    }

    private fun replaceSpokenPunctuation(input: String, baseLang: String): String {
        var text = input
        val replacements = when (baseLang) {
            "de" -> listOf(
                Regex("""\bneue zeile\b""", RegexOption.IGNORE_CASE) to "\n",
                Regex("""\bneuer absatz\b""", RegexOption.IGNORE_CASE) to "\n\n",
                Regex("""\bfragezeichen\b""", RegexOption.IGNORE_CASE) to "?",
                Regex("""\bausrufezeichen\b""", RegexOption.IGNORE_CASE) to "!",
                Regex("""\bdoppelpunkt\b""", RegexOption.IGNORE_CASE) to ":",
                Regex("""\bsemikolon\b""", RegexOption.IGNORE_CASE) to ";",
                Regex("""\bkomma\b""", RegexOption.IGNORE_CASE) to ",",
                Regex("""\bpunkt\b""", RegexOption.IGNORE_CASE) to "."
            )
            "fr" -> listOf(
                Regex("""\bnouveau paragraphe\b""", RegexOption.IGNORE_CASE) to "\n\n",
                Regex("""\bnouvelle ligne\b""", RegexOption.IGNORE_CASE) to "\n",
                Regex("""\bpoint d'interrogation\b""", RegexOption.IGNORE_CASE) to "?",
                Regex("""\bpoint d'exclamation\b""", RegexOption.IGNORE_CASE) to "!",
                Regex("""\bvirgule\b""", RegexOption.IGNORE_CASE) to ",",
                Regex("""\bpoint\b""", RegexOption.IGNORE_CASE) to "."
            )
            "es" -> listOf(
                Regex("""\bnuevo párrafo\b""", RegexOption.IGNORE_CASE) to "\n\n",
                Regex("""\bnueva línea\b""", RegexOption.IGNORE_CASE) to "\n",
                Regex("""\bcoma\b""", RegexOption.IGNORE_CASE) to ",",
                Regex("""\bpunto\b""", RegexOption.IGNORE_CASE) to "."
            )
            else -> listOf(
                Regex("""\bnew paragraph\b""", RegexOption.IGNORE_CASE) to "\n\n",
                Regex("""\bnew line\b""", RegexOption.IGNORE_CASE) to "\n",
                Regex("""\bquestion mark\b""", RegexOption.IGNORE_CASE) to "?",
                Regex("""\bexclamation mark\b""", RegexOption.IGNORE_CASE) to "!",
                Regex("""\bexclamation point\b""", RegexOption.IGNORE_CASE) to "!",
                Regex("""\bcolon\b""", RegexOption.IGNORE_CASE) to ":",
                Regex("""\bsemicolon\b""", RegexOption.IGNORE_CASE) to ";",
                Regex("""\bcomma\b""", RegexOption.IGNORE_CASE) to ",",
                Regex("""\bperiod\b""", RegexOption.IGNORE_CASE) to "."
            )
        }

        for ((pattern, symbol) in replacements) {
            text = text.replace(pattern, symbol)
        }
        return text
    }

    private fun normalizeWhitespaceAndPunctuation(input: String): String {
        var text = input
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex(""" *([.,!?:;])"""), "$1")
            .replace(Regex("""([.,!?:;])([^\s\n.,!?:;])"""), "$1 $2")
            .replace(Regex(""" *\n *"""), "\n")
        text = text.replace(Regex("""([,!?:;])\1+"""), "$1")
        return text.trim()
    }

    private fun capitalizeSentences(input: String): String {
        val sb = StringBuilder(input.length)
        var capitalizeNext = true
        for (ch in input) {
            if (capitalizeNext && ch.isLetter()) {
                sb.append(ch.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(ch)
                if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                    capitalizeNext = true
                }
            }
        }
        return sb.toString()
    }

    private fun ensureTerminalPunctuation(input: String, baseLang: String): String {
        val last = input.lastOrNull() ?: return input
        if (last in setOf('.', '!', '?', ':', ';', '\n')) return input
        val firstWord = input.trimStart().substringBefore(' ').lowercase().trim(',', '.')
        val starters = questionStartersByLang[baseLang] ?: questionStartersByLang["en"]!!
        val suffix = if (firstWord in starters) "?" else "."
        return "$input$suffix"
    }
}
