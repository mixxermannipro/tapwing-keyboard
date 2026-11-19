package com.tapwing.keyboard.dictionary

import kotlin.math.min

data class SuggestionChip(
    val text: String,
    val isCorrection: Boolean = false,
    val isGrammarFix: Boolean = false,
    val isEmoji: Boolean = false,
    val isShortcut: Boolean = false,
    val isMath: Boolean = false,
    val replaceCharCount: Int = 0
)

data class GrammarHint(
    val description: String,
    val replaceCharCount: Int,
    val replacement: String
)

object SuggestionEngine {

    private val germanTypoMap = mapOf(
        "udn" to "und",
        "nciht" to "nicht",
        "niht" to "nicht",
        "ihc" to "ich",
        "siht" to "sieht",
        "vieleicht" to "vielleicht",
        "vilicht" to "vielleicht",
        "warumm" to "warum",
        "morgn" to "morgen",
        "heut" to "heute",
        "wiedre" to "wieder",
        "weider" to "wieder",
        "danek" to "danke",
        "bitet" to "bitte",
        "halol" to "hallo",
        "gutenmorgen" to "guten Morgen",
        "schon" to "schön",
        "konnen" to "können",
        "konnte" to "könnte",
        "mochte" to "möchte",
        "moechte" to "möchte",
        "fuer" to "für",
        "ueber" to "über",
        "groesse" to "Größe",
        "strasse" to "Straße",
        "bisschen" to "bisschen",
        "eigentlich" to "eigentlich",
        "eignentlich" to "eigentlich",
        "natuerlich" to "natürlich",
        "naechste" to "nächste",
        "spater" to "später",
        "spaeter" to "später",
        "frueher" to "früher",
        "glucklich" to "glücklich",
        "gluecklich" to "glücklich",
        "wuensche" to "wünsche",
        "gruesse" to "Grüße",
        "geschaeft" to "Geschäft",
        "ubrigens" to "übrigens",
        "uebrigens" to "übrigens",
        "standard" to "Standard",
        "standart" to "Standard",
        "seid" to "seid"
    )

    private val englishTypoMap = mapOf(
        "teh" to "the",
        "hte" to "the",
        "adn" to "and",
        "nad" to "and",
        "recieve" to "receive",
        "beleive" to "believe",
        "seperate" to "separate",
        "definately" to "definitely",
        "definatly" to "definitely",
        "untill" to "until",
        "becuase" to "because",
        "beacuse" to "because",
        "thier" to "their",
        "wehn" to "when",
        "wiht" to "with",
        "wont" to "won't",
        "dont" to "don't",
        "cant" to "can't",
        "im" to "I'm",
        "ive" to "I've",
        "youre" to "you're",
        "didnt" to "didn't",
        "isnt" to "isn't",
        "accomodate" to "accommodate",
        "occured" to "occurred",
        "tommorow" to "tomorrow",
        "tomorow" to "tomorrow",
        "thx" to "thanks",
        "plz" to "please"
    )

    private val germanCapitalNouns = setOf(
        "zeit", "tag", "jahr", "woche", "monat", "stunde", "minute", "uhr", "termin",
        "nachricht", "text", "bild", "foto", "handy", "telefon", "antwort", "frage",
        "problem", "lösung", "projekt", "arbeit", "büro", "haus", "auto", "weg",
        "freund", "familie", "kollege", "kunde", "meeting", "kaffee", "wasser",
        "idee", "grund", "beispiel", "dokument", "tastatur", "sprache", "einstellung"
    )

