package com.tapwing.keyboard.undo

enum class UndoReason {
    AUTOCORRECT,
    VOICE_INSERT,
    AI_REWRITE,
    OCR_INSERT,
    BULK_DELETE
}

data class UndoSnapshot(
    val beforeText: String,
    val afterText: String,
    val reason: UndoReason,
    val label: String,
    val timestampMs: Long = System.currentTimeMillis()
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
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (beforeText == afterText) return
        val snapshot = UndoSnapshot(
            beforeText = beforeText,
            afterText = afterText,
            reason = reason,
            label = label,
            timestampMs = timestampMs
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
