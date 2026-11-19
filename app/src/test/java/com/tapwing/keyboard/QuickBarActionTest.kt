package com.tapwing.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickBarActionTest {

    @Test
    fun serializesAndParsesCustomOrderEmptyAndDefaults() {
        assertEquals(QuickBarAction.DEFAULT_ACTIONS, QuickBarAction.parse(null))
        assertEquals(QuickBarAction.DEFAULT_ACTIONS, QuickBarAction.parse("INVALID_ENTRY"))

        val custom = listOf(
            QuickBarAction.APPLE_MODE,
            QuickBarAction.RESIZE,
            QuickBarAction.WRITING_TOOLS,
            QuickBarAction.CLIPBOARD
        )
        val encoded = QuickBarAction.serialize(custom)
        assertEquals("APPLE_MODE,RESIZE,WRITING_TOOLS,CLIPBOARD", encoded)
        assertEquals(custom, QuickBarAction.parse(encoded))

        val emptyEncoded = QuickBarAction.serialize(emptyList())
        assertTrue(QuickBarAction.parse(emptyEncoded).isEmpty())

        val deduped = QuickBarAction.parse("RESIZE,UNKNOWN,RESIZE,EMOJI,UNDO")
        assertEquals(listOf(QuickBarAction.RESIZE, QuickBarAction.EMOJI, QuickBarAction.UNDO), deduped)
    }

    @Test
    fun moveAddRemoveEnforceBoundsAndCap() {
        var list = listOf(QuickBarAction.WRITING_TOOLS, QuickBarAction.CLIPBOARD, QuickBarAction.SETTINGS)

        list = QuickBarAction.move(list, 2, 0)
        assertEquals(
            listOf(QuickBarAction.SETTINGS, QuickBarAction.WRITING_TOOLS, QuickBarAction.CLIPBOARD),
            list
        )
        // Out of bounds leaves list unchanged
        assertEquals(list, QuickBarAction.move(list, -1, 1))
        assertEquals(list, QuickBarAction.move(list, 0, 99))

        // Duplicate add ignored
        assertEquals(list, QuickBarAction.add(list, QuickBarAction.SETTINGS))

        // Fill up to MAX_PINNED_ACTIONS and confirm cap is respected
        var full = emptyList<QuickBarAction>()
        QuickBarAction.entries.forEach { full = QuickBarAction.add(full, it) }
        assertEquals(QuickBarAction.MAX_PINNED_ACTIONS, full.size)

        // Remove works cleanly
        val removed = QuickBarAction.remove(list, QuickBarAction.WRITING_TOOLS)
        assertEquals(listOf(QuickBarAction.SETTINGS, QuickBarAction.CLIPBOARD), removed)
    }

    @Test
    fun migratesLegacyIconTogglesCleanly() {
        val migrated = QuickBarAction.fromLegacyToggles(
            showWritingTools = false,
            showClipboard = true,
            showScanText = false
        )
        assertEquals(
            listOf(QuickBarAction.CLIPBOARD, QuickBarAction.TEXT_EDIT, QuickBarAction.SETTINGS),
            migrated
        )
    }
}
