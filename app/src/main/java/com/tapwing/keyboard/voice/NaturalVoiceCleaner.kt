package com.tapwing.keyboard.voice

import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore

sealed interface VoiceCommandResult {
    data class Text(val cleanedText: String) : VoiceCommandResult
    data class RewriteAction(val action: WritingAction) : VoiceCommandResult
    data object DeleteLastWord : VoiceCommandResult
    data object DeleteLastSentence : VoiceCommandResult
    data object ClearAll : VoiceCommandResult
    data object UndoLast : VoiceCommandResult
}

/**
 * Multilingual natural voice dictation cleanup engine (Rambler-grade):
 * strips hesitation fillers and stutters, applies mid-sentence self-corrections,
 * converts spoken punctuation/bullets/emojis, auto-capitalizes sentences, and detects voice edit commands.
 */
object NaturalVoiceCleaner {

    private val fillersByLang = mapOf(
        "de" to setOf("äh", "ääh", "ähm", "ähhm", "öhm", "öh", "hm", "hmm", "mhm", "ähhh", "uhm"),
        "en" to setOf("um", "umm", "uh", "uhh", "erm", "er", "ah", "hmm", "hm", "uhm"),
        "fr" to setOf("euh", "euhh", "hm", "hmm"),
        "es" to setOf("eh", "em", "hm", "hmm"),
        "it" to setOf("ehm", "eh", "hm", "hmm"),
        "pt" to setOf("hm", "hmm", "ãh"),
        "nl" to setOf("eh", "uh", "uhm", "hm", "hmm"),
        "pl" to setOf("yy", "yym", "ee", "hm", "hmm"),
        "tr" to setOf("ıı", "ee", "hm", "hmm")
    )

    private val germanClockCorrection = Regex(
        """\bum\s+(?:\d{1,2}(?::\d{2})?|ein(?:s)?|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf|dreizehn|vierzehn|fünfzehn|sechzehn|siebzehn|achtzehn|neunzehn|zwanzig)\s+uhr\s*,?\s+(?:nein\s*,?\s*warte|ich meine|besser gesagt)\s*,?\s*(?=um\s+\S+\s+uhr\b)""",
        RegexOption.IGNORE_CASE
    )
    private val germanPlaceCorrection = Regex(
        """(?i:\bnach\s+)(?-i:[\p{Lu}][\p{L}-]*)\s*,?\s+(?i:nein\s*,?\s*warte|ich meine|besser gesagt)\s*,?\s*(?=(?i:nach\s+)?[\p{Lu}][\p{L}-]*\b)"""
    )
    private val germanPhraseCorrection = Regex(
        """(?<!\.\s)\b(?:\d+(?:[.,:]\d+)?(?:\s*(?:€|euro|uhr|prozent|minuten))?|[\p{L}-]+)\s*,?\s+(?:nein\s*,?\s*warte|ach\s+nein|äh\s+nein|besser\s+gesagt|eigentlich\s+lieber)\s*,?\s*(?=\S)""",
        RegexOption.IGNORE_CASE
    )
    private val germanCommaMeanCorrection = Regex(
        """(?<![.!?]\s{0,3})\b(?:am\s+[\p{L}-]+|\d+(?:[.,:]\d+)?(?:\s*(?:€|euro|uhr|prozent|minuten))?|[\p{L}-]+)\s*,\s+ich\s+meine\s*,?\s*(?=\S)""",
        RegexOption.IGNORE_CASE
    )

