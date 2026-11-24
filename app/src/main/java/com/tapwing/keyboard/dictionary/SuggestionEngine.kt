package com.tapwing.keyboard.dictionary

import com.tapwing.keyboard.data.KeyboardLocales
import kotlin.math.min

data class SuggestionChip(
    val text: String,
    val isCorrection: Boolean = false,
    val isGrammarFix: Boolean = false,
    val isEmoji: Boolean = false,
    val isShortcut: Boolean = false,
    val isMath: Boolean = false,
    val isOtp: Boolean = false,
    val isNextWord: Boolean = false,
    val replaceCharCount: Int = 0,
    val appendSpace: Boolean = true
)

data class GrammarHint(
    val description: String,
    val replaceCharCount: Int,
    val replacement: String
)

object SuggestionEngine {
    private val emailDomains = listOf("gmail.com", "outlook.com", "icloud.com", "gmx.de", "web.de", "yahoo.com", "proton.me")
    private val sentenceBoundary = Regex("""[.!?](?=\s|$)|[\r\n]""")
    private val sentenceWord = Regex("""[\p{L}][\p{L}'’-]*""")
    private val dupWordRegex = Regex("""\b(\p{L}{2,})\s+\1$""", RegexOption.IGNORE_CASE)
    private val missingSentenceCapRegex = Regex("""([.!?]\s+)(\p{Ll}\p{L}*)$""")
    private val spaceBeforePunctRegex = Regex("""(\p{L}+)\s+([,.!?])$""")

