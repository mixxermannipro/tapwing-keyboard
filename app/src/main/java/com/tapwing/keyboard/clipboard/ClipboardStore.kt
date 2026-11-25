package com.tapwing.keyboard.clipboard

import android.content.Context
import androidx.core.content.ContextCompat
import com.tapwing.keyboard.data.ClipboardRetention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

internal const val SENSITIVE_CLIP_EXTRA = "android.content.extra.IS_SENSITIVE"

data class ClipboardClip(
    val id: String,
    val text: String,
    val timestampMs: Long,
    val pinned: Boolean = false,
    val imagePath: String? = null,
    val mimeType: String? = null,
    val ocrText: String? = null,
    val imageDigest: String? = null
) {
    val isImage: Boolean get() = !imagePath.isNullOrBlank()
}

/**
 * Pure Kotlin / JSON-backed clipboard store supporting text clips, image/screenshot clips,
 * OCR metadata, pinned items, configurable retention, and up to 200 recent unpinned clips.
 */
class ClipboardStore(
    private val storageFile: File?,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val _items = MutableStateFlow<List<ClipboardClip>>(emptyList())
    val items: StateFlow<List<ClipboardClip>> = _items.asStateFlow()

    init {
        loadFromDisk()
    }

    @Synchronized
    fun addClip(
        rawText: String,
        retention: ClipboardRetention = ClipboardRetention.DAYS_7,
        maxUnpinnedItems: Int = 100
    ) {
        if (rawText.isBlank()) return
        val now = clockMs()
        val existing = _items.value.firstOrNull { !it.isImage && it.text == rawText }
        val clip = existing?.copy(timestampMs = now) ?: ClipboardClip(
            id = UUID.randomUUID().toString(),
            text = rawText,
            timestampMs = now
        )
        applyAndSave(listOf(clip) + _items.value.filterNot { it.id == clip.id }, retention, maxUnpinnedItems, now)
    }

    @Synchronized
    fun addImageClip(
        imagePath: String,
        mimeType: String = "image/png",
        displayLabel: String = "Screenshot",
        ocrText: String? = null,
        retention: ClipboardRetention = ClipboardRetention.DAYS_7,
        maxUnpinnedItems: Int = 100,
        imageDigest: String? = null
    ): ClipboardClip? {
        if (imagePath.isBlank()) return null
        val now = clockMs()
        val digest = imageDigest ?: fingerprint(File(imagePath))
        val existing = _items.value.firstOrNull {
            it.imagePath == imagePath || (digest != null && it.imageDigest == digest)
        }
        val recognized = ocrText?.trim()?.takeIf { it.isNotEmpty() }
        val clip = existing?.copy(timestampMs = now, ocrText = existing.ocrText ?: recognized) ?: ClipboardClip(
            id = UUID.randomUUID().toString(),
            text = displayLabel,
            timestampMs = now,
            imagePath = imagePath,
            mimeType = mimeType,
            ocrText = recognized,
            imageDigest = digest
        )
        val saved = applyAndSave(listOf(clip) + _items.value.filterNot { it.id == clip.id },
            retention, maxUnpinnedItems, now)
        if (!saved) return null
        if (clip.imagePath != imagePath) File(imagePath).delete()
        return clip
    }

    @Synchronized
    fun updateClipOcrText(
        id: String,
        ocrText: String,
        retention: ClipboardRetention,
        maxUnpinnedItems: Int
    ) {
        val clean = ocrText.trim()
        if (clean.isEmpty()) return
        val updated = _items.value.map { clip ->
            if (clip.id == id) clip.copy(ocrText = clean) else clip
        }
        applyAndSave(updated, retention, maxUnpinnedItems, clockMs())
    }

    @Synchronized
    fun togglePin(id: String, retention: ClipboardRetention, maxUnpinnedItems: Int) {
        val now = clockMs()
        val updated = _items.value.map { clip ->
            if (clip.id == id) clip.copy(pinned = !clip.pinned, timestampMs = now) else clip
        }
        applyAndSave(updated, retention, maxUnpinnedItems, now)
    }

    @Synchronized
    fun deleteClip(id: String, retention: ClipboardRetention, maxUnpinnedItems: Int) {
        val now = clockMs()
        val target = _items.value.firstOrNull { it.id == id }
        val updated = _items.value.filterNot { it.id == id }
        if (!applyAndSave(updated, retention, maxUnpinnedItems, now)) return
        target?.imagePath?.let { path ->
            if (updated.none { it.imagePath == path }) {
                runCatching { File(path).delete() }
            }
        }
    }

    @Synchronized
    fun clearUnpinned(retention: ClipboardRetention, maxUnpinnedItems: Int) {
        val now = clockMs()
        val kept = _items.value.filter { it.pinned }
        val removed = _items.value.filter { !it.pinned }
        if (!applyAndSave(kept, retention, maxUnpinnedItems, now)) return
        val keptPaths = kept.mapNotNull { it.imagePath }.toSet()
        removed.mapNotNull { it.imagePath }.filter { it !in keptPaths }.forEach { path ->
            runCatching { File(path).delete() }
        }
    }

    @Synchronized
    fun prune(retention: ClipboardRetention, maxUnpinnedItems: Int) {
        applyAndSave(_items.value, retention, maxUnpinnedItems, clockMs())
    }

    private fun applyAndSave(
        rawList: List<ClipboardClip>,
        retention: ClipboardRetention,
        maxUnpinnedItems: Int,
        nowMs: Long
    ): Boolean {
        val limit = maxUnpinnedItems.coerceIn(1, 200)
        val pinned = rawList
            .filter { it.pinned }
            .sortedByDescending { it.timestampMs }

        val unpinned = rawList
            .filter { !it.pinned }
            .filter { clip ->
                if (retention.durationMs <= 0L) true
                else (nowMs - clip.timestampMs) <= retention.durationMs
            }
            .sortedByDescending { it.timestampMs }
            .take(limit)

        val merged = pinned + unpinned
        if (_items.value != merged && !saveToDisk(merged)) return false
        val keptPaths = merged.mapNotNull { it.imagePath }.toSet()
        rawList.mapNotNull { it.imagePath }.filter { it !in keptPaths }.forEach { path ->
            runCatching { File(path).delete() }
        }

        _items.value = merged
        return true
    }

    private fun loadFromDisk() {
        val file = storageFile ?: return
        if (!file.isFile) return
        runCatching {
            val arr = JSONArray(file.readText())
            val list = ArrayList<ClipboardClip>(arr.length())
            var needsMigration = false
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: continue
                val imgPath = obj.optString("imagePath", "").takeIf { it.isNotBlank() }
                if (imgPath != null && !File(imgPath).isFile) continue
                val text = obj.optString("text")
                if (imgPath != null && !obj.has("imageDigest")) needsMigration = true
                if (text.isBlank() && imgPath == null) continue
                list += ClipboardClip(
                    id = id,
                    text = text,
                    timestampMs = obj.optLong("ts"),
                    pinned = obj.optBoolean("pinned", false),
                    imagePath = imgPath,
                    mimeType = obj.optString("mimeType", "").takeIf { it.isNotBlank() },
                    ocrText = obj.optString("ocrText", "").takeIf { it.isNotBlank() },
                    imageDigest = obj.optString("imageDigest", "").takeIf { it.isNotBlank() }
                        ?: imgPath?.let { fingerprint(File(it)) }
                )
            }
            val merged = list.groupBy { clip ->
                clip.isImage to (clip.imageDigest ?: clip.imagePath ?: clip.text)
            }.values.map { copies ->
                val primary = copies.maxWith(compareBy<ClipboardClip> { it.pinned }.thenBy { it.timestampMs })
                primary.copy(
                    timestampMs = copies.maxOf { it.timestampMs },
                    ocrText = primary.ocrText ?: copies.firstNotNullOfOrNull { it.ocrText }
                )
            }.sortedWith(compareByDescending<ClipboardClip> { it.pinned }.thenByDescending { it.timestampMs })
            _items.value = merged
            if ((merged.size != list.size || needsMigration) &&
                saveToDisk(merged)
            ) {
                val retainedPaths = merged.mapNotNull { it.imagePath }.toSet()
                list.mapNotNull { it.imagePath }.distinct().filterNot { it in retainedPaths }.forEach { File(it).delete() }
            }
        }
    }

    private fun saveToDisk(list: List<ClipboardClip>): Boolean {
        val file = storageFile ?: return true
        return runCatching {
            file.parentFile?.mkdirs()
            val arr = JSONArray()
            for (clip in list) {
                arr.put(
                    JSONObject().apply {
                        put("id", clip.id)
                        put("text", clip.text)
                        put("ts", clip.timestampMs)
                        put("pinned", clip.pinned)
                        clip.imagePath?.let { put("imagePath", it) }
                        clip.mimeType?.let { put("mimeType", it) }
                        clip.ocrText?.let { put("ocrText", it) }
                        clip.imageDigest?.let { put("imageDigest", it) }
                    }
                )
            }
            return com.tapwing.keyboard.data.AtomicFileWriter.writeTextAtomically(
                target = file,
                content = arr.toString(),
                tempPrefix = "clipboard-"
            )
        }.getOrDefault(false)
    }

    companion object {
        private const val CLIPBOARD_FILE_NAME = "tapwing_clipboard_history.json"

        internal fun fingerprint(file: File): String? = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val length = stream.read(buffer)
                    if (length < 0) break
                    digest.update(buffer, 0, length)
                }
            }
            digest.digest().toHexString()
        }.getOrNull()

        fun createForContext(context: Context): ClipboardStore {
            val appCtx = context.applicationContext
            // Clipboard history contains sensitive user text/images and MUST live in Credential-Encrypted
            // storage (inaccessible before device unlock / Direct Boot).
            if (!com.tapwing.keyboard.data.TapwingPreferencesRepository.isUserUnlocked(appCtx)) {
                return ClipboardStore(storageFile = null)
            }
            val ceFile = File(appCtx.filesDir, CLIPBOARD_FILE_NAME)
            // Seamless one-time migration from legacy Device-Protected storage -> Credential-Encrypted storage
            runCatching {
                val deCtx = ContextCompat.createDeviceProtectedStorageContext(appCtx)
                if (deCtx != null && deCtx.filesDir != appCtx.filesDir) {
                    val legacyDeFile = File(deCtx.filesDir, CLIPBOARD_FILE_NAME)
                    if (legacyDeFile.isFile) {
                        if (!ceFile.isFile) {
                            legacyDeFile.copyTo(ceFile, overwrite = false)
                        }
                        legacyDeFile.delete()
                    }
                }
            }
            return ClipboardStore(ceFile)
        }
    }
}

internal fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
