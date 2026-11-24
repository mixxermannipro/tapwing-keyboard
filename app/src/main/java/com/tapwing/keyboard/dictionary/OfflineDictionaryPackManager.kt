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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class OfflinePackSpec(
    val langCode: String,
    val displayName: String,
    val approxSizeKb: Int,
    val downloadUrl: String
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
    val topSwipePool: List<String>
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
        return bucket
            .asSequence()
            .filter { (w, _) -> w.startsWith(lower) && w != lower }
            .take(limit)
            .map { it.first }
            .toList()
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
    private val activeJobs = mutableMapOf<String, Job>()
    private val loadedCache = java.util.concurrent.ConcurrentHashMap<String, LoadedDictionaryPack>()

    private val _states = MutableStateFlow<Map<String, PackInstallState>>(emptyMap())
    val states: StateFlow<Map<String, PackInstallState>> = _states.asStateFlow()

    init {
        refreshStates()
    }

    fun refreshStates() {
        val next = LinkedHashMap<String, PackInstallState>()
        availablePacks.forEach { spec ->
            val current = _states.value[spec.langCode]
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
        _states.value = next
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
                downloadToFile(spec.downloadUrl, part) { progress ->
                    updateState(spec.langCode, PackInstallState.Downloading(progress))
                }
                if (!part.isFile || part.length() <= 10_000L) {
                    part.delete()
                    updateState(spec.langCode, PackInstallState.Error("Incomplete dictionary pack"))
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

    private fun downloadToFile(urlStr: String, dest: File, onProgress: (Float) -> Unit) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "TapwingKeyboard/2.0 (Android)")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val total = conn.contentLengthLong.takeIf { it > 0L } ?: 650_000L
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(16 * 1024)
                    var readTotal = 0L
                    while (scope.isActive) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0.05f, 0.99f))
                    }
                    output.flush()
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun updateState(langCode: String, state: PackInstallState) {
        val next = _states.value.toMutableMap()
        next[langCode] = state
        _states.value = next
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
        val availablePacks: List<OfflinePackSpec> = listOf(
            OfflinePackSpec("de", "Deutsch (50.000 Wörter)", 620, "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/de/de_50k.txt"),
            OfflinePackSpec("en", "English (50,000 words)", 560, "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt"),
            OfflinePackSpec("fr", "Français (50 000 mots)", 600, "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/fr/fr_50k.txt"),
            OfflinePackSpec("es", "Español (50.000 palabras)", 590, "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/es/es_50k.txt"),
            OfflinePackSpec("it", "Italiano (50.000 parole)", 600, "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/it/it_50k.txt"),
            OfflinePackSpec("pt", "Português (50.000 palavras)", 590, "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/pt/pt_50k.txt"),
            OfflinePackSpec("nl", "Nederlands (50.000 woorden)", 580, "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/nl/nl_50k.txt"),
            OfflinePackSpec("pl", "Polski (50 000 słów)", 630, "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/pl/pl_50k.txt"),
            OfflinePackSpec("tr", "Türkçe (50.000 kelime)", 610, "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/tr/tr_50k.txt")
        )

        fun parseFrequencyWordList(langCode: String, lines: Sequence<String>): LoadedDictionaryPack {
            val wordFreq = HashMap<String, Int>(52_000)
            val buckets = HashMap<String, ArrayList<Pair<String, Int>>>(1_200)
            val topPool = ArrayList<String>(10_000)

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
                    if (topPool.size < 10_000) {
                        topPool += lower
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
                langCode = langCode,
                wordFreq = wordFreq,
                wordsByPrefix2 = buckets,
                topSwipePool = topPool
            )
        }
    }
}
