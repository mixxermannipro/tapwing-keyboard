package com.tapwing.keyboard.dictionary

import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.PersonalProfile
import com.tapwing.keyboard.data.SmartFieldType
import com.tapwing.keyboard.data.SmartFillKind
import com.tapwing.keyboard.ime.SmartFieldDetector
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
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
    val appendSpace: Boolean = true,
    val isSmartFill: Boolean = false,
    val smartFillKind: SmartFillKind? = null,
    val smartFieldType: SmartFieldType? = null
)

data class GrammarHint(
    val description: String,
    val replaceCharCount: Int,
    val replacement: String
)

object SuggestionEngine {
    private val emailDomainsDe = listOf("gmail.com", "gmx.de", "web.de", "icloud.com", "outlook.com", "t-online.de", "proton.me", "yahoo.com")
    private val emailDomainsDefault = listOf("gmail.com", "outlook.com", "icloud.com", "yahoo.com", "proton.me", "gmx.de", "web.de")

    private val dupWordRegex = Regex("""\b(\p{L}{2,})\s+\1$""", RegexOption.IGNORE_CASE)
    private val missingSentenceCapRegex = Regex("""([.!?]\s+)(\p{Ll}\p{L}*)$""")
    private val spaceBeforePunctRegex = Regex("""(\p{L}+)\s+([,.!?])$""")
    private val missingSpaceAfterCommaRegex = Regex("""(\p{L}{2,}),(\p{L}{2,})$""")
    private val germanDassPronounRegex = Regex("""(,\s+)das(\s+(?:ich|du|er|sie|es|wir|ihr|man|nicht|alles))\s*$""", RegexOption.IGNORE_CASE)
    private val germanVerbDasRegex = Regex("""\b(glaube|hoffe|denke|weiß|weiss|finde|schade|gut|toll|super)\s+das\s*$""", RegexOption.IGNORE_CASE)
    private val englishArticleRegex = Regex("""\b(a|an)\s+(\p{L}{2,})\s*$""", RegexOption.IGNORE_CASE)

    private val ENGLISH_AN_EXCEPTIONS = setOf("hour", "hours", "honest", "honor", "honour", "heir", "herb")
    private val ENGLISH_A_EXCEPTIONS = setOf(
        "university", "user", "users", "unique", "unit", "units", "united", "uniform",
        "universal", "use", "useful", "useless", "usual", "euro", "europe", "european", "one", "once"
    )

    private val englishSentenceCues = setOf(
        "and", "but", "because", "when", "that", "think", "thought", "hope", "know",
        "said", "told", "asked", "wish", "believe", "mean", "guess", "yes", "well",
        "actually", "really", "sure", "maybe", "today", "tomorrow", "yesterday", "what", "why", "how",
        "the", "you", "your", "are", "have", "can", "would", "could", "should", "let", "thank", "thanks", "please", "see", "looking"
    )

