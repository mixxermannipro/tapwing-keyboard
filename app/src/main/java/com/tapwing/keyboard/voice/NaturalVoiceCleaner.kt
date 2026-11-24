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
    data object SendAction : VoiceCommandResult
}

/**
 * Multilingual natural voice dictation cleanup engine (Rambler-grade):
 * strips hesitation fillers and prefix/word stutters, applies mid-sentence & clause-level self-corrections,
 * formats spoken punctuation, emojis, bullets & ordinal numbered lists, inserts German subordinate commas,
 * auto-capitalizes sentences/lists, and detects voice editing/send/rewrite commands.
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

    private val germanDiscardClause = Regex(
        """(?:^|(?<=[.!?]\s))[^.!?\n]{1,140}?\s*,?\s*\b(?:nein\s*,?\s*vergiss\s+das|vergiss\s+das|streich\s+das|alles\s+zurück)\b\s*,?\s*(?=\S)""",
        RegexOption.IGNORE_CASE
    )
    private val englishDiscardClause = Regex(
        """(?:^|(?<=[.!?]\s))[^.!?\n]{1,140}?\s*,?\s*\b(?:never\s*mind|forget\s+that|scratch\s+all\s+that|disregard\s+that)\b\s*,?\s*(?=\S)""",
        RegexOption.IGNORE_CASE
    )

    private val germanMarker = """(?:nein\s*,?\s*warte(?:\s+mal)?|oder\s+nee|nee\s*,?\s*warte|sorry\s*,?\s*ich\s+meine|entschuldigung\s*,?\s*ich\s+meine|ich\s+meine|besser\s+gesagt|ach\s+nein|ach\s+nee|äh\s+nein|äh\s+warte|eigentlich\s+lieber)"""
    private val germanClockCorrection = Regex(
        """\bum\s+(?:\d{1,2}(?::\d{2})?|ein(?:s)?|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf|dreizehn|vierzehn|fünfzehn|sechzehn|siebzehn|achtzehn|neunzehn|zwanzig)\s+uhr\s*,?\s+$germanMarker\s*,?\s*(?=um\s+\S+\s+uhr\b)""",
        RegexOption.IGNORE_CASE
    )
    private val germanPlaceCorrection = Regex(
        """(?i:\bnach\s+)(?-i:[\p{Lu}][\p{L}-]*)\s*,?\s+(?i:$germanMarker)\s*,?\s*(?=(?i:nach\s+)?[\p{Lu}][\p{L}-]*\b)"""
    )
    private val germanPrepPairCorrection = Regex(
        """(?<![.!?]\s{0,3})\b(?:an|mit|für|bei|zu|von|auf|in|am|um)\s+(?:\d+(?:[.,:]\d+)?(?:\s*(?:€|euro|uhr|prozent|minuten|personen|leute))?|[\p{L}-]+)\s*,?\s+$germanMarker\s*,?\s*(?=(?:an|mit|für|bei|zu|von|auf|in|am|um)\s+\S)""",
        RegexOption.IGNORE_CASE
    )
    private val germanPhraseCorrection = Regex(
        """(?<!\.\s)\b(?:\d+(?:[.,:]\d+)?(?:\s*(?:€|euro|uhr|prozent|minuten|personen))?|[\p{L}-]+)\s*,?\s+(?:nein\s*,?\s*warte(?:\s+mal)?|oder\s+nee|nee\s*,?\s*warte|sorry\s*,?\s*ich\s+meine|entschuldigung\s*,?\s*ich\s+meine|ach\s+nein|ach\s+nee|äh\s+nein|äh\s+warte|besser\s+gesagt|eigentlich\s+lieber)\s*,?\s*(?=\S)""",
        RegexOption.IGNORE_CASE
    )
    private val germanCommaMeanCorrection = Regex(
        """(?<![.!?]\s{0,3})\b(?:am\s+[\p{L}-]+|\d+(?:[.,:]\d+)?(?:\s*(?:€|euro|uhr|prozent|minuten))?|[\p{L}-]+)\s*,\s+ich\s+meine\s*,?\s*(?=\S)""",
        RegexOption.IGNORE_CASE
    )

    private val weekdays = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private val englishMarker = """(?:actually\s*,?\s*make\s+that|no\s*,?\s*wait|wait\s*,?\s*no|no\s*,?\s*sorry|sorry\s*,?\s*i\s+mean|or\s+rather|scratch\s+that)"""
    private val englishDayCorrection = Regex(
        """\b(?:$weekdays)\s*,?\s+(?:$englishMarker|i\s+mean)\s*,?\s*(?=(?:$weekdays)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val englishPrepPairCorrection = Regex(
        """(?<![.!?]\s{0,3})\b(?:to|with|for|at|on|in|by|from)\s+(?:\d+(?::\d{2})?(?:\s*(?:am|pm|a\.m\.|p\.m\.))?|\d+(?:[.,]\d+)?(?:\s*(?:dollars|euros|percent|minutes|people))?|[\p{L}-]+)\s*,?\s+(?:$englishMarker|i\s+mean)\s*,?\s*(?=(?:to|with|for|at|on|in|by|from)\s+\S)""",
        RegexOption.IGNORE_CASE
    )
    private val englishGeneralCorrection = Regex(
        """\b(?:\d{1,2}(?::\d{2})?(?:\s*(?:am|pm|a\.m\.|p\.m\.))?|\d+(?:[.,]\d+)?(?:\s*(?:dollars|euros|percent|minutes))?|[\p{L}-]+)\s*,?\s+$englishMarker\s*,?\s*(?=\S)""",
        RegexOption.IGNORE_CASE
    )

    private val intentionalRepeatedWords = setOf(
        "sehr", "ganz", "ja", "so", "ha", "haha", "na", "nein",
        "very", "really", "bye", "no", "so", "ha", "oh"
    )
    private val prefixStutterRegex = Regex("""(?<=\s|^)\p{L}{1,3}-\s+(?=\p{L}{2,})""")
    private val commaDuplicateWord = Regex("""\b(\p{L}+),\s+\1(?=\s|[,!?.]|$)""", RegexOption.IGNORE_CASE)
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

    private val germanTagQuestions = Regex(""",\s*(?:oder|nicht wahr|gell|stimmt's|stimmt es|ne)$""", RegexOption.IGNORE_CASE)
    private val englishTagQuestions = Regex(""",\s*(?:right|isn't it|aren't you|don't you|won't you|okay)$""", RegexOption.IGNORE_CASE)

    private val germanSubordinateConj = Regex(
        """(?<=\p{L})\s+(?=(?:weil|obwohl|damit|sodass|sobald|falls|nachdem|bevor|während|dass)\b)""",
        RegexOption.IGNORE_CASE
    )
    private val germanCommaExcludePrev = setOf(
        "und", "oder", "aber", "als", "wie", "denn", "sondern", "außer", "nur",
        "auch", "genau", "gerade", "schon", "erst", "sehr", "so", "deshalb", "darum"
    )
    private val greetingPrefixRegex = Regex(
        """^(hallo|hi|hey|guten tag|guten morgen|guten abend|liebe grüße|viele grüße|beste grüße|mit freundlichen grüßen|hello|dear)\b""",
        RegexOption.IGNORE_CASE
    )

    private val germanOrdinalFirst = Regex("""\berstens\b""", RegexOption.IGNORE_CASE)
    private val germanOrdinalSecond = Regex("""\bzweitens\b""", RegexOption.IGNORE_CASE)
    private val germanOrdinalSplit = Regex("""\s*,?\s*\b(erstens|zweitens|drittens|viertens|fünftens)\b[:,]?\s*""", RegexOption.IGNORE_CASE)

    private val englishOrdinalPair = Regex("""(?:^|[.,:]\s*)\bfirst(?:ly)?[,:]\s+.+\bsecond(?:ly)?\b""", RegexOption.IGNORE_CASE)
    private val englishOrdinalTriple = Regex("""\bfirst(?:ly)?\b.+\bsecond(?:ly)?\b.+\bthird(?:ly)?\b""", RegexOption.IGNORE_CASE)
    private val englishOrdinalSplit = Regex("""\s*,?\s*\b(firstly|first|secondly|second|thirdly|third|fourthly|fourth)\b[:,]?\s*""", RegexOption.IGNORE_CASE)

    private val wordTokenRegex = Regex("""\p{L}+(['-]\p{L}+)?""")
    private val nonWhitespaceRegex = Regex("""\S+""")
    private val multiSpacesRegex = Regex("""[ \t]+""")
    private val spaceBeforePunctRegex = Regex(""" *([.,!?:;])""")
    private val spacesAroundNewlineRegex = Regex(""" *\n+ *""")
    private val listLineEndRegex = Regex("""^(?:•|\d+\.)\s.*""")

    private val spokenPunctByLang: Map<String, List<Pair<Regex, String>>> = mapOf(
        "de" to listOf(
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
            Regex("""\bbindestrich\b""", RegexOption.IGNORE_CASE) to "-",
            Regex("""\bschrägstrich\b""", RegexOption.IGNORE_CASE) to "/",
            Regex("""\bat zeichen\b""", RegexOption.IGNORE_CASE) to "@",
            Regex("""\banführungszeichen\b""", RegexOption.IGNORE_CASE) to "\"",
            Regex("""\bklammer auf\b""", RegexOption.IGNORE_CASE) to "(",
            Regex("""\bklammer zu\b""", RegexOption.IGNORE_CASE) to ")",
            Regex("""\blächelndes emoji\b""", RegexOption.IGNORE_CASE) to "😊",
            Regex("""\blachendes emoji\b""", RegexOption.IGNORE_CASE) to "😂",
            Regex("""\bzwinkerndes emoji\b""", RegexOption.IGNORE_CASE) to "😉",
            Regex("""\bherz emoji\b""", RegexOption.IGNORE_CASE) to "❤️",
            Regex("""\bfeuer emoji\b""", RegexOption.IGNORE_CASE) to "🔥",
            Regex("""\bfeier emoji\b""", RegexOption.IGNORE_CASE) to "🎉",
            Regex("""\bhäkchen emoji\b""", RegexOption.IGNORE_CASE) to "✅",
            Regex("""\bdaumen hoch emoji\b""", RegexOption.IGNORE_CASE) to "👍"
        ),
        "fr" to listOf(
            Regex("""\bnouveau paragraphe\b""", RegexOption.IGNORE_CASE) to "\n\n",
            Regex("""\bnouvelle ligne\b""", RegexOption.IGNORE_CASE) to "\n",
            Regex("""\bpoint d'interrogation\b""", RegexOption.IGNORE_CASE) to "?",
            Regex("""\bpoint d'exclamation\b""", RegexOption.IGNORE_CASE) to "!",
            Regex("""\bvirgule\b""", RegexOption.IGNORE_CASE) to ",",
            Regex("""\bpoint\b""", RegexOption.IGNORE_CASE) to "."
        ),
        "es" to listOf(
            Regex("""\bnuevo párrafo\b""", RegexOption.IGNORE_CASE) to "\n\n",
            Regex("""\bnueva línea\b""", RegexOption.IGNORE_CASE) to "\n",
            Regex("""\bcoma\b""", RegexOption.IGNORE_CASE) to ",",
            Regex("""\bpunto\b""", RegexOption.IGNORE_CASE) to "."
        ),
        "en" to listOf(
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
            Regex("""\bhyphen\b""", RegexOption.IGNORE_CASE) to "-",
            Regex("""\bslash\b""", RegexOption.IGNORE_CASE) to "/",
            Regex("""\bat sign\b""", RegexOption.IGNORE_CASE) to "@",
            Regex("""\bopen parenthesis\b""", RegexOption.IGNORE_CASE) to "(",
            Regex("""\bclose parenthesis\b""", RegexOption.IGNORE_CASE) to ")",
            Regex("""\bsmiling emoji\b""", RegexOption.IGNORE_CASE) to "😊",
            Regex("""\blaughing emoji\b""", RegexOption.IGNORE_CASE) to "😂",
            Regex("""\bheart emoji\b""", RegexOption.IGNORE_CASE) to "❤️",
            Regex("""\bfire emoji\b""", RegexOption.IGNORE_CASE) to "🔥",
            Regex("""\bparty emoji\b""", RegexOption.IGNORE_CASE) to "🎉",
            Regex("""\bcheck emoji\b""", RegexOption.IGNORE_CASE) to "✅",
            Regex("""\bthumbs up emoji\b""", RegexOption.IGNORE_CASE) to "👍"
        )
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
            lower in setOf("absenden", "nachricht senden", "senden", "send message", "send") -> {
                return VoiceCommandResult.SendAction
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
                "fasse das zusammen", "zusammenfassen", "als stichpunkte", "als liste",
                "mach stichpunkte daraus", "summarize", "summarize that",
                "make it bullet points", "turn into bullet points"
            ) -> {
                return VoiceCommandResult.RewriteAction(WritingAction.SUMMARIZE)
            }
            lower in setOf("formuliere das um", "umformulieren", "rephrase", "rephrase that") -> {
                return VoiceCommandResult.RewriteAction(WritingAction.REPHRASE)
            }
            lower in setOf("korrigiere das", "korrigieren", "proofread", "fix grammar") -> {
                return VoiceCommandResult.RewriteAction(WritingAction.PROOFREAD)
            }
            lower in setOf("übersetze das", "auf englisch übersetzen", "translate that", "translate to english") -> {
                return VoiceCommandResult.RewriteAction(WritingAction.TRANSLATE)
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
        text = formatSpokenOrdinalLists(text, baseLang)
        text = normalizeWhitespaceAndPunctuation(text)

        if (dictionaryStore != null && text.isNotEmpty()) {
            text = wordTokenRegex.replace(text) { match ->
                dictionaryStore.canonicalMidSentenceCasingFor(match.value, locale) ?: match.value
            }
        }

        if (autoPunctuate && text.isNotEmpty()) {
            text = insertGreetingCommas(text)
            if (baseLang == "de") {
                text = insertGermanSubordinateCommas(text)
            }
            text = capitalizeSentences(text)
            text = ensureTerminalPunctuation(text, baseLang)
        }

        return text.trim()
    }

    private fun applySelfCorrections(input: String, baseLang: String): String =
        when (baseLang) {
            "de" -> {
                val discarded = germanDiscardClause.replace(input, "")
                val clockCorrected = germanClockCorrection.replace(discarded, "")
                val placeCorrected = germanPlaceCorrection.replace(clockCorrected) { match ->
                    if (clockCorrected.startsWith("nach ", match.range.last + 1, ignoreCase = true)) "" else "nach "
                }
                val prepCorrected = germanPrepPairCorrection.replace(placeCorrected, "")
                val commaCorrected = germanCommaMeanCorrection.replace(prepCorrected, "")
                germanPhraseCorrection.replace(commaCorrected, "")
            }
            "en" -> {
                val discarded = englishDiscardClause.replace(input, "")
                val dayCorrected = englishDayCorrection.replace(discarded, "")
                val prepCorrected = englishPrepPairCorrection.replace(dayCorrected, "")
                englishGeneralCorrection.replace(prepCorrected, "")
            }
            else -> input
        }

    private fun stripFillers(input: String, baseLang: String): String {
        val withoutPrefixStutters = prefixStutterRegex.replace(input, "")
        val fillers = fillersByLang[baseLang].orEmpty()
        val withoutFillers = nonWhitespaceRegex.replace(withoutPrefixStutters) { match ->
            if (match.value.lowercase().trim(',', '.', '!', '?', '…') in fillers) "" else match.value
        }
        val withoutCommaDups = commaDuplicateWord.replace(withoutFillers) { match ->
            val word = match.groupValues[1]
            if (word.lowercase() in intentionalRepeatedWords) match.value else word
        }
        return consecutiveDuplicateWord.replace(withoutCommaDups) { match ->
            val word = match.groupValues[1]
            if (word.lowercase() in intentionalRepeatedWords) match.value else word
        }
    }

    private fun replaceSpokenPunctuation(input: String, baseLang: String): String {
        var text = input
        val replacements = spokenPunctByLang[baseLang] ?: spokenPunctByLang["en"].orEmpty()
        for ((pattern, symbol) in replacements) {
            text = text.replace(pattern, symbol)
        }
        return text
    }

    private fun formatSpokenOrdinalLists(input: String, baseLang: String): String {
        return when (baseLang) {
            "de" -> {
                if (!germanOrdinalFirst.containsMatchIn(input) || !germanOrdinalSecond.containsMatchIn(input)) {
                    return input
                }
                germanOrdinalSplit.replace(input) { m ->
                    val num = when (m.groupValues[1].lowercase()) {
                        "erstens" -> "1."
                        "zweitens" -> "2."
                        "drittens" -> "3."
                        "viertens" -> "4."
                        "fünftens" -> "5."
                        else -> m.groupValues[1]
                    }
                    "\n$num "
                }.trimStart('\n', ' ')
            }
            "en" -> {
                if (!englishOrdinalPair.containsMatchIn(input) && !englishOrdinalTriple.containsMatchIn(input)) {
                    return input
                }
                englishOrdinalSplit.replace(input) { m ->
                    val num = when (m.groupValues[1].lowercase()) {
                        "first", "firstly" -> "1."
                        "second", "secondly" -> "2."
                        "third", "thirdly" -> "3."
                        "fourth", "fourthly" -> "4."
                        else -> m.groupValues[1]
                    }
                    "\n$num "
                }.trimStart('\n', ' ')
            }
            else -> input
        }
    }

    private fun normalizeWhitespaceAndPunctuation(input: String): String {
        return input
            .replace(multiSpacesRegex, " ")
            .replace(spaceBeforePunctRegex, "$1")
            .replace(spacesAroundNewlineRegex) { match ->
                if (match.value.contains("\n\n")) "\n\n" else "\n"
            }
            .trim()
    }

    private fun insertGreetingCommas(input: String): String {
        val idx = input.indexOf("\n\n")
        if (idx <= 0) return input
        val firstLine = input.substring(0, idx).trimEnd()
        if (firstLine.isEmpty() || firstLine.last() in setOf(',', '.', '!', '?', ':')) return input
        val words = firstLine.split(' ').filter { it.isNotEmpty() }
        if (words.size in 1..4 && greetingPrefixRegex.containsMatchIn(firstLine)) {
            return firstLine + "," + input.substring(idx)
        }
        return input
    }

    private fun insertGermanSubordinateCommas(input: String): String {
        if (input.length < 18) return input
        return germanSubordinateConj.replace(input) { match ->
            val before = input.substring(0, match.range.first)
            val currentSentence = before.substringAfterLast('.').substringAfterLast('!').substringAfterLast('?').substringAfterLast('\n')
            val words = currentSentence.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val lastWord = words.lastOrNull()?.lowercase()?.trim(',', '.', '!', '?', ':').orEmpty()
            if (words.size >= 3 && lastWord !in germanCommaExcludePrev) ", " else match.value
        }
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
                when {
                    ch == '\n' -> capitalizeNext = true
                    ch in ".!?" && input.getOrNull(index + 1)?.isWhitespace() == true -> {
                        val beforeDot = input.substring(0, index)
                        val lineTail = beforeDot.substringAfterLast('\n')
                        val isOrdinalBullet = lineTail.all { it.isDigit() } && lineTail.isNotEmpty()
                        capitalizeNext = true
                        if (isOrdinalBullet) {
                            // Keep capitalizing the first letter after "1. "
                            capitalizeNext = true
                        }
                    }
                    ch == '•' && input.getOrNull(index + 1) == ' ' -> capitalizeNext = true
                }
            }
        }
        return sb.toString()
    }

    private fun ensureTerminalPunctuation(input: String, baseLang: String): String {
        if (input.isEmpty()) return input
        val lastLine = input.substringAfterLast('\n')
        if (listLineEndRegex.matches(lastLine)) return input
        val last = input.last()
        val lastCodePoint = input.codePointBefore(input.length)
        if (last in setOf('.', '!', '?', ':', ';', '\n') ||
            Character.getType(lastCodePoint) == Character.OTHER_SYMBOL.toInt()
        ) {
            return input
        }
        val tagQuestion = if (baseLang == "de") germanTagQuestions.containsMatchIn(input)
        else englishTagQuestions.containsMatchIn(input)
        if (tagQuestion) return "$input?"

        val firstWord = input.trimStart().substringBefore(' ').lowercase().trim(',', '.')
        val starters = questionStartersByLang[baseLang] ?: questionStartersByLang["en"]!!
        val suffix = if (firstWord in starters) "?" else "."
        return "$input$suffix"
    }
}