    private val germanWords = listOf(
        "aber", "alle", "alles", "also", "am", "an", "andere", "auch", "auf", "aus",
        "bei", "beide", "beim", "bin", "bis", "bitte", "bleiben", "brauchen", "bringen",
        "da", "dabei", "dafür", "damit", "danach", "danke", "dann", "darüber", "das",
        "dass", "dein", "deine", "dem", "den", "denn", "der", "des", "deshalb", "dich",
        "die", "diese", "dieser", "dieses", "dir", "doch", "dort", "du", "durch",
        "eben", "eigentlich", "ein", "eine", "einem", "einen", "einer", "einfach",
        "einige", "einmal", "endlich", "entweder", "er", "erst", "es", "etwas", "euch",
        "falls", "fast", "fertig", "finden", "frage", "frei", "freundlich", "für",
        "ganz", "gar", "geben", "gegen", "gehen", "gemacht", "genau", "gerade", "gern",
        "gerne", "gestern", "gibt", "gleich", "groß", "grüße", "gut", "gute", "guten",
        "habe", "haben", "hallo", "halt", "hast", "hat", "hatte", "hätte", "heute",
        "hier", "hilfe", "hin", "hinter", "hoffentlich",
        "ich", "ihm", "ihn", "ihnen", "ihr", "ihre", "im", "immer", "in", "info",
        "ist", "ja", "jahr", "jede", "jeder", "jedes", "jemand", "jetzt",
        "kann", "kannst", "kein", "keine", "keinen", "klar", "klein", "kommen", "kommt",
        "können", "könnte", "kurz",
        "lange", "lassen", "leider", "lesen", "letzte", "leute", "lieb", "liebe", "los",
        "machen", "macht", "mal", "man", "mehr", "mein", "meine", "meinen", "mich",
        "mir", "mit", "möchte", "möglich", "morgen", "muss", "müssen",
        "nach", "nachricht", "nächste", "natürlich", "nein", "neu", "neue", "nicht",
        "nichts", "nie", "noch", "normal", "nun", "nur",
        "ob", "oder", "ohne", "okay", "perfekt", "platz", "problem", "projekt",
        "recht", "richtig", "ruhig",
        "sagen", "sagt", "schade", "schauen", "schicken", "schnell", "schon", "schön",
        "schreiben", "sehr", "sein", "seine", "seit", "selbst", "sicher", "sie",
        "sind", "so", "sofort", "sogar", "soll", "sollen", "sollte", "sondern",
        "sonst", "später", "sprechen", "stadt", "stark", "stehen", "stellen", "stimmt",
        "super", "tag", "tastatur", "teil", "telefon", "termin", "text", "trotzdem",
        "tun", "über", "überhaupt", "übrigens", "uhr", "um", "und", "uns", "unser",
        "unter", "viel", "viele", "vielleicht", "von", "vor", "vorbei",
        "wahr", "während", "wann", "war", "wäre", "warum", "was", "wasser", "weg",
        "wegen", "weil", "weiter", "welche", "welt", "wenig", "wenn", "wer", "werden",
        "wichtig", "wie", "wieder", "will", "wir", "wird", "wirklich", "wissen", "wo",
        "woche", "wohl", "wollen", "wollte", "wünschen", "würde",
        "zeit", "zu", "zuerst", "zufrieden", "zum", "zur", "zurück", "zusammen", "zwei",
        "Hallo", "Danke", "Bitte", "Guten", "Morgen", "Abend", "Grüße", "Termin",
        "Nachricht", "Projekt", "Woche", "Uhr", "Arbeit", "Hilfe", "Antwort", "Frage"
    )

    private val englishWords = listOf(
        "about", "above", "actually", "after", "again", "against", "all", "almost",
        "already", "also", "always", "among", "and", "another", "answer", "any",
        "anyone", "anything", "around", "ask", "available", "away",
        "back", "because", "been", "before", "being", "believe", "best", "better",
        "between", "both", "bring", "build", "but", "called", "can", "change",
        "check", "clear", "close", "come", "comment", "complete", "confirm", "could",
        "day", "definitely", "details", "did", "different", "done", "don't", "down",
        "during", "each", "early", "easy", "email", "enough", "even", "every",
        "everyone", "everything", "example", "family", "feel", "few", "find", "fine",
        "first", "follow", "for", "forward", "friend", "from", "full", "funny",
        "get", "give", "going", "good", "great", "group", "guess",
        "had", "hand", "happy", "hard", "has", "have", "hello", "help", "here",
        "hey", "high", "home", "hope", "hour", "how", "however",
        "idea", "important", "improve", "information", "instead", "into", "issue",
        "just", "keep", "keyboard", "kind", "know", "last", "later", "learn", "leave",
        "less", "let", "letter", "life", "like", "line", "list", "little", "long",
        "look", "love", "made", "make", "many", "maybe", "mean", "meeting", "message",
        "might", "minute", "moment", "money", "month", "more", "morning", "most",
        "much", "must", "name", "near", "need", "never", "new", "next", "nice",
        "night", "nothing", "notice", "now", "number",
        "often", "okay", "once", "only", "open", "option", "order", "other", "our",
        "out", "over", "own", "part", "people", "perfect", "person", "phone", "place",
        "plan", "please", "point", "possible", "problem", "project", "put",
        "question", "quick", "quickly", "quite", "ready", "really", "reason",
        "receive", "remember", "reply", "report", "review", "right", "room",
        "said", "same", "save", "say", "schedule", "school", "search", "see", "seem",
        "send", "sent", "separate", "service", "share", "should", "show", "since",
        "small", "some", "someone", "something", "soon", "sorry", "sound", "speak",
        "start", "state", "still", "stop", "story", "sure", "system",
        "take", "talk", "team", "tell", "text", "thank", "thanks", "that", "their",
        "them", "then", "there", "these", "they", "thing", "think", "this", "those",
        "though", "thought", "through", "time", "today", "together", "tomorrow", "too",
        "true", "try", "turn", "under", "understand", "until", "update", "used",
        "useful", "usually", "very", "voice", "wait", "want", "water", "way", "week",
        "welcome", "well", "went", "were", "what", "when", "where", "whether",
        "which", "while", "who", "why", "will", "with", "without", "word", "work",
        "working", "world", "would", "write", "writing", "wrong", "year", "yes",
        "yesterday", "you", "your", "you're", "Hello", "Thanks", "Please", "Today"
    )