    /**
     * Returns true ONLY when the user is explicitly typing a domain/URL (e.g. "https://...",
     * "www.foo", "tapwing.de", "tapwing.") rather than a normal search query in a browser omnibox.
     */
    internal fun isExplicitUrlInput(textBeforeCursor: String, trailingToken: String): Boolean {
        if (trailingToken.isEmpty()) return false
        // Any whitespace means the user is typing a multi-word search query, never a URL!
        for (i in textBeforeCursor.indices) {
            if (textBeforeCursor[i].isWhitespace()) return false
        }
        return trailingToken.contains("://") ||
            trailingToken.startsWith("www.", ignoreCase = true) ||
            trailingToken.contains('.')
    }

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
        userEmail: String = "",
        smartFieldType: SmartFieldType = SmartFieldType.NONE,
        personalProfile: PersonalProfile? = null,
        smartFillEnabled: Boolean = true
    ): List<SuggestionChip> {
        val results = ArrayList<SuggestionChip>(4)
        val baseLocale = KeyboardLocales.baseLang(locale)
        val trailingToken = extractTrailingToken(textBeforeCursor)
        val effectiveField = when {
            smartFieldType != SmartFieldType.NONE -> smartFieldType
            isEmailField -> SmartFieldType.EMAIL
            else -> SmartFieldType.NONE
        }

        // 0. Dedicated Email Field: personal email addresses before '@', domain completions after '@'
        if (isEmailField || effectiveField == SmartFieldType.EMAIL) {
            if ('@' in trailingToken) {
                if (trailingToken.count { it == '@' } != 1) return emptyList()
                val domain = trailingToken.substringAfter('@')
                val domains = if (baseLocale == "de") emailDomainsDe else emailDomainsDefault
                return domains
                    .filter { it.startsWith(domain, ignoreCase = true) && !it.equals(domain, ignoreCase = true) }
                    .take(3)
                    .map { SuggestionChip(it, replaceCharCount = domain.length, appendSpace = false) }
            }
            if (smartFillEnabled) {
                val emailMatches = resolveSmartProfileSuggestions(
                    fieldType = SmartFieldType.EMAIL,
                    profile = personalProfile,
                    dictionaryStore = dictionaryStore,
                    locale = locale,
                    prefix = trailingToken,
                    appPackage = appPackage,
                    limit = 3
                )
                if (emailMatches.isNotEmpty()) {
                    return emailMatches.map { email ->
                        SuggestionChip(
                            text = email,
                            replaceCharCount = trailingToken.length,
                            appendSpace = false,
                            isSmartFill = true,
                            smartFillKind = SmartFillKind.EMAIL,
                            smartFieldType = SmartFieldType.EMAIL
                        )
                    }
                }
            }
            return emptyList()
        }

        // 0b. Browser Omnibox / URL field: ONLY show URL TLD/slash chips when user actually types a URL!
        // Normal searches ("wetter heute", "tapwing tastatur", "rezept") fall through to full Gboard-level intelligence.
        if (isUrlField && isExplicitUrlInput(textBeforeCursor, trailingToken)) {
            val tlds = if (baseLocale == "de") listOf(".de", ".com", ".org") else listOf(".com", ".org", ".net")
            val hostPart = trailingToken.substringAfter("://").substringBefore('/')
            val hasSlashAfterHost = trailingToken.substringAfter("://").contains('/')
            return when {
                hasSlashAfterHost -> emptyList()
                hostPart.endsWith(".") -> tlds.map { tld ->
                    SuggestionChip(tld.removePrefix("."), replaceCharCount = 0, appendSpace = false)
                }
                '.' !in hostPart && hostPart.length >= 2 -> tlds.map { tld ->
                    SuggestionChip(tld, replaceCharCount = 0, appendSpace = false)
                }
                else -> {
                    val afterDot = "." + hostPart.substringAfterLast('.')
                    val matchingTlds = tlds.filter { it.startsWith(afterDot, ignoreCase = true) && !it.equals(afterDot, ignoreCase = true) }
                    if (matchingTlds.isNotEmpty()) {
                        matchingTlds.map { tld ->
                            SuggestionChip(tld, replaceCharCount = afterDot.length, appendSpace = false)
                        }
                    } else {
                        listOf(SuggestionChip("/", replaceCharCount = 0, appendSpace = false))
                    }
                }
            }
        }

        // 0c. Dedicated Form Fields (Vorname, Nachname, Name, Telefon, Straße, PLZ, Ort, Geburtsdatum, Alter, Firma)
        if (smartFillEnabled && effectiveField != SmartFieldType.NONE) {
            val fieldPrefix = textBeforeCursor.trimStart()
            val matches = resolveSmartProfileSuggestions(
                fieldType = effectiveField,
                profile = personalProfile,
                dictionaryStore = dictionaryStore,
                locale = locale,
                prefix = fieldPrefix,
                appPackage = appPackage,
                limit = 3
            )
            if (matches.isNotEmpty()) {
                val fillKind = effectiveField.fillKind()
                for (value in matches) {
                    if (results.size >= 3) break
                    results += SuggestionChip(
                        text = value,
                        replaceCharCount = fieldPrefix.length,
                        appendSpace = false,
                        isSmartFill = true,
                        smartFillKind = fillKind,
                        smartFieldType = effectiveField
                    )
                }
                // For structured-only fields (phone, postal code, birthdate, age), return exclusively smart fill chips
                if (effectiveField in setOf(
                        SmartFieldType.PHONE,
                        SmartFieldType.POSTAL_CODE,
                        SmartFieldType.BIRTHDATE,
                        SmartFieldType.AGE
                    ) || fieldPrefix.isEmpty()
                ) {
                    return results
                }
            } else if (effectiveField in setOf(SmartFieldType.PHONE, SmartFieldType.POSTAL_CODE, SmartFieldType.BIRTHDATE, SmartFieldType.AGE)) {
                return emptyList()
            }
        }

        // 0d. Conversational Smart-Fill Intent Detection in Free Text (WhatsApp, Email, SMS, Notes)
        if (smartFillEnabled && effectiveField == SmartFieldType.NONE) {
            val intent = SmartFieldDetector.detectConversationalIntent(textBeforeCursor)
            if (intent != null) {
                val matches = resolveSmartProfileSuggestions(
                    fieldType = intent.fieldType,
                    profile = personalProfile,
                    dictionaryStore = dictionaryStore,
                    locale = locale,
                    prefix = intent.partialTyped,
                    appPackage = appPackage,
                    limit = 3
                )
                if (matches.isNotEmpty()) {
                    val fillKind = intent.fieldType.fillKind()
                    val appendSpaceAfter = intent.fieldType !in setOf(SmartFieldType.EMAIL, SmartFieldType.PHONE, SmartFieldType.WEBSITE)
                    for (value in matches) {
                        if (results.size >= 3) break
                        results += SuggestionChip(
                            text = value,
                            replaceCharCount = intent.partialTyped.length,
                            appendSpace = appendSpaceAfter,
                            isSmartFill = true,
                            smartFillKind = fillKind,
                            smartFieldType = intent.fieldType
                        )
                    }
                    if (intent.partialTyped.isEmpty() || results.size >= 2) {
                        return results.take(3)
                    }
                }
            }
        }

        // 1. Text shortcuts & Multi-line Snippets with Placeholders (e.g. "mfg", "/sig", "/termin")
        if (trailingToken.isNotEmpty() && dictionaryStore != null) {
            val snippet = dictionaryStore.lookupSnippet(trailingToken)
            if (snippet != null) {
                val resolved = SnippetPlaceholderResolver.resolve(
                    template = snippet.body,
                    locale = locale,
                    latestClipboardText = latestClipboardText,
                    userName = userName,
                    userEmail = userEmail,
                    profile = personalProfile
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

        if (includeGrammarChips && results.size < 3) {
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
        val prefixEndIdx = textBeforeCursor.length - currentWord.length
        val (prevWord, prevTwoWord) = extractPreviousTwoWordsRange(textBeforeCursor, prefixEndIdx)

        // Dynamic bilingual language detection for the current sentence
        val activeLangOrder = resolveActiveLocaleOrder(locale, secondaryLocales, prevWord, prevTwoWord)
        val primaryLang = activeLangOrder.first()
        val primaryBase = KeyboardLocales.baseLang(primaryLang)
        val primaryIndex = LanguageVocabularies.indexFor(primaryLang)

        if (currentWord.isBlank()) {
            // In URL/browser search bar when completely empty, keep toolbar uncluttered (no forced "Hallo/Danke/Okay")
            if (isUrlField && textBeforeCursor.isBlank()) return results
            val nextWord = textBeforeCursor.lastOrNull()?.isWhitespace() == true && prevWord.isNotEmpty() && !atSentenceStart
            val candidates = when {
                nextWord -> {
                    val learned = dictionaryStore?.predictNextWords(
                        previousWord = prevWord,
                        limit = 6,
                        previousTwoWord = prevTwoWord,
                        appPackage = appPackage
                    ).orEmpty()
                    val contextual = activeLangOrder.firstNotNullOfOrNull { language ->
                        LanguageVocabularies.commonNextWords(language, prevWord, prevTwoWord).takeIf { it.isNotEmpty() }
                    }.orEmpty()
                    learned + contextual
                }
                atSentenceStart -> LanguageVocabularies.defaultEmptyChipsFor(locale)
                else -> emptyList()
            }
            for (word in candidates) {
                if (results.size >= 3) break
                val cased = enforceMidSentenceCasingRule(word, primaryLang, atSentenceStart, dictionaryStore)
                if (results.none { it.text.equals(cased, ignoreCase = true) }) {
                    results += SuggestionChip(text = cased, isNextWord = nextWord)
                }
            }
            return results
        }

        val lower = currentWord.lowercase()
        val knownByUser = dictionaryStore?.isKnownWord(currentWord) == true
        val personalFix = dictionaryStore?.preferredCorrectionFor(currentWord)
        if (personalFix != null && personalFix != currentWord && results.size < 3) {
            results += SuggestionChip(
                text = preserveLeadingCase(currentWord, personalFix),
                isCorrection = true,
                replaceCharCount = currentWord.length
            )
        }
        val typoMap = LanguageVocabularies.typoMapFor(primaryLang)
        val exactTypo = if (knownByUser) null else typoMap[lower]
        if (exactTypo != null && exactTypo != currentWord && results.size < 3 &&
            results.none { it.text.equals(exactTypo, ignoreCase = false) }
        ) {
            val cased = preserveLeadingCase(currentWord, exactTypo)
            results += SuggestionChip(
                text = cased,
                isCorrection = true,
                replaceCharCount = currentWord.length
            )
        }

        // German noun capitalization (stock nouns + morphological suffix nouns like -ung, -heit, -keit, -schaft, -tion)
        if (!knownByUser && primaryBase == "de" &&
            currentWord.first().isLowerCase() &&
            !LanguageVocabularies.isNeverAutoCapitalizeMidSentence(lower, primaryLang) &&
            (lower in LanguageVocabularies.germanCapitalNouns || LanguageVocabularies.isLikelyGermanNounBySuffix(lower))
        ) {
            val capitalized = currentWord.replaceFirstChar { it.uppercaseChar() }
            if (results.size < 3 && results.none { it.text == capitalized }) {
                results += SuggestionChip(
                    text = capitalized,
                    isCorrection = true,
                    replaceCharCount = currentWord.length
                )
            }
        }

        if (includeEmoji && results.size < 3) {
            val learnedEmoji = dictionaryStore?.preferredEmojiFor(lower)
            val emoji = learnedEmoji ?: LanguageVocabularies.emojiTriggers[lower]
            if (emoji != null) {
                results += SuggestionChip(
                    text = emoji,
                    isEmoji = true,
                    replaceCharCount = 0
                )
            }
        }

        // Session topic context: words already typed earlier in this message get immediate prefix completion!
        val sessionWords = extractSessionContextWords(textBeforeCursor, prefixEndIdx)
        if (results.size < 3 && lower.length >= 2 && sessionWords.isNotEmpty()) {
            for (sw in sessionWords) {
                if (results.size >= 3) break
                if (sw.length > lower.length && sw.startsWith(lower, ignoreCase = true) && !sw.equals(currentWord, ignoreCase = true)) {
                    val adjusted = adjustCandidateCase(currentWord, sw, primaryLang, atSentenceStart, dictionaryStore)
                    if (results.none { it.text == adjusted }) {
                        results += SuggestionChip(text = adjusted, replaceCharCount = currentWord.length)
                    }
                }
            }
        }

        // Prioritize context-aware learned vocabulary, per-app words & custom dictionary (O(1) bucket lookup!)
        if (dictionaryStore != null && results.size < 3) {
            dictionaryStore.rankedPrefixCandidates(
                prefix = currentWord,
                limit = 4,
                previousWord = prevWord.takeIf { it.isNotEmpty() },
                previousTwoWord = prevTwoWord.takeIf { it.isNotEmpty() },
                atSentenceStart = atSentenceStart,
                appPackage = appPackage
            ).forEach { match ->
                val adjusted = adjustCandidateCase(currentWord, match, primaryLang, atSentenceStart, dictionaryStore)
                if (results.size < 3 && adjusted != currentWord && results.none { it.text == adjusted }) {
                    results += SuggestionChip(
                        text = adjusted,
                        isCorrection = false,
                        replaceCharCount = currentWord.length
                    )
                }
            }
        }

        // O(1) indexed prefix completions + diacritic-insensitive matches across active vocab & 50k offline pack
        val contextualBoostWords = if (prevWord.isNotEmpty()) {
            LanguageVocabularies.commonNextWords(primaryLang, prevWord, prevTwoWord)
                .mapTo(HashSet(8)) { it.lowercase() }
        } else emptySet()

        val vocabPrefixMatches = primaryIndex.prefixMatches(lower, limit = 6)
        val packPrefixes = if (primaryBase == baseLocale) offlinePack?.prefixMatches(lower, limit = 6).orEmpty() else emptyList()

        // O(1) diacritic/umlaut restoration across ALL 9 languages (e.g. "uber"->"über", "francais"->"français", "manana"->"mañana")
        val diacriticMatches = if (lower.length >= 2) {
            val isGerman = primaryBase == "de"
            val normLower = LanguageVocabularies.foldDiacritics(lower, isGerman)
            val found = ArrayList<String>(4)
            primaryIndex.foldedExact[normLower]?.forEach { cand ->
                if (!cand.equals(currentWord, ignoreCase = true) && found.size < 4 && cand !in found) found += cand
            }
            if (found.size < 4 && normLower.length >= 2) {
                primaryIndex.foldedPrefix2[normLower.substring(0, 2)]?.forEach { cand ->
                    if (found.size < 4 &&
                        !cand.equals(currentWord, ignoreCase = true) &&
                        LanguageVocabularies.foldDiacritics(cand, isGerman).startsWith(normLower) &&
                        cand !in found
                    ) {
                        found += cand
                    }
                }
            }
            if (found.size < 4 && primaryBase == baseLocale && offlinePack != null && normLower.length >= 3) {
                offlinePack.foldedPrefixMatches(normLower, limit = 4).forEach { cand ->
                    if (found.size < 4 && !cand.equals(currentWord, ignoreCase = true) && cand !in found) found += cand
                }
            }
            found
        } else emptyList()

        val prefixMatches = LinkedHashSet<String>(16).apply {
            addAll(vocabPrefixMatches)
            addAll(packPrefixes)
            addAll(diacriticMatches)
        }.let { set ->
            if (contextualBoostWords.isEmpty()) set.toList()
            else set.sortedByDescending { if (it.lowercase() in contextualBoostWords) 1 else 0 }
        }

        for (match in prefixMatches) {
            if (results.size >= 3) break
            val adjusted = adjustCandidateCase(currentWord, match, primaryLang, atSentenceStart, dictionaryStore)
            if (adjusted != currentWord && results.none { it.text == adjusted }) {
                results += SuggestionChip(
                    text = adjusted,
                    isCorrection = false,
                    replaceCharCount = currentWord.length
                )
            }
        }

        // Secondary enabled languages for seamless bilingual typing without switching layouts
        if (results.size < 3 && activeLangOrder.size > 1) {
            for (i in 1 until activeLangOrder.size) {
                val sec = activeLangOrder[i]
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
                val secIndex = LanguageVocabularies.indexFor(sec)
                for (match in secIndex.prefixMatches(lower, limit = 4)) {
                    if (results.size >= 3) break
                    val adjusted = adjustCandidateCase(currentWord, match, sec, atSentenceStart, dictionaryStore)
                    if (adjusted != currentWord && results.none { it.text == adjusted }) {
                        results += SuggestionChip(
                            text = adjusted,
                            isCorrection = false,
                            replaceCharCount = currentWord.length
                        )
                    }
                }
            }
        }

        // Algorithmic Missing-Space Word Split (Gboard-grade: "ichbin" -> "ich bin", "thankyou" -> "thank you")
        val isKnownInVocab = knownByUser ||
            primaryIndex.lowerToCanonical.containsKey(lower) ||
            (primaryBase == baseLocale && offlinePack?.contains(lower) == true)
        if (results.size < 3 && lower.length in 4..22 && !isKnownInVocab) {
            val splitFix = findWordSplitCorrection(
                lower = lower,
                locale = primaryLang,
                primaryIndex = primaryIndex,
                offlinePack = if (primaryBase == baseLocale) offlinePack else null,
                dictionaryStore = dictionaryStore
            )
            if (splitFix != null) {
                val cased = preserveLeadingCase(currentWord, splitFix)
                if (results.none { it.text.equals(cased, ignoreCase = true) }) {
                    results += SuggestionChip(
                        text = cased,
                        isCorrection = true,
                        replaceCharCount = currentWord.length
                    )
                }
            }
        }

        // Ultra-fast SymSpell 1-delete + Keyboard-Proximity weighted Damerau-Levenshtein fuzzy matching
        if (results.size < 3 && lower.length >= 3 && !isKnownInVocab) {
            val seen = HashSet<String>(128)
            val fuzzy = ArrayList<Triple<String, Double, Int>>(24)
            val maxDist = 2.15

            fun considerCandidate(word: String, rankBonus: Int) {
                if (abs(word.length - lower.length) > 2) return
                if (word == currentWord) return
                val wl = word.lowercase()
                if (!seen.add(wl)) return
                val dist = weightedKeyboardDistance(lower, wl, primaryLang, maxDistance = maxDist)
                if (dist > 0.0 && dist <= maxDist) {
                    val contextBonus = if (wl in contextualBoostWords) 95 else 0
                    val personalBoost = (dictionaryStore?.wordScore(word) ?: 0) + contextBonus + rankBonus
                    fuzzy += Triple(word, dist, personalBoost)
                }
            }

            // 1. O(1) SymSpell candidates from primary vocab + 50k pack
            for (cand in primaryIndex.symSpellCandidates(lower)) {
                val rank = primaryIndex.frequencyRank[cand.lowercase()] ?: 500
                considerCandidate(cand, (40 - rank / 20).coerceAtLeast(0))
            }
            if (primaryBase == baseLocale && offlinePack != null) {
                for (cand in offlinePack.symSpellCandidates(lower, limit = 48)) {
                    val freq = offlinePack.wordFreq[cand] ?: 1
                    considerCandidate(cand, (kotlin.math.ln1p(freq.toDouble()) * 3.0).toInt())
                }
            }
            // 2. Repeated-letter collapsed query lookup (e.g. "hallllo" -> "hallo", "hellooo" -> "hello")
            val collapsed = collapseRepeatedLetters(lower)
            if (collapsed != lower && collapsed.length >= 2) {
                primaryIndex.lowerToCanonical[collapsed]?.let { considerCandidate(it, 55) }
                if (primaryBase == baseLocale && offlinePack?.contains(collapsed) == true) {
                    considerCandidate(collapsed, 45)
                }
                for (cand in primaryIndex.symSpellCandidates(collapsed)) {
                    considerCandidate(cand, 25)
                }
            }
            // 3. User's learned vocabulary (bounded scan)
            dictionaryStore?.allKnownVocabularyWords()?.let { userWords ->
                var count = 0
                for (w in userWords) {
                    if (count++ >= 1_200) break
                    considerCandidate(w, 30)
                }
            }
            // 4. Primary built-in vocab fallback for distance ~2 typos not caught by 1-delete SymSpell
            for (w in primaryIndex.words) {
                considerCandidate(w, 10)
            }
            // 5. Secondary languages SymSpell
            for (i in 1 until activeLangOrder.size) {
                val secIndex = LanguageVocabularies.indexFor(activeLangOrder[i])
                for (cand in secIndex.symSpellCandidates(lower)) {
                    considerCandidate(cand, 8)
                }
            }

            fuzzy.sortWith(compareBy<Triple<String, Double, Int>> { it.second }.thenByDescending { it.third })

            for ((word, _, _) in fuzzy) {
                if (results.size >= 3) break
                val adjusted = adjustCandidateCase(currentWord, word, primaryLang, atSentenceStart, dictionaryStore)
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

    private fun resolveSmartProfileSuggestions(
        fieldType: SmartFieldType,
        profile: PersonalProfile?,
        dictionaryStore: AdaptiveDictionaryStore?,
        locale: String,
        prefix: String,
        appPackage: String?,
        limit: Int
    ): List<String> {
        if (dictionaryStore != null) {
            return dictionaryStore.suggestProfileValues(
                fieldType = fieldType,
                profile = profile,
                locale = locale,
                prefix = prefix,
                appPackage = appPackage,
                limit = limit
            )
        }
        if (profile == null || profile.isEmpty) return emptyList()
        val raw = when (fieldType) {
            SmartFieldType.FIRST_NAME -> listOfNotNull(
                profile.firstName.takeIf { it.isNotBlank() },
                profile.fullName.takeIf { it.isNotBlank() && it != profile.firstName }
            )
            SmartFieldType.LAST_NAME -> listOfNotNull(profile.lastName.takeIf { it.isNotBlank() })
            SmartFieldType.FULL_NAME -> listOfNotNull(
                profile.fullName.takeIf { it.isNotBlank() },
                profile.firstName.takeIf { it.isNotBlank() && it != profile.fullName }
            )
            SmartFieldType.EMAIL -> profile.emails.filter { it.isNotBlank() }
            SmartFieldType.PHONE -> profile.phones.filter { it.isNotBlank() }
            SmartFieldType.STREET_ADDRESS -> listOfNotNull(
                profile.fullAddress.takeIf { it.isNotBlank() && it != profile.street },
                profile.street.takeIf { it.isNotBlank() }
            )
            SmartFieldType.POSTAL_CODE -> listOfNotNull(profile.postalCode.takeIf { it.isNotBlank() })
            SmartFieldType.CITY -> listOfNotNull(profile.city.takeIf { it.isNotBlank() })
            SmartFieldType.COUNTRY -> listOfNotNull(profile.country.takeIf { it.isNotBlank() })
            SmartFieldType.BIRTHDATE -> listOfNotNull(profile.formattedBirthDate(locale).takeIf { it.isNotBlank() })
            SmartFieldType.AGE -> listOfNotNull(profile.computedAgeYears()?.toString())
            SmartFieldType.COMPANY -> listOfNotNull(profile.company.takeIf { it.isNotBlank() })
            SmartFieldType.WEBSITE -> listOfNotNull(profile.website.takeIf { it.isNotBlank() })
            SmartFieldType.NONE -> emptyList()
        }
        val cleanPrefix = prefix.trim()
        val filtered = if (cleanPrefix.isEmpty()) raw else {
            val digitsPrefix = if (fieldType == SmartFieldType.PHONE) cleanPrefix.filter(Char::isDigit) else ""
            raw.filter { cand ->
                !cand.equals(cleanPrefix, ignoreCase = true) && (
                    cand.startsWith(cleanPrefix, ignoreCase = true) ||
                        (fieldType == SmartFieldType.PHONE && digitsPrefix.isNotEmpty() && cand.filter(Char::isDigit).contains(digitsPrefix))
                    )
            }
        }
        return filtered.distinctBy { it.lowercase() }.take(limit)
    }

    /**
     * Detects whether the user is currently writing in a secondary enabled language (e.g., English
     * while on German QWERTZ layout) based on the previous 1-2 words, and prioritizes that language.
     */
    private fun resolveActiveLocaleOrder(
        primaryLocale: String,
        secondaryLocales: List<String>,
        prevWord: String,
        prevTwoWord: String
    ): List<String> {
        if (secondaryLocales.isEmpty()) return listOf(primaryLocale)
        val primaryBase = KeyboardLocales.baseLang(primaryLocale)
        val filteredSecondary = secondaryLocales.filter { KeyboardLocales.baseLang(it) != primaryBase }
        if (filteredSecondary.isEmpty()) return listOf(primaryLocale)
        if (prevWord.isEmpty()) return listOf(primaryLocale) + filteredSecondary

        val p1 = prevWord.lowercase()
        val p2 = prevTwoWord.lowercase()
        val primaryIndex = LanguageVocabularies.indexFor(primaryLocale)
        val primaryScore = (if (primaryIndex.lowerToCanonical.containsKey(p1)) 1 else 0) +
            (if (p2.isNotEmpty() && primaryIndex.lowerToCanonical.containsKey(p2)) 1 else 0) +
            (if (LanguageVocabularies.commonNextWords(primaryLocale, p1, p2).isNotEmpty()) 1 else 0)

        var bestSec: String? = null
        var bestSecScore = primaryScore
        for (sec in filteredSecondary) {
            val secIndex = LanguageVocabularies.indexFor(sec)
            val secScore = (if (secIndex.lowerToCanonical.containsKey(p1)) 1 else 0) +
                (if (p2.isNotEmpty() && secIndex.lowerToCanonical.containsKey(p2)) 1 else 0) +
                (if (LanguageVocabularies.commonNextWords(sec, p1, p2).isNotEmpty()) 2 else 0)
            if (secScore > bestSecScore) {
                bestSecScore = secScore
                bestSec = sec
            }
        }
        return if (bestSec != null) {
            listOf(bestSec, primaryLocale) + filteredSecondary.filter { it != bestSec }
        } else {
            listOf(primaryLocale) + filteredSecondary
        }
    }

    /**
     * Algorithmic Missing-Space Word Splitter (e.g. "ichbin" -> "ich bin", "dasmacht" -> "das macht",
     * "thankyou" -> "thank you"). Uses frequency + bigram synergy while refusing to split valid compounds.
     */
    internal fun findWordSplitCorrection(
        lower: String,
        locale: String,
        primaryIndex: LanguageVocabularies.VocabIndex = LanguageVocabularies.indexFor(locale),
        offlinePack: LoadedDictionaryPack? = null,
        dictionaryStore: AdaptiveDictionaryStore? = null
    ): String? {
        if (lower.length !in 4..22) return null
        if (primaryIndex.lowerToCanonical.containsKey(lower)) return null
        if (offlinePack?.contains(lower) == true) return null
        if (dictionaryStore?.isKnownWord(lower) == true) return null

        val baseLang = KeyboardLocales.baseLang(locale)
        var bestSplit: String? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (i in 1 until lower.length) {
            val left = lower.substring(0, i)
            val right = lower.substring(i)
            // Avoid spurious 1-letter splits except English "i" / "a"
            if (left.length == 1 && !(baseLang == "en" && (left == "i" || left == "a"))) continue
            if (right.length < 2) continue

            val leftCanon = primaryIndex.lowerToCanonical[left]
            val rightCanon = primaryIndex.lowerToCanonical[right]
            val leftRank = primaryIndex.frequencyRank[left]
            val rightRank = primaryIndex.frequencyRank[right]

            val leftValid = leftCanon != null && (leftRank != null && leftRank < 350)
            val rightValid = rightCanon != null && (rightRank != null && rightRank < 350)
            if (!leftValid || !rightValid) continue

            // Guard against splitting valid German compound nouns (where both halves are nouns and left is long)
            if (baseLang == "de" && leftCanon!![0].isUpperCase() && rightCanon!![0].isUpperCase()) continue

            val nextWords = LanguageVocabularies.commonNextWords(locale, left)
            val isCommonBigram = nextWords.any { it.equals(right, ignoreCase = true) }
            val freqScore = (400 - leftRank!!) + (400 - rightRank!!) + (if (isCommonBigram) 500 else 0)
            // Require strong evidence: either a known bigram or two top-180 words
            if (!isCommonBigram && (leftRank > 160 || rightRank > 160)) continue

            if (freqScore > bestScore) {
                bestScore = freqScore.toDouble()
                val leftOut = if (baseLang == "en" && left == "i") "I" else leftCanon!!
                bestSplit = "$leftOut ${rightCanon!!}"
            }
        }
        return bestSplit
    }

    private fun collapseRepeatedLetters(s: String): String {
        if (s.length < 3) return s
        var hasTriple = false
        for (i in 2 until s.length) {
            if (s[i] == s[i - 1] && s[i] == s[i - 2]) {
                hasTriple = true
                break
            }
        }
        if (!hasTriple) return s
        val sb = StringBuilder(s.length)
        var run = 1
        sb.append(s[0])
        for (i in 1 until s.length) {
            if (s[i] == s[i - 1]) {
                run++
                if (run <= 2) sb.append(s[i])
            } else {
                run = 1
                sb.append(s[i])
            }
        }
        return sb.toString()
    }

    fun autoCorrectionFor(
        word: String,
        locale: String,
        dictionaryStore: AdaptiveDictionaryStore? = null,
        atSentenceStart: Boolean = false,
        secondaryLocales: List<String> = emptyList(),
        offlinePack: LoadedDictionaryPack? = null,
        previousWord: String? = null,
        previousTwoWord: String? = null,
        appPackage: String? = null
    ): String? {
        val baseLocale = KeyboardLocales.baseLang(locale)
        val hasEnglishSecondary = secondaryLocales.any { KeyboardLocales.baseLang(it) == "en" }
        val prevLower = previousWord?.trim()?.lowercase().orEmpty()
        val prev2Lower = previousTwoWord?.trim()?.lowercase().orEmpty()

        // Standalone English pronoun "i" -> "I" (active EN or bilingual EN sentence context)
        if (word == "i" && (baseLocale == "en" ||
                (hasEnglishSecondary && (prevLower in englishSentenceCues || prev2Lower in englishSentenceCues)))
        ) {
            return "I"
        }
        if (word.length < 2) return null
        if (dictionaryStore?.isKnownWord(word) == true) {
            return null
        }
        val lower = word.lowercase()

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
            !LanguageVocabularies.isNeverAutoCapitalizeMidSentence(lower, locale) &&
            word.first().isLowerCase() &&
            (lower in LanguageVocabularies.germanCapitalNouns || LanguageVocabularies.isLikelyGermanNounBySuffix(lower))
        ) {
            return word.replaceFirstChar { it.uppercaseChar() }
        }

        val primaryIndex = LanguageVocabularies.indexFor(locale)
        // German article-triggered noun capitalization (O(1) lookup!)
        if (baseLocale == "de" &&
            prevLower in LanguageVocabularies.germanNounPrecursors &&
            word.first().isLowerCase() &&
            !LanguageVocabularies.isNeverAutoCapitalizeMidSentence(lower, locale)
        ) {
            val capitalNoun = primaryIndex.lowerToCanonical[lower]
            if (capitalNoun != null && capitalNoun.firstOrNull()?.isUpperCase() == true) return capitalNoun
        }

        // Check secondary languages for valid words or clear typos
        for (sec in secondaryLocales) {
            if (KeyboardLocales.baseLang(sec) == baseLocale) continue
            val secIndex = LanguageVocabularies.indexFor(sec)
            if (secIndex.lowerToCanonical.containsKey(lower)) return null // Valid word in secondary language
            val secMapped = LanguageVocabularies.typoMapFor(sec)[lower]
            if (secMapped != null) return preserveLeadingCase(word, secMapped)
        }

        // O(1) validity check in stock vocab or downloaded 50k pack
        if (primaryIndex.lowerToCanonical.containsKey(lower) || offlinePack?.contains(word) == true) {
            return null
        }

        // High-confidence missing-space word split (e.g. "ichbin" -> "ich bin")
        if (lower.length in 5..18) {
            val split = findWordSplitCorrection(lower, locale, primaryIndex, offlinePack, dictionaryStore)
            if (split != null) {
                return preserveLeadingCase(word, split)
            }
        }

        // Build lightweight next-word context set for context-aware disambiguation
        val contextNext = HashSet<String>(12)
        if (prevLower.isNotEmpty()) {
            dictionaryStore?.predictNextWords(
                previousWord = prevLower,
                limit = 8,
                previousTwoWord = prev2Lower.takeIf { it.isNotEmpty() },
                appPackage = appPackage
            )?.forEach { contextNext += it.lowercase() }
            LanguageVocabularies.commonNextWords(locale, prevLower, prev2Lower)
                .forEach { contextNext += it.lowercase() }
        }

        // Check high-confidence keyboard-proximity / transposition typo in user's learned words
        if (word.length >= 4 && dictionaryStore != null && !atSentenceStart) {
            val learnedMatch = dictionaryStore.allKnownVocabularyWords()
                .asSequence()
                .filter { abs(it.length - word.length) <= 1 && dictionaryStore.wordScore(it) >= 15 }
                .map { cand ->
                    val rawDist = weightedKeyboardDistance(lower, cand.lowercase(), locale, maxDistance = 0.85)
                    val ctxDiscount = if (cand.lowercase() in contextNext) 0.18 else 0.0
                    cand to (rawDist - ctxDiscount).coerceAtLeast(0.01)
                }
                .filter { (_, dist) -> dist > 0.0 && dist <= 0.85 }
                .minWithOrNull(compareBy<Pair<String, Double>> { it.second }.thenByDescending { dictionaryStore.wordScore(it.first) })
                ?.first
            if (learnedMatch != null && !learnedMatch.equals(word, ignoreCase = true)) {
                return adjustCandidateCase(word, learnedMatch, locale, atSentenceStart, dictionaryStore)
            }
        }

        // Safe context-aware proximity/transposition/double-letter autocorrect against primary vocabulary
        if (word.length >= 3) {
            var bestWord: String? = null
            var bestScore = 99.0
            var secondScore = 99.0
            val maxAllowed = if (word.length == 3) 0.50 else 0.68
            val candidates = LinkedHashSet<String>(64).apply {
                addAll(primaryIndex.symSpellCandidates(lower))
                val collapsed = collapseRepeatedLetters(lower)
                if (collapsed != lower) {
                    primaryIndex.lowerToCanonical[collapsed]?.let { add(it) }
                }
                // Include primary vocab for short words (<= 5 chars) to catch pure substitutions
                if (word.length <= 6) {
                    for (w in primaryIndex.words) {
                        if (abs(w.length - word.length) <= 1) add(w)
                    }
                }
            }
            for (cand in candidates) {
                if (abs(cand.length - word.length) > 1) continue
                val cl = cand.lowercase()
                val inContext = cl in contextNext
                if (word.length == 3 && !inContext) continue
                val rawDist = weightedKeyboardDistance(lower, cl, locale, maxDistance = 0.95)
                if (rawDist <= 0.0 || rawDist >= 0.95) continue
                val effectiveScore = (rawDist - if (inContext) 0.22 else 0.0).coerceAtLeast(0.01)
                if (effectiveScore < bestScore) {
                    secondScore = bestScore
                    bestScore = effectiveScore
                    bestWord = cand
                } else if (effectiveScore < secondScore) {
                    secondScore = effectiveScore
                }
            }
            if (bestWord != null && bestScore <= maxAllowed && (secondScore - bestScore) >= 0.18) {
                return adjustCandidateCase(word, bestWord, locale, atSentenceStart, dictionaryStore)
            }
        }

        return null
    }

    fun detectGrammarHint(textBeforeCursor: String, locale: String): GrammarHint? {
        if (textBeforeCursor.length < 3) return null
        val base = KeyboardLocales.baseLang(locale)
        val window = if (textBeforeCursor.length > 64) textBeforeCursor.substring(textBeforeCursor.length - 64) else textBeforeCursor

        // 1. Duplicate adjacent word e.g. "der der" or "the the"
        val dupMatch = dupWordRegex.find(window)
        if (dupMatch != null) {
            val word = dupMatch.groupValues[1]
            return GrammarHint(
                description = if (base == "de") "Doppeltes Wort entfernen" else "Remove duplicate word",
                replaceCharCount = dupMatch.value.length,
                replacement = word
            )
        }

        // 2. Missing capitalization right after sentence boundary (e.g. "Hallo. wie")
        val capMatch = missingSentenceCapRegex.find(window)
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

        // 3. Extra space before punctuation e.g. "Hallo ,"
        val spacePunctMatch = spaceBeforePunctRegex.find(window)
        if (spacePunctMatch != null) {
            val word = spacePunctMatch.groupValues[1]
            val punct = spacePunctMatch.groupValues[2]
            return GrammarHint(
                description = if (base == "de") "Leerzeichen vor Satzzeichen" else "Fix spacing",
                replaceCharCount = spacePunctMatch.value.length,
                replacement = "$word$punct"
            )
        }

        // 4. Missing space after comma e.g. "Hallo,wie" -> "Hallo, wie"
        val commaSpaceMatch = missingSpaceAfterCommaRegex.find(window)
        if (commaSpaceMatch != null) {
            val left = commaSpaceMatch.groupValues[1]
            val right = commaSpaceMatch.groupValues[2]
            return GrammarHint(
                description = if (base == "de") "Leerzeichen nach Komma" else "Add space after comma",
                replaceCharCount = commaSpaceMatch.value.length,
                replacement = "$left, $right"
            )
        }

        // 5. German "das" vs "dass" hints
        if (base == "de") {
            val dassPronoun = germanDassPronounRegex.find(window)
            if (dassPronoun != null) {
                return GrammarHint(
                    description = "„dass“ statt „das“",
                    replaceCharCount = dassPronoun.value.length,
                    replacement = "${dassPronoun.groupValues[1]}dass${dassPronoun.groupValues[2]}"
                )
            }
            val verbDas = germanVerbDasRegex.find(window)
            if (verbDas != null) {
                return GrammarHint(
                    description = "„, dass“",
                    replaceCharCount = verbDas.value.length,
                    replacement = "${verbDas.groupValues[1]}, dass"
                )
            }
        }

        // 6. English "a" vs "an" article hints
        if (base == "en") {
            val artMatch = englishArticleRegex.find(window)
            if (artMatch != null) {
                val art = artMatch.groupValues[1]
                val nextW = artMatch.groupValues[2]
                val nextLower = nextW.lowercase()
                val needsAn = (nextLower[0] in "aeiou" && nextLower !in ENGLISH_A_EXCEPTIONS) ||
                    nextLower in ENGLISH_AN_EXCEPTIONS
                val artLower = art.lowercase()
                if (artLower == "a" && needsAn) {
                    val fixedArt = if (art[0].isUpperCase()) "An" else "an"
                    return GrammarHint(
                        description = "Use 'an'",
                        replaceCharCount = artMatch.value.length,
                        replacement = "$fixedArt $nextW"
                    )
                } else if (artLower == "an" && !needsAn) {
                    val fixedArt = if (art[0].isUpperCase()) "A" else "a"
                    return GrammarHint(
                        description = "Use 'a'",
                        replaceCharCount = artMatch.value.length,
                        replacement = "$fixedArt $nextW"
                    )
                }
            }
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

    /** Zero-allocation backward scan for sentence-start detection on the typing hot path. */
    fun isAtSentenceStart(textBeforeCursor: String, currentWord: String = extractTrailingWord(textBeforeCursor)): Boolean {
        var i = textBeforeCursor.length - currentWord.length - 1
        while (i >= 0 && textBeforeCursor[i].isWhitespace()) {
            if (textBeforeCursor[i] == '\n' || textBeforeCursor[i] == '\r') return true
            i--
        }
        if (i < 0) return true
        val ch = textBeforeCursor[i]
        return ch == '.' || ch == '!' || ch == '?'
    }

    fun extractTrailingWord(textBeforeCursor: String): String {
        if (textBeforeCursor.isEmpty()) return ""
        var i = textBeforeCursor.length - 1
        while (i >= 0 && isWordChar(textBeforeCursor[i])) i--
        return if (i < 0) textBeforeCursor else textBeforeCursor.substring(i + 1)
    }

    fun extractTrailingToken(textBeforeCursor: String): String {
        if (textBeforeCursor.isEmpty()) return ""
        var i = textBeforeCursor.length - 1
        while (i >= 0 && !textBeforeCursor[i].isWhitespace()) i--
        return if (i < 0) textBeforeCursor else textBeforeCursor.substring(i + 1)
    }

    fun extractPreviousWord(textBeforeCursor: String): String {
        var end = textBeforeCursor.length
        while (end > 0 && textBeforeCursor[end - 1].isWhitespace()) end--
        if (end == 0) return ""
        var start = end - 1
        while (start >= 0 && isWordChar(textBeforeCursor[start])) start--
        return textBeforeCursor.substring(start + 1, end)
    }

    fun extractPreviousTwoWords(textBeforeCurrentWord: String): Pair<String, String> =
        extractPreviousTwoWordsRange(textBeforeCurrentWord, textBeforeCurrentWord.length)

    /** Zero-regex backward scanner extracting (prevWord, prevTwoWord) within the current sentence. */
    private fun extractPreviousTwoWordsRange(text: String, endExclusive: Int): Pair<String, String> {
        var i = endExclusive - 1
        var first = ""
        var second = ""
        var found = 0

        while (i >= 0 && found < 2) {
            val ch = text[i]
            if (ch == '\n' || ch == '\r') break
            if ((ch == '.' || ch == '!' || ch == '?') && (i + 1 >= endExclusive || text[i + 1].isWhitespace())) {
                break
            }
            if (isWordChar(ch)) {
                val wordEnd = i + 1
                while (i >= 0 && isWordChar(text[i])) i--
                val wordStart = i + 1
                // Trim leading punctuation-like apostrophe/hyphen so word starts with a letter
                var cleanStart = wordStart
                while (cleanStart < wordEnd && !text[cleanStart].isLetter()) cleanStart++
                if (cleanStart < wordEnd) {
                    val w = text.substring(cleanStart, wordEnd)
                    if (found == 0) first = w else second = w
                    found++
                }
            } else {
                i--
            }
        }
        return first to second
    }

    private fun extractSessionContextWords(text: String, endExclusive: Int): List<String> {
        if (endExclusive < 8) return emptyList()
        val seen = LinkedHashSet<String>(12)
        var i = endExclusive - 1
        while (i >= 0 && seen.size < 12) {
            if (isWordChar(text[i])) {
                val end = i + 1
                while (i >= 0 && isWordChar(text[i])) i--
                val start = i + 1
                if (end - start >= 4 && text[start].isLetter()) {
                    seen += text.substring(start, end)
                }
            } else {
                i--
            }
        }
        return seen.toList()
    }

    private fun isWordChar(ch: Char): Boolean =
        ch.isLetter() || ch == '\'' || ch == '’' || ch == '-'

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

    fun normalizeForUmlautMatch(raw: String, baseLocale: String): String =
        LanguageVocabularies.foldDiacritics(raw, baseLocale == "de")

    private const val COORD_TABLE_CHARS = 384
    private val coordsTableByLanguage = ConcurrentHashMap<String, FloatArray>()
    private val diacriticBaseTable: CharArray = CharArray(COORD_TABLE_CHARS) { code ->
        LanguageVocabularies.foldCharBase(code.toChar())
    }
    private val dpRowScratch = object : ThreadLocal<Array<DoubleArray>>() {
        override fun initialValue(): Array<DoubleArray> = Array(3) { DoubleArray(64) }
    }

    private fun keyGridCoordTable(locale: String): FloatArray =
        coordsTableByLanguage.getOrPut(KeyboardLocales.baseLang(locale)) {
            buildKeyGridTable(KeyboardLocales.baseLang(locale))
        }

    private fun buildKeyGridTable(baseLang: String): FloatArray {
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
        val table = FloatArray(COORD_TABLE_CHARS * 2) { Float.NaN }
        fun put(ch: Char, x: Float, y: Float) {
            val code = ch.code
            if (code in 0 until COORD_TABLE_CHARS) {
                table[code * 2] = x
                table[code * 2 + 1] = y
            }
        }
        fun copyOrPut(ch: Char, ref: Char, fallbackX: Float, fallbackY: Float) {
            val rc = ref.code
            if (rc in 0 until COORD_TABLE_CHARS && !table[rc * 2].isNaN()) {
                put(ch, table[rc * 2], table[rc * 2 + 1])
            } else {
                put(ch, fallbackX, fallbackY)
            }
        }
        top.forEachIndexed { idx, ch -> put(ch, idx.toFloat(), 0f) }
        mid.forEachIndexed { idx, ch -> put(ch, idx + 0.5f, 1f) }
        bot.forEachIndexed { idx, ch -> put(ch, idx + 1.5f, 2f) }
        copyOrPut('ä', 'a', 0.5f, 1f)
        copyOrPut('ö', 'o', 8f, 0f)
        copyOrPut('ü', 'u', 6f, 0f)
        copyOrPut('ß', 's', 1.5f, 1f)
        return table
    }

    private fun isDiacriticEquivalent(ca: Char, cb: Char): Boolean {
        val ac = ca.code
        val bc = cb.code
        if (ac >= COORD_TABLE_CHARS || bc >= COORD_TABLE_CHARS) return false
        return diacriticBaseTable[ac] == diacriticBaseTable[bc]
    }

    fun weightedKeyboardDistance(
        a: String,
        b: String,
        locale: String = "de",
        maxDistance: Double = Double.MAX_VALUE
    ): Double {
        if (a == b) return 0.0
        val n = a.length
        val m = b.length
        if (abs(n - m) > 2) return 99.0
        val table = keyGridCoordTable(locale)
        val scratch = dpRowScratch.get()
        var prev2: DoubleArray
        var prev1: DoubleArray
        var curr: DoubleArray
        if (scratch != null && m + 1 <= scratch[0].size) {
            prev2 = scratch[0]
            prev1 = scratch[1]
            curr = scratch[2]
        } else {
            prev2 = DoubleArray(m + 1)
            prev1 = DoubleArray(m + 1)
            curr = DoubleArray(m + 1)
        }
        for (j in 0..m) {
            prev2[j] = 0.0
            prev1[j] = j.toDouble()
        }
        for (i in 1..n) {
            curr[0] = i.toDouble()
            var rowMin = curr[0]
            val ca = a[i - 1]
            val isDupInput = i > 1 && ca == a[i - 2]
            val delCost = if (isDupInput) 0.42 else 1.0
            val caCode = ca.code
            val ax = if (caCode in 0 until COORD_TABLE_CHARS) table[caCode * 2] else Float.NaN
            val ay = if (!ax.isNaN()) table[caCode * 2 + 1] else Float.NaN
            for (j in 1..m) {
                val cb = b[j - 1]
                val isDupTarget = j > 1 && cb == b[j - 2]
                val insCost = if (isDupTarget) 0.55 else 1.0
                val subCost = when {
                    ca == cb -> 0.0
                    isDiacriticEquivalent(ca, cb) -> 0.25
                    else -> {
                        val cbCode = cb.code
                        val bx = if (!ax.isNaN() && cbCode in 0 until COORD_TABLE_CHARS) table[cbCode * 2] else Float.NaN
                        if (!bx.isNaN()) {
                            val dx = ax - bx
                            val dy = ay - table[cbCode * 2 + 1]
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
                var best = prev1[j] + delCost
                val ins = curr[j - 1] + insCost
                if (ins < best) best = ins
                val sub = prev1[j - 1] + subCost
                if (sub < best) best = sub
                if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) {
                    val trans = prev2[j - 2] + 0.65
                    if (trans < best) best = trans
                }
                curr[j] = best
                if (best < rowMin) rowMin = best
            }
            if (rowMin > maxDistance + 0.65) return 99.0
            val tmp = prev2
            prev2 = prev1
            prev1 = curr
            curr = tmp
        }
        return prev1[m]
    }

    fun damerauLevenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val n = a.length
        val m = b.length
        if (abs(n - m) > 2) return 99
        var prev2 = IntArray(m + 1)
        var prev1 = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            val ca = a[i - 1]
            for (j in 1..m) {
                val cb = b[j - 1]
                val cost = if (ca == cb) 0 else 1
                var best = min(
                    min(prev1[j] + 1, curr[j - 1] + 1),
                    prev1[j - 1] + cost
                )
                if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) {
                    best = min(best, prev2[j - 2] + cost)
                }
                curr[j] = best
            }
            val tmp = prev2
            prev2 = prev1
            prev1 = curr
            curr = tmp
        }
        return prev1[m]
    }
}