    private val weekdays = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private val englishDayCorrection = Regex(
        """\b(?:$weekdays)\s*,?\s+(?:wait\s*,?\s*no|no\s*,?\s*wait|i mean)\s*,?\s*(?=(?:$weekdays)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val englishGeneralCorrection = Regex(
        """\b(?:\d{1,2}(?::\d{2})?(?:\s*(?:am|pm|a\.m\.|p\.m\.))?|\d+(?:[.,]\d+)?(?:\s*(?:dollars|euros|percent|minutes))?|[\p{L}-]+)\s*,?\s+(?:actually\s*,?\s*make\s+that|no\s*,?\s*wait|wait\s*,?\s*no|scratch\s+that)\s*,?\s*(?=\S)""",
        RegexOption.IGNORE_CASE
    )

    private val intentionalRepeatedWords = setOf(
        "sehr", "ganz", "ja", "so", "ha", "haha", "na", "nein",
        "very", "really", "bye", "no", "so", "ha", "oh"
    )
    private val consecutiveDuplicateWord = Regex("""\b(\p{L}+)[ \t]+\1(?=\s|[,!?.]|$)""", RegexOption.IGNORE_CASE)

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
            lower in setOf("lösche das letzte wort", "letztes wort löschen", "delete last word", "scratch last word") -> {
                return VoiceCommandResult.DeleteLastWord
            }
            lower in setOf("lösche den letzten satz", "letzten satz löschen", "delete last sentence", "scratch that") -> {
                return VoiceCommandResult.DeleteLastSentence
            }
            lower in setOf("alles löschen", "text löschen", "clear all", "delete all") -> {
                return VoiceCommandResult.ClearAll
            }
            lower in setOf("rückgängig", "mach das rückgängig", "undo", "undo that") -> {
                return VoiceCommandResult.UndoLast
            }
            lower in setOf(
                "mach es freundlicher", "freundlicher formulieren", "mach es lockerer",
                "make it friendly", "make it casual"
            ) -> {
                return VoiceCommandResult.RewriteAction(WritingAction.CASUAL)
            }
            lower in setOf(
                "mach es professionell", "mach es professioneller", "professioneller formulieren",
                "formeller formulieren", "make it professional", "make it formal"
            ) -> {
                return VoiceCommandResult.RewriteAction(WritingAction.PROFESSIONAL)
            }
            lower in setOf("mach es kürzer", "kürzer fassen", "make it shorter", "make it concise") -> {
                return VoiceCommandResult.RewriteAction(WritingAction.CONCISE)
            }
            lower in setOf("mach es herzlicher", "wärmer formulieren", "make it warmer", "make it emotive") -> {
                return VoiceCommandResult.RewriteAction(WritingAction.EMOTIVE)
            }
            lower in setOf(
                "fasse das zusammen", "zusammenfassen", "als stichpunkte",
                "summarize", "summarize that", "make it bullet points"
            ) -> {
                return VoiceCommandResult.RewriteAction(WritingAction.SUMMARIZE)
            }
            lower in setOf("formuliere das um", "umformulieren", "rephrase", "rephrase that") -> {
                return VoiceCommandResult.RewriteAction(WritingAction.REPHRASE)
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

    private fun applySelfCorrections(input: String, baseLang: String): String =
        when (baseLang) {
            "de" -> {
                val clockCorrected = germanClockCorrection.replace(input, "")
                val placeCorrected = germanPlaceCorrection.replace(clockCorrected) { match ->
                    if (clockCorrected.startsWith("nach ", match.range.last + 1, ignoreCase = true)) "" else "nach "
                }
                val commaCorrected = germanCommaMeanCorrection.replace(placeCorrected, "")
                germanPhraseCorrection.replace(commaCorrected, "")
            }
            "en" -> {
                val dayCorrected = englishDayCorrection.replace(input, "")
                englishGeneralCorrection.replace(dayCorrected, "")
            }
            else -> input
        }

    private fun stripFillers(input: String, baseLang: String): String {
        val fillers = fillersByLang[baseLang].orEmpty()
        val withoutFillers = Regex("""\S+""").replace(input) { match ->
            if (match.value.lowercase().trim(',', '.', '!', '?', '…') in fillers) "" else match.value
        }
        return consecutiveDuplicateWord.replace(withoutFillers) { match ->
            val word = match.groupValues[1]
            if (word.lowercase() in intentionalRepeatedWords) match.value else word
        }
    }

    private fun replaceSpokenPunctuation(input: String, baseLang: String): String {
        var text = input
        val replacements = when (baseLang) {
            "de" -> listOf(
                Regex("""\bneuer absatz\b""", RegexOption.IGNORE_CASE) to "\n\n",
                Regex("""\bneue zeile\b""", RegexOption.IGNORE_CASE) to "\n",
                Regex("""\b(?:stichpunkt|aufzählungspunkt)\b\s*""", RegexOption.IGNORE_CASE) to "\n• ",
                Regex("""\bfragezeichen\b""", RegexOption.IGNORE_CASE) to "?",
                Regex("""\bausrufezeichen\b""", RegexOption.IGNORE_CASE) to "!",
                Regex("""\bdoppelpunkt\b""", RegexOption.IGNORE_CASE) to ":",
                Regex("""\bsemikolon\b""", RegexOption.IGNORE_CASE) to ";",
                Regex("""\bkomma\b""", RegexOption.IGNORE_CASE) to ",",
                Regex("""\bpunkt\b""", RegexOption.IGNORE_CASE) to ".",
                Regex("""\bgedankenstrich\b""", RegexOption.IGNORE_CASE) to " – ",
                Regex("""\bschrägstrich\b""", RegexOption.IGNORE_CASE) to "/",
                Regex("""\blächelndes emoji\b""", RegexOption.IGNORE_CASE) to "😊",
                Regex("""\bdaumen hoch emoji\b""", RegexOption.IGNORE_CASE) to "👍"
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
                Regex("""\bbullet point\b\s*""", RegexOption.IGNORE_CASE) to "\n• ",
                Regex("""\bquestion mark\b""", RegexOption.IGNORE_CASE) to "?",
                Regex("""\bexclamation mark\b""", RegexOption.IGNORE_CASE) to "!",
                Regex("""\bexclamation point\b""", RegexOption.IGNORE_CASE) to "!",
                Regex("""\bcolon\b""", RegexOption.IGNORE_CASE) to ":",
                Regex("""\bsemicolon\b""", RegexOption.IGNORE_CASE) to ";",
                Regex("""\bcomma\b""", RegexOption.IGNORE_CASE) to ",",
                Regex("""\bperiod\b""", RegexOption.IGNORE_CASE) to ".",
                Regex("""\bdash\b""", RegexOption.IGNORE_CASE) to " – ",
                Regex("""\bslash\b""", RegexOption.IGNORE_CASE) to "/",
                Regex("""\bsmiling emoji\b""", RegexOption.IGNORE_CASE) to "😊",
                Regex("""\bthumbs up emoji\b""", RegexOption.IGNORE_CASE) to "👍"
            )
        }

        for ((pattern, symbol) in replacements) {
            text = text.replace(pattern, symbol)
        }
        return text
    }

    private fun normalizeWhitespaceAndPunctuation(input: String): String {
        return input
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex(""" *([.,!?:;])"""), "$1")
            .replace(Regex(""" *\n+ *""")) { match ->
                if (match.value.contains("\n\n")) "\n\n" else "\n"
            }
            .trim()
    }

    private fun capitalizeSentences(input: String): String {
        val sb = StringBuilder(input.length)
        var capitalizeNext = true
        for ((index, ch) in input.withIndex()) {
            if (capitalizeNext && ch.isLetter()) {
                sb.append(ch.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(ch)
                if (ch == '\n' || (ch in ".!?" && input.getOrNull(index + 1)?.isWhitespace() == true)) {
                    capitalizeNext = true
                }
            }
        }
        return sb.toString()
    }

    private fun ensureTerminalPunctuation(input: String, baseLang: String): String {
        if (input.isEmpty()) return input
        if (input.substringAfterLast('\n').startsWith("• ")) return input
        val last = input.last()
        val lastCodePoint = input.codePointBefore(input.length)
        if (last in setOf('.', '!', '?', ':', ';', '\n') ||
            Character.getType(lastCodePoint) == Character.OTHER_SYMBOL.toInt()
        ) {
            return input
        }
        val firstWord = input.trimStart().substringBefore(' ').lowercase().trim(',', '.')
        val starters = questionStartersByLang[baseLang] ?: questionStartersByLang["en"]!!
        val suffix = if (firstWord in starters) "?" else "."
        return "$input$suffix"
    }
}
