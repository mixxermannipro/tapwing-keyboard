package com.tapwing.keyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsNavigationTest {
    @Test
    fun everyDetailBelongsToExactlyOneCategory() {
        val routes = SettingsTab.entries.flatMap { it.routes }
        assertEquals(SettingsRoute.entries.filterNot { it == SettingsRoute.HOME }.toSet(), routes.toSet())
        assertEquals(routes.size, routes.distinct().size)
        assertEquals(SettingsTab.WRITING, SettingsTab.forRoute(SettingsRoute.VOICE))
        assertEquals(SettingsTab.KEYBOARD, SettingsTab.forRoute(SettingsRoute.QUICK_BAR))
        assertEquals(SettingsTab.TOOLS, SettingsTab.forRoute(SettingsRoute.AUTOFILL))
    }

    @Test
    fun scrollDirectionMustPersistBeforeTheBarChanges() {
        val scroll = SettingsBarScroll()
        assertNull(scroll.update(visible = true, listDelta = -7f, leftoverDelta = 0f, threshold = 12f))
        assertNull(scroll.update(visible = true, listDelta = 3f, leftoverDelta = 0f, threshold = 12f))
        assertNull(scroll.update(visible = true, listDelta = -7f, leftoverDelta = 0f, threshold = 12f))
        assertEquals(false, scroll.update(visible = true, listDelta = -6f, leftoverDelta = 0f, threshold = 12f))
        assertEquals(true, scroll.update(visible = false, listDelta = 5f, leftoverDelta = 10f, threshold = 12f))
        assertNull(scroll.update(visible = true, listDelta = 0f, leftoverDelta = -35f, threshold = 12f))
    }
}