    private val emojiTriggers = mapOf(
        "danke" to "🙏",
        "thanks" to "🙏",
        "thank" to "🙏",
        "herz" to "❤️",
        "liebe" to "❤️",
        "love" to "❤️",
        "lachen" to "😂",
        "haha" to "😂",
        "lustig" to "😂",
        "funny" to "😂",
        "feuer" to "🔥",
        "fire" to "🔥",
        "krass" to "🔥",
        "ok" to "👍",
        "okay" to "👍",
        "top" to "👍",
        "super" to "✨",
        "perfekt" to "✨",
        "perfect" to "✨",
        "stern" to "⭐",
        "star" to "⭐",
        "kaffee" to "☕",
        "coffee" to "☕",
        "sonne" to "☀️",
        "sun" to "☀️",
        "party" to "🎉",
        "glückwunsch" to "🎉",
        "congrats" to "🎉",
        "musik" to "🎵",
        "music" to "🎵",
        "idee" to "💡",
        "idea" to "💡",
        "zeit" to "⏰",
        "time" to "⏰",
        "uhr" to "⏰",
        "geld" to "💶",
        "money" to "💵",
        "hallo" to "👋",
        "hello" to "👋",
        "hey" to "👋",
        "traurig" to "😢",
        "sad" to "😢",
        "check" to "✅",
        "erledigt" to "✅",
        "done" to "✅"
    )

