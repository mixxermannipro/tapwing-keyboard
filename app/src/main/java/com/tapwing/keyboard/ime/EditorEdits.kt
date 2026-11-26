package com.tapwing.keyboard.ime

import android.os.Build
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.tapwing.keyboard.undo.UndoAnchor

private const val ANCHOR_WINDOW = 24
private const val WRITING_LIMIT = 8_000

internal fun InputConnection.safeGetTextBeforeCursor(n: Int): String =
    runCatching { getTextBeforeCursor(n, 0)?.toString().orEmpty() }.getOrDefault("")

internal fun InputConnection.safeGetSelectedText(): String =
    runCatching { getSelectedText(0)?.toString().orEmpty() }.getOrDefault("")

internal fun InputConnection.readUndoAnchor(): UndoAnchor? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSurroundingText(ANCHOR_WINDOW, ANCHOR_WINDOW, 0)?.let { surrounding ->
            val start = surrounding.selectionStart
            val end = surrounding.selectionEnd
            if (start !in 0..surrounding.text.length || end !in start..surrounding.text.length) return null
            return UndoAnchor(
                surrounding.text.subSequence(0, start).toString().takeLast(ANCHOR_WINDOW),
                surrounding.text.subSequence(start, end).toString(),
                surrounding.text.subSequence(end, surrounding.text.length).toString().take(ANCHOR_WINDOW),
                (surrounding.offset + start).takeIf { surrounding.offset >= 0 }
            )
        }
    }
    val before = getTextBeforeCursor(ANCHOR_WINDOW, 0)?.toString() ?: return null
    val after = getTextAfterCursor(ANCHOR_WINDOW, 0)?.toString() ?: return null
    UndoAnchor(before, getSelectedText(0)?.toString().orEmpty(), after, null)
}.getOrNull()

/** A write target is specific to the original selection or the original complete field. */
internal data class WritingTarget(
    val text: String,
    val beforeLength: Int,
    val afterLength: Int,
    val anchor: UndoAnchor
) {
    fun replace(connection: InputConnection, replacement: String): Boolean {
        if (connection.readWritingTarget() != this) return false
        return connection.replaceEditorText(beforeLength, afterLength, text, replacement)
    }
}

internal fun InputConnection.readWritingTarget(useSelection: Boolean = true): WritingTarget? = runCatching {
    val anchor = readUndoAnchor() ?: return null
    if (anchor.selected.isNotEmpty()) {
        return if (useSelection && anchor.selected.length <= WRITING_LIMIT) {
            WritingTarget(anchor.selected, 0, 0, anchor)
        } else null
    }

    val before = getTextBeforeCursor(WRITING_LIMIT + 1, 0)?.toString() ?: return null
    val after = getTextAfterCursor(WRITING_LIMIT + 1, 0)?.toString() ?: return null
    if (before.length + after.length > WRITING_LIMIT || (anchor.offset != null && anchor.offset != before.length)) return null
    val extracted = getExtractedText(ExtractedTextRequest(), 0)
    if (extracted != null && (extracted.startOffset != 0 || extracted.text?.toString() != before + after)) {
        return null
    }
    WritingTarget(before + after, before.length, after.length, anchor)
}.getOrNull()

internal fun InputConnection.replaceEditorText(
    beforeLength: Int,
    afterLength: Int,
    original: String,
    replacement: String
): Boolean = runCatching {
    beginBatchEdit()
    try {
        if ((beforeLength > 0 || afterLength > 0) && !deleteSurroundingText(beforeLength, afterLength)) return false
        if (replacement.isEmpty() && (beforeLength > 0 || afterLength > 0)) return true
        if (commitText(replacement, 1)) return true
        if (beforeLength > 0 || afterLength > 0) commitText(original, 1)
        return false
    } finally {
        runCatching { endBatchEdit() }
    }
}.getOrDefault(false)
