package com.tapwing.keyboard.data

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

data class TypingStats(
    val wordsTyped: Long = 0L,
    val charactersTyped: Long = 0L,
    val autocorrectionsApplied: Long = 0L,
    val suggestionsAccepted: Long = 0L,
    val swipesDecoded: Long = 0L,
    val voiceDictations: Long = 0L,
    val snippetsInserted: Long = 0L,
    val charactersSaved: Long = 0L,
    val activeTypingMs: Long = 0L
) {
    val estimatedWpm: Int
        get() {
            if (activeTypingMs < 10_000L || charactersTyped < 25L) return 0
            val minutes = activeTypingMs.toDouble() / 60_000.0
            return ((charactersTyped / 5.0) / minutes).roundToInt().coerceIn(0, 220)
        }
}

/**
 * Privacy-first local keyboard statistics store (stored strictly on-device in device-protected storage).
 * Pauses automatically in Incognito Mode.
 */
class TypingStatsStore(
    private val storageFile: File?,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val _stats = MutableStateFlow(TypingStats())
    val stats: StateFlow<TypingStats> = _stats.asStateFlow()

    private var lastKeystrokeMs: Long = 0L
    private var lastLoadedModMs: Long = -1L

    init {
        loadFromDisk()
    }

    @Synchronized
    fun reloadIfModifiedOnDisk() {
        val file = storageFile ?: return
        if (!file.isFile) return
        val mod = file.lastModified()
        if (mod != lastLoadedModMs) loadFromDisk()
    }

    @Synchronized
    fun recordKeystroke(chars: Int, incognito: Boolean) {
        if (incognito || chars <= 0) return
        val now = clockMs()
        val delta = if (lastKeystrokeMs > 0L) (now - lastKeystrokeMs) else 0L
        lastKeystrokeMs = now
        val addedTime = if (delta in 1L..4_000L) delta else 0L
        update {
            it.copy(
                charactersTyped = it.charactersTyped + chars,
                activeTypingMs = it.activeTypingMs + addedTime
            )
        }
    }

    @Synchronized
    fun recordWordCompleted(incognito: Boolean) {
        if (incognito) return
        update { it.copy(wordsTyped = it.wordsTyped + 1) }
    }

    @Synchronized
    fun recordAutocorrect(incognito: Boolean) {
        if (incognito) return
        update { it.copy(autocorrectionsApplied = it.autocorrectionsApplied + 1) }
    }

    @Synchronized
    fun recordSuggestionAccepted(savedChars: Int, incognito: Boolean) {
        if (incognito) return
        update {
            it.copy(
                suggestionsAccepted = it.suggestionsAccepted + 1,
                wordsTyped = it.wordsTyped + 1,
                charactersSaved = it.charactersSaved + savedChars.coerceAtLeast(0)
            )
        }
    }

    @Synchronized
    fun recordSwipeWord(wordLength: Int, incognito: Boolean) {
        if (incognito) return
        update {
            it.copy(
                swipesDecoded = it.swipesDecoded + 1,
                wordsTyped = it.wordsTyped + 1,
                charactersSaved = it.charactersSaved + (wordLength - 1).coerceAtLeast(0)
            )
        }
    }

    @Synchronized
    fun recordVoiceDictation(insertedChars: Int, incognito: Boolean) {
        if (incognito || insertedChars <= 0) return
        update {
            it.copy(
                voiceDictations = it.voiceDictations + 1,
                charactersSaved = it.charactersSaved + insertedChars
            )
        }
    }

    @Synchronized
    fun recordSnippetInserted(insertedChars: Int, incognito: Boolean) {
        if (incognito || insertedChars <= 0) return
        update {
            it.copy(
                snippetsInserted = it.snippetsInserted + 1,
                charactersSaved = it.charactersSaved + insertedChars
            )
        }
    }

    @Synchronized
    fun reset() {
        lastKeystrokeMs = 0L
        _stats.value = TypingStats()
        saveToDisk()
    }

    private fun update(transform: (TypingStats) -> TypingStats) {
        _stats.value = transform(_stats.value)
        saveToDisk()
    }

    private fun loadFromDisk() {
        val file = storageFile ?: return
        if (!file.isFile) return
        runCatching {
            val obj = JSONObject(file.readText())
            _stats.value = TypingStats(
                wordsTyped = obj.optLong("wordsTyped", 0L),
                charactersTyped = obj.optLong("charactersTyped", 0L),
                autocorrectionsApplied = obj.optLong("autocorrectionsApplied", 0L),
                suggestionsAccepted = obj.optLong("suggestionsAccepted", 0L),
                swipesDecoded = obj.optLong("swipesDecoded", 0L),
                voiceDictations = obj.optLong("voiceDictations", 0L),
                snippetsInserted = obj.optLong("snippetsInserted", 0L),
                charactersSaved = obj.optLong("charactersSaved", 0L),
                activeTypingMs = obj.optLong("activeTypingMs", 0L)
            )
            lastLoadedModMs = file.lastModified()
        }
    }

    private fun saveToDisk() {
        val file = storageFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val s = _stats.value
            val obj = JSONObject().apply {
                put("wordsTyped", s.wordsTyped)
                put("charactersTyped", s.charactersTyped)
                put("autocorrectionsApplied", s.autocorrectionsApplied)
                put("suggestionsAccepted", s.suggestionsAccepted)
                put("swipesDecoded", s.swipesDecoded)
                put("voiceDictations", s.voiceDictations)
                put("snippetsInserted", s.snippetsInserted)
                put("charactersSaved", s.charactersSaved)
                put("activeTypingMs", s.activeTypingMs)
            }
            file.writeText(obj.toString())
            lastLoadedModMs = file.lastModified()
        }
    }

    companion object {
        fun createForContext(context: Context): TypingStatsStore {
            val safeCtx = ContextCompat.createDeviceProtectedStorageContext(context.applicationContext)
                ?: context.applicationContext
            return TypingStatsStore(File(safeCtx.filesDir, "tapwing_typing_stats.json"))
        }
    }
}
