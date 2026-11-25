package com.tapwing.keyboard.ai

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface ModelInstallState {
    data object NotInstalled : ModelInstallState
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : ModelInstallState
    data class Extracting(val progress: Float) : ModelInstallState
    data class Installed(val filePath: String, val sizeBytes: Long) : ModelInstallState
    data class Error(val message: String) : ModelInstallState
}

/**
 * Downloads, extracts, imports, validates, and deletes on-device speech and LLM models
 * (Sherpa-ONNX directory packages, Whisper .bin files, and GGUF language models) inside app-private noBackupFilesDir.
 */
class LocalModelManager(context: Context) {

    private val appContext = context.applicationContext
    private val modelsRoot: File = File(appContext.noBackupFilesDir ?: appContext.filesDir, "tapwing_models")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    private val _states = MutableStateFlow<Map<String, ModelInstallState>>(emptyMap())
    val states: StateFlow<Map<String, ModelInstallState>> = _states.asStateFlow()

    init {
        refreshStates()
    }

    fun refreshStates() {
        val next = LinkedHashMap<String, ModelInstallState>()
        LocalModelCatalog.allModels.forEach { spec ->
            val current = _states.value[spec.id]
            if (current is ModelInstallState.Downloading || current is ModelInstallState.Extracting) {
                next[spec.id] = current
            } else if (spec.kind.isSherpa) {
                val dir = sherpaModelDirFor(spec)
                if (hasValidSherpaInstallation(spec, dir)) {
                    next[spec.id] = ModelInstallState.Installed(dir.absolutePath, directorySizeBytes(dir))
                } else {
                    if (dir.exists()) dir.deleteRecursively()
                    next[spec.id] = ModelInstallState.NotInstalled
                }
            } else {
                val file = modelFileFor(spec)
                if (file.isFile && file.length() > MIN_VALID_MODEL_BYTES && hasValidModelHeader(file, spec.kind)) {
                    next[spec.id] = ModelInstallState.Installed(file.absolutePath, file.length())
                } else {
                    if (file.isFile && (file.length() <= MIN_VALID_MODEL_BYTES || !hasValidModelHeader(file, spec.kind))) {
                        file.delete()
                    }
                    next[spec.id] = ModelInstallState.NotInstalled
                }
            }
        }
        _states.value = next
    }

    fun modelFileFor(spec: LocalModelSpec): File {
        return when {
            spec.kind.isSherpa -> sherpaModelDirFor(spec)
            spec.kind.isLlm -> {
                val dir = File(modelsRoot, "llm")
                if (!dir.exists()) dir.mkdirs()
                File(dir, spec.fileName)
            }
            else -> {
                val dir = File(modelsRoot, "whisper")
                if (!dir.exists()) dir.mkdirs()
                File(dir, spec.fileName)
            }
        }
    }

