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
    data class Installed(val filePath: String, val sizeBytes: Long) : ModelInstallState
    data class Error(val message: String) : ModelInstallState
}

/**
 * Downloads, imports, validates, and deletes on-device Whisper (.bin) speech models
 * inside app-private noBackupFilesDir so models persist locally without draining cloud backup.
 */
class LocalModelManager(context: Context) {

    private val appContext = context.applicationContext
    private val modelsRoot: File = File(appContext.noBackupFilesDir ?: appContext.filesDir, "tapwing_models")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    private val _states = MutableStateFlow<Map<String, ModelInstallState>>(emptyMap())
    val states: StateFlow<Map<String, ModelInstallState>> = _states.asStateFlow()

    init {
        runCatching {
            val legacyLlmDir = File(modelsRoot, "llm")
            if (legacyLlmDir.exists()) legacyLlmDir.deleteRecursively()
        }
        refreshStates()
    }

    fun refreshStates() {
        val next = LinkedHashMap<String, ModelInstallState>()
        LocalModelCatalog.allModels.forEach { spec ->
            val current = _states.value[spec.id]
            if (current is ModelInstallState.Downloading) {
                next[spec.id] = current
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
        val dir = File(modelsRoot, "whisper")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, spec.fileName)
    }

    fun installedModelFile(modelId: String): File? {
        val spec = LocalModelCatalog.findById(modelId) ?: return null
        val f = modelFileFor(spec)
        return if (f.isFile && f.length() > MIN_VALID_MODEL_BYTES && hasValidModelHeader(f, spec.kind)) f else null
    }

    fun firstInstalledModel(kind: LocalModelKind, preferredId: String): Pair<LocalModelSpec, File>? {
        val preferred = LocalModelCatalog.findById(preferredId)
        if (preferred != null && preferred.kind == kind) {
            val f = installedModelFile(preferred.id)
            if (f != null) return preferred to f
        }
        for (spec in LocalModelCatalog.whisperModels) {
            val f = installedModelFile(spec.id)
            if (f != null) return spec to f
        }
        return null
    }

    fun startDownload(spec: LocalModelSpec) {
        if (activeJobs[spec.id]?.isActive == true) return
        val job = scope.launch {
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
                    return@launch
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
        activeJobs[spec.id] = job
    }

    fun cancelDownload(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        val spec = LocalModelCatalog.findById(modelId)
        if (spec != null) {
            File(modelFileFor(spec).parentFile, "${spec.fileName}.part").delete()
        }
        refreshStates()
    }

    fun deleteModel(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        val spec = LocalModelCatalog.findById(modelId) ?: return
        val target = modelFileFor(spec)
        if (target.exists()) target.delete()
        File(target.parentFile, "${spec.fileName}.part").delete()
        LocalInferenceEngine.unloadIfModel(target.absolutePath)
        refreshStates()
    }

    suspend fun importModelFromUri(spec: LocalModelSpec, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val target = modelFileFor(spec)
        val part = File(target.parentFile, "${spec.fileName}.part")
        updateState(spec.id, ModelInstallState.Downloading(0.1f, 0L, spec.approxSizeMb * 1_000_000L))
        return@withContext runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        total += n
                    }
                    output.flush()
                }
            } ?: error("Cannot open selected file")

            if (!part.isFile || part.length() <= MIN_VALID_MODEL_BYTES || !hasValidModelHeader(part, spec.kind)) {
                part.delete()
                updateState(spec.id, ModelInstallState.Error("Selected file is not a valid Whisper speech model"))
                return@withContext false
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
                setRequestProperty("User-Agent", "TapwingKeyboard/2.0 (Android)")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: error("Redirect without Location")
                conn.disconnect()
                currentUrl = URL(URL(currentUrl), loc).toString()
                redirects++
                if (redirects > 8) error("Too many redirects")
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
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var lastReportMs = 0L
                    while (scope.isActive) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportMs >= 180L) {
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

        internal fun hasValidModelHeader(file: File, kind: LocalModelKind): Boolean {
            return runCatching {
                val hdr = ByteArray(4)
                file.inputStream().use { input ->
                    if (input.read(hdr) < 4) return false
                }
                val s = String(hdr, Charsets.US_ASCII)
                when (kind) {
                    LocalModelKind.WHISPER_STT -> s in setOf("lmgg", "ggml", "fmgg", "ggmf", "tjgg", "ggjt", "GGUF")
                }
            }.getOrDefault(false)
        }
    }
}
