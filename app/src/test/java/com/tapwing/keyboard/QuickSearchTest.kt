package com.tapwing.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickSearchTest {
    @Test
    fun searchUsesTheConfiguredEngineAndEncodesUserText() {
        assertEquals("https://duckduckgo.com/?q=M%C3%BCnchen+%26+Wien%3F",
            QuickSearchEngine.DUCKDUCKGO.url(" München & Wien? "))
        assertEquals("https://search.brave.com/search?q=a%23b", QuickSearchEngine.BRAVE.url("a#b"))
        assertNull(QuickSearchEngine.GOOGLE.url("  "))
    }

    @Test
    fun browserIsDefaultAndAppMustMatchTheEngine() {
        assertEquals(QuickSearchEngine.GOOGLE, TapwingPrefs().quickSearchEngine)
        assertNull(QuickSearchEngine.GOOGLE.selectedApp(TapwingPrefs().quickSearchAppPackage))
        assertNull(QuickSearchEngine.BING.selectedApp("com.google.android.googlequicksearchbox"))
        assertEquals("com.google.android.googlequicksearchbox",
            QuickSearchEngine.GOOGLE.selectedApp("com.google.android.googlequicksearchbox")?.packageName)
    }
}
