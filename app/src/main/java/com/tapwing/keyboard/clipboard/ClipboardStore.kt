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
    val pinned: Boolean = false
)

/**
 * Pure Kotlin / JSON-backed clipboard store supporting pinned items,
 * configurable retention (1h, 24h, 7d, 30d, Never), and up to 100 recent unpinned clips.
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
        if (text.isEmpty()) return

        val now = clockMs()
        val existingPinned = _items.value.firstOrNull { it.text == text && it.pinned }
        if (existingPinned != null) {
            val updated = _items.value.map {
                if (it.id == existingPinned.id) it.copy(timestampMs = now) else it
            }
            applyAndSave(updated, retention, maxUnpinnedItems, now)
            return
        }

        val withoutDup = _items.value.filterNot { it.text == text }
        val newClip = ClipboardClip(
            id = UUID.randomUUID().toString(),
            text = text,
            timestampMs = now,
            pinned = false
        )
        applyAndSave(listOf(newClip) + withoutDup, retention, maxUnpinnedItems, now)
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
        val updated = _items.value.filterNot { it.id == id }
        applyAndSave(updated, retention, maxUnpinnedItems, now)
    }

    fun clearUnpinned(retention: ClipboardRetention, maxUnpinnedItems: Int) {
        val now = clockMs()
        val updated = _items.value.filter { it.pinned }
        applyAndSave(updated, retention, maxUnpinnedItems, now)
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
                list += ClipboardClip(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    timestampMs = obj.getLong("ts"),
                    pinned = obj.optBoolean("pinned", false)
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
                    }
                )
            }
            file.writeText(arr.toString())
        }
    }

    companion object {
        fun createForContext(context: Context): ClipboardStore {
            val safeCtx = ContextCompat.createDeviceProtectedStorageContext(context.applicationContext)
                ?: context.applicationContext
            val file = File(safeCtx.filesDir, "tapwing_clipboard_history.json")
            return ClipboardStore(file)
        }
    }
}