    fun suggestionsFor(
        textBeforeCursor: String,
        locale: String,
        includeEmoji: Boolean = true,
        includeGrammarChips: Boolean = true,
        includeMath: Boolean = true,
        dictionaryStore: AdaptiveDictionaryStore? = null
    ): List<SuggestionChip> {
        val results = mutableListOf<SuggestionChip>()

        // 1. Text shortcuts (e.g. "mfg", "/datum", "/zeit", or imported shortcuts)
        val trailingToken = extractTrailingToken(textBeforeCursor)
        if (trailingToken.isNotEmpty() && dictionaryStore != null) {
            val expansion = dictionaryStore.lookupShortcut(trailingToken, locale)
            if (!expansion.isNullOrBlank()) {
                results += SuggestionChip(
                    text = expansion,
                    isShortcut = true,
                    replaceCharCount = trailingToken.length
                )
            }
        }

        // 2. Inline Math Evaluator (e.g. "24*7=" -> "168")
        if (includeMath) {
            val math = InlineMathEvaluator.evaluateTrailingExpression(textBeforeCursor, locale)
            if (math != null) {
                val chipText = if (math.appendEqualsPrefix) "=${math.formattedResult}" else math.formattedResult
                val replaceCount = if (math.appendEqualsPrefix) 0 else math.replaceCharCount
                results += SuggestionChip(
                    text = chipText,
                    isMath = true,
                    replaceCharCount = replaceCount
                )
            }
        }

        if (includeGrammarChips) {
            val grammar = detectGrammarHint(textBeforeCursor, locale)
            if (grammar != null) {
                results += SuggestionChip(
                    text = grammar.replacement,
                    isGrammarFix = true,
                    replaceCharCount = grammar.replaceCharCount
                )
            }
        }

        val currentWord = extractTrailingWord(textBeforeCursor)
        if (currentWord.isBlank()) {
            // Bigram next-word predictions from adaptive learning
            val prevWord = extractPreviousWord(textBeforeCursor)
            if (prevWord.isNotEmpty() && dictionaryStore != null) {
                dictionaryStore.predictNextWords(prevWord, limit = 3).forEach { predicted ->
                    if (results.size < 3 && results.none { it.text == predicted }) {
                        results += SuggestionChip(text = predicted, replaceCharCount = 0)
                    }
                }
            }
            val defaults = if (locale == "de") listOf("Hallo", "Danke", "Okay") else listOf("Hello", "Thanks", "Okay")
            defaults.forEach { word ->
                if (results.size < 3 && results.none { it.text == word }) {
                    results += SuggestionChip(text = word, replaceCharCount = 0)
                }
            }
            return results.take(3)
        }

        val lower = currentWord.lowercase()
        val knownByUser = dictionaryStore?.isKnownWord(currentWord) == true
        val typoMap = if (locale == "de") germanTypoMap else englishTypoMap
        val exactTypo = if (knownByUser) null else typoMap[lower]
        if (exactTypo != null && exactTypo != currentWord) {
            val cased = preserveLeadingCase(currentWord, exactTypo)
            results += SuggestionChip(
                text = cased,
                isCorrection = true,
                replaceCharCount = currentWord.length
            )
        }

        if (!knownByUser && locale == "de" && lower in germanCapitalNouns && currentWord.first().isLowerCase()) {
            val capitalized = currentWord.replaceFirstChar { it.uppercaseChar() }
            if (results.none { it.text == capitalized }) {
                results += SuggestionChip(
                    text = capitalized,
                    isCorrection = true,
                    replaceCharCount = currentWord.length
                )
            }
        }

        if (includeEmoji) {
            val emoji = emojiTriggers[lower]
            if (emoji != null && results.size < 3) {
                results += SuggestionChip(
                    text = emoji,
                    isEmoji = true,
                    replaceCharCount = 0
                )
            }
        }

        // Prioritize imported custom dictionary & learned user vocabulary before stock list
        if (dictionaryStore != null) {
            dictionaryStore.rankedPrefixCandidates(currentWord, limit = 3).forEach { match ->
                if (results.size < 3 && results.none { it.text == match }) {
                    results += SuggestionChip(
                        text = match,
                        isCorrection = false,
                        replaceCharCount = currentWord.length
                    )
                }
            }
        }

        val vocab = if (locale == "de") germanWords else englishWords
        val prefixMatches = vocab
            .asSequence()
            .filter { it.lowercase().startsWith(lower) && it != currentWord }
            .sortedBy { it.length }
            .take(4)
            .toList()

        for (match in prefixMatches) {
            if (results.size >= 3) break
            val adjusted = if (currentWord.first().isUpperCase()) {
                match.replaceFirstChar { it.uppercaseChar() }
            } else {
                match
            }
            if (results.none { it.text == adjusted }) {
                results += SuggestionChip(
                    text = adjusted,
                    isCorrection = false,
                    replaceCharCount = currentWord.length
                )
            }
        }

        if (results.size < 3 && lower.length >= 3 && !knownByUser) {
            val customFuzzyPool = dictionaryStore?.allKnownVocabularyWords().orEmpty()
            val combinedPool = (customFuzzyPool + vocab).distinct()
            val fuzzy = combinedPool
                .asSequence()
                .map { word -> word to levenshtein(lower, word.lowercase()) }
                .filter { (word, dist) -> dist in 1..2 && word != currentWord }
                .sortedBy { it.second }
                .take(3)
                .toList()
            for ((word, _) in fuzzy) {
                if (results.size >= 3) break
                val adjusted = preserveLeadingCase(currentWord, word)
                if (results.none { it.text == adjusted }) {
                    results += SuggestionChip(
                        text = adjusted,
                        isCorrection = true,
                        replaceCharCount = currentWord.length
                    )
                }
            }
        }

        return results.take(3)
    }

