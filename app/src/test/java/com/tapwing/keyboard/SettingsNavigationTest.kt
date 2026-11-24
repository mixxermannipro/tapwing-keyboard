package com.tapwing.keyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationTest {

    @Test
    fun everyDetailRouteBelongsToExactlyOneGboardSection() {
        val routes = SettingsSection.entries.flatMap { it.routes }
        val expected = SettingsRoute.entries.filterNot { it == SettingsRoute.HOME }.toSet()
        assertEquals(expected, routes.toSet())
        assertEquals(routes.size, routes.distinct().size)
        assertNull(SettingsSection.forRoute(SettingsRoute.HOME))
        assertEquals(SettingsSection.BASICS, SettingsSection.forRoute(SettingsRoute.LANGUAGES))
        assertEquals(SettingsSection.BASICS, SettingsSection.forRoute(SettingsRoute.PREFERENCES))
        assertEquals(SettingsSection.BASICS, SettingsSection.forRoute(SettingsRoute.APPEARANCE))
        assertEquals(SettingsSection.TYPING, SettingsSection.forRoute(SettingsRoute.CORRECTIONS))
        assertEquals(SettingsSection.TYPING, SettingsSection.forRoute(SettingsRoute.GLIDE_TYPING))
        assertEquals(SettingsSection.TYPING, SettingsSection.forRoute(SettingsRoute.VOICE))
        assertEquals(SettingsSection.TYPING, SettingsSection.forRoute(SettingsRoute.DICTIONARY))
    }

    @Test
    fun topLevelSectionsFollowGboardFamiliarOrder() {
        val ordered = SettingsCatalog.allRoutes
        assertEquals(
            listOf(
                SettingsRoute.LANGUAGES,
                SettingsRoute.PREFERENCES,
                SettingsRoute.APPEARANCE,
                SettingsRoute.CORRECTIONS,
                SettingsRoute.GLIDE_TYPING,
                SettingsRoute.VOICE,
                SettingsRoute.DICTIONARY
            ),
            ordered.take(7)
        )
    }

    @Test
    fun bilingualMultiTokenSearchFindsSubSettingsAccurately() {
        val labels: (SettingsRoute) -> String = { it.name.lowercase().replace('_', ' ') }

        // Blank query returns full catalog
        assertEquals(SettingsCatalog.allRoutes, SettingsCatalog.search("   ", labels))

        // German & English sub-setting keyword queries
        assertTrue(SettingsRoute.PREFERENCES in SettingsCatalog.search("haptik", labels))
        assertTrue(SettingsRoute.PREFERENCES in SettingsCatalog.search("keyboard height", labels))
        assertTrue(SettingsRoute.APPEARANCE in SettingsCatalog.search("amoled dark", labels))
        assertTrue(SettingsRoute.APPEARANCE in SettingsCatalog.search("liquid glass", labels))
        assertTrue(SettingsRoute.GLIDE_TYPING in SettingsCatalog.search("cursor trackpad", labels))
        assertTrue(SettingsRoute.GLIDE_TYPING in SettingsCatalog.search("leertaste", labels))
        assertTrue(SettingsRoute.VOICE in SettingsCatalog.search("whisper offline", labels))
        assertTrue(SettingsRoute.AUTOFILL in SettingsCatalog.search("bitwarden", labels))
        assertTrue(SettingsRoute.CLIPBOARD in SettingsCatalog.search("screenshot ocr", labels))
        assertTrue(SettingsRoute.WRITING_TOOLS in SettingsCatalog.search("proofread", labels))

        // Unmatched query returns empty
        assertTrue(SettingsCatalog.search("zzznonexistentfeature", labels).isEmpty())
    }

    @Test
    fun expressiveItemShapeUsesOuterRadiusOnEdgesAndInnerBetweenItems() {
        val single = expressiveItemShape(index = 0, count = 1)
        assertEquals(single.topStart, single.bottomEnd)

        val first = expressiveItemShape(index = 0, count = 3)
        val middle = expressiveItemShape(index = 1, count = 3)
        val last = expressiveItemShape(index = 2, count = 3)

        assertEquals(first.topStart, last.bottomStart)
        assertEquals(first.bottomStart, middle.topStart)
        assertEquals(middle.bottomEnd, last.topEnd)
    }
}
