package com.tapwing.keyboard.dictionary

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ParsedDictionaryImport(
    val words: Map<String, Int>,
    val shortcuts: Map<String, String>,
    val locales: Map<String, String> = emptyMap(),
    val sourceFormat: String = "text",
    val processedEntries: Int = 1
)

/**
 * Parses user-supplied dictionary files in Gboard/PixelBoard `.zip` archives (`dictionary.txt`
 * with `# Gboard Dictionary version:1`), plain text (.txt), CSV/TSV, shortcut arrow files
 * ("mfg => Mit freundlichen Grüßen"), or Tapwing JSON backups, and exports Gboard/PixelBoard `.zip`
 * and JSON backups.
 */
object CustomDictionaryParser {

    private val allowedZipExtensions = setOf("txt", "tsv", "csv", "json", "dict")
    private val ARROW_SHORTCUT_REGEX = Regex("""^(\S+)\s*(?:=>|->)\s*(.+)$""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private const val MAX_ZIP_ENTRIES = 256

    fun isZipMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            ((bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) ||
                (bytes[2] == 0x05.toByte() && bytes[3] == 0x06.toByte()))
    }

    fun parseStream(
        inputStream: InputStream,
        maxCompressedBytes: Int = 15 * 1024 * 1024
    ): ParsedDictionaryImport {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = inputStream.read(buf)
            if (read <= 0) break
            total += read
            if (total > maxCompressedBytes) {
                throw IllegalArgumentException(
                    "Dictionary file exceeds ${maxCompressedBytes / (1024 * 1024)} MB limit"
                )
            }
            out.write(buf, 0, read)
        }
        return parseBytes(out.toByteArray())
    }

    fun parseBytes(bytes: ByteArray): ParsedDictionaryImport {
        if (isZipMagic(bytes)) {
            return parseZipBytes(bytes)
        }
        return parse(bytes.toString(Charsets.UTF_8))
    }

    private fun parseZipBytes(
        bytes: ByteArray,
        maxUncompressedBytes: Int = 20 * 1024 * 1024,
        maxEntries: Int = MAX_ZIP_ENTRIES
    ): ParsedDictionaryImport {
        val mergedWords = linkedMapOf<String, Int>()
        val mergedShortcuts = linkedMapOf<String, String>()
        val mergedLocales = linkedMapOf<String, String>()
        var processedEntries = 0
        var seenEntries = 0
        var totalUncompressed = 0
        val buf = ByteArray(8192)

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                seenEntries++
                if (seenEntries > maxEntries) {
                    throw IllegalArgumentException("ZIP archive exceeds $maxEntries entries limit")
                }
                if (entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val normalizedName = entry.name.replace('\\', '/')
                if (normalizedName.split('/').any { it == ".." }) {
                    throw SecurityException("Blocked unsafe ZIP entry path: ${entry.name}")
                }
                val baseName = normalizedName.substringAfterLast('/')
                val isMacJunk = normalizedName.startsWith("__MACOSX/") ||
                    normalizedName.contains("/__MACOSX/") ||
                    baseName.startsWith("._") ||
                    baseName == ".DS_Store"

                val ext = baseName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
                val supported = !isMacJunk &&
                    baseName.isNotBlank() &&
                    (!baseName.contains('.') || ext in allowedZipExtensions)

                if (supported) {
                    val entryOut = ByteArrayOutputStream()
                    while (true) {
                        val read = zis.read(buf)
                        if (read <= 0) break
                        totalUncompressed += read
                        if (totalUncompressed > maxUncompressedBytes) {
                            throw IllegalArgumentException(
                                "ZIP archive uncompressed content exceeds ${maxUncompressedBytes / (1024 * 1024)} MB limit"
                            )
                        }
                        entryOut.write(buf, 0, read)
                    }
                    val parsed = parse(entryOut.toByteArray().toString(Charsets.UTF_8))
                    parsed.words.forEach { (w, weight) ->
                        mergedWords[w] = maxOf(mergedWords.getOrDefault(w, 0), weight)
                    }
                    parsed.shortcuts.forEach { (k, v) ->
                        mergedShortcuts[k] = v
                    }
                    parsed.locales.forEach { (k, loc) ->
                        mergedLocales[k] = loc
                    }
                    processedEntries++
                }
                zis.closeEntry()
            }
        }

        return ParsedDictionaryImport(
            words = mergedWords,
            shortcuts = mergedShortcuts,
            locales = mergedLocales,
            sourceFormat = "gboard_zip",
            processedEntries = processedEntries
        )
    }

    fun parse(rawContent: String): ParsedDictionaryImport {
        // Strip UTF-8 BOM first without touching leading '\t' (significant in Gboard dictionary lines)
        val noBom = rawContent.removePrefix("\uFEFF")
        val trimmed = noBom.trim('\r', '\n', ' ')
        if (trimmed.isBlank()) {
            return ParsedDictionaryImport(emptyMap(), emptyMap())
        }
        if (trimmed.trimStart().startsWith("{")) {
            return parseJson(trimmed.trim())
        }

        val words = linkedMapOf<String, Int>()
        val shortcuts = linkedMapOf<String, String>()
        val locales = linkedMapOf<String, String>()
        var hasGboardHeader = false

        trimmed.lineSequence().forEach { rawLine ->
            val rawNoCr = rawLine.trimEnd('\r', ' ')
            val line = rawNoCr.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#")) {
                if (line.startsWith("# Gboard Dictionary", ignoreCase = true)) {
                    hasGboardHeader = true
                }
                return@forEach
            }

            // Explicit arrow shortcut: "mfg => Mit freundlichen Grüßen" or "addr -> Musterstraße 1"
            val arrowMatch = ARROW_SHORTCUT_REGEX.find(line)
            if (arrowMatch != null) {
                val trigger = arrowMatch.groupValues[1].trim()
                val expansion = arrowMatch.groupValues[2].trim()
                if (trigger.isNotEmpty() && expansion.isNotEmpty()) {
                    shortcuts[trigger] = expansion
                }
                return@forEach
            }

            // Tab-separated (Gboard/PixelBoard format: "<shortcut>\t<word_or_phrase>\t<locale>[\t<pos>]")
            if ('\t' in rawNoCr) {
                val cols = rawNoCr.split('\t').map { it.trim() }
                if (cols.size >= 2) {
                    val col0 = cols[0]
                    val col1 = cols[1]
                    val col2 = cols.getOrNull(2).orEmpty()
                    val col1AsWeight = col1.toIntOrNull()
                    when {
                        col0.isEmpty() && col1.isNotEmpty() -> {
                            words[col1] = words.getOrDefault(col1, 0).coerceAtLeast(10)
                            if (col2.isNotEmpty()) locales[col1.lowercase()] = col2
                        }
                        col1AsWeight != null && col0.isNotEmpty() -> {
                            words[col0] = col1AsWeight.coerceIn(1, 10_000)
                            if (col2.isNotEmpty()) locales[col0.lowercase()] = col2
                        }
                        col0.isNotEmpty() && col1.isEmpty() -> {
                            words[col0] = words.getOrDefault(col0, 0).coerceAtLeast(10)
                            if (col2.isNotEmpty()) locales[col0.lowercase()] = col2
                        }
                        col0.isNotEmpty() && col1.isNotEmpty() -> {
                            shortcuts[col0] = col1
                            if (col2.isNotEmpty()) locales[col0.lowercase()] = col2
                            if (!col1.contains(' ')) {
                                words[col1] = words.getOrDefault(col1, 0).coerceAtLeast(10)
                                if (col2.isNotEmpty()) locales[col1.lowercase()] = col2
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
            line.split(WHITESPACE_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { token ->
                    words[token] = maxOf(words.getOrDefault(token, 0), 10)
                }
        }

        return ParsedDictionaryImport(
            words = words,
            shortcuts = shortcuts,
            locales = locales,
            sourceFormat = if (hasGboardHeader) "gboard_tsv" else "text",
            processedEntries = 1
        )
    }

    private fun parseJson(raw: String): ParsedDictionaryImport {
        val words = linkedMapOf<String, Int>()
        val shortcuts = linkedMapOf<String, String>()
        val locales = linkedMapOf<String, String>()
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
            root.optJSONObject("entryLocales")?.let { obj ->
                obj.keys().forEach { key ->
                    val loc = obj.optString(key, "").trim()
                    if (key.isNotBlank() && loc.isNotEmpty()) locales[key.trim().lowercase()] = loc
                }
            }
        }
        return ParsedDictionaryImport(
            words = words,
            shortcuts = shortcuts,
            locales = locales,
            sourceFormat = "json",
            processedEntries = 1
        )
    }

    fun exportToGboardTsv(
        words: Map<String, Int>,
        shortcuts: Map<String, String>,
        learnedUnigrams: Map<String, Int> = emptyMap(),
        defaultLocaleTag: String = "de-DE",
        entryLocales: Map<String, String> = emptyMap()
    ): String {
        fun sanitizeField(s: String): String =
            s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim()

        val sb = StringBuilder()
        sb.append("# Gboard Dictionary version:1\n")

        val emittedShortcuts = linkedSetOf<String>()
        shortcuts.entries
            .sortedBy { it.key.lowercase() }
            .forEach { (trigger, phrase) ->
                val cleanTrigger = sanitizeField(trigger)
                val cleanPhrase = sanitizeField(phrase)
                if (cleanTrigger.isNotEmpty() &&
                    cleanPhrase.isNotEmpty() &&
                    emittedShortcuts.add(cleanTrigger.lowercase())
                ) {
                    val loc = sanitizeField(
                        entryLocales[cleanTrigger.lowercase()] ?: defaultLocaleTag
                    ).ifEmpty { defaultLocaleTag }
                    sb.append(cleanTrigger).append('\t').append(cleanPhrase).append('\t').append(loc).append('\n')
                }
            }

        val emittedWordsLower = linkedSetOf<String>()
        words.keys
            .sortedBy { it.lowercase() }
            .forEach { word ->
                val cleanWord = sanitizeField(word)
                if (cleanWord.isNotEmpty() && emittedWordsLower.add(cleanWord.lowercase())) {
                    val loc = sanitizeField(
                        entryLocales[cleanWord.lowercase()] ?: defaultLocaleTag
                    ).ifEmpty { defaultLocaleTag }
                    sb.append('\t').append(cleanWord).append('\t').append(loc).append('\n')
                }
            }

        learnedUnigrams.entries
            .filter { it.value >= 2 }
            .sortedBy { it.key.lowercase() }
            .forEach { (word, _) ->
                val cleanWord = sanitizeField(word)
                if (cleanWord.isNotEmpty() && emittedWordsLower.add(cleanWord.lowercase())) {
                    val loc = sanitizeField(
                        entryLocales[cleanWord.lowercase()] ?: defaultLocaleTag
                    ).ifEmpty { defaultLocaleTag }
                    sb.append('\t').append(cleanWord).append('\t').append(loc).append('\n')
                }
            }

        return sb.toString()
    }

    fun exportToGboardZip(
        outputStream: OutputStream,
        words: Map<String, Int>,
        shortcuts: Map<String, String>,
        learnedUnigrams: Map<String, Int> = emptyMap(),
        defaultLocaleTag: String = "de-DE",
        entryLocales: Map<String, String> = emptyMap()
    ): Int {
        val tsv = exportToGboardTsv(
            words = words,
            shortcuts = shortcuts,
            learnedUnigrams = learnedUnigrams,
            defaultLocaleTag = defaultLocaleTag,
            entryLocales = entryLocales
        )
        val zipOut = ZipOutputStream(outputStream)
        zipOut.putNextEntry(ZipEntry("dictionary.txt"))
        zipOut.write(tsv.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
        zipOut.finish()
        zipOut.flush()
        return tsv.lineSequence().count { it.isNotBlank() && !it.startsWith("#") }
    }

    fun exportToJson(
        words: Map<String, Int>,
        shortcuts: Map<String, String>,
        learnedUnigrams: Map<String, Int> = emptyMap(),
        entryLocales: Map<String, String> = emptyMap()
    ): String {
        val root = JSONObject()
        val wordsObj = JSONObject()
        words.forEach { (w, weight) -> wordsObj.put(w, weight) }
        val shortcutsObj = JSONObject()
        shortcuts.forEach { (k, v) -> shortcutsObj.put(k, v) }
        val learnedObj = JSONObject()
        learnedUnigrams.forEach { (w, c) -> learnedObj.put(w, c) }
        val localesObj = JSONObject()
        entryLocales.forEach { (k, v) -> localesObj.put(k, v) }
        root.put("words", wordsObj)
        root.put("shortcuts", shortcutsObj)
        root.put("learnedUnigrams", learnedObj)
        root.put("entryLocales", localesObj)
        return root.toString(2)
    }
}
