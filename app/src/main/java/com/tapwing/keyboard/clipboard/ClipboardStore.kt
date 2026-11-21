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
import java.util.UUID

data class ClipboardClip(
    val id: String,
    val text: String,
    val timestampMs: Long,
    val pinned: Boolean = false,
    val imagePath: String? = null,
    val mimeType: String? = null,
    val ocrText: String? = null
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

    fun addClip(
        rawText: String,
        retention: ClipboardRetention = ClipboardRetention.DAYS_7,
        maxUnpinnedItems: Int = 100
    ) {
        val text = rawText.trim()
        if (text.isEmpty() || isApiKey(text)) return

        val now = clockMs()
        val existingPinned = _items.value.firstOrNull { !it.isImage && it.text == text && it.pinned }
        if (existingPinned != null) {
            val updated = _items.value.map {
                if (it.id == existingPinned.id) it.copy(timestampMs = now) else it
            }
            applyAndSave(updated, retention, maxUnpinnedItems, now)
            return
        }

        val withoutDup = _items.value.filterNot { !it.isImage && it.text == text }
        val newClip = ClipboardClip(
            id = UUID.randomUUID().toString(),
            text = text,
            timestampMs = now,
            pinned = false
        )
        applyAndSave(listOf(newClip) + withoutDup, retention, maxUnpinnedItems, now)
    }

    fun addImageClip(
        imagePath: String,
        mimeType: String = "image/png",
        displayLabel: String = "Screenshot",
        ocrText: String? = null,
        retention: ClipboardRetention = ClipboardRetention.DAYS_7,
        maxUnpinnedItems: Int = 100
    ): ClipboardClip? {
        if (imagePath.isBlank()) return null
        val now = clockMs()
        val existing = _items.value.firstOrNull { it.imagePath == imagePath }
        if (existing != null) return existing

        val newClip = ClipboardClip(
            id = UUID.randomUUID().toString(),
            text = displayLabel,
            timestampMs = now,
            pinned = false,
            imagePath = imagePath,
            mimeType = mimeType,
            ocrText = ocrText?.trim()?.takeIf { it.isNotEmpty() }
        )
        applyAndSave(listOf(newClip) + _items.value, retention, maxUnpinnedItems, now)
        return newClip
    }

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

    fun togglePin(id: String, retention: ClipboardRetention, maxUnpinnedItems: Int) {
        val now = clockMs()
        val updated = _items.value.map { clip ->
            if (clip.id == id) clip.copy(pinned = !clip.pinned, timestampMs = now) else clip
        }
        applyAndSave(updated, retention, maxUnpinnedItems, now)
    }

    fun deleteClip(id: String, retention: ClipboardRetention, maxUnpinnedItems: Int) {
        val now = clockMs()
        val target = _items.value.firstOrNull { it.id == id }
        val updated = _items.value.filterNot { it.id == id }
        applyAndSave(updated, retention, maxUnpinnedItems, now)
        target?.imagePath?.let { path ->
            if (updated.none { it.imagePath == path }) {
                runCatching { File(path).delete() }
            }
        }
    }

    fun clearUnpinned(retention: ClipboardRetention, maxUnpinnedItems: Int) {
        val now = clockMs()
        val kept = _items.value.filter { it.pinned }
        val removed = _items.value.filter { !it.pinned }
        applyAndSave(kept, retention, maxUnpinnedItems, now)
        val keptPaths = kept.mapNotNull { it.imagePath }.toSet()
        removed.mapNotNull { it.imagePath }.filter { it !in keptPaths }.forEach { path ->
            runCatching { File(path).delete() }
        }
    }

    fun prune(retention: ClipboardRetention, maxUnpinnedItems: Int) {
        applyAndSave(_items.value, retention, maxUnpinnedItems, clockMs())
    }

    private fun applyAndSave(
        rawList: List<ClipboardClip>,
        retention: ClipboardRetention,
        maxUnpinnedItems: Int,
        nowMs: Long
    ) {
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
        val keptPaths = merged.mapNotNull { it.imagePath }.toSet()
        rawList.mapNotNull { it.imagePath }.filter { it !in keptPaths }.forEach { path ->
            runCatching { File(path).delete() }
        }

        _items.value = merged
        saveToDisk(merged)
    }

    private fun loadFromDisk() {
        val file = storageFile ?: return
        if (!file.isFile) return
        runCatching {
            val arr = JSONArray(file.readText())
            val list = ArrayList<ClipboardClip>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val imgPath = obj.optString("imagePath", "").takeIf { it.isNotBlank() }
                if (imgPath != null && !File(imgPath).isFile) continue
                val text = obj.getString("text")
                if (imgPath == null && isApiKey(text)) continue
                list += ClipboardClip(
                    id = obj.getString("id"),
                    text = text,
                    timestampMs = obj.getLong("ts"),
                    pinned = obj.optBoolean("pinned", false),
                    imagePath = imgPath,
                    mimeType = obj.optString("mimeType", "").takeIf { it.isNotBlank() },
                    ocrText = obj.optString("ocrText", "").takeIf { it.isNotBlank() }
                )
            }
            _items.value = list
        }
    }

    private fun saveToDisk(list: List<ClipboardClip>) {
        val file = storageFile ?: return
        runCatching {
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
                    }
                )
            }
            file.writeText(arr.toString())
        }
    }

    companion object {
        // Keep recognizable keys off the keyboard and out of clipboard history.
        private val apiKeyPattern = Regex("""(?:AIza[0-9A-Za-z_-]{35}|AQ\.[0-9A-Za-z_.-]{24,}|sk-[0-9A-Za-z_-]{24,})""")

        internal fun isApiKey(text: String): Boolean = apiKeyPattern.matches(text.trim())


        fun createForContext(context: Context): ClipboardStore {
            val safeCtx = ContextCompat.createDeviceProtectedStorageContext(context.applicationContext)
                ?: context.applicationContext
            val file = File(safeCtx.filesDir, "tapwing_clipboard_history.json")
            return ClipboardStore(file)
        }
    }
}
