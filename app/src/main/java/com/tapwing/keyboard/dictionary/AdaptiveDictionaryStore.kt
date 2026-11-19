package com.tapwing.keyboard.dictionary

import android.content.Context
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DictionaryStats(
    val customWordCount: Int,
    val shortcutCount: Int,
    val learnedWordCount: Int,
    val learnedBigramCount: Int,
    val rejectedCount: Int
)

data class ImportSummary(
    val addedWords: Int,
    val addedShortcuts: Int,
    val sourceFormat: String = "text"
)

/**
 * Persistent custom dictionary & adaptive typing-learning store.
 * Stores user-imported dictionaries, text shortcuts, unigram/bigram frequencies, and
 * rejected autocorrections inside device-protected storage so IME & Settings stay in sync.
 */
class AdaptiveDictionaryStore(private val storageFile: File?) {

    private val customWords = linkedMapOf<String, Int>()
    private val customWordsLower = linkedMapOf<String, String>()
    private val shortcuts = linkedMapOf<String, String>()
    private val entryLocales = linkedMapOf<String, String>()
    private val learnedUnigrams = linkedMapOf<String, Int>()
    private val learnedUnigramsLower = linkedMapOf<String, String>()
    private val learnedBigrams = linkedMapOf<String, LinkedHashMap<String, Int>>()
    private val rejectedCorrections = linkedSetOf<String>()

    private var lastLoadedModMs: Long = -1L

    init {
        seedDefaultShortcutsIfEmpty()
        loadFromDisk()
    }

    @Synchronized
    fun reloadIfModifiedOnDisk() {
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
        incognito: Boolean
    ) {
        if (!learningEnabled || incognito) return
        val clean = word.trim()
        if (clean.length < 2 || clean.any { it.isDigit() }) return

        val currentCount = learnedUnigrams[clean] ?: 0
        learnedUnigrams[clean] = (currentCount + 1).coerceAtMost(10_000)
        learnedUnigramsLower[clean.lowercase()] = clean

        trimMapToMax(learnedUnigrams, MAX_UNIGRAMS)

        val prev = previousWord?.trim()?.lowercase().orEmpty()
        if (prev.length >= 2) {
            val nextMap = learnedBigrams.getOrPut(prev) { linkedMapOf() }
            nextMap[clean] = ((nextMap[clean] ?: 0) + 1).coerceAtMost(5_000)
            if (nextMap.size > 25) {
                val lowest = nextMap.entries.minByOrNull { it.value }?.key
                if (lowest != null) nextMap.remove(lowest)
            }
            if (learnedBigrams.size > MAX_BIGRAM_HEADS) {
                val oldestKey = learnedBigrams.keys.firstOrNull()
                if (oldestKey != null) learnedBigrams.remove(oldestKey)
            }
        }
        saveToDisk()
    }

    @Synchronized
    fun recordCorrectionRejected(originalWord: String, learningEnabled: Boolean, incognito: Boolean) {
        if (!learningEnabled || incognito) return
        val clean = originalWord.trim()
        if (clean.length < 2) return
        rejectedCorrections += clean.lowercase()
        // Also learn the user's intended word immediately so Tapwing respects it next time.
        customWordsLower.putIfAbsent(clean.lowercase(), clean)
        learnedUnigrams[clean] = maxOf(learnedUnigrams[clean] ?: 0, 3)
        learnedUnigramsLower[clean.lowercase()] = clean
        saveToDisk()
    }

    @Synchronized
    fun isKnownWord(word: String): Boolean {
        val lower = word.lowercase()
        if (lower in rejectedCorrections) return true
        if (customWordsLower.containsKey(lower)) return true
        return (learnedUnigrams[learnedUnigramsLower[lower]] ?: 0) >= 2
    }

    @Synchronized
    fun lookupShortcut(token: String, locale: String): String? {
        val lower = token.trim().lowercase()
        if (lower == "/datum" || lower == "/date") {
            val pattern = if (locale.startsWith("de")) "dd.MM.yyyy" else "yyyy-MM-dd"
            return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
        }
        if (lower == "/zeit" || lower == "/time") {
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
        return shortcuts[lower]
    }

    @Synchronized
    fun rankedPrefixCandidates(prefix: String, limit: Int = 5): List<String> {
        val lower = prefix.lowercase()
        if (lower.isEmpty()) return emptyList()
        val scored = linkedMapOf<String, Int>()

        customWords.forEach { (word, weight) ->
            if (word.lowercase().startsWith(lower) && word != prefix) {
                scored[word] = maxOf(scored[word] ?: 0, 1_000 + weight)
            }
        }
        learnedUnigrams.forEach { (word, count) ->
            if (word.lowercase().startsWith(lower) && word != prefix) {
                scored[word] = maxOf(scored[word] ?: 0, 500 + count * 10)
            }
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
    fun predictNextWords(previousWord: String, limit: Int = 3): List<String> {
        val prevLower = previousWord.trim().lowercase()
        if (prevLower.isEmpty()) return emptyList()
        val map = learnedBigrams[prevLower] ?: return emptyList()
        return map.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    @Synchronized
    fun listCustomWords(): List<Pair<String, Int>> =
        customWords.entries.sortedByDescending { it.value }.map { it.key to it.value }

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
        learnedBigrams.clear()
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
        return DictionaryStats(
            customWordCount = customWords.size,
            shortcutCount = shortcuts.size,
            learnedWordCount = learnedUnigrams.size,
            learnedBigramCount = bigramPairs,
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

    private fun trimMapToMax(map: MutableMap<String, Int>, maxSize: Int) {
        if (map.size <= maxSize) return
        val toRemove = map.entries.sortedBy { it.value }.take(map.size - maxSize).map { it.key }
        toRemove.forEach { key ->
            map.remove(key)
            learnedUnigramsLower.remove(key.lowercase())
        }
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

            learnedBigrams.clear()
            root.optJSONObject("learnedBigrams")?.let { obj ->
                obj.keys().forEach { prev ->
                    val sub = obj.optJSONObject(prev) ?: return@forEach
                    val map = linkedMapOf<String, Int>()
                    sub.keys().forEach { next ->
                        map[next] = sub.optInt(next, 1)
                    }
                    if (map.isNotEmpty()) learnedBigrams[prev] = map
                }
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

    private fun saveToDisk() {
        val file = storageFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
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
            root.put("learnedBigrams", JSONObject().apply {
                learnedBigrams.forEach { (prev, sub) ->
                    put(prev, JSONObject().apply {
                        sub.forEach { (next, count) -> put(next, count) }
                    })
                }
            })
            root.put("rejected", JSONArray().apply {
                rejectedCorrections.forEach { put(it) }
            })
            file.writeText(root.toString())
            lastLoadedModMs = file.lastModified()
        }
    }

    companion object {
        private const val MAX_UNIGRAMS = 2_500
        private const val MAX_BIGRAM_HEADS = 1_200

        fun createForContext(context: Context): AdaptiveDictionaryStore {
            val safeCtx = ContextCompat.createDeviceProtectedStorageContext(context.applicationContext)
                ?: context.applicationContext
            val file = File(safeCtx.filesDir, "tapwing_dictionary_state.json")
            return AdaptiveDictionaryStore(file)
        }
    }
}