    fun autoCorrectionFor(
        word: String,
        locale: String,
        dictionaryStore: AdaptiveDictionaryStore? = null
    ): String? {
        if (word.length < 2) return null
        if (dictionaryStore?.isKnownWord(word) == true) return null
        val lower = word.lowercase()

        // Match exact lowercase custom/learned word casing (e.g. "tapwing" -> "Tapwing")
        val customExact = dictionaryStore?.allKnownVocabularyWords()
            ?.firstOrNull { it.equals(word, ignoreCase = true) && it != word }
        if (customExact != null) return customExact

        val typoMap = if (locale == "de") germanTypoMap else englishTypoMap
        val mapped = typoMap[lower]
        if (mapped != null) {
            return preserveLeadingCase(word, mapped)
        }
        if (locale == "de" && lower in germanCapitalNouns && word.first().isLowerCase()) {
            return word.replaceFirstChar { it.uppercaseChar() }
        }
        return null
    }

    fun detectGrammarHint(textBeforeCursor: String, locale: String): GrammarHint? {
        if (textBeforeCursor.length < 3) return null

        // Duplicate adjacent word e.g. "der der" or "the the"
        val dupRegex = Regex("""\b(\p{L}{2,})\s+\1$""", RegexOption.IGNORE_CASE)
        val dupMatch = dupRegex.find(textBeforeCursor)
        if (dupMatch != null) {
            val word = dupMatch.groupValues[1]
            return GrammarHint(
                description = if (locale == "de") "Doppeltes Wort entfernen" else "Remove duplicate word",
                replaceCharCount = dupMatch.value.length,
                replacement = word
            )
        }

        // Missing capitalization right after sentence boundary (e.g. "Hallo. wie")
        val capRegex = Regex("""([.!?]\s+)(\p{Ll}\p{L}*)$""")
        val capMatch = capRegex.find(textBeforeCursor)
        if (capMatch != null) {
            val sep = capMatch.groupValues[1]
            val word = capMatch.groupValues[2]
            if (word.length >= 2) {
                val fixed = word.replaceFirstChar { it.uppercaseChar() }
                return GrammarHint(
                    description = if (locale == "de") "Satzanfang groß" else "Capitalize sentence start",
                    replaceCharCount = capMatch.value.length,
                    replacement = "$sep$fixed"
                )
            }
        }

        // Extra space before punctuation e.g. "Hallo ,"
        val spacePunctRegex = Regex("""(\p{L}+)\s+([,.!?])$""")
        val spacePunctMatch = spacePunctRegex.find(textBeforeCursor)
        if (spacePunctMatch != null) {
            val word = spacePunctMatch.groupValues[1]
            val punct = spacePunctMatch.groupValues[2]
            return GrammarHint(
                description = if (locale == "de") "Leerzeichen vor Satzzeichen" else "Fix spacing",
                replaceCharCount = spacePunctMatch.value.length,
                replacement = "$word$punct"
            )
        }

        return null
    }

    fun extractTrailingWord(textBeforeCursor: String): String {
        if (textBeforeCursor.isEmpty()) return ""
        val idx = textBeforeCursor.indexOfLast { !it.isLetter() && it != '\'' && it != '-' }
        return if (idx == -1) textBeforeCursor else textBeforeCursor.substring(idx + 1)
    }

    fun extractTrailingToken(textBeforeCursor: String): String {
        if (textBeforeCursor.isEmpty()) return ""
        val idx = textBeforeCursor.indexOfLast { it.isWhitespace() }
        return if (idx == -1) textBeforeCursor else textBeforeCursor.substring(idx + 1)
    }

    fun extractPreviousWord(textBeforeCursor: String): String {
        val trimmed = textBeforeCursor.trimEnd()
        if (trimmed.isEmpty()) return ""
        val lastWord = extractTrailingWord(trimmed)
        return lastWord
    }

    private fun preserveLeadingCase(source: String, target: String): String {
        if (source.isEmpty() || target.isEmpty()) return target
        return if (source.first().isUpperCase()) {
            target.replaceFirstChar { it.uppercaseChar() }
        } else {
            target
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > 2) return 99
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + cost)
                prev = temp
            }
        }
        return dp[b.length]
    }
}