    fun sherpaModelDirFor(spec: LocalModelSpec): File {
        val dir = File(modelsRoot, "sherpa")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, spec.id)
    }

    fun installedModelFile(modelId: String): File? {
        val spec = LocalModelCatalog.findById(modelId) ?: return null
        return if (spec.kind.isSherpa) {
            val dir = sherpaModelDirFor(spec)
            if (hasValidSherpaInstallation(spec, dir)) dir else null
        } else {
            val f = modelFileFor(spec)
            if (f.isFile && f.length() > MIN_VALID_MODEL_BYTES && hasValidModelHeader(f, spec.kind)) f else null
        }
    }

    /**
     * Picks the best installed on-device speech model for the active locale:
     * 1) Preferred model if installed AND supports the active locale (and multilingual if requested)
     * 2) When preferMultilingual=true, installed speech models with native multilingual code-switching
     * 3) Next-gen Sherpa-ONNX model supporting the active locale
     * 4) Any installed Whisper model (supports all locales)
     * 5) Preferred model regardless of locale, or any installed speech model as last resort
     */
    fun bestInstalledSpeechModel(
        preferredId: String,
        locale: String,
        preferMultilingual: Boolean = false
    ): Pair<LocalModelSpec, File>? {
        val preferred = LocalModelCatalog.findById(preferredId)?.takeIf { it.kind.isSpeechModel }
        if (preferred != null && preferred.supportsLocale(locale) &&
            (!preferMultilingual || preferred.supportsMultilingualCodeSwitching)
        ) {
            installedModelFile(preferred.id)?.let { return preferred to it }
        }
        if (preferMultilingual) {
            for (spec in LocalModelCatalog.speechModels) {
                if (spec.supportsMultilingualCodeSwitching && spec.supportsLocale(locale)) {
                    installedModelFile(spec.id)?.let { return spec to it }
                }
            }
        }
        if (preferred != null && preferred.supportsLocale(locale)) {
            installedModelFile(preferred.id)?.let { return preferred to it }
        }
        for (spec in LocalModelCatalog.sherpaModels) {
            if (spec.supportsLocale(locale)) {
                installedModelFile(spec.id)?.let { return spec to it }
            }
        }
        for (spec in LocalModelCatalog.whisperModels) {
            installedModelFile(spec.id)?.let { return spec to it }
        }
        if (preferred != null) {
            installedModelFile(preferred.id)?.let { return preferred to it }
        }
        for (spec in LocalModelCatalog.speechModels) {
            installedModelFile(spec.id)?.let { return spec to it }
        }
        return null
    }

    /**
     * Picks the best installed on-device GGUF LLM for local writing aid & dictation cleanup.
     */
    fun bestInstalledLlmModel(preferredId: String): Pair<LocalModelSpec, File>? {
        val preferred = LocalModelCatalog.findById(preferredId)?.takeIf { it.kind.isLlm }
        if (preferred != null) {
            installedModelFile(preferred.id)?.let { return preferred to it }
        }
        for (spec in LocalModelCatalog.llmModels) {
            installedModelFile(spec.id)?.let { return spec to it }
        }
        return null
    }

    fun firstInstalledModel(kind: LocalModelKind, preferredId: String): Pair<LocalModelSpec, File>? {
        val preferred = LocalModelCatalog.findById(preferredId)
        if (preferred != null && preferred.kind == kind) {
            val f = installedModelFile(preferred.id)
            if (f != null) return preferred to f
        }
        for (spec in LocalModelCatalog.allModels) {
            if (spec.kind == kind) {
                val f = installedModelFile(spec.id)
                if (f != null) return spec to f
            }
        }
        return null
    }

    fun startDownload(spec: LocalModelSpec) {
        if (activeJobs[spec.id]?.isActive == true) return
        val job = scope.launch {
            if (spec.kind.isSherpa) {
                downloadAndInstallSherpa(spec)
            } else {
                downloadAndInstallSingleFile(spec)
            }
        }
        activeJobs[spec.id] = job
    }

    private suspend fun downloadAndInstallSingleFile(spec: LocalModelSpec) {
        val target = modelFileFor(spec)
        val part = File(target.parentFile, "${spec.fileName}.part")
        updateState(spec.id, ModelInstallState.Downloading(0f, 0L, spec.approxSizeMb * 1_000_000L))
        try {
            downloadWithRedirects(spec.downloadUrl, part) { downloaded, total ->
                val effectiveTotal = if (total > 0L) total else spec.approxSizeMb * 1_000_000L
                val progress = (downloaded.toFloat() / effectiveTotal.toFloat()).coerceIn(0f, 0.99f)
                updateState(spec.id, ModelInstallState.Downloading(progress, downloaded, effectiveTotal))
            }
            if (!part.isFile || part.length() <= MIN_VALID_MODEL_BYTES || !hasValidModelHeader(part, spec.kind)) {
                part.delete()
                updateState(spec.id, ModelInstallState.Error("Downloaded file was incomplete or invalid"))
                return
            }
            if (spec.kind.isLlm) {
                LocalLlmEngine.unloadIfModel(target.absolutePath)
            } else {
                LocalInferenceEngine.unloadIfModel(target.absolutePath)
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            updateState(spec.id, ModelInstallState.Installed(target.absolutePath, target.length()))
        } catch (ce: CancellationException) {
            part.delete()
            updateState(spec.id, ModelInstallState.NotInstalled)
            throw ce
        } catch (ex: Exception) {
            part.delete()
            updateState(spec.id, ModelInstallState.Error(ex.localizedMessage ?: "Download failed"))
        }
    }

    private suspend fun downloadAndInstallSherpa(spec: LocalModelSpec) {
        val targetDir = sherpaModelDirFor(spec)
        val parentDir = targetDir.parentFile!!
        val archivePart = File(parentDir, "${spec.id}.tar.bz2.part")
        val extractTmpDir = File(parentDir, "${spec.id}.tmp")
        val estBytes = spec.approxSizeMb * 1_000_000L
        updateState(spec.id, ModelInstallState.Downloading(0f, 0L, estBytes))
        try {
            downloadWithRedirects(spec.downloadUrl, archivePart) { downloaded, total ->
                val effectiveTotal = if (total > 0L) total else estBytes
                val progress = ((downloaded.toFloat() / effectiveTotal.toFloat()) * 0.85f).coerceIn(0f, 0.85f)
                updateState(spec.id, ModelInstallState.Downloading(progress, downloaded, effectiveTotal))
            }
            if (!archivePart.isFile || archivePart.length() <= MIN_VALID_MODEL_BYTES) {
                archivePart.delete()
                updateState(spec.id, ModelInstallState.Error("Downloaded model archive was incomplete"))
                return
            }
            updateState(spec.id, ModelInstallState.Extracting(0.86f))
            if (extractTmpDir.exists()) extractTmpDir.deleteRecursively()
            extractTmpDir.mkdirs()

            TarBz2Extractor.extract(archivePart, extractTmpDir) { count, _ ->
                val frac = (0.86f + (count.coerceAtMost(5) / 5f) * 0.12f).coerceIn(0.86f, 0.99f)
                updateState(spec.id, ModelInstallState.Extracting(frac))
            }
            archivePart.delete()

            if (!hasValidSherpaInstallation(spec, extractTmpDir)) {
                extractTmpDir.deleteRecursively()
                updateState(spec.id, ModelInstallState.Error("Extracted archive missing required ONNX files"))
                return
            }

            SherpaOnnxEngine.unloadIfModel(spec.id)
            if (targetDir.exists()) targetDir.deleteRecursively()
            if (!extractTmpDir.renameTo(targetDir)) {
                extractTmpDir.copyRecursively(targetDir, overwrite = true)
                extractTmpDir.deleteRecursively()
            }
            updateState(spec.id, ModelInstallState.Installed(targetDir.absolutePath, directorySizeBytes(targetDir)))
        } catch (ce: CancellationException) {
            archivePart.delete()
            extractTmpDir.deleteRecursively()
            updateState(spec.id, ModelInstallState.NotInstalled)
            throw ce
        } catch (ex: Exception) {
            archivePart.delete()
            extractTmpDir.deleteRecursively()
            updateState(spec.id, ModelInstallState.Error(ex.localizedMessage ?: "Download failed"))
        }
    }

    fun cancelDownload(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        val spec = LocalModelCatalog.findById(modelId)
        if (spec != null) {
            if (spec.kind.isSherpa) {
                val dir = sherpaModelDirFor(spec)
                File(dir.parentFile, "${spec.id}.tar.bz2.part").delete()
                File(dir.parentFile, "${spec.id}.tmp").deleteRecursively()
            } else {
                File(modelFileFor(spec).parentFile, "${spec.fileName}.part").delete()
            }
        }
        refreshStates()
    }

    fun deleteModel(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        val spec = LocalModelCatalog.findById(modelId) ?: return
        if (spec.kind.isSherpa) {
            SherpaOnnxEngine.unloadIfModel(spec.id)
            val dir = sherpaModelDirFor(spec)
            if (dir.exists()) dir.deleteRecursively()
            File(dir.parentFile, "${spec.id}.tar.bz2.part").delete()
            File(dir.parentFile, "${spec.id}.tmp").deleteRecursively()
        } else {
            val target = modelFileFor(spec)
            if (spec.kind.isLlm) {
                LocalLlmEngine.unloadIfModel(target.absolutePath)
            } else {
                LocalInferenceEngine.unloadIfModel(target.absolutePath)
            }
            if (target.exists()) target.delete()
            File(target.parentFile, "${spec.fileName}.part").delete()
        }
        refreshStates()
    }

    suspend fun importModelFromUri(spec: LocalModelSpec, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (spec.kind.isSherpa) {
            val targetDir = sherpaModelDirFor(spec)
            val parentDir = targetDir.parentFile!!
            val archivePart = File(parentDir, "${spec.id}.tar.bz2.part")
            val extractTmpDir = File(parentDir, "${spec.id}.tmp")
            updateState(spec.id, ModelInstallState.Downloading(0.15f, 0L, spec.approxSizeMb * 1_000_000L))
            return@withContext runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(archivePart).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                        }
                        output.flush()
                    }
                } ?: error("Cannot open selected archive")

                updateState(spec.id, ModelInstallState.Extracting(0.86f))
                if (extractTmpDir.exists()) extractTmpDir.deleteRecursively()
                extractTmpDir.mkdirs()
                TarBz2Extractor.extract(archivePart, extractTmpDir)
                archivePart.delete()

                if (!hasValidSherpaInstallation(spec, extractTmpDir)) {
                    extractTmpDir.deleteRecursively()
                    updateState(spec.id, ModelInstallState.Error("Selected archive is missing required model files"))
                    return@withContext false
                }
                SherpaOnnxEngine.unloadIfModel(spec.id)
                if (targetDir.exists()) targetDir.deleteRecursively()
                if (!extractTmpDir.renameTo(targetDir)) {
                    extractTmpDir.copyRecursively(targetDir, overwrite = true)
                    extractTmpDir.deleteRecursively()
                }
                updateState(spec.id, ModelInstallState.Installed(targetDir.absolutePath, directorySizeBytes(targetDir)))
                true
            }.getOrElse { ex ->
                archivePart.delete()
                extractTmpDir.deleteRecursively()
                updateState(spec.id, ModelInstallState.Error(ex.localizedMessage ?: "Import failed"))
                false
            }
        }

        val target = modelFileFor(spec)
        val part = File(target.parentFile, "${spec.fileName}.part")
        updateState(spec.id, ModelInstallState.Downloading(0.1f, 0L, spec.approxSizeMb * 1_000_000L))
        return@withContext runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                    }
                    output.flush()
                }
            } ?: error("Cannot open selected file")

            if (!part.isFile || part.length() <= MIN_VALID_MODEL_BYTES || !hasValidModelHeader(part, spec.kind)) {
                part.delete()
                val errMsg = if (spec.kind.isLlm) {
                    "Selected file is not a valid GGUF language model"
                } else {
                    "Selected file is not a valid Whisper speech model"
                }
                updateState(spec.id, ModelInstallState.Error(errMsg))
                return@withContext false
            }
            if (spec.kind.isLlm) {
                LocalLlmEngine.unloadIfModel(target.absolutePath)
            } else {
                LocalInferenceEngine.unloadIfModel(target.absolutePath)
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            updateState(spec.id, ModelInstallState.Installed(target.absolutePath, target.length()))
            true
        }.getOrElse { ex ->
            part.delete()
            updateState(spec.id, ModelInstallState.Error(ex.localizedMessage ?: "Import failed"))
            false
        }
    }

    private suspend fun downloadWithRedirects(
        startUrl: String,
        dest: File,
        onProgress: (Long, Long) -> Unit
    ) {
        var currentUrl = startUrl
        var redirects = 0
        var conn: HttpURLConnection
        while (true) {
            conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "TapwingKeyboard/2.6 (Android)")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: error("Redirect without Location")
                conn.disconnect()
                currentUrl = URL(URL(currentUrl), loc).toString()
                redirects++
                if (redirects > 10) error("Too many redirects")
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                error("HTTP $code")
            }
            break
        }

        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var downloaded = 0L
                    var lastReportMs = 0L
                    while (scope.isActive) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportMs >= 160L) {
                            lastReportMs = now
                            onProgress(downloaded, total)
                        }
                    }
                    output.flush()
                    onProgress(downloaded, total)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun updateState(id: String, state: ModelInstallState) {
        val next = _states.value.toMutableMap()
        next[id] = state
        _states.value = next
    }

    companion object {
        private const val MIN_VALID_MODEL_BYTES = 1_000_000L
        private const val MIN_VALID_ONNX_BYTES = 10_240L

        internal fun hasValidModelHeader(file: File, kind: LocalModelKind): Boolean {
            return runCatching {
                val hdr = ByteArray(4)
                file.inputStream().use { input ->
                    if (input.read(hdr) < 4) return false
                }
                val s = String(hdr, Charsets.US_ASCII)
                when (kind) {
                    LocalModelKind.WHISPER_STT -> s in setOf("lmgg", "ggml", "fmgg", "ggmf", "tjgg", "ggjt", "GGUF")
                    LocalModelKind.LOCAL_LLM -> s == "GGUF"
                    LocalModelKind.SHERPA_STREAMING,
                    LocalModelKind.SHERPA_TRANSDUCER,
                    LocalModelKind.SHERPA_CANARY -> false
                }
            }.getOrDefault(false)
        }

        internal fun hasValidSherpaInstallation(spec: LocalModelSpec, dir: File): Boolean {
            if (!spec.kind.isSherpa || !dir.isDirectory || spec.requiredFiles.isEmpty()) return false
            return spec.requiredFiles.all { name ->
                val f = File(dir, name)
                if (!f.isFile || f.length() == 0L) return@all false
                if (name.endsWith(".onnx", ignoreCase = true)) f.length() >= MIN_VALID_ONNX_BYTES else true
            }
        }

        private fun directorySizeBytes(dir: File): Long {
            if (!dir.exists()) return 0L
            return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
}
