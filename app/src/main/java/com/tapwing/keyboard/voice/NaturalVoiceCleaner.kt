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
    data object SelectAll : VoiceCommandResult
    data object CopyAll : VoiceCommandResult
    data object PasteClipboard : VoiceCommandResult
    data object SendAction : VoiceCommandResult
}

/**
 * Multilingual natural voice dictation cleanup engine (Rambler-grade + Gboard-beating):
 * - strips hesitation fillers and prefix/word stutters (with safe bilingual DE+EN code-switching),
 * - applies mid-sentence & clause-level self-corrections in primary and secondary languages,
 * - automatically detects spoken bullet lists, checklists, numbered points & ordinal enumerations,
 * - formats spoken punctuation, times, emails & emojis, inserts German subordinate commas,
 * - applies personal dictionary casing, learned voice corrections & conservative fuzzy name matching,
 * - detects voice editing, clipboard, send, and AI rewrite commands.
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
    // Safe English hesitation fillers that never collide with German words ("um" = preposition, "er" = pronoun).
    private val bilingualSafeEnglishFillers = setOf("umm", "uh", "uhh", "erm", "uhm")

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
    private val germanOrdinalSplit = Regex(
        """\s*,?\s*\b(erstens|zweitens|drittens|viertens|fünftens|sechstens|siebtens|achtens)\b[:,]?\s*""",
        RegexOption.IGNORE_CASE
    )

    private val englishOrdinalPair = Regex("""(?:^|[.,:]\s*)\bfirst(?:ly)?[,:]\s+.+\bsecond(?:ly)?\b""", RegexOption.IGNORE_CASE)
    private val englishOrdinalTriple = Regex("""\bfirst(?:ly)?\b.+\bsecond(?:ly)?\b.+\bthird(?:ly)?\b""", RegexOption.IGNORE_CASE)
    private val englishOrdinalSplit = Regex(
        """\s*,?\s*\b(firstly|first|secondly|second|thirdly|third|fourthly|fourth|fifthly|fifth|sixthly|sixth)\b[:,]?\s*""",
        RegexOption.IGNORE_CASE
    )

    // Automatic bullet-point detection patterns
    private val trailingBulletSuffixRegex = Regex(
        """\s*,?\s*\b(?:als stichpunkte|als liste|als aufzählung|as bullet points|as a bullet list|as a list|as bullets)\b[.!?]*$""",
        RegexOption.IGNORE_CASE
    )
    private val spokenNumberedPointRegex = Regex(
        """\b(?:punkt|nummer|point|number|item)\s+(?:[1-9]|eins|zwei|drei|vier|fünf|sechs|sieben|acht|one|two|three|four|five|six)\b[:,]?\s*""",
        RegexOption.IGNORE_CASE
    )
    private val explicitColonListHeaderRegex = Regex(
        """^([^:\n]{2,50}):\s+(.+)$"""
    )
    private val spokenListLeadInRegex = Regex(
        """^((?:einkaufsliste|aufgabenliste|aufgaben|to-?dos?|checkliste|packliste|zutaten|tagesordnung|agenda|stichpunkte|notizen|ideen|folgende punkte|shopping list|grocery list|groceries|task list|tasks|checklist|packing list|ingredients|action items|key points)(?:\s+(?:für|fuer|for)\s+[\p{L}\d-]+(?:\s+[\p{L}\d-]+)?)?)(?:[:,]|\s+)(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val listItemSplitRegex = Regex(
        """\s*(?:;|,(?:\s*(?:und|sowie|and)\b)?|\s+(?:und|sowie|and)\s+)\s*""",
        RegexOption.IGNORE_CASE
    )

    // Smart clock time with minutes ("14 uhr 30" -> "14:30 Uhr") and spoken email
    private val germanTimeWithMinutesRegex = Regex(
        """\b([01]?\d|2[0-3])\s+uhr\s+([0-5]\d)\b""",
        RegexOption.IGNORE_CASE
    )
    private val spokenEmailRegex = Regex(
        """\b([a-z0-9._+-]{2,})\s+(?:at|@)\s+([a-z0-9-]+(?:\s+(?:punkt|dot)\s+[a-z0-9-]+)*)\s+(?:punkt|dot)\s+(de|com|org|net|io|ch|at|eu|ai|dev)\b""",
        RegexOption.IGNORE_CASE
    )

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
            Regex("""\b(?:stichpunkt|aufzählungspunkt|spiegelstrich|nächster punkt)\b\s*""", RegexOption.IGNORE_CASE) to "\n• ",
            Regex("""\bfragezeichen\b""", RegexOption.IGNORE_CASE) to "?",
            Regex("""\bausrufezeichen\b""", RegexOption.IGNORE_CASE) to "!",
            Regex("""\bdoppelpunkt\b""", RegexOption.IGNORE_CASE) to ":",
            Regex("""\bsemikolon\b""", RegexOption.IGNORE_CASE) to ";",
            Regex("""\bkomma\b""", RegexOption.IGNORE_CASE) to ",",
            Regex("""\bpunkt\b(?!\s+(?:[1-9]|eins|zwei|drei|vier|fünf|sechs)\b)""", RegexOption.IGNORE_CASE) to ".",
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
            Regex("""\bdaumen hoch emoji\b""", RegexOption.IGNORE_CASE) to "👍",
            Regex("""\brakete emoji\b""", RegexOption.IGNORE_CASE) to "🚀",
            Regex("""\bstern emoji\b""", RegexOption.IGNORE_CASE) to "⭐",
            Regex("""\bdanke emoji\b""", RegexOption.IGNORE_CASE) to "🙏"
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
            Regex("""\b(?:bullet point|next point)\b\s*""", RegexOption.IGNORE_CASE) to "\n• ",
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
            Regex("""\bthumbs up emoji\b""", RegexOption.IGNORE_CASE) to "👍",
            Regex("""\brocket emoji\b""", RegexOption.IGNORE_CASE) to "🚀",
            Regex("""\bstar emoji\b""", RegexOption.IGNORE_CASE) to "⭐",
            Regex("""\bpray emoji\b""", RegexOption.IGNORE_CASE) to "🙏"
        )
    )

    // Safe English multi-word punctuation cues usable during multilingual DE+EN speech without colliding with normal words.
    private val bilingualSafeEnglishPunct: List<Pair<Regex, String>> = listOf(
        Regex("""\bnew paragraph\b""", RegexOption.IGNORE_CASE) to "\n\n",
        Regex("""\bnew line\b""", RegexOption.IGNORE_CASE) to "\n",
        Regex("""\b(?:bullet point|next point)\b\s*""", RegexOption.IGNORE_CASE) to "\n• ",
        Regex("""\bquestion mark\b""", RegexOption.IGNORE_CASE) to "?",
        Regex("""\bexclamation (?:mark|point)\b""", RegexOption.IGNORE_CASE) to "!"
    )

    fun parseCommandOrClean(
        rawTranscript: String,
        locale: String,
        removeFillers: Boolean = true,
        autoPunctuate: Boolean = true,
        dictionaryStore: AdaptiveDictionaryStore? = null,
        multilingual: Boolean = false,
        autoBulletLists: Boolean = true
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
            lower in setOf("alles markieren", "alles auswählen", "select all") -> {
                return VoiceCommandResult.SelectAll
            }
            lower in setOf("alles kopieren", "text kopieren", "copy all") -> {
                return VoiceCommandResult.CopyAll
            }
            lower in setOf("einfügen", "aus zwischenablage einfügen", "paste", "paste clipboard") -> {
                return VoiceCommandResult.PasteClipboard
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
                "als stichpunkte", "als liste", "mach stichpunkte daraus", "stichpunkte erstellen",
                "make it bullet points", "turn into bullet points", "bullet points", "bullet list"
            ) -> {
                return VoiceCommandResult.RewriteAction(WritingAction.BULLET_LIST)
            }
            lower in setOf("fasse das zusammen", "zusammenfassen", "summarize", "summarize that") -> {
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
                dictionaryStore = dictionaryStore,
                multilingual = multilingual,
                autoBulletLists = autoBulletLists
            )
        )
    }

    fun clean(
        rawTranscript: String,
        locale: String,
        removeFillers: Boolean = true,
        autoPunctuate: Boolean = true,
        dictionaryStore: AdaptiveDictionaryStore? = null,
        multilingual: Boolean = false,
        autoBulletLists: Boolean = true
    ): String {
        var text = rawTranscript.trim()
        if (text.isEmpty()) return ""
        val baseLang = KeyboardLocales.baseLang(locale)

        text = applySelfCorrections(text, baseLang, multilingual)

        if (removeFillers) {
            text = stripFillers(text, baseLang, multilingual)
        }

        text = formatSmartEntities(text, baseLang)
        text = replaceSpokenPunctuation(text, baseLang, multilingual)
        text = formatSpokenOrdinalLists(text, baseLang, multilingual)
        if (autoBulletLists) {
            text = formatAutomaticBulletLists(text)
        }
        text = normalizeWhitespaceAndPunctuation(text)

        if (dictionaryStore != null && text.isNotEmpty()) {
            text = applyPersonalizedDictionary(text, locale, dictionaryStore)
        }

        if (autoPunctuate && text.isNotEmpty()) {
            text = insertGreetingCommas(text)
            if (baseLang == "de") {
                text = insertGermanSubordinateCommas(text)
            }
            text = capitalizeSentences(text)
            text = ensureTerminalPunctuation(text, baseLang, multilingual)
        }

        return text.trim()
    }

    private fun applyPersonalizedDictionary(
        input: String,
        locale: String,
        store: AdaptiveDictionaryStore
    ): String {
        return wordTokenRegex.replace(input) { match ->
            val word = match.value
            val voiceFix = store.preferredVoiceCorrectionFor(word)
            if (voiceFix != null) return@replace voiceFix
            val canonical = store.canonicalMidSentenceCasingFor(word, locale)
            if (canonical != null) return@replace canonical
            store.bestFuzzyCustomMatch(word, locale) ?: word
        }
    }

    private fun applySelfCorrections(input: String, baseLang: String, multilingual: Boolean): String {
        var text = when (baseLang) {
            "de" -> applyGermanSelfCorrections(input)
            "en" -> applyEnglishSelfCorrections(input)
            else -> input
        }
        if (multilingual) {
            if (baseLang != "en") text = applyEnglishSelfCorrections(text)
            if (baseLang != "de") text = applyGermanSelfCorrections(text)
        }
        return text
    }

    private fun applyGermanSelfCorrections(input: String): String {
        val discarded = germanDiscardClause.replace(input, "")
        val clockCorrected = germanClockCorrection.replace(discarded, "")
        val placeCorrected = germanPlaceCorrection.replace(clockCorrected) { match ->
            if (clockCorrected.startsWith("nach ", match.range.last + 1, ignoreCase = true)) "" else "nach "
        }
        val prepCorrected = germanPrepPairCorrection.replace(placeCorrected, "")
        val commaCorrected = germanCommaMeanCorrection.replace(prepCorrected, "")
        return germanPhraseCorrection.replace(commaCorrected, "")
    }

    private fun applyEnglishSelfCorrections(input: String): String {
        val discarded = englishDiscardClause.replace(input, "")
        val dayCorrected = englishDayCorrection.replace(discarded, "")
        val prepCorrected = englishPrepPairCorrection.replace(dayCorrected, "")
        return englishGeneralCorrection.replace(prepCorrected, "")
    }

    private fun stripFillers(input: String, baseLang: String, multilingual: Boolean): String {
        val withoutPrefixStutters = prefixStutterRegex.replace(input, "")
        val primaryFillers = fillersByLang[baseLang].orEmpty()
        val fillers = when {
            !multilingual -> primaryFillers
            baseLang == "de" -> primaryFillers + bilingualSafeEnglishFillers
            baseLang == "en" -> primaryFillers + fillersByLang["de"].orEmpty()
            else -> primaryFillers + bilingualSafeEnglishFillers
        }
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

    private fun formatSmartEntities(input: String, baseLang: String): String {
        var text = input
        // Fast-path guards so sub-millisecond benchmark stays untouched for ordinary prose
        if (text.contains(" at ", ignoreCase = true) || text.contains('@')) {
            text = spokenEmailRegex.replace(text) { m ->
                val user = m.groupValues[1].lowercase()
                val domain = m.groupValues[2].lowercase()
                    .replace(Regex("""\s+(?:punkt|dot)\s+"""), ".")
                    .replace(" ", "")
                val tld = m.groupValues[3].lowercase()
                "$user@$domain.$tld"
            }
        }
        if (baseLang == "de" && text.contains("uhr", ignoreCase = true)) {
            text = germanTimeWithMinutesRegex.replace(text) { m ->
                "${m.groupValues[1]}:${m.groupValues[2]} Uhr"
            }
        }
        return text
    }

    private fun replaceSpokenPunctuation(input: String, baseLang: String, multilingual: Boolean): String {
        var text = input
        val replacements = spokenPunctByLang[baseLang] ?: spokenPunctByLang["en"].orEmpty()
        for ((pattern, symbol) in replacements) {
            text = text.replace(pattern, symbol)
        }
        if (multilingual && baseLang != "en") {
            for ((pattern, symbol) in bilingualSafeEnglishPunct) {
                text = text.replace(pattern, symbol)
            }
        }
        return text
    }

    private fun formatSpokenOrdinalLists(input: String, baseLang: String, multilingual: Boolean): String {
        var text = input
        if (baseLang == "de" || multilingual) {
            if (germanOrdinalFirst.containsMatchIn(text) && germanOrdinalSecond.containsMatchIn(text)) {
                text = germanOrdinalSplit.replace(text) { m ->
                    val num = when (m.groupValues[1].lowercase()) {
                        "erstens" -> "1."
                        "zweitens" -> "2."
                        "drittens" -> "3."
                        "viertens" -> "4."
                        "fünftens" -> "5."
                        "sechstens" -> "6."
                        "siebtens" -> "7."
                        "achtens" -> "8."
                        else -> m.groupValues[1]
                    }
                    "\n$num "
                }.trimStart('\n', ' ')
            }
        }
        if (baseLang == "en" || multilingual) {
            if (englishOrdinalPair.containsMatchIn(text) || englishOrdinalTriple.containsMatchIn(text)) {
                text = englishOrdinalSplit.replace(text) { m ->
                    val num = when (m.groupValues[1].lowercase()) {
                        "first", "firstly" -> "1."
                        "second", "secondly" -> "2."
                        "third", "thirdly" -> "3."
                        "fourth", "fourthly" -> "4."
                        "fifth", "fifthly" -> "5."
                        "sixth", "sixthly" -> "6."
                        else -> m.groupValues[1]
                    }
                    "\n$num "
                }.trimStart('\n', ' ')
            }
        }
        return text
    }

    /**
     * Automatically formats spoken bullet lists & enumerations into clean "• Item" lines:
     * 1) Inline trailing trigger ("Milch, Eier, Brot als Stichpunkte" / "... as bullet points")
     * 2) Repeated numbered markers ("Punkt 1 Budget Punkt 2 Zeitplan", "Nummer 1 ... Nummer 2 ...")
     * 3) Recognized list lead-ins ("Einkaufsliste: Milch, Eier, Brot und Butter",
     *    "Aufgaben für heute: Mails beantworten, Code reviewen und einkaufen",
     *    "Shopping list: milk, eggs and coffee")
     */
    internal fun formatAutomaticBulletLists(input: String): String {
        if (input.isBlank() || input.contains("\n• ") || input.contains("\n1. ")) return input

        // 1) Inline trailing list cue ("... als Stichpunkte" / "... as bullet points")
        val trailingMatch = trailingBulletSuffixRegex.find(input)
        if (trailingMatch != null) {
            val body = input.substring(0, trailingMatch.range.first).trim().trimEnd(',', ':', ';')
            val formatted = tryFormatHeaderOrItemsAsBullets(body, minItemsWithoutColon = 2)
            if (formatted != null) return formatted
        }

        // 2) Repeated spoken numbered points ("Punkt 1 ... Punkt 2 ...", "Nummer 1 ... Nummer 2 ...", "Item 1 ... Item 2 ...")
        if (input.contains("punkt", ignoreCase = true) ||
            input.contains("nummer", ignoreCase = true) ||
            input.contains("point", ignoreCase = true) ||
            input.contains("number", ignoreCase = true) ||
            input.contains("item", ignoreCase = true)
        ) {
            val matches = spokenNumberedPointRegex.findAll(input).toList()
            if (matches.size >= 2) {
                val lead = input.substring(0, matches.first().range.first).trim().trimEnd(',', ';')
                val items = matches.mapIndexedNotNull { idx, m ->
                    val start = m.range.last + 1
                    val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else input.length
                    input.substring(start, end).trim().trimEnd(',', ';', '.').takeIf { it.isNotEmpty() }
                }
                if (items.size >= 2) {
                    val bullets = items.joinToString("\n") { "• $it" }
                    return if (lead.isNotEmpty()) {
                        val header = if (lead.endsWith(':')) lead else "$lead:"
                        "$header\n$bullets"
                    } else {
                        bullets
                    }
                }
            }
        }

        // 3) Spoken list lead-in with or without explicit colon
        val leadInMatch = spokenListLeadInRegex.find(input)
        if (leadInMatch != null) {
            val header = leadInMatch.groupValues[1].trim()
            val rest = leadInMatch.groupValues[2].trim()
            val hasColon = input.getOrNull(leadInMatch.groups[1]!!.range.last + 1) == ':'
            val items = splitConciseListItems(rest, minItems = if (hasColon) 2 else 3)
            if (items != null) {
                return "$header:\n" + items.joinToString("\n") { "• $it" }
            }
        }

        // 4) Generic short header with explicit colon ("Für das Meeting: Design, Budget und Release")
        if (input.contains(':')) {
            val colonMatch = explicitColonListHeaderRegex.find(input)
            if (colonMatch != null) {
                val header = colonMatch.groupValues[1].trim()
                val rest = colonMatch.groupValues[2].trim()
                val headerWords = header.split(' ').count { it.isNotBlank() }
                if (headerWords in 1..6 && !header.contains('.') && !header.contains('?')) {
                    val items = splitConciseListItems(rest, minItems = 3)
                    if (items != null) {
                        return "$header:\n" + items.joinToString("\n") { "• $it" }
                    }
                }
            }
        }

        return input
    }

    private fun tryFormatHeaderOrItemsAsBullets(body: String, minItemsWithoutColon: Int): String? {
        if (body.isBlank()) return null
        val colonMatch = explicitColonListHeaderRegex.find(body)
        if (colonMatch != null) {
            val header = colonMatch.groupValues[1].trim()
            val rest = colonMatch.groupValues[2].trim()
            val items = splitConciseListItems(rest, minItems = 2, maxWordsPerItem = 12)
            if (items != null) {
                return "$header:\n" + items.joinToString("\n") { "• $it" }
            }
        }
        val items = splitConciseListItems(body, minItems = minItemsWithoutColon, maxWordsPerItem = 12)
        return items?.joinToString("\n") { "• $it" }
    }

    private fun splitConciseListItems(
        raw: String,
        minItems: Int,
        maxWordsPerItem: Int = 7
    ): List<String>? {
        val cleaned = raw.trim().trimEnd('.', '!', '?')
        if (cleaned.isEmpty()) return null
        val parts = listItemSplitRegex.split(cleaned)
            .map { it.trim().trimEnd(',', ';', '.') }
            .filter { it.isNotEmpty() }
        if (parts.size < minItems) return null
        // Guardrail: reject normal prose clauses that happen to have commas and "und"
        if (parts.any { part -> part.split(' ').count { it.isNotBlank() } > maxWordsPerItem }) {
            return null
        }
        return parts
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
                        capitalizeNext = true
                    }
                    ch == '•' && input.getOrNull(index + 1) == ' ' -> capitalizeNext = true
                }
            }
        }
        return sb.toString()
    }

    private fun ensureTerminalPunctuation(input: String, baseLang: String, multilingual: Boolean): String {
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
        val tagQuestion = when {
            multilingual -> germanTagQuestions.containsMatchIn(input) || englishTagQuestions.containsMatchIn(input)
            baseLang == "de" -> germanTagQuestions.containsMatchIn(input)
            else -> englishTagQuestions.containsMatchIn(input)
        }
        if (tagQuestion) return "$input?"

        val firstWord = input.trimStart().substringBefore(' ').lowercase().trim(',', '.')
        val starters = questionStartersByLang[baseLang] ?: questionStartersByLang["en"]!!
        val isQuestion = firstWord in starters ||
            (multilingual && firstWord in questionStartersByLang["en"].orEmpty()) ||
            (multilingual && baseLang != "de" && firstWord in questionStartersByLang["de"].orEmpty())
        val suffix = if (isQuestion) "?" else "."
        return "$input$suffix"
    }
}
