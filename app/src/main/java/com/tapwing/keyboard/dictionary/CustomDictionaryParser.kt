package com.tapwing.keyboard.dictionary

import org.json.JSONObject

data class ParsedDictionaryImport(
    val words: Map<String, Int>,
    val shortcuts: Map<String, String>
)

/**
 * Parses user-supplied dictionary files in plain text (.txt), CSV/TSV (including Gboard
 * Personal Dictionary TSV exports), shortcut arrow files ("mfg => Mit freundlichen Grüßen"),
 * or Tapwing JSON backups.
 */
object CustomDictionaryParser {

    fun parse(rawContent: String): ParsedDictionaryImport {
        val trimmed = rawContent.trim('\r', '\n', ' ')
        if (trimmed.isBlank()) {
            return ParsedDictionaryImport(emptyMap(), emptyMap())
        }
        if (trimmed.trimStart().startsWith("{")) {
            return parseJson(trimmed.trim())
        }

        val words = linkedMapOf<String, Int>()
        val shortcuts = linkedMapOf<String, String>()

        trimmed.lineSequence().forEach { rawLine ->
            val rawNoCr = rawLine.trimEnd('\r', ' ')
            val line = rawNoCr.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            // Explicit arrow shortcut: "mfg => Mit freundlichen Grüßen" or "addr -> Musterstraße 1"
            val arrowMatch = Regex("""^(\S+)\s*(?:=>|->)\s*(.+)$""").find(line)
            if (arrowMatch != null) {
                val trigger = arrowMatch.groupValues[1].trim()
                val expansion = arrowMatch.groupValues[2].trim()
                if (trigger.isNotEmpty() && expansion.isNotEmpty()) {
                    shortcuts[trigger] = expansion
                }
                return@forEach
            }

            // Tab-separated (Gboard Personal Dictionary format: "<shortcut>\t<word_or_phrase>\t<locale>")
            if ('\t' in rawNoCr) {
                val cols = rawNoCr.split('\t').map { it.trim() }
                if (cols.size >= 2) {
                    val col0 = cols[0]
                    val col1 = cols[1]
                    val col1AsWeight = col1.toIntOrNull()
                    when {
                        col0.isEmpty() && col1.isNotEmpty() -> {
                            words[col1] = words.getOrDefault(col1, 0).coerceAtLeast(10)
                        }
                        col1AsWeight != null && col0.isNotEmpty() -> {
                            words[col0] = col1AsWeight.coerceIn(1, 10_000)
                        }
                        col0.isNotEmpty() && col1.isEmpty() -> {
                            words[col0] = words.getOrDefault(col0, 0).coerceAtLeast(10)
                        }
                        col0.isNotEmpty() && col1.isNotEmpty() -> {
                            shortcuts[col0] = col1
                            if (!col1.contains(' ')) {
                                words[col1] = words.getOrDefault(col1, 0).coerceAtLeast(10)
                            }
                        }
                    }
                }
                return@forEach
            }

            // Comma or semicolon separated: "Fachwort,25" or "mfg;Mit freundlichen Grüßen"
            val sep = when {
                ';' in line -> ';'
                ',' in line -> ','
                else -> null
            }
            if (sep != null) {
                val parts = line.split(sep, limit = 2).map { it.trim() }
                val first = parts.getOrNull(0).orEmpty()
                val second = parts.getOrNull(1).orEmpty()
                val freq = second.toIntOrNull()
                if (first.isNotEmpty() && freq != null) {
                    words[first] = freq.coerceIn(1, 10_000)
                } else if (first.isNotEmpty() && second.isNotEmpty()) {
                    shortcuts[first] = second
                } else if (first.isNotEmpty()) {
                    words[first] = 10
                }
                return@forEach
            }

            // Single word or space-separated word tokens on a plain text line
            line.split(Regex("""\s+"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { token ->
                    words[token] = maxOf(words.getOrDefault(token, 0), 10)
                }
        }

        return ParsedDictionaryImport(words = words, shortcuts = shortcuts)
    }

    private fun parseJson(raw: String): ParsedDictionaryImport {
        val words = linkedMapOf<String, Int>()
        val shortcuts = linkedMapOf<String, String>()
        runCatching {
            val root = JSONObject(raw)
            root.optJSONObject("words")?.let { obj ->
                obj.keys().forEach { key ->
                    val weight = obj.optInt(key, 10).coerceIn(1, 10_000)
                    if (key.isNotBlank()) words[key.trim()] = weight
                }
            }
            root.optJSONObject("shortcuts")?.let { obj ->
                obj.keys().forEach { key ->
                    val value = obj.optString(key, "").trim()
                    if (key.isNotBlank() && value.isNotEmpty()) shortcuts[key.trim()] = value
                }
            }
            root.optJSONObject("learnedUnigrams")?.let { obj ->
                obj.keys().forEach { key ->
                    val weight = obj.optInt(key, 1).coerceIn(1, 10_000)
                    if (key.isNotBlank()) {
                        words[key.trim()] = maxOf(words.getOrDefault(key.trim(), 0), weight)
                    }
                }
            }
        }
        return ParsedDictionaryImport(words = words, shortcuts = shortcuts)
    }

    fun exportToJson(
        words: Map<String, Int>,
        shortcuts: Map<String, String>,
        learnedUnigrams: Map<String, Int> = emptyMap()
    ): String {
        val root = JSONObject()
        val wordsObj = JSONObject()
        words.forEach { (w, weight) -> wordsObj.put(w, weight) }
        val shortcutsObj = JSONObject()
        shortcuts.forEach { (k, v) -> shortcutsObj.put(k, v) }
        val learnedObj = JSONObject()
        learnedUnigrams.forEach { (w, c) -> learnedObj.put(w, c) }
        root.put("words", wordsObj)
        root.put("shortcuts", shortcutsObj)
        root.put("learnedUnigrams", learnedObj)
        return root.toString(2)
    }
}
