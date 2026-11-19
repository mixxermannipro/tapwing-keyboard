package com.tapwing.keyboard.data

/**
 * Available shortcuts that the user can pin to and freely reorder in the top keyboard strip.
 */
enum class QuickBarAction {
    WRITING_TOOLS,
    CLIPBOARD,
    TEXT_EDIT,
    SCAN_TEXT,
    SETTINGS,
    RESIZE,
    EMOJI,
    ONE_HANDED,
    INCOGNITO,
    QUICK_SEARCH,
    APPLE_MODE,
    LANGUAGE,
    UNDO;

    companion object {
        val DEFAULT_ACTIONS: List<QuickBarAction> = listOf(
            WRITING_TOOLS,
            CLIPBOARD,
            TEXT_EDIT,
            SCAN_TEXT,
            SETTINGS
        )

        const val MAX_PINNED_ACTIONS: Int = 8
        private const val EMPTY_SENTINEL: String = "__EMPTY__"

        fun serialize(actions: List<QuickBarAction>): String {
            val cleaned = actions.distinct().take(MAX_PINNED_ACTIONS)
            return if (cleaned.isEmpty()) {
                EMPTY_SENTINEL
            } else {
                cleaned.joinToString(separator = ",") { it.name }
            }
        }

        fun parse(raw: String?): List<QuickBarAction> {
            if (raw == null) return DEFAULT_ACTIONS
            val trimmed = raw.trim()
            if (trimmed == EMPTY_SENTINEL) return emptyList()
            if (trimmed.isEmpty()) return DEFAULT_ACTIONS

            val parsed = trimmed.split(',')
                .mapNotNull { token ->
                    val name = token.trim()
                    if (name.isEmpty()) null else runCatching { valueOf(name) }.getOrNull()
                }
                .distinct()
                .take(MAX_PINNED_ACTIONS)

            return parsed.ifEmpty { DEFAULT_ACTIONS }
        }

        fun fromLegacyToggles(
            showWritingTools: Boolean,
            showClipboard: Boolean,
            showScanText: Boolean
        ): List<QuickBarAction> = DEFAULT_ACTIONS.filter { action ->
            when (action) {
                WRITING_TOOLS -> showWritingTools
                CLIPBOARD -> showClipboard
                SCAN_TEXT -> showScanText
                else -> true
            }
        }

        fun move(
            list: List<QuickBarAction>,
            fromIndex: Int,
            toIndex: Int
        ): List<QuickBarAction> {
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return list
            }
            val mutable = list.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            return mutable.toList()
        }

        fun add(
            list: List<QuickBarAction>,
            action: QuickBarAction
        ): List<QuickBarAction> {
            if (action in list || list.size >= MAX_PINNED_ACTIONS) return list
            return list + action
        }

        fun remove(
            list: List<QuickBarAction>,
            action: QuickBarAction
        ): List<QuickBarAction> = list.filterNot { it == action }
    }
}
