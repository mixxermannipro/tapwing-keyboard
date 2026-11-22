package com.tapwing.keyboard.undo

enum class UndoReason {
    AUTOCORRECT,
    VOICE_INSERT,
    AI_REWRITE,
    OCR_INSERT,
    BULK_DELETE,
    INSERT
}

data class UndoAnchor(
    val before: String,
    val selected: String,
    val after: String,
    val offset: Int?
)

data class UndoSnapshot(
    val beforeText: String,
    val afterText: String,
    val reason: UndoReason,
    val label: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val anchor: UndoAnchor? = null,
    val originalCorrectionWord: String? = null
)

class UndoManager(private val maxHistory: Int = 10) {

    private val stack = ArrayDeque<UndoSnapshot>()
    private var unrelatedTypingCount = 0
    private var activeBannerSnapshot: UndoSnapshot? = null

    fun record(
        beforeText: String,
        afterText: String,
        reason: UndoReason,
        label: String,
        timestampMs: Long = System.currentTimeMillis(),
        anchor: UndoAnchor? = null,
        originalCorrectionWord: String? = null
    ) {
        if (beforeText == afterText) return
        val snapshot = UndoSnapshot(
            beforeText = beforeText,
            afterText = afterText,
            reason = reason,
            label = label,
            timestampMs = timestampMs,
            anchor = anchor,
            originalCorrectionWord = originalCorrectionWord
        )
        stack.addLast(snapshot)
        while (stack.size > maxHistory) {
            stack.removeFirst()
        }
        activeBannerSnapshot = snapshot
        unrelatedTypingCount = 0
    }

    fun activeBanner(): UndoSnapshot? = activeBannerSnapshot

    fun canUndo(): Boolean = stack.isNotEmpty()

    fun matchingUndo(anchor: UndoAnchor): UndoSnapshot? =
        stack.lastOrNull()?.takeIf { anchor.selected.isEmpty() && it.anchor == anchor }

    fun notifyUnrelatedTyping() {
        if (activeBannerSnapshot == null) return
        unrelatedTypingCount++
        if (unrelatedTypingCount >= 2) {
            activeBannerSnapshot = null
        }
    }

    fun popUndo(): UndoSnapshot? {
        val popped = stack.removeLastOrNull()
        activeBannerSnapshot = null
        unrelatedTypingCount = 0
        return popped
    }

    fun dismissBanner() {
        activeBannerSnapshot = null
        unrelatedTypingCount = 0
    }

    fun clear() {
        stack.clear()
        activeBannerSnapshot = null
        unrelatedTypingCount = 0
    }
}
