package com.tapwing.keyboard.dictionary

import android.content.Context
import androidx.core.content.ContextCompat
import com.tapwing.keyboard.ai.WritingAction
import com.tapwing.keyboard.data.KeyboardLocales
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DictionaryStats(
    val customWordCount: Int,
    val shortcutCount: Int,
    val snippetCount: Int = 0,
    val learnedWordCount: Int,
    val learnedBigramCount: Int,
    val learnedTrigramCount: Int = 0,
    val perAppProfileCount: Int = 0,
    val rejectedCount: Int
)

data class ImportSummary(
    val addedWords: Int,
    val addedShortcuts: Int,
    val sourceFormat: String = "text"
)

/**
 * Persistent custom dictionary & adaptive typing-learning store.
 * Learns unigrams, bigrams, trigrams, recency order, emoji habits, and rejected autocorrections
 * while strictly separating sentence-start capitalization from mid-sentence casing so words
 * like "und" are never erroneously capitalized mid-sentence.
 */
class AdaptiveDictionaryStore(
    private val storageFile: File?,
    private val synchronousPersistence: Boolean = true
) {

    private val customWords = linkedMapOf<String, Int>()
    private val customWordsLower = linkedMapOf<String, String>()
    private val shortcuts = linkedMapOf<String, String>()
    private val entryLocales = linkedMapOf<String, String>()

    private val learnedUnigrams = linkedMapOf<String, Int>()
    private val learnedUnigramsLower = linkedMapOf<String, String>()
    // Tracks mid-sentence exact casing counts: lowercase -> (exactForm -> count)
    private val midSentenceCaseCounts = linkedMapOf<String, LinkedHashMap<String, Int>>()
    // Monotonic order index for recency weighting
    private val wordRecency = linkedMapOf<String, Long>()
    private var recencyClock: Long = 0L

    private val learnedBigrams = linkedMapOf<String, LinkedHashMap<String, Int>>()
    private val learnedTrigrams = linkedMapOf<String, LinkedHashMap<String, Int>>()
    private val learnedEmojis = linkedMapOf<String, LinkedHashMap<String, Int>>()
    private val perAppUnigrams = linkedMapOf<String, LinkedHashMap<String, Int>>()
    private val perAppBigrams = linkedMapOf<String, LinkedHashMap<String, LinkedHashMap<String, Int>>>()
    private val perAppTones = linkedMapOf<String, String>()
    private val learnedCorrections = linkedMapOf<String, String>()
    private val voiceCorrections = linkedMapOf<String, String>()
    private val recentEmojis = mutableListOf<String>()
    private val snippets = mutableListOf<TextSnippet>()
    private val rejectedCorrections = linkedSetOf<String>()

    private var lastLoadedModMs: Long = -1L
    @Volatile
    private var dirty: Boolean = false
    private var pendingFlush: java.util.concurrent.ScheduledFuture<*>? = null

    init {
        seedDefaultShortcutsIfEmpty()
        seedDefaultSnippetsIfEmpty()
        loadFromDisk()
    }

    @Synchronized
    fun reloadIfModifiedOnDisk() {
        if (dirty) return
        val file = storageFile ?: return
        if (!file.isFile) return
        val mod = file.lastModified()
        if (mod != lastLoadedModMs) {
            loadFromDisk()
        }
    }

    @Synchronized
    fun importText(rawContent: String): ImportSummary {
        return mergeParsed(CustomDictionaryParser.parse(rawContent))
    }

    @Synchronized
    fun importBytes(bytes: ByteArray): ImportSummary {
        return mergeParsed(CustomDictionaryParser.parseBytes(bytes))
    }

    @Synchronized
    fun importStream(inputStream: InputStream): ImportSummary {
        return mergeParsed(CustomDictionaryParser.parseStream(inputStream))
    }

    private fun mergeParsed(parsed: ParsedDictionaryImport): ImportSummary {
        var wordsAdded = 0
        var shortcutsAdded = 0

        parsed.words.forEach { (word, weight) ->
            val clean = word.trim()
            if (clean.isNotEmpty()) {
                val prev = customWords[clean]
                if (prev == null) wordsAdded++
                customWords[clean] = maxOf(prev ?: 0, weight.coerceIn(1, 10_000))
                customWordsLower[clean.lowercase()] = clean
            }
        }

        parsed.shortcuts.forEach { (trigger, expansion) ->
            val t = trigger.trim().lowercase()
            val e = expansion.trim()
            if (t.isNotEmpty() && e.isNotEmpty()) {
                if (!shortcuts.containsKey(t)) shortcutsAdded++
                shortcuts[t] = e
            }
        }

        parsed.locales.forEach { (key, localeTag) ->
            val k = key.trim().lowercase()
            val loc = localeTag.trim()
            if (k.isNotEmpty() && loc.isNotEmpty()) {
                entryLocales[k] = loc
            }
        }

        saveToDisk()
        return ImportSummary(
            addedWords = wordsAdded,
            addedShortcuts = shortcutsAdded,
            sourceFormat = parsed.sourceFormat
        )
    }

    @Synchronized
    fun addCustomWord(word: String, weight: Int = 20) {
        val clean = word.trim()
        if (clean.length < 2) return
        customWords[clean] = maxOf(customWords[clean] ?: 0, weight)
        customWordsLower[clean.lowercase()] = clean
        saveToDisk()
    }

    @Synchronized
    fun removeCustomWord(word: String) {
        customWords.remove(word)
        customWordsLower.remove(word.lowercase())
        saveToDisk()
    }

    @Synchronized
    fun addShortcut(trigger: String, expansion: String) {
        val t = trigger.trim().lowercase()
        val e = expansion.trim()
        if (t.isEmpty() || e.isEmpty()) return
        shortcuts[t] = e
        saveToDisk()
    }

    @Synchronized
    fun removeShortcut(trigger: String) {
        shortcuts.remove(trigger.lowercase())
        saveToDisk()
    }

    @Synchronized
    fun recordTypedWord(
        word: String,
        previousWord: String?,
        learningEnabled: Boolean,
        incognito: Boolean,
        previousTwoWord: String? = null,
        wasSentenceStart: Boolean = false,
        locale: String = "de",
        appPackage: String? = null
    ) {
        if (!learningEnabled || incognito) return
        if (learnWord(word, previousWord, previousTwoWord, wasSentenceStart, locale, appPackage)) saveToDisk()
    }

    private fun learnWord(
        word: String,
        previousWord: String?,
        previousTwoWord: String?,
        wasSentenceStart: Boolean,
        locale: String,
        appPackage: String?
    ): Boolean {
        val clean = word.trim()
        if (clean.isEmpty() || clean.none { it.isLetter() } || clean.any { !it.isLetter() && it !in "'’-" }) return false

        val lower = clean.lowercase()
        val isSingleLeadingCap = clean.length >= 2 &&
            clean[0].isUpperCase() &&
            clean.drop(1).all { it.isLowerCase() }

        // If typed at the start of a sentence with simple TitleCase (e.g. "Und", "Ich", "The"),
        // store its base unigram as lowercase unless it's already an explicitly capitalized custom word
        // or a known German noun.
        val storedForm = when {
            customWordsLower.containsKey(lower) -> customWordsLower[lower]!!
            wasSentenceStart && isSingleLeadingCap &&
                !(KeyboardLocales.baseLang(locale) == "de" && lower in LanguageVocabularies.germanCapitalNouns) -> lower
            else -> clean
        }

        val existingForm = learnedUnigramsLower[lower]
        val unigramKey = if (existingForm != null && !wasSentenceStart) {
            // Keep existing unless mid-sentence usage establishes the new casing
            existingForm
        } else if (existingForm != null) {
            existingForm
        } else {
            storedForm
        }

        val currentCount = learnedUnigrams[unigramKey] ?: 0
        learnedUnigrams[unigramKey] = (currentCount + 1).coerceAtMost(20_000)
        learnedUnigramsLower.putIfAbsent(lower, unigramKey)
        recencyClock++
        wordRecency[lower] = recencyClock

        if (!wasSentenceStart) {
            val caseMap = midSentenceCaseCounts.getOrPut(lower) { linkedMapOf() }
            caseMap[clean] = ((caseMap[clean] ?: 0) + 1).coerceAtMost(10_000)
            // Update displayed preferred form if this mid-sentence form clearly dominates
            val dominant = dominantMidSentenceForm(lower)
            if (dominant != null && dominant != unigramKey) {
                val movedCount = learnedUnigrams.remove(unigramKey) ?: (currentCount + 1)
                learnedUnigrams[dominant] = movedCount
                learnedUnigramsLower[lower] = dominant
            }
        }

        trimUnigramsIfNeeded()

        val prev = previousWord?.trim()?.lowercase().orEmpty()
        if (prev.isNotEmpty()) {
            val nextMap = learnedBigrams.getOrPut(prev) { linkedMapOf() }
            val nextStored = if (wasSentenceStart && isSingleLeadingCap && lower in LanguageVocabularies.germanNeverAutoCapMidSentence) {
                lower
            } else {
                storedForm
            }
            nextMap[nextStored] = ((nextMap[nextStored] ?: 0) + 1).coerceAtMost(10_000)
            trimSubMap(nextMap, MAX_FOLLOWERS_PER_HEAD)
            trimOuterMap(learnedBigrams, MAX_BIGRAM_HEADS)

            val prev2 = previousTwoWord?.trim()?.lowercase().orEmpty()
            if (prev2.isNotEmpty()) {
                val trigramKey = "$prev2 $prev"
                val triMap = learnedTrigrams.getOrPut(trigramKey) { linkedMapOf() }
                triMap[nextStored] = ((triMap[nextStored] ?: 0) + 1).coerceAtMost(10_000)
                trimSubMap(triMap, MAX_TRIGRAM_FOLLOWERS)
                trimOuterMap(learnedTrigrams, MAX_TRIGRAM_CONTEXTS)
            }
        }

        val cleanPkg = appPackage?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanPkg != null) {
            val appMap = perAppUnigrams.getOrPut(cleanPkg) { linkedMapOf() }
            appMap[storedForm] = ((appMap[storedForm] ?: 0) + 1).coerceAtMost(5_000)
            trimSubMap(appMap, MAX_PER_APP_WORDS)
            trimOuterMap(perAppUnigrams, MAX_APP_PROFILES)

            if (prev.isNotEmpty()) {
                val appBiHead = perAppBigrams.getOrPut(cleanPkg) { linkedMapOf() }
                val appFollowers = appBiHead.getOrPut(prev) { linkedMapOf() }
                val nextStored = if (wasSentenceStart && isSingleLeadingCap && lower in LanguageVocabularies.germanNeverAutoCapMidSentence) {
                    lower
                } else {
                    storedForm
                }
                appFollowers[nextStored] = ((appFollowers[nextStored] ?: 0) + 1).coerceAtMost(5_000)
                trimSubMap(appFollowers, MAX_TRIGRAM_FOLLOWERS)
                trimOuterMap(appBiHead, MAX_PER_APP_BIGRAMS)
                trimOuterMap(perAppBigrams, MAX_APP_PROFILES)
            }
        }
        return true
    }

    @Synchronized
    fun recordAppWritingAction(appPackage: String?, action: WritingAction, learningEnabled: Boolean, incognito: Boolean) {
        if (!learningEnabled || incognito) return
        val pkg = appPackage?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (action in setOf(WritingAction.PROFESSIONAL, WritingAction.CASUAL, WritingAction.CONCISE, WritingAction.EMOTIVE)) {
            perAppTones[pkg] = action.name
            trimOuterMap(perAppTones, MAX_APP_PROFILES)
            saveToDisk()
        }
    }

    @Synchronized
    fun preferredToneForApp(appPackage: String?): WritingAction? {
        val pkg = appPackage?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        perAppTones[pkg]?.let { raw ->
            return runCatching { WritingAction.valueOf(raw) }.getOrNull()
        }
        val lowerPkg = pkg.lowercase()
        return when {
            lowerPkg.contains("whatsapp") || lowerPkg.contains("telegram") ||
                lowerPkg.contains("signal") || lowerPkg.contains("discord") ||
                lowerPkg.contains("instagram") || lowerPkg.contains("messaging") -> WritingAction.CASUAL
            lowerPkg.contains("gm") || lowerPkg.contains("outlook") ||
                lowerPkg.contains("office") || lowerPkg.contains("teams") ||
                lowerPkg.contains("slack") || lowerPkg.contains("linkedin") -> WritingAction.PROFESSIONAL
            else -> null
        }
    }

    @Synchronized
    fun listSnippets(): List<TextSnippet> = snippets.toList()

    @Synchronized
    fun addOrUpdateSnippet(trigger: String, title: String, body: String, existingId: String? = null) {
        val cleanTrigger = trigger.trim().lowercase().let {
            if (it.startsWith("/")) it else "/$it"
        }
        val cleanTitle = title.trim().ifEmpty { cleanTrigger }
        val cleanBody = body.trim()
        if (cleanTrigger.length < 2 || cleanBody.isEmpty()) return

        val idx = if (existingId != null) snippets.indexOfFirst { it.id == existingId } else -1
        val item = TextSnippet(
            id = existingId ?: UUID.randomUUID().toString(),
            trigger = cleanTrigger,
            title = cleanTitle,
            body = cleanBody
        )
        if (idx >= 0) {
            snippets[idx] = item
        } else {
            snippets.removeAll { it.trigger.equals(cleanTrigger, ignoreCase = true) }
            snippets.add(0, item)
        }
        saveToDisk()
    }

    @Synchronized
    fun deleteSnippet(id: String) {
        snippets.removeAll { it.id == id }
        saveToDisk()
    }

    @Synchronized
    fun lookupSnippet(token: String): TextSnippet? {
        val lower = token.trim().lowercase()
        if (lower.isEmpty()) return null
        return snippets.firstOrNull { it.trigger.equals(lower, ignoreCase = true) }
    }

    @Synchronized
    fun recordEmojiAfterWord(word: String, emoji: String, learningEnabled: Boolean, incognito: Boolean) {
        if (!learningEnabled || incognito) return
        val lower = word.trim().lowercase()
        val cleanEmoji = emoji.trim()
        if (lower.length < 2 || cleanEmoji.isEmpty()) return
        val map = learnedEmojis.getOrPut(lower) { linkedMapOf() }
        map[cleanEmoji] = ((map[cleanEmoji] ?: 0) + 1).coerceAtMost(500)
        trimSubMap(map, 6)
        trimOuterMap(learnedEmojis, 400)
        saveToDisk()
    }

    @Synchronized
    fun preferredEmojiFor(word: String): String? {
        val lower = word.trim().lowercase()
        return learnedEmojis[lower]?.maxByOrNull { it.value }?.key
    }

    @Synchronized
    fun recordRecentEmoji(emoji: String, learningEnabled: Boolean, incognito: Boolean) {
        if (!learningEnabled || incognito) return
        val clean = emoji.trim()
        if (clean.isEmpty()) return
        recentEmojis.remove(clean)
        recentEmojis.add(0, clean)
        while (recentEmojis.size > MAX_RECENT_EMOJIS) {
            recentEmojis.removeAt(recentEmojis.lastIndex)
        }
        saveToDisk()
    }

    @Synchronized
    fun listRecentEmojis(): List<String> = recentEmojis.toList()

    @Synchronized
    fun recordChosenCorrection(
        mistypedWord: String,
        chosenWord: String,
        learningEnabled: Boolean,
        incognito: Boolean
    ) {
        if (!learningEnabled || incognito) return
        val rawLower = mistypedWord.trim().lowercase()
        val chosen = chosenWord.trim()
        if (rawLower.length < 2 || chosen.length < 2 || rawLower == chosen.lowercase()) return
        rejectedCorrections.remove(rawLower)
        learnedCorrections[rawLower] = chosen
        if (learnedCorrections.size > MAX_LEARNED_CORRECTIONS) {
            val oldest = learnedCorrections.keys.firstOrNull()
            if (oldest != null) learnedCorrections.remove(oldest)
        }
        saveToDisk()
    }

    @Synchronized
    fun preferredCorrectionFor(word: String): String? {
        val lower = word.trim().lowercase()
        if (lower in rejectedCorrections) return null
        return learnedCorrections[lower]
    }

    /**
     * Records a user correction made after voice dictation (e.g. ASR heard "Maier" -> user corrected to "Meyer").
     * Future voice transcripts automatically apply this mapping and also boost the target word in the custom vocabulary.
     */
    @Synchronized
    fun recordVoiceCorrection(
        heardWord: String,
        correctedWord: String,
        learningEnabled: Boolean,
        incognito: Boolean
    ) {
        if (!learningEnabled || incognito) return
        val heardLower = heardWord.trim().lowercase()
        val target = correctedWord.trim()
        if (heardLower.length < 2 || target.length < 2) return
        if (heardLower == target.lowercase() && heardWord.trim() == target) return
        voiceCorrections[heardLower] = target
        if (voiceCorrections.size > MAX_VOICE_CORRECTIONS) {
            val oldest = voiceCorrections.keys.firstOrNull()
            if (oldest != null) voiceCorrections.remove(oldest)
        }
        // Also reinforce the corrected spelling as a learned word so STT/LLM prompts prioritize it.
        learnedUnigrams[target] = maxOf((learnedUnigrams[target] ?: 0) + 2, 3)
        learnedUnigramsLower[target.lowercase()] = target
        recencyClock++
        wordRecency[target.lowercase()] = recencyClock
        saveToDisk()
    }

    @Synchronized
    fun preferredVoiceCorrectionFor(word: String): String? {
        val lower = word.trim().lowercase()
        if (lower.length < 2) return null
        return voiceCorrections[lower]
    }

    /**
     * Returns the highest-signal personal vocabulary for STT biasing and LLM cleanup/writing prompts:
     * custom dictionary entries, voice-corrected spellings, per-app frequent terms, and top learned words.
     */
    @Synchronized
    fun topVocabularyForVoice(appPackage: String? = null, limit: Int = 30): Set<String> {
        val out = linkedSetOf<String>()
        val seenLower = hashSetOf<String>()
        fun add(raw: String) {
            val clean = raw.trim()
            if (clean.length !in 2..36 || clean.none { it.isLetter() }) return
            val lower = clean.lowercase()
            if (seenLower.add(lower)) out += clean
        }

        // 1) Explicit custom dictionary entries (highest priority: names, brands, jargon)
        customWords.entries
            .sortedByDescending { it.value }
            .forEach { (w, _) ->
                if (out.size < limit) add(w)
            }
        // 2) Targets of learned voice corrections
        voiceCorrections.values.reversed().forEach { target ->
            if (out.size < limit) add(target)
        }
        // 3) Per-app vocabulary for the active app (e.g. work acronyms in Slack/Outlook, names in WhatsApp)
        val cleanPkg = appPackage?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanPkg != null && out.size < limit) {
            perAppUnigrams[cleanPkg]?.entries
                ?.sortedByDescending { it.value }
                ?.forEach { (w, count) ->
                    if (out.size < limit && count >= 2) add(w)
                }
        }
        // 4) Frequently typed / recently reinforced learned words (skip trivial short function words)
        learnedUnigrams.entries
            .asSequence()
            .filter { (w, count) -> count >= 2 && w.length >= 4 }
            .sortedByDescending { (w, count) ->
                count * 10L + ((wordRecency[w.lowercase()] ?: 0L) * 5L / maxOf(1L, recencyClock))
            }
            .forEach { (w, _) ->
                if (out.size < limit) add(w)
            }
        return out
    }

    /**
     * Conservative fuzzy match against explicit custom dictionary words for STT post-processing.
     * Fixes near-miss ASR spellings of personal names/brands (e.g. "Tapwig" -> "Tapwing", "Kubernetis" -> "Kubernetes")
     * while NEVER touching words that are already standard vocabulary words.
     */
    @Synchronized
    fun bestFuzzyCustomMatch(word: String, locale: String): String? {
        val clean = word.trim()
        if (clean.length < 5 || customWords.isEmpty()) return null
        val lower = clean.lowercase()
        // Exact match is handled elsewhere; never rewrite already-known standard words.
        if (customWordsLower.containsKey(lower)) return null
        if (LanguageVocabularies.isNeverAutoCapitalizeMidSentence(clean, locale)) return null
        if (LanguageVocabularies.containsStandardWord(clean, locale)) return null

        val maxDist = if (clean.length >= 8) 2 else 1
        var bestCandidate: String? = null
        var bestDist = Int.MAX_VALUE
        var bestWeight = -1

        for ((customWord, weight) in customWords) {
            if (customWord.length < 5) continue
            val customLower = customWord.lowercase()
            if (kotlin.math.abs(customLower.length - lower.length) > maxDist) continue
            // Require matching first letter (or German phonetic pair c/k, f/v, p/b, t/d, s/z) to avoid false hits
            if (!compatibleInitial(lower[0], customLower[0])) continue
            val dist = levenshteinBounded(lower, customLower, maxDist)
            if (dist in 1..maxDist && (dist < bestDist || (dist == bestDist && weight > bestWeight))) {
                bestDist = dist
                bestWeight = weight
                bestCandidate = customWord
            }
        }
        return bestCandidate
    }

    private fun compatibleInitial(a: Char, b: Char): Boolean {
        if (a == b) return true
        return when (a) {
            'c' -> b == 'k' || b == 'z'
            'k' -> b == 'c'
            'f' -> b == 'v' || b == 'p'
            'v' -> b == 'f' || b == 'w'
            'w' -> b == 'v'
            's' -> b == 'z'
            'z' -> b == 's' || b == 'c'
            else -> false
        }
    }

    private fun levenshteinBounded(a: String, b: String, maxDist: Int): Int {
        val n = a.length
        val m = b.length
        if (kotlin.math.abs(n - m) > maxDist) return maxDist + 1
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                val v = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
                curr[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > maxDist) return maxDist + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }

    @Synchronized
    fun recordTextBatch(
        text: String,
        learningEnabled: Boolean,
        incognito: Boolean,
        locale: String = "de",
        appPackage: String? = null
    ) {
        if (!learningEnabled || incognito || text.isBlank()) return
        val sentences = text.split(Regex("""[.!?\n]+"""))
        for (sentence in sentences) {
            val words = Regex("""[\p{L}][\p{L}'-]*""").findAll(sentence).map { it.value }.toList()
            var p1: String? = null
            var p2: String? = null
            words.forEachIndexed { idx, w ->
                learnWord(
                    word = w,
                    previousWord = p1,
                    previousTwoWord = p2,
                    wasSentenceStart = idx == 0,
                    locale = locale,
                    appPackage = appPackage
                )
                p2 = p1
                p1 = w
            }
        }
        saveToDisk()
    }

    @Synchronized
    fun recordCorrectionRejected(originalWord: String, learningEnabled: Boolean, incognito: Boolean) {
        if (!learningEnabled || incognito) return
        val clean = originalWord.trim()
        if (clean.length < 2) return
        val lower = clean.lowercase()
        rejectedCorrections += lower
        learnedCorrections.remove(lower)
        customWordsLower.putIfAbsent(lower, clean)
        learnedUnigrams[clean] = maxOf(learnedUnigrams[clean] ?: 0, 3)
        learnedUnigramsLower[lower] = clean
        recencyClock++
        wordRecency[lower] = recencyClock
        saveToDisk()
    }

    @Synchronized
    fun unrejectCorrection(word: String) {
        rejectedCorrections.remove(word.trim().lowercase())
        saveToDisk()
    }

    @Synchronized
    fun removeLearnedWord(word: String) {
        val lower = word.trim().lowercase()
        val existingKey = learnedUnigramsLower.remove(lower)
        if (existingKey != null) learnedUnigrams.remove(existingKey)
        learnedUnigrams.remove(word)
        midSentenceCaseCounts.remove(lower)
        wordRecency.remove(lower)
        saveToDisk()
    }

    @Synchronized
    fun isKnownWord(word: String): Boolean {
        val lower = word.lowercase()
        if (lower in rejectedCorrections) return true
        if (customWordsLower.containsKey(lower)) return true
        val key = learnedUnigramsLower[lower] ?: return false
        return (learnedUnigrams[key] ?: 0) >= 2
    }

    /**
     * Returns an explicit canonical casing for [word] ONLY if it comes from the user's explicit
     * custom dictionary OR has been consistently typed with that casing mid-sentence.
     * Never forces uppercase on words that were merely capitalized at sentence starts.
     */
    @Synchronized
    fun canonicalMidSentenceCasingFor(word: String, locale: String): String? {
        val lower = word.lowercase()
        customWords[word]?.let { return word }
        customWordsLower[lower]?.let { custom ->
            if (custom != word) return custom
        }
        if (LanguageVocabularies.isNeverAutoCapitalizeMidSentence(word, locale)) {
            return null
        }
        val dominant = dominantMidSentenceForm(lower)
        if (dominant != null && dominant != word) {
            return dominant
        }
        return null
    }

    private fun dominantMidSentenceForm(lower: String): String? {
        val counts = midSentenceCaseCounts[lower] ?: return null
        if (counts.isEmpty()) return null
        val lowerCount = counts[lower] ?: 0
        val bestUpper = counts.entries
            .filter { it.key != lower }
            .maxByOrNull { it.value } ?: return null
        // Require at least 2 mid-sentence uses and clear preference over lowercase.
        return if (bestUpper.value >= 2 && bestUpper.value > lowerCount * 2) {
            bestUpper.key
        } else {
            null
        }
    }

    @Synchronized
    fun lookupShortcut(token: String, locale: String): String? {
        val lower = token.trim().lowercase()
        val spec = KeyboardLocales.specFor(locale)
        val cal = Calendar.getInstance()
        return when (lower) {
            "/datum", "/date" -> SimpleDateFormat(spec.datePattern, Locale.getDefault()).format(Date())
            "/zeit", "/time" -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            "/woche", "/week" -> {
                val wk = cal.get(Calendar.WEEK_OF_YEAR)
                if (spec.baseLanguage == "de") "KW $wk" else "Week $wk"
            }
            "/monat", "/month" -> SimpleDateFormat("MMMM", Locale.forLanguageTag(spec.bcp47Tag)).format(Date())
            "/jahr", "/year" -> SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            else -> shortcuts[lower]
        }
    }

    @Synchronized
    fun rankedPrefixCandidates(
        prefix: String,
        limit: Int = 5,
        previousWord: String? = null,
        previousTwoWord: String? = null,
        atSentenceStart: Boolean = false,
        appPackage: String? = null
    ): List<String> {
        val lower = prefix.lowercase()
        if (lower.isEmpty()) return emptyList()
        val scored = linkedMapOf<String, Double>()

        val prevLower = previousWord?.trim()?.lowercase().orEmpty()
        val prev2Lower = previousTwoWord?.trim()?.lowercase().orEmpty()
        val bigramMap = if (prevLower.isNotEmpty()) learnedBigrams[prevLower] else null
        val trigramMap = if (prevLower.isNotEmpty() && prev2Lower.isNotEmpty()) {
            learnedTrigrams["$prev2Lower $prevLower"]
        } else null
        val cleanApp = appPackage?.trim()?.takeIf { it.isNotEmpty() }
        val appMap = cleanApp?.let { perAppUnigrams[it] }
        val appBigramFollowers = if (cleanApp != null && prevLower.isNotEmpty()) {
            perAppBigrams[cleanApp]?.get(prevLower)
        } else null

        fun addScore(word: String, baseScore: Double) {
            val cleanWord = if (!atSentenceStart &&
                word.firstOrNull()?.isUpperCase() == true &&
                prefix.firstOrNull()?.isLowerCase() == true &&
                word.lowercase() in LanguageVocabularies.germanNeverAutoCapMidSentence
            ) {
                word.lowercase()
            } else {
                word
            }
            if (!cleanWord.lowercase().startsWith(lower) || cleanWord == prefix) return
            val recencyBonus = ((wordRecency[cleanWord.lowercase()] ?: 0L).toDouble() / maxOf(1L, recencyClock)) * 180.0
            val biBoost = (bigramMap?.get(cleanWord) ?: bigramMap?.get(cleanWord.lowercase()) ?: 0) * 400.0
            val appBiBoost = (appBigramFollowers?.get(cleanWord) ?: appBigramFollowers?.get(cleanWord.lowercase()) ?: 0) * 600.0
            val triBoost = (trigramMap?.get(cleanWord) ?: trigramMap?.get(cleanWord.lowercase()) ?: 0) * 750.0
            val appBoost = (appMap?.get(cleanWord) ?: appMap?.get(cleanWord.lowercase()) ?: 0) * 260.0
            val total = baseScore + recencyBonus + biBoost + appBiBoost + triBoost + appBoost
            scored[cleanWord] = maxOf(scored[cleanWord] ?: 0.0, total)
        }

        customWords.forEach { (word, weight) ->
            addScore(word, 1_000.0 + weight)
        }
        learnedUnigrams.forEach { (word, count) ->
            addScore(word, 500.0 + count * 15.0)
        }
        appMap?.forEach { (word, count) ->
            addScore(word, 650.0 + count * 30.0)
        }
        appBigramFollowers?.forEach { (word, count) ->
            addScore(word, 780.0 + count * 40.0)
        }
        bigramMap?.forEach { (word, count) ->
            addScore(word, 700.0 + count * 20.0)
        }

        return scored.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    @Synchronized
    fun allKnownVocabularyWords(): Set<String> {
        val out = linkedSetOf<String>()
        out.addAll(customWords.keys)
        out.addAll(learnedUnigrams.keys)
        return out
    }

    @Synchronized
    fun wordScore(word: String): Int {
        val custom = customWords[word] ?: customWords[customWordsLower[word.lowercase()]] ?: 0
        val learned = learnedUnigrams[word] ?: learnedUnigrams[learnedUnigramsLower[word.lowercase()]] ?: 0
        val recency = ((wordRecency[word.lowercase()] ?: 0L) * 20 / maxOf(1L, recencyClock)).toInt()
        return custom * 2 + learned * 5 + recency
    }

    @Synchronized
    fun predictNextWords(
        previousWord: String,
        limit: Int = 3,
        previousTwoWord: String? = null,
        appPackage: String? = null
    ): List<String> {
        val prevLower = previousWord.trim().lowercase()
        if (prevLower.isEmpty()) return emptyList()
        val scores = linkedMapOf<String, Double>()
        val forms = linkedMapOf<String, String>()
        val cleanPkg = appPackage?.trim()?.takeIf { it.isNotEmpty() }

        fun addContext(words: Map<String, Int>?, weight: Double) {
            if (words.isNullOrEmpty()) return
            val total = words.values.sum().toDouble() + 1
            for ((word, count) in words.entries.sortedByDescending { it.value }) {
                val key = word.lowercase()
                forms.putIfAbsent(key, word)
                scores[key] = (scores[key] ?: 0.0) + weight * count / total
            }
        }

        val prev2Lower = previousTwoWord?.trim()?.lowercase().orEmpty()
        if (prev2Lower.isNotEmpty()) addContext(learnedTrigrams["$prev2Lower $prevLower"], 9.0)
        addContext(cleanPkg?.let { perAppBigrams[it]?.get(prevLower) }, 4.0)
        addContext(learnedBigrams[prevLower], 2.0)

        return scores.entries.sortedWith(
            compareByDescending<Map.Entry<String, Double>> { it.value }
                .thenByDescending { wordRecency[it.key] ?: 0L }
        ).take(limit.coerceAtLeast(0)).map { (key, _) ->
            dominantMidSentenceForm(key) ?: forms.getValue(key)
        }
    }

    @Synchronized
    fun listCustomWords(): List<Pair<String, Int>> =
        customWords.entries.sortedByDescending { it.value }.map { it.key to it.value }

    @Synchronized
    fun listTopLearnedWords(limit: Int = 40): List<Pair<String, Int>> =
        learnedUnigrams.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    @Synchronized
    fun listRejectedCorrections(): List<String> = rejectedCorrections.sorted()

    @Synchronized
    fun listShortcuts(): List<Pair<String, String>> =
        shortcuts.entries.sortedBy { it.key }.map { it.key to it.value }

    @Synchronized
    fun exportJson(): String = CustomDictionaryParser.exportToJson(
        words = customWords,
        shortcuts = shortcuts,
        learnedUnigrams = learnedUnigrams,
        entryLocales = entryLocales
    )

    @Synchronized
    fun exportGboardTsv(defaultLocaleTag: String = "de-DE"): String = CustomDictionaryParser.exportToGboardTsv(
        words = customWords,
        shortcuts = shortcuts,
        learnedUnigrams = learnedUnigrams,
        defaultLocaleTag = defaultLocaleTag,
        entryLocales = entryLocales
    )

    @Synchronized
    fun exportGboardZip(outputStream: OutputStream, defaultLocaleTag: String = "de-DE"): Int =
        CustomDictionaryParser.exportToGboardZip(
            outputStream = outputStream,
            words = customWords,
            shortcuts = shortcuts,
            learnedUnigrams = learnedUnigrams,
            defaultLocaleTag = defaultLocaleTag,
            entryLocales = entryLocales
        )

    @Synchronized
    fun clearLearnedData() {
        learnedUnigrams.clear()
        learnedUnigramsLower.clear()
        midSentenceCaseCounts.clear()
        wordRecency.clear()
        learnedBigrams.clear()
        learnedTrigrams.clear()
        learnedEmojis.clear()
        perAppUnigrams.clear()
        perAppBigrams.clear()
        perAppTones.clear()
        learnedCorrections.clear()
        voiceCorrections.clear()
        recentEmojis.clear()
        rejectedCorrections.clear()
        saveToDisk()
    }

    @Synchronized
    fun clearCustomWords() {
        customWords.clear()
        customWordsLower.clear()
        val shortcutKeys = shortcuts.keys
        entryLocales.keys.retainAll { it in shortcutKeys }
        saveToDisk()
    }

    @Synchronized
    fun stats(): DictionaryStats {
        val bigramPairs = learnedBigrams.values.sumOf { it.size }
        val trigramPairs = learnedTrigrams.values.sumOf { it.size }
        return DictionaryStats(
            customWordCount = customWords.size,
            shortcutCount = shortcuts.size,
            snippetCount = snippets.size,
            learnedWordCount = learnedUnigrams.size,
            learnedBigramCount = bigramPairs,
            learnedTrigramCount = trigramPairs,
            perAppProfileCount = perAppUnigrams.size,
            rejectedCount = rejectedCorrections.size
        )
    }

    private fun seedDefaultShortcutsIfEmpty() {
        if (shortcuts.isEmpty()) {
            shortcuts["mfg"] = "Mit freundlichen Grüßen"
            shortcuts["lg"] = "Liebe Grüße"
            shortcuts["vg"] = "Viele Grüße"
            shortcuts["br"] = "Best regards"
            shortcuts["omw"] = "On my way!"
        }
    }

    private fun seedDefaultSnippetsIfEmpty() {
        if (snippets.isEmpty()) {
            snippets += TextSnippet(
                id = "default_sig",
                trigger = "/sig",
                title = "Signatur",
                body = "Viele Grüße\n{NAME}\n{EMAIL}"
            )
            snippets += TextSnippet(
                id = "default_termin",
                trigger = "/termin",
                title = "Termin-Vorschlag",
                body = "Hallo,\nwie wäre es am {WOCHENTAG}, {DATUM} um {ZEIT} Uhr?\nViele Grüße\n{NAME}"
            )
            snippets += TextSnippet(
                id = "default_meeting_en",
                trigger = "/meet",
                title = "Meeting Reply (EN)",
                body = "Hi,\nLet's connect on {DATE} ({WEEK}) at {TIME}.\nBest,\n{NAME}"
            )
        }
    }

    private fun trimUnigramsIfNeeded() {
        if (learnedUnigrams.size <= MAX_UNIGRAMS) return
        val toRemove = learnedUnigrams.entries.sortedBy { it.value }.take(learnedUnigrams.size - MAX_UNIGRAMS).map { it.key }
        toRemove.forEach { key ->
            learnedUnigrams.remove(key)
            val lower = key.lowercase()
            learnedUnigramsLower.remove(lower)
            midSentenceCaseCounts.remove(lower)
            wordRecency.remove(lower)
        }
    }

    private fun trimSubMap(map: MutableMap<String, Int>, maxSize: Int) {
        if (map.size <= maxSize) return
        val lowest = map.entries.minByOrNull { it.value }?.key
        if (lowest != null) map.remove(lowest)
    }

    private fun <V> trimOuterMap(map: MutableMap<String, V>, maxSize: Int) {
        if (map.size <= maxSize) return
        val oldestKey = map.keys.firstOrNull()
        if (oldestKey != null) map.remove(oldestKey)
    }

    private fun loadFromDisk() {
        val file = storageFile ?: return
        if (!file.isFile) return
        runCatching {
            val root = JSONObject(file.readText())
            customWords.clear()
            customWordsLower.clear()
            root.optJSONObject("customWords")?.let { obj ->
                obj.keys().forEach { k ->
                    customWords[k] = obj.optInt(k, 10)
                    customWordsLower[k.lowercase()] = k
                }
            }

            root.optJSONObject("shortcuts")?.let { obj ->
                shortcuts.clear()
                obj.keys().forEach { k ->
                    val v = obj.optString(k, "")
                    if (v.isNotEmpty()) shortcuts[k] = v
                }
                if (shortcuts.isEmpty()) seedDefaultShortcutsIfEmpty()
            }

            learnedUnigrams.clear()
            learnedUnigramsLower.clear()
            root.optJSONObject("learnedUnigrams")?.let { obj ->
                obj.keys().forEach { k ->
                    learnedUnigrams[k] = obj.optInt(k, 1)
                    learnedUnigramsLower[k.lowercase()] = k
                }
            }

            midSentenceCaseCounts.clear()
            root.optJSONObject("midSentenceCases")?.let { obj ->
                obj.keys().forEach { lower ->
                    val sub = obj.optJSONObject(lower) ?: return@forEach
                    val map = linkedMapOf<String, Int>()
                    sub.keys().forEach { form -> map[form] = sub.optInt(form, 1) }
                    if (map.isNotEmpty()) midSentenceCaseCounts[lower] = map
                }
            }

            wordRecency.clear()
            recencyClock = root.optLong("recencyClock", 0L)
            root.optJSONObject("wordRecency")?.let { obj ->
                obj.keys().forEach { k ->
                    val v = obj.optLong(k, 0L)
                    if (v > 0L) {
                        wordRecency[k] = v
                        if (v > recencyClock) recencyClock = v
                    }
                }
            }

            learnedBigrams.clear()
            root.optJSONObject("learnedBigrams")?.let { obj ->
                obj.keys().forEach { prev ->
                    val sub = obj.optJSONObject(prev) ?: return@forEach
                    val map = linkedMapOf<String, Int>()
                    sub.keys().forEach { next -> map[next] = sub.optInt(next, 1) }
                    if (map.isNotEmpty()) learnedBigrams[prev] = map
                }
            }

            learnedTrigrams.clear()
            root.optJSONObject("learnedTrigrams")?.let { obj ->
                obj.keys().forEach { ctx ->
                    val sub = obj.optJSONObject(ctx) ?: return@forEach
                    val map = linkedMapOf<String, Int>()
                    sub.keys().forEach { next -> map[next] = sub.optInt(next, 1) }
                    if (map.isNotEmpty()) learnedTrigrams[ctx] = map
                }
            }

            learnedEmojis.clear()
            root.optJSONObject("learnedEmojis")?.let { obj ->
                obj.keys().forEach { word ->
                    val sub = obj.optJSONObject(word) ?: return@forEach
                    val map = linkedMapOf<String, Int>()
                    sub.keys().forEach { emoji -> map[emoji] = sub.optInt(emoji, 1) }
                    if (map.isNotEmpty()) learnedEmojis[word] = map
                }
            }

            perAppUnigrams.clear()
            root.optJSONObject("perAppUnigrams")?.let { obj ->
                obj.keys().forEach { pkg ->
                    val sub = obj.optJSONObject(pkg) ?: return@forEach
                    val map = linkedMapOf<String, Int>()
                    sub.keys().forEach { w -> map[w] = sub.optInt(w, 1) }
                    if (map.isNotEmpty()) perAppUnigrams[pkg] = map
                }
            }

            perAppBigrams.clear()
            root.optJSONObject("perAppBigrams")?.let { obj ->
                obj.keys().forEach { pkg ->
                    val headsObj = obj.optJSONObject(pkg) ?: return@forEach
                    val headMap = linkedMapOf<String, LinkedHashMap<String, Int>>()
                    headsObj.keys().forEach { prev ->
                        val follObj = headsObj.optJSONObject(prev) ?: return@forEach
                        val follMap = linkedMapOf<String, Int>()
                        follObj.keys().forEach { next -> follMap[next] = follObj.optInt(next, 1) }
                        if (follMap.isNotEmpty()) headMap[prev] = follMap
                    }
                    if (headMap.isNotEmpty()) perAppBigrams[pkg] = headMap
                }
            }

            learnedCorrections.clear()
            root.optJSONObject("learnedCorrections")?.let { obj ->
                obj.keys().forEach { k ->
                    val v = obj.optString(k, "").trim()
                    if (v.isNotEmpty()) learnedCorrections[k] = v
                }
            }

            voiceCorrections.clear()
            root.optJSONObject("voiceCorrections")?.let { obj ->
                obj.keys().forEach { k ->
                    val v = obj.optString(k, "").trim()
                    if (v.isNotEmpty()) voiceCorrections[k] = v
                }
            }

            recentEmojis.clear()
            root.optJSONArray("recentEmojis")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.optString(i, "").trim()
                    if (e.isNotEmpty()) recentEmojis += e
                }
            }

            perAppTones.clear()
            root.optJSONObject("perAppTones")?.let { obj ->
                obj.keys().forEach { pkg ->
                    val tone = obj.optString(pkg, "").trim()
                    if (tone.isNotEmpty()) perAppTones[pkg] = tone
                }
            }

            root.optJSONArray("snippets")?.let { arr ->
                snippets.clear()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val trig = o.optString("trigger", "").trim()
                    val body = o.optString("body", "")
                    if (trig.isNotEmpty() && body.isNotEmpty()) {
                        snippets += TextSnippet(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            trigger = trig,
                            title = o.optString("title", trig),
                            body = body
                        )
                    }
                }
                if (snippets.isEmpty()) seedDefaultSnippetsIfEmpty()
            }

            entryLocales.clear()
            root.optJSONObject("entryLocales")?.let { obj ->
                obj.keys().forEach { k ->
                    val loc = obj.optString(k, "").trim()
                    if (loc.isNotEmpty()) entryLocales[k.lowercase()] = loc
                }
            }

            rejectedCorrections.clear()
            root.optJSONArray("rejected")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "")
                    if (v.isNotEmpty()) rejectedCorrections += v
                }
            }
            lastLoadedModMs = file.lastModified()
        }
    }

    fun flush() {
        val file = storageFile ?: return
        val json: String?
        synchronized(this) {
            pendingFlush?.cancel(false)
            pendingFlush = null
            if (!dirty) return
            json = serializeStateLocked()
            dirty = false
        }
        if (json != null) {
            writeSnapshot(file, json)
        }
    }

    private fun saveToDisk() {
        val file = storageFile ?: return
        dirty = true
        if (synchronousPersistence) {
            pendingFlush?.cancel(false)
            pendingFlush = null
            val json = serializeStateLocked()
            dirty = false
            writeSnapshot(file, json)
            return
        }
        pendingFlush?.cancel(false)
        pendingFlush = ioExecutor.schedule({
            val json = synchronized(this) {
                if (!dirty) return@schedule
                dirty = false
                serializeStateLocked()
            }
            writeSnapshot(file, json)
        }, PERSIST_DEBOUNCE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun writeSnapshot(file: File, json: String) {
        if (com.tapwing.keyboard.data.AtomicFileWriter.writeTextAtomically(file, json, tempPrefix = "dict-")) {
            synchronized(this) {
                lastLoadedModMs = file.lastModified()
            }
        }
    }

    private fun serializeStateLocked(): String {
        val root = JSONObject()

        root.put("customWords", JSONObject().apply {
            customWords.forEach { (k, v) -> put(k, v) }
        })
        root.put("shortcuts", JSONObject().apply {
            shortcuts.forEach { (k, v) -> put(k, v) }
        })
        root.put("entryLocales", JSONObject().apply {
            entryLocales.forEach { (k, v) -> put(k, v) }
        })
        root.put("learnedUnigrams", JSONObject().apply {
            learnedUnigrams.forEach { (k, v) -> put(k, v) }
        })
        root.put("midSentenceCases", JSONObject().apply {
            midSentenceCaseCounts.forEach { (lower, sub) ->
                put(lower, JSONObject().apply { sub.forEach { (f, c) -> put(f, c) } })
            }
        })
        root.put("recencyClock", recencyClock)
        root.put("wordRecency", JSONObject().apply {
            wordRecency.forEach { (k, v) -> put(k, v) }
        })
        root.put("learnedBigrams", JSONObject().apply {
            learnedBigrams.forEach { (prev, sub) ->
                put(prev, JSONObject().apply { sub.forEach { (next, count) -> put(next, count) } })
            }
        })
        root.put("learnedTrigrams", JSONObject().apply {
            learnedTrigrams.forEach { (ctx, sub) ->
                put(ctx, JSONObject().apply { sub.forEach { (next, count) -> put(next, count) } })
            }
        })
        root.put("learnedEmojis", JSONObject().apply {
            learnedEmojis.forEach { (w, sub) ->
                put(w, JSONObject().apply { sub.forEach { (e, c) -> put(e, c) } })
            }
        })
        root.put("perAppUnigrams", JSONObject().apply {
            perAppUnigrams.forEach { (pkg, sub) ->
                put(pkg, JSONObject().apply { sub.forEach { (w, c) -> put(w, c) } })
            }
        })
        root.put("perAppBigrams", JSONObject().apply {
            perAppBigrams.forEach { (pkg, heads) ->
                put(pkg, JSONObject().apply {
                    heads.forEach { (prev, sub) ->
                        put(prev, JSONObject().apply { sub.forEach { (next, c) -> put(next, c) } })
                    }
                })
            }
        })
        root.put("learnedCorrections", JSONObject().apply {
            learnedCorrections.forEach { (k, v) -> put(k, v) }
        })
        root.put("voiceCorrections", JSONObject().apply {
            voiceCorrections.forEach { (k, v) -> put(k, v) }
        })
        root.put("recentEmojis", JSONArray().apply {
            recentEmojis.forEach { put(it) }
        })
        root.put("perAppTones", JSONObject().apply {
            perAppTones.forEach { (pkg, tone) -> put(pkg, tone) }
        })
        root.put("snippets", JSONArray().apply {
            snippets.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("trigger", s.trigger)
                    put("title", s.title)
                    put("body", s.body)
                })
            }
        })
        root.put("rejected", JSONArray().apply {
            rejectedCorrections.forEach { put(it) }
        })
        return root.toString()
    }

    companion object {
        private const val PERSIST_DEBOUNCE_MS = 1_500L
        private const val MAX_UNIGRAMS = 8_000
        private const val MAX_BIGRAM_HEADS = 3_000
        private const val MAX_FOLLOWERS_PER_HEAD = 30
        private const val MAX_TRIGRAM_CONTEXTS = 2_000
        private const val MAX_TRIGRAM_FOLLOWERS = 15
        private const val MAX_APP_PROFILES = 40
        private const val MAX_PER_APP_WORDS = 200
        private const val MAX_PER_APP_BIGRAMS = 250
        private const val MAX_LEARNED_CORRECTIONS = 300
        private const val MAX_VOICE_CORRECTIONS = 300
        private const val MAX_RECENT_EMOJIS = 28

        private val ioExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "tapwing-dict-io").apply { isDaemon = true }
        }
        private val stores = mutableMapOf<String, AdaptiveDictionaryStore>()

        @Synchronized
        fun createForContext(context: Context): AdaptiveDictionaryStore {
            val safeCtx = ContextCompat.createDeviceProtectedStorageContext(context.applicationContext)
                ?: context.applicationContext
            val file = File(safeCtx.filesDir, "tapwing_dictionary_state.json")
            return stores.getOrPut(file.absolutePath) {
                AdaptiveDictionaryStore(file, synchronousPersistence = false)
            }
        }
    }
}
