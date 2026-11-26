package com.tapwing.keyboard.dictionary

import android.content.Context
import com.tapwing.keyboard.data.KeyboardLocales
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

import com.tapwing.keyboard.ai.DownloadIntegrityVerifier

data class OfflinePackSpec(
    val langCode: String,
    val displayName: String,
    val approxSizeKb: Int,
    val downloadUrl: String,
    val exactSizeBytes: Long = 0L,
    val sha256: String = "",
    val licenseName: String = "MIT / CC-BY-SA-4.0",
    val licenseUrl: String = "https://github.com/hermitdave/FrequencyWords"
)

sealed interface PackInstallState {
    data object NotInstalled : PackInstallState
    data class Downloading(val progress: Float) : PackInstallState
    data class Installed(val wordCount: Int, val sizeBytes: Long) : PackInstallState
    data class Error(val message: String) : PackInstallState
}

class LoadedDictionaryPack(
    val langCode: String,
    val wordFreq: Map<String, Int>,
    val wordsByPrefix2: Map<String, List<Pair<String, Int>>>,
    val topSwipePool: List<String>,
    val symSpell1Deletes: Map<String, List<String>> = emptyMap(),
    val foldedPrefix2: Map<String, List<String>> = emptyMap(),
    val swipeBuckets: Map<Int, List<String>> = emptyMap()
) {
    fun contains(word: String): Boolean = wordFreq.containsKey(word.lowercase())

    fun prefixMatches(prefix: String, limit: Int = 6): List<String> {
        val lower = prefix.lowercase()
        if (lower.isEmpty()) return emptyList()
        if (lower.length == 1) {
            return topSwipePool
                .asSequence()
                .filter { it.startsWith(lower) && it != lower }
                .take(limit)
                .toList()
        }
        val bucket = wordsByPrefix2[lower.take(2)] ?: return emptyList()
        val out = ArrayList<String>(limit)
        for ((w, _) in bucket) {
            if (w.length > lower.length && w.startsWith(lower)) {
                out += w
                if (out.size >= limit) break
            }
        }
        return out
    }

    fun foldedPrefixMatches(foldedPrefix: String, limit: Int = 4): List<String> {
        if (foldedPrefix.length < 2 || foldedPrefix2.isEmpty()) return emptyList()
        val bucket = foldedPrefix2[foldedPrefix.substring(0, 2)] ?: return emptyList()
        val isGerman = langCode == "de"
        val out = ArrayList<String>(limit)
        for (w in bucket) {
            if (LanguageVocabularies.foldDiacritics(w, isGerman).startsWith(foldedPrefix)) {
                out += w
                if (out.size >= limit) break
            }
        }
        return out
    }

    fun symSpellCandidates(lowerQuery: String, limit: Int = 48): List<String> {
        if (symSpell1Deletes.isEmpty() || lowerQuery.length < 3) return emptyList()
        val out = LinkedHashSet<String>(limit)
        symSpell1Deletes[lowerQuery]?.forEach { if (out.size < limit) out += it }
        val sb = StringBuilder(lowerQuery.length - 1)
        for (i in lowerQuery.indices) {
            if (out.size >= limit) break
            sb.setLength(0)
            sb.append(lowerQuery, 0, i)
            sb.append(lowerQuery, i + 1, lowerQuery.length)
            val del = sb.toString()
            if (wordFreq.containsKey(del) && out.size < limit) out += del
            symSpell1Deletes[del]?.forEach { if (out.size < limit) out += it }
        }
        return out.toList()
    }
}

/**
 * Downloads, caches, and indexes 50,000-word offline frequency dictionary packs per language
 * (powered by MIT/CC-BY-SA FrequencyWords lists) for instant spellchecking, completions & swipe typing.
 */
class OfflineDictionaryPackManager(context: Context) {

    private val appContext = context.applicationContext
    private val packsDir: File = File(appContext.noBackupFilesDir ?: appContext.filesDir, "tapwing_dictionaries")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val loadedCache = ConcurrentHashMap<String, LoadedDictionaryPack>()

    private val _states = MutableStateFlow<Map<String, PackInstallState>>(emptyMap())
    val states: StateFlow<Map<String, PackInstallState>> = _states.asStateFlow()

    init {
        refreshStatesAsync()
    }

    fun refreshStatesAsync() {
        scope.launch { refreshStates() }
    }