    fun suggestionsFor(
        textBeforeCursor: String,
        locale: String,
        includeEmoji: Boolean = true,
        includeGrammarChips: Boolean = true,
        includeMath: Boolean = true,
        dictionaryStore: AdaptiveDictionaryStore? = null,
        secondaryLocales: List<String> = emptyList(),
        isEmailField: Boolean = false,
        isUrlField: Boolean = false,
        offlinePack: LoadedDictionaryPack? = null,
        appPackage: String? = null,
        latestClipboardText: String = "",
        userName: String = "",
        userEmail: String = ""
    ): List<SuggestionChip> {
        val results = mutableListOf<SuggestionChip>()
        val baseLocale = KeyboardLocales.baseLang(locale)

        // 1. Text shortcuts & Multi-line Snippets with Placeholders (e.g. "mfg", "/sig", "/termin")
        val trailingToken = extractTrailingToken(textBeforeCursor)
        if (isEmailField) {
            if ('@' !in trailingToken || trailingToken.count { it == '@' } != 1) return emptyList()
            val domain = trailingToken.substringAfter('@')
            return emailDomains.filter { it.startsWith(domain, ignoreCase = true) && !it.equals(domain, ignoreCase = true) }
                .take(3)
                .map { SuggestionChip(it, replaceCharCount = domain.length, appendSpace = false) }
        }
        if (isUrlField) {
            if (textBeforeCursor.isBlank()) return listOf("https://", "www.").map { SuggestionChip(it, appendSpace = false) }
            return emptyList()
        }
        if (trailingToken.isNotEmpty() && dictionaryStore != null) {
            val snippet = dictionaryStore.lookupSnippet(trailingToken)
            if (snippet != null) {
                val resolved = SnippetPlaceholderResolver.resolve(
                    template = snippet.body,
                    locale = locale,
                    latestClipboardText = latestClipboardText,
                    userName = userName,
                    userEmail = userEmail
                )
                results += SuggestionChip(
                    text = resolved,
                    isShortcut = true,
                    replaceCharCount = trailingToken.length
                )
            } else {
                val expansion = dictionaryStore.lookupShortcut(trailingToken, locale)
                if (!expansion.isNullOrBlank()) {
                    results += SuggestionChip(
                        text = expansion,
                        isShortcut = true,
                        replaceCharCount = trailingToken.length
                    )
                }
            }
        }

        // 2. Inline Math Evaluator (e.g. "24*7=" -> "168", "150+19%=" -> "178,5")
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
        val atSentenceStart = isAtSentenceStart(textBeforeCursor, currentWord)
        val (prevWord, prevTwoWord) = extractPreviousTwoWords(
            if (currentWord.isEmpty()) textBeforeCursor else textBeforeCursor.dropLast(currentWord.length)
        )

        if (currentWord.isBlank()) {
            val nextWord = textBeforeCursor.lastOrNull()?.isWhitespace() == true && prevWord.isNotEmpty() && !atSentenceStart
            val candidates = when {
                nextWord -> {
                    val learned = dictionaryStore?.predictNextWords(
                        previousWord = prevWord,
                        limit = 6,
                        previousTwoWord = prevTwoWord,
                        appPackage = appPackage
                    ).orEmpty()
                    val contextual = (listOf(locale) + secondaryLocales).firstNotNullOfOrNull { language ->
                        LanguageVocabularies.commonNextWords(language, prevWord, prevTwoWord).takeIf { it.isNotEmpty() }
                    }.orEmpty()
                    learned + contextual
                }
                atSentenceStart -> LanguageVocabularies.defaultEmptyChipsFor(locale)
                else -> emptyList()
            }
            for (word in candidates) {
                if (results.size >= 3) break
                val cased = enforceMidSentenceCasingRule(word, locale, atSentenceStart, dictionaryStore)
                if (results.none { it.text.equals(cased, ignoreCase = true) }) {
                    results += SuggestionChip(text = cased, isNextWord = nextWord)
                }
            }
            return results
        }

        val lower = currentWord.lowercase()
        val knownByUser = dictionaryStore?.isKnownWord(currentWord) == true
        val personalFix = dictionaryStore?.preferredCorrectionFor(currentWord)
        if (personalFix != null && personalFix != currentWord) {
            results += SuggestionChip(
                text = preserveLeadingCase(currentWord, personalFix),
                isCorrection = true,
                replaceCharCount = currentWord.length
            )
        }
        val typoMap = LanguageVocabularies.typoMapFor(locale)
        val exactTypo = if (knownByUser) null else typoMap[lower]
        if (exactTypo != null && exactTypo != currentWord && results.none { it.text.equals(exactTypo, ignoreCase = false) }) {
            val cased = preserveLeadingCase(currentWord, exactTypo)
            results += SuggestionChip(
                text = cased,
                isCorrection = true,
                replaceCharCount = currentWord.length
            )
        }

        if (!knownByUser && baseLocale == "de" &&
            lower in LanguageVocabularies.germanCapitalNouns &&
            currentWord.first().isLowerCase() &&
            !LanguageVocabularies.isNeverAutoCapitalizeMidSentence(lower, locale)
        ) {
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
            val learnedEmoji = dictionaryStore?.preferredEmojiFor(lower)
            val emoji = learnedEmoji ?: LanguageVocabularies.emojiTriggers[lower]
            if (emoji != null && results.size < 3) {
                results += SuggestionChip(
                    text = emoji,
                    isEmoji = true,
                    replaceCharCount = 0
                )
            }
        }

        // Prioritize context-aware learned vocabulary, per-app words & custom dictionary
        if (dictionaryStore != null) {
            dictionaryStore.rankedPrefixCandidates(
                prefix = currentWord,
                limit = 4,
                previousWord = prevWord.takeIf { it.isNotEmpty() },
                previousTwoWord = prevTwoWord.takeIf { it.isNotEmpty() },
                atSentenceStart = atSentenceStart,
                appPackage = appPackage
            ).forEach { match ->
                val adjusted = adjustCandidateCase(currentWord, match, locale, atSentenceStart, dictionaryStore)
                if (results.size < 3 && adjusted != currentWord && results.none { it.text == adjusted }) {
                    results += SuggestionChip(
                        text = adjusted,
                        isCorrection = false,
                        replaceCharCount = currentWord.length
                    )
                }
            }
        }

        // Active language stock vocabulary + 50k offline pack prefix completions + umlaut-insensitive matching
        val vocab = LanguageVocabularies.vocabularyFor(locale)
        val packPrefixes = offlinePack?.prefixMatches(lower, limit = 6).orEmpty()
        val normLower = if (baseLocale == "de" && lower.length >= 3) normalizeForUmlautMatch(lower, "de") else ""
        val umlautMatches = if (normLower.isNotEmpty()) {
            val found = ArrayList<String>(4)
            fun scanForUmlauts(source: List<String>, maxScan: Int) {
                var scanned = 0
                for (cand in source) {
                    if (found.size >= 4 || scanned >= maxScan) break
                    scanned++
                    val cl = cand.lowercase()
                    if (cl != lower && hasGermanSpecialChar(cl) && normalizeForUmlautMatch(cl, "de").startsWith(normLower)) {
                        if (cand !in found) found += cand
                    }
                }
            }
            scanForUmlauts(vocab, vocab.size)
            if (found.size < 4 && offlinePack != null) {
                scanForUmlauts(offlinePack.topSwipePool, 2_500)
            }
            found
        } else emptyList()

        val contextualBoostWords = if (prevWord.isNotEmpty()) {
            LanguageVocabularies.commonNextWords(locale, prevWord, prevTwoWord).map { it.lowercase() }.toSet()
        } else emptySet()

        val vocabPrefixMatches = ArrayList<String>(5)
        for (word in vocab) {
            if (vocabPrefixMatches.size >= 5) break
            if (word.length > lower.length && word.startsWith(lower, ignoreCase = true) && word != currentWord) {
                vocabPrefixMatches += word
            }
        }
        val prefixMatches = (vocabPrefixMatches + packPrefixes + umlautMatches)
            .distinct()
            .let { list ->
                if (contextualBoostWords.isEmpty()) list
                else list.sortedByDescending { if (it.lowercase() in contextualBoostWords) 1 else 0 }
            }

        for (match in prefixMatches) {
            if (results.size >= 3) break
            val adjusted = adjustCandidateCase(currentWord, match, locale, atSentenceStart, dictionaryStore)
            if (adjusted != currentWord && results.none { it.text == adjusted }) {
                results += SuggestionChip(
                    text = adjusted,
                    isCorrection = false,
                    replaceCharCount = currentWord.length
                )
            }
        }

        // Secondary enabled languages for bilingual typing without switching
        if (results.size < 3 && secondaryLocales.isNotEmpty()) {
            for (sec in secondaryLocales) {
                if (KeyboardLocales.baseLang(sec) == baseLocale) continue
                val secTypo = if (knownByUser) null else LanguageVocabularies.typoMapFor(sec)[lower]
                if (secTypo != null && secTypo != currentWord && results.size < 3) {
                    val adjusted = preserveLeadingCase(currentWord, secTypo)
                    if (results.none { it.text == adjusted }) {
                        results += SuggestionChip(
                            text = adjusted,
                            isCorrection = true,
                            replaceCharCount = currentWord.length
                        )
                    }
                }
                val secVocab = LanguageVocabularies.vocabularyFor(sec)
                for (match in secVocab) {
                    if (results.size >= 3) break
                    if (match.length > lower.length && match.startsWith(lower, ignoreCase = true) && match != currentWord) {
                        val adjusted = adjustCandidateCase(currentWord, match, sec, atSentenceStart, dictionaryStore)
                        if (results.none { it.text == adjusted }) {
                            results += SuggestionChip(
                                text = adjusted,
                                isCorrection = false,
                                replaceCharCount = currentWord.length
                            )
                        }
                    }
                }
            }
        }

        // Keyboard-proximity weighted Damerau-Levenshtein matching with early-abort DP
        if (results.size < 3 && lower.length >= 3 && !knownByUser && offlinePack?.contains(lower) != true) {
            val seen = HashSet<String>(512)
            val fuzzy = ArrayList<Triple<String, Double, Int>>(16)
            val maxDist = 2.15

            fun considerPool(pool: Iterable<String>, maxItems: Int) {
                var count = 0
                for (word in pool) {
                    if (count >= maxItems) break
                    count++
                    if (kotlin.math.abs(word.length - lower.length) > 2) continue
                    if (word == currentWord) continue
                    val wl = word.lowercase()
                    if (!seen.add(wl)) continue
                    val dist = weightedKeyboardDistance(lower, wl, locale, maxDistance = maxDist)
                    if (dist > 0.0 && dist <= maxDist) {
                        val contextBonus = if (wl in contextualBoostWords) 80 else 0
                        val personalBoost = (dictionaryStore?.wordScore(word) ?: 0) + contextBonus
                        fuzzy += Triple(word, dist, personalBoost)
                    }
                }
            }

            dictionaryStore?.allKnownVocabularyWords()?.let { considerPool(it, 2_000) }
            considerPool(vocab, vocab.size)
            offlinePack?.topSwipePool?.let { considerPool(it, 2_500) }
            for (sec in secondaryLocales) {
                if (KeyboardLocales.baseLang(sec) != baseLocale) {
                    considerPool(LanguageVocabularies.vocabularyFor(sec), 800)
                }
            }

            fuzzy.sortWith(compareBy<Triple<String, Double, Int>> { it.second }.thenByDescending { it.third })

            for ((word, _, _) in fuzzy) {
                if (results.size >= 3) break
                val adjusted = adjustCandidateCase(currentWord, word, locale, atSentenceStart, dictionaryStore)
                if (adjusted != currentWord && results.none { it.text == adjusted }) {
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
        dictionaryStore: AdaptiveDictionaryStore? = null,
        atSentenceStart: Boolean = false,
        secondaryLocales: List<String> = emptyList(),
        offlinePack: LoadedDictionaryPack? = null
    ): String? {
        val baseLocale = KeyboardLocales.baseLang(locale)
        // Standalone English pronoun "i" -> "I"
        if (word == "i" && baseLocale == "en") return "I"
        if (word.length < 2) return null
        if (dictionaryStore?.isKnownWord(word) == true) {
            // Even if known, NEVER auto-capitalize mid-sentence function words
            return null
        }
        val lower = word.lowercase()

        // Respect explicit custom words or consistently mid-sentence capitalized terms,
        // while NEVER letting sentence-start capitalization ("Und") leak into mid-sentence ("und").
        val canonical = dictionaryStore?.canonicalMidSentenceCasingFor(word, locale)
        if (canonical != null && canonical != word) {
            return canonical
        }

        val personalCorrection = dictionaryStore?.preferredCorrectionFor(word)
        if (personalCorrection != null && personalCorrection != word) {
            return preserveLeadingCase(word, personalCorrection)
        }

        val typoMap = LanguageVocabularies.typoMapFor(locale)
        val mapped = typoMap[lower]
        if (mapped != null) {
            return preserveLeadingCase(word, mapped)
        }

        if (baseLocale == "de" &&
            lower in LanguageVocabularies.germanCapitalNouns &&
            !LanguageVocabularies.isNeverAutoCapitalizeMidSentence(lower, locale) &&
            word.first().isLowerCase()
        ) {
            return word.replaceFirstChar { it.uppercaseChar() }
        }

        // Check secondary languages for clear typos (e.g., typing English "teh" while on DE keyboard)
        for (sec in secondaryLocales) {
            if (KeyboardLocales.baseLang(sec) == baseLocale) continue
            val secExact = LanguageVocabularies.vocabularyFor(sec).any { it.equals(word, ignoreCase = true) }
            if (secExact) return null // Valid word in secondary language -> don't break it!
            val secMapped = LanguageVocabularies.typoMapFor(sec)[lower]
            if (secMapped != null) return preserveLeadingCase(word, secMapped)
        }

        // If the word is already valid in stock vocab or downloaded 50k pack, don't force fuzzy change
        val primaryVocab = LanguageVocabularies.vocabularyFor(locale)
        if (primaryVocab.any { it.equals(word, ignoreCase = true) } ||
            offlinePack?.contains(word) == true
        ) {
            return null
        }

        // Check high-confidence keyboard-proximity / transposition typo in user's learned words (>= 3 uses)
        if (word.length >= 4 && dictionaryStore != null && !atSentenceStart) {
            val learnedMatch = dictionaryStore.allKnownVocabularyWords()
                .asSequence()
                .filter { kotlin.math.abs(it.length - word.length) <= 1 && dictionaryStore.wordScore(it) >= 15 }
                .map { it to weightedKeyboardDistance(lower, it.lowercase(), locale, maxDistance = 0.85) }
                .filter { (_, dist) -> dist > 0.0 && dist <= 0.85 }
                .minWithOrNull(compareBy<Pair<String, Double>> { it.second }.thenByDescending { dictionaryStore.wordScore(it.first) })
                ?.first
            if (learnedMatch != null && !learnedMatch.equals(word, ignoreCase = true)) {
                return adjustCandidateCase(word, learnedMatch, locale, atSentenceStart, dictionaryStore)
            }
        }

        // Safe high-confidence proximity/transposition autocorrect against primary vocabulary
        // (e.g. "huete" -> "heute", "hsllo" -> "hallo", "raelly" -> "really").
        // Requires a single unambiguous winner (distance <= 0.68 and clear margin over runner-up).
        if (word.length >= 4) {
            var bestWord: String? = null
            var bestDist = 99.0
            var secondDist = 99.0
            for (cand in primaryVocab) {
                if (kotlin.math.abs(cand.length - word.length) > 1) continue
                val dist = weightedKeyboardDistance(lower, cand.lowercase(), locale, maxDistance = 0.95)
                if (dist <= 0.0 || dist >= 0.95) continue
                if (dist < bestDist) {
                    secondDist = bestDist
                    bestDist = dist
                    bestWord = cand
                } else if (dist < secondDist) {
                    secondDist = dist
                }
            }
            if (bestWord != null && bestDist <= 0.68 && (secondDist - bestDist) >= 0.20) {
                return adjustCandidateCase(word, bestWord, locale, atSentenceStart, dictionaryStore)
            }
        }

        return null
    }

    private fun hasGermanSpecialChar(s: String): Boolean {
        for (i in s.indices) {
            val ch = s[i]
            if (ch == 'ä' || ch == 'ö' || ch == 'ü' || ch == 'ß') return true
        }
        return false
    }

    fun detectGrammarHint(textBeforeCursor: String, locale: String): GrammarHint? {
        if (textBeforeCursor.length < 3) return null
        val base = KeyboardLocales.baseLang(locale)

        // Duplicate adjacent word e.g. "der der" or "the the"
        val dupMatch = dupWordRegex.find(textBeforeCursor)
        if (dupMatch != null) {
            val word = dupMatch.groupValues[1]
            return GrammarHint(
                description = if (base == "de") "Doppeltes Wort entfernen" else "Remove duplicate word",
                replaceCharCount = dupMatch.value.length,
                replacement = word
            )
        }

        // Missing capitalization right after sentence boundary (e.g. "Hallo. wie")
        val capMatch = missingSentenceCapRegex.find(textBeforeCursor)
        if (capMatch != null) {
            val sep = capMatch.groupValues[1]
            val word = capMatch.groupValues[2]
            if (word.length >= 2) {
                val fixed = word.replaceFirstChar { it.uppercaseChar() }
                return GrammarHint(
                    description = if (base == "de") "Satzanfang groß" else "Capitalize sentence start",
                    replaceCharCount = capMatch.value.length,
                    replacement = "$sep$fixed"
                )
            }
        }

        // Extra space before punctuation e.g. "Hallo ,"
        val spacePunctMatch = spaceBeforePunctRegex.find(textBeforeCursor)
        if (spacePunctMatch != null) {
            val word = spacePunctMatch.groupValues[1]
            val punct = spacePunctMatch.groupValues[2]
            return GrammarHint(
                description = if (base == "de") "Leerzeichen vor Satzzeichen" else "Fix spacing",
                replaceCharCount = spacePunctMatch.value.length,
                replacement = "$word$punct"
            )
        }

        return null
    }

    /** Extracts a 4–8 digit 2FA / OTP verification code from a recent clipboard message. */
    private val verificationLabel = Regex(
        """\b(?:bestätigungs-?code|verifizierungs-?code|sicherheits-?code|einmal-?(?:code|passwort)|anmelde-?code|prüf-?code|tan|otp|2fa|(?:verification|security|authentication|sign[- ]in|login|one[- ]time)\s+(?:code|password|pin)|passcode|2fa code)\b""",
        RegexOption.IGNORE_CASE
    )
    private val verificationDigits = Regex("""(?<![\p{L}\d/@])(?:[0-9]{3}[- ][0-9]{3}|[0-9]{4,8})(?![\p{L}\d/@])""")

    fun extractOtpFromClipboard(clipText: String): String? {
        val clean = clipText.trim()
        if (clean.length !in 4..500) return null
        if (verificationDigits.matches(clean)) return clean.filter(Char::isDigit)
        val labels = verificationLabel.findAll(clean).toList()
        if (labels.isEmpty()) return null
        val candidates = verificationDigits.findAll(clean).mapNotNull { digits ->
            val distance = labels.minOf { label ->
                when {
                    digits.range.first > label.range.last -> digits.range.first - label.range.last
                    digits.range.last < label.range.first -> label.range.first - digits.range.last
                    else -> Int.MAX_VALUE
                }
            }
            if (distance <= 32) digits.value.filter(Char::isDigit) to distance else null
        }.sortedBy { it.second }.toList()
        val first = candidates.firstOrNull() ?: return null
        return first.first.takeUnless {
            candidates.getOrNull(1)?.let { other -> other.second == first.second && other.first != first.first } == true
        }
    }

    fun isAtSentenceStart(textBeforeCursor: String, currentWord: String = extractTrailingWord(textBeforeCursor)): Boolean {
        val prefix = if (currentWord.isEmpty()) textBeforeCursor else textBeforeCursor.dropLast(currentWord.length)
        if (prefix.isBlank()) return true
        val boundary = sentenceBoundary.findAll(prefix).lastOrNull() ?: return false
        return prefix.substring(boundary.range.last + 1).isBlank()
    }

    fun extractTrailingWord(textBeforeCursor: String): String {
        if (textBeforeCursor.isEmpty()) return ""
        val idx = textBeforeCursor.indexOfLast { !it.isLetter() && it != '\'' && it != '’' && it != '-' }
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
        return extractTrailingWord(trimmed)
    }

    fun extractPreviousTwoWords(textBeforeCurrentWord: String): Pair<String, String> {
        val sentenceStart = sentenceBoundary.findAll(textBeforeCurrentWord).lastOrNull()?.range?.last?.plus(1) ?: 0
        val words = sentenceWord.findAll(textBeforeCurrentWord, sentenceStart).map { it.value }.toList()
        return words.lastOrNull().orEmpty() to words.getOrNull(words.lastIndex - 1).orEmpty()
    }

    private fun enforceMidSentenceCasingRule(
        candidate: String,
        locale: String,
        atSentenceStart: Boolean,
        dictionaryStore: AdaptiveDictionaryStore?
    ): String {
        if (atSentenceStart) {
            return candidate.replaceFirstChar { it.uppercaseChar() }
        }
        if (LanguageVocabularies.isNeverAutoCapitalizeMidSentence(candidate, locale) &&
            dictionaryStore?.canonicalMidSentenceCasingFor(candidate, locale) == null
        ) {
            return candidate.lowercase()
        }
        return candidate
    }

    private fun adjustCandidateCase(
        typedWord: String,
        candidate: String,
        locale: String,
        atSentenceStart: Boolean,
        dictionaryStore: AdaptiveDictionaryStore?
    ): String {
        if (typedWord.isEmpty() || candidate.isEmpty()) return candidate
        if (typedWord.first().isUpperCase()) {
            return candidate.replaceFirstChar { it.uppercaseChar() }
        }
        if (!atSentenceStart &&
            LanguageVocabularies.isNeverAutoCapitalizeMidSentence(candidate, locale) &&
            dictionaryStore?.canonicalMidSentenceCasingFor(candidate, locale) == null
        ) {
            return candidate.lowercase()
        }
        return candidate
    }

    private fun preserveLeadingCase(source: String, target: String): String {
        if (source.isEmpty() || target.isEmpty()) return target
        return if (source.first().isUpperCase()) {
            target.replaceFirstChar { it.uppercaseChar() }
        } else {
            target
        }
    }

    fun normalizeForUmlautMatch(raw: String, baseLocale: String): String {
        val folded = raw.lowercase()
            .replace("ä", "a")
            .replace("ö", "o")
            .replace("ü", "u")
            .replace("ß", "ss")
        return if (baseLocale == "de") {
            folded
                .replace("ae", "a")
                .replace("oe", "o")
                .replace("ue", "u")
        } else {
            folded
        }
    }

    private val coordsByLanguage = java.util.concurrent.ConcurrentHashMap<String, Map<Char, Pair<Float, Float>>>()

    private fun keyGridCoords(locale: String): Map<Char, Pair<Float, Float>> =
        coordsByLanguage.getOrPut(KeyboardLocales.baseLang(locale)) { buildKeyGrid(KeyboardLocales.baseLang(locale)) }

    private fun buildKeyGrid(baseLang: String): Map<Char, Pair<Float, Float>> {
        val top = when (baseLang) {
            "de" -> "qwertzuiop"
            "fr" -> "azertyuiop"
            "tr" -> "qwertyuıopğü"
            else -> "qwertyuiop"
        }
        val mid = when (baseLang) {
            "fr" -> "qsdfghjklm"
            "es" -> "asdfghjklñ"
            "tr" -> "asdfghjklşi"
            else -> "asdfghjkl"
        }
        val bot = when (baseLang) {
            "de" -> "yxcvbnm"
            "fr" -> "wxcvbn"
            "tr" -> "zxcvbnmöç"
            else -> "zxcvbnm"
        }
        val coords = HashMap<Char, Pair<Float, Float>>(32)
        top.forEachIndexed { idx, ch -> coords[ch] = idx.toFloat() to 0f }
        mid.forEachIndexed { idx, ch -> coords[ch] = (idx + 0.5f) to 1f }
        bot.forEachIndexed { idx, ch -> coords[ch] = (idx + 1.5f) to 2f }
        coords['ä'] = coords['a'] ?: (0.5f to 1f)
        coords['ö'] = coords['o'] ?: (8f to 0f)
        coords['ü'] = coords['u'] ?: (6f to 0f)
        coords['ß'] = coords['s'] ?: (1.5f to 1f)
        return coords
    }

    private fun isUmlautPair(ca: Char, cb: Char): Boolean =
        (ca == 'a' && cb == 'ä') || (ca == 'ä' && cb == 'a') ||
            (ca == 'o' && cb == 'ö') || (ca == 'ö' && cb == 'o') ||
            (ca == 'u' && cb == 'ü') || (ca == 'ü' && cb == 'u') ||
            (ca == 's' && cb == 'ß') || (ca == 'ß' && cb == 's') ||
            (ca == 'c' && cb == 'ç') || (ca == 'ç' && cb == 'c') ||
            (ca == 'n' && cb == 'ñ') || (ca == 'ñ' && cb == 'n')

    fun weightedKeyboardDistance(
        a: String,
        b: String,
        locale: String = "de",
        maxDistance: Double = Double.MAX_VALUE
    ): Double {
        if (a == b) return 0.0
        val n = a.length
        val m = b.length
        if (kotlin.math.abs(n - m) > 2) return 99.0
        val coords = keyGridCoords(locale)
        var prev2 = DoubleArray(m + 1)
        var prev1 = DoubleArray(m + 1) { it.toDouble() }
        var curr = DoubleArray(m + 1)
        for (i in 1..n) {
            curr[0] = i.toDouble()
            var rowMin = curr[0]
            val ca = a[i - 1]
            val pa = coords[ca]
            for (j in 1..m) {
                val cb = b[j - 1]
                val subCost = when {
                    ca == cb -> 0.0
                    isUmlautPair(ca, cb) -> 0.25
                    else -> {
                        val pb = coords[cb]
                        if (pa != null && pb != null) {
                            val dx = pa.first - pb.first
                            val dy = pa.second - pb.second
                            val distSq = dx * dx + dy * dy
                            when {
                                distSq <= 1.5625f -> 0.45
                                distSq <= 3.4225f -> 0.55
                                else -> 1.1
                            }
                        } else {
                            1.0
                        }
                    }
                }
                var best = minOf(
                    prev1[j] + 1.0,
                    curr[j - 1] + 1.0,
                    prev1[j - 1] + subCost
                )
                if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) {
                    val trans = prev2[j - 2] + 0.65
                    if (trans < best) best = trans
                }
                curr[j] = best
                if (best < rowMin) rowMin = best
            }
            // Early-abort if even the best cell in this row exceeds maxDistance (accounting for transposition slack)
            if (rowMin > maxDistance + 0.65) return 99.0
            val tmp = prev2
            prev2 = prev1
            prev1 = curr
            curr = tmp
        }
        return prev1[m]
    }

    /**
     * Optimal String Alignment / Damerau-Levenshtein distance so adjacent character
     * transpositions (e.g. "udn" -> "und", "nciht" -> "nicht", "teh" -> "the") count as distance 1.
     */
    fun damerauLevenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val n = a.length
        val m = b.length
        if (kotlin.math.abs(n - m) > 2) return 99
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = min(
                    min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                    d[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = min(best, d[i - 2][j - 2] + cost)
                }
                d[i][j] = best
            }
        }
        return d[n][m]
    }
}