    fun close() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        loadedCache.clear()
        scope.coroutineContext[Job]?.cancel()
    }

    fun clearMemoryCache() {
        loadedCache.clear()
    }

    fun refreshStates() {
        _states.update { currentMap ->
            val next = LinkedHashMap<String, PackInstallState>()
            availablePacks.forEach { spec ->
                val current = currentMap[spec.langCode]
                if (current is PackInstallState.Downloading) {
                    next[spec.langCode] = current
                } else {
                    val file = packFileFor(spec.langCode)
                    if (file.isFile && file.length() > 10_000L) {
                        val count = loadedCache[spec.langCode]?.wordFreq?.size ?: estimateLines(file)
                        next[spec.langCode] = PackInstallState.Installed(count, file.length())
                    } else {
                        next[spec.langCode] = PackInstallState.NotInstalled
                    }
                }
            }
            next
        }
    }

    fun packFileFor(langCode: String): File {
        val base = KeyboardLocales.baseLang(langCode)
        if (!packsDir.exists()) packsDir.mkdirs()
        return File(packsDir, "${base}_50k.txt")
    }

    /**
     * Non-blocking cache lookup for hot UI-thread typing paths. Triggers async preload if the pack
     * is installed on disk but not yet parsed in memory.
     */
    fun getCachedPack(locale: String): LoadedDictionaryPack? {
        val base = KeyboardLocales.baseLang(locale)
        loadedCache[base]?.let { return it }
        preloadLocaleAsync(base)
        return null
    }

    @Synchronized
    fun getLoadedPack(locale: String): LoadedDictionaryPack? {
        val base = KeyboardLocales.baseLang(locale)
        loadedCache[base]?.let { return it }
        val file = packFileFor(base)
        if (!file.isFile || file.length() <= 10_000L) return null
        val loaded = runCatching {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                parseFrequencyWordList(base, lines)
            }
        }.getOrNull() ?: return null
        loadedCache[base] = loaded
        return loaded
    }

    fun preloadLocaleAsync(locale: String) {
        val base = KeyboardLocales.baseLang(locale)
        if (loadedCache.containsKey(base)) return
        val file = packFileFor(base)
        if (file.isFile && file.length() > 10_000L) {
            scope.launch {
                getLoadedPack(base)
            }
        }
    }

    fun startDownload(spec: OfflinePackSpec) {
        if (activeJobs[spec.langCode]?.isActive == true) return
        val job = scope.launch {
            val target = packFileFor(spec.langCode)
            val part = File(target.parentFile, "${target.name}.part")
            updateState(spec.langCode, PackInstallState.Downloading(0.05f))
            try {
                val digest = downloadToFile(spec.downloadUrl, part) { progress ->
                    updateState(spec.langCode, PackInstallState.Downloading(progress))
                }
                val integrity = DownloadIntegrityVerifier.verify(
                    file = part,
                    expectedSizeBytes = spec.exactSizeBytes,
                    expectedSha256 = spec.sha256,
                    precomputedSha256 = digest
                )
                if (integrity.isFailure || !part.isFile || part.length() <= 10_000L) {
                    part.delete()
                    val msg = integrity.exceptionOrNull()?.localizedMessage ?: "Dictionary integrity check failed"
                    updateState(spec.langCode, PackInstallState.Error(msg))
                    return@launch
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                synchronized(this@OfflineDictionaryPackManager) {
                    loadedCache.remove(spec.langCode)
                }
                val loaded = getLoadedPack(spec.langCode)
                val count = loaded?.wordFreq?.size ?: estimateLines(target)
                updateState(spec.langCode, PackInstallState.Installed(count, target.length()))
            } catch (ce: CancellationException) {
                part.delete()
                updateState(spec.langCode, PackInstallState.NotInstalled)
                throw ce
            } catch (ex: Exception) {
                part.delete()
                updateState(spec.langCode, PackInstallState.Error(ex.localizedMessage ?: "Download failed"))
            } finally {
                activeJobs.remove(spec.langCode)
            }
        }
        activeJobs[spec.langCode] = job
    }

    fun cancelDownload(langCode: String) {
        activeJobs.remove(langCode)?.cancel()
        File(packFileFor(langCode).parentFile, "${packFileFor(langCode).name}.part").delete()
        refreshStates()
    }

    fun deletePack(langCode: String) {
        activeJobs.remove(langCode)?.cancel()
        val f = packFileFor(langCode)
        if (f.exists()) f.delete()
        synchronized(this) {
            loadedCache.remove(KeyboardLocales.baseLang(langCode))
        }
        refreshStates()
    }

    private suspend fun downloadToFile(urlStr: String, dest: File, onProgress: (Float) -> Unit): String {
        var currentUrl = urlStr
        var redirects = 0
        var conn: HttpURLConnection
        while (true) {
            val parsed = URL(currentUrl)
            if (!parsed.protocol.equals("https", ignoreCase = true) || parsed.host.isBlank()) {
                throw SecurityException("Blocked non-HTTPS dictionary download URL")
            }
            conn = (parsed.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "TapwingKeyboard/2.9 (Android)")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: error("Redirect without Location")
                conn.disconnect()
                val nextUrl = URL(parsed, loc).toString()
                if (!URL(nextUrl).protocol.equals("https", ignoreCase = true)) {
                    throw SecurityException("Blocked non-HTTPS redirect target")
                }
                currentUrl = nextUrl
                redirects++
                if (redirects > 5) error("Too many redirects")
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                error("HTTP $code")
            }
            break
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        try {
            val total = conn.contentLengthLong.takeIf { it > 0L } ?: 650_000L
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(16 * 1024)
                    var readTotal = 0L
                    while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        readTotal += n
                        if (readTotal > MAX_PACK_BYTES) {
                            throw SecurityException("Dictionary pack exceeds ${MAX_PACK_BYTES / (1024 * 1024)} MB limit")
                        }
                        digest.update(buf, 0, n)
                        output.write(buf, 0, n)
                        onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0.05f, 0.99f))
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    output.flush()
                }
            }
        } finally {
            conn.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateState(langCode: String, state: PackInstallState) {
        _states.update { current ->
            val next = LinkedHashMap(current)
            next[langCode] = state
            next
        }
    }

    private fun estimateLines(file: File): Int {
        return runCatching {
            var count = 0
            file.bufferedReader(Charsets.UTF_8).use { br ->
                while (br.readLine() != null) count++
            }
            count
        }.getOrDefault(0)
    }

    companion object {
        private const val MAX_PACK_BYTES = 10L * 1024L * 1024L
        private const val FREQUENCY_WORDS_COMMIT = "525f9b560de45753a5ea01069454e72e9aa541c6"
        val availablePacks: List<OfflinePackSpec> = listOf(
            OfflinePackSpec(
                langCode = "de",
                displayName = "Deutsch (50.000 Wörter)",
                approxSizeKb = 647,
                downloadUrl = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/$FREQUENCY_WORDS_COMMIT/content/2018/de/de_50k.txt",
                exactSizeBytes = 662_497L,
                sha256 = "d9e50546fd7e8b6fe6542a2b33c51d1331092b2a3916ec09f80d97856068705b"
            ),
            OfflinePackSpec(
                langCode = "en",
                displayName = "English (50,000 words)",
                approxSizeKb = 608,
                downloadUrl = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/$FREQUENCY_WORDS_COMMIT/content/2018/en/en_50k.txt",
                exactSizeBytes = 622_749L,
                sha256 = "5351ff405b1126ef555791dd4d9798a48e3e9a501a9fc481a9da957752cfb458"
            ),
            OfflinePackSpec(
                langCode = "fr",
                displayName = "Français (50 000 mots)",
                approxSizeKb = 638,
                downloadUrl = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/$FREQUENCY_WORDS_COMMIT/content/2018/fr/fr_50k.txt",
                exactSizeBytes = 653_452L,
                sha256 = "f81f7c570b6433764da99aa30f4dfb08d81c5926301377b709af23a13b1f9596"
            ),
            OfflinePackSpec(
                langCode = "es",
                displayName = "Español (50.000 palabras)",
                approxSizeKb = 643,
                downloadUrl = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/$FREQUENCY_WORDS_COMMIT/content/2018/es/es_50k.txt",
                exactSizeBytes = 658_626L,
                sha256 = "dcff3ad4316192f4dc4ff7d26e637c6ff314ef1ca0f3f720c5649018a71056c0"
            ),
            OfflinePackSpec(
                langCode = "it",
                displayName = "Italiano (50.000 parole)",
                approxSizeKb = 638,
                downloadUrl = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/$FREQUENCY_WORDS_COMMIT/content/2018/it/it_50k.txt",
                exactSizeBytes = 653_205L,
                sha256 = "bb96cdcb56d28342c1e909db6b2525448b7767136b7e86a2ccc649a1be66fc19"
            ),
            OfflinePackSpec(
                langCode = "pt",
                displayName = "Português (50.000 palavras)",
                approxSizeKb = 637,
                downloadUrl = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/$FREQUENCY_WORDS_COMMIT/content/2018/pt/pt_50k.txt",
                exactSizeBytes = 652_361L,
                sha256 = "f97704382f97273ff59488b1885e7ac90d20f81fd92e5fbad3e3ce43633f495c"
            ),
            OfflinePackSpec(
                langCode = "nl",
                displayName = "Nederlands (50.000 woorden)",
                approxSizeKb = 636,
                downloadUrl = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/$FREQUENCY_WORDS_COMMIT/content/2018/nl/nl_50k.txt",
                exactSizeBytes = 650_762L,
                sha256 = "099bd0c27b514d54284360ad9ce2b4ef7b6903b22e848b67ab10f6aa2adb4baa"
            ),
            OfflinePackSpec(
                langCode = "pl",
                displayName = "Polski (50 000 słów)",
                approxSizeKb = 661,
                downloadUrl = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/$FREQUENCY_WORDS_COMMIT/content/2018/pl/pl_50k.txt",
                exactSizeBytes = 676_499L,
                sha256 = "50bf8d30d1ed4f5cedceb3e40e74d793ec6bec0f16d9f3dd0a2c9e313402e10f"
            ),
            OfflinePackSpec(
                langCode = "tr",
                displayName = "Türkçe (50.000 kelime)",
                approxSizeKb = 695,
                downloadUrl = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/$FREQUENCY_WORDS_COMMIT/content/2018/tr/tr_50k.txt",
                exactSizeBytes = 711_935L,
                sha256 = "480192844e7fdfe9591bbf0b9cfbb96ed5751cfbba7024fd45d8cb7508e07d5c"
            )
        )

        fun parseFrequencyWordList(langCode: String, lines: Sequence<String>): LoadedDictionaryPack {
            val baseLang = KeyboardLocales.baseLang(langCode)
            val isGerman = baseLang == "de"
            val wordFreq = HashMap<String, Int>(52_000)
            val buckets = HashMap<String, ArrayList<Pair<String, Int>>>(1_200)
            val topPool = ArrayList<String>(10_000)
            val symSpell = HashMap<String, ArrayList<String>>(24_000)
            val foldedPrefix2 = HashMap<String, ArrayList<String>>(512)
            val swipeBuckets = HashMap<Int, ArrayList<String>>(512)
            val sb = StringBuilder(24)

            for (rawLine in lines) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val spaceIdx = trimmed.indexOf(' ')
                val wordRaw = if (spaceIdx > 0) trimmed.substring(0, spaceIdx) else trimmed
                val freq = if (spaceIdx > 0) trimmed.substring(spaceIdx + 1).trim().toIntOrNull() ?: 1 else 1
                val clean = wordRaw.trim()
                if (clean.length < 2 || !clean.all { it.isLetter() || it == '\'' || it == '-' }) continue

                val lower = clean.lowercase()
                if (!wordFreq.containsKey(lower)) {
                    wordFreq[lower] = freq
                    val rank = topPool.size
                    if (rank < 10_000) {
                        topPool += lower
                        val fChar = LanguageVocabularies.foldCharBase(lower[0])
                        val lChar = LanguageVocabularies.foldCharBase(lower[lower.length - 1])
                        val sKey = (fChar.code shl 16) or lChar.code
                        swipeBuckets.getOrPut(sKey) { ArrayList(16) }.add(lower)

                        // Build O(1) SymSpell 1-delete index for top 6,000 most frequent words
                        if (rank < 6_000 && lower.length in 3..18) {
                            for (i in lower.indices) {
                                sb.setLength(0)
                                sb.append(lower, 0, i)
                                sb.append(lower, i + 1, lower.length)
                                val del = sb.toString()
                                val list = symSpell.getOrPut(del) { ArrayList(2) }
                                if (list.size < 6) list += lower
                            }
                        }

                        // Index words with umlauts/diacritics for O(1) ASCII-to-diacritic restoration
                        if (rank < 8_000 && LanguageVocabularies.hasSpecialDiacritic(lower)) {
                            val folded = LanguageVocabularies.foldDiacritics(lower, isGerman)
                            if (folded.length >= 2) {
                                val fp = foldedPrefix2.getOrPut(folded.substring(0, 2)) { ArrayList(8) }
                                if (fp.size < 60) fp += lower
                            }
                        }
                    }
                    if (lower.length >= 2) {
                        val p2 = lower.take(2)
                        val list = buckets.getOrPut(p2) { ArrayList(32) }
                        if (list.size < 120) {
                            list += (lower to freq)
                        }
                    }
                }
            }
            return LoadedDictionaryPack(
                langCode = baseLang,
                wordFreq = wordFreq,
                wordsByPrefix2 = buckets,
                topSwipePool = topPool,
                symSpell1Deletes = symSpell,
                foldedPrefix2 = foldedPrefix2,
                swipeBuckets = swipeBuckets
            )
        }
    }
}
